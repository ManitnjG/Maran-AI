"""External action connectors for Maran.

Every connector is opt-in through environment variables. No action silently falls
back to a mock. Callers should keep consequential actions behind mission/user
approval before invoking these functions.
"""
from __future__ import annotations

import base64
import json
import os
from email.message import EmailMessage
from typing import Any
from urllib.parse import quote

import httpx


class IntegrationError(RuntimeError):
    pass


def _set(*names: str) -> bool:
    return all(bool(os.getenv(n, "").strip()) for n in names)


def integration_status() -> dict[str, dict[str, Any]]:
    google = _set("GOOGLE_ACCESS_TOKEN")
    github = _set("GITHUB_TOKEN", "GITHUB_REPOSITORY")
    whatsapp = _set("WHATSAPP_ACCESS_TOKEN", "WHATSAPP_PHONE_NUMBER_ID")
    linkedin = _set("LINKEDIN_ACCESS_TOKEN", "LINKEDIN_AUTHOR_URN")
    twilio = _set("TWILIO_ACCOUNT_SID", "TWILIO_AUTH_TOKEN", "TWILIO_FROM_NUMBER")
    tally = _set("TALLY_URL")
    return {
        "gmail_send": {"status": "ready" if google else "needs_connection", "approval": True},
        "gmail_read": {"status": "ready" if google else "needs_connection", "approval": False},
        "gmail_reply": {"status": "ready" if google else "needs_connection", "approval": True},
        "google_calendar": {"status": "ready" if google else "needs_connection", "approval": True},
        "google_drive": {"status": "ready" if google else "needs_connection", "approval": True},
        "whatsapp_send": {"status": "ready" if whatsapp else "needs_connection", "approval": True},
        "whatsapp_read": {"status": "webhook_required", "approval": False},
        "instagram_post": {"status": "ready" if _set("META_ACCESS_TOKEN", "INSTAGRAM_USER_ID") else "needs_connection", "approval": True},
        "facebook_post": {"status": "ready" if _set("META_ACCESS_TOKEN", "FACEBOOK_PAGE_ID") else "needs_connection", "approval": True},
        "linkedin_post": {"status": "ready" if linkedin else "needs_connection", "approval": True},
        "youtube_publish": {"status": "connector_scaffold", "approval": True},
        "instagram_analytics": {"status": "ready" if _set("META_ACCESS_TOKEN", "INSTAGRAM_USER_ID") else "needs_connection", "approval": False},
        "social_analytics": {"status": "connector_scaffold", "approval": False},
        "gst_filing": {"status": "assisted_only", "approval": True, "note": "No GST portal/CAPTCHA automation."},
        "gst_portal_login": {"status": "unsupported", "approval": True},
        "tally_post": {"status": "ready" if tally else "needs_connection", "approval": True},
        "tally_company_connection": {"status": "ready" if tally else "needs_connection", "approval": False},
        "invoice_posting": {"status": "ready" if tally else "needs_connection", "approval": True},
        "bank_transactions": {"status": "read_only_connector_required", "approval": False},
        "payment_execution": {"status": "unsupported", "approval": True},
        "customer_calling": {"status": "ready" if twilio else "needs_connection", "approval": True},
        "sms_send": {"status": "ready" if twilio else "needs_connection", "approval": True},
        "crm": {"status": "built_in_lead_store", "approval": False},
        "github_actions": {"status": "ready" if github else "needs_connection", "approval": True},
        "github_code_push": {"status": "ready" if github else "needs_connection", "approval": True},
        "apk_from_phone": {"status": "ready_via_github_actions" if github else "needs_github_connection", "approval": True},
        "full_device_control": {"status": "unsupported", "approval": True},
        "background_wake_word": {"status": "foreground_service_only", "approval": False},
        "captcha_bypass": {"status": "unsupported", "approval": True},
        "otp_bypass": {"status": "unsupported", "approval": True},
    }


def _google_headers() -> dict[str, str]:
    token = os.getenv("GOOGLE_ACCESS_TOKEN", "").strip()
    if not token:
        raise IntegrationError("Google is not connected: set GOOGLE_ACCESS_TOKEN")
    return {"Authorization": f"Bearer {token}"}


