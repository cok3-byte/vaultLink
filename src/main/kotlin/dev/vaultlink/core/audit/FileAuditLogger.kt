package dev.vaultlink.core.audit

import java.io.File
import java.time.format.DateTimeFormatter

class FileAuditLogger(private val auditFile: File) : VaultAuditLogger {
    override fun log(event: AuditEvent) {
        val line = listOf(
            DateTimeFormatter.ISO_INSTANT.format(event.timestamp),
            event.osUser,
            event.mount,
            event.secretPath,
            event.version?.toString() ?: "latest",
            event.result.name,
        ).joinToString(" | ")
        auditFile.parentFile?.mkdirs()
        auditFile.appendText(line + System.lineSeparator())
    }
}
