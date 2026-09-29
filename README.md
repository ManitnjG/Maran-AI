# MARAN AI

**MARAN — Your Personal AI Workforce**

MARAN is a voice-first, general-purpose multi-agent platform. A Chief AI turns a user goal into a mission, delegates work to specialist agents, verifies results, and requests approval for consequential actions.

## Product principles
- Voice-first: Tamil, English, and mixed-language commands
- Goal-first UX: users describe outcomes, not agents
- Multi-agent: planner, specialists, verifier
- OpenCode Zen is the sole AI engine: planning, assignment, drafting, research synthesis and review; free-only model fallback
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
Bottom navigation: **Home · Missions · AI · Workers · More**

The AI tab offers direct OpenCode chat. Home offers mission and voice controls. Missions use the backend for saved plans, workers, evidence, approvals and results.

## Status
See [current implementation status](docs/STATUS.md) for working features, deployment requirements and unfinished integrations.


## Current executable milestone

MARAN now has a working Android + FastAPI skeleton with Manager-controlled missions, approval gates, bounded worker execution, dynamic temporary workers, voice command routing, worker reuse/cleanup, durable mission context, verification state, and GitHub Actions APK builds.

Voice examples:
- "Find corporate tour leads in Chennai"
- "Create worker Hotel Quotation Specialist"
- "Create worker Tamil Social Media Specialist"

OpenCode is the only registered reasoning provider. Business-system integrations remain unfinished. API keys and service credentials must be supplied as deployment secrets; they are never committed to this repository.

## Run and connect

```sh
cd services/orchestrator
python -m pip install -r requirements.txt
# Set environment variables from .env.example in your process/deployment.
# .env files are not loaded automatically.
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers 1
```

In the Android app, open **More → Connection** and enter your running server's URL.
`10.0.2.2` is Android-emulator-only. A physical phone needs a reachable server address.
Use HTTPS and `MARAN_ACCESS_TOKEN` for a remotely reachable deployment.

All AI reasoning uses OpenCode Zen. The server checks the active free-model catalog before every
request and never falls back to paid models or another provider. Optionally set
`OPENCODE_API_KEY` as a server secret and `MARAN_OPENCODE_ZEN_MODEL` to one currently free model ID.
A blank model selects up to three free candidates on availability failures only.
Authentication, billing and rate limits stop further attempts.

**Access blocker:** live keyless requests returned HTTP 403 restricting the free tier to OpenCode.
Neither an API integration nor the optional legacy CLI adapter guarantees access. Missions remain
blocked until authorized OpenCode access works. Android chat keys and backend keys are separate.
The app uses OpenCode's model gateway; it does not embed the full OpenCode CLI runtime.

Read [implementation status and remaining work](docs/STATUS.md) before deployment.
Generated model content is a draft. Public-source research now works through Exa MCP without a key (rate limited); business-system writebacks are still pending.

See [deployment and phone setup](docs/DEPLOYMENT.md). Successful Android CI publishes a debug preview APK on the Releases page.


## Maran 0.8 external actions

The orchestrator now exposes an opt-in connector layer for Google Workspace (Gmail, Calendar, Drive), GitHub Actions/code updates, WhatsApp Cloud messaging, Meta/LinkedIn publishing, Twilio SMS/voice, Tally HTTP/XML posting, and a built-in CRM lead store. No credential is bundled in the app. Configure only the services you use through environment variables; `/integrations` reports live configuration readiness.

Consequential actions require `confirmed: true` at the API boundary. GST portal automation, payment execution, CAPTCHA bypass, OTP interception and unrestricted device control are intentionally not implemented.
