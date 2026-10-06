"""Deterministic permission and approval policy for MARAN plans.

AI output can propose work, but application code owns permission decisions.
"""
from dataclasses import dataclass
import re

AUTO = "auto"
CONFIRM = "confirm"
BLOCKED = "blocked"

@dataclass(frozen=True)
class RiskDecision:
    level: str
    reason: str | None = None

# These controls must remain human-entered/handled. Approval does not turn them
# into automatable actions.
_PROTECTED = (
    "captcha", "otp", "one time password", "upi pin", "card pin", "cvv",
    "password", "passcode", "biometric", "security prompt", "2fa", "two factor",
)
# Consequential external actions need a fresh user decision before a tool could
# perform them. The current executor still produces drafts only.
_CONFIRM = (
    "send email", "send message", "reply to", "publish", "post to", "delete",
    "remove account", "book ", "reserve ", "purchase", "buy ", "payment",
    "pay ", "transfer", "file gst", "submit gst", "submit tax", "file tax",
    "sign ", "accept terms", "install ", "uninstall ", "place order",
)

def assess(agent: str, title: str, objective: str = "") -> RiskDecision:
    text = f"{title} {objective}".lower()
    if any(token in text for token in _PROTECTED):
        return RiskDecision(BLOCKED, "Protected authentication/security action cannot be automated")
    if agent == "accounting" or any(token in text for token in _CONFIRM):
        return RiskDecision(CONFIRM, "Consequential external or financial action requires user approval")
    return RiskDecision(AUTO)

def apply_step_policy(step, objective: str = ""):
    decision = assess(step.agent, step.title, objective)
    step.risk_level = decision.level
    step.approval_reason = decision.reason
    if decision.level == CONFIRM:
        step.requires_approval = True
    elif decision.level == BLOCKED:
        # Keep this distinct from approval: a blocked security action is not
        # made executable by approving it.
        step.requires_approval = False
        step.approved = False
    return step

def apply_plan_policy(plan, objective: str = ""):
    for step in plan:
        apply_step_policy(step, objective)
    return plan
