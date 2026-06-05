package com.mozeeb.picoclaw.cmp.di

import com.mozeeb.picoclaw.cmp.core.SettingsRepository
import com.mozeeb.picoclaw.cmp.mvi.ServiceViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Common Koin module — platform-agnostic bindings.
 * [AppSettings] and [CoreServiceAdapter] are provided by [platformModule].
 *
 * Entry points call:
 * ```kotlin
 * startKoin { modules(platformModule(), appModule) }
 * ```
 *
 * Then [App] retrieves [ServiceViewModel] via `koinViewModel()` (from koin-compose-viewmodel).
 */
val appModule = module {
    single { SettingsRepository(get()) }
    viewModel { ServiceViewModel(get(), get(), get()) }
}
