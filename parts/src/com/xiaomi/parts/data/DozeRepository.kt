package com.xiaomi.parts.data

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.xiaomi.parts.data.PartsStoreScope.safeData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class DozeState(
    val dozeEnabled: Boolean = true,
    val handWaveEnabled: Boolean = false,
    val pocketEnabled: Boolean = true,
    val pickUpEnabled: Boolean = true,
)

class DozeRepository(context: Context) {

    private val appContext = context.applicationContext
    private val store = appContext.partsDataStore
    private val cr = appContext.contentResolver

    private val _stateFlow = MutableStateFlow(DozeState())
    val stateFlow: StateFlow<DozeState> = _stateFlow.asStateFlow()

    init {
        PartsStoreScope.io.launch {
            store.safeData().collect { prefs ->
                _stateFlow.value = DozeState(
                    dozeEnabled = readEffectiveBoolean(KEY_DOZE_ENABLED, PREF_DOZE_ENABLED, prefs[PREF_DOZE_ENABLED], true),
                    handWaveEnabled = readEffectiveBoolean(KEY_HAND_WAVE, PREF_HAND_WAVE, prefs[PREF_HAND_WAVE], false),
                    pocketEnabled = readEffectiveBoolean(KEY_POCKET, PREF_POCKET, prefs[PREF_POCKET], true),
                    pickUpEnabled = readEffectiveBoolean(KEY_PICK_UP, PREF_PICK_UP, prefs[PREF_PICK_UP], true),
                )
            }
        }
    }

    fun setBoolean(key: String, enabled: Boolean) {
        val prefKey = when (key) {
            KEY_DOZE_ENABLED -> {
                _stateFlow.update { it.copy(dozeEnabled = enabled) }
                PREF_DOZE_ENABLED
            }
            KEY_HAND_WAVE -> {
                _stateFlow.update { it.copy(handWaveEnabled = enabled) }
                PREF_HAND_WAVE
            }
            KEY_POCKET -> {
                _stateFlow.update { it.copy(pocketEnabled = enabled) }
                PREF_POCKET
            }
            KEY_PICK_UP -> {
                _stateFlow.update { it.copy(pickUpEnabled = enabled) }
                PREF_PICK_UP
            }
            else -> return
        }

        PartsStoreScope.io.launch {
            store.edit { it[prefKey] = enabled }
            runCatching {
                Settings.Secure.putInt(cr, key, if (enabled) 1 else 0)
            }
        }
    }

    private fun readEffectiveBoolean(
        secureKey: String,
        prefKey: androidx.datastore.preferences.core.Preferences.Key<Boolean>,
        stored: Boolean?,
        defaultValue: Boolean,
    ): Boolean {
        val fallback = stored ?: defaultValue
        return runCatching {
            val raw = Settings.Secure.getString(cr, secureKey)
            if (raw != null) raw != "0" else fallback
        }.getOrDefault(fallback)
    }

    companion object {
        const val KEY_DOZE_ENABLED = "doze_enabled"
        const val KEY_HAND_WAVE = "doze_pulse_on_hand_wave"
        const val KEY_POCKET = "doze_pulse_on_pocket"
        const val KEY_PICK_UP = "doze_pulse_on_pick_up"

        private val PREF_DOZE_ENABLED = booleanPreferencesKey(KEY_DOZE_ENABLED)
        private val PREF_HAND_WAVE = booleanPreferencesKey(KEY_HAND_WAVE)
        private val PREF_POCKET = booleanPreferencesKey(KEY_POCKET)
        private val PREF_PICK_UP = booleanPreferencesKey(KEY_PICK_UP)
    }
}
