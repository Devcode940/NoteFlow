package com.noteflow.ai.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.noteflow.ai.domain.model.AppSettings
import com.noteflow.ai.domain.model.EditorMode
import com.noteflow.ai.domain.model.ExportFormat
import com.noteflow.ai.domain.model.ThemeMode
import com.noteflow.ai.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "noteflow_preferences")

/** DataStore-backed user preferences. All writes are transactional. */
@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PreferencesRepository {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val EDITOR_MODE = stringPreferencesKey("editor_mode")
        val AUTO_SAVE = stringPreferencesKey("auto_save") // "true"/"false" via booleanPreferencesKey alternative
        val AUTO_SAVE_DELAY = longPreferencesKey("auto_save_delay_ms")
        val WORD_GOAL = intPreferencesKey("word_goal")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val BACKEND_URL = stringPreferencesKey("backend_url")
        val SYNC_ENABLED = stringPreferencesKey("sync_enabled")
        val EXPORT_FORMAT = stringPreferencesKey("export_format")
        val AI_REQUEST_COUNT = longPreferencesKey("ai_request_count")
        val AI_LAST_USED_AT = longPreferencesKey("ai_last_used_at")
    }

    override val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name) }
                .getOrDefault(ThemeMode.SYSTEM),
            defaultEditorMode = runCatching {
                EditorMode.valueOf(prefs[Keys.EDITOR_MODE] ?: EditorMode.EDIT.name)
            }.getOrDefault(EditorMode.EDIT),
            autoSave = prefs[Keys.AUTO_SAVE]?.toBoolean() ?: true,
            autoSaveDelayMs = prefs[Keys.AUTO_SAVE_DELAY] ?: 800L,
            wordGoal = prefs[Keys.WORD_GOAL] ?: 500,
            aiModel = prefs[Keys.AI_MODEL] ?: "gemini-2.5-flash",
            backendUrl = prefs[Keys.BACKEND_URL] ?: "",
            syncEnabled = prefs[Keys.SYNC_ENABLED]?.toBoolean() ?: true,
            defaultExportFormat = runCatching {
                ExportFormat.valueOf(prefs[Keys.EXPORT_FORMAT] ?: ExportFormat.MARKDOWN.name)
            }.getOrDefault(ExportFormat.MARKDOWN),
            aiRequestCount = prefs[Keys.AI_REQUEST_COUNT] ?: 0L,
            aiLastUsedAt = prefs[Keys.AI_LAST_USED_AT]
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setDefaultEditorMode(mode: EditorMode) {
        context.dataStore.edit { it[Keys.EDITOR_MODE] = mode.name }
    }

    override suspend fun setAutoSave(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SAVE] = enabled.toString() }
    }

    override suspend fun setAutoSaveDelayMs(delayMs: Long) {
        context.dataStore.edit { it[Keys.AUTO_SAVE_DELAY] = delayMs }
    }

    override suspend fun setWordGoal(goal: Int) {
        context.dataStore.edit { it[Keys.WORD_GOAL] = goal }
    }

    override suspend fun setAiModel(model: String) {
        context.dataStore.edit { it[Keys.AI_MODEL] = model }
    }

    override suspend fun setBackendUrl(url: String) {
        context.dataStore.edit { it[Keys.BACKEND_URL] = url.trim() }
    }

    override suspend fun setSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SYNC_ENABLED] = enabled.toString() }
    }

    override suspend fun setDefaultExportFormat(format: ExportFormat) {
        context.dataStore.edit { it[Keys.EXPORT_FORMAT] = format.name }
    }

    override suspend fun recordAiUsage() {
        context.dataStore.edit {
            it[Keys.AI_REQUEST_COUNT] = (it[Keys.AI_REQUEST_COUNT] ?: 0L) + 1
            it[Keys.AI_LAST_USED_AT] = System.currentTimeMillis()
        }
    }
}
