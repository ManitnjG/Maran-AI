from fastapi import FastAPI, HTTPException
from .models import Mission, MissionCreate, MissionStatus, ApprovalDecision
from .planner import local_plan
from .store import store
from .executor import execute_local

app=FastAPI(title="MARAN Orchestrator",version="0.1.0")

@app.get("/health")
def health(): return {"ok":True,"service":"maran-orchestrator"}

@app.get("/agents")
def agents():
    from .agents import AGENTS
    return [{"id":a.id,"name":a.name,"skills":a.skills,"permissions":a.permissions} for a in AGENTS.values()]

@app.post("/missions",response_model=Mission)
def create_mission(req:MissionCreate):
    m=Mission(objective=req.objective,workspace_id=req.workspace_id,status=MissionStatus.planning)
    m.events.append({"type":"mission_created"})
    m.assigned_agents,m.plan=local_plan(m.objective)
    m.status=MissionStatus.running
    if any(s.requires_approval for s in m.plan):
        m.status=MissionStatus.waiting_approval
        m.events.append({"type":"approval_required"})
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
