package com.lingualens.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.lingualens.app.R
import com.lingualens.app.data.Repo
import com.lingualens.app.translate.Nl2En
import com.lingualens.app.translate.Sentences
import com.lingualens.app.util.Speaker
import kotlinx.coroutines.launch

/**
 * Appears in the text-selection menu of any app (Chrome, Discord, WhatsApp, ...).
 * Highlight Dutch text, tap "LinguaLens: vertaal", get the English plus a save button.
 */
class ProcessTextActivity : ComponentActivity() {

    private lateinit var dutchView: TextView
    private lateinit var englishView: TextView
    private var source = ""
    private var text = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_quick)
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        text = readIncomingText().trim()
        source = callingPackage ?: referrer?.host ?: ""

        dutchView = findViewById(R.id.dutch)
        englishView = findViewById(R.id.english)
        dutchView.text = text

        if (text.isEmpty()) {
            finish(); return
        }

        Speaker.init(this)
        translate()

        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnSpeak).setOnClickListener {
            Speaker.speak(this, text)
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
        englishView.text = "Vertalen…"
        lifecycleScope.launch {
            val sentences = Sentences.split(text)
            val result = if (sentences.size > 1) {
                Nl2En.translateAll(sentences).joinToString(" ")
            } else {
                Nl2En.translate(text)
            }
            englishView.text = if (result.isBlank()) {
                "Geen vertaling. Open LinguaLens en download het offline model."
            } else result
        }
    }

    private fun save() {
        lifecycleScope.launch {
            val english = englishView.text?.toString().orEmpty()
            val result = Repo.get(this@ProcessTextActivity)
                .save(text, english, text, source)
            Toast.makeText(this@ProcessTextActivity, result.message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
