package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TextToSpeechHelper(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _speakingMessageId = MutableStateFlow<Long?>(null)
    val speakingMessageId: StateFlow<Long?> = _speakingMessageId.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isInitialized = true
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _speakingMessageId.value = utteranceId?.toLongOrNull()
            }

            override fun onDone(utteranceId: String?) {
                _speakingMessageId.value = null
            }

            override fun onError(utteranceId: String?) {
                _speakingMessageId.value = null
            }
        })
    }

    fun speak(messageId: Long, text: String) {
        if (!isInitialized) return

        if (_speakingMessageId.value == messageId) {
            stop()
            return
        }

        stop()
        // Strip markdown fences/tags for cleaner audio speech
        val cleanedText = text
            .replace(Regex("```[a-zA-Z]*\\n[\\s\\S]*?```"), "Code snippet omitted.")
            .replace(Regex("[#*`_>~-]"), "")
            .trim()

        _speakingMessageId.value = messageId
        tts?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, messageId.toString())
    }

    fun stop() {
        tts?.stop()
        _speakingMessageId.value = null
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
