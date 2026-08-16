package com.lingualens.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Dutch text-to-speech, shared across the app. */
object Speaker {

    private var tts: TextToSpeech? = null
    private var ready = false

    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("nl", "NL"))
                ready = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    fun speak(context: Context, text: String) {
        init(context)
        val t = text.trim()
        if (t.isEmpty()) return
        tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "tolk")
    }

    fun isReady(): Boolean = ready

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
