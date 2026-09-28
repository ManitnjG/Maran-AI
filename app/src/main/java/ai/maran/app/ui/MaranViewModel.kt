package ai.maran.app.ui
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.maran.app.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MaranUiState(val missions:List<RemoteMission> = emptyList(),val busy:Boolean=false,val error:String?=null)
class MaranViewModel:ViewModel(){
 private val _state=MutableStateFlow(MaranUiState()); val state=_state.asStateFlow()
 init { refresh() }
 fun refresh()=viewModelScope.launch {
  try { _state.value=_state.value.copy(missions=ApiProvider.api.missions(),error=null) }
  catch(e:Exception){ _state.value=_state.value.copy(error="MARAN server unavailable") }
 }
 fun create(objective:String)=viewModelScope.launch {
  if(objective.isBlank()) return@launch
  _state.value=_state.value.copy(busy=true,error=null)
  try { ApiProvider.api.createMission(MissionCreate(objective)); refresh() }
  catch(e:Exception){ _state.value=_state.value.copy(error="Could not create mission") }
  finally { _state.value=_state.value.copy(busy=false) }
 }
 fun decide(id:String,approved:Boolean)=viewModelScope.launch {
  try { ApiProvider.api.approve(id,ApprovalDecision(approved)); refresh() }
  catch(e:Exception){ _state.value=_state.value.copy(error="Approval update failed") }
 }
}
