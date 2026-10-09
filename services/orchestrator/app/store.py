import json, sqlite3
from datetime import datetime, timezone
from threading import RLock
from .models import Mission
from .config import settings

class MissionStore:
    def __init__(self,path:str|None=None):
        self.lock=RLock()
        self.path=path or settings.database_path
        self.db=sqlite3.connect(self.path,check_same_thread=False)
        self.db.execute("CREATE TABLE IF NOT EXISTS missions (id TEXT PRIMARY KEY, payload TEXT NOT NULL, created_at TEXT NOT NULL)")
        self.db.commit()
    def put_many(self, missions):
        with self.lock, self.db:
            self.db.executemany("INSERT INTO missions(id,payload,created_at) VALUES(?,?,?)",
                [(m.id,m.model_dump_json(),m.created_at.isoformat()) for m in missions])
    def put(self,m:Mission)->Mission:
        m.updated_at=datetime.now(timezone.utc)
        with self.lock, self.db:
            self.db.execute("INSERT OR REPLACE INTO missions(id,payload,created_at) VALUES(?,?,?)",(m.id,m.model_dump_json(),m.created_at.isoformat()))
        return m
    def get(self,id:str)->Mission|None:
        with self.lock:
            row=self.db.execute("SELECT payload FROM missions WHERE id=?",(id,)).fetchone()
        return Mission.model_validate_json(row[0]) if row else None
    def all(self)->list[Mission]:
        with self.lock:
            rows=self.db.execute("SELECT payload FROM missions ORDER BY created_at DESC").fetchall()
        return [Mission.model_validate_json(r[0]) for r in rows]
    def clear_workspace(self,workspace_id:str)->list[str]:
        missions=[m for m in self.all() if m.workspace_id==workspace_id]
        ids=[m.id for m in missions]
        if not ids:return []
        with self.lock,self.db:
            self.db.executemany("DELETE FROM missions WHERE id=?",[(mid,) for mid in ids])
        return ids
store=MissionStore()
