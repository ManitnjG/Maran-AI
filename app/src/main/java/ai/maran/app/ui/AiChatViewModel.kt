package ai.maran.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.maran.app.data.*
import ai.maran.app.security.SecureTokenStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class AiChatState(
 val models:List<FreeModel> = emptyList(),val selected:String="",val messages:List<ChatMessage> = emptyList(),
 val busy:Boolean=false,val error:String?=null,val keySaved:Boolean=false,val lastModel:String?=null
)
class AiChatViewModel(application:Application):AndroidViewModel(application) {
 private val client=OpenCodeClient()
 private val keys=SecureTokenStore(application,"opencode")
 private val mutable=MutableStateFlow(AiChatState(keySaved=keys.read().isNotBlank()))
 val state=mutable.asStateFlow()
 private var job:Job?=null
 init { refresh() }
 fun select(id:String) { if(!state.value.busy) mutable.update { it.copy(selected=id) } }
 fun saveKey(key:String) { try { keys.save(key.trim());mutable.update { it.copy(keySaved=key.isNotBlank(),error=null) } }
 catch(_:Exception) { mutable.update { it.copy(error="Could not save the key securely.") } } }
 fun clear() { if(!state.value.busy) mutable.update { it.copy(messages=emptyList(),error=null,lastModel=null) } }
 fun stop() { job?.cancel() }
 fun refresh() {
  if(state.value.busy) return
  job=viewModelScope.launch {
   mutable.update { it.copy(busy=true,error=null) }
   try { val models=client.models();mutable.update { it.copy(models=models,error=if(models.isEmpty()) "No active free models are listed." else null) } }
   catch(e:CancellationException) { throw e }
   catch(_:Exception) { mutable.update { it.copy(error="Could not refresh the free-model catalog. Check your internet connection.") } }
   finally { mutable.update { it.copy(busy=false) } }
  }
 }
 fun send(text:String) {
  if(state.value.busy || text.isBlank()) return
  if(text.length>12000) { mutable.update { it.copy(error="Please keep each message under 12,000 characters.") };return }
  job=viewModelScope.launch {
   mutable.update { it.copy(busy=true,error=null) }
   try {
    // Recheck prices for every request. Never fall back to a paid or stale catalog entry.
    val models=client.models().filter { it.protocol!="unsupported" }
    mutable.update { it.copy(models=models) }
    val selected=state.value.selected
    val candidates=if(selected.isBlank()) models else models.filter { it.id==selected }
    require(candidates.isNotEmpty()) { "The selected model is no longer listed as free. Refresh and choose another." }
    val history=state.value.messages.takeLast(10)+ChatMessage("user",text.trim())
    var answer:String?=null
    var used:String?=null
    for(model in candidates.take(if(selected.isBlank()) 3 else 1)) {
     try { answer=client.reply(model,history,keys.read());used=model.name;break }
     catch(e:OpenCodeFailure) { if(!FreeModelCatalog.mayFallback(e.status)) throw e }
    }
    check(answer!=null) { "The free models tried are unavailable. Choose another model or try again later." }
    mutable.update { it.copy(messages=history+ChatMessage("assistant",answer!!),lastModel=used) }
   } catch(e:CancellationException) { mutable.update { it.copy(error="Request stopped.") };throw e }
   catch(e:Exception) { mutable.update { it.copy(error=when(e) {
    is OpenCodeFailure,is IllegalArgumentException,is IllegalStateException -> e.message
    else -> "Could not reach OpenCode. Check your internet connection and try again."
   }) } }
   finally { mutable.update { it.copy(busy=false) } }
  }
 }
}
