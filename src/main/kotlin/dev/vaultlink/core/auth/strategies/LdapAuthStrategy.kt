package dev.vaultlink.core.auth.strategies

import dev.vaultlink.core.auth.AuthMethod
import dev.vaultlink.core.auth.AuthResult
import dev.vaultlink.core.auth.VaultAuthStrategy
import dev.vaultlink.core.auth.renewSelf
import dev.vaultlink.core.auth.ui.CredentialsLoginDialog
import java.net.http.HttpClient

/** renewable may come back false depending on how the LDAP server is configured — never assume renewable. */
class LdapAuthStrategy(
    private val vaultUrl: String,
    private val ldapMountPath: String,
    private val httpClient: HttpClient,
) : VaultAuthStrategy {

    override val method = AuthMethod.LDAP

    override fun authenticate(): AuthResult {
        val credentials = CredentialsLoginDialog.promptOrThrow(AuthMethod.LDAP)
        return usernamePasswordLogin(vaultUrl, ldapMountPath, credentials.username, credentials.password, httpClient)
    }

    override fun renew(clientToken: String): AuthResult = renewSelf(vaultUrl, httpClient, clientToken)

    override fun supportsRenewal(): Boolean = true // see AuthResult.renewable at runtime
}
