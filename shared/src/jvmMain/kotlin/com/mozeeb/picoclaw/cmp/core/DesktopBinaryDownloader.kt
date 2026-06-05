package com.mozeeb.picoclaw.cmp.core

import java.io.File

/**
 * Desktop (JVM) binary downloader.
 *
 * Installs to `~/.picoclaw/bin/picoclaw[.exe]` — the first location the
 * [DesktopCoreServiceAdapter] checks during binary resolution.
 */
class DesktopBinaryDownloader : JvmBinaryDownloaderBase() {

    private val osName = System.getProperty("os.name", "").lowercase()
    private val isWindows = osName.contains("win")

    override val platformToken: String = when {
        isWindows -> "windows"
        osName.contains("mac") || osName.contains("darwin") -> "darwin"
        else -> "linux"
    }

    override val arch: String = run {
        val a = System.getProperty("os.arch", "").lowercase()
        when {
            a.contains("aarch64") || a.contains("arm64") -> "arm64"
            a.contains("amd64") || a.contains("x86_64") || a.contains("x64") -> "x86_64"
            else -> "x86_64"
        }
    }

    override fun installTargetFile(): File {
        val home = System.getProperty("user.home") ?: "."
        val binaryName = if (isWindows) "picoclaw.exe" else "picoclaw"
        return File(home, ".picoclaw/bin/$binaryName")
    }

    override fun markExecutable(file: File) {
        if (!isWindows) {
            // chmod +x equivalent
            file.setExecutable(true, false)
            file.setReadable(true, false)
        }
    }
}
