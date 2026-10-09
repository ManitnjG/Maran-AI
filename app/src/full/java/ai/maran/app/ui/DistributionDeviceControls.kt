package ai.maran.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ai.maran.app.BuildConfig
import ai.maran.app.control.MaranAccessibilityService

@Composable
internal fun DistributionDeviceControls(onStatus:(String)->Unit) {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(MaranAccessibilityService.available()) }
    var showDisclosure by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<String?>(null) }

    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _,event ->
            if(event==Lifecycle.Event.ON_RESUME) enabled=MaranAccessibilityService.available()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    if(showDisclosure) AlertDialog(
        onDismissRequest={showDisclosure=false},
        title={Text("MARAN Device Control disclosure")},
        text={Text(
            "Device Control is optional. It can read visible text and UI labels on the active screen and perform only an explicit Back, Home, Recents, scroll or exact-label tap command that you give MARAN. It does not passively log screen events. It will not act on passwords, OTP, CAPTCHA, payments, permissions, sign-in or security prompts. Device-control results are kept local and excluded from cloud AI context. You can disable the service at any time in Android Accessibility settings."
        )},
        confirmButton={TextButton(onClick={
            showDisclosure=false
            try { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            catch(_:Exception) { onStatus("Accessibility settings unavailable on this phone") }
        }){Text("I agree — open settings")}},
        dismissButton={TextButton(onClick={showDisclosure=false}){Text("Cancel")}}
    )

    MaranSectionHeading("Voice Device Control","Optional, user-initiated controls in the Full build")
    Text(if(enabled) "Device Control is enabled" else "Device Control is disabled")
    Text(
        "This Full build is intended for private/GitHub distribution. Autonomous missions never invoke Accessibility. Screen actions run only after your direct command.",
        style=MaterialTheme.typography.bodySmall
    )
    MaranPrimaryButton(
        label=if(enabled) "Review Device Control settings" else "Review disclosure & enable",
        onClick={showDisclosure=true}
    )
    if(BuildConfig.DEBUG) {
        Text(
            "Android may restrict Accessibility for sideloaded debug APKs. MARAN does not bypass that protection.",
            style=MaterialTheme.typography.bodySmall
        )
        MaranSecondaryButton(label="Open MARAN App Info",onClick={
            try {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package",context.packageName,null)))
            } catch(_:Exception) { onStatus("Could not open MARAN App Info") }
        })
    }

    MaranSectionHeading("Apps on this phone")
    Text(
        "The Full build can list launchable apps visible through Android package visibility. This list stays local and is excluded from cloud AI context.",
        style=MaterialTheme.typography.bodySmall
    )
    MaranSecondaryButton(label="List launchable apps",onClick={installedApps=DeviceAppInventory.listLaunchable(context)})
    installedApps?.let { SelectionContainer { Text(it,style=MaterialTheme.typography.bodySmall) } }
}
