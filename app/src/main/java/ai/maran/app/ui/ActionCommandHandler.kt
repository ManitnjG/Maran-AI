package ai.maran.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ai.maran.app.data.IntegrationCapability

private data class PendingCommunication(
    val route:ActionRoute,
    val recipient:String="",
    val subject:String="",
    val body:String=""
)

private fun emailIn(text:String):String =
    Regex("""[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}""",RegexOption.IGNORE_CASE)
        .find(text)?.value.orEmpty()

private fun phoneIn(text:String):String {
    val match=Regex("""(?<!\d)(?:\+?\d[\d ()-]{6,}\d)""").find(text)?.value.orEmpty()
    return match
}

private fun bodyIn(text:String):String =
    Regex("""(?i)\b(?:saying|that says|with (?:the )?message|message is|body is)\s+["']?(.+?)["']?\s*$""")
        .find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()

private fun connectorReady(
    route:ActionRoute,
    integrations:Map<String,IntegrationCapability>
):Boolean = route.kind.connectorId?.let { integrations[it]?.status=="ready" } == true

private fun packageFor(context:android.content.Context,kind:ActionKind):String? {
    val candidates=when(kind) {
        ActionKind.WHATSAPP -> listOf("com.whatsapp","com.whatsapp.w4b")
        ActionKind.INSTAGRAM -> listOf("com.instagram.android")
        ActionKind.FACEBOOK -> listOf("com.facebook.katana")
        ActionKind.LINKEDIN -> listOf("com.linkedin.android")
        ActionKind.DRIVE -> listOf("com.google.android.apps.docs")
        else -> emptyList()
    }
    return candidates.firstOrNull { context.packageManager.getLaunchIntentForPackage(it)!=null }
}

