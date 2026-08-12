package dev.vaultlink.core.auth

/** Extensible interface: each auth method (OIDC, LDAP, Token, Userpass) implements this. */
interface VaultAuthStrategy {
    val method: AuthMethod
    fun authenticate(): AuthResult
    fun renew(clientToken: String): AuthResult
    fun supportsRenewal(): Boolean
}
