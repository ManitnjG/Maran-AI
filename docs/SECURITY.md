# Security & Control Model

1. Least privilege by default.
2. Every tool has declared permissions.
3. Child agents inherit a subset of mission permissions.
4. External side effects can require explicit approval.
5. OTP, CAPTCHA, authentication and security controls are never bypassed.
6. Tax/accounting outputs are drafts until validated and authorized where required.
7. Secrets are encrypted and excluded from prompts/logs where possible.
8. Mission/tool actions are auditable.
9. Untrusted web/file content is data, not authority; agents must resist prompt injection.
10. Destructive actions require elevated confirmation and should support preview/undo when possible.
11. Public lead generation must respect privacy, platform terms and anti-spam rules.
12. Model fallback preserves task state but does not circumvent provider policy or access restrictions.
