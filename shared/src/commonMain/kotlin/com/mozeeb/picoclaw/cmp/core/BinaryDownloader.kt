package com.mozeeb.picoclaw.cmp.core

/**
 * Downloads the PicoClaw core binary from GitHub releases at runtime.
 *
 * Flutter (`picoclaw_fui`) fetches the binary at BUILD time via tools/fetch_core_local.dart.
 * CMP improves on this by allowing a runtime download on the platforms that can actually
 * execute the binary (Desktop JVM + Android). Browser/iOS targets return [DownloadResult.Unsupported].
 *
 * Implementations use only `java.net.HttpURLConnection` + `kotlinx.serialization` + JDK archive
 * APIs — no third-party HTTP/download library (mirrors the in-house QR-code policy).
 *
 * Resolution mirrors the Flutter tool's asset-selection logic:
 *   repo: sipeed/picoclaw
 *   asset name pattern: picoclaw_<platform>_<arch>.{zip,tar.gz}
 *     platform ∈ {darwin, windows, linux, android}
 *     arch     ∈ {x86_64, arm64}
 */
interface BinaryDownloader {

    /** Whether this platform supports runtime binary download (Desktop + Android only). */
    val isSupported: Boolean

    /**
     * Download the latest core binary, extract it, install it to the platform's
     * binary location, and mark it executable.
     *
     * @param onProgress receives download progress in [0.0, 1.0] (best-effort; may stay at 0 if
     *        the server doesn't report Content-Length).
     * @return [DownloadResult.Success] with the installed absolute path, or a failure.
     */
    suspend fun downloadLatest(onProgress: (Float) -> Unit = {}): DownloadResult
}

/** Outcome of a [BinaryDownloader.downloadLatest] call. */
sealed interface DownloadResult {
    /** Binary downloaded and installed. [installedPath] is the absolute path now executable. */
    data class Success(val installedPath: String) : DownloadResult

    /** Download failed. [message] is a user-friendly explanation. */
    data class Failure(val message: String) : DownloadResult

    /** Runtime download is not supported on this platform (iOS / Web). */
    data object Unsupported : DownloadResult
}

/** GitHub repository that hosts the PicoClaw core releases. */
internal const val PICOCLAW_GITHUB_REPO = "sipeed/picoclaw"
