package com.example.todoapp.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

const val DEFAULT_DAILY_LIMIT = 10

@Singleton
class PreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val dailyLimitKey = intPreferencesKey("daily_limit")
    private val lastResetDateKey = stringPreferencesKey("last_reset_date")

    val dailyLimit: Flow<Int> = dataStore.data.map { prefs ->
        prefs[dailyLimitKey] ?: DEFAULT_DAILY_LIMIT
    }

    suspend fun setDailyLimit(limit: Int) {
        dataStore.edit { prefs ->
            prefs[dailyLimitKey] = limit
        }
    }

    val lastResetDate: Flow<String?> = dataStore.data.map { prefs ->
        prefs[lastResetDateKey]
    }

    suspend fun setLastResetDate(date: String) {
        dataStore.edit { prefs ->
            prefs[lastResetDateKey] = date
        }
    }
}
