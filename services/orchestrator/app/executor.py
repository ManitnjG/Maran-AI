import asyncio
from .models import Mission, MissionStatus, Verification
from .policies import policy

async def _run_step(step):
    # Adapter boundary: real worker/tool implementations replace this body.
    await asyncio.sleep(0)
    step.status="completed"
    return step

async def execute_local(mission:Mission)->Mission:
    if mission.status==MissionStatus.waiting_approval:return mission
    semaphore=asyncio.Semaphore(policy.max_parallel)
    async def guarded(step):
        if step.requires_approval or step.status=="stopped": return
        async with semaphore:
            step.status="running"
            try:
                await asyncio.wait_for(_run_step(step),timeout=policy.step_timeout_seconds)
                mission.events.append({"type":"step_completed","step_id":step.id,"agent":step.agent})
            except asyncio.TimeoutError:
                step.status="failed";mission.events.append({"type":"step_timeout","step_id":step.id,"agent":step.agent})
    workers=[s for s in mission.plan if s.agent!="verifier"]
    await asyncio.gather(*(guarded(s) for s in workers))
    mission.status=MissionStatus.verifying
    verifier=next((s for s in mission.plan if s.agent=="verifier"),None)
    if verifier: await guarded(verifier)
    mission.verification=Verification.unverified
    mission.status=MissionStatus.completed
    mission.events.append({"type":"mission_completed","verification":mission.verification.value})
    return mission
