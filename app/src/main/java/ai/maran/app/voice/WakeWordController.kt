package ai.maran.app.voice

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import java.util.Locale

/** Opt-in listening only while the MARAN screen is visible. */
class WakeWordController(context:Context,private val onCommand:(String)->Unit,private val onStatus:(String)->Unit):RecognitionListener{
 private val handler=Handler(Looper.getMainLooper())
 private val recognizer=SpeechRecognizer.createSpeechRecognizer(context)
 private var active=false
 private var awaitingCommand=false
 private var language="en-IN"
 init{recognizer.setRecognitionListener(this)}
 fun start(locale:String){language=locale;active=true;listen()}
 private fun listen(){if(active)try{recognizer.startListening(SpeechController.intent(language));onStatus(if(awaitingCommand)"Say your command" else "Listening for Maran")}catch(_:Exception){stop();onStatus("Speech recognition unavailable")}}
 private fun again(){if(active)handler.postDelayed({listen()},900)}
 fun stop(){active=false;awaitingCommand=false;handler.removeCallbacksAndMessages(null);recognizer.cancel();onStatus("Microphone off")}
 fun destroy(){stop();recognizer.destroy()}
 override fun onResults(results:Bundle?){
  if(!active)return
  val text=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
  val marker=Regex("(?i)(?:\\bmaran\\b|மாறன்|மாரன்)").find(text)
  val command=if(awaitingCommand)text else marker?.let{text.substring(it.range.last+1).trim(' ',':',',','.')} .orEmpty()
  if(command.isNotBlank()){awaitingCommand=false;onCommand(command)}else if(marker!=null){awaitingCommand=true}
  again()
 }
 override fun onError(error:Int){
  if(!active)return
  if(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS||error==SpeechRecognizer.ERROR_RECOGNIZER_BUSY||error==SpeechRecognizer.ERROR_NETWORK||error==SpeechRecognizer.ERROR_NETWORK_TIMEOUT){stop();onStatus("Listening stopped. Check microphone permission and network.")}else again()
 }
 override fun onReadyForSpeech(params:Bundle?){}
 override fun onBeginningOfSpeech(){}
 override fun onRmsChanged(rmsdB:Float){}
 override fun onBufferReceived(buffer:ByteArray?){}
 override fun onEndOfSpeech(){}
 override fun onPartialResults(partialResults:Bundle?){}
 override fun onEvent(eventType:Int,params:Bundle?){}
}
