package ai.maran.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable fun AiChatScreen(vm:AiChatViewModel=viewModel()) {
 val state by vm.state.collectAsState()
 var input by remember { mutableStateOf("") }
 var key by remember { mutableStateOf("") }
 var settings by remember { mutableStateOf(false) }
 var replaceKey by remember { mutableStateOf(false) }
 var confirmRemove by remember { mutableStateOf(false) }
 var expanded by remember { mutableStateOf(false) }
 if(confirmRemove) AlertDialog(
  onDismissRequest={confirmRemove=false},
  title={Text("Remove saved key?")},
  text={Text("You will need to enter a key again to use authenticated OpenCode access.")},
  confirmButton={TextButton(onClick={vm.removeKey();confirmRemove=false;key=""}){Text("Remove")}},
  dismissButton={TextButton(onClick={confirmRemove=false}){Text("Keep key")}}
 )
 Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
  verticalArrangement=Arrangement.spacedBy(10.dp)) {
  Text("MARAN AI",style=MaterialTheme.typography.headlineLarge)
  Text("OpenCode • Free models • Direct from your phone")
  Text("Internet required. No MARAN server needed. Messages go to OpenCode and its model providers; their access and data policies apply.",style=MaterialTheme.typography.bodySmall)
  Text(if(state.keySaved) "✓ API key saved • used automatically" else "No API key saved",style=MaterialTheme.typography.bodySmall)
  Box {
   OutlinedButton(onClick={expanded=true},enabled=!state.busy) {
    Text(state.models.find { it.id==state.selected }?.name ?: "Auto • free models only")
   }
   DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
    DropdownMenuItem(text={Text("Auto • free models only")},onClick={vm.select("");expanded=false})
    state.models.forEach { model ->
     DropdownMenuItem(text={Text(model.name)},enabled=model.protocol!="unsupported",onClick={vm.select(model.id);expanded=false})
    }
   }
  }
  Row {
   TextButton(onClick=vm::refresh,enabled=!state.busy) { Text("Refresh models") }
   TextButton(onClick={settings=!settings},enabled=!state.busy) { Text("Access") }
   TextButton(onClick=vm::clear,enabled=!state.busy) { Text("Clear chat") }
  }
  if(settings) {
   Text("Some free models only allow the official OpenCode app. An authorized Zen key may be required; it does not guarantee free access. No paid fallback is enabled.")
   if(state.keySaved && !replaceKey) {
    Text("Your key is saved securely. Reopening the app and clearing chat do not remove it.")
    Row {
     OutlinedButton(onClick={replaceKey=true}) { Text("Change key") }
     TextButton(onClick={confirmRemove=true}) { Text("Remove key") }
    }
   } else {
    OutlinedTextField(key,{key=it},label={Text(if(state.keySaved) "Replacement OpenCode key" else "OpenCode key • enter once")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
    Button(onClick={if(vm.saveKey(key)){key="";replaceKey=false}},enabled=key.isNotBlank()&&!state.busy) { Text("Save once") }
    if(state.keySaved) TextButton(onClick={key="";replaceKey=false}) { Text("Cancel") }
   }
   Text("Saved on this phone only. Uninstalling MARAN or clearing its app data removes the saved key.")

  }
  if(state.messages.isEmpty()) Text("Ask in English or தமிழ். This chat writes answers; it does not execute missions or control other apps.")
  state.messages.forEach { message ->
   Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
    Text(if(message.role=="user") "You" else "MARAN",style=MaterialTheme.typography.labelLarge)
    SelectionContainer { Text(message.content) }
   } }
  }
  state.lastModel?.let { Text("Answered by $it",style=MaterialTheme.typography.bodySmall) }
  state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
  OutlinedTextField(input,{input=it},enabled=!state.busy,label={Text("Ask MARAN AI")},modifier=Modifier.fillMaxWidth(),maxLines=6)
  if(state.busy) {
   LinearProgressIndicator(Modifier.fillMaxWidth())
   OutlinedButton(onClick=vm::stop) { Text("Stop") }
  } else Button(onClick={vm.send(input)},enabled=input.isNotBlank(),modifier=Modifier.fillMaxWidth()) { Text("Send") }
  Text("Chat stays in memory for this session. Only the last 10 messages are sent as context. Free availability can change.",style=MaterialTheme.typography.bodySmall)
 }
}
