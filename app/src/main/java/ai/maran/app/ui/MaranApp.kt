package ai.maran.app.ui
import androidx.compose.foundation.layout.*
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
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.maran.app.data.RemoteMission
private enum class Tab(val label:String,val icon:ImageVector){Home("Home",Icons.Rounded.Home),Missions("Missions",Icons.Rounded.Checklist),Maran("MARAN",Icons.Rounded.GraphicEq),Workforce("Workforce",Icons.Rounded.Groups),More("More",Icons.Rounded.GridView)}
@Composable fun MaranApp(vm:MaranViewModel=viewModel()){
 var tab by remember{mutableStateOf(Tab.Home)}; val state by vm.state.collectAsState()
 var spoken by remember{mutableStateOf<String?>(null)}
 val speech=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){r-> if(r.resultCode==android.app.Activity.RESULT_OK){ SpeechController.result(r.data)?.let{spoken=it;vm.handleVoice(it)} } }
 MaterialTheme(colorScheme=if(androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()){
  Scaffold(bottomBar={NavigationBar{Tab.entries.forEach{item->NavigationBarItem(selected=tab==item,onClick={tab=item},icon={Icon(item.icon,null)},label={Text(item.label)})}}}){pad->
   Box(Modifier.padding(pad).fillMaxSize()){when(tab){Tab.Home->HomeScreen(state,vm::create,{speech.launch(SpeechController.intent())});Tab.Missions->MissionScreen(state,vm::decide);Tab.Maran->CommandScreen(state,vm::create,{speech.launch(SpeechController.intent())},spoken);Tab.Workforce->WorkforceScreen(state.missions,state.workers,vm::stopWorker);Tab.More->SimpleScreen("More","Skills · Connections · Knowledge · Automations · Settings",Icons.Rounded.GridView)}}
  }
 }
}
@Composable private fun HomeScreen(state:MaranUiState,onCreate:(String)->Unit,onVoice:()->Unit){
 var input by remember{mutableStateOf("")}
 Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column{Text("MARAN",style=MaterialTheme.typography.headlineLarge);Text("Your Personal AI Workforce",color=MaterialTheme.colorScheme.onSurfaceVariant)};AssistChip(onClick={},label={Text(if(state.connected)"● Connected" else "○ Offline")})}
  Card(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){Text("What should we get done?",style=MaterialTheme.typography.titleLarge);FilledIconButton(onClick=onVoice,modifier=Modifier.size(72.dp)){Icon(Icons.Rounded.Mic,"Talk",Modifier.size(34.dp))};OutlinedTextField(input,{input=it},Modifier.fillMaxWidth(),placeholder={Text("Ask MARAN anything…")},trailingIcon={IconButton(onClick={onCreate(input);input=""}){Icon(Icons.Rounded.Send,"Start mission")}})}}
  state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)};Text("Continue",style=MaterialTheme.typography.titleMedium)
  LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){items(state.missions.take(5)){mission->MissionCard(mission,null)}}
 }
}
@Composable private fun CommandScreen(state:MaranUiState,onCreate:(String)->Unit,onVoice:()->Unit,spoken:String?){
 var input by remember{mutableStateOf("")}
 Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
  FilledIconButton(onClick=onVoice,modifier=Modifier.size(110.dp)){Icon(Icons.Rounded.GraphicEq,"MARAN voice",Modifier.size(52.dp))};Spacer(Modifier.height(20.dp));Text("MARAN",style=MaterialTheme.typography.headlineLarge);Text("Tell me the outcome. I’ll organize the workforce.");spoken?.let{Text("Heard: $it",color=MaterialTheme.colorScheme.onSurfaceVariant)};Spacer(Modifier.height(20.dp));OutlinedTextField(input,{input=it},Modifier.fillMaxWidth(),placeholder={Text("Type a mission…")});Spacer(Modifier.height(10.dp));Button(onClick={onCreate(input);input=""},enabled=!state.busy&&input.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text(if(state.busy)"Planning…" else "Start mission")}
 }
}
@Composable private fun MissionScreen(state:MaranUiState,onDecision:(String,Boolean)->Unit){Column(Modifier.fillMaxSize().padding(20.dp)){Text("Missions",style=MaterialTheme.typography.headlineLarge);Spacer(Modifier.height(12.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)){items(state.missions){m->MissionCard(m,if(m.status=="waiting_approval") onDecision else null)}}}}
@Composable private fun MissionCard(m:RemoteMission,onDecision:((String,Boolean)->Unit)?){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(m.objective,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f));AssistChip(onClick={},label={Text(m.status.replace('_',' '))})};Text(m.assigned_agents.joinToString(" · "),color=MaterialTheme.colorScheme.onSurfaceVariant);m.plan.take(3).forEach{step->Text("• "+step.title,style=MaterialTheme.typography.bodySmall)};m.result?.summary?.let{Text(it,style=MaterialTheme.typography.bodyMedium)};if(m.status=="completed"||m.status=="failed"){Text("Verification: "+m.verification,color=MaterialTheme.colorScheme.onSurfaceVariant)};if(onDecision!=null){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={onDecision(m.id,false)}){Text("Reject")};Button(onClick={onDecision(m.id,true)}){Text("Approve")}}}}}}
@Composable private fun WorkforceScreen(missions:List<RemoteMission>,workers:List<ai.maran.app.data.WorkerDto>,onStop:(String)->Unit){val agents=missions.flatMap{it.assigned_agents}.distinct();Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Workforce",style=MaterialTheme.typography.headlineLarge);Text("Chief MARAN",style=MaterialTheme.typography.titleLarge);agents.forEach{agent->AssistChip(onClick={},label={Text(agent.replace('_',' ').replaceFirstChar(Char::uppercase))})};if(workers.isNotEmpty()){Text("Dynamic workers",style=MaterialTheme.typography.titleMedium);workers.forEach{w->AssistChip(onClick={onStop(w.id)},label={Text(w.name)},trailingIcon={Icon(Icons.Rounded.Close,"Stop worker")})}}}}
@Composable private fun SimpleScreen(title:String,body:String,icon:ImageVector){Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Icon(icon,null,Modifier.size(36.dp));Text(title,style=MaterialTheme.typography.headlineLarge);Text(body,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
