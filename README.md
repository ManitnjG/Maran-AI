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
See [current implementation status](docs/STATUS.md) for working features, deployment requirements and unfinished integrations.


## Current executable milestone

MARAN now has a working Android + FastAPI skeleton with Manager-controlled missions, approval gates, bounded worker execution, dynamic temporary workers, voice command routing, worker reuse/cleanup, durable mission context, verification state, and GitHub Actions APK builds.

Voice examples:
- "Find corporate tour leads in Chennai"
- "Create worker Hotel Quotation Specialist"
- "Create worker Tamil Social Media Specialist"

External providers and business systems are opt-in adapters. API keys and service credentials must be supplied as deployment secrets; they are never committed to this repository.

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

For an operator-hosted Ollama model, set `MARAN_OLLAMA_URL=http://127.0.0.1:11434/v1`
and `MARAN_OLLAMA_MODEL` to a model installed on that host. This option needs no provider
API key but does require model hosting. Configured cloud providers remain available as
fallbacks. No model configured means a **blocked** mission, never a fabricated success.

Read [implementation status and remaining work](docs/STATUS.md) before deployment.
Generated model content is a draft. Public-source research now works through Exa MCP without a key (rate limited); business-system writebacks are still pending.

See [deployment and phone setup](docs/DEPLOYMENT.md). Successful Android CI publishes a debug preview APK on the Releases page.
