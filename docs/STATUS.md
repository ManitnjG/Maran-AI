# MARAN 0.3 readiness

## Implemented
- Native Android connection setup with Android Keystore encrypted access token, Tamil/English selection, and explicit microphone state.
- Opt-in recognition of “Maran” / “மாறன்” while the MARAN screen is visible. Stops on screen exit or app background. This is not a low-power background wake engine.
- Missions, manager assignment, named workers, approvals, retries, stop/cancellation, duplicate-run suppression and saved step outputs.
- Live public web search and URL reading through the documented Exa MCP service without a key (rate limited). Research workers share one query. Results retain source URLs, retrieval time and published contacts; prospects are not asserted to be interested buyers.
- Model draft generation with configured-provider fallback. Optional local Ollama needs no model API key.
- Optional OpenCode CLI free-model adapter with all tools denied and isolated temporary configuration. Disabled by default: a live CLI probe in this environment returned a provider 403 free-tier restriction. No impersonation headers or bypasses are used. This adapter is not currently live-verified.
- Tally sales accounting-voucher XML export with user-entered amounts and exact ledger names, decimal validation and balanced entries. Manual review/import only; not a GST invoice or a posted transaction.
- Authenticated connection diagnostics and honest capability statuses.
- Mission text export and portable JSON backup/restore with no-overwrite validation; Android can save these files. Backups contain mission results only, not worker registry, configuration or credentials.
- Startup recovers interrupted missions into blocked/retryable status. Restoring a backup never runs work.
- Free Render deployment blueprint; optional Docker Compose with persistent data volume and Ollama. Production refuses to start without a token of at least 24 characters.
- Automatic installable **debug preview** APK release after successful Android CI on main.

## Verified in this development session
- Live Exa search returned TTDC official-source links; URL fetching returned the TTDC contact page.
- Backend unit/integration tests exercise protocol errors, rate limits, shared search, sources, cancellation, approval, fallback, voucher accounting/escaping, and backup restore.
- Check the exact commit's Actions results for Android compilation and APK availability.

## Not production-complete
- Hosted deployment and a model endpoint still require successful live connection checks. Render free services have ephemeral storage and sleep when idle. Export backups; do not rely on that tier for durable business records.
- The connected Render plugin was not exposing deployment actions in this session when inspected; the prepared blueprint has not been deployed by this work yet.
- GST filing, Tally writeback, email/calendar actions and social publishing are not connected. These require account-specific integrations and must not be described as completed by generated text.
- Technical SEO metrics, social analytics and verified buying intent are not measured.
- Background wake-word operation, recurring mission scheduling, multi-user isolation and production release signing are not implemented.
- Phone installation, microphone behavior and Android Keystore behavior require an actual device test. Debug signing can change between CI runners; this is not a guaranteed in-place upgrade path.

## Runtime limits
Run one Uvicorn worker. Active cancellation is process-local. SQLite persists on a persistent volume, but free Render's filesystem does not survive replacement. Each source/model request is bounded; free remote providers can reject or rate-limit requests. No unlimited-service guarantee is made.
