package ai.maran.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Optional spoken feedback. Never reports execution success independently of tool results. */
class MaranSpeaker(context:Context):TextToSpeech.OnInitListener {
    private val tts=TextToSpeech(context.applicationContext,this)
    private var ready=false
    private var pending:String?=null
    override fun onInit(status:Int) {
        ready=status==TextToSpeech.SUCCESS
        if(ready) {
            tts.language=Locale.forLanguageTag("en-IN")
            pending?.let { speak(it) }
            pending=null
        }
    }
    fun speak(message:String,locale:String="en-IN") {
        val speech=message.trim().take(1200)
        if(speech.isEmpty()) return
        if(!ready) { pending=speech; return }
        tts.language=Locale.forLanguageTag(locale)
        tts.speak(speech,TextToSpeech.QUEUE_FLUSH,null,"maran-reply")
    }
    fun stop() { pending=null;tts.stop() }
    fun close() { stop();tts.shutdown() }
}
