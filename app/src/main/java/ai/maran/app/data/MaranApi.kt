package ai.maran.app.data
import ai.maran.app.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

data class MissionCreate(val objective:String,val workspace_id:String="default")
data class PlanStep(val id:String,val title:String,val agent:String,val requires_approval:Boolean=false,val status:String="pending")
data class MissionResult(val summary:String?=null,val completed_steps:List<String> = emptyList(),val failed_steps:List<String> = emptyList(),val note:String?=null)
data class RemoteMission(val id:String,val objective:String,val status:String,val verification:String,val assigned_agents:List<String>,val plan:List<PlanStep>,val result:MissionResult?=null)
data class ApprovalDecision(val approved:Boolean,val note:String?=null)
data class WorkerCreate(val name:String,val skills:List<String>,val temporary:Boolean=true)
data class WorkerDto(val id:String,val name:String,val skills:List<String>,val permissions:List<String>)
data class VoiceCommand(val text:String,val confidence:Double=1.0)
data class VoiceResult(val action:String,val objective:String?=null,val name:String?=null,val skills:List<String>?=null,val worker:WorkerDto?=null,val heard:String?=null,val reason:String?=null)

interface MaranApi {
 @POST("missions") suspend fun createMission(@Body request:MissionCreate):RemoteMission
 @GET("missions") suspend fun missions():List<RemoteMission>
 @POST("missions/{id}/run") suspend fun runMission(@Path("id") id:String):RemoteMission
 @POST("missions/{id}/approval") suspend fun approve(@Path("id") id:String,@Body decision:ApprovalDecision):RemoteMission
 @POST("workers") suspend fun createWorker(@Body request:WorkerCreate):WorkerDto
 @GET("workers") suspend fun workers():List<WorkerDto>
 @DELETE("workers/{id}") suspend fun stopWorker(@Path("id") id:String):Map<String,Any>
 @POST("voice/interpret") suspend fun voice(@Body request:VoiceCommand):VoiceResult
}
object ApiProvider {
 val api:MaranApi by lazy {
  Retrofit.Builder().baseUrl(BuildConfig.MARAN_API_BASE_URL).addConverterFactory(GsonConverterFactory.create()).build().create(MaranApi::class.java)
 }
}
