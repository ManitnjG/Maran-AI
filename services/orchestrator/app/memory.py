import json
import re
import sqlite3
from datetime import datetime, timezone
from threading import RLock
from .config import settings

_SECRET_HINT = re.compile(r"(?i)\b(password|passcode|api[ _-]?key|access[ _-]?token|refresh[ _-]?token|otp|cvv|upi[ _-]?pin|card[ _-]?pin)\b")

class WorkspaceMemory:
    """Small persistent preference/context store.

    It intentionally rejects obvious secrets. Memory is context only and never
    grants a permission or bypasses an approval gate.
    """
    def __init__(self):
        self.lock = RLock()
        self.db = sqlite3.connect(settings.database_path, check_same_thread=False)
        self.db.execute("""CREATE TABLE IF NOT EXISTS workspace_memory(
            workspace_id TEXT NOT NULL,
            key TEXT NOT NULL,
            value TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            PRIMARY KEY(workspace_id,key)
        )""")
        self.db.commit()

    def set(self, workspace_id: str, key: str, value: str):
        key, value = key.strip(), value.strip()
        if not key or len(key) > 80 or len(value) > 1000:
            raise ValueError("Memory key/value length is invalid")
        if _SECRET_HINT.search(key) or _SECRET_HINT.search(value):
            raise ValueError("Secrets must not be stored in MARAN memory")
        now = datetime.now(timezone.utc).isoformat()
        with self.lock, self.db:
            self.db.execute(
                "INSERT OR REPLACE INTO workspace_memory(workspace_id,key,value,updated_at) VALUES(?,?,?,?)",
                (workspace_id, key, value, now),
            )
        return {"workspace_id": workspace_id, "key": key, "value": value, "updated_at": now}

    def set_system(self, workspace_id: str, key: str, value: str):
        # System-generated values are constrained and never contain credentials.
        return self.set(workspace_id, key, value[:1000])

    def all(self, workspace_id: str):
        with self.lock:
            rows = self.db.execute(
                "SELECT key,value,updated_at FROM workspace_memory WHERE workspace_id=? ORDER BY key",
                (workspace_id,),
            ).fetchall()
        return [{"key": k, "value": v, "updated_at": t} for k, v, t in rows]

    def prompt_context(self, workspace_id: str) -> dict[str, str]:
        return {item["key"]: item["value"] for item in self.all(workspace_id)[:20]}

    def delete(self, workspace_id: str, key: str) -> bool:
        with self.lock, self.db:
            cur = self.db.execute(
                "DELETE FROM workspace_memory WHERE workspace_id=? AND key=?",
                (workspace_id, key),
            )
        return cur.rowcount > 0

memory_store = WorkspaceMemory()
