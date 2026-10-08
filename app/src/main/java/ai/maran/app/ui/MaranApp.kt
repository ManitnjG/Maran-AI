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
private enum class Tab(val label:String,val icon:ImageVector){Home("Home",Icons.Rounded.Home),Missions("Missions",Icons.Rounded.Checklist),Approvals("Approve",Icons.Rounded.FactCheck),Ai("AI",Icons.Rounded.AutoAwesome),Workforce("Workers",Icons.Rounded.Groups),Tools("Tools",Icons.Rounded.Build)}
@Composable fun MaranApp(vm:MaranViewModel=viewModel(),initialTab:String?=null,focusMissionId:String?=null){
 var tab by remember{mutableStateOf(
  when(initialTab){
   "approvals"->Tab.Approvals
   "missions"->Tab.Missions
   else->Tab.Home
  }
 )}
 LaunchedEffect(initialTab){
  tab=when(initialTab){
   "approvals"->Tab.Approvals
   "missions"->Tab.Missions
   else->tab
  }
 }
 val state by vm.state.collectAsState()
 var voiceStart by remember { mutableIntStateOf(0) }
 var pendingVoiceCommand by remember { mutableStateOf<String?>(null) }
 MaranTheme {
  Scaffold(containerColor=MaranTokens.background,bottomBar={
   NavigationBar(containerColor=MaranTokens.surface,tonalElevation=0.dp) {
    Tab.entries.forEach { item ->
     NavigationBarItem(
      selected=tab==item,
      onClick={tab=item},
      icon={Icon(item.icon,contentDescription=item.label)},
      label={Text(item.label)},
      colors=NavigationBarItemDefaults.colors(
       selectedIconColor=MaranTokens.accent,
       selectedTextColor=MaranTokens.text,
       indicatorColor=MaranTokens.accentMuted,
       unselectedIconColor=MaranTokens.muted,
       unselectedTextColor=MaranTokens.muted
      )
     )
    }
   }
  }){pad->
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
     Tab.Missions -> MissionScreen(state,vm::decide,vm::run,vm::stop,vm::refresh,vm::exportMission,focusMissionId)
     Tab.Approvals -> ApprovalScreen(state,vm::decide,vm::decideAction,vm::refresh,focusMissionId)
     Tab.Workforce -> WorkforceScreen(state.missions,state.workers,state.learnedSkills,vm::stopWorker,vm::createWorker)
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
@Composable private fun MissionScreen(state:MaranUiState,onDecision:(String,Boolean)->Unit,onRun:(String)->Unit,onStop:(String)->Unit,onRefresh:()->Unit,onExport:(String)->Unit,focusMissionId:String?=null){
 val ordered=state.missions.sortedByDescending{it.id==focusMissionId}
 Column(Modifier.fillMaxSize().padding(20.dp)){MaranSectionHeading("Missions","Manage plans and approvals");MaranSecondaryButton("Refresh",onRefresh);state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)};Spacer(Modifier.height(12.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)){items(ordered){m->MissionCard(m,if(m.status=="waiting_approval"&&m.actions.isEmpty()) onDecision else null,onRun,onStop,onExport)}}}
}
@Composable private fun MissionCard(
 m:RemoteMission,
 onDecision:((String,Boolean)->Unit)?,
 onRun:((String)->Unit)?=null,
 onStop:((String)->Unit)?=null,
 onExport:((String)->Unit)?=null
){
 MaranPanel(Modifier.fillMaxWidth()){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Top){
   Text(m.objective,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
   AssistChip(onClick={},label={Text(m.status.replace('_',' '))})
  }
  if(m.autonomy_enabled){
   Text("Autonomous • cycle ${m.cycle}/${m.max_cycles}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
  }
  if(m.assigned_agents.isNotEmpty()){
   Text(m.assigned_agents.joinToString(" · "),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
  }
  m.plan.forEach{step->
   val detail=buildString{
    append("• ");append(step.title);append(" — ");append(step.status)
    if(step.attempts>0) append(" • attempt ${step.attempts}")
    if(step.risk_level!="auto") append(" • ${step.risk_level.replace('_',' ')}")
    step.tool_id?.let{append(" • tool ");append(it.replace('_',' '))}
   }
   Text(detail,style=MaterialTheme.typography.bodySmall)
   step.approval_reason?.let{Text(it,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
   step.output?.let{androidx.compose.foundation.text.selection.SelectionContainer{Text(it)}}
   step.evidence?.let{e->
    e.retrieved_at?.let{Text("Retrieved: "+it,style=MaterialTheme.typography.bodySmall)}
    e.sources.forEach{url->
     val handler=androidx.compose.ui.platform.LocalUriHandler.current
     TextButton(onClick={try{handler.openUri(url)}catch(_:Exception){}}){Text(url)}
    }
   }
   step.error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
  }
  m.result?.summary?.let{Text(it,style=MaterialTheme.typography.bodyMedium)}
  m.result?.note?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
  if(m.events.isNotEmpty()){
   Text("Agent activity",style=MaterialTheme.typography.titleSmall)
   m.events.takeLast(5).forEach{event->
    val extra=listOfNotNull(
     event.cycle?.let{"cycle $it"},
     event.step_id,
     event.reason,
     event.outcome,
     event.name
    ).joinToString(" • ")
    Text("• "+event.type.replace('_',' ')+(if(extra.isBlank())"" else " — $extra"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   }
  }
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   if(onExport!=null) MaranSecondaryButton("Export",onClick={onExport(m.id)})
   if(onRun!=null&&m.status in listOf("blocked","failed","running")) MaranPrimaryButton("Continue",onClick={onRun(m.id)})
  }
  if(onStop!=null&&m.status !in listOf("completed","cancelled")){
   MaranSecondaryButton("Stop mission",onClick={onStop(m.id)})
  }
  if(m.status in listOf("completed","failed","blocked")){
   Text("Verification: "+m.verification,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
  if(onDecision!=null){
   Text("MARAN is waiting for your approval before consequential work.",style=MaterialTheme.typography.bodySmall)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
    MaranSecondaryButton("Reject",onClick={onDecision(m.id,false)})
    MaranPrimaryButton("Approve",onClick={onDecision(m.id,true)})
   }
  }
 }
}

@Composable private fun ApprovalScreen(
 state:MaranUiState,
 onMissionDecision:(String,Boolean)->Unit,
 onActionDecision:(String,String,Boolean)->Unit,
 onRefresh:()->Unit,
 focusMissionId:String?=null
){
 val waiting=state.missions.filter { m ->
  (m.status=="waiting_approval" && m.actions.isEmpty()) ||
  m.actions.any { it.status in listOf("waiting_approval","needs_connection","failed") }
 }.sortedByDescending{it.id==focusMissionId}
 Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  MaranSectionHeading("Approval Centre","Review the exact plan or external action before anything consequential runs")
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   AssistChip(onClick={},label={Text("\${waiting.size} waiting")})
   MaranSecondaryButton("Refresh",onRefresh)
  }
  state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  if(waiting.isEmpty()){
   MaranPanel(Modifier.fillMaxWidth()){
    Text("Nothing needs approval",style=MaterialTheme.typography.titleMedium)
    Text("Safe research, drafting and verification can continue automatically. External side effects appear here first.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   }
  } else {
   LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)){
    items(waiting){m->
     MaranPanel(Modifier.fillMaxWidth()){
      Text(m.objective,style=MaterialTheme.typography.titleMedium)
      if(m.status=="waiting_approval"&&m.actions.isEmpty()){
       Text("Mission plan approval",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
       m.plan.filter{it.requires_approval&&!it.approved}.forEach{step->
        Text("• "+step.title,style=MaterialTheme.typography.bodySmall)
        step.approval_reason?.let{Text(it,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
       }
       Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        MaranSecondaryButton("Reject",onClick={onMissionDecision(m.id,false)})
        MaranPrimaryButton("Approve plan",onClick={onMissionDecision(m.id,true)})
       }
      }
      m.actions.filter{it.status in listOf("waiting_approval","needs_connection","failed")}.forEach{action->
       HorizontalDivider()
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
        Text(action.title,style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f))
        AssistChip(onClick={},label={Text(action.status.replace('_',' '))})
       }
       Text("Tool: "+action.tool_id.replace('_',' '),style=MaterialTheme.typography.bodySmall)
       Text("Connection: "+action.connection_status.replace('_',' '),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
       action.reason?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
       val args=action.args.entries.joinToString("\n"){(k,v)->"\$k: \${v?.toString()?.take(700) ?: ""}"}.take(1800)
       if(args.isNotBlank()){
        androidx.compose.foundation.text.selection.SelectionContainer{
         Text(args,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
       }
       action.error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
       Text(
        "Approval applies only to this exact action. MARAN cannot use it to approve another action, OTP, CAPTCHA, password, PIN, CVV, biometric or payment authentication.",
        style=MaterialTheme.typography.labelSmall,
        color=MaterialTheme.colorScheme.onSurfaceVariant
       )
       Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        MaranSecondaryButton("Reject",onClick={onActionDecision(m.id,action.id,false)})
        MaranPrimaryButton(
         if(action.status=="waiting_approval") "Approve action" else "Retry action",
         onClick={onActionDecision(m.id,action.id,true)}
        )
       }
      }
     }
    }
   }
  }
 }
}

@Composable private fun WorkforceScreen(
 missions:List<RemoteMission>,
 workers:List<ai.maran.app.data.WorkerDto>,
 learnedSkills:List<ai.maran.app.data.LearnedSkillDto>,
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
  MaranSectionHeading("Your team","MARAN coordinates every task")

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
     MaranPrimaryButton(
      label="Add to team",
      onClick={
       val parsed=skills.split(",").map { it.trim() }.filter { it.isNotBlank() }
       onCreate(name,parsed);name="";skills="";showAdd=false
      },
      enabled=name.isNotBlank(),
      modifier=Modifier.fillMaxWidth()
     )
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

  if(learnedSkills.isNotEmpty()){
   MaranSectionHeading("Learned workflows","Successful worker combinations MARAN can reuse")
   learnedSkills.take(8).forEach{skill->
    MaranPanel(Modifier.fillMaxWidth()){
     Text(skill.name,style=MaterialTheme.typography.titleSmall)
     Text(skill.agents.joinToString(" • "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
     Text("Used successfully "+skill.success_count+" time"+if(skill.success_count==1)"" else "s",style=MaterialTheme.typography.labelSmall)
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

