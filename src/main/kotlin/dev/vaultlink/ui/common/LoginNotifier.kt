package dev.vaultlink.ui.common

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import dev.vaultlink.core.auth.AuthMethod
import dev.vaultlink.core.auth.AuthResult
import dev.vaultlink.core.auth.LoginException

/**
 * Translates a login result (or a flow that starts with login, e.g. "fetch secret") into a
 * consistent notification — distinct title/type per case (success/fail/timeout/error/
 * cancelled), never the token's value or a secret's value. Used by Settings ("Test login") and
 * the Tool Window ("Fetch secret") to avoid duplicating the message mapping.
 */
object LoginNotifier {

    /** "Login only" use case (Settings > Test login): success is an [AuthResult]. */
    fun notifyResult(project: Project?, method: AuthMethod, result: Result<AuthResult>) {
        val error = result.exceptionOrNull()
        if (error == null) {
            notifySuccess(project, "Vault: session started", "Login successful via $method.")
        } else {
            notifyError(project, error)
        }
    }

    /** "Login + something else" use case (Tool Window > Fetch secret): the caller builds its own success text. */
    fun notifySuccess(project: Project?, title: String, message: String) {
        notify(project, title, message, NotificationType.INFORMATION)
    }

    fun notifyError(project: Project?, error: Throwable) {
        val (title, message, type) = describeError(error)
        notify(project, title, message, type)
    }

    private fun describeError(error: Throwable): Triple<String, String, NotificationType> = when (error) {
        is LoginException.Failed -> Triple(
            "Vault: credentials rejected",
            error.message ?: "Invalid credentials.",
            NotificationType.ERROR,
        )
        is LoginException.TimedOut -> Triple(
            "Vault: timed out",
            error.message ?: "Login did not complete in time.",
            NotificationType.WARNING,
        )
        is LoginException.ConnectionError -> Triple(
            "Vault: connection error",
            error.message ?: "Could not contact the Vault server.",
            NotificationType.ERROR,
        )
        is LoginException.Cancelled -> Triple(
            "Vault: login cancelled",
            error.message ?: "Login was cancelled.",
            NotificationType.INFORMATION,
        )
        else -> Triple(
            "Vault: unexpected error",
            error.message ?: "An unexpected error occurred.",
            NotificationType.ERROR,
        )
    }

    private fun notify(project: Project?, title: String, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("VaultLink")
            .createNotification(title, message, type)
            .notify(project)
    }
}