async def gmail_send(to: str, subject: str, body: str) -> dict[str, Any]:
    msg = EmailMessage()
    msg["To"] = to
    msg["Subject"] = subject
    msg.set_content(body)
    raw = base64.urlsafe_b64encode(msg.as_bytes()).decode().rstrip("=")
    async with httpx.AsyncClient(timeout=30) as c:
        r = await c.post("https://gmail.googleapis.com/gmail/v1/users/me/messages/send", headers=_google_headers(), json={"raw": raw})
    if r.status_code >= 300:
        raise IntegrationError(f"Gmail send failed ({r.status_code}): {r.text[:300]}")
    return r.json()


async def gmail_list(max_results: int = 10, query: str = "") -> dict[str, Any]:
    max_results = min(max(max_results, 1), 50)
    params = {"maxResults": max_results}
    if query:
        params["q"] = query
    async with httpx.AsyncClient(timeout=30) as c:
        lr = await c.get("https://gmail.googleapis.com/gmail/v1/users/me/messages", headers=_google_headers(), params=params)
        if lr.status_code >= 300:
            raise IntegrationError(f"Gmail read failed ({lr.status_code}): {lr.text[:300]}")
        items = lr.json().get("messages", [])
        out = []
        for item in items:
            mr = await c.get(f"https://gmail.googleapis.com/gmail/v1/users/me/messages/{item['id']}", headers=_google_headers(), params={"format": "metadata", "metadataHeaders": ["From", "To", "Subject", "Date"]})
            if mr.status_code < 300:
                data = mr.json()
                headers = {h["name"].lower(): h["value"] for h in data.get("payload", {}).get("headers", [])}
                out.append({"id": data.get("id"), "threadId": data.get("threadId"), "snippet": data.get("snippet", ""), **headers})
    return {"messages": out}


async def gmail_reply(message_id: str, to: str, subject: str, body: str, thread_id: str | None = None) -> dict[str, Any]:
    msg = EmailMessage()
    msg["To"] = to
    msg["Subject"] = subject if subject.lower().startswith("re:") else f"Re: {subject}"
    msg["In-Reply-To"] = message_id
    msg["References"] = message_id
    msg.set_content(body)
    raw = base64.urlsafe_b64encode(msg.as_bytes()).decode().rstrip("=")
    payload: dict[str, Any] = {"raw": raw}
    if thread_id:
        payload["threadId"] = thread_id
    async with httpx.AsyncClient(timeout=30) as c:
        r = await c.post("https://gmail.googleapis.com/gmail/v1/users/me/messages/send", headers=_google_headers(), json=payload)
    if r.status_code >= 300:
        raise IntegrationError(f"Gmail reply failed ({r.status_code}): {r.text[:300]}")
    return r.json()


async def calendar_create(summary: str, start: str, end: str, timezone: str = "Asia/Kolkata", description: str = "") -> dict[str, Any]:
    payload = {"summary": summary, "description": description, "start": {"dateTime": start, "timeZone": timezone}, "end": {"dateTime": end, "timeZone": timezone}}
    async with httpx.AsyncClient(timeout=30) as c:
        r = await c.post("https://www.googleapis.com/calendar/v3/calendars/primary/events", headers={**_google_headers(), "Content-Type": "application/json"}, json=payload)
    if r.status_code >= 300:
        raise IntegrationError(f"Calendar create failed ({r.status_code}): {r.text[:300]}")
    return r.json()


async def drive_upload_text(name: str, content: str, mime_type: str = "text/plain") -> dict[str, Any]:
    boundary = "maran-boundary"
    meta = json.dumps({"name": name})
    body = (f"--{boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n{meta}\r\n"
            f"--{boundary}\r\nContent-Type: {mime_type}\r\n\r\n{content}\r\n--{boundary}--").encode()
    headers = {**_google_headers(), "Content-Type": f"multipart/related; boundary={boundary}"}
    async with httpx.AsyncClient(timeout=60) as c:
        r = await c.post("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,webViewLink", headers=headers, content=body)
    if r.status_code >= 300:
        raise IntegrationError(f"Drive upload failed ({r.status_code}): {r.text[:300]}")
    return r.json()



async def gmail_get(message_id: str) -> dict[str, Any]:
    async with httpx.AsyncClient(timeout=30) as client:
        r=await client.get(
            f"https://gmail.googleapis.com/gmail/v1/users/me/messages/{quote(message_id,safe='')}",
            headers=_google_headers(),
            params={"format":"metadata","metadataHeaders":["To","Subject","Date"]},
        )
    if r.status_code>=300:
        raise IntegrationError(f"Gmail verification failed ({r.status_code})")
    return r.json()


