"""MARAN action proposal/execution runtime.

The model may propose arguments, but code chooses the allowed tool set, validates
identifiers, checks connector readiness, and requires separate approval before
any external side effect.
"""
from __future__ import annotations

import json
import re
from datetime import datetime, timezone
from typing import Any

from .models import ActionIntent, Mission
from .integrations import (
    IntegrationError, integration_status, gmail_send, calendar_create,
    drive_upload_text, github_dispatch, github_put_file, whatsapp_send,
    facebook_post, instagram_post, linkedin_post, twilio_sms, twilio_call,
    tally_post_xml, gmail_get, calendar_get, drive_get,
)
from .router import ProviderError

TOOL_SCHEMAS: dict[str, dict[str, Any]] = {
    "gmail_send": {"required": ["to", "subject", "body"]},
    "google_calendar": {"required": ["summary", "start", "end"], "optional": ["timezone", "description"]},
    "google_drive": {"required": ["name", "content"], "optional": ["mime_type"]},
    "github_actions": {"required": [], "optional": ["workflow", "ref", "inputs"]},
    "github_code_push": {"required": ["path", "content", "message"], "optional": ["branch", "sha"]},
    "whatsapp_send": {"required": ["to", "body"]},
    "facebook_post": {"required": ["text"]},
    "instagram_post": {"required": ["image_url"], "optional": ["caption"]},
    "linkedin_post": {"required": ["text"]},
    "sms_send": {"required": ["to", "body"]},
    "customer_calling": {"required": ["to", "twiml"]},
    "tally_post": {"required": ["xml"]},
}

_HINTS = {
    "gmail_send": ("send email", "send mail", "email to", "mail to"),
    "google_calendar": ("calendar", "schedule meeting", "create event", "book meeting"),
    "google_drive": ("google drive", "upload to drive", "save to drive"),
    "github_actions": ("github action", "build apk", "run workflow", "trigger workflow"),
    "github_code_push": ("push to github", "update github", "commit to github", "github file"),
    "whatsapp_send": ("whatsapp", "send whatsapp"),
    "facebook_post": ("facebook post", "post on facebook"),
    "instagram_post": ("instagram post", "post on instagram"),
    "linkedin_post": ("linkedin post", "post on linkedin"),
    "sms_send": ("send sms", "text message"),
    "customer_calling": ("call customer", "make a call", "twilio call"),
    "tally_post": ("post to tally", "tally invoice", "tally voucher"),
}

def candidate_tools(objective: str) -> list[str]:
    q = objective.lower()
    return [tool for tool, hints in _HINTS.items() if any(h in q for h in hints)]

def _evidence_text(mission: Mission) -> str:
    parts = [mission.objective]
    for step in mission.plan:
        if step.output:
            parts.append(step.output[:5000])
        if step.evidence:
            parts.extend(step.evidence.get("sources", [])[:20])
            for record in step.evidence.get("records", [])[:20]:
                parts.append(json.dumps(record, ensure_ascii=False))
    return "\n".join(parts)

def _json_from(text: str) -> dict[str, Any]:
    cleaned = text.strip()
    fence = chr(96) * 3
    if cleaned.startswith(fence):
        cleaned = cleaned.removeprefix(fence + "json").removeprefix(fence).strip()
        if cleaned.endswith(fence):
            cleaned = cleaned[:-3].strip()
    start, end = cleaned.find("{"), cleaned.rfind("}")
    if start < 0 or end < start:
        raise ValueError("No JSON object returned")
    value = json.loads(cleaned[start:end + 1])
    if not isinstance(value, dict):
        raise ValueError("Action proposal must be an object")
    return value

def _bounded_args(tool_id: str, args: Any) -> dict[str, Any]:
    if not isinstance(args, dict):
        raise ValueError("Action arguments must be an object")
    schema = TOOL_SCHEMAS[tool_id]
    allowed = set(schema.get("required", [])) | set(schema.get("optional", []))
    out = {k: v for k, v in args.items() if k in allowed}
    for key in schema.get("required", []):
        if key not in out or out[key] in (None, "", [], {}):
            raise ValueError(f"Missing required argument: {key}")
    if len(json.dumps(out, ensure_ascii=False)) > 600_000:
        raise ValueError("Action payload is too large")
    return out

def _external_identifier_is_grounded(tool_id: str, args: dict[str, Any], mission: Mission) -> bool:
    haystack = _evidence_text(mission).lower()
    if tool_id == "gmail_send":
        target = str(args.get("to", "")).strip().lower()
        return bool(re.fullmatch(r"[^@\s]+@[^@\s]+\.[^@\s]+", target) and target in haystack)
    if tool_id in {"whatsapp_send", "sms_send", "customer_calling"}:
        digits = re.sub(r"\D", "", str(args.get("to", "")))
        source_digits = re.sub(r"\D", "", haystack)
        return len(digits) >= 7 and digits in source_digits
    if tool_id == "instagram_post":
        url = str(args.get("image_url", "")).strip()
        return url.startswith(("https://", "http://")) and url.lower() in haystack
    return True

