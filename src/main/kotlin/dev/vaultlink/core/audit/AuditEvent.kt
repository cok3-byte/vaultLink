package dev.vaultlink.core.audit

import java.time.Instant

/** Structurally has no field for the secret's value: an accidental log can never expose it. */
data class AuditEvent(
    val timestamp: Instant,
    val osUser: String,
    val mount: String,
    val secretPath: String,
    val version: Int?,
    val result: AuditResult,
)

enum class AuditResult {
    SUCCESS,
    AUTH_FAILED,
    PERMISSION_DENIED,
    NOT_FOUND,
    NETWORK_ERROR,
}
