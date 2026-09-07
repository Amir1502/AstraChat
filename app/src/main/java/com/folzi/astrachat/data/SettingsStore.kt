package com.folzi.astrachat.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.folzi.astrachat.core.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

private val Context.settingsData by preferencesDataStore("settings")
@Serializable
data class AppSettings(
    val theme: String = "system", val dynamicColors: Boolean = false,
    val textScale: Float = 1f, val animations: Boolean = true,
    val providerId: String = "openai", val modelId: String = "", val generation: Generation = Generation(),
)
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val key = stringPreferencesKey("preferences_v1")
    val settings = context.settingsData.data.map { prefs ->
        prefs[key]?.let { runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull() } ?: AppSettings()
    }
    suspend fun save(value: AppSettings) { context.settingsData.edit { it[key] = json.encodeToString(value) } }
}
