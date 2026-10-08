package ai.maran.app.ui

import android.Manifest
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
import ai.maran.app.voice.MaranSpeechRecognizer
import ai.maran.app.voice.MaranSpeaker
import ai.maran.app.data.OPENROUTER_NEMOTRON_FREE

private fun formattedAnswer(raw:String):androidx.compose.ui.text.AnnotatedString = buildAnnotatedString {
    // Format bold text while streaming without changing the underlying answer.
    val cleaned=raw.replace(Regex("""(?m)^\s*#{1,6}\s+"""),"")
        .replace(Regex("""(?m)^\s*---+\s*$"""),"")
    val bold=Regex("""\*\*(.+?)\*\*""")
    var cursor=0
    for(match in bold.findAll(cleaned)) {
        append(cleaned.substring(cursor,match.range.first))
        withStyle(SpanStyle(fontWeight=FontWeight.Bold)) {
            append(match.groupValues[1])
        }
        cursor=match.range.last+1
    }
    append(cleaned.substring(cursor))
}

@Composable
fun AiChatScreen(
    vm:AiChatViewModel=viewModel(),
    autoListenSignal:Int=0,
    incomingCommand:String?=null,
    onIncomingConsumed:()->Unit={},
    onAutoListenConsumed:()->Unit={}
) {
    val state by vm.state.collectAsState()
    var input by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var showKeySetup by remember(state.keySaved) { mutableStateOf(!state.keySaved) }
    var confirmRemove by remember { mutableStateOf(false) }
    var voiceLanguage by remember { mutableStateOf("en-IN") }
    var lastHeard by remember { mutableStateOf<String?>(null) }
    var liveTranscript by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    val context=LocalContext.current
    val speaker=remember(context) { MaranSpeaker(context) }
    DisposableEffect(speaker) { onDispose { speaker.close() } }
    val recognizer=remember(context) { MaranSpeechRecognizer(context) }
    DisposableEffect(recognizer) { onDispose { recognizer.close() } }
    var speakNextReply by remember { mutableStateOf(false) }
    var replyAfterCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.messages.lastOrNull()?.content,state.busy,speakNextReply) {
        if(speakNextReply && !state.busy && state.messages.size>replyAfterCount) {
            val reply=state.messages.lastOrNull()?.takeIf { it.role=="assistant" && it.content.isNotBlank() }
            if(reply!=null) {
                speaker.speak(reply.content.replace(Regex("[*#]"),""),voiceLanguage)
                speakNextReply=false
            }
        }
    }
    val registry=remember(context) { AndroidToolRegistry(context) }
    var pendingTorch by remember { mutableStateOf<String?>(null) }
    val torchPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val command=pendingTorch
        pendingTorch=null
        if(command!=null) {
            val action=LocalCommandEngine.parse(command)
            if(granted && action!=null) vm.recordPhoneAction(command,registry.execute(action).message)
            else vm.recordPhoneAction(command,"Camera/flashlight permission was declined. No action performed.")
        }
    }
    val runLocal:(String)->Boolean = { command ->
        val action=LocalCommandEngine.parse(command)
        if(action==null) false else {
            if(action.toolId.startsWith("torch.") &&
                context.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) {
                pendingTorch=command
                torchPermission.launch(Manifest.permission.CAMERA)
            } else {
                val outcome=registry.execute(action)
                vm.recordPhoneAction(command,outcome.message)
            }
            true
        }
    }
    val screenControl=rememberScreenCommand(vm)
    val realWorld=rememberRealWorldCommand(vm)
    val nativeDispatch=rememberPhoneCommand(vm) { command ->
        if(command.trim().lowercase() in listOf("stop speaking","maran stop speaking","stop talking","be quiet")) {
            speaker.stop()
            speakNextReply=false
        } else if (!runLocal(command) && !screenControl(command) && !realWorld(command)) vm.send(command)
    }

    val dispatchCommand:(String)->Unit = { raw ->
        val normalized=raw.trim().replace(
            Regex("""^(?i:(?:hey\s+)?maran)\s*[,.:!]*\s*"""),""
        ).replace(Regex("""^(?i:(?:please\s+)?(?:can|could|would)\s+you\s+|please\s+)"""),"")
            .trim().ifBlank { raw.trim() }
        nativeDispatch(normalized)
    }

    fun startVoice() {
        liveTranscript=""
        recognizer.start(
            language=voiceLanguage,
            onPartial={ heard ->
                liveTranscript=heard
                input=heard
            },
            onFinal={ heard ->
                isListening=false
                liveTranscript=""
                lastHeard=heard
                input=heard
                replyAfterCount=state.messages.size
                speakNextReply=true
                dispatchCommand(heard)
                input=""
            },
            onError={ message ->
                isListening=false
                liveTranscript=""
                if(message!="Voice recognition stopped.") vm.voiceError(message)
            },
            onListening={ active -> isListening=active }
        )
    }

    val permissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if(granted) startVoice()
        else vm.voiceError("Microphone permission is required for voice recognition.")
    }

    LaunchedEffect(autoListenSignal) {
        if(autoListenSignal>0) {
            if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) {
                startVoice()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            onAutoListenConsumed()
        }
    }
    LaunchedEffect(incomingCommand) {
        val command=incomingCommand?.trim().orEmpty()
        if(command.isNotEmpty()) {
            replyAfterCount=state.messages.size
            speakNextReply=true
            dispatchCommand(command)
            onIncomingConsumed()
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
        MaranSectionHeading("MARAN AI","Chat, voice and on-device actions")
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
                    MaranPrimaryButton(label="Save key",onClick={if(vm.saveKey(key)){key="";showKeySetup=false}},enabled=key.isNotBlank()&&!state.busy)
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
                Text(
                    if(recognizer.onDevice)
                        "Voice recognition stays inside MARAN and uses Android on-device speech recognition on this phone."
                    else
                        "Voice recognition stays inside MARAN. Your installed Android speech engine handles recognition in the background without opening its full-screen UI.",
                    style=MaterialTheme.typography.bodySmall
                )
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
                if(isListening) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        if(liveTranscript.isBlank()) "Listening…" else liveTranscript,
                        style=MaterialTheme.typography.bodyLarge
                    )
                    MaranSecondaryButton(
                        label="Stop listening",
                        onClick=recognizer::stop,
                        modifier=Modifier.fillMaxWidth()
                    )
                } else {
                    MaranPrimaryButton(
                        label=if(state.busy) "AI is answering…" else "Speak to MARAN",
                        onClick={
                            if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) {
                                startVoice()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        enabled=!state.busy,
                        modifier=Modifier.fillMaxWidth()
                    )
                }
                Text(
                    if(recognizer.onDevice) "On-device speech • in-app UI" else "System speech engine • in-app UI",
                    style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant
                )
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
        MaranInput(
            value=input,onValueChange={input=it},
            enabled=!state.busy,
            label=if(state.keySaved) "Ask MARAN AI" else "Ask about your phone, or save an AI key",
            modifier=Modifier.fillMaxWidth(),
            maxLines=6
        )

        if(state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            MaranSecondaryButton(label="Stop",onClick=vm::stop)
        } else {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                MaranPrimaryButton(
                    label="Send",
                    onClick={speakNextReply=false;dispatchCommand(input);input=""},
                    enabled=input.isNotBlank()&&(state.keySaved||vm.supportsLocal(input)||phoneCommandTarget(input)!=null||realWorldCommand(input)!=null||LocalCommandEngine.parse(input)!=null||screenCommand(input)||DeviceInventoryCommand.matches(input)),
                    modifier=Modifier.weight(1f)
                )
                MaranSecondaryButton(label="Clear",onClick=vm::clear,enabled=state.messages.isNotEmpty())
            }
        }
      }
    }
}
