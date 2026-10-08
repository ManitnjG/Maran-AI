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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody

data class MaranUiState(val missions:List<RemoteMission> = emptyList(),val workers:List<WorkerDto> = emptyList(),val learnedSkills:List<LearnedSkillDto> = emptyList(),val memory:List<MemoryItem> = emptyList(),val knowledge:List<KnowledgeDoc> = emptyList(),val knowledgeHits:List<KnowledgeHit> = emptyList(),val busy:Boolean=false,val connected:Boolean=false,val error:String?=null,val serverUrl:String="",val capabilities:Capabilities?=null,val diagnostics:String?=null,val export:ExportResult?=null,val voiceLanguage:String="en-IN")
class MaranViewModel(application:Application):AndroidViewModel(application){
 private val prefs=application.getSharedPreferences("connection",0)
 private val tokenStore=ai.maran.app.security.SecureTokenStore(application)
 private val localWorkers=LocalWorkerStore(application)
 private var api=ApiProvider.create(prefs.getString("url",BuildConfig.MARAN_API_BASE_URL)!!,tokenStore.read())
 private val _state=MutableStateFlow(MaranUiState(serverUrl=prefs.getString("url",BuildConfig.MARAN_API_BASE_URL)!!,workers=localWorkers.list()))
 val state=_state.asStateFlow()
 init { MaranAutonomyWork.schedule(application); refresh() }
 private suspend fun reload(){
  val missions=api.missions()
  val learned=try{api.skills()}catch(_:Exception){emptyList()}
  val memory=try{api.memory()}catch(_:Exception){emptyList()}
  val knowledge=try{api.knowledge()}catch(_:Exception){emptyList()}
  _state.value=_state.value.copy(missions=missions,workers=localWorkers.list(),learnedSkills=learned,memory=memory,knowledge=knowledge,connected=true,capabilities=api.capabilities(),error=null)
 }
 private suspend fun action(block:suspend ()->Unit){
  _state.value=_state.value.copy(busy=true,error=null)
  try{block()}catch(e:CancellationException){throw e}
  catch(e:Exception){_state.value=_state.value.copy(error=if(e is retrofit2.HttpException && e.code()==401) "Server authorization failed. Check the configured MARAN server." else "Request failed. Check the server connection and refresh; your saved mission may still be running.")}
  finally{_state.value=_state.value.copy(busy=false)}
 }
 fun configure(url:String,token:String){
  try{
   val uri=java.net.URI(url.trim())
   require(uri.scheme in listOf("https","http") && !uri.host.isNullOrBlank() && uri.userInfo==null && uri.query==null && uri.fragment==null)
   require(token.isBlank() || uri.scheme=="https")
   val effectiveToken=if(token.isBlank()&&url.trim()==_state.value.serverUrl)tokenStore.read() else token
   require(effectiveToken.isBlank() || uri.scheme=="https")
   tokenStore.save(effectiveToken)
   api=ApiProvider.create(url,effectiveToken)
   prefs.edit().putString("url",url.trim()).apply()
   _state.value=_state.value.copy(serverUrl=url.trim(),connected=false,error=null)
   refresh()
  }catch(e:Exception){_state.value=_state.value.copy(error="Enter a valid server URL. Access tokens require HTTPS.")}
 }
 fun refresh()=viewModelScope.launch {
  try{reload()}catch(e:CancellationException){throw e}catch(e:Exception){_state.value=_state.value.copy(connected=false,workers=localWorkers.list(),error=null)}
 }
 fun create(objective:String)=viewModelScope.launch {
  if(objective.isBlank() || _state.value.busy)return@launch
  action{
   val m=api.createMission(MissionCreate(objective.trim(),autonomous=true,max_cycles=4));reload()
   if(m.status!="waiting_approval"){
    MaranAutonomyWork.enqueue(getApplication(),m.id)
    api.autonomousRun(m.id)
   }
   reload()
  }
 }
 fun handleVoice(text:String)=viewModelScope.launch {
  if(_state.value.busy)return@launch
  action{val r=api.voice(VoiceCommand(text));when(r.action){
   "mission"->{val m=api.createMission(MissionCreate(r.objective?:text,autonomous=true,max_cycles=4));reload();if(m.status!="waiting_approval"){MaranAutonomyWork.enqueue(getApplication(),m.id);api.autonomousRun(m.id)};reload()}
   "create_worker"->reload()
   else->_state.value=_state.value.copy(error="Please review and type this command: ${r.heard?:text}")
  }}
 }
 fun stopWorker(id:String){
  localWorkers.remove(id)
  _state.value=_state.value.copy(workers=localWorkers.list(),error=null)
 }
 fun createWorker(name:String,skills:List<String>){
  if(name.isBlank())return
  localWorkers.add(name,skills)
  _state.value=_state.value.copy(workers=localWorkers.list(),error=null)
 }
 fun decide(id:String,approved:Boolean)=viewModelScope.launch{action{api.approve(id,ApprovalDecision(approved));reload()}}
 fun decideAction(missionId:String,actionId:String,approved:Boolean)=viewModelScope.launch{
  action{api.decideAction(missionId,actionId,ActionDecision(approved));reload()}
 }
 fun run(id:String)=viewModelScope.launch{action{MaranAutonomyWork.enqueue(getApplication(),id);api.autonomousRun(id);reload()}}
 fun stop(id:String)=viewModelScope.launch{action{api.stopMission(id);reload()}}
 fun saveMemory(key:String,value:String)=viewModelScope.launch{
  if(key.isBlank()||value.isBlank())return@launch
  action{api.remember(MemoryWrite(key.trim(),value.trim()));reload()}
 }
 fun forgetMemory(key:String)=viewModelScope.launch{action{api.forgetMemory(key);reload()}}
 fun uploadKnowledge(name:String,mimeType:String,bytes:ByteArray,allowAi:Boolean)=viewModelScope.launch{
  if(bytes.isEmpty()||bytes.size>5_000_000){_state.value=_state.value.copy(error="Knowledge files must be between 1 byte and 5 MB.");return@launch}
  action{
   val media=(mimeType.ifBlank{"application/octet-stream"}).toMediaTypeOrNull()
   val body=bytes.toRequestBody(media)
   val part=MultipartBody.Part.createFormData("file",name,body)
   val workspace="default".toRequestBody("text/plain".toMediaTypeOrNull())
   val allow=allowAi.toString().toRequestBody("text/plain".toMediaTypeOrNull())
   api.uploadKnowledge(part,workspace,allow)
   reload()
  }
 }
 fun deleteKnowledge(id:String)=viewModelScope.launch{action{api.deleteKnowledge(id);reload()}}
 fun searchKnowledge(query:String)=viewModelScope.launch{
  if(query.isBlank()){_state.value=_state.value.copy(knowledgeHits=emptyList());return@launch}
  action{_state.value=_state.value.copy(knowledgeHits=api.searchKnowledge(query.trim()))}
 }
 fun deleteRemoteWorkspace()=viewModelScope.launch{
  action{
   api.deleteWorkspace(WorkspaceDeleteRequest())
   _state.value=_state.value.copy(missions=emptyList(),learnedSkills=emptyList(),memory=emptyList(),knowledge=emptyList(),diagnostics="Remote MARAN workspace data deleted.")
   reload()
  }
 }
 fun clearToken(){tokenStore.save("");api=ApiProvider.create(_state.value.serverUrl,"");refresh()}
 fun checkConnections()=viewModelScope.launch{action{val r=api.diagnostics();_state.value=_state.value.copy(diagnostics=r.entrySet().joinToString("\n"){(k,v)->k+": "+v.asJsonObject.get("message").asString})}}
 fun backup()=viewModelScope.launch{action{_state.value=_state.value.copy(export=ExportResult("maran-backup.json",com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(api.backup())))}}
 fun restore(text:String)=viewModelScope.launch{action{val result=api.restore(com.google.gson.JsonParser.parseString(text).asJsonObject);reload();_state.value=_state.value.copy(diagnostics="Restored missions: "+result.get("restored").asInt)}}
 fun voucher(request:SalesVoucherRequest)=viewModelScope.launch{action{_state.value=_state.value.copy(export=api.voucher(request))}}
 fun exportMission(id:String)=viewModelScope.launch{action{_state.value=_state.value.copy(export=api.export(id))}}
 fun setLanguage(language:String){_state.value=_state.value.copy(voiceLanguage=language)}
 fun voiceUnavailable(){_state.value=_state.value.copy(error="Speech recognition is unavailable. Type your command instead.")}
}
