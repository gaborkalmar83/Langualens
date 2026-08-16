package com.langualens.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.langualens.app.R
import com.langualens.app.data.Prefs
import com.langualens.app.data.Repo
import com.langualens.app.translate.Translate
import com.langualens.app.ui.MainActivity
import com.langualens.app.util.LocaleHelper
import com.langualens.app.util.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Draggable bubble floating over other apps. Tapping it reads the visible screen
 * through the accessibility service and shows source and translation side by side.
 */
class BubbleService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private var bubble: View? = null
    private var panel: View? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)
        Translate.configure(prefs.sourceLanguage, prefs.targetLanguage)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        Speaker.init(this)
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.bubble_channel), NotificationManager.IMPORTANCE_MIN
        )
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, BubbleService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.bubble_running))
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    null as Icon?, getString(R.string.stop), stop
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    /* ---------------------------- bubble ---------------------------- */

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun showBubble() {
        if (bubble != null) return
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)
        view.findViewById<TextView>(R.id.bubbleLabel).text =
            prefs.sourceLanguage.uppercase().take(2)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(240)
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var dragging = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchX = event.rawX; touchY = event.rawY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) dragging = true
                    params.x = startX + dx
                    params.y = startY + dy
                    try { wm.updateViewLayout(v, params) } catch (t: Throwable) { }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) onBubbleTapped()
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(view, params)
            bubble = view
        } catch (t: Throwable) {
            Toast.makeText(this, getString(R.string.overlay_missing), Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun onBubbleTapped() {
        if (panel != null) { hidePanel(); return }
        val service = ScreenReaderService.instance
        if (service == null) {
            Toast.makeText(
                this, getString(R.string.enable_accessibility_first), Toast.LENGTH_LONG
            ).show()
            return
        }
        val lines = service.readScreen()
        if (lines.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_text_found), Toast.LENGTH_SHORT).show()
            return
        }
        showPanel(lines)
    }

    /* ---------------------------- panel ---------------------------- */

    private fun showPanel(lines: List<String>) {
        hidePanel()
        Translate.configure(prefs.sourceLanguage, prefs.targetLanguage)

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_panel, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.62).toInt(),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM }

        val list = view.findViewById<LinearLayout>(R.id.panelList)
        val title = view.findViewById<TextView>(R.id.panelTitle)
        view.findViewById<Button>(R.id.btnClose).setOnClickListener { hidePanel() }
        view.findViewById<Button>(R.id.btnRescan).setOnClickListener {
            val fresh = ScreenReaderService.instance?.readScreen().orEmpty()
            hidePanel()
            if (fresh.isNotEmpty()) showPanel(fresh)
        }

        try {
            wm.addView(view, params)
            panel = view
        } catch (t: Throwable) {
            return
        }

        val capped = lines.take(60)
        title.text = getString(R.string.screen_translated, capped.size)

        val rows = capped.map { addRow(list, it) }

        job?.cancel()
        job = scope.launch {
            val translations = Translate.translateAll(capped)
            translations.forEachIndexed { i, value ->
                rows.getOrNull(i)?.text = value.ifBlank { "—" }
            }
        }
    }

    private fun addRow(list: LinearLayout, source: String): TextView {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        val top = TextView(this).apply {
            text = source
            setTextColor(Color.parseColor("#E7EDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }
        val bottom = TextView(this).apply {
            text = "…"
            setTextColor(Color.parseColor("#7FB2FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(3), 0, 0)
        }
        container.addView(top)
        container.addView(bottom)

        container.setOnClickListener {
            val translation = bottom.text?.toString().orEmpty()
            scope.launch {
                val result = Repo.get(this@BubbleService)
                    .save(source, translation, source, "screen")
                Toast.makeText(
                    this@BubbleService,
                    Repo.message(this@BubbleService, result),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        container.setOnLongClickListener {
            Speaker.speak(this, source, prefs.sourceLanguage)
            true
        }

        val divider = View(this).apply {
            setBackgroundColor(Color.parseColor("#1FFFFFFF"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            )
        }

        list.addView(container)
        list.addView(divider)
        return bottom
    }

    private fun hidePanel() {
        panel?.let { try { wm.removeView(it) } catch (t: Throwable) { } }
        panel = null
    }

    override fun onDestroy() {
        job?.cancel()
        hidePanel()
        bubble?.let { try { wm.removeView(it) } catch (t: Throwable) { } }
        bubble = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_STOP = "com.langualens.app.STOP_BUBBLE"
        private const val CHANNEL_ID = "langualens_bubble"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            val intent = Intent(context, BubbleService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, BubbleService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
