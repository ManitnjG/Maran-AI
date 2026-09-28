from .models import Mission, MissionStatus
from .planner import local_plan

class ManagerAgent:
    id="manager"
    name="Manager MARAN"

    def assign(self, mission: Mission) -> Mission:
        mission.assigned_agents, mission.plan = local_plan(mission.objective)
        mission.events.append({"type":"manager_assigned","manager":self.id,"agents":mission.assigned_agents})
        mission.status=MissionStatus.running
        if any(step.requires_approval for step in mission.plan):
            mission.status=MissionStatus.waiting_approval
            mission.events.append({"type":"approval_required","manager":self.id})
        return mission

    def stop_agent(self, mission: Mission, agent_id: str, reason: str="Stopped by Manager MARAN") -> Mission:
        for step in mission.plan:
            if step.agent==agent_id and step.status in ("pending","running"):
                step.status="stopped"
        mission.events.append({"type":"agent_stopped","manager":self.id,"agent":agent_id,"reason":reason})
        return mission

    def stop_all(self, mission: Mission, reason: str="Mission stopped by Manager MARAN") -> Mission:
        for step in mission.plan:
            if step.status in ("pending","running"):
                step.status="stopped"
        mission.status=MissionStatus.cancelled
        mission.events.append({"type":"workforce_stopped","manager":self.id,"reason":reason})
        return mission

manager=ManagerAgent()
