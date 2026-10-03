package ai.maran.app.ui

import android.content.Intent
import android.net.Uri
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
        title={Text("Enable MARAN Device Control?")},
        text={Text("This optional service can inspect visible screen text and perform supported screen actions after your command. If Android says Restricted setting, open MARAN App Info and use its three-dot menu to Allow restricted settings (if offered) for an APK you trust. Then enable MARAN Device Control in Accessibility settings. It cannot bypass passwords, OTPs, payments or Android security prompts.")},
        confirmButton={TextButton(onClick={
            pending=null
            try {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                vm.voiceError("Enable MARAN Device Control in Accessibility settings, then repeat your command.")
            } catch(_:Exception) {
                vm.voiceError("Could not open Accessibility settings.")
            }
        }){Text("Open Settings")}},
        dismissButton={
            Row {
                TextButton(onClick={
                    pending=null
                    try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package",context.packageName,null))) }
                    catch(_:Exception) { vm.voiceError("Could not open MARAN App Info.") }
                }) { Text("App Info") }
                TextButton(onClick={pending=null}) { Text("Cancel") }
            }
        }
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
