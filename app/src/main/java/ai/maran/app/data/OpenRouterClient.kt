package ai.maran.app.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
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

data class AiMessage(val role:String,val content:String)

class OpenRouterFailure(val status:Int, message:String):IOException(message)

class OpenRouterClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(210, TimeUnit.SECONDS)
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

    suspend fun chat(apiKey:String, messages:List<AiMessage>):String {
        require(apiKey.isNotBlank()) { "Enter your OpenRouter API key once." }
        val root = JsonObject().apply {
            addProperty("model", OPENROUTER_NEMOTRON_FREE)
            addProperty("temperature", 0.4)
            addProperty("max_tokens", 2048)
            add("messages", JsonArray().apply {
                messages.forEach { m ->
                    add(JsonObject().apply {
                        addProperty("role", m.role)
                        addProperty("content", m.content)
                    })
                }
            })
        }
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", "https://github.com/ManitnjG/Maran-AI")
            .header("X-Title", "MARAN AI")
            .post(root.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val body = try {
            execute(request)
        } catch (e: java.net.SocketTimeoutException) {
            // Free providers can occasionally stall. Retry once before surfacing an error.
            execute(request)
        }
        val json = JsonParser.parseString(body).asJsonObject
        val choice = json.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
            ?: throw IOException("OpenRouter returned no choices.")
        val content = choice.getAsJsonObject("message")?.get("content")
        if (content == null || content.isJsonNull) throw IOException("Nemotron returned no text.")
        return if (content.isJsonPrimitive) content.asString else content.toString()
    }
}
