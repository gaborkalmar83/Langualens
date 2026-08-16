package com.langualens.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Text to speech in whichever language is currently being read. */
object Speaker {

    private var tts: TextToSpeech? = null
    private var ready = false

    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
        }
    }

    fun speak(context: Context, text: String, languageTag: String) {
        init(context)
        val body = text.trim()
        if (body.isEmpty()) return
        val engine = tts ?: return
        if (!ready) return
        try {
            val locale = Locale.forLanguageTag(languageTag)
            val result = engine.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                engine.setLanguage(Locale.ENGLISH)
            }
        } catch (t: Throwable) {
            // keep whatever voice is already loaded
        }
        engine.speak(body, TextToSpeech.QUEUE_FLUSH, null, "langualens")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
