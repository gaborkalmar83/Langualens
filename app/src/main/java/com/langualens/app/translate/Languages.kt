package com.langualens.app.translate

/** The 59 languages ML Kit can translate fully on device, plus the app's own interface locales. */
object Languages {

    data class Lang(val tag: String, val english: String, val native: String) {
        val label: String get() = if (english == native) english else "$native  ·  $english"
    }

    /** Shown at the top of the picker because they are the ones actually used here. */
    private val PINNED = listOf("nl", "en", "hu", "de", "es", "fr", "mk", "hr", "it", "pl")

    val ALL: List<Lang> = listOf(
        Lang("af", "Afrikaans", "Afrikaans"),
        Lang("ar", "Arabic", "العربية"),
        Lang("be", "Belarusian", "Беларуская"),
        Lang("bg", "Bulgarian", "Български"),
        Lang("bn", "Bengali", "বাংলা"),
        Lang("ca", "Catalan", "Català"),
        Lang("cs", "Czech", "Čeština"),
        Lang("cy", "Welsh", "Cymraeg"),
        Lang("da", "Danish", "Dansk"),
        Lang("de", "German", "Deutsch"),
        Lang("el", "Greek", "Ελληνικά"),
        Lang("en", "English", "English"),
        Lang("eo", "Esperanto", "Esperanto"),
        Lang("es", "Spanish", "Español"),
        Lang("et", "Estonian", "Eesti"),
        Lang("fa", "Persian", "فارسی"),
        Lang("fi", "Finnish", "Suomi"),
        Lang("fr", "French", "Français"),
        Lang("ga", "Irish", "Gaeilge"),
        Lang("gl", "Galician", "Galego"),
        Lang("gu", "Gujarati", "ગુજરાતી"),
        Lang("he", "Hebrew", "עברית"),
        Lang("hi", "Hindi", "हिन्दी"),
        Lang("hr", "Croatian", "Hrvatski"),
        Lang("ht", "Haitian Creole", "Kreyòl ayisyen"),
        Lang("hu", "Hungarian", "Magyar"),
        Lang("id", "Indonesian", "Bahasa Indonesia"),
        Lang("is", "Icelandic", "Íslenska"),
        Lang("it", "Italian", "Italiano"),
        Lang("ja", "Japanese", "日本語"),
        Lang("ka", "Georgian", "ქართული"),
        Lang("kn", "Kannada", "ಕನ್ನಡ"),
        Lang("ko", "Korean", "한국어"),
        Lang("lt", "Lithuanian", "Lietuvių"),
        Lang("lv", "Latvian", "Latviešu"),
        Lang("mk", "Macedonian", "Македонски"),
        Lang("mr", "Marathi", "मराठी"),
        Lang("ms", "Malay", "Bahasa Melayu"),
        Lang("mt", "Maltese", "Malti"),
        Lang("nl", "Dutch", "Nederlands"),
        Lang("no", "Norwegian", "Norsk"),
        Lang("pl", "Polish", "Polski"),
        Lang("pt", "Portuguese", "Português"),
        Lang("ro", "Romanian", "Română"),
        Lang("ru", "Russian", "Русский"),
        Lang("sk", "Slovak", "Slovenčina"),
        Lang("sl", "Slovenian", "Slovenščina"),
        Lang("sq", "Albanian", "Shqip"),
        Lang("sv", "Swedish", "Svenska"),
        Lang("sw", "Swahili", "Kiswahili"),
        Lang("ta", "Tamil", "தமிழ்"),
        Lang("te", "Telugu", "తెలుగు"),
        Lang("th", "Thai", "ไทย"),
        Lang("tl", "Tagalog", "Tagalog"),
        Lang("tr", "Turkish", "Türkçe"),
        Lang("uk", "Ukrainian", "Українська"),
        Lang("ur", "Urdu", "اردو"),
        Lang("vi", "Vietnamese", "Tiếng Việt"),
        Lang("zh", "Chinese", "中文")
    )

    private val byTag: Map<String, Lang> = ALL.associateBy { it.tag }

    /** Pinned entries first, then the rest alphabetically by English name. */
    val ORDERED: List<Lang> by lazy {
        val pinned = PINNED.mapNotNull { byTag[it] }
        val rest = ALL.filter { it.tag !in PINNED }.sortedBy { it.english }
        pinned + rest
    }

    fun find(tag: String): Lang? = byTag[tag]

    fun nameOf(tag: String): String = byTag[tag]?.native ?: tag.uppercase()

    /**
     * Locales the app interface itself is translated into.
     * An empty tag means "follow the system language".
     */
    val INTERFACE: List<Lang> = listOf(
        Lang("", "System default", "System default"),
        Lang("en", "English", "English"),
        Lang("nl", "Dutch", "Nederlands"),
        Lang("hu", "Hungarian", "Magyar"),
        Lang("de", "German", "Deutsch"),
        Lang("es", "Spanish", "Español"),
        Lang("fr", "French", "Français"),
        Lang("mk", "Macedonian", "Македонски"),
        Lang("sr-Latn", "Serbian", "Srpski")
    )

    fun interfaceName(tag: String): String =
        INTERFACE.firstOrNull { it.tag == tag }?.native ?: tag
}
