package ai.maran.app.data

object DeviceTaskPolicy {
    fun defaultDeviceMode(url:String):Boolean = runCatching {
        val host=java.net.URI(url.trim()).host
        host.isNullOrBlank() || host in setOf("10.0.2.2","localhost","127.0.0.1","::1","[::1]")
    }.getOrDefault(true)

    fun chooseWorker(objective:String,workers:List<WorkerDto>):WorkerDto? = workers.maxByOrNull { w ->
        w.skills.flatMap { it.lowercase().split(Regex("[^\\p{L}\\p{N}]+")) }
            .filter { it.length>2 }.distinct().count { objective.lowercase().contains(it) }
    }
    fun recover(m:RemoteMission):RemoteMission = if(m.status=="running") m.copy(
        status="interrupted",verification="Not verified",
        plan=m.plan.map { if(it.status=="running") it.copy(status="interrupted",error="App closed before this step finished. Tap Run / retry.") else it }
    ) else m
}
