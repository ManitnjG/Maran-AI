import pytest
from app.router import ModelRouter,ProviderError,QuotaError

class Fake:
 def __init__(self,name,result=None,error=None):self.name=name;self.result=result;self.error=error
 async def complete(self,prompt):
  if self.error:raise self.error
  return self.result

@pytest.mark.asyncio
async def test_router_falls_back_after_quota():
 first=Fake("primary",error=QuotaError("limit"))
 second=Fake("backup",result="ok")
 out,provider=await ModelRouter([first,second]).complete("hello")
 assert out=="ok" and provider=="backup"

@pytest.mark.asyncio
async def test_router_raises_when_all_fail():
 with pytest.raises(ProviderError):
  await ModelRouter([Fake("one",error=ProviderError("down"))]).complete("hello")
