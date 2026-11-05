package dev.vaultlink.core.credentials

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * Stores each Vault profile's session client_token in memory only (PasswordSafe memory-only): lost
 * when the IDE closes. Keyed by profile id so a token issued by one Vault server is never read —
 * and therefore never sent — when talking to another.
 */
object VaultCredentialsStore {
    private fun attributes(profileId: String) = CredentialAttributes(
        serviceName = generateServiceName("VaultLink", "session-token:$profileId"),
        userName = null,
        isPasswordMemoryOnly = true,
    )

    fun store(profileId: String, clientToken: String) {
        PasswordSafe.instance.set(attributes(profileId), Credentials("vault", clientToken))
    }

    fun read(profileId: String): String? = PasswordSafe.instance.getPassword(attributes(profileId))

    fun clear(profileId: String) {
        PasswordSafe.instance.set(attributes(profileId), null)
    }
}
