package com.mozeeb.picoclaw.cmp.core

/** JS/Browser file picker stub — binary execution is not supported in the browser. */
actual suspend fun pickBinaryFile(): String? {
    console.warn("WARN: pickBinaryFile() not supported on JS/Web")
    return null
}
