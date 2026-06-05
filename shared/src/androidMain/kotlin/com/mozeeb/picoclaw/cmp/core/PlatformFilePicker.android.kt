package com.mozeeb.picoclaw.cmp.core

import android.util.Log

/**
 * Android file picker stub.
 *
 * Android requires an Activity + ActivityResult launcher (Storage Access Framework)
 * to pick files, which can't be done from the shared module. On Android the binary
 * is obtained via [BinaryDownloader] or bundled in jniLibs/ — not manual browsing.
 *
 * Mirrors Flutter, which also hides the "Browse" button on Android.
 */
actual suspend fun pickBinaryFile(): String? {
    Log.w("PicoClawFilePicker", "pickBinaryFile() not supported on Android — use Download instead")
    return null
}
