package dev.vaultlink.core.credentials

import dev.vaultlink.core.auth.AuthMethod
import dev.vaultlink.core.auth.AuthResult
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory session state per Vault profile (never to disk, same spirit as
 * [VaultCredentialsStore]): only metadata (method, when it authenticated, estimated expiry) —
 * never the token's value. Lets the UI (Settings, Tool Window) show whether a profile has an active
 * session without reading/exposing the token itself.
 */
object VaultSessionStatus {

    data class SessionInfo(val method: AuthMethod, val authenticatedAt: Instant, val expiresAt: Instant?)

    private val byProfile = ConcurrentHashMap<String, SessionInfo>()

    fun update(profileId: String, method: AuthMethod, auth: AuthResult) {
        val expiresAt = if (auth.leaseDuration > 0) auth.issuedAt.plusSeconds(auth.leaseDuration) else null
        byProfile[profileId] = SessionInfo(method, auth.issuedAt, expiresAt)
    }

    fun clear(profileId: String) {
        byProfile.remove(profileId)
    }

    /** Short text for the UI; never includes the token's value. */
    fun summary(profileId: String): String {
        val info = byProfile[profileId] ?: return "No active session"
        return "Active session (${info.method})"
    }

    fun isActive(profileId: String): Boolean = byProfile.containsKey(profileId)
}
