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

data class MaranUiState(val missions:List<RemoteMission> = emptyList(),val workers:List<WorkerDto> = emptyList(),val busy:Boolean=false,val connected:Boolean=false,val deviceMode:Boolean=true,val error:String?=null,val serverUrl:String="",val capabilities:Capabilities?=null,val diagnostics:String?=null,val export:ExportResult?=null,val voiceLanguage:String="en-IN")
class MaranViewModel(application:Application):AndroidViewModel(application){
 private val prefs=application.getSharedPreferences("connection",0)
 private val tokenStore=ai.maran.app.security.SecureTokenStore(application)
 private val savedUrl=prefs.getString("url",BuildConfig.MARAN_API_BASE_URL)!!
 private val deviceApi=DeviceTaskApi(application,viewModelScope)
 private var api:MaranApi=if(prefs.getBoolean("device_mode",DeviceTaskPolicy.defaultDeviceMode(savedUrl)))deviceApi else ApiProvider.create(savedUrl,tokenStore.read())
 private val _state=MutableStateFlow(MaranUiState(deviceMode=api===deviceApi,voiceLanguage=prefs.getString("voice_language","en-IN")!!,serverUrl=if(DeviceTaskPolicy.defaultDeviceMode(savedUrl))"" else savedUrl))
 val state=_state.asStateFlow()
 init {
  refresh()
  viewModelScope.launch {
   while(true){kotlinx.coroutines.delay(750);if(_state.value.deviceMode)reload()}
  }
 }
 fun useDeviceMode(){
  prefs.edit().putBoolean("device_mode",true).apply();api=deviceApi
  _state.value=_state.value.copy(deviceMode=true,connected=false,error=null,missions=emptyList(),workers=emptyList(),export=null)
  refresh()
 }
 private suspend fun reload(){
  val source=api
  val missions=source.missions(); val workers=source.workers();val capabilities=source.capabilities()
  if(source===api)_state.value=_state.value.copy(missions=missions,workers=workers,connected=true,capabilities=capabilities)
 }
 private suspend fun action(block:suspend ()->Unit){
  _state.value=_state.value.copy(busy=true,error=null)
  try{block()}catch(e:CancellationException){throw e}
  catch(e:Exception){_state.value=_state.value.copy(error=if(e is retrofit2.HttpException && e.code()==401) "Server authorization failed. Check the configured MARAN server." else if(_state.value.deviceMode)e.message ?: "Device task failed. Please retry." else "Request failed. Check the server connection and refresh; your saved mission may still be running.")}
  finally{_state.value=_state.value.copy(busy=false)}
 }
 fun configure(url:String,token:String){
  try{
   val uri=java.net.URI(url.trim())
   require(!DeviceTaskPolicy.defaultDeviceMode(url)){"Use device mode instead of an emulator address."}
   require(uri.scheme in listOf("https","http") && !uri.host.isNullOrBlank() && uri.userInfo==null && uri.query==null && uri.fragment==null)
   require(token.isBlank() || uri.scheme=="https")
   val effectiveToken=if(token.isBlank()&&url.trim()==_state.value.serverUrl)tokenStore.read() else token
   require(effectiveToken.isBlank() || uri.scheme=="https")
   tokenStore.save(effectiveToken)
   api=ApiProvider.create(url,effectiveToken)
   prefs.edit().putString("url",url.trim()).putBoolean("device_mode",false).apply()
   _state.value=_state.value.copy(serverUrl=url.trim(),deviceMode=false,connected=false,error=null,missions=emptyList(),workers=emptyList(),export=null)
   refresh()
  }catch(e:Exception){_state.value=_state.value.copy(error="Enter a reachable server URL, not an emulator or localhost address. Access tokens require HTTPS.")}
 }
 fun refresh()=viewModelScope.launch {
  try{reload()}catch(e:CancellationException){throw e}catch(e:Exception){_state.value=_state.value.copy(connected=false,error="Cannot connect to the configured MARAN server.")}
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
  if(_state.value.busy)return@launch
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
 fun clearToken(){tokenStore.save("");if(!_state.value.deviceMode)api=ApiProvider.create(_state.value.serverUrl,"");refresh()}
 fun checkConnections()=viewModelScope.launch{action{val r=api.diagnostics();_state.value=_state.value.copy(diagnostics=r.entrySet().joinToString("\n"){(k,v)->k+": "+v.asJsonObject.get("message").asString})}}
 fun backup()=viewModelScope.launch{action{_state.value=_state.value.copy(export=ExportResult("maran-backup.json",com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(api.backup())))}}
 fun restore(text:String)=viewModelScope.launch{action{val result=api.restore(com.google.gson.JsonParser.parseString(text).asJsonObject);reload();_state.value=_state.value.copy(diagnostics="Restored missions: "+result.get("restored").asInt)}}
 fun voucher(request:SalesVoucherRequest)=viewModelScope.launch{action{_state.value=_state.value.copy(export=api.voucher(request))}}
 fun exportMission(id:String)=viewModelScope.launch{action{_state.value=_state.value.copy(export=api.export(id))}}
 fun setLanguage(language:String){prefs.edit().putString("voice_language",language).apply();_state.value=_state.value.copy(voiceLanguage=language)}
 fun voiceUnavailable(){_state.value=_state.value.copy(error="Speech recognition is unavailable. Type your command instead.")}
}
