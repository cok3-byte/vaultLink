package dev.vaultlink.core.vault.model

data class VaultSecretVersion(
    val version: Int,
    val createdTime: String,
    val deletionTime: String?,
    val destroyed: Boolean,
)
