from enum import Enum
from typing import Any
from pydantic import BaseModel, Field
from uuid import uuid4
from datetime import datetime, timezone

class MissionStatus(str, Enum):
    queued="queued"; planning="planning"; running="running"
    waiting_approval="waiting_approval"; verifying="verifying"
    blocked="blocked"; completed="completed"; failed="failed"; cancelled="cancelled"

class Verification(str, Enum):
    pending="pending"; verified="verified"; partial="partial"
    conflicting="conflicting"; unverified="unverified"

class MissionCreate(BaseModel):
    objective: str = Field(min_length=1, max_length=4000)
    workspace_id: str = "default"
    autonomous: bool = True
    max_cycles: int = Field(default=4, ge=1, le=10)

class PlanStep(BaseModel):
    id: str
    title: str
    agent: str
    requires_approval: bool = False
    approval_reason: str | None = None
    risk_level: str = "auto"
    tool_id: str | None = None
    status: str = "pending"
    approved: bool = False
    attempts: int = 0
    output: str | None = None
    provider: str | None = None
    error: str | None = None
    evidence: dict[str, Any] | None = None

class ActionIntent(BaseModel):
    id: str = Field(default_factory=lambda: "action-" + str(uuid4()))
    tool_id: str
    title: str
    args: dict[str, Any] = Field(default_factory=dict)
    reason: str | None = None
    status: str = "waiting_approval"
    connection_status: str = "unknown"
    requires_approval: bool = True
    approved: bool = False
    attempts: int = 0
    result: dict[str, Any] | None = None
    verification: str = "pending"
    error: str | None = None
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    updated_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))

class Mission(BaseModel):
    id: str = Field(default_factory=lambda: str(uuid4()))
    objective: str
    workspace_id: str = "default"
    status: MissionStatus = MissionStatus.queued
    verification: Verification = Verification.pending
    assigned_agents: list[str] = Field(default_factory=list)
    plan: list[PlanStep] = Field(default_factory=list)
    actions: list[ActionIntent] = Field(default_factory=list)
    events: list[dict[str, Any]] = Field(default_factory=list)
    result: dict[str, Any] | None = None
    autonomy_enabled: bool = True
    max_cycles: int = Field(default=4, ge=1, le=10)
    cycle: int = 0
    learned_skill_id: str | None = None
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    updated_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))

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

class ActionDecision(BaseModel):
    approved: bool
    note: str | None = None

class MemoryWrite(BaseModel):
    key: str = Field(min_length=1,max_length=80)
    value: str = Field(min_length=1,max_length=1000)
    workspace_id: str = "default"
