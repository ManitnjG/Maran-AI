import os
import httpx
from .router import ModelRouter,ProviderError,QuotaError
from .config import settings

class OpenAICompatibleProvider:
    def __init__(self,name:str,base_url:str,key_env:str,model:str):
        self.name=name;self.base_url=base_url.rstrip("/");self.key_env=key_env;self.model=model
    async def complete(self,prompt:str)->str:
        key=os.getenv(self.key_env)
        if not key:raise ProviderError(f"{self.name} not configured")
        headers={"Authorization":f"Bearer {key}","Content-Type":"application/json"}
        body={"model":self.model,"messages":[{"role":"user","content":prompt}]}
        try:
            async with httpx.AsyncClient(timeout=90) as client:
                r=await client.post(f"{self.base_url}/chat/completions",headers=headers,json=body)
        except httpx.HTTPError as exc:
            raise ProviderError(f"network:{type(exc).__name__}") from exc
        if r.status_code in (402,429):raise QuotaError(f"http_{r.status_code}")
        if r.status_code>=400:raise ProviderError(f"http_{r.status_code}")
        try:return r.json()["choices"][0]["message"]["content"]
        except (KeyError,IndexError,TypeError) as exc:raise ProviderError("invalid_provider_response") from exc

def configured_router()->ModelRouter:
    providers=[]
    # MARAN_MODEL_ORDER contains provider aliases. Each alias reads its own URL/model/key env vars.
    for alias in settings.model_order:
        prefix="MARAN_PROVIDER_"+alias.upper().replace("-","_")
        url=os.getenv(prefix+"_URL","").strip()
        model=os.getenv(prefix+"_MODEL","").strip()
        key_env=prefix+"_API_KEY"
        if url and model:providers.append(OpenAICompatibleProvider(alias,url,key_env,model))
    return ModelRouter(providers)
