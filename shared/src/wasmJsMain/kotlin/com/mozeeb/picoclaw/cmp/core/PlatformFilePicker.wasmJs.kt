package com.mozeeb.picoclaw.cmp.core

/** WasmJS/Browser file picker stub — binary execution is not supported in the browser. */
actual suspend fun pickBinaryFile(): String? {
    println("WARN: pickBinaryFile() not supported on WasmJS/Web")
    return null
}
