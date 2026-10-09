package ai.maran.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ai.maran.app.MainActivity
import ai.maran.app.R

object MaranNotifier {
    private const val CHANNEL="maran_missions"

    fun enabled(context:Context):Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED

    private fun manager(context:Context):NotificationManager {
        val nm=context.getSystemService(NotificationManager::class.java)
        if(Build.VERSION.SDK_INT>=26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL,"MARAN missions",NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description="Mission completion, failures and approval requests"
                }
            )
        }
        return nm
    }

    fun mission(context:Context,m:RemoteMission) {
        if(!enabled(context)) return
        val approval=m.status=="waiting_approval" || m.actions.any{it.status=="waiting_approval"}
        val title=when {
            approval -> "MARAN needs your approval"
            m.status=="completed" -> "MARAN mission completed"
            m.status in listOf("blocked","failed") -> "MARAN mission needs attention"
            else -> return
        }
        val intent=Intent(context,MainActivity::class.java).apply {
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab",if(approval)"approvals" else "missions")
            putExtra("mission_id",m.id)
        }
        val pending=PendingIntent.getActivity(
            context,m.id.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body=m.objective.take(140)
        val notification=NotificationCompat.Builder(context,CHANNEL)
            .setSmallIcon(R.drawable.maran_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        manager(context).notify(m.id.hashCode(),notification)
    }
}
