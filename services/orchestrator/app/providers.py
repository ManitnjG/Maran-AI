import os
import httpx
from .router import ModelRouter,ProviderError,QuotaError

from .zen_gateway import OpenCodeZenProvider

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
    # All MARAN reasoning goes through OpenCode. Legacy adapters are not registered.
    return ModelRouter([OpenCodeZenProvider()])
