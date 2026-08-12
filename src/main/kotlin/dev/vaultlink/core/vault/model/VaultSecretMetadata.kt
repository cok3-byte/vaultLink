package dev.vaultlink.core.vault.model

data class VaultSecretMetadata(
    val currentVersion: Int,
    val versions: List<VaultSecretVersion>,
)
