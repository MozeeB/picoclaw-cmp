package com.mozeeb.picoclaw.cmp.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** JS/Browser stub — binary execution is not supported in the browser. */
class JsCoreServiceAdapter : CoreServiceAdapter {

    private val _logFlow = MutableSharedFlow<String>(extraBufferCapacity = 100)
    override val logFlow: Flow<String> = _logFlow

    override suspend fun validateBinary(customPath: String): BinaryValidation {
        console.warn("WARN: validateBinary() not supported on JS/Web")
        return BinaryValidation.NotFound(listOf("JS/Web — binary execution not supported"))
    }

    override suspend fun start(host: String, port: Int, path: String, binaryPath: String, extraArgs: String) {
        console.warn("WARN: CoreServiceAdapter.start() not supported on JS/Web")
        _logFlow.emit("WARN: Service execution is not supported in the browser.")
        throw BinaryNotFoundException(listOf("JS/Web — binary execution not supported"))
    }

    override suspend fun stop() {
        console.warn("WARN: CoreServiceAdapter.stop() not supported on JS/Web")
    }

    override suspend fun exportLogs(logs: List<String>) {
        console.warn("WARN: CoreServiceAdapter.exportLogs() not supported on JS/Web")
    }

    override suspend fun getDeviceIpAddress(): String? {
        console.warn("WARN: CoreServiceAdapter.getDeviceIpAddress() not supported on JS/Web")
        return null
    }
}
