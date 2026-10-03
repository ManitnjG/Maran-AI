package ai.maran.app.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import ai.maran.app.voice.SpeechController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.maran.app.data.RemoteMission
private enum class Tab(val label:String,val icon:ImageVector){Home("Home",Icons.Rounded.Home),Missions("Missions",Icons.Rounded.Checklist),Ai("AI",Icons.Rounded.AutoAwesome),Workforce("Workers",Icons.Rounded.Groups),Tools("Tools",Icons.Rounded.Build)}
@Composable fun MaranApp(vm:MaranViewModel=viewModel()){
 var tab by remember{mutableStateOf(Tab.Home)}
 val state by vm.state.collectAsState()
 var voiceStart by remember { mutableIntStateOf(0) }
 var pendingVoiceCommand by remember { mutableStateOf<String?>(null) }
 MaterialTheme(colorScheme=if(androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme(primary=Color(0xFF818CF8),secondary=Color(0xFF38BDF8),tertiary=Color(0xFFC084FC),background=Color(0xFF090D16),surface=Color(0xFF111827)) else lightColorScheme(primary=Color(0xFF4F46E5),secondary=Color(0xFF0284C7),tertiary=Color(0xFF7C3AED))){
  Scaffold(bottomBar={NavigationBar{Tab.entries.forEach{item->NavigationBarItem(selected=tab==item,onClick={tab=item},icon={Icon(item.icon,null)},label={Text(item.label)})}}}){pad->
   Box(Modifier.padding(pad).fillMaxSize()){
    when(tab){
     Tab.Ai -> AiChatScreen(
      autoListenSignal=voiceStart,
      incomingCommand=pendingVoiceCommand,
      onIncomingConsumed={pendingVoiceCommand=null},
      onAutoListenConsumed={voiceStart=0}
     )
     Tab.Home -> PremiumHomeScreen(state,vm::create,
      {tab=Tab.Ai;voiceStart++},
      {tab=Tab.Ai},{tab=Tab.Missions},{tab=Tab.Workforce},{tab=Tab.Tools},
      onWakeCommand={command->pendingVoiceCommand=command;tab=Tab.Ai}
     )
     Tab.Missions -> MissionScreen(state,vm::decide,vm::run,vm::stop,vm::refresh,vm::exportMission)
     Tab.Workforce -> WorkforceScreen(state.missions,state.workers,vm::stopWorker,vm::createWorker)
     Tab.Tools -> ToolsScreen(state,vm)
    }
   }
  }
 }
}
@Composable private fun HomeScreen(state:MaranUiState,onCreate:(String)->Unit,onCommand:(String)->Unit,onVoice:()->Unit){
 var input by remember{mutableStateOf("")}
 Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column{Text("MARAN",style=MaterialTheme.typography.headlineLarge);Text("Your Personal AI Workforce",color=MaterialTheme.colorScheme.onSurfaceVariant)};AssistChip(onClick={},label={Text(if(state.connected)"● Connected" else "○ Offline")})}
  Card(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){Text("What should we get done?",style=MaterialTheme.typography.titleLarge);FilledIconButton(onClick=onVoice,modifier=Modifier.size(72.dp)){Icon(Icons.Rounded.Mic,"Talk",Modifier.size(34.dp))};OutlinedTextField(input,{input=it},Modifier.fillMaxWidth(),placeholder={Text("Ask MARAN anything…")},trailingIcon={IconButton(enabled=!state.busy&&input.isNotBlank(),onClick={onCreate(input);input=""}){Icon(Icons.Rounded.Send,"Start mission")}})}}
  WakeControl(state.voiceLanguage,onCommand)
  state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)};Text("Continue",style=MaterialTheme.typography.titleMedium)
  LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){items(state.missions.take(5)){mission->MissionCard(mission,null)}}
 }
}
@Composable private fun CommandScreen(state:MaranUiState,onCreate:(String)->Unit,onCommand:(String)->Unit,onVoice:()->Unit,spoken:String?){
 var input by remember{mutableStateOf("")}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
  state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  WakeControl(state.voiceLanguage,onCommand)
  FilledIconButton(onClick=onVoice,modifier=Modifier.size(110.dp)){Icon(Icons.Rounded.GraphicEq,"MARAN voice",Modifier.size(52.dp))};Spacer(Modifier.height(20.dp));Text("MARAN",style=MaterialTheme.typography.headlineLarge);Text("Tell me the outcome. I’ll organize the workforce.");spoken?.let{Text("Heard: $it",color=MaterialTheme.colorScheme.onSurfaceVariant)};Spacer(Modifier.height(20.dp));OutlinedTextField(input,{input=it},Modifier.fillMaxWidth(),placeholder={Text("Type a mission…")});Spacer(Modifier.height(10.dp));Button(onClick={onCreate(input);input=""},enabled=!state.busy&&input.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text(if(state.busy)"Planning…" else "Start mission")}
 }
}
@Composable private fun MissionScreen(state:MaranUiState,onDecision:(String,Boolean)->Unit,onRun:(String)->Unit,onStop:(String)->Unit,onRefresh:()->Unit,onExport:(String)->Unit){Column(Modifier.fillMaxSize().padding(20.dp)){Text("Missions",style=MaterialTheme.typography.headlineLarge);TextButton(onClick=onRefresh){Text("Refresh")};state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)};Spacer(Modifier.height(12.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)){items(state.missions){m->MissionCard(m,if(m.status=="waiting_approval") onDecision else null,onRun,onStop,onExport)}}}}
@Composable private fun MissionCard(m:RemoteMission,onDecision:((String,Boolean)->Unit)?,onRun:((String)->Unit)?=null,onStop:((String)->Unit)?=null,onExport:((String)->Unit)?=null){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(m.objective,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f));AssistChip(onClick={},label={Text(m.status.replace('_',' '))})};Text(m.assigned_agents.joinToString(" · "),color=MaterialTheme.colorScheme.onSurfaceVariant);m.plan.forEach{step->Text("• "+step.title+" — "+step.status,style=MaterialTheme.typography.bodySmall);step.output?.let{androidx.compose.foundation.text.selection.SelectionContainer{Text(it)}};step.evidence?.let{e->e.retrieved_at?.let{Text("Retrieved: "+it,style=MaterialTheme.typography.bodySmall)};e.sources.forEach{url->val handler=androidx.compose.ui.platform.LocalUriHandler.current;TextButton(onClick={try{handler.openUri(url)}catch(_:Exception){}}){Text(url)}}};step.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}};m.result?.summary?.let{Text(it,style=MaterialTheme.typography.bodyMedium)};if(onExport!=null){TextButton(onClick={onExport(m.id)}){Text("Export results")}};m.result?.note?.let{Text(it,style=MaterialTheme.typography.bodySmall)};if(onRun!=null&&m.status in listOf("blocked","failed","running")){TextButton(onClick={onRun(m.id)}){Text("Run / retry")}};if(onStop!=null&&m.status !in listOf("completed","cancelled")){TextButton(onClick={onStop(m.id)}){Text("Stop mission")}};if(m.status=="completed"||m.status=="failed"||m.status=="blocked"){Text("Verification: "+m.verification,color=MaterialTheme.colorScheme.onSurfaceVariant)};if(onDecision!=null){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={onDecision(m.id,false)}){Text("Reject")};Button(onClick={onDecision(m.id,true)}){Text("Approve")}}}}}}
@Composable private fun WorkforceScreen(
 missions:List<RemoteMission>,
 workers:List<ai.maran.app.data.WorkerDto>,
 onStop:(String)->Unit,
 onCreate:(String,List<String>)->Unit
){
 var showAdd by remember{mutableStateOf(false)}
 var name by remember{mutableStateOf("")}
 var skills by remember{mutableStateOf("")}
 val agents=missions.flatMap{it.assigned_agents}.distinct()
 Column(
  Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
  verticalArrangement=Arrangement.spacedBy(14.dp)
 ){
  Text("Your team",style=MaterialTheme.typography.headlineLarge)
  Text("MARAN coordinates every task",color=MaterialTheme.colorScheme.onSurfaceVariant)

  Card(Modifier.fillMaxWidth()){
   Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
     Column{
      Text("MARAN",style=MaterialTheme.typography.titleLarge)
      Text("Manager",color=MaterialTheme.colorScheme.onSurfaceVariant)
     }
     AssistChip(onClick={},label={Text("Ready")})
    }
    Text("Worker profiles stay available on this device. Remote mission execution can sync when a server is available.",color=MaterialTheme.colorScheme.onSurfaceVariant)
   }
  }

  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
   Text("Specialist workers",style=MaterialTheme.typography.titleLarge)
   FilledTonalIconButton(onClick={showAdd=!showAdd}){Icon(if(showAdd) Icons.Rounded.Close else Icons.Rounded.PersonAdd,"Add worker")}
  }

  if(showAdd){
   Card(Modifier.fillMaxWidth()){
    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
     Text("Add worker",style=MaterialTheme.typography.titleMedium)
     OutlinedTextField(name,{name=it},label={Text("Worker name")},modifier=Modifier.fillMaxWidth(),singleLine=true)
     OutlinedTextField(skills,{skills=it},label={Text("Skills, comma separated")},modifier=Modifier.fillMaxWidth(),singleLine=true)
     Button(
      onClick={
       val parsed=skills.split(",").map { it.trim() }.filter { it.isNotBlank() }
       onCreate(name,parsed);name="";skills="";showAdd=false
      },
      enabled=name.isNotBlank(),
      modifier=Modifier.fillMaxWidth()
     ){Text("Add to team")}
    }
   }
  }

  workers.forEach{w->
   Card(Modifier.fillMaxWidth()){
    Row(
     Modifier.fillMaxWidth().padding(16.dp),
     horizontalArrangement=Arrangement.SpaceBetween,
     verticalAlignment=Alignment.CenterVertically
    ){
     Column(Modifier.weight(1f)){
      Text(w.name,style=MaterialTheme.typography.titleMedium)
      Text(w.skills.joinToString(" • ").ifBlank{"General assistant"},color=MaterialTheme.colorScheme.onSurfaceVariant)
     }
     IconButton(onClick={onStop(w.id)}){Icon(Icons.Rounded.DeleteOutline,"Remove worker")}
    }
   }
  }

  if(agents.isNotEmpty()){
   Text("Active mission agents",style=MaterialTheme.typography.titleMedium)
   agents.forEach{agent->AssistChip(onClick={},label={Text(agent.replace('_',' ').replaceFirstChar(Char::uppercase))})}
  }
 }
}
@Composable private fun SimpleScreen(title:String,body:String,icon:ImageVector){Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Icon(icon,null,Modifier.size(36.dp));Text(title,style=MaterialTheme.typography.headlineLarge);Text(body,color=MaterialTheme.colorScheme.onSurfaceVariant)}}

