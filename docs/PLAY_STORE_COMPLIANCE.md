# Google Play / Play Protect Release Checklist

This checklist applies to the **play** flavor only. The **full** flavor is a private/GitHub build and must not be uploaded to Google Play.

## Implemented in code

- Target/compile Android 16 API 36.
- Play flavor contains no AccessibilityService declaration or AccessibilityService subclass.
- Play flavor contains no broad launcher-app inventory query.
- No `QUERY_ALL_PACKAGES`.
- No `READ_CONTACTS`; named calls use the Android system contact picker.
- No SMS, call-log, location, install-package, device-admin, VPN, exact-alarm, overlay, or notification-listener permissions.
- Microphone is user-triggered for in-app speech recognition; Android privacy indicators remain visible.
- Camera permission is used for flashlight control only.
- Notification permission is optional and used only for mission status/approval alerts.
- Local Android tool results are excluded from cloud-AI history.
- Knowledge-file AI context is opt-in per upload.
- External connector actions are separately displayed and confirmed in Approval Centre.
- A confirmed in-app remote workspace deletion path is implemented.
- Play release builds require an HTTPS MARAN backend.
- Release shrinking/obfuscation is enabled.
- Play and Full variants are built separately.
- A signed Play AAB workflow is prepared; signing secrets remain outside GitHub source.

## Required before Play Console submission

1. Enroll the app in **Play App Signing** and create/secure an upload key. Put only the base64 upload-keystore and passwords into GitHub Actions secrets if CI signing is desired.
2. Configure `MARAN_API_BASE_URL` as a production HTTPS endpoint.
3. Replace the placeholder contact section in `docs/PRIVACY_POLICY.md`, publish it at a stable public URL, and add that URL in Play Console.
4. Complete the **Data safety** form from actual production behavior. Re-check every connected third-party AI/integration service before answering the form.
5. Complete content rating, target audience, ads declaration, and any AI-related disclosures requested by Play Console.
6. Upload **only** `app-play-release.aab`. Never upload the Full flavor.
7. Run Play Console's pre-launch report and fix crashes, ANRs, permission warnings, security findings, and policy declarations before production rollout.
8. Start with Internal testing, then Closed testing, then Production after review.

## Accessibility boundary

The Full flavor has an optional AccessibilityService for direct user commands. Google Play policy prohibits using AccessibilityService for autonomous initiation/planning/execution. For that reason the Play flavor omits AccessibilityService entirely. Autonomous backend missions may still research, draft, verify, retry, schedule background work, and use separately permitted integrations, but they cannot drive other Android apps through Accessibility in the Play build.

## Package visibility boundary

The Play flavor declares only a finite set of known packages needed for interoperability. Broad launchable-app inventory is reserved for the Full flavor. Do not add `QUERY_ALL_PACKAGES` unless a future core use case independently satisfies Google Play's restricted permission policy and the required declaration is approved.

## Release artifact

Run **Build signed Play AAB** after configuring the required secrets. The resulting `maran-play-signed-aab` artifact is the candidate for Play Console upload. Google Play review and Play Protect behavior remain Google's decision; passing this checklist is not a guarantee of approval.
