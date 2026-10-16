package com.filemanager.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val theme = stringPreferencesKey("theme")
        val sort = stringPreferencesKey("sort")
        val hidden = booleanPreferencesKey("show_hidden")
        val confirmDelete = booleanPreferencesKey("confirm_delete")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            onboardingDone = p[Keys.onboarding] ?: false,
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            sort = p[Keys.sort]?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() } ?: SortOrder.NAME,
            showHidden = p[Keys.hidden] ?: false,
            confirmDelete = p[Keys.confirmDelete] ?: true
        )
    }

    suspend fun setOnboardingDone() { context.dataStore.edit { it[Keys.onboarding] = true } }
    suspend fun setTheme(v: ThemeMode) { context.dataStore.edit { it[Keys.theme] = v.name } }
    suspend fun setSort(v: SortOrder) { context.dataStore.edit { it[Keys.sort] = v.name } }
    suspend fun setShowHidden(v: Boolean) { context.dataStore.edit { it[Keys.hidden] = v } }
    suspend fun setConfirmDelete(v: Boolean) { context.dataStore.edit { it[Keys.confirmDelete] = v } }
}
