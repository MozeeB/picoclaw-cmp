package com.mozeeb.picoclaw.cmp.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath
import java.io.File

internal fun createDataStore(): DataStore<Preferences> {
    val storeDir = File(System.getProperty("user.home") ?: ".", ".picoclaw").also { it.mkdirs() }
    return PreferenceDataStoreFactory.createWithPath {
        storeDir.resolve("picoclaw_settings.preferences_pb").absolutePath.toPath()
    }
}
