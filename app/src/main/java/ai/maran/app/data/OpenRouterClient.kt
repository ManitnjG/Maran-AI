package ai.maran.app.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

const val OPENROUTER_NEMOTRON_FREE = "nvidia/nemotron-3.5-lightning:free"
const val OPENROUTER_FREE_ROUTER = "openrouter/free"

data class AiMessage(val role:String,val content:String)

class OpenRouterFailure(val status:Int, message:String):IOException(message)

class OpenRouterClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(22, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val parsed = runCatching {
                            JsonParser.parseString(body).asJsonObject
                                .getAsJsonObject("error")?.get("message")?.asString
                        }.getOrNull()
                        throw OpenRouterFailure(
                            response.code,
                            parsed ?: when(response.code) {
                                401 -> "OpenRouter key is invalid or missing."
                                402 -> "OpenRouter account requires credits for this request."
                                429 -> "OpenRouter free-model rate limit reached. Please try again later."
                                else -> "OpenRouter request failed (HTTP \${response.code})."
                            }
                        )
                    }
                    if (!continuation.isCancelled) continuation.resume(body)
                } catch (e: Exception) {
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                } finally {
                    response.close()
                }
            }
        })
    }

    private fun request(apiKey:String, model:String, messages:List<AiMessage>, stream:Boolean=false):Request {
        val root = JsonObject().apply {
            addProperty("model", model)
            addProperty("temperature", 0.4)
            addProperty("max_tokens", 512)
            addProperty("stream", stream)
            add("messages", JsonArray().apply {
                messages.forEach { m ->
                    add(JsonObject().apply {
                        addProperty("role", m.role)
                        addProperty("content", m.content)
                    })
                }
            })
        }
        return Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", "https://github.com/ManitnjG/Maran-AI")
            .header("X-Title", "MARAN AI")
            .post(root.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun textFrom(body:String):String {
        val json = JsonParser.parseString(body).asJsonObject
        val choice = json.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
            ?: throw IOException("OpenRouter returned no choices.")
        val content = choice.getAsJsonObject("message")?.get("content")
        if (content == null || content.isJsonNull) throw IOException("OpenRouter returned no text.")
        return if (content.isJsonPrimitive) content.asString else content.toString()
    }

    suspend fun chat(apiKey:String, messages:List<AiMessage>):String {
        require(apiKey.isNotBlank()) { "Enter your OpenRouter API key once." }

        // Prefer Nemotron. If its free provider is overloaded/stalled, automatically
        // fall back to OpenRouter's free-model router instead of making the user retry.
        // Fast path: do not wait a long time for an overloaded free provider.
        // Nemotron gets a short chance; MARAN then switches to the free router.
        val primary = request(apiKey, OPENROUTER_NEMOTRON_FREE, messages)
        try {
            return kotlinx.coroutines.withTimeout(9_000L) { textFrom(execute(primary)) }
        } catch (e: OpenRouterFailure) {
            if (e.status == 401 || e.status == 402) throw e
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            // Provider was too slow: switch immediately.
        } catch (_: IOException) {
            // Provider/network failure: switch immediately.
        }

        return textFrom(execute(request(apiKey, OPENROUTER_FREE_ROUTER, messages)))
    }
    private suspend fun streamFrom(
        apiKey:String,
        model:String,
        messages:List<AiMessage>,
        onDelta:(String)->Unit
    ):String = withContext(Dispatchers.IO) {
        val call = http.newCall(request(apiKey,model,messages,stream=true))
        val cancellation = currentCoroutineContext()[kotlinx.coroutines.Job]?.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            call.execute().use { res ->
                if (!res.isSuccessful) {
                    val body=res.body?.string().orEmpty()
                    val parsed=runCatching {
                        JsonParser.parseString(body).asJsonObject.getAsJsonObject("error")?.get("message")?.asString
                    }.getOrNull()
                    throw OpenRouterFailure(res.code,parsed ?: "OpenRouter request failed (HTTP ${res.code}).")
                }
                val source=res.body?.source() ?: throw IOException("OpenRouter returned an empty stream.")
                val answer=StringBuilder()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line=source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data=line.removePrefix("data:").trim()
                    if (data=="[DONE]") break
                    val delta=runCatching {
                        JsonParser.parseString(data).asJsonObject
                            .getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                            ?.getAsJsonObject("delta")?.get("content")
                            ?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                    }.getOrDefault("")
                    if (delta.isNotEmpty()) {
                        answer.append(delta)
                        withContext(Dispatchers.Main.immediate) { onDelta(delta) }
                    }
                }
                if (answer.isEmpty()) throw IOException("OpenRouter returned no streamed text.")
                answer.toString()
            }
        } finally {
            cancellation?.dispose()
            call.cancel()
        }
    }

    suspend fun streamChat(
        apiKey:String,
        messages:List<AiMessage>,
        onDelta:(String)->Unit
    ):String {
        require(apiKey.isNotBlank()) { "Enter your OpenRouter API key once." }
        var received=false
        val forward:(String)->Unit = { delta -> received=true; onDelta(delta) }
        // Route quickly to an available free model. Switch once if no text arrives.
        try {
            return withTimeout(12_000L) {
                streamFrom(apiKey, OPENROUTER_FREE_ROUTER, messages, forward)
            }
        } catch (e: OpenRouterFailure) {
            if (received || e.status==401 || e.status==402) throw e
        } catch (e: TimeoutCancellationException) {
            if (received) throw e
        } catch (e: IOException) {
            if (received) throw e
        }
        return streamFrom(apiKey, OPENROUTER_NEMOTRON_FREE, messages, onDelta)
    }

}
