package dev.vaultlink.services

import dev.vaultlink.core.auth.AuthMethod
import java.util.UUID

/**
 * A named connection to one Vault server: where it is and how to authenticate against it.
 * Persisted as part of [VaultSettingsState] (IDE-level, so every project can pick from the same
 * list). A project is associated with a profile via `VaultProjectSettingsState.profileId`; when it
 * has none — or the one it had was deleted — it uses the default profile.
 *
 * `var` + default values on every property are required by the IntelliJ XML serializer (no-arg
 * constructor + setters). [id] is stable across renames, so associations survive a name change.
 * Never holds a token or password: sessions live in memory only, keyed by [id].
 */
data class VaultProfile(
    var id: String = newProfileId(),
    var name: String = "Default",
    var vaultUrl: String = "",
    var namespace: String? = null,
    var authMethod: AuthMethod = AuthMethod.OIDC,
    var oidcMountPath: String = "oidc",
    var oidcRole: String = "",
    var ldapMountPath: String = "ldap",
    var userpassMountPath: String = "userpass",
    var callbackPort: Int = 8250,
    var callbackPath: String = "/oidc/callback",
    var customCaCertPath: String? = null,
) {
    /**
     * Whether switching from [other] to this configuration must invalidate an existing session:
     * a token issued by one server/namespace/auth backend must never be sent to another.
     */
    fun invalidatesSessionOf(other: VaultProfile): Boolean =
        vaultUrl != other.vaultUrl || namespace != other.namespace || authMethod != other.authMethod ||
            customCaCertPath != other.customCaCertPath

    override fun toString(): String = name
}

fun newProfileId(): String = UUID.randomUUID().toString()
