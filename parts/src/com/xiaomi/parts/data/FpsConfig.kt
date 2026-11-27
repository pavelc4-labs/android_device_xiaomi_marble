package com.xiaomi.parts.data

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.xiaomi.parts.data.PartsStoreScope.safeData
import com.xiaomi.parts.ui.fps.FpsOverlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class FpsStatsSnapshot(
    val fps: String = "0",
    val cpuTotal: String = "0%",
    val cpuTemp: String = "0°C",
    val gpuUsage: String = "0%",
    val gpuTemp: String = "0°C",
    val gpuSpeed: String = "0 MHz",
    val ramUsage: String = "0.0 GB",
    val isMock: Boolean = false,
)

@Immutable
data class FpsConfigSnapshot(
    val alpha: Float = 0.78f,
    val textSize: Float = 13f,
    val fpsIntervalMs: Long = 500L,
    val showFps: Boolean = true,
    val showCpuUsage: Boolean = true,
    val showCpuTemp: Boolean = true,
    val showGpuUsage: Boolean = true,
    val showGpuTemp: Boolean = true,
    val showGpuSpeed: Boolean = false,
    val showRam: Boolean = true,
    val showCompact: Boolean = false,
    val touchPassthrough: Boolean = false,
    val mockData: Boolean = false,
) {
    fun hasEnabledMetrics(): Boolean =
        showFps || showCpuUsage || showCpuTemp || showGpuUsage || showGpuTemp || showGpuSpeed || showRam
}

