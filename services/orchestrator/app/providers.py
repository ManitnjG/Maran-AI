import os
import shutil
import httpx
from .router import ModelRouter,ProviderError,QuotaError
from .config import settings

class OpenCodeZenProvider:
    def __init__(self,model:str="big-pickle"):
        self.name="opencode-zen";self.model=model
    async def complete(self,prompt:str)->str:
        key=os.getenv("OPENCODE_API_KEY")
        if not key:raise ProviderError("opencode-zen not configured")
        headers={"Content-Type":"application/json"}
        if key: headers["Authorization"]=f"Bearer {key}"
        body={"model":self.model,"messages":[{"role":"user","content":prompt}]}
        try:
            async with httpx.AsyncClient(timeout=90) as client:r=await client.post("https://opencode.ai/zen/v1/chat/completions",headers=headers,json=body)
        except httpx.HTTPError as exc:raise ProviderError(f"network:{type(exc).__name__}") from exc
        if r.status_code in (402,429):raise QuotaError(f"http_{r.status_code}")
        if r.status_code>=400:raise ProviderError(f"http_{r.status_code}")
        try:return r.json()["choices"][0]["message"]["content"]
        except (ValueError,KeyError,IndexError,TypeError) as exc:raise ProviderError("invalid_provider_response") from exc

class OpenAICompatibleProvider:
    def __init__(self,name:str,base_url:str,key_env:str,model:str,requires_key:bool=True):
        self.name=name;self.base_url=base_url.rstrip("/");self.key_env=key_env;self.model=model;self.requires_key=requires_key
    async def complete(self,prompt:str)->str:
        key=os.getenv(self.key_env)
        if self.requires_key and not key:raise ProviderError(f"{self.name} not configured")
        headers={"Content-Type":"application/json"}
        if key: headers["Authorization"]=f"Bearer {key}"
        body={"model":self.model,"messages":[{"role":"user","content":prompt}]}
        try:
            async with httpx.AsyncClient(timeout=90) as client:
                r=await client.post(f"{self.base_url}/chat/completions",headers=headers,json=body)
        except httpx.HTTPError as exc:
            raise ProviderError(f"network:{type(exc).__name__}") from exc
        if r.status_code in (402,429):raise QuotaError(f"http_{r.status_code}")
        if r.status_code>=400:raise ProviderError(f"http_{r.status_code}")
        try:return r.json()["choices"][0]["message"]["content"]
        except (ValueError,KeyError,IndexError,TypeError) as exc:raise ProviderError("invalid_provider_response") from exc

def configured_router()->ModelRouter:
    providers=[]
    if os.getenv("OPENCODE_API_KEY"):
        providers.append(OpenCodeZenProvider(os.getenv("MARAN_OPENCODE_ZEN_MODEL","big-pickle")))
    # MARAN_MODEL_ORDER contains provider aliases. Each alias reads its own URL/model/key env vars.
    for alias in settings.model_order:
        prefix="MARAN_PROVIDER_"+alias.upper().replace("-","_")
        url=os.getenv(prefix+"_URL","").strip()
        model=os.getenv(prefix+"_MODEL","").strip()
        key_env=prefix+"_API_KEY"
        if url and model:providers.append(OpenAICompatibleProvider(alias,url,key_env,model))
    local_url=os.getenv("MARAN_OLLAMA_URL", "").strip()
    if local_url:
        providers.append(OpenAICompatibleProvider("ollama",local_url,"MARAN_OLLAMA_API_KEY",os.getenv("MARAN_OLLAMA_MODEL","qwen2.5:3b"),requires_key=False))
    if os.getenv("MARAN_OPENCODE_FREE_ENABLED", "false").lower() == "true" and shutil.which("opencode"):
        from .opencode_provider import OpenCodeCLIProvider
        requested = os.getenv("MARAN_OPENCODE_FREE_MODELS", "opencode/big-pickle,opencode/mimo-v2.6-flash-free").split(",")
        for model in requested[:2]:
            model=model.strip()
            if model.startswith("opencode/") and (model.endswith("-free") or model=="opencode/big-pickle"):
                providers.append(OpenCodeCLIProvider(model))
    return ModelRouter(providers)
