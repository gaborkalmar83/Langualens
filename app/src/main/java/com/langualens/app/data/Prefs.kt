package com.langualens.app.data

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("langualens_prefs", Context.MODE_PRIVATE)

    /** "paragraph" (default, translates a whole block) or "sentence". */
    var readerMode: String
        get() = sp.getString(KEY_MODE, "paragraph") ?: "paragraph"
        set(v) = sp.edit().putString(KEY_MODE, v).apply()

    /** Hide the translation until tapped, for reading practice. */
    var hideTranslation: Boolean
        get() = sp.getBoolean(KEY_HIDE, false)
        set(v) = sp.edit().putBoolean(KEY_HIDE, v).apply()

    var autoTranslateOnLoad: Boolean
        get() = sp.getBoolean(KEY_AUTO, true)
        set(v) = sp.edit().putBoolean(KEY_AUTO, v).apply()

    /** Language you are reading. */
    var sourceLanguage: String
        get() = sp.getString(KEY_SOURCE, "nl") ?: "nl"
        set(v) = sp.edit().putString(KEY_SOURCE, v).apply()

    /** Language you understand, shown underneath. */
    var targetLanguage: String
        get() = sp.getString(KEY_TARGET, "en") ?: "en"
        set(v) = sp.edit().putString(KEY_TARGET, v).apply()

    /** BCP-47 tag for the app interface, empty means follow the system. */
    var uiLanguage: String
        get() = sp.getString(KEY_UI, "") ?: ""
        set(v) = sp.edit().putString(KEY_UI, v).apply()

    var ankiDeck: String
        get() = sp.getString(KEY_DECK, "LanguaLens") ?: "LanguaLens"
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
        private const val KEY_HIDE = "hide_translation"
        private const val KEY_AUTO = "auto_translate"
        private const val KEY_SOURCE = "source_language"
        private const val KEY_TARGET = "target_language"
        private const val KEY_UI = "ui_language"
        private const val KEY_DECK = "anki_deck"
        private const val KEY_MODEL = "anki_model"
        private const val KEY_AUTO_ANKI = "auto_push_anki"
        private const val KEY_LAST_URL = "last_url"
    }
}
