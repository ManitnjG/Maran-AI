package ai.maran.app.ui

import androidx.compose.runtime.Composable

internal fun screenCommand(text:String):Boolean {
    val normalized=text.trim().replace(Regex("""^(?i:hey\s+)?(?i:maran)[,:\s!.]*"""),"").lowercase()
    return normalized in setOf("go back","back","go home","home screen","scroll down","scroll up",
        "read this screen","what is on my screen","what's on my screen",
        "show recent apps","open recent apps","recent apps","show my recent apps","show recent applications") ||
        Regex("""^(tap|press|select|click)\s+\S.+$""").matches(normalized)
}

@Composable
internal fun rememberScreenCommand(vm:AiChatViewModel):(String)->Boolean = { command ->
    if(!screenCommand(command)) false
    else {
        vm.recordPhoneAction(
            command,
            "Screen-control automation is not included in the Google Play build. MARAN can still use supported Android intents and on-device APIs that keep you in control."
        )
        true
    }
}
