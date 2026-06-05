package com.mozeeb.picoclaw.cmp.core

/** WasmJS/Browser stub — runtime binary download is not supported. */
class WasmJsBinaryDownloader : BinaryDownloader {
    override val isSupported: Boolean = false
    override suspend fun downloadLatest(onProgress: (Float) -> Unit): DownloadResult {
        println("WARN: BinaryDownloader not supported on WasmJS/Web")
        return DownloadResult.Unsupported
    }
}
