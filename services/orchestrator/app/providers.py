import os
from .router import ModelRouter,ProviderError

class OpenAICompatibleProvider:
 def __init__(self,name,base_url,key_env,model):
  self.name=name;self.base_url=base_url;self.key_env=key_env;self.model=model
 async def complete(self,prompt:str)->str:
  key=os.getenv(self.key_env)
  if not key:raise ProviderError(f"{self.name} not configured")
  # Network adapter intentionally lives behind this interface; secrets never enter source control.
  raise ProviderError(f"{self.name} adapter requires deployment connector")

def configured_router()->ModelRouter:
 providers=[]
 # Add deployment-specific provider instances here or through a secrets-backed adapter.
 return ModelRouter(providers)
