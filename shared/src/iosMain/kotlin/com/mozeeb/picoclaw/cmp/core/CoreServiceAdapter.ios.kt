package com.mozeeb.picoclaw.cmp.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** iOS stub — PicoClaw binary execution is not supported on iOS. */
class IosCoreServiceAdapter : CoreServiceAdapter {

    private val _logFlow = MutableSharedFlow<String>(extraBufferCapacity = 100)
    override val logFlow: Flow<String> = _logFlow

    override suspend fun validateBinary(customPath: String): BinaryValidation {
        println("WARN: validateBinary() not supported on iOS")
        return BinaryValidation.NotFound(listOf("iOS — binary execution not supported"))
    }

    override suspend fun start(host: String, port: Int, path: String, binaryPath: String, extraArgs: String) {
        println("WARN: CoreServiceAdapter.start() not supported on iOS")
        _logFlow.emit("WARN: Service execution is not supported on iOS.")
        throw BinaryNotFoundException(listOf("iOS — binary execution not supported"))
    }

    override suspend fun stop() {
        println("WARN: CoreServiceAdapter.stop() not supported on iOS")
    }

    override suspend fun exportLogs(logs: List<String>) {
        println("WARN: CoreServiceAdapter.exportLogs() not supported on iOS")
    }

    override suspend fun getDeviceIpAddress(): String? {
        println("WARN: CoreServiceAdapter.getDeviceIpAddress() not supported on iOS")
        return null
    }
}
