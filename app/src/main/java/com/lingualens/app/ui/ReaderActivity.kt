package com.lingualens.app.ui

import android.annotation.SuppressLint
import android.app.Activity
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
import com.lingualens.app.R
import com.lingualens.app.data.Prefs
import com.lingualens.app.data.Repo
import com.lingualens.app.translate.Nl2En
import com.lingualens.app.util.Speaker
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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        prefs = Prefs(this)
        repo = Repo.get(this)
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
        web.addJavascriptInterface(Bridge(), "LinguaLens")

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
            btnMode.text = if (prefs.readerMode == "sentence") "ZIN" else "ALINEA"
            btnVeil.text = if (prefs.hideEnglish) "VERBORGEN" else "TOON"
        }
        refreshLabels()

        btnMode.setOnClickListener {
            prefs.readerMode = if (prefs.readerMode == "sentence") "paragraph" else "sentence"
            refreshLabels()
            web.evaluateJavascript("if(window.tolkClear)window.tolkClear();", null)
            injected = false
            web.reload()
        }
        btnVeil.setOnClickListener {
            prefs.hideEnglish = !prefs.hideEnglish
            refreshLabels()
            web.evaluateJavascript(
                "if(window.tolkSetHidden)window.tolkSetHidden(${prefs.hideEnglish});", null
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
            else -> loadPlainText("Plak een Nederlandse tekst of open een link met LinguaLens.")
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
            <!doctype html><html lang="nl"><head>
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
        titleView.text = "Tekst"
        web.loadDataWithBaseURL("https://tolk.local/", html, "text/html", "utf-8", null)
    }

    private fun injectAndRun() {
        val js = try {
            assets.open("reader.js").bufferedReader().use { it.readText() }
        } catch (t: Throwable) {
            toast("reader.js ontbreekt"); return
        }
        val bootstrap = """
            window.__tolkMode = '${prefs.readerMode}';
            window.__tolkHidden = ${prefs.hideEnglish};
        """.trimIndent()

        if (!injected) {
            web.evaluateJavascript(bootstrap, null)
            web.evaluateJavascript(js, null)
            injected = true
        }
        web.evaluateJavascript("if(window.tolkRun)window.tolkRun();", null)
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        web.removeJavascriptInterface("LinguaLens")
        super.onDestroy()
    }

    @Suppress("unused")
    inner class Bridge {

        @JavascriptInterface
        fun requestTranslate(payload: String) {
            lifecycleScope.launch {
                val items = withContext(Dispatchers.Default) { parse(payload) }
                if (items.isEmpty()) return@launch
                val translations = Nl2En.translateAll(items.map { it.second })
                val obj = JSONObject()
                items.forEachIndexed { i, pair ->
                    val value = translations.getOrNull(i).orEmpty()
                    if (value.isNotBlank()) obj.put(pair.first, value)
                }
                val literal = JSONObject.quote(obj.toString())
                web.evaluateJavascript("window.tolkApply($literal);", null)
            }
        }

        @JavascriptInterface
        fun save(text: String, context: String, source: String) {
            lifecycleScope.launch {
                val result = repo.save(text, null, context, source)
                toast(result.message)
            }
        }

        @JavascriptInterface
        fun lookup(text: String) {
            lifecycleScope.launch {
                val translated = Nl2En.translate(text)
                toast(if (translated.isBlank()) "Geen vertaling" else translated)
            }
        }

        @JavascriptInterface
        fun speak(text: String) {
            runOnUiThread { Speaker.speak(this@ReaderActivity, text) }
        }

        @JavascriptInterface
        fun onReady(count: Int) {
            // no-op, kept for future progress reporting
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
        const val EXTRA_URL = "tolk_url"
        const val EXTRA_TEXT = "tolk_text"
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
    }
}
