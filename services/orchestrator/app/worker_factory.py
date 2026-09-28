import re
from dataclasses import dataclass
from .agents import Agent, AGENTS
from .policies import SAFE_DYNAMIC

@dataclass
class DynamicWorker:
    agent:Agent
    temporary:bool=True
    active:bool=True

class WorkerFactory:
    def __init__(self): self.dynamic:dict[str,DynamicWorker]={}
    def find_by_skills(self,skills:list[str])->Agent|None:
        wanted={s.lower() for s in skills}
        for a in list(AGENTS.values())+[w.agent for w in self.dynamic.values() if w.active]:
            if wanted and wanted.issubset({s.lower() for s in a.skills}): return a
        return None
    def create(self,name:str,skills:list[str],temporary:bool=True)->Agent:
        reusable=self.find_by_skills(skills)
        if reusable:return reusable
        base=re.sub(r"[^a-z0-9]+","_",name.lower()).strip("_") or "worker"
        aid=base;i=2
        while aid in AGENTS or aid in self.dynamic: aid=f"{base}_{i}";i+=1
        a=Agent(aid,name,tuple(dict.fromkeys(skills)),("read_public_web",))
        self.dynamic[aid]=DynamicWorker(a,temporary,True);return a
    def stop(self,aid:str)->bool:
        w=self.dynamic.get(aid)
        if not w:return False
        w.active=False;return True
    def cleanup(self)->list[str]:
        stopped=[]
        for aid,w in self.dynamic.items():
            if w.temporary and w.active:w.active=False;stopped.append(aid)
        return stopped
    def all(self):return [w.agent for w in self.dynamic.values() if w.active]
factory=WorkerFactory()
