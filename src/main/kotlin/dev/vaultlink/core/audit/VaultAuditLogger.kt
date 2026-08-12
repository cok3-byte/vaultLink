package dev.vaultlink.core.audit

interface VaultAuditLogger {
    fun log(event: AuditEvent)
}
