from .models import Mission

class MissionStore:
    def __init__(self): self._missions: dict[str,Mission]={}
    def put(self,m:Mission)->Mission: self._missions[m.id]=m; return m
    def get(self,id:str)->Mission|None: return self._missions.get(id)
    def all(self)->list[Mission]: return list(reversed(list(self._missions.values())))

store=MissionStore()
