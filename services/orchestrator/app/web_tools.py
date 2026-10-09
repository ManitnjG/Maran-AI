"""Keyless, rate-limited public research through Exa's documented hosted MCP."""
import json
import os
import re
import ipaddress
from urllib.parse import urlsplit
from datetime import datetime, timezone
import httpx

ENDPOINT = "https://mcp.exa.ai/mcp"
MAX_BYTES = 750_000

class ToolError(Exception):
    pass


def public_url(value: str) -> str:
    try:
        u = urlsplit(value)
        host = (u.hostname or "").lower()
        if u.scheme not in ("http", "https") or not host or u.username or u.password:
            raise ValueError()
        if u.port not in (None, 80, 443) or "." not in host or host.endswith((".local", ".localhost", ".internal")):
            raise ValueError()
        try:
            address = ipaddress.ip_address(host)
        except ValueError:
            address = None
        if address is not None and not address.is_global:
            raise ValueError()
        return value
    except (ValueError, TypeError):
        raise ToolError("Only public HTTP(S) URLs without credentials are supported") from None


def decode_rpc(text: str, request_id: int) -> dict:
    candidates = [text]
    if text.lstrip().startswith(("event:", "data:", ":")):
        candidates = ["\n".join(line[5:].lstrip() for line in block.splitlines() if line.startswith("data:"))
                      for block in text.replace("\r\n", "\n").split("\n\n")]
    for candidate in candidates:
        try:
            value = json.loads(candidate)
        except (ValueError, TypeError):
            continue
        if isinstance(value, dict) and value.get("id") == request_id:
            if "error" in value:
                raise ToolError("Search provider rejected the request")
            if isinstance(value.get("result"), dict):
                return value["result"]
    raise ToolError("Search provider returned an invalid response")


async def _rpc(client, payload):
    async with client.stream("POST", ENDPOINT, json=payload) as response:
        if response.status_code == 429:
            raise ToolError("Search limit reached. Retry later; no results were fabricated.")
        if response.status_code >= 400:
            raise ToolError(f"Search unavailable (HTTP {response.status_code})")
        chunks, size = [], 0
        async for chunk in response.aiter_bytes():
            size += len(chunk)
            if size > MAX_BYTES:
                raise ToolError("Search response exceeded the size limit")
            chunks.append(chunk)
        return b"".join(chunks).decode("utf-8", errors="replace"), response.headers


async def call_exa(tool: str, arguments: dict) -> dict:
    if os.getenv("MARAN_WEB_ENABLED", "true").lower() != "true":
        raise ToolError("Public web tools are disabled on this server")
    headers = {"Accept": "application/json, text/event-stream"}
    if os.getenv("EXA_API_KEY"):
        headers["x-api-key"] = os.environ["EXA_API_KEY"]
    try:
        async with httpx.AsyncClient(timeout=35, headers=headers) as client:
            raw, response_headers = await _rpc(client, {
                "jsonrpc": "2.0", "id": 1, "method": "initialize",
                "params": {"protocolVersion": "2024-11-05", "capabilities": {},
                           "clientInfo": {"name": "maran-ai", "version": "0.3.0"}}})
            initialized = decode_rpc(raw, 1)
            client.headers["MCP-Protocol-Version"] = initialized.get("protocolVersion", "2024-11-05")
            if response_headers.get("mcp-session-id"):
                client.headers["Mcp-Session-Id"] = response_headers["mcp-session-id"]
            await _rpc(client, {"jsonrpc": "2.0", "method": "notifications/initialized"})
            raw, _ = await _rpc(client, {"jsonrpc": "2.0", "id": 2, "method": "tools/call",
                                        "params": {"name": tool, "arguments": arguments}})
            result = decode_rpc(raw, 2)
            if result.get("isError"):
                raise ToolError("Search tool could not complete this request; retry later")
            return result
    except (httpx.HTTPError, ImportError) as exc:
        raise ToolError("Search connection unavailable") from exc


def normalize(result: dict) -> dict:
    text = "\n\n".join(item.get("text", "") for item in result.get("content", []) if item.get("type") == "text")
    if not text.strip():
        raise ToolError("Search returned no readable results")
    sources = []
    for match in re.findall(r"https?://[^\s<>\]\)\"]+", text):
        url = match.rstrip(".,;")
        try: public_url(url)
        except ToolError: continue
        if url not in sources: sources.append(url)
    if not sources:
        raise ToolError("Search response contained no source URLs")
    records = []
    for block in re.split(r"(?m)(?=^Title: )", text):
        url_match = re.search(r"(?m)^URL: (https?://\S+)", block)
        if not url_match: continue
        url = url_match.group(1)
        if url not in sources: continue
        title = re.search(r"(?m)^Title: (.+)", block)
        emails = sorted(set(re.findall(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", block)))
        if not any(r["url"] == url for r in records):
            records.append({"title": title.group(1) if title else url, "url": url,
                            "published_emails": emails[:10], "status": "unqualified_prospect"})
    return {"text": text[:30000], "sources": sources[:20], "records": records[:20],
            "retrieved_at": datetime.now(timezone.utc).isoformat(),
            "verification": "source_retrieved", "provider": "exa",
            "note": "Source retrieval does not verify a claim, contact availability, or buying intent."}


async def research(query: str) -> dict:
    return normalize(await call_exa("web_search_exa", {"query": query, "numResults": 5, "objective": "Find relevant public primary sources for the query and include source URLs. Do not infer buying intent."}))


async def fetch_page(url: str) -> dict:
    # The backend calls only the fixed Exa endpoint, never a user-supplied host.
    return normalize(await call_exa("web_fetch_exa", {"urls": [public_url(url)], "maxCharacters": 12000}))


def _merge_results(items: list[dict]) -> dict:
    if not items:
        raise ToolError("Research returned no results")
    sources, records, texts = [], [], []
    for item in items:
        if item.get("text"):
            texts.append(item["text"])
        for url in item.get("sources", []):
            if url not in sources:
                sources.append(url)
        for record in item.get("records", []):
            url = record.get("url")
            if url and not any(r.get("url") == url for r in records):
                records.append(record)
    return {
        "text": "\n\n".join(texts)[:45000],
        "sources": sources[:30],
        "records": records[:30],
        "retrieved_at": datetime.now(timezone.utc).isoformat(),
        "verification": "multi_source_retrieved",
        "provider": "exa",
        "note": "Multiple public sources were retrieved. Source retrieval still does not prove buying intent, identity, availability, or a business outcome.",
    }

async def deep_research(query: str) -> dict:
    """Broader public research with a bounded second query and source dedupe."""
    first = normalize(await call_exa(
        "web_search_exa",
        {"query": query, "numResults": 8,
         "objective": "Find relevant public primary sources, official pages, and direct source URLs. Do not infer buying intent."}
    ))
    try:
        second = normalize(await call_exa(
            "web_search_exa",
            {"query": query + " official contact source",
             "numResults": 5,
             "objective": "Prefer official or first-party pages with verifiable contact or factual information and source URLs."}
        ))
        items=[first,second]
        for url in first.get("sources",[])[:2]:
            try:
                items.append(await fetch_page(url))
            except ToolError:
                pass
        return _merge_results(items)
    except ToolError:
        return first
