# Maran 0.8.0 - External Actions

Added opt-in integrations for Gmail send/read/reply, Google Calendar event creation, Google Drive text upload, GitHub file updates and workflow dispatch, WhatsApp Cloud text sending, Facebook/Instagram/LinkedIn publishing, Twilio SMS/voice calls, Tally XML posting, and a built-in CRM lead store.

Consequential actions require explicit `confirmed: true`. Credentials are never bundled and must be provided through environment variables. Capability readiness is exposed by `GET /integrations` and displayed in Android Tools > Readiness.

GST filing/login, direct payment execution, unrestricted device control, CAPTCHA bypass and OTP interception remain unsupported. YouTube upload and broad social analytics are connector scaffolds for a future release.

Validation: backend test suite 86/86 passed against a fresh SQLite database.
