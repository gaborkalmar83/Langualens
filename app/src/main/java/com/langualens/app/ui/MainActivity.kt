package com.langualens.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import com.langualens.app.anki.AnkiBridge
import com.langualens.app.ui.screens.LanguaLensRoot
import com.langualens.app.ui.theme.LanguaLensTheme
import com.langualens.app.util.LocaleHelper
import com.langualens.app.util.Speaker

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Speaker.init(this)
        requestBasicPermissions()
        setContent {
            LanguaLensTheme {
                LanguaLensRoot(
                    onOpenUrl = { url -> ReaderActivity.openUrl(this, url) },
                    onOpenText = { text -> ReaderActivity.openText(this, text) },
                    onRequestOverlay = { openOverlaySettings() },
                    onRequestAccessibility = { openAccessibilitySettings() },
                    onRequestAnki = { requestAnkiPermission() },
                    onInterfaceLanguageChanged = { recreate() }
                )
            }
        }
    }

    private fun requestBasicPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11
            )
        }
    }

    private fun requestAnkiPermission() {
        if (!AnkiBridge.hasPermission(this)) {
            ActivityCompat.requestPermissions(this, arrayOf(AnkiBridge.PERMISSION), 12)
        }
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
