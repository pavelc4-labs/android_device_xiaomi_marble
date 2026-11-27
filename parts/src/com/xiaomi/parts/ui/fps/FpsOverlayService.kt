package com.xiaomi.parts.ui.fps

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.xiaomi.parts.R
import com.xiaomi.parts.data.FpsHardwareMonitor
import com.xiaomi.parts.data.FpsPrefs
import com.xiaomi.parts.data.FpsStatsSnapshot
import com.xiaomi.parts.ui.theme.XiaomiPartsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FpsOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val NOTIFICATION_ID = 9081
        private const val CHANNEL_ID = "fps_overlay_channel"
        private const val FAST_MIN_INTERVAL_MS = 1000L
        private const val SLOW_MIN_INTERVAL_MS = 3000L
        private const val IDLE_INTERVAL_MS = 2000L
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: FpsPrefs

    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var monitorJob: Job? = null
    private var isDisplayAttached = true

    private val statsFlow = MutableStateFlow(FpsStatsSnapshot())

    private val displayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isDisplayAttached = false
                    stopMonitoring()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isDisplayAttached = true
                    if (prefs.isEnabled) startMonitoring()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = FpsPrefs.getInstance(this)

        startOverlayForeground()
        registerDisplayReceiver()

        serviceScope.launch {
            prefs.configFlow.collect { config ->
                updateTouchPassthrough(config.touchPassthrough)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!prefs.hasOverlayPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            if (!prefs.resolveEnabledForService()) {
                stopSelf()
                return@launch
            }

            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

            if (composeView == null) {
                showOverlay()
            }
            startMonitoring()
        }

        return START_STICKY
    }

    private fun startOverlayForeground() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.feat_fps_title),
            NotificationManager.IMPORTANCE_MIN,
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.feat_fps_title))
            .setContentText(getString(R.string.fps_overlay_active))
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun showOverlay() {
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FpsOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FpsOverlayService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val stats by statsFlow.collectAsState()
                val config by prefs.configFlow.collectAsState()
                XiaomiPartsTheme(darkTheme = true) {
                    FpsOverlayContent(stats = stats, config = config)
                }
            }
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            buildWindowFlags(prefs.touchPassthrough),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 48
            y = 120
        }

        attachDragListener(composeView!!)
        windowManager.addView(composeView, layoutParams)
    }

    private fun buildWindowFlags(passthrough: Boolean): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            if (passthrough) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0

    private fun updateTouchPassthrough(enabled: Boolean) {
        val params = layoutParams ?: return
        val view = composeView ?: return
        params.flags = buildWindowFlags(enabled)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun startMonitoring() {
        if (monitorJob?.isActive == true || !isDisplayAttached) return
        monitorJob = serviceScope.launch(Dispatchers.IO) {
            var lastFpsPoll = 0L
            var lastFastPoll = 0L
            var lastSlowPoll = 0L

            while (isActive) {
                val config = prefs.configFlow.value
                if (!config.hasEnabledMetrics()) {
                    delay(IDLE_INTERVAL_MS)
                    continue
                }

                val now = SystemClock.elapsedRealtime()
                val fpsStep = config.fpsIntervalMs.coerceIn(250L, 2000L)
                var snapshot = statsFlow.value

                if (config.showFps && now - lastFpsPoll >= fpsStep) {
                    snapshot = snapshot.copy(fps = FpsHardwareMonitor.pollFpsOnly(config))
                    lastFpsPoll = now
                }
                if (now - lastFastPoll >= maxOf(fpsStep, FAST_MIN_INTERVAL_MS)) {
                    snapshot = FpsHardwareMonitor.pollFastMetrics(config, snapshot)
                    lastFastPoll = now
                }
                if (now - lastSlowPoll >= maxOf(fpsStep, SLOW_MIN_INTERVAL_MS)) {
                    snapshot = FpsHardwareMonitor.pollSlowMetrics(this@FpsOverlayService, config, snapshot)
                    lastSlowPoll = now
                }

                if (snapshot != statsFlow.value) {
                    statsFlow.value = snapshot
                }
                delay(fpsStep)
            }
        }
    }

    private fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun registerDisplayReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(displayReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    private fun attachDragListener(view: View) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f

        view.setOnTouchListener { _, event ->
            val params = layoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newX = startX + (event.rawX - touchX).toInt()
                    val newY = startY + (event.rawY - touchY).toInt()
                    if (params.x != newX || params.y != newY) {
                        params.x = newX
                        params.y = newY
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        stopMonitoring()
        runCatching { unregisterReceiver(displayReceiver) }
        FpsHardwareMonitor.closeAll()
        composeView?.let {
            it.disposeComposition()
            runCatching { windowManager.removeViewImmediate(it) }
        }
        composeView = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        super.onDestroy()
    }
}
