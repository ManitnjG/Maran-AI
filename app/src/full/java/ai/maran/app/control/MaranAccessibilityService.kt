package ai.maran.app.control

import android.content.Intent
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent

/** User-enabled and deliberately limited screen controls. Never used for passwords or authentication. */
class MaranAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile private var active: MaranAccessibilityService? = null
        fun available(): Boolean = active != null

        fun run(command: String): ScreenResult {
            val service = active ?: return ScreenResult(false, "Enable MARAN Device Control in Android Accessibility settings first.")
            return service.performCommand(command)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        }
        active = this
    }

    override fun onInterrupt() {}
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Never collect or persist passive events. Inspect only on an explicit user command.
    }
    override fun onUnbind(intent: Intent?): Boolean {
        if (active === this) active = null
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        if (active === this) active = null
        super.onDestroy()
    }

    private fun performCommand(raw: String): ScreenResult {
        val command = raw.trim().lowercase()
        if (command == "go back" || command == "back") {
            val success = performGlobalAction(GLOBAL_ACTION_BACK)
            return ScreenResult(success, if (success) "Went back." else "Android could not go back.")
        }
        if (command == "go home" || command == "home screen") {
            val success = performGlobalAction(GLOBAL_ACTION_HOME)
            return ScreenResult(success, if (success) "Opened the home screen." else "Android could not open Home.")
        }
        if (command == "show recent apps" || command == "open recent apps" || command == "recent apps" || command == "show my recent apps" || command == "show recent applications") {
            val success = performGlobalAction(GLOBAL_ACTION_RECENTS)
            return ScreenResult(success, if (success) "Opened recent apps." else "Android could not open recent apps.")
        }
        val root = rootInActiveWindow ?: return ScreenResult(false, "The current screen has no accessible content.")
        try {
            if (command == "scroll down" || command == "scroll up") {
                val action = if (command == "scroll down") AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                val candidate = nodes(root).firstOrNull { it.isScrollable && it.actionList.any { entry -> entry.id == action } }
                    ?: return ScreenResult(false, "No accessible scrollable area was found.")
                val done = candidate.performAction(action)
                return ScreenResult(done, if (done) "Scrolled "+command.removePrefix("scroll ")+ "." else "This screen did not scroll.")
            }
            if (command == "read this screen" || command == "what is on my screen" || command == "what's on my screen") {
                val description = nodes(root).filter { it.isVisibleToUser && !it.isPassword }
                    .mapNotNull { node ->
                        val t = node.text?.toString()?.trim().orEmpty().ifEmpty { node.contentDescription?.toString()?.trim().orEmpty() }
                        t.takeIf { it.isNotBlank() && it.length < 180 }
                    }.distinct().take(18).joinToString(". ")
                return ScreenResult(description.isNotBlank(),
                    if (description.isNotBlank()) "Visible screen content: "+description.take(1100)
                    else "There is no readable public text on this screen.")
            }
            val target = Regex("""^(?:tap|press|select|click)\s+(.+)$""").matchEntire(command)?.groupValues?.get(1)?.trim()
                ?: return ScreenResult(false, "Unsupported screen command.")
            if (target.length < 2 || Regex("""(?i)\b(password|otp|captcha|pay|purchase|buy|delete|remove|confirm|submit|send|transfer|allow|grant|install|authorize|verify|sign in|log in|withdraw)\b""").containsMatchIn(target))
                return ScreenResult(false, "That action needs direct review and confirmation in the app.")
            val matches = nodes(root).filter { node ->
                node.isVisibleToUser && !node.isPassword &&
                (node.text?.toString()?.trim()?.equals(target,ignoreCase=true)==true ||
                 node.contentDescription?.toString()?.trim()?.equals(target,ignoreCase=true)==true)
            }.distinctBy { it.viewIdResourceName to it.text?.toString() }
            if (matches.isEmpty()) return ScreenResult(false, "I could not find an accessible element named "+target+".")
            if (matches.size != 1) return ScreenResult(false, "There are multiple matches for "+target+". Please be more specific.")
            var clickable: AccessibilityNodeInfo? = matches.single()
            while (clickable != null && !clickable.isClickable) clickable=clickable.parent
            if (clickable == null || clickable.isPassword) return ScreenResult(false, "That element cannot be safely activated.")
            val success = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return ScreenResult(success, if (success) "Activated "+target+"." else "The app did not accept that action.")
        } finally {
            root.recycle()
        }
    }

    private fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result=mutableListOf<AccessibilityNodeInfo>()
        val pending=ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        while(pending.isNotEmpty() && result.size < 250) {
            val node=pending.removeFirst()
            result.add(node)
            for(i in 0 until node.childCount) node.getChild(i)?.let { pending.add(it) }
        }
        return result
    }
}

data class ScreenResult(val success: Boolean, val message: String)
