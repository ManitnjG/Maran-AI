from enum import Enum
from typing import Any
from pydantic import BaseModel, Field
from uuid import uuid4
from datetime import datetime, timezone

class MissionStatus(str, Enum):
    queued="queued"; planning="planning"; running="running"
    waiting_approval="waiting_approval"; verifying="verifying"
    completed="completed"; failed="failed"; cancelled="cancelled"

class Verification(str, Enum):
    pending="pending"; verified="verified"; partial="partial"
    conflicting="conflicting"; unverified="unverified"

class MissionCreate(BaseModel):
    objective: str = Field(min_length=1, max_length=4000)
    workspace_id: str = "default"

class PlanStep(BaseModel):
    id: str
    title: str
    agent: str
    requires_approval: bool = False
    status: str = "pending"

class Mission(BaseModel):
    id: str = Field(default_factory=lambda: str(uuid4()))
    objective: str
    workspace_id: str = "default"
    status: MissionStatus = MissionStatus.queued
    verification: Verification = Verification.pending
    assigned_agents: list[str] = []
    plan: list[PlanStep] = []
    events: list[dict[str, Any]] = []
    result: dict[str, Any] | None = None
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))

class WorkerCreate(BaseModel):
    name: str = Field(min_length=1,max_length=80)
    skills: list[str] = Field(min_length=1,max_length=30)
    temporary: bool = True

class VoiceCommand(BaseModel):
    text: str = Field(min_length=1,max_length=1000)
    confidence: float = Field(default=1.0,ge=0.0,le=1.0)

class StopRequest(BaseModel):
    agent_id: str | None = None
    reason: str = "Stopped by Manager MARAN"

class ApprovalDecision(BaseModel):
    approved: bool
    note: str | None = None
