package com.mozeeb.picoclaw.cmp.di

import com.mozeeb.picoclaw.cmp.core.Analytics
import com.mozeeb.picoclaw.cmp.core.AndroidAnalytics
import com.mozeeb.picoclaw.cmp.core.AndroidBinaryDownloader
import com.mozeeb.picoclaw.cmp.core.AndroidCoreServiceAdapter
import com.mozeeb.picoclaw.cmp.core.AppSettings
import com.mozeeb.picoclaw.cmp.core.BinaryDownloader
import com.mozeeb.picoclaw.cmp.core.CoreServiceAdapter
import com.mozeeb.picoclaw.cmp.core.DataStoreSettings
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<CoreServiceAdapter> { AndroidCoreServiceAdapter(androidContext()) }
    single<AppSettings> { DataStoreSettings(createDataStore()) }
    single<BinaryDownloader> { AndroidBinaryDownloader(androidContext()) }
    single<Analytics> { AndroidAnalytics() }
}
