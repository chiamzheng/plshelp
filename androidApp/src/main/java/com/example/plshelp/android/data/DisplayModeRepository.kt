package com.example.plshelp.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.plshelp.android.ui.screens.DisplayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "display_mode_prefs")

class DisplayModeRepository(private val context: Context) {

    private val DISPLAY_MODE_KEY = stringPreferencesKey("display_mode")

    val displayModeFlow: Flow<DisplayMode> = context.dataStore.data
        .map { preferences ->
            val displayModeString = preferences[DISPLAY_MODE_KEY] ?: DisplayMode.DISTANCE.name
            DisplayMode.valueOf(displayModeString)
        }

    suspend fun saveDisplayMode(displayMode: DisplayMode) {
        context.dataStore.edit { preferences ->
            preferences[DISPLAY_MODE_KEY] = displayMode.name
        }
    }
}
