package com.lingualens.app.data

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("tolk_prefs", Context.MODE_PRIVATE)

    /** "sentence" or "paragraph" */
    var readerMode: String
        get() = sp.getString(KEY_MODE, "sentence") ?: "sentence"
        set(v) = sp.edit().putString(KEY_MODE, v).apply()

    /** Hide the English until tapped, for reading practice. */
    var hideEnglish: Boolean
        get() = sp.getBoolean(KEY_HIDE, false)
        set(v) = sp.edit().putBoolean(KEY_HIDE, v).apply()

    var autoTranslateOnLoad: Boolean
        get() = sp.getBoolean(KEY_AUTO, true)
        set(v) = sp.edit().putBoolean(KEY_AUTO, v).apply()

    var ankiDeck: String
        get() = sp.getString(KEY_DECK, "Dutch::LinguaLens") ?: "Dutch::LinguaLens"
        set(v) = sp.edit().putString(KEY_DECK, v).apply()

    var ankiModel: String
        get() = sp.getString(KEY_MODEL, "Basic") ?: "Basic"
        set(v) = sp.edit().putString(KEY_MODEL, v).apply()

    var autoPushAnki: Boolean
        get() = sp.getBoolean(KEY_AUTO_ANKI, false)
        set(v) = sp.edit().putBoolean(KEY_AUTO_ANKI, v).apply()

    var lastUrl: String
        get() = sp.getString(KEY_LAST_URL, "") ?: ""
        set(v) = sp.edit().putString(KEY_LAST_URL, v).apply()

    companion object {
        private const val KEY_MODE = "reader_mode"
        private const val KEY_HIDE = "hide_english"
        private const val KEY_AUTO = "auto_translate"
        private const val KEY_DECK = "anki_deck"
        private const val KEY_MODEL = "anki_model"
        private const val KEY_AUTO_ANKI = "auto_push_anki"
        private const val KEY_LAST_URL = "last_url"
    }
}
