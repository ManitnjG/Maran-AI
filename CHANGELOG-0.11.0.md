# MARAN 0.11.0 — Action Agent

## New

- **Action Agent runtime**: completed mission drafts can propose a structured external action from an application-controlled allow-list. The AI cannot invent a new tool or bypass connector permissions.
- **Approval Centre**: mission-plan approval and real external-action approval are separated. The exact tool, connection status and arguments are shown before execution.
- **Real approved actions**: Gmail send, Calendar event creation, Drive text upload, GitHub workflow/file actions, WhatsApp, Facebook, Instagram, LinkedIn, Twilio SMS/call and Tally posting use the existing opt-in connector layer.
- **Side-effect verification**: Gmail, Calendar and Drive actions attempt a read-back verification when the connected OAuth scope permits it; otherwise MARAN reports provider acknowledgement only.
- **Deep Research**: broader public search, source dedupe and bounded follow-up reads of top public sources.
- **Practical workers**: Tour Itinerary & Quotation, Marketing, Email, Calendar, Business Admin and Knowledge workers join the existing Research, Tour Lead, SEO, Social, Documents, Accounting, Coding and Verifier workers.
- **Knowledge Files**: user-selected PDF, DOCX, TXT, Markdown, CSV and JSON files up to 5 MB can be stored and searched. AI context is a separate opt-in per file.
- **Knowledge search UI**: search uploaded reference files directly from Tools.
- **Mission notifications**: optional Android notifications for approval, completion, blocked and failed mission states.
- **Notification routing**: tapping an alert opens Approval Centre or Missions with the relevant mission surfaced first.
- **Voice 2.0**: in-app speech level meter, faster silence completion, Auto/English/Tamil modes, interruption by tapping the mic and optional hands-free follow-up after MARAN finishes speaking.
- **Remote data deletion**: a confirmed in-app control removes default-workspace missions, memory, learned workflows, Knowledge Files and built-in CRM leads from the configured MARAN backend.

## Safety / Play boundaries

- The Play flavor still contains no AccessibilityService.
- External side effects require a separate action approval.
- Recipient email/phone/image URL identifiers for supported outbound actions must be grounded in the mission objective or retrieved evidence.
- CAPTCHA, OTP, passwords, PINs, CVV, biometrics, payment authentication and security prompts remain non-automatable.
- Google Play and Full/private flavors remain separate.
- Knowledge Files are not automatically sent to AI providers; only matching snippets from files explicitly marked Allow AI context can be added to worker prompts.
- Notification permission is optional.
- Production Play builds still require HTTPS and a separate upload signing key.

## Configuration still required

The relevant server connector must be configured before a real external action can succeed. MARAN reports needs_connection rather than pretending the action completed.

Google Play review/Play Protect classification remains Google's decision.