@Composable
internal fun rememberActionCommandHandler(
    vm:AiChatViewModel,
    integrations:Map<String,IntegrationCapability>,
    serverConnected:Boolean,
    onCreateMission:(String)->Unit
):(String)->Boolean {
    val context=LocalContext.current
    var pending by remember { mutableStateOf<PendingCommunication?>(null) }

    fun record(command:String,message:String) {
        vm.recordActionRouting(command,message)
    }

    fun openWhatsapp(command:String,recipient:String,body:String) {
        val pkg=packageFor(context,ActionKind.WHATSAPP)
        if(pkg==null) {
            record(command,"WhatsApp is not installed or is not visible to MARAN. Nothing was sent.")
            return
        }
        try {
            val digits=recipient.filter(Char::isDigit)
            val intent=if(digits.length>=7) {
                Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/$digits?text="+Uri.encode(body))).setPackage(pkg)
            } else {
                Intent(Intent.ACTION_SEND).apply {
                    type="text/plain"
                    putExtra(Intent.EXTRA_TEXT,body)
                    setPackage(pkg)
                }
            }
            context.startActivity(intent)
            record(
                command,
                if(digits.length>=7)
                    "Opened a WhatsApp draft for $recipient. Review it and tap Send in WhatsApp; MARAN did not press Send."
                else
                    "Opened a WhatsApp draft. Choose the recipient, review it and tap Send in WhatsApp; MARAN did not press Send."
            )
        } catch(e:Exception) {
            record(command,"Could not open a WhatsApp draft: "+(e.message?:"unavailable")+". Nothing was sent.")
        }
    }

    fun openEmail(command:String,recipient:String,subject:String,body:String) {
        try {
            val to=recipient.takeIf { emailIn(it).isNotBlank() }.orEmpty()
            val intent=Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:"+to)).apply {
                if(subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT,subject)
                if(body.isNotBlank()) putExtra(Intent.EXTRA_TEXT,body)
            }
            context.startActivity(Intent.createChooser(intent,"Choose email app"))
            record(command,"Opened an email draft. Review the recipient and content, then send it from your email app; MARAN did not press Send.")
        } catch(e:Exception) {
            record(command,"Could not open an email draft: "+(e.message?:"no email app available")+".")
        }
    }

    fun openSms(command:String,recipient:String,body:String) {
        try {
            val digits=recipient.filter(Char::isDigit)
            val intent=Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+digits)).apply {
                putExtra("sms_body",body)
            }
            context.startActivity(intent)
            record(command,"Opened an SMS draft. Review the recipient and message, then send it yourself; MARAN did not press Send.")
        } catch(e:Exception) {
            record(command,"Could not open an SMS draft: "+(e.message?:"messaging app unavailable")+".")
        }
    }

    fun openCalendar(command:String) {
        try {
            val intent=Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).apply {
                putExtra(CalendarContract.Events.TITLE,command.take(180))
            }
            context.startActivity(intent)
            record(command,"Opened a Calendar event draft. Confirm the date, time and details in Calendar before saving.")
        } catch(e:Exception) {
            record(command,"Could not open a Calendar event draft: "+(e.message?:"calendar app unavailable")+".")
        }
    }

    fun openSocialDraft(route:ActionRoute) {
        val pkg=packageFor(context,route.kind)
        if(pkg==null) {
            record(route.normalized,"The requested social app is not installed or visible. Nothing was published.")
            return
        }
        try {
            val text=bodyIn(route.normalized).ifBlank { route.normalized }
            val intent=Intent(Intent.ACTION_SEND).apply {
                type="text/plain"
                putExtra(Intent.EXTRA_TEXT,text)
                setPackage(pkg)
            }
            context.startActivity(intent)
            record(route.normalized,"Opened a share/post draft in the requested app. Review and publish it there; MARAN did not press Publish.")
        } catch(e:Exception) {
            record(route.normalized,"Could not open the requested social app: "+(e.message?:"unavailable")+".")
        }
    }

    fun routeMission(route:ActionRoute) {
        if(!serverConnected) {
            record(
                route.normalized,
                "This is an Action Agent task, but the MARAN server is not connected. Open Tools, connect the MARAN server, then retry. I did not send this command to OpenRouter."
            )
            return
        }
        onCreateMission(route.normalized)
        record(
            route.normalized,
            "Routed to MARAN Action Agent. Safe research/drafting can run automatically; any external send, post, upload or other consequential action must appear in Approval Centre before execution."
        )
    }

    val current=pending
    if(current!=null) {
        var recipient by remember(current.route.normalized) { mutableStateOf(current.recipient) }
        var subject by remember(current.route.normalized) { mutableStateOf(current.subject) }
        var body by remember(current.route.normalized) { mutableStateOf(current.body) }
        val ready=connectorReady(current.route,integrations)
        val kind=current.route.kind

        AlertDialog(
            onDismissRequest={pending=null},
            title={Text(when(kind){
                ActionKind.WHATSAPP -> "WhatsApp message"
                ActionKind.EMAIL -> "Email"
                ActionKind.SMS -> "SMS message"
                else -> "Review action"
            })},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedTextField(
                        value=recipient,onValueChange={recipient=it},
                        modifier=Modifier.fillMaxWidth(),
                        label={Text(when(kind){
                            ActionKind.EMAIL -> "Recipient email"
                            else -> "Recipient phone (optional for local draft)"
                        })},
                        singleLine=true
                    )
                    if(kind==ActionKind.EMAIL) {
                        OutlinedTextField(
                            value=subject,onValueChange={subject=it},
                            modifier=Modifier.fillMaxWidth(),
                            label={Text("Subject")},
                            singleLine=true
                        )
                    }
                    OutlinedTextField(
                        value=body,onValueChange={body=it},
                        modifier=Modifier.fillMaxWidth(),
                        label={Text("Message")},
                        minLines=3,
                        maxLines=6
                    )
                    Text(
                        if(ready)
                            "The connected server can prepare this as an Action Agent mission. It still requires Approval Centre before an external send."
                        else
                            "No ready server connector is available for this action. MARAN can open a local draft and you remain responsible for tapping Send."
                    )
                }
            },
            confirmButton={
                TextButton(
                    enabled=body.isNotBlank(),
                    onClick={
                        val route=current.route
                        pending=null
                        val canCloud=ready && when(kind) {
                            ActionKind.EMAIL -> emailIn(recipient).isNotBlank()
                            ActionKind.WHATSAPP,ActionKind.SMS -> recipient.filter(Char::isDigit).length>=7
                            else -> false
                        }
                        if(canCloud) {
                            val objective=when(kind) {
                                ActionKind.EMAIL -> "Send email to "+recipient.trim()+" with subject "+subject.trim().ifBlank{"(no subject)"}+" and body: "+body.trim()
                                ActionKind.WHATSAPP -> "Send WhatsApp message to "+recipient.trim()+" with body: "+body.trim()
                                ActionKind.SMS -> "Send SMS to "+recipient.trim()+" with body: "+body.trim()
                                else -> route.normalized
                            }
                            routeMission(route.copy(normalized=objective))
                        } else when(kind) {
                            ActionKind.WHATSAPP -> openWhatsapp(route.normalized,recipient,body)
                            ActionKind.EMAIL -> openEmail(route.normalized,recipient,subject,body)
                            ActionKind.SMS -> openSms(route.normalized,recipient,body)
                            else -> Unit
                        }
                    }
                ){Text(if(ready)"Continue safely" else "Open draft")}
            },
            dismissButton={TextButton(onClick={pending=null}){Text("Cancel")}}
        )
    }

    return { command ->
        val route=actionCommandRoute(command)
        if(route==null) false
        else {
            when(route.kind) {
                ActionKind.WHATSAPP,ActionKind.EMAIL,ActionKind.SMS -> {
                    pending=PendingCommunication(
                        route=route,
                        recipient=when(route.kind) {
                            ActionKind.EMAIL -> emailIn(route.normalized)
                            else -> phoneIn(route.normalized)
                        },
                        subject="",
                        body=bodyIn(route.normalized)
                    )
                }
                ActionKind.CALENDAR -> {
                    if(connectorReady(route,integrations)) routeMission(route)
                    else openCalendar(route.normalized)
                }
                ActionKind.INSTAGRAM,ActionKind.FACEBOOK,ActionKind.LINKEDIN -> {
                    if(connectorReady(route,integrations)) routeMission(route)
                    else openSocialDraft(route)
                }
                ActionKind.DRIVE -> {
                    if(connectorReady(route,integrations)) routeMission(route)
                    else {
                        val pkg=packageFor(context,ActionKind.DRIVE)
                        if(pkg!=null) {
                            try {
                                context.startActivity(context.packageManager.getLaunchIntentForPackage(pkg)!!)
                                record(route.normalized,"Google Drive connector is not configured. Opened Drive for manual upload; MARAN did not upload anything.")
                            } catch(e:Exception) {
                                record(route.normalized,"Google Drive connector is not configured and Drive could not be opened.")
                            }
                        } else record(route.normalized,"Google Drive connector is not configured. Connect it in the MARAN server before asking MARAN to upload files.")
                    }
                }
                else -> routeMission(route)
            }
            true
        }
    }
}
