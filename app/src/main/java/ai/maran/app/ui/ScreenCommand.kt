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
        "read this screen","what is on my screen","what's on my screen") ||
        Regex("""^(tap|press|select|click)\s+\S.+$""").matches(normalized)
}

@Composable
internal fun rememberScreenCommand(vm:AiChatViewModel):(String)->Boolean {
    val context=LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    if(pending!=null) AlertDialog(
        onDismissRequest={pending=null},
        title={Text("Enable MARAN Device Control?")},
        text={Text("This optional Android Accessibility service can inspect visible accessible text and operate a matching element only after your command. Enable it manually in Android Settings. It cannot bypass passwords, OTPs or secure prompts.")},
        confirmButton={TextButton(onClick={
            pending=null
            try {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                vm.voiceError("Enable MARAN Device Control in Accessibility settings, then repeat your command.")
            } catch(_:Exception) {
                vm.voiceError("Could not open Accessibility settings.")
            }
        }){Text("Open Settings")}},
        dismissButton={TextButton(onClick={pending=null}){Text("Cancel")}}
    )
    return { command ->
        if(!screenCommand(command)) false
        else {
            if(!MaranAccessibilityService.available()) {
                pending=command
                vm.voiceError("Device Control is disabled. Enable it only if you want MARAN to perform screen commands.")
            } else {
                val normalized=command.trim().replace(Regex("""^(?i:hey\s+)?(?i:maran)[,:\s!.]*"""),"").trim()
                val result=MaranAccessibilityService.run(normalized)
                vm.recordPhoneAction(command,result.message)
            }
            true
        }
    }
}
