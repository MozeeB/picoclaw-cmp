package com.mozeeb.picoclaw.cmp.core

/** iOS stub — runtime binary download is not supported (no binary execution on iOS). */
class IosBinaryDownloader : BinaryDownloader {
    override val isSupported: Boolean = false
    override suspend fun downloadLatest(onProgress: (Float) -> Unit): DownloadResult {
        println("WARN: BinaryDownloader not supported on iOS")
        return DownloadResult.Unsupported
    }
}
