from contextlib import asynccontextmanager
import asyncio
import os
import secrets
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse
from .models import Mission, MissionCreate, MissionStatus, ApprovalDecision, StopRequest, VoiceCommand, WorkerCreate, MemoryWrite
from .planner import local_plan
from .store import store
from .executor import execute_local
from .autonomous import run_autonomous
from .skills import skill_registry
from .memory import memory_store
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

app=FastAPI(title="MARAN Orchestrator",version="0.6.0",lifespan=lifespan)

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
        if m.autonomy_enabled:
            return await run_autonomous(m, checkpoint=store.put)
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
    m=Mission(objective=req.objective,workspace_id=req.workspace_id,status=MissionStatus.planning,autonomy_enabled=req.autonomous,max_cycles=req.max_cycles)
    m.events.append({"type":"mission_created","autonomous":req.autonomous,"max_cycles":req.max_cycles})
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

@app.post("/missions/{mission_id}/autonomous-run",response_model=Mission)
async def autonomous_run(mission_id:str):
    m=store.get(mission_id)
    if not m: raise HTTPException(404,"Mission not found")
    if not m.autonomy_enabled:
        m.autonomy_enabled=True
        m.events.append({"type":"autonomy_enabled","note":"Enabled by explicit autonomous-run request"})
        store.put(m)
    return await run_mission(mission_id)

@app.get("/missions/{mission_id}/events")
def mission_events(mission_id:str):
    m=store.get(mission_id)
    if not m: raise HTTPException(404,"Mission not found")
    return m.events

@app.get("/skills")
def learned_skills(workspace_id:str="default"):
    return skill_registry.all(workspace_id)

@app.delete("/skills/{skill_id}")
def forget_skill(skill_id:str,workspace_id:str="default"):
    if not skill_registry.delete(skill_id,workspace_id):
        raise HTTPException(404,"Learned skill not found")
    return {"ok":True,"skill_id":skill_id}

@app.get("/memory")
def workspace_memory(workspace_id:str="default"):
    return memory_store.all(workspace_id)

@app.post("/memory")
def remember(item:MemoryWrite):
    try:
        return memory_store.set(item.workspace_id,item.key,item.value)
    except ValueError as exc:
        raise HTTPException(422,str(exc)) from exc

@app.delete("/memory/{key}")
def forget_memory(key:str,workspace_id:str="default"):
    if not memory_store.delete(workspace_id,key):
        raise HTTPException(404,"Memory item not found")
    return {"ok":True,"key":key}

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


# --- External integrations and action endpoints (Maran 0.8) ---
from .integrations import (
    IntegrationError, integration_status, gmail_send, gmail_list, gmail_reply,
    calendar_create, drive_upload_text, github_dispatch, github_put_file,
    whatsapp_send, facebook_post, instagram_post, linkedin_post,
    twilio_sms, twilio_call, tally_post_xml,
)
from .crm import LeadInput, lead_store

class ConfirmedAction(BaseModel):
    confirmed: bool = False

class GmailSendRequest(ConfirmedAction):
    to: str = Field(min_length=3, max_length=320)
    subject: str = Field(min_length=1, max_length=300)
    body: str = Field(min_length=1, max_length=100_000)

class GmailReplyRequest(GmailSendRequest):
    message_id: str = Field(min_length=1, max_length=300)
    thread_id: str | None = Field(default=None, max_length=300)

class CalendarCreateRequest(ConfirmedAction):
    summary: str = Field(min_length=1, max_length=300)
    start: str = Field(min_length=10, max_length=80)
    end: str = Field(min_length=10, max_length=80)
    timezone: str = Field(default="Asia/Kolkata", max_length=80)
    description: str = Field(default="", max_length=10_000)

class DriveUploadRequest(ConfirmedAction):
    name: str = Field(min_length=1, max_length=240)
    content: str = Field(min_length=1, max_length=500_000)
    mime_type: str = Field(default="text/plain", max_length=100)

class GithubDispatchRequest(ConfirmedAction):
    workflow: str = Field(default="android.yml", max_length=240)
    ref: str = Field(default="main", max_length=240)
    inputs: dict[str, str] = Field(default_factory=dict)

class GithubFileRequest(ConfirmedAction):
    path: str = Field(min_length=1, max_length=500)
    content: str = Field(max_length=500_000)
    message: str = Field(min_length=1, max_length=300)
    branch: str = Field(default="main", max_length=240)
    sha: str | None = Field(default=None, max_length=100)

class MessageRequest(ConfirmedAction):
    to: str = Field(min_length=3, max_length=120)
    body: str = Field(min_length=1, max_length=10_000)

class SocialTextRequest(ConfirmedAction):
    text: str = Field(min_length=1, max_length=10_000)

