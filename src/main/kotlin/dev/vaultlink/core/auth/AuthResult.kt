package dev.vaultlink.core.auth

import java.time.Instant

data class AuthResult(
    val clientToken: String,
    val leaseDuration: Long,
    val renewable: Boolean,
    val issuedAt: Instant,
)
