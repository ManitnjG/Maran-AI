package ai.maran.app.data

import android.content.Context
import androidx.work.*
import ai.maran.app.BuildConfig
import ai.maran.app.security.SecureTokenStore
import java.util.concurrent.TimeUnit

class MaranAutonomyWorker(
    appContext: Context,
    params: WorkerParameters
): CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val prefs=applicationContext.getSharedPreferences("connection",Context.MODE_PRIVATE)
        val url=prefs.getString("url",BuildConfig.MARAN_API_BASE_URL) ?: BuildConfig.MARAN_API_BASE_URL
        val token=SecureTokenStore(applicationContext).read()
        val api=ApiProvider.create(url,token)
        return try {
            val missionId=inputData.getString("mission_id")
            val completed=mutableListOf<RemoteMission>()
            if(!missionId.isNullOrBlank()) {
                completed += api.autonomousRun(missionId)
            } else {
                api.missions()
                    .filter { it.autonomy_enabled && it.status in setOf("planning","running","blocked","verifying") }
                    .take(3)
                    .forEach { completed += api.autonomousRun(it.id) }
            }
            val seen=applicationContext.getSharedPreferences("mission_notifications",Context.MODE_PRIVATE)
            completed.forEach { mission ->
                val old=seen.getString(mission.id,null)
                if(old!=mission.status && mission.status in setOf("waiting_approval","completed","blocked","failed")) {
                    MaranNotifier.mission(applicationContext,mission)
                    seen.edit().putString(mission.id,mission.status).apply()
                }
            }
            Result.success()
        } catch(e: retrofit2.HttpException) {
            if(e.code()==401 || e.code()==403) Result.failure()
            else if(runAttemptCount<3) Result.retry() else Result.failure()
        } catch(_: Exception) {
            if(runAttemptCount<3) Result.retry() else Result.failure()
        }
    }
}

object MaranAutonomyWork {
    private fun constraints()=Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun enqueue(context:Context,missionId:String) {
        val work=OneTimeWorkRequestBuilder<MaranAutonomyWorker>()
            .setInputData(workDataOf("mission_id" to missionId))
            .setConstraints(constraints())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "maran-mission-$missionId",
            ExistingWorkPolicy.KEEP,
            work
        )
    }

    fun schedule(context:Context) {
        val work=PeriodicWorkRequestBuilder<MaranAutonomyWorker>(15,TimeUnit.MINUTES)
            .setConstraints(constraints())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "maran-autonomy-sync",
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }
}
