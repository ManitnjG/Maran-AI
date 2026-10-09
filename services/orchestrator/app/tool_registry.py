from dataclasses import dataclass, asdict
from .integrations import integration_status

@dataclass(frozen=True)
class ToolSpec:
    id: str
    permission: str
    side_effect: str
    approval: bool
    available_status: str

_READ_TOOLS = (
    ToolSpec("public_web_search","read_public_web","none",False,"available"),
    ToolSpec("public_web_page","read_public_web","none",False,"available"),
    ToolSpec("mission_memory","read_workspace_memory","none",False,"available"),
    ToolSpec("skill_registry","read_learned_skills","none",False,"available"),
    ToolSpec("mission_context","read_outputs","none",False,"available"),
    ToolSpec("knowledge_search","read_user_knowledge","none",False,"available"),
)

def registry():
    out=[asdict(t) for t in _READ_TOOLS]
    for tool_id,meta in integration_status().items():
        out.append({
            "id":tool_id,
            "permission":"external_integration",
            "side_effect":"external" if meta.get("approval") else "read",
            "approval":bool(meta.get("approval")),
            "available_status":meta.get("status","unknown"),
        })
    return out

def tool_for_agent(agent_id: str) -> str | None:
    if agent_id in {"research","tour_leads","itinerary","seo","marketing"}:
        return "public_web_search"
    if agent_id == "knowledge":
        return "knowledge_search"
    if agent_id == "verifier":
        return "mission_context"
    return None
