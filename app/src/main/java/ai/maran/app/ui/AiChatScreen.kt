package ai.maran.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.maran.app.voice.SpeechController

@Composable fun AiChatScreen(
    vm:AiChatViewModel=viewModel(), language:String="en-IN", onSettings:()->Unit={},
    onTask:(String)->Unit={}, onActivity:()->Unit={}
) {
    val state by vm.state.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val list=rememberLazyListState()
    val clipboard=LocalClipboardManager.current
    val speech=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if(it.resultCode==Activity.RESULT_OK) SpeechController.result(it.data)?.let { result->input=result }
    }
    val followBottom by remember { derivedStateOf { !list.canScrollForward } }
    LaunchedEffect(state.messages.size,state.messages.lastOrNull()?.content) {
        if(state.messages.isNotEmpty() && (followBottom || state.messages.last().content.isEmpty()))
            list.scrollToItem(state.messages.lastIndex)
    }
    if(confirmClear) AlertDialog(onDismissRequest={confirmClear=false},title={Text("Clear conversation?")},
        text={Text("This removes the messages in this chat.")},
        confirmButton={TextButton(onClick={vm.clear();confirmClear=false}){Text("Clear")}},
        dismissButton={TextButton(onClick={confirmClear=false}){Text("Keep")}})
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Surface(shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Rounded.AutoAwesome,null,Modifier.padding(10.dp),tint=MaterialTheme.colorScheme.primary)
            }
            Text("M A R A N",Modifier.weight(1f).padding(start=12.dp),style=MaterialTheme.typography.titleLarge)
            if(state.messages.isNotEmpty()) IconButton(onClick={confirmClear=true},enabled=!state.busy){Icon(Icons.Rounded.AddComment,"New chat")}
            IconButton(onClick=onSettings){Icon(Icons.Rounded.AccountCircle,"Profile and settings")}
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),state=list,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(state.messages.isEmpty()) item {
                Column(verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    Text("Your personal AI workspace",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("What can I help\nyou do?",style=MaterialTheme.typography.headlineLarge)
                    Text("Ask in English or தமிழ். Turn an idea into a task for your team.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        QuickAction("Research",Icons.Rounded.Search,Modifier.weight(1f)){input="Research "}
                        QuickAction("Create document",Icons.Rounded.Description,Modifier.weight(1f)){input="Draft a document about "}
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        QuickAction("Find tour leads",Icons.Rounded.Groups,Modifier.weight(1f)){onTask("Find tour leads in Chennai using public business sources")}
                        QuickAction("Write content",Icons.Rounded.Edit,Modifier.weight(1f)){input="Write content for "}
                    }
                    PremiumCard {
                        Text("Let your team handle it",style=MaterialTheme.typography.titleMedium)
                        Text("Create a task, review its plan, and follow the results.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick={onTask("")}){Text("Create task")}
                    }
                    TextButton(onClick=onActivity){Text("View activity")}
                }
            }
            itemsIndexed(state.messages) { _, message ->
                val user=message.role=="user"
                Card(Modifier.fillMaxWidth().padding(start=if(user)28.dp else 0.dp,end=if(user)0.dp else 12.dp),
                    colors=CardDefaults.cardColors(containerColor=if(user)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(if(user)"You" else "MARAN",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                            if(message.content.isNotBlank()) IconButton(onClick={clipboard.setText(AnnotatedString(message.content))},modifier=Modifier.size(48.dp)) {Icon(Icons.Rounded.ContentCopy,"Copy message",Modifier.size(18.dp))}
                        }
                        if(message.content.isEmpty()&&state.busy) Text("Connecting to AI…",color=MaterialTheme.colorScheme.onSurfaceVariant)
                        else SelectionContainer { Text(message.content) }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            if(!state.keySaved) TextButton(onClick=onSettings){Text("Set up AI to start chatting")}
            (state.error?:voiceError)?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
            if(state.busy) Text("MARAN is responding…",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)
            OutlinedTextField(value=input,onValueChange={input=it},modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large,
                placeholder={Text("Ask MARAN anything…")},maxLines=5,
                leadingIcon={IconButton(onClick={try{voiceError=null;speech.launch(SpeechController.intent(language))}catch(_:ActivityNotFoundException){voiceError="Voice input is unavailable. You can type your message."}}){Icon(Icons.Rounded.Mic,"Dictate message")}},
                trailingIcon={if(state.busy) IconButton(onClick=vm::stop){Icon(Icons.Rounded.StopCircle,"Stop response",tint=MaterialTheme.colorScheme.primary)}
                    else IconButton(enabled=input.isNotBlank()&&state.keySaved,onClick={vm.send(input);input=""}){Icon(Icons.Rounded.ArrowUpward,"Send message",tint=MaterialTheme.colorScheme.primary)}})
            Spacer(Modifier.height(8.dp))
        }
    }
}
@Composable private fun QuickAction(title:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier,onClick:()->Unit) {
    OutlinedCard(onClick=onClick,modifier=modifier.heightIn(min=112.dp)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(icon,null,tint=MaterialTheme.colorScheme.secondary)
            Text(title,style=MaterialTheme.typography.titleSmall)
        }
    }
}
