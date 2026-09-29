# Device workspace fix (0.9.1)

The previous APK defaulted to the emulator host 10.0.2.2, which is not a deployed server available from a physical phone. Workers and task creation were therefore disabled.

Device workspace is now the default for blank/emulator/localhost configuration. Explicit custom server URLs keep server mode; a disconnected server offers an explicit Use device action. Device and server workspaces remain separate.

Device workers and tasks are persisted on the phone. Four editable-through-add/remove worker presets provide drafting skills. Task execution uses the existing encrypted OpenRouter key, makes an actual AI request, saves output or failure, and supports cancellation. Missing keys produce a blocked task with setup/retry instructions. Tasks interrupted by process death are marked interrupted on next launch.

Device mode produces AI drafts only. It does not browse live sources, send messages, file returns, or continue execution after the process ends. These limits are shown in the task creation UI and result. No fake source links or contacts are inserted. Server tools remain available when a reachable server is explicitly configured.

Validation: unit tests cover emulator migration, custom server preservation, worker routing, and process-death recovery. Android CI compiles/tests/builds the APK. Live OpenRouter requests and physical-device verification require the user device/key.
