package ai.maran.app.ui
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.maran.app.BuildConfig
import ai.maran.app.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MaranUiState(val missions:List<RemoteMission> = emptyList(),val workers:List<WorkerDto> = emptyList(),val busy:Boolean=false,val connected:Boolean=false,val error:String?=null,val serverUrl:String="")
class MaranViewModel(application:Application):AndroidViewModel(application){
 private val prefs=application.getSharedPreferences("connection",0)
 private var api=ApiProvider.create(prefs.getString("url",BuildConfig.MARAN_API_BASE_URL)!!,"")
 private val _state=MutableStateFlow(MaranUiState(serverUrl=prefs.getString("url",BuildConfig.MARAN_API_BASE_URL)!!))
 val state=_state.asStateFlow()
 init { refresh() }
 private suspend fun reload(){
  val missions=api.missions(); val workers=api.workers()
  _state.value=_state.value.copy(missions=missions,workers=workers,connected=true)
 }
 private suspend fun action(block:suspend ()->Unit){
  _state.value=_state.value.copy(busy=true,error=null)
  try{block()}catch(e:CancellationException){throw e}
  catch(e:Exception){_state.value=_state.value.copy(error=if(e is retrofit2.HttpException && e.code()==401) "Enter your server access token in More." else "Request failed. Check the server connection and refresh; your saved mission may still be running.")}
  finally{_state.value=_state.value.copy(busy=false)}
 }
 fun configure(url:String,token:String){
  try{
   val uri=java.net.URI(url.trim())
   require(uri.scheme in listOf("https","http") && !uri.host.isNullOrBlank() && uri.userInfo==null && uri.query==null && uri.fragment==null)
   require(token.isBlank() || uri.scheme=="https")
   api=ApiProvider.create(url,token)
   prefs.edit().putString("url",url.trim()).apply()
   _state.value=_state.value.copy(serverUrl=url.trim(),connected=false,error=null)
   refresh()
  }catch(e:Exception){_state.value=_state.value.copy(error="Enter a valid server URL. Access tokens require HTTPS.")}
 }
 fun refresh()=viewModelScope.launch {
  try{reload()}catch(e:CancellationException){throw e}catch(e:Exception){_state.value=_state.value.copy(connected=false,error="Cannot connect. Set your server URL and access token in More.")}
 }
 fun create(objective:String)=viewModelScope.launch {
  if(objective.isBlank() || _state.value.busy)return@launch
  action{
   val m=api.createMission(MissionCreate(objective.trim()));reload()
   if(m.status!="waiting_approval")api.runMission(m.id)
   reload()
  }
 }
 fun handleVoice(text:String)=viewModelScope.launch {
  action{val r=api.voice(VoiceCommand(text));when(r.action){
   "mission"->{val m=api.createMission(MissionCreate(r.objective?:text));reload();if(m.status!="waiting_approval")api.runMission(m.id);reload()}
   "create_worker"->reload()
   else->_state.value=_state.value.copy(error="Please review and type this command: ${r.heard?:text}")
  }}
 }
 fun stopWorker(id:String)=viewModelScope.launch{action{api.stopWorker(id);reload()}}
 fun createWorker(name:String,skills:List<String>)=viewModelScope.launch{action{api.createWorker(WorkerCreate(name,skills));reload()}}
 fun decide(id:String,approved:Boolean)=viewModelScope.launch{action{api.approve(id,ApprovalDecision(approved));reload()}}
 fun run(id:String)=viewModelScope.launch{action{api.runMission(id);reload()}}
 fun stop(id:String)=viewModelScope.launch{action{api.stopMission(id);reload()}}
 fun voiceUnavailable(){_state.value=_state.value.copy(error="Speech recognition is unavailable. Type your command instead.")}
}
