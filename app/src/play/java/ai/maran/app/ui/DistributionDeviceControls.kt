package ai.maran.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun DistributionDeviceControls(onStatus:(String)->Unit) {
    MaranSectionHeading("Play-safe phone controls","Built for Google Play policy compatibility")
    Text(
        "This Google Play build does not include AccessibilityService, broad contact access, or broad launcher-app inventory. MARAN still supports voice, AI agents, background missions, flashlight, volume, alarms, dialer/contact picker, known-app intents and other narrowly scoped Android APIs."
    )
    MaranPanel(Modifier) {
        Text("Autonomous agents and phone UI control are separated: background/AI missions cannot drive another app's interface.")
        Text("Calls open the Android dialer. Named contacts use the system picker. You stay in control of consequential actions.")
    }
}
