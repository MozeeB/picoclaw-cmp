package com.mozeeb.picoclaw.cmp.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * Shared (Desktop + Android) implementation of [BinaryDownloader].
 *
 * Uses only JDK APIs (java.net.HttpURLConnection, java.util.zip) + kotlinx.serialization —
 * no third-party HTTP/download/archive library.
 *
 * Subclasses supply the platform-specific asset token, arch, and install target.
 */
abstract class JvmBinaryDownloaderBase : BinaryDownloader {

    override val isSupported: Boolean = true

    /** GitHub asset platform token: "darwin" | "windows" | "linux" | "android". */
    protected abstract val platformToken: String

    /** CPU architecture token: "x86_64" | "arm64". */
    protected abstract val arch: String

    /** Where to install the extracted binary, and how to name it. */
    protected abstract fun installTargetFile(): File

    /** Mark the installed file executable (chmod +x). */
    protected abstract fun markExecutable(file: File)

    private val json = Json { ignoreUnknownKeys = true }

    // -------------------------------------------------------------------------
    // Orchestration
    // -------------------------------------------------------------------------

    override suspend fun downloadLatest(onProgress: (Float) -> Unit): DownloadResult =
        withContext(Dispatchers.IO) {
            try {
                onProgress(0f)

                // 1. Resolve the best matching asset from the latest release
                val asset = resolveAsset()
                    ?: return@withContext DownloadResult.Failure(
                        "No matching release asset for $platformToken/$arch in $PICOCLAW_GITHUB_REPO."
                    )

                // 2. Download the archive to a temp file (with progress)
                val tempArchive = File.createTempFile("picoclaw_dl", suffixFor(asset.name))
                try {
                    downloadFile(asset.url, tempArchive, onProgress)

                    // 3. Extract the binary bytes from the archive
                    val binaryBytes = extractBinaryBytes(tempArchive, asset.name)
                        ?: return@withContext DownloadResult.Failure(
                            "Downloaded ${asset.name} but no executable was found inside."
                        )

                    // 4. Install to the platform target + chmod
                    val target = installTargetFile()
                    target.parentFile?.mkdirs()
                    target.writeBytes(binaryBytes)
                    markExecutable(target)
                    onProgress(1f)

                    DownloadResult.Success(target.absolutePath)
                } finally {
                    tempArchive.delete()
                }
            } catch (e: Exception) {
                DownloadResult.Failure(e.message ?: "Download failed (${e::class.simpleName})")
            }
        }

    // -------------------------------------------------------------------------
    // GitHub release resolution (mirrors fetch_core_local.dart selectBestAsset)
    // -------------------------------------------------------------------------

    private data class Asset(val name: String, val url: String)

    private fun resolveAsset(): Asset? {
        val releaseJson = fetchReleaseJson()
        val assets = json.parseToJsonElement(releaseJson)
            .jsonObject["assets"]?.jsonArray ?: return null

        data class Scored(val name: String, val url: String, val score: Int)

        val archLower = arch.lowercase()
        val scored = assets.mapNotNull { el ->
            val obj = el.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val url = obj["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val lower = name.lowercase()
            var score = 0
            if (lower.endsWith(".zip")) score += 1
            if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) score += 1
            if (lower.contains("_${platformToken}_") ||
                (lower.startsWith("picoclaw") && lower.contains(platformToken))
            ) score += 8
            if (lower.contains(archLower)) score += 4
            if (score > 0) Scored(name, url, score) else null
        }.sortedByDescending { it.score }

        // Require a platform-token match to avoid installing the wrong OS binary
        val best = scored.firstOrNull { it.name.lowercase().contains(platformToken) }
            ?: return null
        return Asset(best.name, best.url)
    }

