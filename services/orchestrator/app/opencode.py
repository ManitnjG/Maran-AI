import os
import shutil
import asyncio
from .config import settings

class OpenCodeWorker:
    async def available(self) -> bool:
        return os.getenv("MARAN_OPENCODE_ENABLED", "false").lower() == "true" and shutil.which("opencode") is not None

    async def run(self, task: str, workspace: str) -> dict:
        if not await self.available():
            return {"ok": False, "reason": "opencode_not_configured"}
        proc = await asyncio.create_subprocess_exec(
            "opencode", "run", task, cwd=workspace,
            stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)
        try:
            out, err = await asyncio.wait_for(proc.communicate(), settings.worker_timeout_seconds)
        except (asyncio.TimeoutError, asyncio.CancelledError):
            if proc.returncode is None:
                proc.kill()
            await proc.communicate()
            raise
        return {"ok": proc.returncode == 0, "output": out.decode(errors="replace")[-12000:],
                "error": err.decode(errors="replace")[-4000:]}

opencode = OpenCodeWorker()
