import asyncio
from .models import Mission, MissionStatus, Verification
from .policies import policy
from .providers import configured_router
from .router import ProviderError
from .context import context_vault


async def execute_local(mission: Mission, checkpoint=None) -> Mission:
    """Generate reviewable drafts. No business-system action is implied by a draft."""
    if mission.status in (MissionStatus.waiting_approval, MissionStatus.cancelled, MissionStatus.completed):
        return mission
    router = configured_router()
    semaphore = asyncio.Semaphore(max(1, policy.max_parallel))
    mission.status = MissionStatus.running

    def save():
        if checkpoint:
            checkpoint(mission)

    async def guarded(step):
        if step.status in ("completed", "stopped"):
            return
        if step.requires_approval and not step.approved:
            step.status = "blocked"
            step.error = "Approval required"
            return
        async with semaphore:
            step.status = "running"
            save()
            try:
                prompt = (
                    f"You are MARAN's {step.agent} worker. Produce a useful draft for this objective:\n"
                    f"{mission.objective}\nOnly draft content; do not claim to execute actions, browse, "
                    "verify live facts, contact people, file GST, or write to Tally. State missing inputs "
                    "and mark unsupported facts as unverified. Never invent leads or contact details."
                )
                output, provider = await asyncio.wait_for(router.complete(prompt), timeout=policy.step_timeout_seconds)
                step.output, step.provider, step.error = output, provider, None
                step.status = "completed"
                context_vault.put(mission.id, step.id, {"output": output, "provider": provider})
                mission.events.append({"type": "step_completed", "step_id": step.id, "provider": provider})
            except (ProviderError, asyncio.TimeoutError) as exc:
                step.status = "blocked"
                step.error = "No model is available. Configure a provider or a local Ollama server, then retry."
                mission.events.append({"type": "step_blocked", "step_id": step.id, "reason": type(exc).__name__})
            finally:
                save()

    try:
        await asyncio.gather(*(guarded(s) for s in mission.plan if s.agent != "verifier"))
    except asyncio.CancelledError:
        for step in mission.plan:
            if step.status == "running":
                step.status = "pending"
        save()
        raise
    completed = [s.id for s in mission.plan if s.agent != "verifier" and s.status == "completed"]
    blocked = [s.id for s in mission.plan if s.agent != "verifier" and s.status != "completed"]
    for step in mission.plan:
        if step.agent == "verifier" and step.status != "stopped":
            step.status = "completed" if completed and not blocked else "blocked"
            step.output = "Draft output only. No live sources or external actions have been independently verified."
    mission.verification = Verification.unverified
    mission.status = MissionStatus.blocked if blocked else MissionStatus.completed
    mission.result = {
        "summary": "Mission needs configuration or missing steps" if blocked else "Draft ready for review",
        "completed_steps": completed, "blocked_steps": blocked, "failed_steps": [],
        "note": "No external actions were performed. Drafts are not verified facts or completed business transactions."
    }
    mission.events.append({"type": "mission_blocked" if blocked else "mission_completed", "verification": "unverified"})
    save()
    return mission
