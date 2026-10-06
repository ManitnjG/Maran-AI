from .models import PlanStep
from .agents import select_agents,AGENTS
from .worker_factory import factory
from .skills import skill_registry
from .safety import apply_plan_policy
from .tool_registry import tool_for_agent

APPROVAL_AGENTS={"accounting"}

def local_plan(objective:str,workspace_id:str="default")->tuple[list[str],list[PlanStep]]:
    agents=select_agents(objective)
    learned=skill_registry.match(objective,workspace_id)
    if learned:
        for aid in learned["agents"]:
            if factory.get(aid) and aid not in agents:
                agents.insert(-1 if "verifier" in agents else len(agents),aid)
    custom=factory.match(objective)
    for a in custom:
        if a.id not in agents:agents.insert(-1 if "verifier" in agents else len(agents),a.id)
    workers=[a for a in agents if a!="verifier"]
    steps=[]
    for i,aid in enumerate(workers,1):
        agent=factory.get(aid)
        if not agent:continue
        steps.append(PlanStep(id=f"step-{i}",title=f"{agent.name}: work on mission objective",agent=aid,requires_approval=aid in APPROVAL_AGENTS,tool_id=tool_for_agent(aid)))
    steps.append(PlanStep(id=f"step-{len(steps)+1}",title="Verify evidence and final output",agent="verifier",tool_id=tool_for_agent("verifier")))
    apply_plan_policy(steps,objective)
    return agents,steps
