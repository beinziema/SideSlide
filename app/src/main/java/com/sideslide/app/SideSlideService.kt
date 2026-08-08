package com.sideslide.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

class SideSlideService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var store: SettingsStore
    private val handler = Handler(Looper.getMainLooper())
    private val edgeViews = mutableListOf<View>()
    private var panel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var downX = 0f
    private var downY = 0f
    private var activeEdge = Edge.LEFT
    private var holdRunnable: Runnable? = null
    private var gestureStarted = false
    private var panelVisible = false
    private var lastOrientationWidth = 0
    private var lastOrientationHeight = 0

    enum class Edge { LEFT, RIGHT }

    override fun onCreate() {
        super.onCreate()
        store = SettingsStore(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        if (Settings.canDrawOverlays(this) && store.enabled) installEdgeCapture()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (store.enabled && Settings.canDrawOverlays(this)) {
            installEdgeCapture()
        } else {
            removeEdgeCapture()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removeEdgeCapture()
        hidePanel(immediate = true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun installEdgeCapture() {
        if (edgeViews.isNotEmpty()) return
        val metrics = resources.displayMetrics
        lastOrientationWidth = metrics.widthPixels
        lastOrientationHeight = metrics.heightPixels

        val edge = store.edge
        if (edge == "left" || edge == "both") addEdgeView(Edge.LEFT)
        if (edge == "right" || edge == "both") addEdgeView(Edge.RIGHT)
    }

    private fun addEdgeView(edge: Edge) {
        val density = resources.displayMetrics.density
        val width = (14 * density).toInt()
        val view = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnTouchListener { _, event -> handleEdgeTouch(edge, event) }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = if (edge == Edge.LEFT) Gravity.START else Gravity.END
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        try {
            windowManager.addView(view, params)
            edgeViews += view
        } catch (_: Exception) {
            Toast.makeText(this, "SideSlide could not create its edge detector.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleEdgeTouch(edge: Edge, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeEdge = edge
                downX = event.rawX
                downY = event.rawY
                gestureStarted = true
                panelVisible = false
                holdRunnable?.let(handler::removeCallbacks)
                holdRunnable = Runnable {
                    if (gestureStarted && !panelVisible) {
                        // Holding alone is not enough. The swipe distance threshold must still be crossed.
                    }
                }
                handler.postDelayed(holdRunnable!!, store.holdMs)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!gestureStarted) return true
                val dx = if (activeEdge == Edge.LEFT) event.rawX - downX else downX - event.rawX
                val dy = kotlin.math.abs(event.rawY - downY)
                val elapsed = event.eventTime - event.downTime
                val distancePx = store.swipeDistanceDp * resources.displayMetrics.density
                val adjustedDistance = distancePx * (1.15f - store.sensitivity * 0.45f)
                if (!panelVisible && elapsed >= store.holdMs && dx >= adjustedDistance && dx > dy * 0.8f) {
                    showPanel(activeEdge)
                    return true
                }
                if (panelVisible) return true
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                holdRunnable?.let(handler::removeCallbacks)
                gestureStarted = false
                if (panelVisible) {
                    val dx = if (activeEdge == Edge.LEFT) event.rawX - downX else downX - event.rawX
                    if (dx < store.swipeDistanceDp * resources.displayMetrics.density * 0.5f) hidePanel()
                }
                return true
            }
        }
        return true
    }

    private fun showPanel(edge: Edge) {
        if (panelVisible || !Settings.canDrawOverlays(this)) return
        panelVisible = true
        if (store.haptics) performHaptic()

        val density = resources.displayMetrics.density
        val width = (store.panelWidthDp * density).toInt()
        val height = (store.panelHeightDp * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
            setBackgroundColor(Color.rgb(245, 242, 250))
            alpha = 0f
            elevation = 18 * density
        }

        val title = TextView(this).apply {
            text = "SideSlide"
            textSize = 20f
            setTextColor(Color.rgb(35, 32, 40))
            setPadding(4, 0, 4, (8 * density).toInt())
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))

        val scrollView = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isFillViewport = true
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addAction(list, "Settings") {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            hidePanel()
        }
        addAction(list, "Home") {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(home)
            hidePanel()
        }
        addAction(list, "Browser") {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            hidePanel()
        }
        addAction(list, "Close") { hidePanel() }
        scrollView.addView(list)
        root.addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f))

        val params = WindowManager.LayoutParams(
            width,
            height,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or (if (edge == Edge.LEFT) Gravity.START else Gravity.END)
            y = ((resources.displayMetrics.heightPixels - height) * store.panelVertical).toInt().coerceAtLeast(0)
            x = 0
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        try {
            windowManager.addView(root, params)
            panel = root
            panelParams = params
            val translation = if (edge == Edge.LEFT) -width.toFloat() else width.toFloat()
            root.translationX = translation
            root.animate().translationX(0f).alpha(1f).setDuration(store.animationMs).setInterpolator(DecelerateInterpolator()).start()
        } catch (_: Exception) {
            panelVisible = false
        }
    }

    private fun addAction(list: LinearLayout, label: String, action: () -> Unit) {
        val density = resources.displayMetrics.density
        val item = TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(Color.rgb(35, 32, 40))
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
            setBackgroundColor(Color.WHITE)
            isClickable = true
            setOnClickListener { action() }
        }
        val params = LinearLayout.LayoutParams(-1, (54 * density).toInt()).apply { bottomMargin = (6 * density).toInt() }
        list.addView(item, params)
    }

    private fun hidePanel(immediate: Boolean = false) {
        val current = panel ?: run {
            panelVisible = false
            return
        }
        panelVisible = false
        if (immediate) {
            try { windowManager.removeViewImmediate(current) } catch (_: Exception) {}
            panel = null
            return
        }
        val edge = activeEdge
        val width = current.width.toFloat().coerceAtLeast(resources.displayMetrics.density * store.panelWidthDp)
        val target = if (edge == Edge.LEFT) -width else width
        current.animate().translationX(target).alpha(0f).setDuration(store.fadeMs).withEndAction {
            try { windowManager.removeView(current) } catch (_: Exception) {}
            if (panel === current) panel = null
        }.start()
    }

    private fun performHaptic() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(android.os.VibrationEffect.createOneShot(16, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") vibrator.vibrate(16)
    }

    private fun removeEdgeCapture() {
        holdRunnable?.let(handler::removeCallbacks)
        holdRunnable = null
        gestureStarted = false
        edgeViews.forEach { view -> try { windowManager.removeView(view) } catch (_: Exception) {} }
        edgeViews.clear()
    }

    private fun overlayType(): Int = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "SideSlide", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps SideSlide's edge gesture detector available."
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("SideSlide is active")
                .setContentText("Swipe from an edge to open your panel")
                .setSmallIcon(android.R.drawable.ic_menu_more)
                .setContentIntent(pending)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("SideSlide is active")
                .setContentText("Swipe from an edge to open your panel")
                .setSmallIcon(android.R.drawable.ic_menu_more)
                .setContentIntent(pending)
                .setOngoing(true)
                .build()
        }
    }

    companion object {
        private const val CHANNEL_ID = "sideslide_service"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.sideslide.app.STOP"

        fun start(context: Context) {
            val intent = Intent(context, SideSlideService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SideSlideService::class.java))
        }
    }
}