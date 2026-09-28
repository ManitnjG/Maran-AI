from dataclasses import dataclass
from .agents import AGENTS
from .config import settings

@dataclass(frozen=True)
class WorkforcePolicy:
    max_parallel:int=settings.max_parallel_workers
    max_retries:int=settings.max_worker_retries
    step_timeout_seconds:int=settings.worker_timeout_seconds
    idle_stop_seconds:int=300
    max_agents_per_mission:int=settings.max_agents_per_mission

SENSITIVE={"draft_accounting"}
SAFE_DYNAMIC={"read_public_web","read_files","create_files","read_code","draft_code","read_outputs"}

def dedupe_agents(agent_ids:list[str])->list[str]:
    out=[]
    seen_skills=set()
    for aid in agent_ids:
        agent=AGENTS.get(aid)
        if not agent:
            # Dynamic workers were already selected by Manager; preserve them here.
            out.append(aid); continue
        skills=set(agent.skills)
        if aid!="verifier" and skills and skills.issubset(seen_skills): continue
        out.append(aid); seen_skills.update(skills)
    return out

policy=WorkforcePolicy()
