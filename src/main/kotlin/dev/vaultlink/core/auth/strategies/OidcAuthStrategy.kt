package dev.vaultlink.core.auth.strategies

import dev.vaultlink.core.auth.AuthMethod
import dev.vaultlink.core.auth.AuthResult
import dev.vaultlink.core.auth.VaultAuthStrategy
import dev.vaultlink.core.auth.oidc.OidcLoginCoordinator
import dev.vaultlink.core.auth.renewSelf
import java.net.http.HttpClient

class OidcAuthStrategy(
    private val vaultUrl: String,
    private val oidcMountPath: String,
    private val oidcRole: String,
    private val callbackPort: Int,
    private val callbackPath: String,
    private val httpClient: HttpClient,
) : VaultAuthStrategy {

    override val method = AuthMethod.OIDC

    override fun authenticate(): AuthResult =
        OidcLoginCoordinator(vaultUrl, oidcMountPath, oidcRole, callbackPort, callbackPath, httpClient).login()

    override fun renew(clientToken: String): AuthResult = renewSelf(vaultUrl, httpClient, clientToken)

    override fun supportsRenewal(): Boolean = true
}
