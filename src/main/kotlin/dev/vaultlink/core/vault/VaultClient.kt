package dev.vaultlink.core.vault

import dev.vaultlink.core.vault.model.VaultMount
import dev.vaultlink.core.vault.model.VaultSecretData
import dev.vaultlink.core.vault.model.VaultSecretMetadata

interface VaultClient {
    fun readSecret(mount: String, path: String, version: Int? = null): VaultSecretData
    fun readMetadata(mount: String, path: String): VaultSecretMetadata
    fun health(): Boolean

    /** KV v2 secrets engine mounts only (type=kv, options.version=2) — the only kind this plugin reads. */
    fun listMounts(): List<VaultMount>

    /** Direct children of [path] within [mount] ("" = mount root). Entries ending in "/" are subfolders. */
    fun listSecrets(mount: String, path: String = ""): List<String>
}
