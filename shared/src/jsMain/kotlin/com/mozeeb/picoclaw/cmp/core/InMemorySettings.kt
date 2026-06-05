package com.mozeeb.picoclaw.cmp.core

/**
 * In-memory AppSettings for JS/Browser — ephemeral, not persisted across page reloads.
 * Sufficient for the browser target where the service can't run anyway.
 */
class InMemorySettings : AppSettings {
    private val store = mutableMapOf<String, Any>()

    override suspend fun getString(key: String, default: String) =
        store[key] as? String ?: default

    override suspend fun getInt(key: String, default: Int) =
        store[key] as? Int ?: default

    override suspend fun getBoolean(key: String, default: Boolean) =
        store[key] as? Boolean ?: default

    override suspend fun putString(key: String, value: String) { store[key] = value }
    override suspend fun putInt(key: String, value: Int) { store[key] = value }
    override suspend fun putBoolean(key: String, value: Boolean) { store[key] = value }
}
