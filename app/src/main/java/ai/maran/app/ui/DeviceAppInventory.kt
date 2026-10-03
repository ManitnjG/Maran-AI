package ai.maran.app.ui

import android.content.Context
import android.content.Intent
import java.util.Locale

/**
 * Only an explicit installed-app inventory question invokes the on-device package manager.
 * Never upload the results to an AI provider; package visibility is limited to launchable apps.
 */
internal object DeviceInventoryCommand {
    fun matches(raw: String): Boolean {
        val text = raw.trim()
            .replace(Regex("""^(?i:(?:hey\s+)?maran)\s*[,.:!]*\s*"""), "")
            .trim().lowercase(Locale.ROOT)
        val asksForList = Regex(
            """^(?:please\s+)?(?:(?:list|show|display|name)(?:\s+me)?|(?:what|which)\b|tell\s+me\b)"""
        ).containsMatchIn(text)
        val mentionsApps = Regex("""\b(?:apps?|applications?)\b""").containsMatchIn(text)
        val otherAction = Regex(
            """\b(?:open|launch|start|install|uninstall|delete|remove|settings?|permissions?|recent|running|develop|create|recommend|best)\b"""
        ).containsMatchIn(text)
        return asksForList && mentionsApps && !otherAction
    }
}

internal object DeviceAppInventory {
    private data class Entry(val label: String, val packageName: String)

    fun listLaunchable(context: Context): String = try {
        val manager = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val activities = manager.queryIntentActivities(launcher, 0)
        val entries = activities.mapNotNull { info ->
            val label = info.loadLabel(manager).toString().trim()
            val pkg = info.activityInfo?.packageName.orEmpty()
            if (label.isBlank() || pkg.isBlank()) null else Entry(label, pkg)
        }.distinctBy { it.packageName }
            .sortedWith(compareBy<Entry> { it.label.lowercase(Locale.ROOT) }.thenBy { it.packageName })

        if (entries.isEmpty()) {
            "Android returned no visible launchable apps. Check that MARAN is installed normally and try again."
        } else {
            val duplicateNames = entries.groupingBy { it.label.lowercase(Locale.ROOT) }.eachCount()
            val shown = entries.take(500)
            val lines = shown.mapIndexed { index, app ->
                val name = if ((duplicateNames[app.label.lowercase(Locale.ROOT)] ?: 0) > 1)
                    "${app.label} (${app.packageName})" else app.label
                "${index + 1}. $name"
            }
            "${entries.size} launchable apps visible to MARAN on this phone:\n" +
                lines.joinToString("\n") +
                (if (entries.size > shown.size) "\nShowing the first ${shown.size}." else "") +
                "\nThis list comes directly from Android. Hidden, disabled and non-launcher system apps may not appear."
        }
    } catch (_: SecurityException) {
        "Android denied access to the launcher-app list. No apps were read."
    } catch (_: Exception) {
        "Android could not read launchable apps on this phone. Please try again."
    }
}