class FpsPrefs private constructor(
    private val context: Context,
) {
    private val store = context.partsDataStore
    private val serviceIntent by lazy { Intent(context, FpsOverlayService::class.java) }
    private val defaultMock = !FpsHardwareMonitor.isHardwareAvailable()

    @Volatile
    private var loadedFromStore = false

    private val _isEnabledFlow = MutableStateFlow(false)
    val isEnabledFlow: StateFlow<Boolean> = _isEnabledFlow.asStateFlow()

    private val _configFlow = MutableStateFlow(FpsConfigSnapshot(mockData = defaultMock))
    val configFlow: StateFlow<FpsConfigSnapshot> = _configFlow.asStateFlow()

    init {
        PartsStoreScope.io.launch {
            store.safeData().collect { prefs ->
                loadedFromStore = true
                _isEnabledFlow.value = prefs[KEY_ENABLED] ?: false
                _configFlow.value = prefs.toConfigSnapshot()
            }
        }
    }

    var isEnabled: Boolean
        get() = _isEnabledFlow.value
        set(value) {
            loadedFromStore = true
            _isEnabledFlow.value = value
            PartsStoreScope.editAsync(store) { it[KEY_ENABLED] = value }
            if (value) startService() else stopService()
        }

    var mockData: Boolean
        get() = _configFlow.value.mockData
        set(value) = updateConfig(KEY_MOCK_DATA, value) { it.copy(mockData = value) }

    var fpsIntervalMs: Long
        get() = _configFlow.value.fpsIntervalMs
        set(value) = updateConfig(KEY_INTERVAL_MS, value) { it.copy(fpsIntervalMs = value) }

    var showCompact: Boolean
        get() = _configFlow.value.showCompact
        set(value) = updateConfig(KEY_COMPACT, value) { it.copy(showCompact = value) }

    var touchPassthrough: Boolean
        get() = _configFlow.value.touchPassthrough
        set(value) = updateConfig(KEY_TOUCH_PASSTHROUGH, value) { it.copy(touchPassthrough = value) }

    var showFps: Boolean
        get() = _configFlow.value.showFps
        set(value) = updateConfig(KEY_SHOW_FPS, value) { it.copy(showFps = value) }

    var showCpuUsage: Boolean
        get() = _configFlow.value.showCpuUsage
        set(value) = updateConfig(KEY_SHOW_CPU_USAGE, value) { it.copy(showCpuUsage = value) }

    var showCpuTemp: Boolean
        get() = _configFlow.value.showCpuTemp
        set(value) = updateConfig(KEY_SHOW_CPU_TEMP, value) { it.copy(showCpuTemp = value) }

    var showGpuUsage: Boolean
        get() = _configFlow.value.showGpuUsage
        set(value) = updateConfig(KEY_SHOW_GPU_USAGE, value) { it.copy(showGpuUsage = value) }

    var showGpuTemp: Boolean
        get() = _configFlow.value.showGpuTemp
        set(value) = updateConfig(KEY_SHOW_GPU_TEMP, value) { it.copy(showGpuTemp = value) }

    var showGpuSpeed: Boolean
        get() = _configFlow.value.showGpuSpeed
        set(value) = updateConfig(KEY_SHOW_GPU_SPEED, value) { it.copy(showGpuSpeed = value) }

    var showRam: Boolean
        get() = _configFlow.value.showRam
        set(value) = updateConfig(KEY_SHOW_RAM, value) { it.copy(showRam = value) }

    var overlayAlpha: Float
        get() = _configFlow.value.alpha
        set(value) = updateConfig(KEY_ALPHA, value) { it.copy(alpha = value) }

    var textSizeSp: Float
        get() = _configFlow.value.textSize
        set(value) = updateConfig(KEY_TEXT_SIZE, value) { it.copy(textSize = value) }

    private inline fun <T> updateConfig(
        key: Preferences.Key<T>,
        value: T,
        crossinline transform: (FpsConfigSnapshot) -> FpsConfigSnapshot,
    ) {
        _configFlow.update { transform(it) }
        PartsStoreScope.editAsync(store) { it[key] = value }
    }

    suspend fun resolveEnabledForService(): Boolean {
        if (loadedFromStore) return _isEnabledFlow.value
        val prefs = store.safeData().first()
        loadedFromStore = true
        val enabled = prefs[KEY_ENABLED] ?: false
        _isEnabledFlow.value = enabled
        _configFlow.value = prefs.toConfigSnapshot()
        return enabled
    }

    fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun startService() {
        if (!hasOverlayPermission()) return
        context.startForegroundService(serviceIntent)
    }

    fun stopService() {
        context.stopService(serviceIntent)
    }

    private fun Preferences.toConfigSnapshot(): FpsConfigSnapshot = FpsConfigSnapshot(
        alpha = this[KEY_ALPHA] ?: 0.78f,
        textSize = this[KEY_TEXT_SIZE] ?: 13f,
        fpsIntervalMs = (this[KEY_INTERVAL_MS] ?: 500L).coerceIn(250L, 2000L),
        showFps = this[KEY_SHOW_FPS] ?: true,
        showCpuUsage = this[KEY_SHOW_CPU_USAGE] ?: true,
        showCpuTemp = this[KEY_SHOW_CPU_TEMP] ?: true,
        showGpuUsage = this[KEY_SHOW_GPU_USAGE] ?: true,
        showGpuTemp = this[KEY_SHOW_GPU_TEMP] ?: true,
        showGpuSpeed = this[KEY_SHOW_GPU_SPEED] ?: false,
        showRam = this[KEY_SHOW_RAM] ?: true,
        showCompact = this[KEY_COMPACT] ?: false,
        touchPassthrough = this[KEY_TOUCH_PASSTHROUGH] ?: false,
        mockData = this[KEY_MOCK_DATA] ?: defaultMock,
    )

    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("fps_enabled")
        private val KEY_MOCK_DATA = booleanPreferencesKey("fps_mock_data")
        private val KEY_INTERVAL_MS = longPreferencesKey("fps_interval_ms")
        private val KEY_COMPACT = booleanPreferencesKey("fps_compact")
        private val KEY_TOUCH_PASSTHROUGH = booleanPreferencesKey("fps_touch_passthrough")
        private val KEY_SHOW_FPS = booleanPreferencesKey("fps_show_fps")
        private val KEY_SHOW_CPU_USAGE = booleanPreferencesKey("fps_show_cpu_usage")
        private val KEY_SHOW_CPU_TEMP = booleanPreferencesKey("fps_show_cpu_temp")
        private val KEY_SHOW_GPU_USAGE = booleanPreferencesKey("fps_show_gpu_usage")
        private val KEY_SHOW_GPU_TEMP = booleanPreferencesKey("fps_show_gpu_temp")
        private val KEY_SHOW_GPU_SPEED = booleanPreferencesKey("fps_show_gpu_speed")
        private val KEY_SHOW_RAM = booleanPreferencesKey("fps_show_ram")
        private val KEY_ALPHA = floatPreferencesKey("fps_alpha")
        private val KEY_TEXT_SIZE = floatPreferencesKey("fps_text_size")

        @Volatile
        private var instance: FpsPrefs? = null

        fun getInstance(context: Context): FpsPrefs =
            instance ?: synchronized(this) {
                instance ?: FpsPrefs(context.applicationContext).also { instance = it }
            }
    }
}
