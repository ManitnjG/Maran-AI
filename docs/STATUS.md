# MARAN implementation status

## Working in code
- Android server URL configuration (no rebuild needed), session-only access token, text/voice commands, worker listing/removal, full step output, approve/reject, retry and stop.
- Durable mission plans, outputs, events and context in SQLite; completed steps survive retries.
- Approval resumes execution. Cancelled/completed missions cannot be rerun. Concurrent run requests share one task. Stop cancels in-flight model requests.
- Configured model draft generation, fallback, empty/malformed response handling, optional keyless Ollama endpoint.
- Missing providers produce blocked missions; drafts remain unverified. No fake successful tool execution.
- Optional bearer-token gate. Use HTTPS and set MARAN_ACCESS_TOKEN before exposing the server.
- Temporary workers stay available for blocked missions; explicit cleanup preserves workers used by unfinished missions.

## Requires deployment or external services
- A running backend and model endpoint. The APK alone does not host a language model.
- Ollama needs an operator-managed machine/model; it removes API-key requirements, not compute requirements. OpenCode Zen still requires its provider credentials in this adapter.
- Real public-web research, verified lead discovery, email/calendar, social publishing, GST filing and Tally writeback adapters are not implemented. Draft generation must not be represented as these actions.
- OpenCode CLI helper exists with timeout/cancellation, but is not invoked automatically by missions. Sandboxed workspace provisioning and scoped approval are needed before enabling autonomous code execution.
- Always-listening wake word, schedules/notifications, account isolation, encrypted secret storage, production signed release and backup/restore management remain open.

## Operational limits
Run one Uvicorn process: active execution ownership is process-local. SQLite persists results, but after a process restart an interrupted mission must be retried manually. The app stores only the server address; re-enter its access token on restart. This is a personal single-operator service, not a multi-tenant product.
