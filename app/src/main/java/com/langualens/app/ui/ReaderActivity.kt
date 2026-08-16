package com.langualens.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.langualens.app.R
import com.langualens.app.data.Prefs
import com.langualens.app.data.Repo
import com.langualens.app.translate.Translate
import com.langualens.app.util.LocaleHelper
import com.langualens.app.util.Speaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ReaderActivity : ComponentActivity() {

    private lateinit var web: WebView
    private lateinit var prefs: Prefs
    private lateinit var repo: Repo
    private lateinit var titleView: TextView
    private var injected = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        prefs = Prefs(this)
        repo = Repo.get(this)
        Translate.configure(prefs.sourceLanguage, prefs.targetLanguage)
        Speaker.init(this)

        titleView = findViewById(R.id.title)
        web = findViewById(R.id.web)

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = userAgentString.replace("; wv", "")
        }
        web.addJavascriptInterface(Bridge(), "LanguaLens")

        web.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) titleView.text = title
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                injected = false
                if (prefs.autoTranslateOnLoad) injectAndRun()
            }
        }

        setupButtons()
        handleIntent(intent)
    }

    private fun setupButtons() {
        val btnMode = findViewById<Button>(R.id.btnMode)
        val btnVeil = findViewById<Button>(R.id.btnVeil)
        val btnTranslate = findViewById<Button>(R.id.btnTranslate)

        fun refreshLabels() {
            btnMode.text = getString(
                if (prefs.readerMode == "paragraph") R.string.reader_mode_paragraph
                else R.string.reader_mode_sentence
            )
            btnVeil.text = getString(
                if (prefs.hideTranslation) R.string.reader_hidden else R.string.reader_show
            )
            btnTranslate.text = getString(R.string.reader_translate)
        }
        refreshLabels()

        btnMode.setOnClickListener {
            prefs.readerMode = if (prefs.readerMode == "paragraph") "sentence" else "paragraph"
            refreshLabels()
            injected = false
            web.reload()
        }
        btnVeil.setOnClickListener {
            prefs.hideTranslation = !prefs.hideTranslation
            refreshLabels()
            web.evaluateJavascript(
                "if(window.llSetHidden)window.llSetHidden(${prefs.hideTranslation});", null
            )
        }
        btnTranslate.setOnClickListener { injectAndRun() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val url = intent.dataString
        val extraUrl = intent.getStringExtra(EXTRA_URL)
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
        val plain = intent.getStringExtra(EXTRA_TEXT)

        when {
            !extraUrl.isNullOrBlank() -> load(extraUrl)
            !url.isNullOrBlank() -> load(url)
            !plain.isNullOrBlank() -> loadPlainText(plain)
            !shared.isNullOrBlank() -> {
                val found = URL_REGEX.find(shared)?.value
                if (found != null) load(found) else loadPlainText(shared)
            }
            else -> loadPlainText(getString(R.string.reader_placeholder))
        }
    }

    private fun load(raw: String) {
        val url = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
        prefs.lastUrl = url
        web.loadUrl(url)
    }

    private fun loadPlainText(text: String) {
        val escaped = text
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .split(Regex("\n{2,}"))
            .joinToString("") { "<p>" + it.replace("\n", "<br>") + "</p>" }
        val html = """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
              body{margin:0;padding:18px 16px 90px;font-family:Georgia,serif;
                   font-size:18px;line-height:1.65;color:#1c1c1c;background:#fbfaf7;}
              @media (prefers-color-scheme: dark){
                body{background:#101319;color:#e8eaf0;}
              }
              p{margin:0 0 16px;}
            </style></head><body>$escaped</body></html>
        """.trimIndent()
        titleView.text = getString(R.string.text_label)
        web.loadDataWithBaseURL("https://langualens.local/", html, "text/html", "utf-8", null)
    }

    private fun injectAndRun() {
        val js = try {
            assets.open("reader.js").bufferedReader().use { it.readText() }
        } catch (t: Throwable) {
            toast(getString(R.string.reader_missing_script)); return
        }
        val hint = JSONObject.quote(getString(R.string.reader_tap_hint))
        val bootstrap = """
            window.__llMode = '${prefs.readerMode}';
            window.__llHidden = ${prefs.hideTranslation};
            window.__llHint = $hint;
        """.trimIndent()

        if (!injected) {
            web.evaluateJavascript(bootstrap, null)
            web.evaluateJavascript(js, null)
            injected = true
        }
        web.evaluateJavascript("if(window.llRun)window.llRun();", null)
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        web.removeJavascriptInterface("LanguaLens")
        super.onDestroy()
    }

    @Suppress("unused")
    inner class Bridge {

        @JavascriptInterface
        fun requestTranslate(payload: String) {
            lifecycleScope.launch {
                val items = withContext(Dispatchers.Default) { parse(payload) }
                if (items.isEmpty()) return@launch
                val translations = Translate.translateAll(items.map { it.second })
                val obj = JSONObject()
                items.forEachIndexed { i, pair ->
                    val value = translations.getOrNull(i).orEmpty()
                    if (value.isNotBlank()) obj.put(pair.first, value)
                }
                val literal = JSONObject.quote(obj.toString())
                web.evaluateJavascript("window.llApply($literal);", null)
            }
        }

        @JavascriptInterface
        fun save(text: String, context: String, origin: String) {
            lifecycleScope.launch {
                val result = repo.save(text, null, context, origin)
                toast(Repo.message(this@ReaderActivity, result))
            }
        }

        /**
         * Translates the current selection and shows it inside the page as a large
         * transient popup, rather than a toast, so the result is readable at a
         * glance without covering the selection bar.
         */
        @JavascriptInterface
        fun lookup(text: String) {
            lifecycleScope.launch {
                val translated = Translate.translate(text)
                val message =
                    if (translated.isBlank()) getString(R.string.no_translation) else translated
                val literal = JSONObject.quote(message)
                web.evaluateJavascript("if(window.llPopup)window.llPopup($literal);", null)
            }
        }

        @JavascriptInterface
        fun speak(text: String) {
            runOnUiThread {
                Speaker.speak(this@ReaderActivity, text, prefs.sourceLanguage)
            }
        }

        private fun parse(payload: String): List<Pair<String, String>> = try {
            val arr = JSONArray(payload)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                val text = o.optString("text")
                if (id.isBlank() || text.isBlank()) null else id to text
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    companion object {
        const val EXTRA_URL = "langualens_url"
        const val EXTRA_TEXT = "langualens_text"
        private val URL_REGEX = Regex("https?://\\S+")

        fun openUrl(activity: Activity, url: String) {
            activity.startActivity(
                Intent(activity, ReaderActivity::class.java).putExtra(EXTRA_URL, url)
            )
        }

        fun openText(activity: Activity, text: String) {
            activity.startActivity(
                Intent(activity, ReaderActivity::class.java).putExtra(EXTRA_TEXT, text)
            )
        }

        /**
         * Same as [openText] but callable from a Service, which has no task of its
         * own to launch into. Used by the floating panel's "Reader" button.
         */
        fun openTextFrom(context: Context, text: String) {
            context.startActivity(
                Intent(context, ReaderActivity::class.java)
                    .putExtra(EXTRA_TEXT, text)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
            )
        }
    }
}
