package dev.vaultlink.core.vault

import dev.vaultlink.core.vault.model.VaultSecretData
import dev.vaultlink.core.vault.model.VaultSecretMetadata

interface VaultClient {
    fun readSecret(mount: String, path: String, version: Int? = null): VaultSecretData
    fun readMetadata(mount: String, path: String): VaultSecretMetadata
    fun health(): Boolean
}
