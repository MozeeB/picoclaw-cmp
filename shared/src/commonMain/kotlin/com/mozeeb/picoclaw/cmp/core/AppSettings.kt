package com.mozeeb.picoclaw.cmp.core

/**
 * Platform-agnostic settings interface.
 *
 * Implementations:
 * - Android / JVM / iOS: backed by AndroidX DataStore<Preferences>
 * - JS / WasmJS: backed by an in-memory map (ephemeral, sufficient for browser use)
 */
interface AppSettings {
    suspend fun getString(key: String, default: String = ""): String
    suspend fun getInt(key: String, default: Int = 0): Int
    suspend fun getBoolean(key: String, default: Boolean = false): Boolean
    suspend fun putString(key: String, value: String)
    suspend fun putInt(key: String, value: Int)
    suspend fun putBoolean(key: String, value: Boolean)
}
