# Architecture

User → Android Client → API Gateway → Chief AI / Mission Orchestrator → Agent Registry → Tool Gateway → External Services

Supporting systems:
- Model Router: provider health, capability, quota, cost and fallback
- State Store: missions survive model/provider switches
- Approval Engine: blocks consequential actions until authorized
- Verifier: checks outputs/evidence before completion
- Scheduler: recurring and conditional missions
- Audit Log: append-only action history
- Knowledge Store: workspace-scoped reusable context
- Secrets Vault: encrypted provider/integration credentials

## Agent hierarchy
Chief AI may delegate to manager and specialist agents. Child agents can never gain permissions beyond their parent/mission policy.

## Initial workers
Research, Tour Leads, SEO, Social Marketing, Documents/OCR, Accounting/GST/Tally assistance, Coding/OpenCode, QA/Verifier.

## Model fallback
Provider failure → classify failure → persist checkpoint → choose eligible fallback → compact handoff → resume → verify.
Never rotate credentials to evade provider restrictions or limits.
