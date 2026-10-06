package ai.maran.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import ai.maran.app.control.MaranAccessibilityService

internal fun screenCommand(text:String):Boolean {
    val normalized=text.trim().replace(Regex("""^(?i:hey\s+)?(?i:maran)[,:\s!.]*"""),"").lowercase()
    return normalized in setOf("go back","back","go home","home screen","scroll down","scroll up",
        "read this screen","what is on my screen","what's on my screen",
        "show recent apps","open recent apps","recent apps","show my recent apps","show recent applications") ||
        Regex("""^(tap|press|select|click)\s+\S.+$""").matches(normalized)
}

@Composable
internal fun rememberScreenCommand(vm:AiChatViewModel):(String)->Boolean {
    val context=LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }

    if(pending!=null) AlertDialog(
        onDismissRequest={pending=null},
        title={Text("Enable optional Device Control?")},
        text={Text(
            "MARAN Device Control can read visible text and UI labels on the active screen and perform only your explicit Back, Home, Recents, scroll or exact-label tap command. It does not passively log screen events. It will not act on passwords, OTP, CAPTCHA, payments, permissions, sign-in or security prompts. Device-control results are kept local and excluded from cloud AI context. You can disable the service at any time in Android Accessibility settings."
        )},
        confirmButton={TextButton(onClick={
            pending=null
            try {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                vm.voiceError("Enable MARAN Device Control if you agree, then repeat your command.")
            } catch(_:Exception) {
                vm.voiceError("Could not open Accessibility settings.")
            }
        }){Text("I agree — open settings")}},
        dismissButton={TextButton(onClick={pending=null}){Text("Cancel")}}
    )

    return { command ->
        if(!screenCommand(command)) false
        else {
            if(!MaranAccessibilityService.available()) {
                pending=command
                vm.voiceError("Device Control is disabled. It is optional and requires your explicit consent.")
            } else {
                val normalized=command.trim().replace(Regex("""^(?i:hey\s+)?(?i:maran)[,:\s!.]*"""),"").trim()
                val result=MaranAccessibilityService.run(normalized)
                vm.recordPhoneAction(command,result.message)
            }
            true
        }
    }
}
