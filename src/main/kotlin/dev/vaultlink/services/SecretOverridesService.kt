package dev.vaultlink.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import dev.vaultlink.core.vault.model.SecretOverrides
import dev.vaultlink.core.vault.model.SecretPath
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-secret user overrides (disabled keys, edited values), keyed by [SecretPath] so switching
 * secrets via Browse… doesn't drag along the previous secret's exclusions. Deliberately **not** a
 * `PersistentStateComponent` — [SecretOverrides.editedValues] is secret material and must never
 * reach `.idea/vaultlink.xml`, the same reasoning behind `VaultCredentialsStore`'s memory-only
 * PasswordSafe entry. Everything here is lost when the IDE closes.
 */
@Service(Service.Level.PROJECT)
class SecretOverridesService {
    private val byPath = ConcurrentHashMap<SecretPath, SecretOverrides>()

    fun get(path: SecretPath): SecretOverrides = byPath[path] ?: SecretOverrides()

    fun set(path: SecretPath, overrides: SecretOverrides) {
        if (overrides.isEmpty) byPath.remove(path) else byPath[path] = overrides
    }

    /** Drops every edited value (across all secrets) but keeps disabled-key exclusions — used on logout. */
    fun discardValues() {
        byPath.replaceAll { _, overrides -> overrides.withoutValues() }
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
