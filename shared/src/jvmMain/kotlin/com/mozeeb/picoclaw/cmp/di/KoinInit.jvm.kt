package com.mozeeb.picoclaw.cmp.di

import org.koin.core.context.startKoin

/**
 * Initializes Koin for the Desktop (JVM) target.
 * Call this once at application startup before rendering the first composable.
 */
fun initKoin() {
    startKoin {
        modules(platformModule(), appModule)
    }
}
