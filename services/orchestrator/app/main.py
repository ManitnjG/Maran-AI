import asyncio
import os
import secrets
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse
from .models import Mission, MissionCreate, MissionStatus, ApprovalDecision, StopRequest, VoiceCommand, WorkerCreate
from .planner import local_plan
from .store import store
from .executor import execute_local
from .manager import manager
from .voice import interpret
from .worker_factory import factory
from .context import context_vault
from .providers import configured_router
from .router import ProviderError

app=FastAPI(title="MARAN Orchestrator",version="0.1.0")

# Run one ASGI worker: active task ownership is process-local.
active_runs: dict[str, asyncio.Task] = {}

@app.middleware("http")
async def authenticate(request: Request, call_next):
    token = os.getenv("MARAN_ACCESS_TOKEN", "")
    if token and request.url.path != "/health":
        supplied = request.headers.get("Authorization", "")
        if not secrets.compare_digest(supplied, "Bearer " + token):
            return JSONResponse(status_code=401, content={"detail": "Authentication required"})
    return await call_next(request)

async def execute_saved(m):
    try:
        return await execute_local(m, checkpoint=store.put)
    finally:
        active_runs.pop(m.id, None)

@app.get("/health")
def health():
    router=configured_router()
    return {"ok":True,"service":"maran-orchestrator","configured_models":[s.provider.name for s in router.providers]}

@app.get("/agents")
def agents():
    from .agents import AGENTS
    return [{"id":a.id,"name":a.name,"skills":a.skills,"permissions":a.permissions} for a in AGENTS.values()]

@app.post("/missions",response_model=Mission)
def create_mission(req:MissionCreate):
    m=Mission(objective=req.objective,workspace_id=req.workspace_id,status=MissionStatus.planning)
    m.events.append({"type":"mission_created"})
    m=manager.assign(m)
    return store.put(m)

@app.post("/missions/{mission_id}/run",response_model=Mission)
async def run_mission(mission_id:str):
    m=store.get(mission_id)
    if not m: raise HTTPException(404,"Mission not found")
    if mission_id not in active_runs:
        active_runs[mission_id] = asyncio.create_task(execute_saved(m))
    task = active_runs[mission_id]
    try:
        return await asyncio.shield(task)
    except asyncio.CancelledError:
        if task.cancelled():
            return store.get(mission_id)
        raise

@app.get("/missions",response_model=list[Mission])
def list_missions(): return store.all()

@app.get("/missions/{mission_id}",response_model=Mission)
def get_mission(mission_id:str):
    m=store.get(mission_id)
    if not m: raise HTTPException(404,"Mission not found")
    return m

@app.post("/missions/{mission_id}/approval",response_model=Mission)
async def approve(mission_id:str,decision:ApprovalDecision):
    m=store.get(mission_id)
    if not m: raise HTTPException(404,"Mission not found")
    if m.status != MissionStatus.waiting_approval:
        raise HTTPException(409,"Mission is not waiting for approval")
    m.events.append({"type":"approval_decision","approved":decision.approved,"note":decision.note})
    if not decision.approved:
        return store.put(manager.stop_all(m, "Approval rejected"))
    for step in m.plan:
        if step.requires_approval: step.approved=True
    m.status=MissionStatus.running
    store.put(m)
    return await run_mission(mission_id)

@app.post("/missions/{mission_id}/stop",response_model=Mission)
async def stop_work(mission_id:str,request:StopRequest):
    task = active_runs.get(mission_id)
    if task:
        task.cancel()
        try: await task
        except asyncio.CancelledError: pass
    m=store.get(mission_id)
    if not m: raise HTTPException(404,"Mission not found")
    m=manager.stop_agent(m,request.agent_id,request.reason) if request.agent_id else manager.stop_all(m,request.reason)
    return store.put(m)


@app.post("/voice/interpret")
def voice_interpret(command:VoiceCommand):
    result=interpret(command)
    if result.get("action")=="create_worker":
        worker=factory.create(result["name"],result["skills"],result["temporary"])
        return {**result,"worker":{"id":worker.id,"name":worker.name,"skills":worker.skills,"permissions":worker.permissions}}
    return result


@app.post("/workers")
def create_worker(request:WorkerCreate):
    worker=factory.create(request.name,request.skills,request.temporary)
    return {"id":worker.id,"name":worker.name,"skills":worker.skills,"permissions":worker.permissions}

@app.get("/workers")
def workers():
    return [{"id":a.id,"name":a.name,"skills":a.skills,"permissions":a.permissions} for a in factory.all()]

@app.delete("/workers/{agent_id}")
async def stop_dynamic_worker(agent_id:str):
    if not factory.stop(agent_id): raise HTTPException(404,"Dynamic worker not found")
    for m in store.all():
        if agent_id in m.assigned_agents and m.status not in (MissionStatus.completed, MissionStatus.cancelled):
            await stop_work(m.id, StopRequest(agent_id=agent_id, reason="Worker removed"))
    return {"ok":True,"agent_id":agent_id}

@app.post("/workers/cleanup")
def cleanup_workers():
    in_use={a for m in store.all() if m.status not in (MissionStatus.completed, MissionStatus.cancelled) for a in m.assigned_agents}
    return {"stopped":factory.cleanup(exclude=in_use)}

@app.get("/missions/{mission_id}/context")
def mission_context(mission_id:str):
    if not store.get(mission_id): raise HTTPException(404,"Mission not found")
    return context_vault.get(mission_id)
