package dev.vaultlink.core.credentials

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/** Stores the session client_token in memory only (PasswordSafe memory-only): lost when the IDE closes. */
object VaultCredentialsStore {
    private val attributes = CredentialAttributes(
        serviceName = generateServiceName("VaultLink", "session-token"),
        userName = null,
        isPasswordMemoryOnly = true,
    )

    fun store(clientToken: String) {
        PasswordSafe.instance.set(attributes, Credentials("vault", clientToken))
    }

    fun read(): String? = PasswordSafe.instance.getPassword(attributes)

    fun clear() {
        PasswordSafe.instance.set(attributes, null)
    }
}
