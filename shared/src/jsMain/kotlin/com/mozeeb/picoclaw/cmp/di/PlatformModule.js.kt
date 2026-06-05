package com.mozeeb.picoclaw.cmp.di

import com.mozeeb.picoclaw.cmp.core.Analytics
import com.mozeeb.picoclaw.cmp.core.AppSettings
import com.mozeeb.picoclaw.cmp.core.BinaryDownloader
import com.mozeeb.picoclaw.cmp.core.CoreServiceAdapter
import com.mozeeb.picoclaw.cmp.core.InMemorySettings
import com.mozeeb.picoclaw.cmp.core.JsBinaryDownloader
import com.mozeeb.picoclaw.cmp.core.JsCoreServiceAdapter
import com.mozeeb.picoclaw.cmp.core.NoOpAnalytics
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<CoreServiceAdapter> { JsCoreServiceAdapter() }
    single<AppSettings> { InMemorySettings() }
    single<BinaryDownloader> { JsBinaryDownloader() }
    single<Analytics> { NoOpAnalytics() }
}
