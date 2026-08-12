package dev.vaultlink.core.vault.model

data class VaultSecretData(
    val data: Map<String, String>,
    val metadata: VaultSecretVersion,
)
