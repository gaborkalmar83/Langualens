package com.langualens.app.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Reads the text currently drawn on screen when the floating bubble asks for it.
 * Nothing is captured unless the user taps the bubble.
 */
class ScreenReaderService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally passive: we only read on demand.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /** Visible text lines, ordered top to bottom. */
    fun readScreen(): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val found = ArrayList<Pair<Int, String>>()
        try {
            collect(root, found, 0)
        } catch (t: Throwable) {
            // ignore, return whatever we got
        }
        val seen = HashSet<String>()
        return found
            .sortedBy { it.first }
            .map { it.second }
            .filter { seen.add(it) }
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<Pair<Int, String>>, depth: Int) {
        if (node == null || depth > 40 || out.size > 400) return
        if (node.packageName?.toString() == packageName) return

        val raw = node.text?.toString()?.trim().orEmpty()
        if (raw.length >= 2 && raw.any { it.isLetter() } && !isNoise(raw)) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                out.add(rect.top to raw)
            }
        }
        for (i in 0 until node.childCount) {
            collect(node.getChild(i), out, depth + 1)
        }
    }

    private fun isNoise(s: String): Boolean {
        if (s.length > 800) return true
        // pure numbers, timestamps, single symbols
        return s.matches(Regex("^[\\d\\s:.,/%+\\-]+$"))
    }

    companion object {
        @Volatile
        var instance: ScreenReaderService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }
}
