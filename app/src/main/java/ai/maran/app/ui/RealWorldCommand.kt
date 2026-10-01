package ai.maran.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

internal fun realWorldCommand(text: String): Pair<String,String>? {
    val trimmed=text.trim()
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
