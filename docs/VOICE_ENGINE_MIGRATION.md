# MARAN Voice Engine migration (incremental)

This document tracks implemented source changes, not claims of production readiness.

## Existing modules to preserve
- Kotlin/Jetpack Compose: MaranApp, AiChatScreen, PremiumHomeScreen, ToolsScreen.
- OpenRouterClient / AiChatViewModel: streaming chat and fallback.
- MaranViewModel and existing Python orchestrator: Manager, workers, research and mission handling.
- SpeechController and opt-in WakeWordController: Android speech entry points.
- SecureTokenStore and local worker storage.

## Architecture and boundaries
Voice/text → deterministic local parser → validated native AndroidToolRegistry → actual ActionResult → chat / TTS.
Unmatched requests → existing action handlers → existing Manager / AI chat as configured.
Android permissions are requested at use time. External actions are prepared in the appropriate app and require user confirmation. Tool execution is never inferred from AI prose.

## Implemented in this stage
- LocalCommandEngine parses offline torch, media volume and timer requests.
- AndroidToolRegistry checks torch hardware; applies and reads back stream volume; opens the system timer flow.
- Optional runtime camera permission, microphone flow preserved.
- Lifecycle-managed TextToSpeech feedback for a new spoken response, and stop-speaking command.
- Parser unit tests.
- Existing UI, tools, Manager and workers retained.

## Further stages / not yet implemented
- Full streaming SpeechRecognizer UI, partial speech and VAD.
- General voice orb and background wake word reliability on all Android devices.
- Shared capability/permission/risk registry for every existing action.
- User-approved AccessibilityService screen interaction, semantic disambiguation and read screen.
- Editable persistent skills, demonstration learning and memory isolation.
- Multi-step task planner, persistent background execution and connector OAuth flows.
- Complete release signing, on-device tests and accessibility/privacy audit.

## Acceptance checklist
1. CI passes Kotlin compilation and unit tests.
2. On device: tap mic → "Maran, turn on flashlight" → grant camera access if required → torch comes on.
3. Assistant reports only API execution result and speaks the new result.
4. "Stop speaking" interrupts speech; "flashlight off" switches off.
5. Test unsupported/no-flash hardware and denied permission.
6. Confirm existing chat/Manager screens still work and no secrets enter builds.
