import json,re,sqlite3
from dataclasses import dataclass
from .agents import Agent,AGENTS
from .config import settings

@dataclass
class DynamicWorker:
    agent:Agent
    temporary:bool=True
    active:bool=True

class WorkerFactory:
    def __init__(self):
        self.db=sqlite3.connect(settings.database_path,check_same_thread=False)
        self.db.execute("""CREATE TABLE IF NOT EXISTS dynamic_workers(
          id TEXT PRIMARY KEY,name TEXT NOT NULL,skills TEXT NOT NULL,
          permissions TEXT NOT NULL,temporary INTEGER NOT NULL,active INTEGER NOT NULL)""")
        self.db.commit()
    def _row(self,r):
        a=Agent(r[0],r[1],tuple(json.loads(r[2])),tuple(json.loads(r[3])))
        return DynamicWorker(a,bool(r[4]),bool(r[5]))
    def _active(self):
        return [self._row(r) for r in self.db.execute("SELECT id,name,skills,permissions,temporary,active FROM dynamic_workers WHERE active=1").fetchall()]
    def find_by_skills(self,skills:list[str])->Agent|None:
        wanted={s.lower() for s in skills}
        for a in list(AGENTS.values())+[w.agent for w in self._active()]:
            if wanted and wanted.issubset({s.lower() for s in a.skills}):return a
        return None
    def create(self,name:str,skills:list[str],temporary:bool=True)->Agent:
        reusable=self.find_by_skills(skills)
        if reusable:return reusable
        base=re.sub(r"[^a-z0-9]+","_",name.lower()).strip("_") or "worker";aid=base;i=2
        ids=set(AGENTS)|{r[0] for r in self.db.execute("SELECT id FROM dynamic_workers").fetchall()}
        while aid in ids:aid=f"{base}_{i}";i+=1
        a=Agent(aid,name,tuple(dict.fromkeys(skills)),("read_public_web",))
        self.db.execute("INSERT INTO dynamic_workers VALUES(?,?,?,?,?,1)",(a.id,a.name,json.dumps(a.skills),json.dumps(a.permissions),int(temporary)));self.db.commit()
        return a
    def stop(self,aid:str)->bool:
        cur=self.db.execute("UPDATE dynamic_workers SET active=0 WHERE id=? AND active=1",(aid,));self.db.commit();return cur.rowcount>0
    def cleanup(self,exclude=None)->list[str]:
        ids=[r[0] for r in self.db.execute("SELECT id FROM dynamic_workers WHERE temporary=1 AND active=1").fetchall()]
        ids=[aid for aid in ids if aid not in (exclude or set())]
        for aid in ids:self.stop(aid)
        return ids
    def all(self):return [w.agent for w in self._active()]
    def get(self,aid:str):
        if aid in AGENTS:return AGENTS[aid]
        return next((w.agent for w in self._active() if w.agent.id==aid),None)
    def match(self,objective:str)->list[Agent]:
        words=set(re.findall(r"[a-z0-9_-]{3,}",objective.lower()))
        scored=[]
        for w in self._active():
            tokens=set(w.agent.skills)|set(re.findall(r"[a-z0-9_-]{3,}",w.agent.name.lower()))
            score=len(words & {t.lower() for t in tokens})
            if score:scored.append((score,w.agent))
        return [a for _,a in sorted(scored,key=lambda x:(-x[0],x[1].id))]
    def is_temporary(self,aid:str)->bool:
        row=self.db.execute("SELECT temporary FROM dynamic_workers WHERE id=?",(aid,)).fetchone()
        return bool(row[0]) if row else False
factory=WorkerFactory()
