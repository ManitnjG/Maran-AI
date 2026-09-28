from .models import PlanStep
from .agents import select_agents, AGENTS

APPROVAL_AGENTS={"accounting"}

def local_plan(objective: str) -> tuple[list[str],list[PlanStep]]:
    """Safe deterministic planner used until/configuration-independent of an LLM."""
    agents=select_agents(objective)
    steps=[]
    for i,agent_id in enumerate(a for a in agents if a!="verifier", start=1):
        steps.append(PlanStep(
            id=f"step-{i}",
            title=f"{AGENTS[agent_id].name}: work on mission objective",
            agent=agent_id,
            requires_approval=agent_id in APPROVAL_AGENTS
        ))
    steps.append(PlanStep(id=f"step-{len(steps)+1}",title="Verify evidence and final output",agent="verifier"))
    return agents,steps
