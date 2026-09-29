import asyncio
import os
from .providers import configured_router
from .web_tools import research


def capabilities():
    models = [p.provider.name for p in configured_router().providers]
    return {
        "version": "0.5.0", "models": models, "ai_engine": "OpenCode Zen",
        "brain_stages": ["planning", "worker_assignment", "drafts", "research_synthesis", "review"],
        "access_tested": False,
        "web_enabled": os.getenv("MARAN_WEB_ENABLED", "true").lower() == "true",
        "authentication_enabled": bool(os.getenv("MARAN_ACCESS_TOKEN")),
        "storage": os.getenv("MARAN_STORAGE_MODE", "local_sqlite"),
        "features": {
            "public_research": "available" if os.getenv("MARAN_WEB_ENABLED", "true").lower() == "true" else "disabled",
            "draft_generation": "needs_authorized_opencode_access",
            "mission_planning": "opencode",
            "output_review": "opencode",
            "tally_voucher_export": "available_for_review",
            "tally_writeback": "not_connected", "gst_filing": "not_connected",
            "social_publishing": "not_connected", "wake_word": "not_implemented",
        },
        "note": "All AI reasoning uses OpenCode Zen, free models only. Access is not guaranteed; run connection checks. Web queries are sent to Exa; keyless usage is rate limited."
    }


async def diagnostics():
    async def model():
        try:
            _, name = await asyncio.wait_for(configured_router().complete("Reply with OK only."), 45)
            return {"ok": True, "message": "Model responded", "provider": name}
        except Exception:
            return {"ok": False, "message": "OpenCode did not respond; check authorized OpenCode access"}
    async def web():
        try:
            result = await asyncio.wait_for(research("Tamil Nadu Tourism official website"), 45)
            return {"ok": True, "message": "Public search responded", "source_count": len(result['sources'])}
        except Exception:
            return {"ok": False, "message": "Public search unavailable or rate limited"}
    m, w = await asyncio.gather(model(), web())
    return {"model": m, "web": w}
