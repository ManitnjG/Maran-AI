package ai.maran.app.data

import com.google.gson.*
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class FreeModel(val id:String,val name:String,val protocol:String)
data class ChatMessage(val role:String,val content:String)
class OpenCodeFailure(val status:Int):IOException(when(status){
 401,403 -> "OpenCode denied access. Its free tier may require the official OpenCode app or an authorized Zen key. MARAN cannot bypass this restriction."
 402 -> "OpenCode requires billing for this request. No paid model was selected."
 429 -> "OpenCode's rate limit was reached. Wait before trying again."
 else -> "OpenCode request failed (HTTP $status)."
})

object FreeModelCatalog {
 fun parse(json:String):List<FreeModel> {
  val provider=JsonParser.parseString(json).asJsonObject.getAsJsonObject("opencode")
  return provider.getAsJsonObject("models").entrySet().mapNotNull { (id,entry) ->
   val m=entry.asJsonObject
   val cost=m.getAsJsonObject("cost") ?: return@mapNotNull null
   if(m.get("status")?.asString=="deprecated" || !cost.has("input") || !cost.has("output") ||
      cost.entrySet().any { !it.value.isJsonPrimitive || !it.value.asJsonPrimitive.isNumber || it.value.asDouble!=0.0 })
       return@mapNotNull null
   val npm=m.getAsJsonObject("provider")?.get("npm")?.asString ?: provider.get("npm").asString
   val protocol=when(npm) {
    "@ai-sdk/openai-compatible" -> "chat/completions"
    "@ai-sdk/openai" -> "responses"
    "@ai-sdk/anthropic" -> "messages"
    else -> "unsupported"
   }
   FreeModel(id,m.get("name")?.asString ?: id,protocol)
  }.sortedBy { it.name }
 }
 fun mayFallback(status:Int)=status==404 || status==502 || status==503 || status==504
}

class OpenCodeClient {
 private val client=OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS)
  .callTimeout(120,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
 private suspend fun request(request:Request):String=suspendCancellableCoroutine { continuation ->
  val call=client.newCall(request)
  continuation.invokeOnCancellation { call.cancel() }
  call.enqueue(object:Callback {
   override fun onFailure(call:Call,e:IOException) { if(!continuation.isCancelled) continuation.resumeWithException(e) }
   override fun onResponse(call:Call,response:Response) {
    try {
     val result=response.use {
      if(!it.isSuccessful) throw OpenCodeFailure(it.code())
      val source=it.body()?.source() ?: throw IOException("Empty provider response")
      source.request(8L*1024*1024+1)
      if(source.buffer().size()>8L*1024*1024) throw IOException("Provider response too large")
      source.readUtf8()
     }
     if(!continuation.isCancelled) continuation.resume(result)
    } catch(e:Exception) { if(!continuation.isCancelled) continuation.resumeWithException(e) }
   }
  })
 }
 suspend fun models()=FreeModelCatalog.parse(request(Request.Builder().url("https://models.dev/api.json").build()))
 suspend fun reply(model:FreeModel,history:List<ChatMessage>,key:String):String {
  require(model.protocol!="unsupported") { "This model's API format is not supported yet." }
  val payload=JsonObject().apply {
   addProperty("model",model.id)
   addProperty("stream",false)
   val messages=Gson().toJsonTree(history)
   if(model.protocol=="responses") {
    add("input",messages);addProperty("max_output_tokens",4096);addProperty("store",false)
   } else { add("messages",messages);addProperty("max_tokens",4096) }
  }
  val builder=Request.Builder().url("https://opencode.ai/zen/v1/"+model.protocol)
   .post(RequestBody.create(MediaType.parse("application/json"),payload.toString()))
  if(key.isNotBlank()) builder.header("Authorization","Bearer $key")
  if(model.protocol=="messages") {
   builder.header("anthropic-version","2023-06-01")
   if(key.isNotBlank()) builder.header("x-api-key",key)
  }
  return parseReply(model.protocol,request(builder.build())).ifBlank { throw IOException("The model returned no text. Try another free model.") }
 }
 companion object {
  fun parseReply(protocol:String,json:String):String {
   val root=JsonParser.parseString(json).asJsonObject
   return when(protocol) {
    "responses" -> root.getAsJsonArray("output")?.flatMap { item ->
     item.asJsonObject.getAsJsonArray("content")?.mapNotNull { it.asJsonObject.get("text")?.takeUnless { t->t.isJsonNull }?.asString } ?: emptyList()
    }?.joinToString("\n") ?: ""
    "messages" -> root.getAsJsonArray("content")?.mapNotNull { it.asJsonObject.get("text")?.asString }?.joinToString("\n") ?: ""
    else -> root.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject?.getAsJsonObject("message")?.get("content")?.takeUnless { it.isJsonNull }?.asString ?: ""
   }
  }
 }
}
