package com.xiaomi.parts.data

import android.content.Context
import android.os.IBinder
import android.os.Parcel
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.xiaomi.parts.data.PartsStoreScope.safeData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SaturationRepository(context: Context) {

    private val store = context.applicationContext.partsDataStore

    private val _saturationFlow = MutableStateFlow(DEFAULT_VALUE.toFloat())
    val saturationFlow: StateFlow<Float> = _saturationFlow.asStateFlow()

    private val surfaceFlinger: IBinder? by lazy {
        runCatching {
            val sm = Class.forName("android.os.ServiceManager")
            sm.getMethod("getService", String::class.java).invoke(null, "SurfaceFlinger") as? IBinder
        }.getOrNull()
    }

    init {
        PartsStoreScope.io.launch {
            store.safeData().collect { prefs ->
                _saturationFlow.value = (prefs[KEY_SATURATION] ?: DEFAULT_VALUE).coerceIn(0, 100).toFloat()
            }
        }
    }

    fun setSaturation(percent: Float) {
        val clamped = percent.roundToInt().coerceIn(0, 100)
        _saturationFlow.value = clamped.toFloat()
        PartsStoreScope.io.launch {
            store.edit { it[KEY_SATURATION] = clamped }
            applyToSurfaceFlinger(clamped)
        }
    }

    private fun applyToSurfaceFlinger(percent: Int) {
        val binder = surfaceFlinger ?: return
        // 50% maps to neutral 1.001f (matching marble SurfaceFlinger quirk), 0%..100% -> 0.0f..2.0f
        val factor = if (percent == DEFAULT_VALUE) 1.001f else percent / 50.0f
        runCatching {
            val data = Parcel.obtain()
            try {
                data.writeInterfaceToken("android.ui.ISurfaceComposer")
                data.writeFloat(factor)
                binder.transact(SURFACE_FLINGER_SATURATION_CODE, data, null, 0)
            } finally {
                data.recycle()
            }
        }
    }

    companion object {
        private val KEY_SATURATION = intPreferencesKey("saturation_percent")
        private const val DEFAULT_VALUE = 50
        private const val SURFACE_FLINGER_SATURATION_CODE = 1022
    }
}