async def calendar_get(event_id: str) -> dict[str, Any]:
    async with httpx.AsyncClient(timeout=30) as client:
        r=await client.get(
            f"https://www.googleapis.com/calendar/v3/calendars/primary/events/{quote(event_id,safe='')}",
            headers=_google_headers(),
        )
    if r.status_code>=300:
        raise IntegrationError(f"Calendar verification failed ({r.status_code})")
    return r.json()


async def drive_get(file_id: str) -> dict[str, Any]:
    async with httpx.AsyncClient(timeout=30) as client:
        r=await client.get(
            f"https://www.googleapis.com/drive/v3/files/{quote(file_id,safe='')}?fields=id,name,trashed,webViewLink",
            headers=_google_headers(),
        )
    if r.status_code>=300:
        raise IntegrationError(f"Drive verification failed ({r.status_code})")
    return r.json()

def _github_headers() -> dict[str, str]:
    token = os.getenv("GITHUB_TOKEN", "").strip()
    if not token:
        raise IntegrationError("GitHub is not connected: set GITHUB_TOKEN")
    return {"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"}


async def github_dispatch(workflow: str = "android.yml", ref: str = "main", inputs: dict[str, str] | None = None) -> dict[str, Any]:
    repo = os.getenv("GITHUB_REPOSITORY", "").strip()
    if not repo:
        raise IntegrationError("Set GITHUB_REPOSITORY as owner/repo")
    url = f"https://api.github.com/repos/{repo}/actions/workflows/{quote(workflow, safe='')}/dispatches"
    async with httpx.AsyncClient(timeout=30) as c:
        r = await c.post(url, headers=_github_headers(), json={"ref": ref, "inputs": inputs or {}})
    if r.status_code not in (201, 204):
        raise IntegrationError(f"GitHub workflow dispatch failed ({r.status_code}): {r.text[:300]}")
    return {"ok": True, "repository": repo, "workflow": workflow, "ref": ref}


async def github_put_file(path: str, content: str, message: str, branch: str = "main", sha: str | None = None) -> dict[str, Any]:
    repo = os.getenv("GITHUB_REPOSITORY", "").strip()
    if not repo:
        raise IntegrationError("Set GITHUB_REPOSITORY as owner/repo")
    payload: dict[str, Any] = {"message": message, "content": base64.b64encode(content.encode()).decode(), "branch": branch}
    if sha:
        payload["sha"] = sha
    async with httpx.AsyncClient(timeout=30) as c:
        r = await c.put(f"https://api.github.com/repos/{repo}/contents/{quote(path, safe='/')}", headers=_github_headers(), json=payload)
    if r.status_code >= 300:
        raise IntegrationError(f"GitHub file update failed ({r.status_code}): {r.text[:300]}")
    return r.json()


async def whatsapp_send(to: str, body: str) -> dict[str, Any]:
    token, phone_id = os.getenv("WHATSAPP_ACCESS_TOKEN", ""), os.getenv("WHATSAPP_PHONE_NUMBER_ID", "")
    if not token or not phone_id:
        raise IntegrationError("WhatsApp Cloud API is not connected")
    payload = {"messaging_product": "whatsapp", "to": to, "type": "text", "text": {"body": body}}
    async with httpx.AsyncClient(timeout=30) as c:
        r = await c.post(f"https://graph.facebook.com/v23.0/{phone_id}/messages", headers={"Authorization": f"Bearer {token}"}, json=payload)
    if r.status_code >= 300:
        raise IntegrationError(f"WhatsApp send failed ({r.status_code}): {r.text[:300]}")
    return r.json()


async def facebook_post(message: str) -> dict[str, Any]:
    token, page = os.getenv("META_ACCESS_TOKEN", ""), os.getenv("FACEBOOK_PAGE_ID", "")
    if not token or not page:
        raise IntegrationError("Facebook Page is not connected")
    async with httpx.AsyncClient(timeout=30) as c:
        r = await c.post(f"https://graph.facebook.com/v23.0/{page}/feed", data={"message": message, "access_token": token})
    if r.status_code >= 300:
        raise IntegrationError(f"Facebook post failed ({r.status_code}): {r.text[:300]}")
    return r.json()


