package ai.maran.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

internal fun phoneCommandTarget(text: String): String? {
    val match = Regex("""^(?:please\s+)?(?:call|dial|phone)\s+(.+?)\s*[.!]?$""", RegexOption.IGNORE_CASE)
        .matchEntire(text.trim()) ?: return null
    return match.groupValues[1].trim().takeIf { it.isNotEmpty() }
}

private fun looksLikeNumber(value:String):Boolean =
    value.count { it.isDigit() } >= 5 && value.all { it.isDigit() || it in " +-()#" }

@Composable
internal fun rememberPhoneCommand(vm: AiChatViewModel, fallback: (String) -> Unit = vm::send): (String) -> Unit {
    val context=LocalContext.current
    var requestedName by remember { mutableStateOf<String?>(null) }

    fun openDialer(label:String,number:String) {
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Uri.encode(number))))
            vm.recordPhoneAction("call $label","Opened the system dialer for $label. You choose whether to place the call.")
        } catch(e:Exception) {
            vm.recordPhoneAction("call $label","Couldn't open the dialer: "+(e.message ?: "Phone app unavailable")+".")
        }
    }

    val picker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri=result.data?.data
        val requested=requestedName
        requestedName=null
        if(result.resultCode!=Activity.RESULT_OK || uri==null) {
            if(requested!=null) vm.recordPhoneAction("call $requested","Contact selection was cancelled. No call was made.")
            return@rememberLauncherForActivityResult
        }
        try {
            val projection=arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            context.contentResolver.query(uri,projection,null,null,null)?.use { cursor ->
                if(!cursor.moveToFirst()) {
                    vm.voiceError("The selected contact has no readable phone number.")
                    return@use
                }
                val name=cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)).orEmpty()
                val number=cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)).orEmpty()
                if(number.isBlank()) vm.voiceError("The selected contact has no phone number.")
                else openDialer(name.ifBlank { requested ?: "selected contact" },number)
            } ?: vm.voiceError("Unable to read the selected contact.")
        } catch(e:Exception) {
            vm.voiceError("Unable to use the selected contact: "+(e.message ?: "unknown error"))
        }
    }

    return { text ->
        val target=phoneCommandTarget(text)
        if(target==null) fallback(text)
        else if(looksLikeNumber(target)) openDialer(target,target)
        else {
            requestedName=target
            try {
                val intent=Intent(Intent.ACTION_PICK,ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                picker.launch(intent)
            } catch(e:Exception) {
                requestedName=null
                vm.voiceError("The system contact picker is unavailable on this phone.")
            }
        }
    }
}
