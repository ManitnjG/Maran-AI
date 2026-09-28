from .models import PlanStep
from .agents import select_agents,AGENTS
from .worker_factory import factory

APPROVAL_AGENTS={"accounting"}

def local_plan(objective:str)->tuple[list[str],list[PlanStep]]:
    agents=select_agents(objective)
    custom=factory.match(objective)
    for a in custom:
        if a.id not in agents:agents.insert(-1 if "verifier" in agents else len(agents),a.id)
    workers=[a for a in agents if a!="verifier"]
    steps=[]
    for i,aid in enumerate(workers,1):
        agent=factory.get(aid)
        if not agent:continue
        steps.append(PlanStep(id=f"step-{i}",title=f"{agent.name}: work on mission objective",agent=aid,requires_approval=aid in APPROVAL_AGENTS))
    steps.append(PlanStep(id=f"step-{len(steps)+1}",title="Verify evidence and final output",agent="verifier"))
    return agents,steps
