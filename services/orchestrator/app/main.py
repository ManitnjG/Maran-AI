from contextlib import asynccontextmanager
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

@asynccontextmanager
async def lifespan(app):
    if os.getenv("MARAN_ENV") == "production" and len(os.getenv("MARAN_ACCESS_TOKEN", "")) < 24:
        raise RuntimeError("Production requires a MARAN_ACCESS_TOKEN of at least 24 characters")
    for m in store.all():
        if m.status in (MissionStatus.planning, MissionStatus.running, MissionStatus.verifying):
            m.status = MissionStatus.blocked
            for step in m.plan:
                if step.status == "running": step.status = "pending"
            m.events.append({"type": "restart_recovered", "note": "Retry to continue incomplete work"})
            store.put(m)
    yield
    tasks = list(active_runs.values())
    for task in tasks: task.cancel()
    await asyncio.gather(*tasks, return_exceptions=True)

app=FastAPI(title="MARAN Orchestrator",version="0.5.0",lifespan=lifespan)

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

# Authenticated utility endpoints used by the Android Tools screen.
from pydantic import BaseModel, Field
from fastapi.responses import Response
from .business_tools import SalesVoucher, sales_voucher_xml
from .capabilities import capabilities, diagnostics
from .web_tools import research, fetch_page, ToolError

class SearchRequest(BaseModel):
    query: str = Field(min_length=1, max_length=2000)

class PageRequest(BaseModel):
    url: str = Field(min_length=8, max_length=2000)

@app.get('/capabilities')
def get_capabilities():
    return capabilities()

@app.post('/diagnostics')
async def check_connections():
    return await diagnostics()

@app.post('/tools/search')
async def search_public(req: SearchRequest):
    try: return await research(req.query)
    except ToolError as exc: raise HTTPException(503, str(exc)) from exc

@app.post('/tools/page')
async def read_public_page(req: PageRequest):
    try: return await fetch_page(req.url)
    except ToolError as exc: raise HTTPException(503, str(exc)) from exc

@app.post('/tools/tally-voucher')
def export_voucher(req: SalesVoucher):
    return {'filename': 'maran-sales-voucher.xml', 'content': sales_voucher_xml(req),
            'note': 'Accounting voucher draft only. Review amounts, date, company and existing ledger names before importing in Tally. No GST treatment or filing is included. Nothing has been posted.'}

@app.get('/missions/{mission_id}/export')
def export_mission(mission_id: str):
    m = store.get(mission_id)
    if not m: raise HTTPException(404, 'Mission not found')
    parts = [m.objective, f'Status: {m.status.value} | Verification: {m.verification.value}']
    for step in m.plan:
        parts.extend([f'\n## {step.title} ({step.status})', step.output or step.error or 'No output'])
        if step.evidence:
            parts.append('Retrieved at: ' + step.evidence.get('retrieved_at', 'unknown'))
            parts.extend(step.evidence.get('sources', []))
    return {'filename': f'maran-{m.id}.txt', 'content': '\n\n'.join(parts)}

@app.get('/backup')
def export_backup():
    # Portable JSON only: no provider keys, auth tokens or external credentials.
    return {'schema_version': 1, 'missions': [m.model_dump(mode='json') for m in store.all()]}

class BackupImport(BaseModel):
    schema_version: int
    missions: list[Mission] = Field(max_length=500)

@app.post('/backup/restore')
async def restore_backup(req: BackupImport):
    if req.schema_version != 1: raise HTTPException(422, 'Unsupported backup version')
    ids = [m.id for m in req.missions]
    if len(set(ids)) != len(ids): raise HTTPException(422, 'Duplicate mission IDs')
    if any(store.get(mid) for mid in ids): raise HTTPException(409, 'Restore would overwrite existing missions')
    for m in req.missions:
        if m.status not in (MissionStatus.completed, MissionStatus.cancelled, MissionStatus.waiting_approval):
            m.status = MissionStatus.blocked
        for step in m.plan:
            if step.status == 'running': step.status = 'pending'
        m.events.append({'type': 'backup_restored', 'note': 'No work executed by restore'})
    store.put_many(req.missions)
    return {'restored': len(req.missions)}
