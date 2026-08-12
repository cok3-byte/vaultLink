package dev.vaultlink.core.cache

import dev.vaultlink.core.vault.model.VaultSecretData
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** ConcurrentHashMap per project, configurable TTL, no disk. Invalidated when the project closes. */
class InMemorySecretCache(private val ttlMinutes: Long) {
    private data class Entry(val data: VaultSecretData, val expiresAt: Instant)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun get(key: String): VaultSecretData? {
        val entry = entries[key] ?: return null
        if (Instant.now().isAfter(entry.expiresAt)) {
            entries.remove(key)
            return null
        }
        return entry.data
    }

    fun put(key: String, data: VaultSecretData) {
        entries[key] = Entry(data, Instant.now().plusSeconds(ttlMinutes * 60))
    }

    fun invalidateAll() {
        entries.clear()
    }
}