class InstagramPostRequest(ConfirmedAction):
    image_url: str = Field(min_length=8, max_length=2000)
    caption: str = Field(default="", max_length=2200)

class VoiceCallRequest(ConfirmedAction):
    to: str = Field(min_length=3, max_length=120)
    twiml: str = Field(min_length=1, max_length=20_000)

class TallyPostRequest(ConfirmedAction):
    xml: str = Field(min_length=1, max_length=1_000_000)

class LeadStatusRequest(BaseModel):
    status: str = Field(min_length=1, max_length=40)
    notes: str | None = Field(default=None, max_length=4000)

def _require_confirmation(req: ConfirmedAction):
    if not req.confirmed:
        raise HTTPException(409, "This external action requires explicit confirmation")

def _integration_error(exc: IntegrationError):
    raise HTTPException(503, str(exc)) from exc

@app.get('/integrations')
def integrations_status():
    return integration_status()

@app.get('/actions/gmail/inbox')
async def action_gmail_inbox(max_results: int = 10, q: str = ""):
    try: return await gmail_list(max_results, q)
    except IntegrationError as exc: _integration_error(exc)

@app.post('/actions/gmail/send')
async def action_gmail_send(req: GmailSendRequest):
    _require_confirmation(req)
    try: return await gmail_send(req.to, req.subject, req.body)
    except IntegrationError as exc: _integration_error(exc)

@app.post('/actions/gmail/reply')
async def action_gmail_reply(req: GmailReplyRequest):
    _require_confirmation(req)
    try: return await gmail_reply(req.message_id, req.to, req.subject, req.body, req.thread_id)
    except IntegrationError as exc: _integration_error(exc)

@app.post('/actions/calendar/create')
async def action_calendar_create(req: CalendarCreateRequest):
    _require_confirmation(req)
    try: return await calendar_create(req.summary, req.start, req.end, req.timezone, req.description)
    except IntegrationError as exc: _integration_error(exc)

@app.post('/actions/drive/upload-text')
async def action_drive_upload(req: DriveUploadRequest):
    _require_confirmation(req)
    try: return await drive_upload_text(req.name, req.content, req.mime_type)
    except IntegrationError as exc: _integration_error(exc)

@app.post('/actions/github/dispatch')
async def action_github_dispatch(req: GithubDispatchRequest):
    _require_confirmation(req)
    try: return await github_dispatch(req.workflow, req.ref, req.inputs)
    except IntegrationError as exc: _integration_error(exc)

@app.post('/actions/github/file')
async def action_github_file(req: GithubFileRequest):
    _require_confirmation(req)
    try: return await github_put_file(req.path, req.content, req.message, req.branch, req.sha)
    except IntegrationError as exc: _integration_error(exc)

@app.post('/actions/whatsapp/send')
async def action_whatsapp(req: MessageRequest):
    _require_confirmation(req)
    try: return await whatsapp_send(req.to, req.body)
    except IntegrationError as exc: _integration_error(exc)

@app.post('/actions/facebook/post')
async def action_facebook(req: SocialTextRequest):
    _require_confirmation(req)
    try: return await facebook_post(req.text)
    except IntegrationError as exc: _integration_error(exc)

@app.post('/actions/instagram/post')
async def action_instagram(req: InstagramPostRequest):
    _require_confirmation(req)
    try: return await instagram_post(req.image_url, req.caption)
    except IntegrationError as exc: _integration_error(exc)

@app.post('/actions/linkedin/post')
async def action_linkedin(req: SocialTextRequest):
    _require_confirmation(req)
    try: return await linkedin_post(req.text)
    except IntegrationError as exc: _integration_error(exc)

@app.post('/actions/sms/send')
async def action_sms(req: MessageRequest):
    _require_confirmation(req)
    try: return await twilio_sms(req.to, req.body)
    except IntegrationError as exc: _integration_error(exc)

@app.post('/actions/call')
async def action_call(req: VoiceCallRequest):
    _require_confirmation(req)
    try: return await twilio_call(req.to, req.twiml)
    except IntegrationError as exc: _integration_error(exc)

@app.post('/actions/tally/post')
async def action_tally(req: TallyPostRequest):
    _require_confirmation(req)
    try: return await tally_post_xml(req.xml)
    except IntegrationError as exc: _integration_error(exc)

@app.get('/crm/leads')
def crm_leads(status: str = "", limit: int = 100):
    return lead_store.list(status, limit)

@app.post('/crm/leads')
def crm_add_lead(req: LeadInput):
    return lead_store.add(req)

@app.post('/crm/leads/{lead_id}/status')
def crm_update_lead(lead_id: str, req: LeadStatusRequest):
    item = lead_store.update_status(lead_id, req.status, req.notes)
    if not item: raise HTTPException(404, "Lead not found")
    return item
