package ai.maran.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.maran.app.data.OPENROUTER_NEMOTRON_FREE

@Composable
fun AiChatScreen(vm:AiChatViewModel=viewModel()) {
    val state by vm.state.collectAsState()
    var input by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var showKeySetup by remember(state.keySaved) { mutableStateOf(!state.keySaved) }
    var confirmRemove by remember { mutableStateOf(false) }

    if(confirmRemove) AlertDialog(
        onDismissRequest={confirmRemove=false},
        title={Text("Remove OpenRouter key?")},
        text={Text("You will need to enter the key again before MARAN AI can chat.")},
        confirmButton={TextButton(onClick={vm.removeKey();confirmRemove=false;showKeySetup=true}){Text("Remove")}},
        dismissButton={TextButton(onClick={confirmRemove=false}){Text("Keep")}}
    )

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)
    ) {
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

        state.messages.forEach { message ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(if(message.role=="user") "You" else "MARAN",style=MaterialTheme.typography.labelLarge)
                    SelectionContainer { Text(message.content) }
                }
            }
        }

        state.lastModel?.let { Text("Answered by $it",style=MaterialTheme.typography.bodySmall) }
        state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }

        OutlinedTextField(
            value=input,onValueChange={input=it},
            enabled=!state.busy&&state.keySaved,
            label={Text(if(state.keySaved) "Ask MARAN AI" else "Save OpenRouter key first")},
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
                    enabled=input.isNotBlank()&&state.keySaved,
                    modifier=Modifier.weight(1f)
                ){Text("Send")}
                OutlinedButton(onClick=vm::clear,enabled=state.messages.isNotEmpty()){Text("Clear")}
            }
        }
    }
}
