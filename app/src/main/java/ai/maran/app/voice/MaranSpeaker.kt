package ai.maran.app.voice

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Optional spoken feedback. Never reports execution success independently of tool results. */
class MaranSpeaker(context:Context):TextToSpeech.OnInitListener {
    private val tts=TextToSpeech(context.applicationContext,this)
    private val callbacks=ConcurrentHashMap<String,()->Unit>()
    private val ids=AtomicInteger()
    private val main=Handler(Looper.getMainLooper())
    private var ready=false
    private var pending:Triple<String,String,(() -> Unit)?>?=null

    init {
        tts.setOnUtteranceProgressListener(object:UtteranceProgressListener(){
            override fun onStart(utteranceId:String?)=Unit
            override fun onError(utteranceId:String?){finish(utteranceId)}
            override fun onDone(utteranceId:String?){finish(utteranceId)}
            private fun finish(id:String?){
                val cb=id?.let{callbacks.remove(it)} ?: return
                main.post(cb)
            }
        })
    }

    override fun onInit(status:Int) {
        ready=status==TextToSpeech.SUCCESS
        if(ready) {
            tts.language=Locale.forLanguageTag("en-IN")
            pending?.let { (text,locale,done)-> speak(text,locale,done) }
            pending=null
        }
    }

    fun speak(message:String,locale:String="en-IN",onDone:(()->Unit)?=null) {
        val speech=message.trim().take(1200)
        if(speech.isEmpty()){onDone?.invoke();return}
        if(!ready){pending=Triple(speech,locale,onDone);return}
        tts.language=Locale.forLanguageTag(locale)
        val id="maran-reply-"+ids.incrementAndGet()
        if(onDone!=null)callbacks[id]=onDone
        tts.speak(speech,TextToSpeech.QUEUE_FLUSH,Bundle(),id)
    }

    fun stop() {
        pending=null
        callbacks.clear()
        tts.stop()
    }
    fun close(){stop();tts.shutdown()}
}
