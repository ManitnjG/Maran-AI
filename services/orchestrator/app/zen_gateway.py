"""Free-only OpenCode Zen gateway. No third-party fallback or client impersonation."""
import os
import httpx
from .router import ProviderError

def free_models(catalog):
    provider = catalog["opencode"]
    models = []
    for mid, model in provider["models"].items():
        cost = model.get("cost", {})
        if not isinstance(cost,dict):
            continue
        if model.get("status") == "deprecated" or not {"input","output"} <= cost.keys():
            continue
        if any(type(v) not in (int,float) or v != 0 for v in cost.values()):
            continue
        npm = model.get("provider",{}).get("npm",provider.get("npm"))
        protocol = {"@ai-sdk/openai-compatible":"chat/completions","@ai-sdk/openai":"responses",
                    "@ai-sdk/anthropic":"messages"}.get(npm)
        if protocol:
            models.append((mid,protocol))
    return sorted(models)

def response_text(protocol, data):
    if protocol == "responses":
        return "\n".join(c["text"] for item in data.get("output",[]) for c in item.get("content",[]) if c.get("type")=="output_text")
    if protocol == "messages":
        return "\n".join(c["text"] for c in data.get("content",[]) if c.get("type")=="text")
    return data["choices"][0]["message"]["content"]

class OpenCodeZenProvider:
    name = "opencode-zen"
    def __init__(self, model=None):
        self.model = model or os.getenv("MARAN_OPENCODE_ZEN_MODEL","").strip()

    async def complete(self,prompt):
        try:
            async with httpx.AsyncClient(timeout=90,follow_redirects=False) as client:
                catalog = await client.get("https://models.dev/api.json")
                if catalog.status_code != 200 or len(catalog.content)>8*1024*1024:
                    raise ProviderError("OpenCode free-model catalog unavailable; no model was called.")
                candidates = free_models(catalog.json())
                if self.model:
                    candidates = [m for m in candidates if m[0] == self.model]
                if not candidates:
                    raise ProviderError("No selected OpenCode model is currently listed as free.")
                headers = {"Content-Type":"application/json"}
                key = os.getenv("OPENCODE_API_KEY","").strip()
                if key: headers["Authorization"] = f"Bearer {key}"
                for mid, protocol in candidates[:3]:
                    body = {"model":mid,"stream":False}
                    messages = [{"role":"user","content":prompt}]
                    request_headers = dict(headers)
                    if protocol == "responses":
                        body.update(input=messages,max_output_tokens=4096,store=False)
                    else:
                        body.update(messages=messages,max_tokens=4096)
                    if protocol == "messages":
                        request_headers["anthropic-version"]="2023-06-01"
                        if key:request_headers["x-api-key"]=key
                    response = await client.post("https://opencode.ai/zen/v1/"+protocol,json=body,headers=request_headers)
                    if response.status_code in (401,403):
                        raise ProviderError("OpenCode denied access. Free-tier access may be restricted to OpenCode; authorized access is required.")
                    if response.status_code in (402,429):
                        raise ProviderError("OpenCode billing or rate limit reached. No paid fallback or further model attempts were made.")
                    if response.status_code in (404,502,503,504):
                        continue
                    if response.status_code >= 400:
                        raise ProviderError(f"OpenCode request failed (HTTP {response.status_code}).")
                    output = response_text(protocol,response.json())
                    if not isinstance(output,str) or not output.strip():
                        raise ProviderError("OpenCode returned no usable text.")
                    return output
                raise ProviderError("The free OpenCode models tried are unavailable.")
        except (httpx.HTTPError,ValueError,KeyError,IndexError,TypeError,AttributeError) as exc:
            raise ProviderError("OpenCode connection or response failed; check access and retry.") from exc
