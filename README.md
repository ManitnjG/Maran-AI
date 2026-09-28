# MARAN AI

**MARAN — Your Personal AI Workforce**

MARAN is a voice-first, general-purpose multi-agent platform. A Chief AI turns a user goal into a mission, delegates work to specialist agents, verifies results, and requests approval for consequential actions.

## Product principles
- Voice-first: Tamil, English, and mixed-language commands
- Goal-first UX: users describe outcomes, not agents
- Multi-agent: planner, specialists, verifier
- Provider-independent: automatic model fallback with persisted mission state
- Human control: explicit approval gates and audit history
- Modular skills: tourism leads, SEO, social, documents, accounting, coding/OpenCode, and more
- Security: least-privilege tools; no OTP/CAPTCHA/security bypass

## Planned repository layout
- `apps/android/` — Kotlin + Jetpack Compose client
- `services/orchestrator/` — Chief AI, missions, agent runtime
- `packages/contracts/` — shared schemas
- `workers/` — specialist workers
- `docs/` — architecture, UX, security and roadmap
- `.github/workflows/` — CI and Android builds

## Core UX
Bottom navigation: **Home · Missions · MARAN · Workforce · More**

The center MARAN action opens voice mode. Every substantial request becomes a Mission with plan, agents, progress, evidence, approvals and results.

## Status
Foundation initialized. Next milestone: installable Android shell + orchestrator skeleton + model-router contracts.
