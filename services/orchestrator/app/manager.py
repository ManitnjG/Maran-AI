from .models import Mission, MissionStatus
from .planner import local_plan
from .policies import policy, dedupe_agents
from .skills import skill_registry
from datetime import datetime, timezone

class ManagerAgent:
    id="manager"; name="Manager MARAN"
    def assign(self,mission:Mission)->Mission:
        learned=skill_registry.match(mission.objective,mission.workspace_id)
        agents,plan=local_plan(mission.objective,mission.workspace_id)
        agents=dedupe_agents(agents)[:policy.max_agents_per_mission]
        allowed=set(agents)
        mission.assigned_agents=agents
        mission.plan=[s for s in plan if s.agent in allowed]
        mission.events.append({"type":"manager_assigned","timestamp":datetime.now(timezone.utc).isoformat(),"strategy":"learned_recipe_plus_safe_template" if learned else "preliminary_safety_template","manager":self.id,"agents":agents,"max_parallel":policy.max_parallel,"learned_skill_id":learned["id"] if learned else None})
        mission.status=MissionStatus.waiting_approval if any(s.requires_approval for s in mission.plan) else MissionStatus.running
        if mission.status==MissionStatus.waiting_approval: mission.events.append({"type":"approval_required","timestamp":datetime.now(timezone.utc).isoformat(),"manager":self.id})
        return mission
    def recover(self,mission:Mission)->Mission:
        mission.events.append({"type":"manager_recovered","timestamp":datetime.now(timezone.utc).isoformat(),"manager":self.id})
        return mission
    def stop_agent(self,mission:Mission,agent_id:str,reason:str="Stopped by Manager MARAN")->Mission:
        for step in mission.plan:
            if step.agent==agent_id and step.status in ("pending","running","blocked"): step.status="stopped"
        mission.events.append({"type":"agent_stopped","manager":self.id,"agent":agent_id,"reason":reason});return mission
    def stop_all(self,mission:Mission,reason:str="Mission stopped by Manager MARAN")->Mission:
        for step in mission.plan:
            if step.status in ("pending","running","blocked"): step.status="stopped"
        mission.status=MissionStatus.cancelled;mission.events.append({"type":"workforce_stopped","manager":self.id,"reason":reason});return mission
manager=ManagerAgent()
