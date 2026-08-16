package com.langualens.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.langualens.app.R
import com.langualens.app.data.Prefs
import com.langualens.app.data.Repo
import com.langualens.app.translate.Sentences
import com.langualens.app.translate.Translate
import com.langualens.app.util.LocaleHelper
import com.langualens.app.util.Speaker
import kotlinx.coroutines.launch

/**
 * Appears in the text selection menu of any app (Chrome, Discord, WhatsApp).
 * Highlight text, tap LanguaLens, get the translation plus a save button.
 */
class ProcessTextActivity : ComponentActivity() {

    private lateinit var sourceView: TextView
    private lateinit var targetView: TextView
    private lateinit var prefs: Prefs
    private var origin = ""
    private var text = ""

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_quick)
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        prefs = Prefs(this)
        Translate.configure(prefs.sourceLanguage, prefs.targetLanguage)

        text = readIncomingText().trim()
        origin = callingPackage ?: referrer?.host ?: ""

        sourceView = findViewById(R.id.sourceText)
        targetView = findViewById(R.id.targetText)
        sourceView.text = text

        if (text.isEmpty()) {
            finish(); return
        }

        Speaker.init(this)
        translate()

        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnSpeak).setOnClickListener {
            Speaker.speak(this, text, prefs.sourceLanguage)
        }
        findViewById<Button>(R.id.btnReader).setOnClickListener {
            startActivity(
                Intent(this, ReaderActivity::class.java)
                    .putExtra(ReaderActivity.EXTRA_TEXT, text)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            finish()
        }
    }

    private fun readIncomingText(): String {
        intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.let { return it.toString() }
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { return it }
        return ""
    }

    private fun translate() {
        targetView.text = getString(R.string.translating)
        lifecycleScope.launch {
            val sentences = Sentences.split(text)
            val result = if (sentences.size > 1) {
                Translate.translateAll(sentences).joinToString(" ")
            } else {
                Translate.translate(text)
            }
            targetView.text =
                if (result.isBlank()) getString(R.string.no_translation) else result
        }
    }

    private fun save() {
        lifecycleScope.launch {
            val translation = targetView.text?.toString().orEmpty()
            val result = Repo.get(this@ProcessTextActivity)
                .save(text, translation, text, origin)
            Toast.makeText(
                this@ProcessTextActivity,
                Repo.message(this@ProcessTextActivity, result),
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }
}