async def instagram_post(image_url: str, caption: str = "") -> dict[str, Any]:
    token, user_id = os.getenv("META_ACCESS_TOKEN", ""), os.getenv("INSTAGRAM_USER_ID", "")
    if not token or not user_id:
        raise IntegrationError("Instagram Professional account is not connected")
    async with httpx.AsyncClient(timeout=45) as c:
        create = await c.post(f"https://graph.facebook.com/v23.0/{user_id}/media", data={"image_url": image_url, "caption": caption, "access_token": token})
        if create.status_code >= 300:
            raise IntegrationError(f"Instagram media create failed ({create.status_code}): {create.text[:300]}")
        cid = create.json().get("id")
        publish = await c.post(f"https://graph.facebook.com/v23.0/{user_id}/media_publish", data={"creation_id": cid, "access_token": token})
    if publish.status_code >= 300:
        raise IntegrationError(f"Instagram publish failed ({publish.status_code}): {publish.text[:300]}")
    return publish.json()


async def linkedin_post(text: str) -> dict[str, Any]:
    token, author = os.getenv("LINKEDIN_ACCESS_TOKEN", ""), os.getenv("LINKEDIN_AUTHOR_URN", "")
    if not token or not author:
        raise IntegrationError("LinkedIn is not connected")
    payload = {"author": author, "lifecycleState": "PUBLISHED", "specificContent": {"com.linkedin.ugc.ShareContent": {"shareCommentary": {"text": text}, "shareMediaCategory": "NONE"}}, "visibility": {"com.linkedin.ugc.MemberNetworkVisibility": "PUBLIC"}}
    async with httpx.AsyncClient(timeout=30) as c:
        r = await c.post("https://api.linkedin.com/v2/ugcPosts", headers={"Authorization": f"Bearer {token}", "X-Restli-Protocol-Version": "2.0.0", "Content-Type": "application/json"}, json=payload)
    if r.status_code >= 300:
        raise IntegrationError(f"LinkedIn post failed ({r.status_code}): {r.text[:300]}")
    return {"ok": True, "id": r.headers.get("x-restli-id")}


async def twilio_sms(to: str, body: str) -> dict[str, Any]:
    sid, token, from_num = os.getenv("TWILIO_ACCOUNT_SID", ""), os.getenv("TWILIO_AUTH_TOKEN", ""), os.getenv("TWILIO_FROM_NUMBER", "")
    if not sid or not token or not from_num:
        raise IntegrationError("Twilio is not connected")
    async with httpx.AsyncClient(timeout=30, auth=(sid, token)) as c:
        r = await c.post(f"https://api.twilio.com/2010-04-01/Accounts/{sid}/Messages.json", data={"To": to, "From": from_num, "Body": body})
    if r.status_code >= 300:
        raise IntegrationError(f"SMS failed ({r.status_code}): {r.text[:300]}")
    data = r.json()
    return {"sid": data.get("sid"), "status": data.get("status")}


async def twilio_call(to: str, twiml: str) -> dict[str, Any]:
    sid, token, from_num = os.getenv("TWILIO_ACCOUNT_SID", ""), os.getenv("TWILIO_AUTH_TOKEN", ""), os.getenv("TWILIO_FROM_NUMBER", "")
    if not sid or not token or not from_num:
        raise IntegrationError("Twilio is not connected")
    async with httpx.AsyncClient(timeout=30, auth=(sid, token)) as c:
        r = await c.post(f"https://api.twilio.com/2010-04-01/Accounts/{sid}/Calls.json", data={"To": to, "From": from_num, "Twiml": twiml})
    if r.status_code >= 300:
        raise IntegrationError(f"Call failed ({r.status_code}): {r.text[:300]}")
    data = r.json()
    return {"sid": data.get("sid"), "status": data.get("status")}


async def tally_post_xml(xml: str) -> dict[str, Any]:
    url = os.getenv("TALLY_URL", "").strip()
    if not url:
        raise IntegrationError("Tally is not connected: set TALLY_URL, e.g. http://192.168.1.10:9000")
    async with httpx.AsyncClient(timeout=30) as c:
        r = await c.post(url, content=xml.encode(), headers={"Content-Type": "text/xml; charset=utf-8"})
    if r.status_code >= 300:
        raise IntegrationError(f"Tally post failed ({r.status_code}): {r.text[:300]}")
    return {"ok": True, "response": r.text[:5000]}
