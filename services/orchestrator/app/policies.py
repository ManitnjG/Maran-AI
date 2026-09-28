from dataclasses import dataclass
from .agents import AGENTS

@dataclass(frozen=True)
class WorkforcePolicy:
    max_parallel:int=6
    max_retries:int=2
    step_timeout_seconds:int=120
    idle_stop_seconds:int=300
    max_agents_per_mission:int=12

SENSITIVE={"draft_accounting"}
SAFE_DYNAMIC={"read_public_web","read_files","create_files","read_code","draft_code","read_outputs"}

def dedupe_agents(agent_ids:list[str])->list[str]:
    out=[]
    seen_skills=set()
    for aid in agent_ids:
        agent=AGENTS.get(aid)
        if not agent: continue
        skills=set(agent.skills)
        if aid!="verifier" and skills and skills.issubset(seen_skills): continue
        out.append(aid); seen_skills.update(skills)
    return out

policy=WorkforcePolicy()
