package dev.vaultlink.core.net

import dev.vaultlink.core.vault.exception.VaultException

/** Exponential backoff only on network/timeout/5xx errors; never retries 401/403 or 404. */
class RetryPolicy(
    private val maxAttempts: Int = 3,
    private val initialBackoffMs: Long = 250,
) {
    fun <T> execute(block: () -> T): T {
        var attempt = 0
        var backoff = initialBackoffMs
        while (true) {
            attempt++
            try {
                return block()
            } catch (e: VaultException.PermissionDenied) {
                throw e
            } catch (e: VaultException.NotFound) {
                throw e
            } catch (e: VaultException) {
                if (attempt >= maxAttempts) throw e
                Thread.sleep(backoff)
                backoff *= 2
            }
        }
    }
}
