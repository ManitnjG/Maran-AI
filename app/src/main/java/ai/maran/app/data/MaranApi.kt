package ai.maran.app.data
import ai.maran.app.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

data class Evidence(val sources:List<String> = emptyList(),val retrieved_at:String?=null)
data class IntegrationCapability(val status:String,val approval:Boolean=false,val note:String?=null)
data class Capabilities(val models:List<String>,val storage:String,val features:Map<String,String>,val integrations:Map<String,IntegrationCapability> = emptyMap(),val note:String)
data class ExportResult(val filename:String,val content:String,val note:String?=null)
data class SalesVoucherRequest(val company:String,val customer_ledger:String,val sales_ledger:String,val voucher_number:String,val voucher_date:String,val amount:String)
data class MissionCreate(val objective:String,val workspace_id:String="default",val autonomous:Boolean=true,val max_cycles:Int=4)
data class PlanStep(
 val id:String,
 val title:String,
 val agent:String,
 val requires_approval:Boolean=false,
 val approval_reason:String?=null,
 val risk_level:String="auto",
 val tool_id:String?=null,
 val status:String="pending",
 val approved:Boolean=false,
 val attempts:Int=0,
 val output:String?=null,
 val provider:String?=null,
 val error:String?=null,
 val evidence:Evidence?=null
)
data class MissionResult(val summary:String?=null,val completed_steps:List<String> = emptyList(),val blocked_steps:List<String> = emptyList(),val failed_steps:List<String> = emptyList(),val note:String?=null)
data class ActionIntentDto(
 val id:String,
 val tool_id:String,
 val title:String,
 val args:Map<String,Any?> = emptyMap(),
 val reason:String?=null,
 val status:String="waiting_approval",
 val connection_status:String="unknown",
 val requires_approval:Boolean=true,
 val approved:Boolean=false,
 val attempts:Int=0,
 val result:Map<String,Any?>?=null,
 val verification:String="pending",
 val error:String?=null
)
data class MissionEvent(
 val type:String,
 val timestamp:String?=null,
 val step_id:String?=null,
 val reason:String?=null,
 val agent:String?=null,
 val provider:String?=null,
 val cycle:Int?=null,
 val outcome:String?=null,
 val skill_id:String?=null,
 val name:String?=null
)
data class RemoteMission(
 val id:String,
 val objective:String,
 val status:String,
 val verification:String,
 val assigned_agents:List<String>,
 val plan:List<PlanStep>,
 val actions:List<ActionIntentDto> = emptyList(),
 val events:List<MissionEvent> = emptyList(),
 val result:MissionResult?=null,
 val autonomy_enabled:Boolean=true,
 val max_cycles:Int=4,
 val cycle:Int=0,
 val learned_skill_id:String?=null
)
data class LearnedSkillDto(
 val id:String,
 val name:String,
 val trigger_terms:List<String> = emptyList(),
 val agents:List<String> = emptyList(),
 val step_titles:List<String> = emptyList(),
 val success_count:Int=0
)
data class MemoryItem(val key:String,val value:String,val updated_at:String?=null)
data class KnowledgeDoc(
 val id:String,
 val name:String,
 val mime_type:String,
 val allow_ai:Boolean=false,
 val characters:Int=0,
 val created_at:String?=null
)
data class MemoryWrite(val key:String,val value:String,val workspace_id:String="default")
data class StopRequest(val reason:String="Stopped by user")
data class ApprovalDecision(val approved:Boolean,val note:String?=null)
data class ActionDecision(val approved:Boolean,val note:String?=null)
data class WorkspaceDeleteRequest(val confirmed:Boolean=true,val workspace_id:String="default")
data class WorkerCreate(val name:String,val skills:List<String>,val temporary:Boolean=true)
data class WorkerDto(val id:String,val name:String,val skills:List<String>,val permissions:List<String>)
data class VoiceCommand(val text:String,val confidence:Double=1.0)
data class Health(val ok:Boolean,val service:String)
data class VoiceResult(val action:String,val objective:String?=null,val name:String?=null,val skills:List<String>?=null,val worker:WorkerDto?=null,val heard:String?=null,val reason:String?=null)

