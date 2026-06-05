package com.mozeeb.picoclaw.cmp.di

import com.mozeeb.picoclaw.cmp.core.AppSettings
import com.mozeeb.picoclaw.cmp.core.BinaryDownloader
import com.mozeeb.picoclaw.cmp.core.CoreServiceAdapter
import com.mozeeb.picoclaw.cmp.core.InMemorySettings
import com.mozeeb.picoclaw.cmp.core.WasmJsBinaryDownloader
import com.mozeeb.picoclaw.cmp.core.WasmJsCoreServiceAdapter
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<CoreServiceAdapter> { WasmJsCoreServiceAdapter() }
    single<AppSettings> { InMemorySettings() }
    single<BinaryDownloader> { WasmJsBinaryDownloader() }
}
