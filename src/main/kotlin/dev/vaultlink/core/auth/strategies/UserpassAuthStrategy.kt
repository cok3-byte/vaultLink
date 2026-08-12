package dev.vaultlink.core.auth.strategies

import dev.vaultlink.core.auth.AuthMethod
import dev.vaultlink.core.auth.AuthResult
import dev.vaultlink.core.auth.VaultAuthStrategy
import dev.vaultlink.core.auth.renewSelf
import dev.vaultlink.core.auth.ui.CredentialsLoginDialog
import java.net.http.HttpClient

class UserpassAuthStrategy(
    private val vaultUrl: String,
    private val userpassMountPath: String,
    private val httpClient: HttpClient,
) : VaultAuthStrategy {

    override val method = AuthMethod.USERPASS

    override fun authenticate(): AuthResult {
        val credentials = CredentialsLoginDialog.promptOrThrow(AuthMethod.USERPASS)
        return usernamePasswordLogin(vaultUrl, userpassMountPath, credentials.username, credentials.password, httpClient)
    }

    override fun renew(clientToken: String): AuthResult = renewSelf(vaultUrl, httpClient, clientToken)

    override fun supportsRenewal(): Boolean = true
}
