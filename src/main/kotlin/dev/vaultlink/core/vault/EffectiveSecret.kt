package dev.vaultlink.core.vault

import dev.vaultlink.core.vault.model.SecretOverrides

/**
 * The single definition of "what actually gets injected": Vault's data with the user's overrides
 * applied on top. Every injection path (live Run Config, `.env`) must go through this — never the
 * raw secret data directly — so disabling or editing a key behaves identically everywhere.
 */
object EffectiveSecret {
    fun effective(data: Map<String, String>, overrides: SecretOverrides): Map<String, String> =
        data.filterKeys { it !in overrides.disabledKeys }
            .mapValues { (key, value) -> overrides.editedValues[key] ?: value }
}
