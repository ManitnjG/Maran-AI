package ai.maran.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

private data class PhoneChoice(val name: String, val number: String)

internal fun phoneCommandTarget(text: String): String? {
    val match = Regex("""^(?:please\s+)?(?:call|dial|phone)\s+(.+?)\s*[.!]?$""", RegexOption.IGNORE_CASE)
        .matchEntire(text.trim()) ?: return null
    return match.groupValues[1].trim().takeIf { it.isNotEmpty() }
}

@Composable
internal fun rememberPhoneCommand(vm: AiChatViewModel): (String) -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    var requested by remember { mutableStateOf<String?>(null) }
    var choices by remember { mutableStateOf<List<PhoneChoice>>(emptyList()) }
    var showChoices by remember { mutableStateOf(false) }

    fun lookup(target: String) {
        val found = mutableListOf<PhoneChoice>()
        try {
            val name = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            val number = ContactsContract.CommonDataKinds.Phone.NUMBER
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(name, number), null, null, name + " COLLATE NOCASE ASC"
            )?.use { cursor ->
                val ni = cursor.getColumnIndexOrThrow(name)
                val pi = cursor.getColumnIndexOrThrow(number)
                while (cursor.moveToNext()) {
                    val n = cursor.getString(ni).orEmpty()
                    val phone = cursor.getString(pi).orEmpty()
                    if (n.contains(target, ignoreCase = true) && phone.isNotBlank() &&
                        found.none { it.name == n && it.number == phone }) {
                        found.add(PhoneChoice(n, phone))
                        if (found.size >= 20) break
                    }
                }
            }
            choices = found.sortedWith(compareBy<PhoneChoice> { !it.name.equals(target, ignoreCase = true) }.thenBy { it.name }).take(10)
            showChoices = choices.isNotEmpty()
            if (choices.isEmpty()) {
                vm.recordPhoneAction("call " + target, "No saved contact matched '" + target + "'. Check the name in Contacts.")
                requested = null
            }
        } catch (_: SecurityException) {
            vm.voiceError("Please allow Contacts access to find a saved phone number.")
            requested = null
        } catch (e: Exception) {
            vm.voiceError("Unable to search contacts: " + (e.message ?: "unknown error"))
            requested = null
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val name = requested
        if (granted && name != null) lookup(name)
        else {
            vm.voiceError("Contacts access was declined. No call was made.")
            requested = null
        }
    }

    if (showChoices) AlertDialog(
        onDismissRequest = { showChoices = false; requested = null },
        title = { Text("Choose contact to dial") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Check the contact and number. MARAN opens the dialer; you confirm the call.")
                choices.forEach { choice ->
                    TextButton(
                        onClick = {
                            showChoices = false
                            requested = null
                            try {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(choice.number))))
                                vm.recordPhoneAction("call " + choice.name, "Opened the dialer for " + choice.name + ". Confirm the call in your phone app.")
                            } catch (e: Exception) {
                                vm.recordPhoneAction("call " + choice.name, "Couldn't open the dialer: " + (e.message ?: "Phone app unavailable") + ".")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(choice.name + " • " + choice.number) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showChoices = false; requested = null }) { Text("Cancel") } }
    )

    return { text ->
        val target = phoneCommandTarget(text)
        if (target == null) vm.send(text)
        else {
            requested = target
            if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) lookup(target)
            else permission.launch(Manifest.permission.READ_CONTACTS)
        }
    }
}
