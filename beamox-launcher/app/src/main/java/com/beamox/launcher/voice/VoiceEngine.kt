package com.beamox.launcher.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Распознавание речи (Google / on-device) + озвучка ответов (TTS).
 * Все методы вызывать с главного потока.
 */
class VoiceEngine(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var useOnDevice = Build.VERSION.SDK_INT >= 31 &&
        runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx) }.getOrDefault(false)

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    var speaking = false
        private set

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(ctx)

    init {
        tts = TextToSpeech(ctx) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ru", "RU")
                ttsReady = true
            }
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        val t = tts
        if (!ttsReady || t == null || text.isBlank()) { onDone?.invoke(); return }
        val clean = text.replace(Regex("[*#`_>|]"), " ").take(1200)
        speaking = true
        t.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { main.post { speaking = false; onDone?.invoke() } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { main.post { speaking = false; onDone?.invoke() } }
        })
        t.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "beamox-" + System.nanoTime())
    }

    fun stopSpeaking() { tts?.stop(); speaking = false }

    private fun newRecognizer(): SpeechRecognizer {
        recognizer?.destroy()
        val r = if (useOnDevice && Build.VERSION.SDK_INT >= 31)
            SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
        else SpeechRecognizer.createSpeechRecognizer(ctx)
        recognizer = r
        return r
    }

    /**
     * Слушать одну фразу.
     * @param onPartial промежуточный текст (для «живой» подписи)
     * @param onResult итоговый текст или null (тишина/ошибка)
     */
    fun listenOnce(
        onPartial: (String) -> Unit = {},
        onLevel: (Float) -> Unit = {},
        longSilence: Boolean = false,
        onResult: (String?, Int?) -> Unit,
    ) {
        val r = newRecognizer()
        var finished = false
        fun finish(text: String?, err: Int?) {
            if (finished) return
            finished = true
            onResult(text, err)
        }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) = onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                // Нет офлайн-пакета языка → переключаемся на обычное распознавание
                if (useOnDevice && (error == 12 || error == 13 || error == SpeechRecognizer.ERROR_CLIENT)) {
                    useOnDevice = false
                }
                finish(null, error)
            }
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                finish(list?.firstOrNull(), null)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.let(onPartial)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        if (longSilence) {
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        try {
            r.startListening(i)
        } catch (e: Exception) {
            finish(null, SpeechRecognizer.ERROR_CLIENT)
        }
    }

    fun cancelListening() {
        runCatching { recognizer?.cancel() }
    }

    fun release() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        runCatching { tts?.shutdown() }
        tts = null
    }

    companion object {
        /** Ищет слово-активатор; возвращает текст после него ("" — только активатор) или null. */
        fun stripWakeWord(text: String, wakeWords: List<String>): String? {
            val t = text.lowercase(Locale("ru")).replace(",", " ").replace(Regex("\\s+"), " ").trim()
            for (w in wakeWords.sortedByDescending { it.length }) {
                val idx = t.indexOf(w)
                if (idx >= 0) return t.substring(idx + w.length).trim(' ', ',', '.', '!')
            }
            return null
        }
    }
}
