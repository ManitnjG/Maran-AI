import os,shutil,asyncio
class OpenCodeWorker:
 async def available(self)->bool:
  return os.getenv("MARAN_OPENCODE_ENABLED","false").lower()=="true" and shutil.which("opencode") is not None
 async def run(self,task:str,workspace:str)->dict:
  if not await self.available():return {"ok":False,"reason":"opencode_not_configured"}
  proc=await asyncio.create_subprocess_exec("opencode","run",task,cwd=workspace,stdout=asyncio.subprocess.PIPE,stderr=asyncio.subprocess.PIPE)
  out,err=await proc.communicate()
  return {"ok":proc.returncode==0,"output":out.decode()[-12000:],"error":err.decode()[-4000:]}
opencode=OpenCodeWorker()
