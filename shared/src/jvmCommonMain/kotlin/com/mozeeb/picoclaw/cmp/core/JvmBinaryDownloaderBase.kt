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
 * The PicoClaw release archive ships TWO executables: `picoclaw` (the agent/gateway Cobra CLI)
 * and `picoclaw-launcher` (the web-console launcher that serves the management UI on the port).
 * Both are installed side by side; the launcher is returned as the primary binary to run.
 */
abstract class JvmBinaryDownloaderBase : BinaryDownloader {

    override val isSupported: Boolean = true

    /** GitHub asset platform token: "darwin" | "windows" | "linux" | "android". */
    protected abstract val platformToken: String

    /** CPU architecture token: "x86_64" | "arm64". */
    protected abstract val arch: String

    /**
     * Directory to install the extracted executables into.
     * The gateway + launcher live side by side (the launcher spawns the gateway).
     */
    protected abstract fun installDir(): File

    /** Mark the installed file executable (chmod +x). No-op on Windows. */
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

                    // 3. Extract ALL picoclaw executables (gateway + launcher)
                    val executables = extractExecutables(tempArchive, asset.name)
                    if (executables.isEmpty()) {
                        return@withContext DownloadResult.Failure(
                            "Downloaded ${asset.name} but found no picoclaw executable inside."
                        )
                    }

                    // 4. Install every executable into the bin dir + chmod
                    val dir = installDir().also { it.mkdirs() }
                    executables.forEach { (name, bytes) ->
                        val file = File(dir, name)
                        file.writeBytes(bytes)
                        markExecutable(file)
                    }
                    onProgress(1f)

                    // 5. Return the launcher (serves the web UI), falling back to the core binary
                    val primary = pickPrimaryBinary(dir, executables.keys)
                    DownloadResult.Success(primary.absolutePath)
                } finally {
                    tempArchive.delete()
                }
            } catch (e: Exception) {
                DownloadResult.Failure(e.message ?: "Download failed (${e::class.simpleName})")
            }
        }

    /**
     * Pick which installed binary to run. The web-console binary (launcher / -web) is preferred —
     * the bare `picoclaw` gateway is a Cobra CLI and does NOT serve the web UI (running it with
     * `-port` fails with `unknown command`). Handles both desktop names (picoclaw-launcher) and
     * Android names (libpicoclaw-web.so).
     */
    private fun pickPrimaryBinary(dir: File, names: Set<String>): File {
        fun find(pred: (String) -> Boolean) = names.firstOrNull { pred(it.lowercase()) }
        val chosen = find { it.contains("launcher") }
            ?: find { it.contains("web") }
            ?: find { it.contains("picoclaw") }
            ?: names.first()
        return File(dir, chosen)
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
    // Archive extraction — zip + tar.gz (returns basename -> bytes)
    // -------------------------------------------------------------------------

    private fun extractExecutables(archive: File, assetName: String): Map<String, ByteArray> {
        val lower = assetName.lowercase()
        return when {
            lower.endsWith(".zip") -> extractFromZip(archive)
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") ->
                GZIPInputStream(archive.inputStream().buffered()).use { extractFromTar(it) }
            else -> {
                val base = assetName.substringAfterLast('/').ifBlank { "picoclaw" }
                mapOf(base to archive.readBytes())
            }
        }
    }

    private fun extractFromZip(archive: File): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        val chosenPath = HashMap<String, String>() // basename -> path we kept
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val path = entry.name
                val base = path.substringAfterLast('/').substringAfterLast('\\')
                val bytes = zip.readBytes()
                if (isExecutableCandidate(base) && shouldKeep(chosenPath[base], path)) {
                    out[base] = bytes
                    chosenPath[base] = path
                }
            }
        }
        return out
    }

    /** Minimal POSIX tar reader (ustar) — sufficient for release archives. */
    private fun extractFromTar(input: InputStream): Map<String, ByteArray> {
        val stream = BufferedInputStream(input)
        val header = ByteArray(512)
        val out = LinkedHashMap<String, ByteArray>()
        val chosenPath = HashMap<String, String>()

        while (true) {
            if (!readFully(stream, header)) break
            // Empty 512-byte block marks the end of the archive
            if (header.all { it == 0.toByte() }) break

            val name = String(header, 0, 100, Charsets.UTF_8).substringBefore(' ').trim()
            val sizeOctal = String(header, 124, 12, Charsets.UTF_8).substringBefore(' ').trim()
            val size = sizeOctal.toLongOrNull(8) ?: 0L
            val typeFlag = header[156].toInt().toChar()

            // Read the file body, padded to a 512-byte boundary
            val bodySize = size.toInt().coerceAtLeast(0)
            val body = ByteArray(bodySize)
            if (bodySize > 0 && !readFully(stream, body)) break
            val padding = ((512 - (bodySize % 512)) % 512)
            if (padding > 0) skipFully(stream, padding)

            // Regular file? typeFlag '0' (or NUL for old archives)
            if (typeFlag == '0' || typeFlag == ' ') {
                val base = name.substringAfterLast('/').substringAfterLast('\\')
                if (isExecutableCandidate(base) && shouldKeep(chosenPath[base], name)) {
                    out[base] = body
                    chosenPath[base] = name
                }
            }
        }
        return out
    }

    /**
     * True for picoclaw executables; excludes text/metadata files. Matches both desktop names
     * (`picoclaw`, `picoclaw-launcher`) and Android names (`libpicoclaw.so`, `libpicoclaw-web.so`).
     */
    private fun isExecutableCandidate(baseName: String): Boolean {
        val base = baseName.lowercase()
        if (base.isEmpty()) return false
        if (base.endsWith(".txt") || base.endsWith(".md") || base.endsWith(".json") ||
            base.endsWith(".sha256") || base.endsWith(".sig") || base.endsWith(".yaml") ||
            base.endsWith(".yml") || base.endsWith(".toml")
        ) return false
        return base.contains("picoclaw")
    }

    /**
     * For "universal" archives that carry the same basename under multiple arch directories,
     * keep the copy whose path matches the device arch (replace a non-matching earlier pick).
     */
    private fun shouldKeep(currentPath: String?, candidatePath: String): Boolean {
        if (currentPath == null) return true
        return !pathMatchesArch(currentPath) && pathMatchesArch(candidatePath)
    }

    private fun pathMatchesArch(path: String): Boolean {
        val p = path.lowercase()
        return when (arch) {
            "arm64" -> p.contains("arm64") || p.contains("aarch64")
            "x86_64" -> p.contains("x86_64") || p.contains("amd64") || p.contains("x64")
            else -> false
        }
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
