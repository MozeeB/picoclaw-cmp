package com.mozeeb.picoclaw.cmp.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/** DataStore-backed AppSettings for Desktop (JVM). */
class DataStoreSettings(private val dataStore: DataStore<Preferences>) : AppSettings {
    override suspend fun getString(key: String, default: String): String =
        dataStore.data.first()[stringPreferencesKey(key)] ?: default

    override suspend fun getInt(key: String, default: Int): Int =
        dataStore.data.first()[intPreferencesKey(key)] ?: default

    override suspend fun getBoolean(key: String, default: Boolean): Boolean =
        dataStore.data.first()[booleanPreferencesKey(key)] ?: default

    override suspend fun putString(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    override suspend fun putInt(key: String, value: Int) {
        dataStore.edit { it[intPreferencesKey(key)] = value }
    }

    override suspend fun putBoolean(key: String, value: Boolean) {
        dataStore.edit { it[booleanPreferencesKey(key)] = value }
    }
}
