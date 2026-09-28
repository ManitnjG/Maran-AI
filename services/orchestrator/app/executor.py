from .models import Mission, MissionStatus, Verification

async def execute_local(mission: Mission) -> Mission:
    """Execution scaffold. Real tools plug into agent handlers without changing mission semantics."""
    if mission.status == MissionStatus.waiting_approval:
        return mission
    for step in mission.plan:
        if step.requires_approval:
            continue
        step.status="completed"
        mission.events.append({"type":"step_completed","step_id":step.id,"agent":step.agent})
    mission.status=MissionStatus.verifying
    # Until external evidence/tool adapters are connected, never falsely claim verified.
    mission.verification=Verification.unverified
    mission.status=MissionStatus.completed
    mission.events.append({"type":"mission_completed","verification":mission.verification.value})
    return mission
