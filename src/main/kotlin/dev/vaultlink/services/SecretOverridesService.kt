package dev.vaultlink.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import dev.vaultlink.core.vault.model.SecretOverrides
import dev.vaultlink.core.vault.model.SecretPath
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-secret user overrides (disabled keys, edited values), keyed by Vault profile + [SecretPath]
 * so switching secrets via Browse… — or switching to another Vault server that happens to have the
 * same mount/secret — doesn't drag along the previous secret's exclusions or edited values. Deliberately **not** a
 * `PersistentStateComponent` — [SecretOverrides.editedValues] is secret material and must never
 * reach `.idea/vaultlink.xml`, the same reasoning behind `VaultCredentialsStore`'s memory-only
 * PasswordSafe entry. Everything here is lost when the IDE closes.
 */
@Service(Service.Level.PROJECT)
class SecretOverridesService {
    private data class Key(val profileId: String, val path: SecretPath)

    private val byPath = ConcurrentHashMap<Key, SecretOverrides>()

    fun get(profileId: String, path: SecretPath): SecretOverrides = byPath[Key(profileId, path)] ?: SecretOverrides()

    fun set(profileId: String, path: SecretPath, overrides: SecretOverrides) {
        val key = Key(profileId, path)
        if (overrides.isEmpty) byPath.remove(key) else byPath[key] = overrides
    }

    /** Drops every edited value of [profileId]'s secrets but keeps disabled-key exclusions — used on logout. */
    fun discardValues(profileId: String) {
        byPath.replaceAll { key, overrides -> if (key.profileId == profileId) overrides.withoutValues() else overrides }
        byPath.entries.removeIf { (_, overrides) -> overrides.isEmpty }
    }

    /** Drops everything for every secret — used by the "Clear memory" action. */
    fun clearAll() {
        byPath.clear()
    }

    companion object {
        fun getInstance(project: Project): SecretOverridesService =
            project.getService(SecretOverridesService::class.java)
    }
}
