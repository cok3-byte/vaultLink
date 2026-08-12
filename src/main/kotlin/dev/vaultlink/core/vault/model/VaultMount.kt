package dev.vaultlink.core.vault.model

/** A KV v2 secrets engine mount, as returned by `GET /v1/sys/mounts` (already filtered to type=kv, version=2). */
data class VaultMount(val path: String)
