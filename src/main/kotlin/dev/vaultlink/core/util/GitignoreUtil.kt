package dev.vaultlink.core.util

import java.io.File

object GitignoreUtil {
    /** Adds [entry] to .gitignore in [projectRoot] if not already present. */
    fun ensureEntry(projectRoot: File, entry: String) {
        val gitignore = File(projectRoot, ".gitignore")
        val existing = if (gitignore.exists()) gitignore.readLines() else emptyList()
        if (existing.any { it.trim() == entry }) return
        val prefix = if (gitignore.exists() && gitignore.length() > 0) System.lineSeparator() else ""
        gitignore.appendText(prefix + entry + System.lineSeparator())
    }
}
