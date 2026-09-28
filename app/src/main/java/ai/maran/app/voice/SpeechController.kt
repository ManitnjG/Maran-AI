package ai.maran.app.voice
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.result.ActivityResultLauncher
import java.util.Locale

object SpeechController {
 fun intent(language:String?=null)=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
  putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
  putExtra(RecognizerIntent.EXTRA_LANGUAGE,language ?: Locale.getDefault().toLanguageTag())
  putExtra(RecognizerIntent.EXTRA_PROMPT,"Speak to MARAN")
 }
 fun result(data:Intent?):String?=data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
}
