package ai.maran.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.maran.app.data.AiMessage
import ai.maran.app.data.OPENROUTER_NEMOTRON_FREE
import ai.maran.app.data.OpenRouterClient
import ai.maran.app.data.OpenRouterFailure
import ai.maran.app.security.SecureTokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AiChatState(
    val messages:List<AiMessage> = emptyList(),
    val busy:Boolean = false,
    val error:String? = null,
    val keySaved:Boolean = false,
    val lastModel:String? = null
)

class AiChatViewModel(application:Application):AndroidViewModel(application) {
    private val client = OpenRouterClient()
    private val keys = SecureTokenStore(application, "openrouter")
    private val mutable = MutableStateFlow(AiChatState(keySaved = keys.read().isNotBlank()))
    val state = mutable.asStateFlow()
    private var job:Job? = null

    fun saveKey(value:String):Boolean {
        val key=value.trim()
        if(key.isBlank()) return false
        if(!key.startsWith("sk-or-")) {
            mutable.update { it.copy(error="That does not look like an OpenRouter API key.") }
            return false
        }
        return try {
            keys.save(key)
            check(keys.read()==key)
            mutable.update { it.copy(keySaved=true,error=null) }
            true
        } catch(_:Exception) {
            mutable.update { it.copy(error="Could not save the key securely.") }
            false
        }
    }

    fun removeKey() {
        try {
            keys.save("")
            mutable.update { it.copy(keySaved=false,error=null,messages=emptyList(),lastModel=null) }
        } catch(_:Exception) {
            mutable.update { it.copy(error="Could not remove the saved key.") }
        }
    }

    fun clear() {
        if(!state.value.busy) mutable.update { it.copy(messages=emptyList(),error=null,lastModel=null) }
    }

    fun stop() { job?.cancel() }

    fun send(text:String) {
        if(state.value.busy || text.isBlank()) return
        if(text.length > 12000) {
            mutable.update { it.copy(error="Please keep each message under 12,000 characters.") }
            return
        }
        val key=keys.read()
        if(key.isBlank()) {
            mutable.update { it.copy(error="Save your OpenRouter API key once before chatting.",keySaved=false) }
            return
        }
        job=viewModelScope.launch {
            mutable.update { it.copy(busy=true,error=null) }
            try {
                val history=(state.value.messages.takeLast(10)+AiMessage("user",text.trim()))
                val answer=client.chat(key,history)
                mutable.update {
                    it.copy(
                        messages=history+AiMessage("assistant",answer),
                        lastModel=OPENROUTER_NEMOTRON_FREE
                    )
                }
            } catch(e:CancellationException) {
                mutable.update { it.copy(error="Request stopped.") }
                throw e
            } catch(e:OpenRouterFailure) {
                if(e.status==401) mutable.update { it.copy(keySaved=false,error=e.message) }
                else mutable.update { it.copy(error=e.message) }
            } catch(e:java.net.SocketTimeoutException) {
                mutable.update { it.copy(error="The free AI provider is busy and timed out after retrying. Please tap Send again in a moment.") }
            } catch(e:Exception) {
                val message = e.message.orEmpty()
                val friendly = if (message.contains("timeout", ignoreCase=true) || message.contains("timed out", ignoreCase=true))
                    "The free AI provider is busy and timed out after retrying. Please tap Send again in a moment."
                else message.ifBlank { "Could not reach OpenRouter. Check your internet connection and try again." }
                mutable.update { it.copy(error=friendly) }
            } finally {
                mutable.update { it.copy(busy=false) }
            }
        }
    }
}
