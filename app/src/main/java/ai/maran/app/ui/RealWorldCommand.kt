package ai.maran.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.provider.AlarmClock
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

internal fun realWorldCommand(text: String): Pair<String,String>? {
    val trimmed=text.trim()
    val deviceActions=mapOf(
        "camera" to "camera", "the camera" to "camera", "camera app" to "camera",
        "gallery" to "gallery", "photos" to "gallery", "photo gallery" to "gallery",
        "wifi settings" to "wifi", "wi-fi settings" to "wifi",
        "bluetooth settings" to "bluetooth", "phone settings" to "settings",
        "settings" to "settings", "display settings" to "display",
        "alarm" to "alarm", "alarms" to "alarm",
        "whatsapp" to "whatsapp", "youtube" to "youtube", "chrome" to "chrome",
        "gmail" to "gmail", "instagram" to "instagram"
    )
    if (Regex("""^(?:please\s+)?(?:take\s+(?:a\s+)?photo|take\s+(?:a\s+)?picture|turn\s+on\s+(?:the\s+)?camera|launch\s+(?:the\s+)?camera)\s*[.!]?$""",RegexOption.IGNORE_CASE).matches(trimmed)) return "device" to "camera"
    val open=Regex("""^(?:please\s+)?(?:open|launch|start|show)\s+(.+?)\s*[.!]?$""",RegexOption.IGNORE_CASE)
        .matchEntire(trimmed)?.groupValues?.get(1)?.trim()?.lowercase()
    if(open!=null && deviceActions.containsKey(open)) return "device" to deviceActions.getValue(open)
    val commands=listOf(
        Triple(Regex("""^(?:please\s+)?(?:search(?:\s+the\s+web)?\s+for|google)\s+(.+)$""",RegexOption.IGNORE_CASE),"web","Web search"),
        Triple(Regex("""^(?:please\s+)?(?:show|find|open)\s+(.+?)\s+(?:on|in)\s+(?:google\s+)?maps$""",RegexOption.IGNORE_CASE),"map","Maps search"),
        Triple(Regex("""^(?:please\s+)?(?:navigate|directions)\s+to\s+(.+)$""",RegexOption.IGNORE_CASE),"navigate","Navigation"),
        Triple(Regex("""^(?:please\s+)?(?:compose|draft|write)\s+(?:an?\s+)?email\s*(?:about\s+|to\s+)?(.+)$""",RegexOption.IGNORE_CASE),"email","Email draft")
    )
    for((pattern,type,_) in commands) {
        val value=pattern.matchEntire(trimmed)?.groupValues?.get(1)?.trim().orEmpty()
        if(value.isNotBlank()) return type to value.take(500)
    }
    return null
}

@Composable
internal fun rememberRealWorldCommand(vm: AiChatViewModel): (String) -> Boolean {
    val context=LocalContext.current
    return { command ->
        val parsed=realWorldCommand(command)
        if(parsed==null) false
        else {
            val (type,value)=parsed
            try {
                val intent=when(type) {
                    "device" -> when(value) {
                        "camera" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                        "gallery" -> Intent(Intent.ACTION_GET_CONTENT).apply { type="image/*"; addCategory(Intent.CATEGORY_OPENABLE) }
                        "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
                        "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                        "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
                        "settings" -> Intent(Settings.ACTION_SETTINGS)
                        "alarm" -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
                        else -> {
                            val packages=mapOf(
                                "whatsapp" to "com.whatsapp",
                                "youtube" to "com.google.android.youtube",
                                "chrome" to "com.android.chrome",
                                "gmail" to "com.google.android.gm",
                                "instagram" to "com.instagram.android"
                            )
                            val pkg=packages[value] ?: error("Unsupported app")
                            context.packageManager.getLaunchIntentForPackage(pkg)
                                ?: throw IllegalStateException(value+" is not installed or cannot be opened.")
                        }
                    }
                    "web" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q="+Uri.encode(value)))
                    "map" -> Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+Uri.encode(value)))
                    "navigate" -> Intent(Intent.ACTION_VIEW,Uri.parse("google.navigation:q="+Uri.encode(value)))
                    else -> Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:")).apply {
                        putExtra(Intent.EXTRA_SUBJECT,value)
                    }
                }
                val safeIntent=if(type=="email") Intent.createChooser(intent,"Choose email app") else intent
                context.startActivity(safeIntent)
                vm.recordPhoneAction(command, when(type) {
                    "device" -> "Requested Android to open: "+value+". Check the opened app or settings screen."
                    "web" -> "Opened browser search for: "+value+". Review the results and their sources."
                    "map" -> "Opened Maps search for: "+value+"."
                    "navigate" -> "Opened navigation for: "+value+". Confirm the route before travelling."
                    else -> "Opened an email draft about: "+value+". Review it before sending; no message was sent."
                })
            } catch(e:Exception) {
                vm.recordPhoneAction(command,"Could not open a supporting app: "+(e.message ?: "unavailable"))
            }
            true
        }
    }
}
