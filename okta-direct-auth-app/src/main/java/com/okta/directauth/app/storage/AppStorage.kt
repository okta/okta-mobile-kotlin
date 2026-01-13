package com.okta.directauth.app.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "directauth_app_prefs")

class AppStorage(private val context: Context) {
    fun get(key: String): Flow<String> {
        val stringKey = stringPreferencesKey(key)
        return context.dataStore.data.map { preferences ->
            preferences[stringKey] ?: ""
        }
    }

    suspend fun save(key: String, value: String) {
        val stringKey = stringPreferencesKey(key)
        context.dataStore.edit { preferences ->
            preferences[stringKey] = value
        }
    }

    suspend fun clear(key: String) {
        val stringKey = stringPreferencesKey(key)
        context.dataStore.edit { preferences ->
            preferences.remove(stringKey)
        }
    }
}