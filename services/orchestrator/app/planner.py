from .models import PlanStep
from .agents import select_agents, AGENTS

APPROVAL_AGENTS={"accounting"}

def local_plan(objective: str) -> tuple[list[str],list[PlanStep]]:
    """Safe deterministic planner used without requiring an LLM."""
    agents=select_agents(objective)
    workers=[agent_id for agent_id in agents if agent_id!="verifier"]
    steps=[]
    for i,agent_id in enumerate(workers, start=1):
        steps.append(PlanStep(
            id=f"step-{i}",
            title=f"{AGENTS[agent_id].name}: work on mission objective",
            agent=agent_id,
            requires_approval=agent_id in APPROVAL_AGENTS
        ))
    steps.append(PlanStep(id=f"step-{len(steps)+1}",title="Verify evidence and final output",agent="verifier"))
    return agents,steps
