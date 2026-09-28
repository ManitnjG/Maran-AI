import json, sqlite3
from .models import Mission
from .config import settings

class MissionStore:
    def __init__(self,path:str|None=None):
        self.path=path or settings.database_path
        self.db=sqlite3.connect(self.path,check_same_thread=False)
        self.db.execute("CREATE TABLE IF NOT EXISTS missions (id TEXT PRIMARY KEY, payload TEXT NOT NULL, created_at TEXT NOT NULL)")
        self.db.commit()
    def put(self,m:Mission)->Mission:
        payload=m.model_dump_json()
        self.db.execute("INSERT OR REPLACE INTO missions(id,payload,created_at) VALUES(?,?,?)",(m.id,payload,m.created_at.isoformat()))
        self.db.commit(); return m
    def get(self,id:str)->Mission|None:
        row=self.db.execute("SELECT payload FROM missions WHERE id=?",(id,)).fetchone()
        return Mission.model_validate_json(row[0]) if row else None
    def all(self)->list[Mission]:
        rows=self.db.execute("SELECT payload FROM missions ORDER BY created_at DESC").fetchall()
        return [Mission.model_validate_json(r[0]) for r in rows]
store=MissionStore()
