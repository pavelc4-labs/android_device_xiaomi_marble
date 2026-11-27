package com.xiaomi.parts.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.IOException

internal val Context.partsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "xiaomi_parts_prefs",
)

@OptIn(ExperimentalCoroutinesApi::class)
internal object PartsStoreScope {
    val io: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    fun DataStore<Preferences>.safeData(): Flow<Preferences> =
        data.catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }

    fun editAsync(
        store: DataStore<Preferences>,
        block: suspend (MutablePreferences) -> Unit,
    ) {
        io.launch {
            store.edit(block)
        }
    }
}
