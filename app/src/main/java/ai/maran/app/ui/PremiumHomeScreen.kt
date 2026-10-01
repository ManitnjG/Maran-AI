package ai.maran.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ink=Color(0xFF10172B)
private val violet=Color(0xFF818CF8)
private val blue=Color(0xFF38BDF8)

@Composable
fun PremiumHomeScreen(
 state:MaranUiState,
 onCreate:(String)->Unit,
 onVoice:()->Unit,
 onAi:()->Unit,
 onMissions:()->Unit,
 onWorkers:()->Unit,
 onTools:()->Unit
){
 var prompt by remember { mutableStateOf("") }
 val actions=listOf(
   "Tour planning" to "Create a 3-day Tamil Nadu tour plan with itinerary and budget",
   "Marketing" to "Draft a Tamil and English tour promotion",
   "Research" to "Research corporate tour package opportunities",
   "Tally draft" to "Prepare a sales voucher draft for review"
 )
 LazyColumn(
   modifier=Modifier.fillMaxSize().background(ink),
   contentPadding=PaddingValues(18.dp),
   verticalArrangement=Arrangement.spacedBy(16.dp)
 ){
  item {
   Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){
    Surface(shape=RoundedCornerShape(15.dp),color=violet.copy(alpha=.18f)){
     Icon(Icons.Rounded.AutoAwesome,"MARAN",Modifier.padding(13.dp).size(26.dp),tint=violet)
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)){
     Text("MARAN",style=MaterialTheme.typography.headlineSmall,color=Color.White)
     Text("Your AI workforce",color=Color(0xFF9CA9C5),style=MaterialTheme.typography.bodySmall)
    }
    Surface(shape=RoundedCornerShape(16.dp),color=if(state.connected) Color(0xFF123D34) else Color(0xFF313649)){
     Text(if(state.connected) "● Connected" else "○ Offline",Modifier.padding(horizontal=11.dp,vertical=7.dp),color=if(state.connected) Color(0xFF6EE7B7) else Color(0xFFCBD5E1),style=MaterialTheme.typography.labelSmall)
    }
   }
  }
  item {
   Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF1C2450)),modifier=Modifier.fillMaxWidth()){
    Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFF312E81),Color(0xFF172554),Color(0xFF14273E)))).padding(21.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
     Text("What can I do for you?",style=MaterialTheme.typography.headlineMedium,color=Color.White)
     Text("Describe your goal. MARAN can help you plan and coordinate the work.",color=Color(0xFFCBD5E1))
     OutlinedTextField(value=prompt,onValueChange={prompt=it},modifier=Modifier.fillMaxWidth(),enabled=!state.busy,placeholder={Text("Type a mission or request…")},minLines=2,maxLines=4,shape=RoundedCornerShape(16.dp),colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedBorderColor=violet,unfocusedBorderColor=Color(0xFF62719E),focusedPlaceholderColor=Color(0xFFB7C2DA),unfocusedPlaceholderColor=Color(0xFFB7C2DA))))
     Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
      Button(onClick={val value=prompt.trim();if(value.isNotEmpty()){onCreate(value);prompt=""}},enabled=prompt.isNotBlank()&&!state.busy,modifier=Modifier.weight(1f),shape=RoundedCornerShape(12.dp)){Icon(Icons.Rounded.ArrowUpward,null);Spacer(Modifier.width(6.dp));Text(if(state.busy) "Working…" else "Start mission")}
      FilledTonalIconButton(onClick=onVoice,enabled=!state.busy,modifier=Modifier.size(48.dp)){Icon(Icons.Rounded.Mic,"Speak to MARAN")}
     }
    }
   }
  }
  item {
   Text("Workspace",style=MaterialTheme.typography.titleLarge,color=Color.White)
   Spacer(Modifier.height(10.dp))
   Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
    DashboardTile("AI chat","Ask anything",Icons.Rounded.AutoAwesome,Modifier.weight(1f),onAi)
    DashboardTile("Missions","${state.missions.size} total",Icons.Rounded.Checklist,Modifier.weight(1f),onMissions)
   }
   Spacer(Modifier.height(9.dp))
   Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
    DashboardTile("Workers","${state.workers.size} saved",Icons.Rounded.Groups,Modifier.weight(1f),onWorkers)
    DashboardTile("Tools","Open toolbox",Icons.Rounded.Build,Modifier.weight(1f),onTools)
   }
  }
  item {
   Text("Quick starts",style=MaterialTheme.typography.titleLarge,color=Color.White)
   Spacer(Modifier.height(9.dp))
   actions.forEach { (title,description)->
    OutlinedCard(onClick={prompt=description},modifier=Modifier.fillMaxWidth().padding(bottom=8.dp),shape=RoundedCornerShape(14.dp),colors=CardDefaults.outlinedCardColors(containerColor=Color(0xFF1A243A)),border=CardDefaults.outlinedCardBorder().copy(brush=androidx.compose.ui.graphics.SolidColor(Color(0xFF33415B)))) {
     Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
      Column(Modifier.weight(1f)){Text(title,color=Color.White,style=MaterialTheme.typography.titleSmall);Text(description,color=Color(0xFFA5B4CD),style=MaterialTheme.typography.bodySmall)}
      Icon(Icons.Rounded.NorthEast,null,tint=blue)
     }
    }
   }
  }
  if(state.error!=null) item { Text(state.error,color=Color(0xFFFCA5A5)) }
  item { Text("Recent missions",style=MaterialTheme.typography.titleLarge,color=Color.White) }
  if(state.missions.isEmpty()) item { Text("Your missions will appear here.",color=Color(0xFFA5B4CD)) }
  items(state.missions.take(4)){ mission->
   Surface(color=Color(0xFF1A243A),shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()){
    Column(Modifier.padding(14.dp)){Text(mission.objective,color=Color.White,style=MaterialTheme.typography.titleSmall);Text(mission.status.replace('_',' '),color=blue,style=MaterialTheme.typography.labelMedium)}
   }
  }
 }
}

@Composable
private fun DashboardTile(label:String,sub:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier,onClick:()->Unit){
 Surface(onClick=onClick,modifier=modifier,height=120.dp,shape=RoundedCornerShape(17.dp),color=Color(0xFF1A243A)){
  Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.SpaceBetween){
   Icon(icon,label,tint=violet,modifier=Modifier.size(24.dp))
   Column { Text(label,color=Color.White,style=MaterialTheme.typography.titleMedium);Text(sub,color=Color(0xFFA5B4CD),style=MaterialTheme.typography.bodySmall) }
  }
 }
}
