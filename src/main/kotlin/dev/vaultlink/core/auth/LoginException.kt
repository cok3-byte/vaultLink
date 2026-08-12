package dev.vaultlink.core.auth

/**
 * Taxonomy of login/authentication failures (OIDC, LDAP, Userpass, Token) — "success" is the
 * absence of an exception ([AuthResult] returned normally). Used so the UI shows a distinct,
 * appropriate message per case instead of a raw exception string.
 */
sealed class LoginException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Vault rejected the credentials/role/token (400/401/403). */
    class Failed(message: String) : LoginException(message)

    /** The wait timed out (network or the OIDC browser flow). */
    class TimedOut(message: String, cause: Throwable? = null) : LoginException(message, cause)

    /** Could not reach Vault (5xx, DNS, connection refused, etc.). */
    class ConnectionError(message: String, cause: Throwable? = null) : LoginException(message, cause)

    /** The user closed/cancelled the login dialog. */
    class Cancelled(message: String) : LoginException(message)

    /** Any other case not covered above. */
    class Unexpected(message: String, cause: Throwable? = null) : LoginException(message, cause)
}
