package dev.vaultlink.core.credentials

import dev.vaultlink.core.auth.AuthMethod
import dev.vaultlink.core.auth.AuthResult
import java.time.Instant

/**
 * In-memory session state (never to disk, same spirit as [VaultCredentialsStore]): only
 * metadata (method, when it authenticated, estimated expiry) — never the token's value. Lets
 * the UI (Settings, Tool Window) show whether a session is active without reading/exposing the
 * token itself.
 */
object VaultSessionStatus {

    data class SessionInfo(val method: AuthMethod, val authenticatedAt: Instant, val expiresAt: Instant?)

    @Volatile
    private var current: SessionInfo? = null

    fun update(method: AuthMethod, auth: AuthResult) {
        val expiresAt = if (auth.leaseDuration > 0) auth.issuedAt.plusSeconds(auth.leaseDuration) else null
        current = SessionInfo(method, auth.issuedAt, expiresAt)
    }

    fun clear() {
        current = null
    }

    /** Short text for the UI; never includes the token's value. */
    fun summary(): String {
        val info = current ?: return "No active session"
        return "Active session (${info.method})"
    }

    fun isActive(): Boolean = current != null
}
