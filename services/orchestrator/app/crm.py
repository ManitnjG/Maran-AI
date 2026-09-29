from __future__ import annotations

import sqlite3
import uuid
from datetime import datetime, timezone
from threading import RLock
from pydantic import BaseModel, Field
from .config import settings


class LeadInput(BaseModel):
    name: str = Field(min_length=1, max_length=160)
    company: str = Field(default="", max_length=160)
    email: str = Field(default="", max_length=320)
    phone: str = Field(default="", max_length=80)
    source: str = Field(default="manual", max_length=80)
    status: str = Field(default="new", max_length=40)
    notes: str = Field(default="", max_length=4000)


class LeadStore:
    def __init__(self, path: str | None = None):
        self.path = path or settings.database_path
        self.lock = RLock()
        self.db = sqlite3.connect(self.path, check_same_thread=False)
        self.db.execute("""CREATE TABLE IF NOT EXISTS crm_leads (
            id TEXT PRIMARY KEY, name TEXT NOT NULL, company TEXT NOT NULL,
            email TEXT NOT NULL, phone TEXT NOT NULL, source TEXT NOT NULL,
            status TEXT NOT NULL, notes TEXT NOT NULL, created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL)""")
        self.db.commit()

    def add(self, lead: LeadInput) -> dict:
        now = datetime.now(timezone.utc).isoformat()
        item = {"id": str(uuid.uuid4()), **lead.model_dump(), "created_at": now, "updated_at": now}
        with self.lock, self.db:
            self.db.execute("INSERT INTO crm_leads VALUES(?,?,?,?,?,?,?,?,?,?)", (
                item["id"], item["name"], item["company"], item["email"], item["phone"],
                item["source"], item["status"], item["notes"], item["created_at"], item["updated_at"]
            ))
        return item

    def list(self, status: str = "", limit: int = 100) -> list[dict]:
        limit = min(max(limit, 1), 500)
        with self.lock:
            if status:
                rows = self.db.execute("SELECT * FROM crm_leads WHERE status=? ORDER BY updated_at DESC LIMIT ?", (status, limit)).fetchall()
            else:
                rows = self.db.execute("SELECT * FROM crm_leads ORDER BY updated_at DESC LIMIT ?", (limit,)).fetchall()
        cols = ["id","name","company","email","phone","source","status","notes","created_at","updated_at"]
        return [dict(zip(cols, r)) for r in rows]

    def update_status(self, lead_id: str, status: str, notes: str | None = None) -> dict | None:
        now = datetime.now(timezone.utc).isoformat()
        with self.lock, self.db:
            row = self.db.execute("SELECT * FROM crm_leads WHERE id=?", (lead_id,)).fetchone()
            if not row:
                return None
            if notes is None:
                self.db.execute("UPDATE crm_leads SET status=?, updated_at=? WHERE id=?", (status, now, lead_id))
            else:
                self.db.execute("UPDATE crm_leads SET status=?, notes=?, updated_at=? WHERE id=?", (status, notes, now, lead_id))
        return next((x for x in self.list(limit=500) if x["id"] == lead_id), None)

lead_store = LeadStore()
