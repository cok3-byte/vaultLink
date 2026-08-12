package dev.vaultlink.core.vault

import dev.vaultlink.core.vault.model.SecretPath

/** Parses "<mount>.<secret>" from the project name. */
object SecretPathResolver {
    private val PATTERN = Regex("^([^.]+)\\.(.+)$")

    fun resolve(projectName: String): SecretPath? {
        val match = PATTERN.matchEntire(projectName) ?: return null
        return SecretPath(mount = match.groupValues[1], secretName = match.groupValues[2])
    }
}
