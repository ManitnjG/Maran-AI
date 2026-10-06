import asyncio
from datetime import datetime, timezone
from .executor import execute_local
from .manager import manager
from .models import MissionStatus
from .policies import policy
from .skills import skill_registry
from .memory import memory_store

_TERMINAL = {MissionStatus.completed, MissionStatus.cancelled, MissionStatus.failed}

def _event(mission, event_type: str, **data):
    mission.events.append({
        "type": event_type,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        **data,
    })

async def run_autonomous(mission, checkpoint=None):
    """Bounded observe/execute/verify/retry loop.

    The loop never overrides approval gates or protected-action policy. It only
    retries steps that remain eligible under deterministic application rules.
    """
    if mission.status in _TERMINAL:
        return mission
    if not mission.autonomy_enabled:
        return await execute_local(mission, checkpoint=checkpoint)

    def save():
        if checkpoint:
            checkpoint(mission)

    max_attempts = max(1, policy.max_retries + 1)
    start_cycle = max(0, mission.cycle)
    for cycle in range(start_cycle + 1, mission.max_cycles + 1):
        mission.cycle = cycle
        _event(mission, "autonomy_cycle_started", cycle=cycle, max_cycles=mission.max_cycles)
        save()

        before = [(s.id, s.status, s.attempts, bool(s.output)) for s in mission.plan]
        mission = await execute_local(mission, checkpoint=checkpoint)

        if mission.status == MissionStatus.completed:
            skill = skill_registry.learn(mission)
            if skill:
                mission.learned_skill_id = skill["id"]
                memory_store.set_system(mission.workspace_id, "last_learned_skill", skill["name"])
                memory_store.set_system(
                    mission.workspace_id, "last_successful_workers", ", ".join(skill["agents"])
                )
                _event(mission, "skill_learned", skill_id=skill["id"], name=skill["name"])
            _event(mission, "autonomy_finished", cycle=cycle, outcome="completed")
            save()
            return mission

        if mission.status in (MissionStatus.waiting_approval, MissionStatus.cancelled):
            _event(
                mission, "autonomy_paused", cycle=cycle,
                reason="approval_required" if mission.status == MissionStatus.waiting_approval else "cancelled"
            )
            save()
            return mission

        if mission.status != MissionStatus.blocked:
            _event(mission, "autonomy_finished", cycle=cycle, outcome=mission.status.value)
            save()
            return mission

        retriable = [
            s for s in mission.plan
            if s.status == "blocked" and s.risk_level != "blocked" and s.attempts < max_attempts
        ]
        if not retriable:
            _event(mission, "autonomy_exhausted", cycle=cycle, reason="no_retriable_steps")
            save()
            return mission

        after = [(s.id, s.status, s.attempts, bool(s.output)) for s in mission.plan]
        _event(
            mission, "autonomy_retry", cycle=cycle,
            steps=[s.id for s in retriable], progress=before != after
        )
        manager.recover(mission)
        for step in retriable:
            step.status = "pending"
            step.error = None
        mission.status = MissionStatus.running
        save()
        await asyncio.sleep(0)

    if mission.status not in _TERMINAL:
        mission.status = MissionStatus.blocked
        _event(mission, "autonomy_exhausted", cycle=mission.cycle, reason="max_cycles_reached")
        save()
    return mission
