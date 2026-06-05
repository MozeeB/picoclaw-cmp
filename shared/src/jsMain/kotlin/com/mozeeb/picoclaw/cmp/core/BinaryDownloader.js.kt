package com.mozeeb.picoclaw.cmp.core

/** JS/Browser stub — runtime binary download is not supported. */
class JsBinaryDownloader : BinaryDownloader {
    override val isSupported: Boolean = false
    override suspend fun downloadLatest(onProgress: (Float) -> Unit): DownloadResult {
        console.warn("WARN: BinaryDownloader not supported on JS/Web")
        return DownloadResult.Unsupported
    }
}
