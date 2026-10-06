package ai.maran.app.control

/**
 * Google Play distribution deliberately excludes AccessibilityService.
 * Standard Android intents and on-device APIs remain available.
 */
class MaranAccessibilityService {
    companion object {
        fun available(): Boolean = false
        fun run(command: String): ScreenResult = ScreenResult(
            false,
            "Screen control is not included in the Google Play build. Use supported Play-safe phone actions instead."
        )
    }
}

data class ScreenResult(val success: Boolean, val message: String)
