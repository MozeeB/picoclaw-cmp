package com.mozeeb.picoclaw.cmp.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File

// Singleton context holder — set in PicoClawApp.onCreate() before Koin starts
internal object AndroidContext {
    lateinit var appContext: Context
}

internal fun createDataStore(): DataStore<Preferences> {
    val file = File(AndroidContext.appContext.filesDir, "picoclaw_settings.preferences_pb")
    return PreferenceDataStoreFactory.create(
        produceFile = { file },
    )
}
