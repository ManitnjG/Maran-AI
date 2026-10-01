package ai.maran.app.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.maran.app.voice.SpeechController
import ai.maran.app.data.OPENROUTER_NEMOTRON_FREE

private fun formattedAnswer(raw:String):androidx.compose.ui.text.AnnotatedString = buildAnnotatedString {
    // Keep streamed Markdown readable without exposing literal ** and ###.
    val cleaned=raw.replace(Regex("(?m)^#{1,6} +"), "")
        .replace(Regex("(?m)^---+\\\\s*$"), "")
    val parts=Regex("\\\\*\\\\*(.+?)\\\\*\\\\*").findAll(cleaned)
    var cursor=0
    parts.forEach { match ->
        append(cleaned.substring(cursor,match.range.first))
        withStyle(SpanStyle(fontWeight=FontWeight.Bold)) { append(match.groupValues[1]) }
        cursor=match.range.last+1
    }
    append(cleaned.substring(cursor))
}

@Composable
fun AiChatScreen(vm:AiChatViewModel=viewModel()) {
    val state by vm.state.collectAsState()
    var input by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var showKeySetup by remember(state.keySaved) { mutableStateOf(!state.keySaved) }
    var confirmRemove by remember { mutableStateOf(false) }
    var voiceLanguage by remember { mutableStateOf("en-IN") }
    var lastHeard by remember { mutableStateOf<String?>(null) }
    val context=LocalContext.current

    fun startVoice(launcher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>) {
        try {
            launcher.launch(SpeechController.intent(voiceLanguage))
        } catch(_:ActivityNotFoundException) {
            vm.voiceError("Speech recognition is not available on this phone. Install or enable a speech recognition service.")
        } catch(e:Exception) {
            vm.voiceError("Could not start voice recognition: "+(e.message ?: "unknown error"))
        }
    }

    lateinit var speechLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>
    val permissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if(granted) startVoice(speechLauncher)
        else vm.voiceError("Microphone permission is required for voice recognition.")
    }
    speechLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        if(result.resultCode==Activity.RESULT_OK) {
            val heard=SpeechController.result(result.data)
            if(heard.isNullOrBlank()) vm.voiceError("I could not hear any words. Please try again.")
            else {
                lastHeard=heard
                input=heard
                vm.sendVoice(heard)
                input=""
            }
        }
    }

    if(confirmRemove) AlertDialog(
        onDismissRequest={confirmRemove=false},
        title={Text("Remove OpenRouter key?")},
        text={Text("You will need to enter the key again before MARAN AI can chat.")},
        confirmButton={TextButton(onClick={vm.removeKey();confirmRemove=false;showKeySetup=true}){Text("Remove")}},
        dismissButton={TextButton(onClick={confirmRemove=false}){Text("Keep")}}
    )

    val scroll=rememberScrollState()
    Column(Modifier.fillMaxSize().imePadding()) {
      Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal=16.dp,vertical=12.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("MARAN AI",style=MaterialTheme.typography.headlineLarge)
        AssistChip(onClick={},label={Text("OpenRouter • Auto Free AI")})
        Text(
            "Primary: $OPENROUTER_NEMOTRON_FREE. If Nemotron is busy, MARAN automatically switches to another free OpenRouter model. Free-tier limits still apply.",
            style=MaterialTheme.typography.bodySmall
        )

        if(showKeySetup) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("One-time OpenRouter setup",style=MaterialTheme.typography.titleMedium)
                    Text("Your key is encrypted with Android Keystore and stays on this phone.",style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value=key,onValueChange={key=it},
                        label={Text("OpenRouter API key")},
                        visualTransformation=PasswordVisualTransformation(),
                        singleLine=true,modifier=Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick={if(vm.saveKey(key)){key="";showKeySetup=false}},
                        enabled=key.isNotBlank()&&!state.busy
                    ){Text("Save key")}
                    if(state.keySaved) TextButton(onClick={key="";showKeySetup=false}){Text("Cancel")}
                }
            }
        } else {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick={},label={Text("✓ Key saved")})
                TextButton(onClick={showKeySetup=true}){Text("Change key")}
                TextButton(onClick={confirmRemove=true}){Text("Remove")}
            }
        }

        if(state.messages.isEmpty()) {
            Text("Hi. Ask me in English or தமிழ்.")
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Voice AI",style=MaterialTheme.typography.titleMedium)
                Text("Speak naturally. Your phone converts speech to text, then Nemotron answers.",style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected=voiceLanguage=="en-IN",
                        onClick={voiceLanguage="en-IN"},
                        label={Text("English")}
                    )
                    FilterChip(
                        selected=voiceLanguage=="ta-IN",
                        onClick={voiceLanguage="ta-IN"},
                        label={Text("தமிழ்")}
                    )
                }
                Button(
                    onClick={
                        if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) {
                            startVoice(speechLauncher)
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    enabled=!state.busy,
                    modifier=Modifier.fillMaxWidth()
                ){
                    Text(if(state.busy) "AI is answering…" else "🎤 Speak to MARAN")
                }
                lastHeard?.let { Text("Heard: $it",style=MaterialTheme.typography.bodySmall) }
            }
        }

        state.messages.forEach { message ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(if(message.role=="user") "You" else "MARAN",style=MaterialTheme.typography.labelLarge)
                    SelectionContainer { Text(if(message.role=="assistant") formattedAnswer(message.content) else buildAnnotatedString { append(message.content) }) }
                }
            }
        }

        state.lastModel?.let { Text("Answered by $it",style=MaterialTheme.typography.bodySmall) }
        state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }

      } // Scrollable conversation
      Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value=input,onValueChange={input=it},
            enabled=!state.busy,
            label={Text(if(state.keySaved) "Ask MARAN AI" else "Ask about your phone, or save an AI key")},
            modifier=Modifier.fillMaxWidth(),
            maxLines=6
        )

        if(state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick=vm::stop){Text("Stop")}
        } else {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick={vm.send(input);input=""},
                    enabled=input.isNotBlank()&&(state.keySaved||vm.supportsLocal(input)),
                    modifier=Modifier.weight(1f)
                ){Text("Send")}
                OutlinedButton(onClick=vm::clear,enabled=state.messages.isNotEmpty()){Text("Clear")}
            }
        }
      }
    }
}
