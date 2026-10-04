# MARAN native UI component foundation

The app now uses a shared Material 3 Compose theme in `app/src/main/java/ai/maran/app/ui/MaranDesignSystem.kt`.

Includes:
- Centralized color, shape, surface, border and spacing tokens.
- `MaranPrimaryButton`, `MaranSecondaryButton`, `MaranPanel`, `MaranInput` and `MaranSectionHeading`.
- Consistent bottom navigation, Home action area, AI chat controls, Tool inputs and key Mission/Worker controls.
- Existing routes, handlers, worker execution, permission checks, on-device app listing and AI provider logic are preserved.

## External design reference still blocked

Requested Figma file: https://www.figma.com/design/UgZiytwte7RBrpCy6DobZa/Manoj-design-system?node-id=2200-442079

The connected Figma API explicitly returned *doesn't have edit access* for node 2200:442079. The colors and components in this PR are therefore a normalization of MARAN's existing native UI, **not a claim of exact Figma fidelity**. Do not copy, guess, or label tokens as from the unavailable Figma file. Once access is granted, read the actual component definitions, compare their tokens, and update this shared layer with verified values instead of rewriting feature screens.

## Testing

Android CI runs Kotlin unit tests and debug APK assembly. Visual parity must be verified after Figma access, and Android interaction remains subject to on-device checks.
