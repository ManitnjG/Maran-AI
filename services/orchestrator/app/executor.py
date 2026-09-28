import asyncio
from .models import Mission, MissionStatus, Verification
from .policies import policy
from .worker_factory import factory

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
            for attempt in range(policy.max_retries+1):
                try:
                    await asyncio.wait_for(_run_step(step),timeout=policy.step_timeout_seconds)
                    mission.events.append({"type":"step_completed","step_id":step.id,"agent":step.agent,"attempt":attempt+1})
                    break
                except (asyncio.TimeoutError,Exception) as exc:
                    if attempt<policy.max_retries:
                        mission.events.append({"type":"step_retry","step_id":step.id,"agent":step.agent,"attempt":attempt+1})
                        await asyncio.sleep(min(2**attempt,4))
                    else:
                        step.status="failed";mission.events.append({"type":"step_failed","step_id":step.id,"agent":step.agent,"error":type(exc).__name__})
    workers=[s for s in mission.plan if s.agent!="verifier"]
    await asyncio.gather(*(guarded(s) for s in workers))
    mission.status=MissionStatus.verifying
    verifier=next((s for s in mission.plan if s.agent=="verifier"),None)
    if verifier: await guarded(verifier)
    failed=[s.id for s in mission.plan if s.status=="failed"]
    completed=[s.id for s in mission.plan if s.status=="completed"]
    mission.result={"summary":"Mission workflow completed","completed_steps":completed,"failed_steps":failed,"note":"Worker tool adapters determine substantive output."}
    mission.verification=Verification.partial if completed and not failed else Verification.unverified
    mission.status=MissionStatus.completed if not failed else MissionStatus.failed
    mission.events.append({"type":"mission_completed","verification":mission.verification.value})
    released=[]
    for aid in mission.assigned_agents:
        if factory.is_temporary(aid) and factory.stop(aid):released.append(aid)
    if released:mission.events.append({"type":"temporary_workers_released","agents":released})
    return mission
