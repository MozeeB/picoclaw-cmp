package com.mozeeb.picoclaw.cmp.di

import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(platformModule(), appModule)
    }
}
