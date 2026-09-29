package ai.maran.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.maran.app.data.RemoteMission

private enum class Tab(val label:String,val icon:ImageVector) {
    Assistant("Assistant",Icons.Rounded.Home), Workers("Workers",Icons.Rounded.Groups), Activity("Activity",Icons.Rounded.History)
}
@Composable fun MaranApp(vm:MaranViewModel=viewModel(),chat:AiChatViewModel=viewModel()) {
    var tab by rememberSaveable { mutableStateOf(Tab.Assistant) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var taskDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var addWorker by rememberSaveable { mutableStateOf(false) }
    val state by vm.state.collectAsState()
    BackHandler(settings || selectedId!=null || tab!=Tab.Assistant) {
        when { settings->settings=false; selectedId!=null->selectedId=null; else->tab=Tab.Assistant }
    }
    MaranTheme {
        Scaffold(bottomBar={if(!settings) NavigationBar(containerColor=MaterialTheme.colorScheme.background) {
            Tab.entries.forEach { item->NavigationBarItem(selected=tab==item,onClick={tab=item;selectedId=null},
                icon={Icon(item.icon,null)},label={Text(item.label)},
                colors=NavigationBarItemDefaults.colors(indicatorColor=MaterialTheme.colorScheme.primaryContainer,selectedIconColor=MaterialTheme.colorScheme.primary)) }
        }}) { padding->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    settings->SettingsScreen(state,vm,chat){settings=false}
                    selectedId!=null->{val mission=state.missions.find { it.id==selectedId }
                        if(mission!=null) TaskDetail(mission,state,vm){selectedId=null}
                        else Column(Modifier.padding(20.dp)){TextButton(onClick={selectedId=null}){Text("Back")};EmptyState("Task unavailable","Refresh activity to load this task.")}}
                    tab==Tab.Assistant->AiChatScreen(chat,state.voiceLanguage,{settings=true},{taskDraft=it},{tab=Tab.Activity})
                    tab==Tab.Workers->WorkersScreen(state,vm,{addWorker=true},{settings=true})
                    else->ActivityScreen(state,vm,{selectedId=it},{taskDraft=""},{settings=true})
                }
            }
        }
        taskDraft?.let { initial->
            var objective by rememberSaveable(initial) { mutableStateOf(initial) }
            AlertDialog(onDismissRequest={taskDraft=null},title={Text("Create a task")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(if(state.deviceMode)"Create an AI draft using your saved OpenRouter key. Live research and external actions need a task server." else "Describe the outcome. MARAN will prepare a plan and assign workers.")
                OutlinedTextField(objective,{objective=it},label={Text("What should we get done?")},minLines=3)
                if(!state.connected) Text("Connect your task server in Settings first.",color=MaterialTheme.colorScheme.error)
            }},confirmButton={TextButton(enabled=objective.isNotBlank()&&!state.busy&&state.connected,onClick={vm.create(objective);taskDraft=null;tab=Tab.Activity}){Text("Create task")}},
                dismissButton={TextButton(onClick={taskDraft=null}){Text("Cancel")}})
        }
        if(addWorker) AddWorkerDialog(state.busy,{addWorker=false}){name,skills->vm.createWorker(name,skills);addWorker=false}
    }
}
@Composable private fun PageHeader(title:String,subtitle:String,onSettings:()->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(title,style=MaterialTheme.typography.headlineMedium)
            Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick=onSettings){Icon(Icons.Rounded.AccountCircle,"Profile and settings")}
    }
}
@Composable private fun ConnectionNotice(state:MaranUiState,vm:MaranViewModel,onSettings:()->Unit) {
    if(state.deviceMode) { Text("Device workspace • AI drafts",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium) }
    else if(!state.connected) PremiumCard {
        Text("Task server disconnected",style=MaterialTheme.typography.titleSmall)
        Text("AI chat uses its own connection. Connect your server to load workers and tasks.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row {TextButton(onClick=onSettings){Text("Settings")};TextButton(onClick={vm.refresh()}){Text("Retry")};TextButton(onClick=vm::useDeviceMode){Text("Use device")}}
    }
    state.error?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
    if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
}
@Composable private fun WorkersScreen(state:MaranUiState,vm:MaranViewModel,onAdd:()->Unit,onSettings:()->Unit) {
    var stopId by remember { mutableStateOf<String?>(null) }
    if(stopId!=null) AlertDialog(onDismissRequest={stopId=null},title={Text("Stop this worker?")},text={Text("This sends a stop request for the selected worker.")},
        confirmButton={TextButton(onClick={stopId?.let{vm.stopWorker(it)};stopId=null}){Text("Stop worker")}},dismissButton={TextButton(onClick={stopId=null}){Text("Keep")}})
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {PageHeader("Your team","MARAN coordinates every task",onSettings)}
        item {ConnectionNotice(state,vm,onSettings)}
        item {PremiumCard {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.AutoAwesome,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(32.dp))
                Column(Modifier.weight(1f)){Text("MARAN",style=MaterialTheme.typography.titleLarge);Text("Manager",color=MaterialTheme.colorScheme.onSurfaceVariant)}
                StatusLabel(if(state.deviceMode)"On device" else if(state.connected)"Connected" else "Offline")
            }
            Text(if(state.deviceMode)"Assigns AI draft tasks to your saved workers. Internet and an OpenRouter key are needed to generate drafts." else "Plans tasks and coordinates your workers.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        }}
        item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Specialist workers",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);IconButton(onClick={vm.refresh()}){Icon(Icons.Rounded.Refresh,"Refresh workers")}}}
        if(state.workers.isEmpty()) item {EmptyState("Build your team","Add a named worker with the skills you need.")}
        items(state.workers,key={it.id}) {worker->
            PremiumCard {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.primaryContainer){Text(worker.name.take(1).uppercase(),Modifier.padding(16.dp),style=MaterialTheme.typography.titleMedium)}
                    Column(Modifier.weight(1f)){Text(worker.name,style=MaterialTheme.typography.titleMedium);Text(worker.skills.joinToString(" · "),color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    IconButton(onClick={stopId=worker.id},enabled=!state.busy&&state.connected){Icon(Icons.Rounded.StopCircle,"Stop ${worker.name}")}
                }
            }
        }
        item {OutlinedButton(onClick=onAdd,enabled=state.connected&&!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Icon(Icons.Rounded.Add,null);Spacer(Modifier.width(8.dp));Text("Add worker")}}
        if(!state.deviceMode) item {WakeControl(state.voiceLanguage,vm::handleVoice)}
    }
}
@Composable private fun AddWorkerDialog(busy:Boolean,onDismiss:()->Unit,onCreate:(String,List<String>)->Unit) {
    var name by rememberSaveable {mutableStateOf("")};var skills by rememberSaveable {mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Add a worker")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(name,{name=it},label={Text("Worker name")},singleLine=true)
        OutlinedTextField(skills,{skills=it},label={Text("Skills, separated by commas")},placeholder={Text("research, tour leads")})
    }},confirmButton={TextButton(enabled=!busy&&name.isNotBlank()&&skills.split(',').any{it.isNotBlank()},onClick={onCreate(name.trim(),skills.split(',').map{it.trim()}.filter{it.isNotEmpty()})}){Text("Add worker")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
@Composable private fun ActivityScreen(state:MaranUiState,vm:MaranViewModel,onOpen:(String)->Unit,onCreate:()->Unit,onSettings:()->Unit) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {PageHeader("Activity","Your tasks, progress, and results",onSettings)}
        item {ConnectionNotice(state,vm,onSettings)}
        item {Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick=onCreate){Icon(Icons.Rounded.Add,null);Text("New task")};OutlinedButton(onClick={vm.refresh()}){Icon(Icons.Rounded.Refresh,null);Text("Refresh")}}}
        if(state.missions.isEmpty()) item {EmptyState("A clear start","Create your first task. Its plan and results will appear here.")}
        items(state.missions,key={it.id}) {m->
            OutlinedCard(onClick={onOpen(m.id)},modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    StatusLabel(m.status)
                    Text(m.objective,style=MaterialTheme.typography.titleMedium)
                    Text(m.assigned_agents.joinToString(" · ").ifBlank{"Awaiting assignment"},color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${m.plan.count{it.status=="completed"}} of ${m.plan.size} steps complete",style=MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
@Composable private fun TaskDetail(m:RemoteMission,state:MaranUiState,vm:MaranViewModel,onBack:()->Unit) {
    val uri=LocalUriHandler.current
    var linkError by remember {mutableStateOf(false)}
    var confirmStop by remember {mutableStateOf(false)}
    if(confirmStop) AlertDialog(onDismissRequest={confirmStop=false},title={Text("Cancel this task?")},text={Text("MARAN will request that this task stop.")},
        confirmButton={TextButton(onClick={vm.stop(m.id);confirmStop=false}){Text("Cancel task")}},dismissButton={TextButton(onClick={confirmStop=false}){Text("Keep running")}})
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Rounded.ArrowBack,"Back")};Text("Task detail",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);IconButton(onClick={vm.refresh()}){Icon(Icons.Rounded.Refresh,"Refresh task")}}}
        item {Text(m.objective,style=MaterialTheme.typography.headlineMedium)}
        item {PremiumCard {
            StatusLabel(m.status)
            Text("Assigned to "+m.assigned_agents.joinToString(" · ").ifBlank{"MARAN"},color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(m.plan.isNotEmpty()) {
                val completed=m.plan.count{it.status=="completed"}
                LinearProgressIndicator(progress={completed.toFloat()/m.plan.size},modifier=Modifier.fillMaxWidth())
                Text("$completed of ${m.plan.size} steps complete",style=MaterialTheme.typography.labelMedium)
            }
        }}
        items(m.plan,key={it.id}) {step->PremiumCard {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Icon(if(step.status=="completed")Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,null,tint=MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)){Text(step.title,style=MaterialTheme.typography.titleMedium);Text(step.status.replace('_',' '),color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            step.output?.let {SelectionContainer{Text(it)}}
            step.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            step.evidence?.let{e->e.retrieved_at?.let{Text("Retrieved: $it",style=MaterialTheme.typography.labelSmall)}
                e.sources.forEach{url->TextButton(onClick={try{uri.openUri(url)}catch(_:Exception){linkError=true}}){Text(url)}}}
        }}
        item {Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            m.result?.summary?.let{SelectionContainer{Text(it)}}
            m.result?.note?.let{Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Text("Verification: ${m.verification}",style=MaterialTheme.typography.bodySmall)
            state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            if(linkError) Text("Could not open this source link.",color=MaterialTheme.colorScheme.error)
            if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(m.status=="waiting_approval") Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedButton(enabled=!state.busy,onClick={vm.decide(m.id,false)}){Text("Reject")}
                Button(enabled=!state.busy,onClick={vm.decide(m.id,true)}){Text("Approve")}
            }
            if(m.status in listOf("blocked","failed","interrupted")) OutlinedButton(enabled=!state.busy,onClick={vm.run(m.id)}){Text("Run / retry")}
            OutlinedButton(enabled=!state.busy,onClick={vm.exportMission(m.id)}){Text("Export results")}
            state.export?.takeIf{it.filename.contains(m.id)}?.let{SelectionContainer{Text(it.content)}}
            if(m.status !in listOf("completed","cancelled")) OutlinedButton(enabled=!state.busy,onClick={confirmStop=true},modifier=Modifier.fillMaxWidth()){Text("Cancel task")}
        }}
    }
}
@Composable private fun SettingsScreen(state:MaranUiState,vm:MaranViewModel,chat:AiChatViewModel,onBack:()->Unit) {
    val ai by chat.state.collectAsState()
    var key by remember {mutableStateOf("")}
    var url by rememberSaveable {mutableStateOf(state.serverUrl)}
    var token by remember {mutableStateOf("")}
    var confirmRemove by remember {mutableStateOf(false)}
    if(confirmRemove) AlertDialog(onDismissRequest={confirmRemove=false},title={Text("Remove AI key?")},text={Text("You will need to save a key again before chatting.")},confirmButton={TextButton(onClick={chat.removeKey();confirmRemove=false}){Text("Remove")}},dismissButton={TextButton(onClick={confirmRemove=false}){Text("Keep")}})
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Rounded.ArrowBack,"Back")};Text("Profile & settings",style=MaterialTheme.typography.headlineSmall)}
        PremiumCard {
            Text("AI connection",style=MaterialTheme.typography.titleLarge)
            StatusLabel(if(ai.keySaved)"Key saved" else "Setup required")
            Text("OpenRouter • Free model routing",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Your key is saved securely on this device. Free models have availability and usage limits.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(key,{key=it},label={Text(if(ai.keySaved)"Replacement API key" else "OpenRouter API key")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
            Row {Button(enabled=key.isNotBlank()&&!ai.busy,onClick={if(chat.saveKey(key))key=""}){Text("Save key")};if(ai.keySaved)TextButton(enabled=!ai.busy,onClick={confirmRemove=true}){Text("Remove key")}}
            ai.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
        PremiumCard {
            Text("Task workspace",style=MaterialTheme.typography.titleLarge)
            Text("Device mode saves workers and AI drafts on this phone. Server mode enables the tools supported by your server.",style=MaterialTheme.typography.bodySmall)
            FilterChip(selected=state.deviceMode,onClick=vm::useDeviceMode,label={Text("Use device mode — no server")})
            Text("Connect the server that runs your workers and stores tasks.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(url,{url=it},label={Text("Server URL")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(token,{token=it},label={Text("Access token")},supportingText={Text("Leave blank to keep the token for the same server.")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
            Button(enabled=!state.busy,onClick={vm.configure(url,token);token=""}){Text("Save connection")}
            StatusLabel(if(state.deviceMode)"Device mode" else if(state.connected)"Connected" else "Disconnected")
            state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
        PremiumCard {
            Text("Voice language",style=MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                FilterChip(selected=state.voiceLanguage=="en-IN",onClick={vm.setLanguage("en-IN")},label={Text("English")})
                FilterChip(selected=state.voiceLanguage=="ta-IN",onClick={vm.setLanguage("ta-IN")},label={Text("தமிழ்")})
            }
        }
    }
}