async def propose_actions(mission: Mission, router) -> list[ActionIntent]:
    candidates = candidate_tools(mission.objective)
    if not candidates:
        return []
    schemas = {k: TOOL_SCHEMAS[k] for k in candidates}
    outputs = "\n\n".join(
        f"{s.agent}: {s.output[:6000]}" for s in mission.plan if s.output and s.agent != "verifier"
    )[:16000]
    prompt = (
        "You are MARAN's action planner. Return JSON only.\n"
        "The application has chosen the ONLY allowed tool ids below. "
        "Never invent a tool, credential, recipient, phone number, account id, URL, date or amount. "
        "Use only information explicitly present in the objective/evidence/drafts. "
        "If required details are missing, return {\"actions\":[]}.\n"
        f"Objective: {mission.objective}\nAllowed schemas: {json.dumps(schemas)}\n"
        f"Worker drafts:\n{outputs}\n"
        'Format: {"actions":[{"tool_id":"...","title":"...","reason":"...","args":{...}}]}'
    )
    try:
        raw, _ = await router.complete(prompt)
        value = _json_from(raw)
    except (ProviderError, ValueError, json.JSONDecodeError):
        return []
    readiness = integration_status()
    intents: list[ActionIntent] = []
    for item in value.get("actions", [])[:3]:
        if not isinstance(item, dict):
            continue
        tool_id = str(item.get("tool_id", ""))
        if tool_id not in candidates or tool_id not in TOOL_SCHEMAS:
            continue
        try:
            args = _bounded_args(tool_id, item.get("args", {}))
        except ValueError:
            continue
        if not _external_identifier_is_grounded(tool_id, args, mission):
            continue
        meta = readiness.get(tool_id, {})
        intents.append(ActionIntent(
            tool_id=tool_id,
            title=str(item.get("title") or tool_id.replace("_", " ").title())[:300],
            reason=str(item.get("reason") or "Requested by the mission")[:1000],
            args=args,
            status="waiting_approval",
            connection_status=str(meta.get("status", "unknown")),
            requires_approval=True,
        ))
    return intents

async def _verify_side_effect(tool_id: str, result: dict[str, Any]) -> str:
    try:
        if tool_id=="gmail_send" and result.get("id"):
            check=await gmail_get(str(result["id"]))
            return "provider_verified" if check.get("id")==result.get("id") else "provider_acknowledged"
        if tool_id=="google_calendar" and result.get("id"):
            check=await calendar_get(str(result["id"]))
            return "provider_verified" if check.get("id")==result.get("id") else "provider_acknowledged"
        if tool_id=="google_drive" and result.get("id"):
            check=await drive_get(str(result["id"]))
            return "provider_verified" if check.get("id")==result.get("id") and not check.get("trashed",False) else "provider_acknowledged"
    except IntegrationError:
        pass
    return "provider_acknowledged"

async def execute_action(intent: ActionIntent) -> ActionIntent:
    if not intent.approved:
        raise IntegrationError("Action is not approved")
    if intent.tool_id not in TOOL_SCHEMAS:
        raise IntegrationError("Unknown action tool")
    status = integration_status().get(intent.tool_id, {}).get("status", "unknown")
    if status != "ready":
        intent.status = "needs_connection"
        intent.connection_status = status
        intent.error = f"{intent.tool_id} is not connected ({status})"
        intent.updated_at = datetime.now(timezone.utc)
        return intent
    a = intent.args
    intent.status = "executing"
    intent.attempts += 1
    intent.updated_at = datetime.now(timezone.utc)
    try:
        if intent.tool_id == "gmail_send":
            result = await gmail_send(a["to"], a["subject"], a["body"])
        elif intent.tool_id == "google_calendar":
            result = await calendar_create(a["summary"], a["start"], a["end"], a.get("timezone", "Asia/Kolkata"), a.get("description", ""))
        elif intent.tool_id == "google_drive":
            result = await drive_upload_text(a["name"], a["content"], a.get("mime_type", "text/plain"))
        elif intent.tool_id == "github_actions":
            result = await github_dispatch(a.get("workflow", "android.yml"), a.get("ref", "main"), a.get("inputs", {}))
        elif intent.tool_id == "github_code_push":
            result = await github_put_file(a["path"], a["content"], a["message"], a.get("branch", "main"), a.get("sha"))
        elif intent.tool_id == "whatsapp_send":
            result = await whatsapp_send(a["to"], a["body"])
        elif intent.tool_id == "facebook_post":
            result = await facebook_post(a["text"])
        elif intent.tool_id == "instagram_post":
            result = await instagram_post(a["image_url"], a.get("caption", ""))
        elif intent.tool_id == "linkedin_post":
            result = await linkedin_post(a["text"])
        elif intent.tool_id == "sms_send":
            result = await twilio_sms(a["to"], a["body"])
        elif intent.tool_id == "customer_calling":
            result = await twilio_call(a["to"], a["twiml"])
        elif intent.tool_id == "tally_post":
            result = await tally_post_xml(a["xml"])
        else:
            raise IntegrationError("Tool is not executable")
        intent.result = result if isinstance(result, dict) else {"result": result}
        intent.status = "completed"
        intent.verification = await _verify_side_effect(intent.tool_id,intent.result)
        intent.error = None
    except IntegrationError as exc:
        intent.status = "failed"
        intent.verification = "failed"
        intent.error = str(exc)
    intent.updated_at = datetime.now(timezone.utc)
    return intent