    private fun fetchReleaseJson(): String {
        val api = URI("https://api.github.com/repos/$PICOCLAW_GITHUB_REPO/releases/latest").toURL()
        val conn = (api.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            // GitHub API requires a User-Agent or returns 403
            setRequestProperty("User-Agent", "PicoClaw-CMP")
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            if (conn.responseCode != 200) {
                throw RuntimeException("GitHub API returned HTTP ${conn.responseCode}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // -------------------------------------------------------------------------
    // Download with progress
    // -------------------------------------------------------------------------

    private fun downloadFile(url: String, dest: File, onProgress: (Float) -> Unit) {
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "PicoClaw-CMP")
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 60_000
        }
        try {
            if (conn.responseCode !in 200..299) {
                throw RuntimeException("Download failed: HTTP ${conn.responseCode}")
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    // -------------------------------------------------------------------------
    // Archive extraction — zip + tar.gz
    // -------------------------------------------------------------------------

    /** Extract the picoclaw executable's raw bytes from [archive]. Returns null if not found. */
    private fun extractBinaryBytes(archive: File, assetName: String): ByteArray? {
        val lower = assetName.lowercase()
        return when {
            lower.endsWith(".zip") -> extractFromZip(archive)
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") ->
                GZIPInputStream(archive.inputStream().buffered()).use { extractFromTar(it) }
            else -> archive.readBytes() // raw binary asset (no archive)
        }
    }

    private fun extractFromZip(archive: File): ByteArray? {
        var best: ByteArray? = null
        var bestScore = -1
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val bytes = zip.readBytes()
                val score = scoreCandidate(entry.name, bytes.size.toLong())
                if (score > bestScore) {
                    bestScore = score
                    best = bytes
                }
            }
        }
        return if (bestScore > 0) best else null
    }

    /** Minimal POSIX tar reader (ustar) — sufficient for release archives. */
    private fun extractFromTar(input: InputStream): ByteArray? {
        val stream = BufferedInputStream(input)
        val header = ByteArray(512)
        var best: ByteArray? = null
        var bestScore = -1

        while (true) {
            if (!readFully(stream, header)) break
            // Empty block marks end of archive
            if (header.all { it == 0.toByte() }) break

            val name = String(header, 0, 100, Charsets.UTF_8).trimEnd(' ', ' ')
            val sizeOctal = String(header, 124, 12, Charsets.UTF_8).trim().trimEnd(' ', ' ')
            val size = sizeOctal.toLongOrNull(8) ?: 0L
            val typeFlag = header[156].toInt().toChar()

            // Read the file body, padded to a 512-byte boundary
            val bodySize = size.toInt().coerceAtLeast(0)
            val body = ByteArray(bodySize)
            if (bodySize > 0 && !readFully(stream, body)) break
            val padding = ((512 - (bodySize % 512)) % 512)
            if (padding > 0) skipFully(stream, padding)

            // Regular file?  typeFlag '0' or NUL
            if (typeFlag == '0' || typeFlag == ' ') {
                val score = scoreCandidate(name, size)
                if (score > bestScore) {
                    bestScore = score
                    best = body
                }
            }
        }
        return if (bestScore > 0) best else null
    }

    /** Score a candidate archive entry — higher = more likely the picoclaw binary. */
    private fun scoreCandidate(entryName: String, size: Long): Int {
        val base = entryName.substringAfterLast('/').substringAfterLast('\\').lowercase()
        // Exclude obvious non-binaries
        if (base.endsWith(".txt") || base.endsWith(".md") || base.endsWith(".json") ||
            base.endsWith(".sha256") || base.endsWith(".sig") || base.isEmpty()
        ) return -1

        var score = 0
        if (base == "picoclaw" || base == "picoclaw.exe" || base == "libpicoclaw.so") score += 100
        else if (base.startsWith("picoclaw")) score += 50
        // Prefer larger files (the binary is bigger than helper scripts)
        if (size > 100_000) score += 10
        if (size > 1_000_000) score += 10
        return if (score > 0) score else 1 // fall back: any file is a weak candidate
    }

    // -------------------------------------------------------------------------
    // Stream helpers
    // -------------------------------------------------------------------------

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) return false
            offset += read
        }
        return true
    }

    private fun skipFully(input: InputStream, count: Int) {
        var remaining = count.toLong()
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() < 0) break
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun suffixFor(assetName: String): String = when {
        assetName.endsWith(".tar.gz") -> ".tar.gz"
        assetName.endsWith(".tgz") -> ".tgz"
        assetName.endsWith(".zip") -> ".zip"
        else -> ".bin"
    }
}
