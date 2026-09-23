package dev.vaultlink.core.vault.model

/**
 * User adjustments layered on top of what Vault returned for a single secret. [editedValues] is
 * secret material: it lives only in memory (see `SecretOverridesService`), never persisted and
 * never logged.
 */
data class SecretOverrides(
    val disabledKeys: Set<String> = emptySet(),
    val editedValues: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean get() = disabledKeys.isEmpty() && editedValues.isEmpty()

    fun withDisabled(key: String, disabled: Boolean): SecretOverrides =
        copy(disabledKeys = if (disabled) disabledKeys + key else disabledKeys - key)

    /** null [value] reverts the key back to whatever Vault returned. */
    fun withValue(key: String, value: String?): SecretOverrides =
        copy(editedValues = if (value == null) editedValues - key else editedValues + (key to value))

    /** Keeps the disabled-keys exclusions but discards edited values — used on logout. */
    fun withoutValues(): SecretOverrides = copy(editedValues = emptyMap())
}
