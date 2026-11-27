package com.xiaomi.parts.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.xiaomi.parts.R
import com.xiaomi.parts.data.PartsStoreScope.safeData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class ThermalProfile(
    val sconfig: String,
    @StringRes val labelRes: Int,
    @StringRes val descRes: Int,
    val badge: String?,
) {
    DEFAULT("0", R.string.thermal_profile_default, R.string.thermal_profile_default_desc, null),
    GAMING("13", R.string.thermal_profile_gaming, R.string.thermal_profile_gaming_desc, "PERF"),
    BENCHMARK("10", R.string.thermal_profile_benchmark, R.string.thermal_profile_benchmark_desc, "BENCH"),
    BROWSER("11", R.string.thermal_profile_browser, R.string.thermal_profile_browser_desc, "WEB"),
    CAMERA("12", R.string.thermal_profile_camera, R.string.thermal_profile_camera_desc, "CAM"),
    STREAMING("4", R.string.thermal_profile_streaming, R.string.thermal_profile_streaming_desc, "STREAM"),
}

class ThermalRepository(context: Context) {

    private val store = context.applicationContext.partsDataStore

    private val _profilesFlow = MutableStateFlow<Map<String, ThermalProfile>>(emptyMap())
    val profilesFlow: StateFlow<Map<String, ThermalProfile>> = _profilesFlow.asStateFlow()

    init {
        PartsStoreScope.io.launch {
            store.safeData().collect { prefs ->
                val isInitialized = prefs[KEY_INITIALIZED] == true
                if (!isInitialized) {
                    store.edit { mutable ->
                        mutable[KEY_INITIALIZED] = true
                        DEFAULT_SAMPLES.forEach { (pkg, profile) ->
                            mutable[pkgKey(pkg)] = profile.name
                        }
                    }
                    _profilesFlow.value = DEFAULT_SAMPLES
                } else {
                    val result = buildMap {
                        prefs.asMap().forEach { (key, value) ->
                            if (key.name.startsWith(PKG_KEY_PREFIX)) {
                                val pkg = key.name.removePrefix(PKG_KEY_PREFIX)
                                val profile = (value as? String)?.let { name ->
                                    ThermalProfile.entries.find { it.name == name }
                                }
                                if (profile != null && profile != ThermalProfile.DEFAULT) {
                                    put(pkg, profile)
                                }
                            }
                        }
                    }
                    _profilesFlow.value = result
                }
            }
        }
    }

    fun setProfile(packageName: String, profile: ThermalProfile) {
        _profilesFlow.update { current ->
            if (profile == ThermalProfile.DEFAULT) current - packageName
            else current + (packageName to profile)
        }
        val key = pkgKey(packageName)
        PartsStoreScope.io.launch {
            store.edit { mutable ->
                mutable[KEY_INITIALIZED] = true
                if (profile == ThermalProfile.DEFAULT) {
                    mutable.remove(key)
                } else {
                    mutable[key] = profile.name
                }
            }
            applySconfig(profile)
        }
    }

    private fun applySconfig(profile: ThermalProfile) {
        runCatching {
            val node = File(THERMAL_SCONFIG_PATH)
            if (node.canWrite()) {
                node.writeText(profile.sconfig)
            }
        }
    }

    companion object {
        private const val THERMAL_SCONFIG_PATH = "/sys/class/thermal/thermal_message/sconfig"
        private const val PKG_KEY_PREFIX = "thermal_pkg_"
        private val KEY_INITIALIZED = booleanPreferencesKey("thermal_initialized")
        private val DEFAULT_SAMPLES = mapOf(
            "com.mobile.legends" to ThermalProfile.GAMING,
            "com.miHoYo.GenshinImpact" to ThermalProfile.GAMING,
        )

        private fun pkgKey(packageName: String) = stringPreferencesKey("$PKG_KEY_PREFIX$packageName")
    }
}
