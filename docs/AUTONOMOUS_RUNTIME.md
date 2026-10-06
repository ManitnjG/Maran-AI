# MARAN Autonomous Runtime

MARAN 0.10 adds a bounded autonomous mission loop around the existing Manager, workers and tool layer.

## Runtime loop

Goal → Manager plan → deterministic tool/permission assignment → execute → observe → verify → retry eligible failures → learn successful workflow → finish.

Each mission records:
- autonomy enabled/disabled
- current cycle and maximum cycles
- per-step attempt count
- risk level and approval reason
- app-selected tool id
- verification state
- append-only mission events
- learned workflow id after successful completion

The runtime never converts model prose into permissions. Application code owns tool selection, approval rules, retry limits and protected-action blocks.

## Approval model

Automatic:
- public web research
- reading mission context
- drafting/summarizing
- learned worker reuse
- non-secret workspace memory

Explicit confirmation:
- configured external communication/publishing actions
- calendar/Drive/GitHub/Tally write actions
- financial/accounting consequential actions
- other irreversible external side effects

Never automated:
- CAPTCHA
- OTP / one-time passwords
- passwords/passcodes
- UPI/card PIN
- CVV
- biometrics
- security prompts
- payment execution

Approving a mission does not turn a protected security action into an allowed tool.

## Skills and memory

Successful missions can create reusable worker recipes. A learned skill stores trigger terms, worker ids and step titles only. It does not store credentials, sessions or executable commands.

Workspace memory is a small SQLite key/value store for preferences and reusable context. Obvious credential/secret fields are rejected. Memory is passed to workers as context only and cannot grant permissions.

## Background work

The Android app schedules network-constrained WorkManager jobs. A mission can continue through the backend autonomous endpoint when the app is not foregrounded. Android WorkManager is best-effort background scheduling, not a real-time always-on daemon.

## Models

OpenCode Zen remains first by default. OpenRouter and local Ollama can be configured as optional fallbacks. No paid provider is silently enabled.

## Internet access

The current autonomous web capability is read-only public research and public page retrieval through the configured Exa MCP path. It is not an unrestricted browser automation engine and does not bypass website authentication, queues, anti-bot controls or security checks.

## External integrations

Existing Gmail, Calendar, Drive, GitHub, WhatsApp, social, Twilio and Tally connector endpoints remain explicit-confirmation actions. The autonomous mission engine does not silently send, publish, call, post, pay or file on the user's behalf.

## Reliability limits

- Active task ownership is process-local; deploy the FastAPI service with one ASGI worker unless a distributed queue is added.
- WorkManager periodic work has Android's minimum/flexible scheduling constraints.
- Model/provider availability is external and can fail.
- Verification is evidence-aware but an AI review is not independent factual proof.
