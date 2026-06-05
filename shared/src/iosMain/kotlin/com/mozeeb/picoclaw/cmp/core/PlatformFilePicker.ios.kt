package com.mozeeb.picoclaw.cmp.core

/** iOS file picker stub — binary execution is not supported on iOS. */
actual suspend fun pickBinaryFile(): String? {
    println("WARN: pickBinaryFile() not supported on iOS")
    return null
}
