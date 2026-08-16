package com.lingualens.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.drawable.Icon
import android.graphics.PixelFormat
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
import com.lingualens.app.R
import com.lingualens.app.data.Repo
import com.lingualens.app.translate.Nl2En
import com.lingualens.app.ui.MainActivity
import com.lingualens.app.util.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Draggable bubble that floats over other apps. Tapping it reads the visible
 * screen text through the accessibility service and shows Dutch/English pairs.
 */
class BubbleService : Service() {

    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var panel: View? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "LinguaLens bubble", NotificationManager.IMPORTANCE_MIN
            )
            channel.setShowBadge(false)
            manager.createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, BubbleService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("LinguaLens")
            .setContentText("Zwevende vertaalknop is actief")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(null as Icon?, "Stop", stop).build()
            )
            .setOngoing(true)
        return builder.build()
    }

    /* ------------------------- bubble ------------------------- */

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun showBubble() {
        if (bubble != null) return
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)
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
                    try { wm.updateViewLayout(v, params) } catch (t: Throwable) {}
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
            Toast.makeText(this, "Overlay-permissie ontbreekt", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun onBubbleTapped() {
        if (panel != null) { hidePanel(); return }
        val service = ScreenReaderService.instance
        if (service == null) {
            Toast.makeText(
                this,
                "Zet eerst 'LinguaLens screen reader' aan bij Toegankelijkheid",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val lines = service.readScreen()
        if (lines.isEmpty()) {
            Toast.makeText(this, "Geen tekst gevonden op het scherm", Toast.LENGTH_SHORT).show()
            return
        }
        showPanel(lines)
    }

    /* ------------------------- panel ------------------------- */

    private fun showPanel(lines: List<String>) {
        hidePanel()
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
        title.text = "Scherm vertaald (${capped.size})"

        val rows = capped.map { text -> addRow(list, text) }

        job?.cancel()
        job = scope.launch {
            val translations = Nl2En.translateAll(capped)
            translations.forEachIndexed { i, value ->
                rows.getOrNull(i)?.text = if (value.isBlank()) "—" else value
            }
        }
    }

    private fun addRow(list: LinearLayout, dutch: String): TextView {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        val nl = TextView(this).apply {
            text = dutch
            setTextColor(Color.parseColor("#E7EDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }
        val en = TextView(this).apply {
            text = "…"
            setTextColor(Color.parseColor("#7FB2FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(3), 0, 0)
        }
        container.addView(nl)
        container.addView(en)

        container.setOnClickListener {
            val english = en.text?.toString().orEmpty()
            scope.launch {
                val result = Repo.get(this@BubbleService)
                    .save(dutch, english, dutch, "scherm")
                Toast.makeText(this@BubbleService, result.message, Toast.LENGTH_SHORT).show()
            }
        }
        container.setOnLongClickListener {
            Speaker.speak(this, dutch)
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
        return en
    }

    private fun hidePanel() {
        panel?.let {
            try { wm.removeView(it) } catch (t: Throwable) {}
        }
        panel = null
    }

    override fun onDestroy() {
        job?.cancel()
        hidePanel()
        bubble?.let { try { wm.removeView(it) } catch (t: Throwable) {} }
        bubble = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_STOP = "com.lingualens.app.STOP_BUBBLE"
        private const val CHANNEL_ID = "tolk_bubble"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            val intent = Intent(context, BubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, BubbleService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
