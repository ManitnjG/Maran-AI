package ai.maran.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private enum class Tab(val label:String,val icon:ImageVector){
    Home("Home",Icons.Rounded.Home), Missions("Missions",Icons.Rounded.Checklist),
    Maran("MARAN",Icons.Rounded.GraphicEq), Workforce("Workforce",Icons.Rounded.Groups),
    More("More",Icons.Rounded.GridView)
}
data class Mission(val title:String,val detail:String,val progress:Float)

@Composable fun MaranApp(){
    var tab by remember { mutableStateOf(Tab.Home) }
    MaterialTheme(colorScheme=if(androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()){
        Scaffold(bottomBar={
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(selected=tab==item,onClick={tab=item},icon={
                        if(item==Tab.Maran) FilledIconButton(onClick={tab=Tab.Maran}){Icon(item.icon,null)}
                        else Icon(item.icon,null)
                    },label={Text(item.label)})
                }
            }
        }){ pad ->
            Box(Modifier.padding(pad).fillMaxSize()){
                when(tab){
                    Tab.Home->HomeScreen()
                    Tab.Missions->MissionsScreen()
                    Tab.Maran->VoiceScreen()
                    Tab.Workforce->WorkforceScreen()
                    Tab.More->MoreScreen()
                }
            }
        }
    }
}

@Composable private fun HomeScreen(){
    val missions=listOf(
        Mission("Find corporate tour leads","Lead Scout + Research Agent",.64f),
        Mission("Website SEO audit","3 agents working",.42f),
        Mission("September accounts","Waiting for approval",.82f)
    )
    Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            Column { Text("MARAN",style=MaterialTheme.typography.headlineLarge); Text("Your Personal AI Workforce",color=MaterialTheme.colorScheme.onSurfaceVariant) }
            AssistChip(onClick={},label={Text("● Ready")})
        }
        Card(Modifier.fillMaxWidth()){
            Column(Modifier.padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text("What should we get done?",style=MaterialTheme.typography.titleLarge)
                FilledIconButton(onClick={},modifier=Modifier.size(72.dp)){Icon(Icons.Rounded.Mic,"Talk to MARAN",Modifier.size(34.dp))}
                OutlinedButton(onClick={},modifier=Modifier.fillMaxWidth()){Icon(Icons.Rounded.AutoAwesome,null); Spacer(Modifier.width(8.dp)); Text("Ask MARAN anything…")}
            }
        }
        Text("Continue",style=MaterialTheme.typography.titleMedium)
        missions.forEach { m -> Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(m.title,style=MaterialTheme.typography.titleMedium);Text(m.detail,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(8.dp));LinearProgressIndicator({m.progress},Modifier.fillMaxWidth())}}}
    }
}
@Composable private fun MissionsScreen()=SimpleScreen("Missions","Running, approvals, scheduled, completed and failed work.",Icons.Rounded.Checklist)
@Composable private fun WorkforceScreen()=SimpleScreen("Workforce","Chief MARAN and specialist agents will appear here.",Icons.Rounded.Groups)
@Composable private fun MoreScreen()=SimpleScreen("More","Skills · Connections · Knowledge · Automations · Settings",Icons.Rounded.GridView)
@Composable private fun VoiceScreen(){
    Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
        FilledIconButton(onClick={},modifier=Modifier.size(120.dp)){Icon(Icons.Rounded.GraphicEq,"MARAN voice",Modifier.size(56.dp))}
        Spacer(Modifier.height(24.dp));Text("MARAN",style=MaterialTheme.typography.headlineLarge)
        Text("Tap to speak",color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp));Text("Listening → Planning → Working → Verifying → Done",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun SimpleScreen(title:String,body:String,icon:ImageVector){
    Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Icon(icon,null,Modifier.size(36.dp));Text(title,style=MaterialTheme.typography.headlineLarge);Text(body,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
