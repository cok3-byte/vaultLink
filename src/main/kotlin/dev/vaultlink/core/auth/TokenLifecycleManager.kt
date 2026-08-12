package dev.vaultlink.core.auth

import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicReference

/** Renews the active token at 75% of its TTL; if it fails (revoked/non-renewable), requires re-login. */
class TokenLifecycleManager(
    private val strategy: VaultAuthStrategy,
    private val onRenewed: (AuthResult) -> Unit,
    private val onRenewalFailed: () -> Unit,
) {
    private val timer = Timer("vaultlink-token-renewal", true)
    private val current = AtomicReference<AuthResult>()

    fun start(initial: AuthResult) {
        current.set(initial)
        scheduleRenewal(initial)
    }

    fun stop() {
        timer.cancel()
    }

    private fun scheduleRenewal(auth: AuthResult) {
        if (!auth.renewable || !strategy.supportsRenewal()) return
        val delayMs = (auth.leaseDuration * 1000 * 0.75).toLong().coerceAtLeast(1000)
        timer.schedule(object : TimerTask() {
            override fun run() {
                try {
                    val renewed = strategy.renew(current.get().clientToken)
                    current.set(renewed)
                    onRenewed(renewed)
                    scheduleRenewal(renewed)
                } catch (e: Exception) {
                    onRenewalFailed() // never retries silently
                }
            }
        }, delayMs)
    }
}
