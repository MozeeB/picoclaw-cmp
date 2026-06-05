package com.mozeeb.picoclaw.cmp.di

import org.koin.core.Koin
import org.koin.core.context.startKoin

/**
 * Initializes Koin for the Desktop (JVM) target.
 * Call this once at application startup before rendering the first composable.
 *
 * @return the started [Koin] instance so the desktop entry point can resolve
 *         singletons (e.g. to construct a shared ServiceViewModel for the tray + UI).
 */
fun initKoin(): Koin =
    startKoin {
        modules(platformModule(), appModule)
    }.koin
