package ai.maran.app.data

import android.content.Context
import ai.maran.app.security.SecureTokenStore
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import java.util.UUID

/** Device-owned workspace. Only AI text generation uses the network; no external actions. */
class DeviceTaskApi(context:Context,private val scope:CoroutineScope):MaranApi {
    private val prefs=context.getSharedPreferences("device_workspace",Context.MODE_PRIVATE)
    private val gson=Gson()
    private val keys=SecureTokenStore(context,"openrouter")
    private val client=OpenRouterClient()
    private val jobs=mutableMapOf<String,Job>()
    private var tasks=read("missions",Array<RemoteMission>::class.java)?.toList().orEmpty().map(DeviceTaskPolicy::recover)
    private var team=read("workers",Array<WorkerDto>::class.java)?.toList() ?: listOf(
        WorkerDto("device-research","Kavin",listOf("research","tour leads"),listOf("ai_draft")),
        WorkerDto("device-content","Nila",listOf("content","social","marketing"),listOf("ai_draft")),
        WorkerDto("device-code","Arjun",listOf("code","automation"),listOf("ai_draft")),
        WorkerDto("device-docs","Meera",listOf("document","report","writing"),listOf("ai_draft"))
    )
    init { persist() }
    private fun <T> read(key:String,type:Class<T>):T? = prefs.getString(key,null)?.let { gson.fromJson(it,type) }
    private fun persist(){prefs.edit().putString("missions",gson.toJson(tasks)).putString("workers",gson.toJson(team)).apply()}
    private fun task(id:String)=tasks.firstOrNull{it.id==id} ?: error("Task not found on this device.")
    private fun replace(m:RemoteMission){tasks=tasks.map{if(it.id==m.id)m else it};persist()}
    private fun unsupported():Nothing = error("This action needs a connected task server. Device mode supports AI drafts only.")
    override suspend fun missions()=tasks
    override suspend fun workers()=team
    override suspend fun health()=Health(true,"device-workspace")
    override suspend fun capabilities()=Capabilities(listOf(OPENROUTER_FREE_ROUTER),"device",mapOf("ai_drafts" to "available"),note="Drafts only. No live browsing, posting, filing, or background execution after the app closes.")
    override suspend fun createWorker(request:WorkerCreate):WorkerDto {
        require(request.name.isNotBlank()&&request.skills.any{it.isNotBlank()}){"Enter a worker name and at least one skill."}
        val worker=WorkerDto(UUID.randomUUID().toString(),request.name.trim(),request.skills.map{it.trim()}.filter{it.isNotEmpty()},listOf("ai_draft"))
        team=team+worker;persist();return worker
    }
    override suspend fun stopWorker(id:String):Map<String,Any> {
        val name=team.firstOrNull{it.id==id}?.name
        tasks.filter{it.status=="running"&&name in it.assigned_agents}.forEach{stopMission(it.id,StopRequest())}
        team=team.filterNot{it.id==id};persist();return mapOf("stopped" to true)
    }
    override suspend fun createMission(request:MissionCreate):RemoteMission {
        require(request.objective.isNotBlank()){"Describe your task."}
        require(request.objective.length<=12000){"Keep the task under 12,000 characters."}
        val worker=DeviceTaskPolicy.chooseWorker(request.objective,team)?.name?:"MARAN"
        val m=RemoteMission(UUID.randomUUID().toString(),request.objective.trim(),"queued","AI draft • not independently verified",listOf(worker),
            listOf(PlanStep("draft","Prepare AI draft",worker)))
        tasks=listOf(m)+tasks;persist();return m
    }
    override suspend fun runMission(id:String):RemoteMission {
        if(jobs[id]?.isActive==true)return task(id)
        val key=keys.read()
        if(key.isBlank()) {
            replace(task(id).copy(status="blocked",plan=task(id).plan.map{it.copy(status="blocked",error="Save your OpenRouter key in Profile & settings, then retry.")}))
            return task(id)
        }
        replace(task(id).copy(status="running",result=null,plan=task(id).plan.map{it.copy(status="running",error=null,output=null)}))
        val job=scope.launch(start=CoroutineStart.LAZY) {
            try {
                val m=task(id)
                val response=client.chat(key,listOf(AiMessage("system",
                    "You are ${m.assigned_agents.joinToString()} in MARAN device mode. Produce a useful draft for the user's task. You have NO browsing, files, email, posting, government filing, or external tools. Never claim you performed an action or verified live data. Never invent business contacts or sources. If the request needs current information, provide a research plan and clearly state that live verification is required. Label the result as an AI draft."),AiMessage("user",m.objective)))
                ensureActive()
                if(task(id).status=="running")replace(task(id).copy(status="completed",plan=task(id).plan.map{it.copy(status="completed",output=response)},
                    result=MissionResult(summary="AI draft ready",note="Review before use. No external actions were performed; live facts are not verified.")))
            } catch(e:CancellationException){throw e}
            catch(e:Exception){if(task(id).status=="running")replace(task(id).copy(status="failed",plan=task(id).plan.map{it.copy(status="failed",error=e.message?:"AI request failed. Retry when connected.")}))}
            finally{if(jobs[id]===coroutineContext[Job])jobs.remove(id)}
        }
        jobs[id]=job;job.start();return task(id)
    }
    override suspend fun stopMission(id:String,request:StopRequest):RemoteMission {
        jobs.remove(id)?.cancel()
        val m=task(id).copy(status="cancelled",plan=task(id).plan.map{if(it.status!="completed")it.copy(status="cancelled") else it})
        replace(m);return m
    }
    override suspend fun approve(id:String,decision:ApprovalDecision):RemoteMission = unsupported()
    override suspend fun voice(request:VoiceCommand):VoiceResult = VoiceResult("mission",objective=request.text,heard=request.text)
    override suspend fun export(id:String):ExportResult {
        val m=task(id)
        return ExportResult("maran-${m.id}.txt",listOf(m.objective,m.verification,m.plan.joinToString("\n\n"){it.output?:it.error?:it.status}).joinToString("\n\n"))
    }
    override suspend fun diagnostics()=JsonObject().apply{add("workspace",JsonObject().apply{addProperty("message","Device workspace ready. AI drafts require internet and your OpenRouter key.")})}
    override suspend fun backup()=JsonObject().apply{add("missions",gson.toJsonTree(tasks));add("workers",gson.toJsonTree(team))}
    override suspend fun restore(data:JsonObject):JsonObject = unsupported()
    override suspend fun voucher(request:SalesVoucherRequest):ExportResult = unsupported()
}
