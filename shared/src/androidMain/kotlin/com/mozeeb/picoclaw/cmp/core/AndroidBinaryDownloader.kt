package com.mozeeb.picoclaw.cmp.core

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Android binary downloader.
 *
 * Installs to `filesDir/libpicoclaw.so` — the location the
 * [AndroidCoreServiceAdapter] checks after nativeLibraryDir.
 */
class AndroidBinaryDownloader(
    private val context: Context,
) : JvmBinaryDownloaderBase() {

    override val platformToken: String = "android"

    override val arch: String = run {
        val abi = Build.SUPPORTED_ABIS.firstOrNull()?.lowercase() ?: "arm64-v8a"
        when {
            abi.contains("arm64") || abi.contains("aarch64") -> "arm64"
            abi.contains("x86_64") -> "x86_64"
            else -> "arm64"
        }
    }

    override fun installTargetFile(): File =
        context.filesDir.resolve(AndroidCoreServiceAdapter.BINARY_NAME) // libpicoclaw.so

    override fun markExecutable(file: File) {
        file.setExecutable(true, false)
        file.setReadable(true, false)
    }
}
