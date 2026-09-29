# MARAN readiness — OpenCode brain

## Implemented
- Native Android connection setup with Android Keystore encrypted access token, Tamil/English selection, and explicit microphone state.
- Opt-in recognition of “Maran” / “மாறன்” while the Home screen is visible. Stops on screen exit or app background. This is not a low-power background wake engine.
- Missions, manager assignment, named workers, approvals, retries, stop/cancellation, duplicate-run suppression and saved step outputs.
- Live public web search and URL reading through the documented Exa MCP service without a key (rate limited). Research workers share one query; AI mission synthesis now requires OpenCode access. The standalone search tool remains independent. Results retain source URLs, retrieval time and published contacts; prospects are not asserted to be interested buyers.
- OpenCode Zen is the only registered AI provider. It creates validated worker plans, generates all worker drafts (including research synthesis), and reviews outputs. MARAN code retains permissions, approvals and cancellation.
- Legacy OpenCode CLI and compatible adapters remain as unregistered code; they are not used by the runtime. Free-tier access failed live with HTTP 403. No impersonation or bypass is used.
- Tally sales accounting-voucher XML export with user-entered amounts and exact ledger names, decimal validation and balanced entries. Manual review/import only; not a GST invoice or a posted transaction.
- Authenticated connection diagnostics and honest capability statuses.
- Mission text export and portable JSON backup/restore with no-overwrite validation; Android can save these files. Backups contain mission results only, not worker registry, configuration or credentials.
- Startup recovers interrupted missions into blocked/retryable status. Restoring a backup never runs work.
- Free Render deployment blueprint; optional Docker Compose with persistent mission data volume. Production refuses to start without a token of at least 24 characters.
- Automatic installable **debug preview** APK release after successful Android CI on main.

## Verified in this development session
- Live Exa search returned TTDC official-source links; URL fetching returned the TTDC contact page.
- Backend unit/integration tests exercise protocol errors, rate limits, shared search, sources, cancellation, approval, fallback, voucher accounting/escaping, and backup restore.
- Check the exact commit's Actions results for Android compilation and APK availability.

## Not production-complete
- Hosted deployment and a model endpoint still require successful live connection checks. Render free services have ephemeral storage and sleep when idle. Export backups; do not rely on that tier for durable business records.
- The backend has not been deployed by this work; Render workspace confirmation remains pending.
- GST filing, Tally writeback, email/calendar actions and social publishing are not connected. These require account-specific integrations and must not be described as completed by generated text.
- Technical SEO metrics, social analytics and verified buying intent are not measured.
- Background wake-word operation, recurring mission scheduling, multi-user isolation and production release signing are not implemented.
- Phone installation, microphone behavior and Android Keystore behavior require an actual device test. Debug signing can change between CI runners; this is not a guaranteed in-place upgrade path.

## Runtime limits
Run one Uvicorn worker. Active cancellation is process-local. SQLite persists on a persistent volume, but free Render's filesystem does not survive replacement. Each source/model request is bounded; free remote providers can reject or rate-limit requests. No unlimited-service guarantee is made.

## Android 0.4 direct AI chat

The default AI tab connects directly to OpenCode Zen, independent of the MARAN backend.
It discovers active zero-cost models from models.dev on every send, supports chat completions, Responses and Anthropic Messages, and excludes deprecated/unknown/paid pricing. Auto mode tries at most three free models on availability errors only; authentication, billing and rate limits stop immediately. Optional Zen keys are encrypted separately using Android Keystore. Chat is memory-only, text-only and sends the last ten messages as context.

This integration does not guarantee keyless access: the live official CLI previously returned HTTP 403 restricting the free tier to OpenCode. No access restriction is bypassed. Provider pricing/catalog information can change, and a catalog listing is not an access entitlement. Backend missions remain a separate feature requiring a deployed server.
