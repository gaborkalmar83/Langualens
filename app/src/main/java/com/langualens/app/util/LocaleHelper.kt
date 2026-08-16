package com.langualens.app.util

import android.content.Context
import android.content.res.Configuration
import com.langualens.app.data.Prefs
import java.util.Locale

/**
 * Applies the interface language the user picked in Settings, independently of
 * the phone's own language. An empty preference means "follow the system".
 */
object LocaleHelper {

    fun wrap(base: Context): Context {
        val tag = try {
            Prefs(base).uiLanguage
        } catch (t: Throwable) {
            ""
        }
        if (tag.isBlank()) return base

        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
