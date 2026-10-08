package ai.maran.app.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * SpeechRecognizer wrapper that keeps the listening UI inside MARAN.
 *
 * Android may still show its own microphone/privacy indicators. Those are
 * platform privacy surfaces and must not be hidden.
 */
class MaranSpeechRecognizer(private val context: Context) : RecognitionListener {
    val onDevice: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    private val recognizer: SpeechRecognizer =
        if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

    private var partialCallback: (String) -> Unit = {}
    private var finalCallback: (String) -> Unit = {}
    private var errorCallback: (String) -> Unit = {}
    private var listeningCallback: (Boolean) -> Unit = {}
    private var destroyed = false

    init {
        recognizer.setRecognitionListener(this)
    }

    fun start(
        language: String?,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
        onListening: (Boolean) -> Unit
    ) {
        if (destroyed) {
            onError("Voice recognizer is unavailable. Reopen MARAN and try again.")
            return
        }
        partialCallback = onPartial
        finalCallback = onFinal
        errorCallback = onError
        listeningCallback = onListening

        val tag = language ?: Locale.getDefault().toLanguageTag()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            if (onDevice) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        try {
            recognizer.cancel()
            recognizer.startListening(intent)
            listeningCallback(true)
        } catch (e: SecurityException) {
            listeningCallback(false)
            errorCallback("Microphone permission is required for voice recognition.")
        } catch (e: Exception) {
            listeningCallback(false)
            errorCallback("Could not start MARAN voice recognition: " + (e.message ?: "unknown error"))
        }
    }

    fun stop() {
        if (!destroyed) {
            try { recognizer.stopListening() } catch (_: Exception) {}
        }
    }

    fun cancel() {
        if (!destroyed) {
            try { recognizer.cancel() } catch (_: Exception) {}
        }
        listeningCallback(false)
    }

    fun close() {
        if (destroyed) return
        destroyed = true
        try { recognizer.cancel() } catch (_: Exception) {}
        try { recognizer.destroy() } catch (_: Exception) {}
    }

    override fun onReadyForSpeech(params: Bundle?) = listeningCallback(true)
    override fun onBeginningOfSpeech() = listeningCallback(true)
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit

    override fun onError(error: Int) {
        listeningCallback(false)
        errorCallback(
            when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error. Please try again."
                SpeechRecognizer.ERROR_CLIENT -> "Voice recognition stopped."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    if (onDevice) "On-device voice recognition failed. Please try again."
                    else "Speech service network error. Please try again."
                SpeechRecognizer.ERROR_NO_MATCH -> "I could not understand that. Please try again."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognizer is busy. Please try again in a moment."
                SpeechRecognizer.ERROR_SERVER -> "Speech service is temporarily unavailable."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I did not hear anything. Please try again."
                else -> "Voice recognition error ($error). Please try again."
            }
        )
    }

    override fun onResults(results: Bundle?) {
        listeningCallback(false)
        val heard = bestResult(results)
        if (heard.isNullOrBlank()) errorCallback("I could not hear any words. Please try again.")
        else finalCallback(heard)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        bestResult(partialResults)?.let(partialCallback)
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun bestResult(bundle: Bundle?): String? {
        val values = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        if (values.isEmpty()) return null
        val scores = bundle?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        if (scores != null && scores.size == values.size) {
            val best = scores.indices.maxByOrNull { scores[it] }
            if (best != null && scores[best] >= 0f) {
                return values[best].trim().takeIf { it.isNotBlank() }
            }
        }
        return values.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }
}
