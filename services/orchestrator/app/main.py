from fastapi import FastAPI, HTTPException
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
    m=await execute_local(m)
    return store.put(m)

@app.get("/missions",response_model=list[Mission])
def list_missions(): return store.all()

@app.get("/missions/{mission_id}",response_model=Mission)
def get_mission(mission_id:str):
    m=store.get(mission_id)
    if not m: raise HTTPException(404,"Mission not found")
    return m

@app.post("/missions/{mission_id}/approval",response_model=Mission)
def approve(mission_id:str,decision:ApprovalDecision):
    m=store.get(mission_id)
    if not m: raise HTTPException(404,"Mission not found")
    m.events.append({"type":"approval_decision","approved":decision.approved,"note":decision.note})
    m.status=MissionStatus.running if decision.approved else MissionStatus.cancelled
    return store.put(m)


@app.post("/missions/{mission_id}/stop",response_model=Mission)
def stop_work(mission_id:str,request:StopRequest):
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
def stop_dynamic_worker(agent_id:str):
    if not factory.stop(agent_id): raise HTTPException(404,"Dynamic worker not found")
    return {"ok":True,"agent_id":agent_id}

@app.post("/workers/cleanup")
def cleanup_workers():
    return {"stopped":factory.cleanup()}

@app.get("/missions/{mission_id}/context")
def mission_context(mission_id:str):
    if not store.get(mission_id): raise HTTPException(404,"Mission not found")
    return context_vault.get(mission_id)