interface MaranApi {
 @GET("capabilities") suspend fun capabilities():Capabilities
 @POST("workspace/delete") suspend fun deleteWorkspace(@Body request:WorkspaceDeleteRequest=WorkspaceDeleteRequest()):Map<String,Any>
 @POST("diagnostics") suspend fun diagnostics():com.google.gson.JsonObject
 @POST("backup/restore") suspend fun restore(@Body data:com.google.gson.JsonObject):com.google.gson.JsonObject
 @GET("backup") suspend fun backup():com.google.gson.JsonObject
 @POST("tools/tally-voucher") suspend fun voucher(@Body request:SalesVoucherRequest):ExportResult
 @GET("missions/{id}/export") suspend fun export(@Path("id") id:String):ExportResult
 @GET("health") suspend fun health():Health
 @POST("missions") suspend fun createMission(@Body request:MissionCreate):RemoteMission
 @GET("missions") suspend fun missions():List<RemoteMission>
 @POST("missions/{id}/run") suspend fun runMission(@Path("id") id:String):RemoteMission
 @POST("missions/{id}/autonomous-run") suspend fun autonomousRun(@Path("id") id:String):RemoteMission
 @POST("missions/{id}/stop") suspend fun stopMission(@Path("id") id:String,@Body request:StopRequest=StopRequest()):RemoteMission
 @POST("missions/{id}/approval") suspend fun approve(@Path("id") id:String,@Body decision:ApprovalDecision):RemoteMission
 @POST("missions/{id}/actions/{actionId}/decision") suspend fun decideAction(@Path("id") id:String,@Path("actionId") actionId:String,@Body decision:ActionDecision):RemoteMission
 @GET("skills") suspend fun skills(@Query("workspace_id") workspaceId:String="default"):List<LearnedSkillDto>
 @GET("memory") suspend fun memory(@Query("workspace_id") workspaceId:String="default"):List<MemoryItem>
 @GET("knowledge") suspend fun knowledge(@Query("workspace_id") workspaceId:String="default"):List<KnowledgeDoc>
 @Multipart
 @POST("knowledge/upload")
 suspend fun uploadKnowledge(
  @Part file:okhttp3.MultipartBody.Part,
  @Part("workspace_id") workspaceId:okhttp3.RequestBody,
  @Part("allow_ai") allowAi:okhttp3.RequestBody
 ):KnowledgeDoc
 @DELETE("knowledge/{documentId}") suspend fun deleteKnowledge(@Path("documentId") documentId:String,@Query("workspace_id") workspaceId:String="default"):Map<String,Any>
 @POST("memory") suspend fun remember(@Body item:MemoryWrite):MemoryItem
 @DELETE("memory/{key}") suspend fun forgetMemory(@Path("key") key:String,@Query("workspace_id") workspaceId:String="default"):Map<String,Any>
 @POST("workers") suspend fun createWorker(@Body request:WorkerCreate):WorkerDto
 @GET("workers") suspend fun workers():List<WorkerDto>
 @DELETE("workers/{id}") suspend fun stopWorker(@Path("id") id:String):Map<String,Any>
 @POST("voice/interpret") suspend fun voice(@Body request:VoiceCommand):VoiceResult
}
object ApiProvider {
 fun create(url:String,token:String):MaranApi {
  val client=okhttp3.OkHttpClient.Builder()
   .readTimeout(5,java.util.concurrent.TimeUnit.MINUTES)
   .addInterceptor { chain ->
    val builder=chain.request().newBuilder()
    if(token.isNotBlank()) builder.header("Authorization","Bearer $token")
    chain.proceed(builder.build())
   }.build()
  return Retrofit.Builder().baseUrl(url.trim().trimEnd('/')+"/").client(client)
   .addConverterFactory(GsonConverterFactory.create()).build().create(MaranApi::class.java)
 }
}
