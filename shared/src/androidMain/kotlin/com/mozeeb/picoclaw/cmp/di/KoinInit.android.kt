package com.mozeeb.picoclaw.cmp.di

import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Initializes Koin for Android.
 * Call from Application.onCreate() AFTER setting [AndroidContext.appContext].
 */
fun initKoin(context: Context) {
    AndroidContext.appContext = context.applicationContext
    startKoin {
        androidLogger(Level.ERROR)
        androidContext(context)
        modules(platformModule(), appModule)
    }
}
