package dev.vaultlink.core.auth

import dev.vaultlink.core.auth.strategies.LdapAuthStrategy
import dev.vaultlink.core.auth.strategies.OidcAuthStrategy
import dev.vaultlink.core.auth.strategies.TokenAuthStrategy
import dev.vaultlink.core.auth.strategies.UserpassAuthStrategy
import java.net.http.HttpClient

/**
 * Builds the [VaultAuthStrategy] matching [authMethod] from explicit parameters (instead of
 * reading directly from `VaultApplicationSettingsService`) so that both `VaultProjectService`
 * (already-saved values) and `VaultSettingsConfigurable` (form values, not yet saved) can reuse
 * the same logic without duplicating the `when`.
 */
object AuthStrategyFactory {
    fun create(
        authMethod: AuthMethod,
        vaultUrl: String,
        oidcMountPath: String,
        oidcRole: String,
        callbackPort: Int,
        callbackPath: String,
        ldapMountPath: String,
        userpassMountPath: String,
        httpClient: HttpClient,
    ): VaultAuthStrategy = when (authMethod) {
        AuthMethod.OIDC -> OidcAuthStrategy(vaultUrl, oidcMountPath, oidcRole, callbackPort, callbackPath, httpClient)
        AuthMethod.LDAP -> LdapAuthStrategy(vaultUrl, ldapMountPath, httpClient)
        AuthMethod.USERPASS -> UserpassAuthStrategy(vaultUrl, userpassMountPath, httpClient)
        AuthMethod.TOKEN -> TokenAuthStrategy(vaultUrl, httpClient)
    }
}
