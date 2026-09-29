# MARAN premium interface

Version 0.9 introduces a dark charcoal and mint Compose interface with Assistant, Workers, and Activity navigation. Settings are accessible through the profile icon. The design uses real API state, not sample contacts or invented progress.

Assistant preserves streaming OpenRouter responses and cancellation, supports copy and confirmed chat clearing, and adds English/Tamil dictation into an editable composer. Quick actions prefill prompts or open a task form. API key configuration is in Settings.

Workers shows server-loaded names and skills, with worker creation and confirmed stop actions. Activity opens task detail with completed-step progress, source links, approval controls, retry, cancellation and selectable exports. Server failures have retry/settings actions. Voice language persists across launches.

Validation checklist on an Android device:
- Open with no key/server: honest setup and empty states, no sample data.
- Save key; send/stream/stop/copy; clear confirmation; reopen Settings.
- Dictate English/Tamil; edit transcription before sending.
- Connect server; add worker; create task; open detail; refresh; approve/retry/cancel.
- Test keyboard, back navigation, large font, and short screen heights.

No new backend capabilities or attachment handling are implied by this UI update.
