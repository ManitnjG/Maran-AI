package ai.maran.app.voice

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import java.util.Locale

object SpeechController {
    fun intent(language:String?=null)=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE,language ?: Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,language ?: Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE,false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false)
        putExtra(RecognizerIntent.EXTRA_PROMPT,"Speak to MARAN")
    }

    fun result(data:Intent?):String? {
        val results=data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
        if(results.isEmpty()) return null
        val scores=data?.getFloatArrayExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES)
        if(scores!=null && scores.size==results.size) {
            val best=scores.indices.maxByOrNull { scores[it] }
            if(best!=null && scores[best]>=0f) return results[best].trim().takeIf { it.isNotBlank() }
        }
        return results.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }
}
