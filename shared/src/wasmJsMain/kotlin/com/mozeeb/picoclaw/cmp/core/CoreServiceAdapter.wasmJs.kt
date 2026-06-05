package com.mozeeb.picoclaw.cmp.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** WasmJS/Browser stub — binary execution is not supported in the browser. */
class WasmJsCoreServiceAdapter : CoreServiceAdapter {

    private val _logFlow = MutableSharedFlow<String>(extraBufferCapacity = 100)
    override val logFlow: Flow<String> = _logFlow

    override suspend fun validateBinary(customPath: String): BinaryValidation {
        println("WARN: validateBinary() not supported on WasmJS/Web")
        return BinaryValidation.NotFound(listOf("WasmJS/Web — binary execution not supported"))
    }

    override suspend fun start(host: String, port: Int, path: String, binaryPath: String, extraArgs: String) {
        println("WARN: CoreServiceAdapter.start() not supported on WasmJS/Web")
        _logFlow.emit("WARN: Service execution is not supported in the browser.")
        throw BinaryNotFoundException(listOf("WasmJS/Web — binary execution not supported"))
    }

    override suspend fun stop() {
        println("WARN: CoreServiceAdapter.stop() not supported on WasmJS/Web")
    }

    override suspend fun exportLogs(logs: List<String>) {
        println("WARN: CoreServiceAdapter.exportLogs() not supported on WasmJS/Web")
    }

    override suspend fun getDeviceIpAddress(): String? {
        println("WARN: CoreServiceAdapter.getDeviceIpAddress() not supported on WasmJS/Web")
        return null
    }
}
