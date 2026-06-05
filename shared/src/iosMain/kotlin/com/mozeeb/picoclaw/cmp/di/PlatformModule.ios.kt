package com.mozeeb.picoclaw.cmp.di

import com.mozeeb.picoclaw.cmp.core.AppSettings
import com.mozeeb.picoclaw.cmp.core.BinaryDownloader
import com.mozeeb.picoclaw.cmp.core.CoreServiceAdapter
import com.mozeeb.picoclaw.cmp.core.DataStoreSettings
import com.mozeeb.picoclaw.cmp.core.IosBinaryDownloader
import com.mozeeb.picoclaw.cmp.core.IosCoreServiceAdapter
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<CoreServiceAdapter> { IosCoreServiceAdapter() }
    single<AppSettings> { DataStoreSettings(createDataStore()) }
    single<BinaryDownloader> { IosBinaryDownloader() }
}
