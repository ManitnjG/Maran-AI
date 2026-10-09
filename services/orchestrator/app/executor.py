import asyncio
import re
from .models import Mission, MissionStatus, Verification
from .policies import policy
from .providers import configured_router
from .router import ProviderError
from .context import context_vault
from .web_tools import research, deep_research, fetch_page, ToolError
from .opencode_brain import plan_mission, worker_prompt, review_mission
from .action_runtime import propose_actions
from .knowledge import knowledge_store


async def execute_local(mission: Mission, checkpoint=None) -> Mission:
    """Generate reviewable drafts. No business-system action is implied by a draft."""
    if mission.status in (MissionStatus.waiting_approval, MissionStatus.cancelled, MissionStatus.completed):
        return mission
    router = configured_router()
    semaphore = asyncio.Semaphore(max(1, policy.max_parallel))
    mission.status = MissionStatus.running
    research_task = None

    def save():
        if checkpoint:
            checkpoint(mission)

    # Existing completed/stopped work is never replanned on retry.
    fresh = not any(s.status in ("completed", "stopped") for s in mission.plan)
    if fresh and not any(e.get("type") == "opencode_plan_created" for e in mission.events):
        mission.status = MissionStatus.planning
        save()
        try:
            await asyncio.wait_for(plan_mission(mission, router), timeout=policy.step_timeout_seconds)
        except (ProviderError, asyncio.TimeoutError) as exc:
            mission.status = MissionStatus.blocked
            mission.verification = Verification.unverified
            reason = str(exc) or "OpenCode planning timed out."
            mission.result = {"summary": "OpenCode planning blocked", "note": reason,
                              "completed_steps": [], "blocked_steps": [s.id for s in mission.plan]}
            for step in mission.plan:
                step.status, step.error = "blocked", reason
                if step.agent != "verifier": step.attempts += 1
            save()
            return mission
        if any(s.requires_approval and not s.approved for s in mission.plan):
            mission.status = MissionStatus.waiting_approval
            mission.events.append({"type":"approval_required","reason":"Review OpenCode's new plan"})
            save()
            return mission
    mission.status = MissionStatus.running
    save()

    async def guarded(step):
        nonlocal research_task
        if step.status in ("completed", "stopped"):
            return
        if step.risk_level == "blocked":
            step.status = "blocked"
            step.error = step.approval_reason or "Protected action cannot be automated"
            mission.events.append({"type":"step_blocked","step_id":step.id,"reason":"protected_action"})
            save()
            return
        if step.requires_approval and not step.approved:
            step.status = "blocked"
            step.error = step.approval_reason or "Approval required"
            return
        max_attempts = max(1, policy.max_retries + 1)
        if step.attempts >= max_attempts:
            step.status = "blocked"
            step.error = "Retry limit reached"
            mission.events.append({"type":"step_blocked","step_id":step.id,"reason":"retry_limit"})
            save()
            return
        async with semaphore:
            step.attempts += 1
            step.status = "running"
            mission.events.append({"type":"step_started","step_id":step.id,"attempt":step.attempts})
            save()
            try:
                if step.agent in ("research", "tour_leads", "itinerary", "seo", "marketing"):
                    if research_task is None:
                        urls = re.findall(r"https?://[^\s<>]+", mission.objective)
                        operation = fetch_page(urls[0].rstrip(".,)")) if urls else deep_research(mission.objective)
                        research_task = asyncio.create_task(operation)
                    evidence = await asyncio.wait_for(asyncio.shield(research_task), timeout=policy.step_timeout_seconds)
                    step.evidence = evidence
                    output, provider = await asyncio.wait_for(
                        router.complete(worker_prompt(mission, step, evidence)), timeout=policy.step_timeout_seconds)
                elif step.agent == "knowledge":
                    matches=knowledge_store.search(mission.objective,mission.workspace_id,ai_only=True,limit=6)
                    if not matches:
                        raise ToolError("No matching knowledge file is available for AI use. Upload a file and enable AI context for it.")
                    evidence={"records":matches,"sources":[],"verification":"user_document_retrieved"}
                    step.evidence=evidence
                    output, provider = await asyncio.wait_for(
                        router.complete(worker_prompt(mission, step, evidence)), timeout=policy.step_timeout_seconds)
                else:
                    output, provider = await asyncio.wait_for(
                        router.complete(worker_prompt(mission, step)), timeout=policy.step_timeout_seconds)
                step.output, step.provider, step.error = output, provider, None
                step.status = "completed"
                context_vault.put(mission.id, step.id, {"output": output, "provider": provider, "evidence": step.evidence})
                mission.events.append({"type": "step_completed", "step_id": step.id, "provider": provider})
            except ToolError as exc:
                step.status = "blocked"
                step.error = str(exc)
                mission.events.append({"type": "step_blocked", "step_id": step.id, "reason": "web_unavailable"})
            except (ProviderError, asyncio.TimeoutError) as exc:
                step.status = "blocked"
                step.error = str(exc) or "OpenCode request timed out. Check authorized OpenCode access, then retry."
                mission.events.append({"type": "step_blocked", "step_id": step.id, "reason": type(exc).__name__})
            finally:
                save()

    try:
        await asyncio.gather(*(guarded(s) for s in mission.plan if s.agent != "verifier"))
    except asyncio.CancelledError:
        if research_task and not research_task.done():
            research_task.cancel()
            await asyncio.gather(research_task, return_exceptions=True)
        for step in mission.plan:
            if step.status == "running":
                step.status = "pending"
        save()
        raise
    if research_task and not research_task.done():
        research_task.cancel()
        await asyncio.gather(research_task, return_exceptions=True)
    completed = [s.id for s in mission.plan if s.agent != "verifier" and s.status == "completed"]
    blocked = [s.id for s in mission.plan if s.agent != "verifier" and s.status != "completed"]
    for step in mission.plan:
        if step.agent == "verifier" and step.status != "stopped":
            if completed and not blocked:
                max_attempts = max(1, policy.max_retries + 1)
                if step.attempts >= max_attempts:
                    step.status, step.error = "blocked", "Verifier retry limit reached"
                    blocked.append(step.id)
                    continue
                mission.status = MissionStatus.verifying
                step.attempts += 1
                mission.events.append({"type":"verification_started","step_id":step.id,"attempt":step.attempts})
                save()
                try:
                    step.output, step.provider = await asyncio.wait_for(
                        review_mission(mission, router), timeout=policy.step_timeout_seconds)
                    step.status, step.error = "completed", None
                    mission.events.append({"type":"verification_completed","step_id":step.id,"provider":step.provider})
                except (ProviderError, asyncio.TimeoutError) as exc:
                    step.status, step.error = "blocked", str(exc) or "OpenCode review timed out."
                    blocked.append(step.id)
            else:
                step.status = "blocked"
                step.error = "Waiting for worker drafts before OpenCode review."
    mission.verification = Verification.partial if any(s.evidence for s in mission.plan) else Verification.unverified

    if not blocked and not mission.actions:
        mission.actions = await propose_actions(mission, router)
        if mission.actions:
            mission.status = MissionStatus.waiting_approval
            mission.result = {
                "summary": "Drafts are ready. External action is waiting for your approval.",
                "completed_steps": completed,
                "blocked_steps": [],
                "failed_steps": [],
                "note": "No external action has run yet. Review the exact tool and arguments in Approval Centre."
            }
            mission.events.append({
                "type": "action_approval_required",
                "action_ids": [a.id for a in mission.actions],
                "count": len(mission.actions),
            })
            save()
            return mission

    mission.status = MissionStatus.blocked if blocked else MissionStatus.completed
    mission.result = {
        "summary": "Mission needs configuration or missing steps" if blocked else "Results ready for review",
        "completed_steps": completed, "blocked_steps": blocked, "failed_steps": [],
        "note": "No unapproved external action was performed. Research evidence and AI drafts still need normal human review."
    }
    mission.events.append({"type": "mission_blocked" if blocked else "mission_completed", "verification": mission.verification.value})
    save()
    return mission
