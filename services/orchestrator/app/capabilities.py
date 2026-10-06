import asyncio
import os
from .providers import configured_router
from .web_tools import research
from .integrations import integration_status


def capabilities():
    models = [p.provider.name for p in configured_router().providers]
    return {
        "version": "0.6.0", "models": models, "ai_engine": "provider_router",
        "brain_stages": ["planning", "worker_assignment", "tool_execution", "observe", "verification", "retry", "skill_learning"],
        "access_tested": False,
        "web_enabled": os.getenv("MARAN_WEB_ENABLED", "true").lower() == "true",
        "authentication_enabled": bool(os.getenv("MARAN_ACCESS_TOKEN")),
        "storage": os.getenv("MARAN_STORAGE_MODE", "local_sqlite"),
        "integrations": integration_status(),
        "features": {
            "public_research": "available" if os.getenv("MARAN_WEB_ENABLED", "true").lower() == "true" else "disabled",
            "draft_generation": "needs_authorized_opencode_access",
            "mission_planning": "opencode",
            "autonomous_runtime": "bounded_safe_cycles",
            "persistent_memory": "local_sqlite_non_secret",
            "learned_skills": "local_sqlite_successful_workflows",
            "approval_policy": "deterministic_application_enforced",
            "output_review": "opencode",
            "tally_voucher_export": "available_for_review",
            "tally_writeback": "not_connected", "gst_filing": "not_connected",
            "social_publishing": "not_connected", "wake_word": "not_implemented",
        },
        "note": "Autonomy is bounded and permission-aware. Public web research can run automatically. Consequential integrations still require explicit confirmation, and CAPTCHA/OTP/password/PIN/CVV/biometric/security prompts are never automated. OpenCode is first by default; optional OpenRouter or local Ollama fallbacks are used only when explicitly configured."
    }


async def diagnostics():
    async def model():
        try:
            _, name = await asyncio.wait_for(configured_router().complete("Reply with OK only."), 45)
            return {"ok": True, "message": "Model responded", "provider": name}
        except Exception:
            return {"ok": False, "message": "No configured model provider responded"}
    async def web():
        try:
            result = await asyncio.wait_for(research("Tamil Nadu Tourism official website"), 45)
            return {"ok": True, "message": "Public search responded", "source_count": len(result['sources'])}
        except Exception:
            return {"ok": False, "message": "Public search unavailable or rate limited"}
    m, w = await asyncio.gather(model(), web())
    return {"model": m, "web": w}
