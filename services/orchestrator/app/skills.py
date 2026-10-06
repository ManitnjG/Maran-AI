import json
import re
import sqlite3
from datetime import datetime, timezone
from threading import RLock
from uuid import uuid4
from .config import settings

_STOP = {
    "the","and","for","with","from","this","that","have","into","about","please",
    "create","make","prepare","find","show","need","want","using","work","mission",
}

def _terms(text: str) -> list[str]:
    words = re.findall(r"[^\W_][\w-]{2,}", text.lower(), flags=re.UNICODE)
    return list(dict.fromkeys(w for w in words if w not in _STOP))[:24]

class SkillRegistry:
    """Persistent reusable recipes learned only from successful missions.

    Recipes remember worker selection and task titles, never credentials,
    permissions, browser sessions or executable shell/tool commands.
    """
    def __init__(self):
        self.lock = RLock()
        self.db = sqlite3.connect(settings.database_path, check_same_thread=False)
        self.db.execute("""CREATE TABLE IF NOT EXISTS learned_skills(
            id TEXT PRIMARY KEY,
            workspace_id TEXT NOT NULL,
            name TEXT NOT NULL,
            trigger_terms TEXT NOT NULL,
            agents TEXT NOT NULL,
            step_titles TEXT NOT NULL,
            success_count INTEGER NOT NULL,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
        )""")
        self.db.commit()

    def all(self, workspace_id: str = "default"):
        with self.lock:
            rows = self.db.execute(
                "SELECT id,name,trigger_terms,agents,step_titles,success_count,created_at,updated_at "
                "FROM learned_skills WHERE workspace_id=? ORDER BY success_count DESC,updated_at DESC",
                (workspace_id,),
            ).fetchall()
        return [{
            "id": r[0], "name": r[1], "trigger_terms": json.loads(r[2]),
            "agents": json.loads(r[3]), "step_titles": json.loads(r[4]),
            "success_count": r[5], "created_at": r[6], "updated_at": r[7],
        } for r in rows]

    def match(self, objective: str, workspace_id: str = "default"):
        wanted = set(_terms(objective))
        if not wanted:
            return None
        best = None
        for skill in self.all(workspace_id):
            known = set(skill["trigger_terms"])
            overlap = len(wanted & known)
            required = 1 if min(len(wanted), len(known)) <= 2 else 2
            if overlap < required:
                continue
            score = overlap / max(1, len(wanted | known))
            if best is None or score > best[0]:
                best = (score, skill)
        return best[1] if best else None

    def learn(self, mission):
        if getattr(mission.status, "value", mission.status) != "completed":
            return None
        worker_steps = [s for s in mission.plan if s.agent != "verifier" and s.status == "completed"]
        if not worker_steps:
            return None
        terms = _terms(mission.objective)
        if not terms:
            return None
        agents = [s.agent for s in worker_steps]
        titles = [s.title[:300] for s in worker_steps]
        existing = self.match(mission.objective, mission.workspace_id)
        now = datetime.now(timezone.utc).isoformat()
        with self.lock, self.db:
            if existing and existing["agents"] == agents:
                merged = list(dict.fromkeys(existing["trigger_terms"] + terms))[:30]
                self.db.execute(
                    "UPDATE learned_skills SET trigger_terms=?,step_titles=?,success_count=success_count+1,updated_at=? WHERE id=?",
                    (json.dumps(merged), json.dumps(titles), now, existing["id"]),
                )
                skill_id = existing["id"]
            else:
                skill_id = "skill-" + str(uuid4())
                name = " / ".join(terms[:4]).title()[:80] or "Learned workflow"
                self.db.execute(
                    "INSERT INTO learned_skills VALUES(?,?,?,?,?,?,?,?,?)",
                    (skill_id, mission.workspace_id, name, json.dumps(terms), json.dumps(agents),
                     json.dumps(titles), 1, now, now),
                )
            self.db.commit()
        return next((s for s in self.all(mission.workspace_id) if s["id"] == skill_id), None)

    def delete(self, skill_id: str, workspace_id: str = "default") -> bool:
        with self.lock, self.db:
            cur = self.db.execute(
                "DELETE FROM learned_skills WHERE id=? AND workspace_id=?",
                (skill_id, workspace_id),
            )
        return cur.rowcount > 0

skill_registry = SkillRegistry()
