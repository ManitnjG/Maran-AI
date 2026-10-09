import sqlite3,json
from .config import settings
class ContextVault:
 def __init__(self):
  self.db=sqlite3.connect(settings.database_path,check_same_thread=False)
  self.db.execute("CREATE TABLE IF NOT EXISTS mission_context(mission_id TEXT,key TEXT,value TEXT,PRIMARY KEY(mission_id,key))");self.db.commit()
 def put(self,mission_id,key,value):
  self.db.execute("INSERT OR REPLACE INTO mission_context VALUES(?,?,?)",(mission_id,key,json.dumps(value)));self.db.commit()
 def get(self,mission_id):
  return {k:json.loads(v) for k,v in self.db.execute("SELECT key,value FROM mission_context WHERE mission_id=?",(mission_id,)).fetchall()}
 def delete_many(self,mission_ids):
  if not mission_ids:return 0
  cur=self.db.executemany("DELETE FROM mission_context WHERE mission_id=?",[(mid,) for mid in mission_ids]);self.db.commit()
  return len(mission_ids)
context_vault=ContextVault()
