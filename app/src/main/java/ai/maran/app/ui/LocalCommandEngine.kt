package ai.maran.app.ui

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.AlarmClock

/** Deterministic, offline, validated device commands. No LLM has direct Android access. */
internal data class LocalAction(val toolId:String, val value:Int=0)
internal enum class ActionStatus { SUCCESS, FAILED, PERMISSION_REQUIRED, CONFIRMATION_REQUIRED, NOT_SUPPORTED, TIMEOUT }
internal data class ActionResult(val status:ActionStatus, val message:String)

internal object LocalCommandEngine {
    fun parse(raw:String):LocalAction? {
        val text=raw.trim().replace(Regex("""^(?i:hey\s+)?(?i:maran)[,\s:!.]*"""),"").trim()
        if(Regex("""(?i)^(?:turn\s+)?(?:on|enable)\s+(?:the\s+)?(?:flashlight|torch)$""").matches(text) ||
            Regex("""(?i)^(?:flashlight|torch)\s+on$""").matches(text)) return LocalAction("torch.on")
        if(Regex("""(?i)^(?:turn\s+)?(?:off|disable)\s+(?:the\s+)?(?:flashlight|torch)$""").matches(text) ||
            Regex("""(?i)^(?:flashlight|torch)\s+off$""").matches(text)) return LocalAction("torch.off")
        val volume=Regex("""(?i)^(?:set\s+)?(?:media\s+)?volume\s+(?:to\s+)?(\d{1,3})\s*%?$""").matchEntire(text)
        if(volume!=null) return LocalAction("volume.set",volume.groupValues[1].toInt())
        val timer=Regex("""(?i)^set\s+(?:a\s+)?timer\s+(?:for\s+)?(\d{1,3})\s*(seconds?|minutes?|hours?)$""").matchEntire(text)
        if(timer!=null) {
            val n=timer.groupValues[1].toInt()
            val unit=timer.groupValues[2].lowercase()
            val seconds=n*when { unit.startsWith("hour")->3600;unit.startsWith("minute")->60;else->1 }
            return LocalAction("timer.set",seconds)
        }
        return null
    }
}

internal class AndroidToolRegistry(private val context:Context) {
    fun execute(action:LocalAction):ActionResult {
        return try {
            when(action.toolId) {
                "torch.on","torch.off" -> {
                    val service=context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val camera=service.cameraIdList.firstOrNull {
                        service.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE)==true
                    } ?: return ActionResult(ActionStatus.NOT_SUPPORTED,"This phone does not expose a flashlight.")
                    val enabled=action.toolId=="torch.on"
                    service.setTorchMode(camera,enabled)
                    ActionResult(ActionStatus.SUCCESS,if(enabled) "Flashlight on." else "Flashlight off.")
                }
                "volume.set" -> {
                    if(action.value !in 0..100) return ActionResult(ActionStatus.FAILED,"Choose a volume between 0 and 100 percent.")
                    val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val target=(max*action.value/100f).toInt().coerceIn(0,max)
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC,target,0)
                    val actual=audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                    ActionResult(if(actual==target) ActionStatus.SUCCESS else ActionStatus.FAILED,
                        if(actual==target) "Media volume set to about "+(actual*100/max.coerceAtLeast(1))+" percent."
                        else "Android did not apply the requested volume.")
                }
                "timer.set" -> {
                    if(action.value !in 1..86400) return ActionResult(ActionStatus.FAILED,"Timer must be between one second and 24 hours.")
                    val intent=Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH,action.value)
                        putExtra(AlarmClock.EXTRA_MESSAGE,"MARAN timer")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if(intent.resolveActivity(context.packageManager)==null)
                        return ActionResult(ActionStatus.NOT_SUPPORTED,"No timer app supports this action.")
                    context.startActivity(intent)
                    ActionResult(ActionStatus.CONFIRMATION_REQUIRED,"Opened your timer app. Confirm its timer if prompted.")
                }
                else -> ActionResult(ActionStatus.NOT_SUPPORTED,"That local action is not supported.")
            }
        } catch(e:SecurityException) {
            ActionResult(ActionStatus.PERMISSION_REQUIRED,"Android denied access to this device function.")
        } catch(e:Exception) {
            ActionResult(ActionStatus.FAILED,"Device action failed: "+(e.message ?: "unavailable"))
        }
    }
}
