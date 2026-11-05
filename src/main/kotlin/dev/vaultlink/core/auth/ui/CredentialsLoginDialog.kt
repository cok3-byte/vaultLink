package dev.vaultlink.core.auth.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogWrapper
import dev.vaultlink.core.auth.AuthMethod
import dev.vaultlink.core.auth.LoginException
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

data class LoginCredentials(val username: String, val password: String)

/** Dialog shared by LDAP and Userpass. The password is never persisted, it only lives in memory. */
class CredentialsLoginDialog(method: AuthMethod) : DialogWrapper(true) {

    private val usernameField = JBTextField()
    private val passwordField = JBPasswordField()

    init {
        title = "VaultLink — $method Login"
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Username:") { cell(usernameField).columns(COLUMNS_MEDIUM) }
        row("Password:") { cell(passwordField).columns(COLUMNS_MEDIUM) }
    }

    override fun getPreferredFocusedComponent(): JComponent = usernameField

    private fun credentials(): LoginCredentials = LoginCredentials(usernameField.text, String(passwordField.password))

    companion object {
        /**
         * Safe to call from a background thread (e.g. inside a Task.Backgroundable): a
         * DialogWrapper can only be shown on the EDT, so creating it/showAndGet() is marshalled
         * via invokeAndWait — this method blocks the calling thread (not the EDT) until the user
         * closes the dialog.
         */
        fun promptOrThrow(method: AuthMethod): LoginCredentials {
            var confirmed = false
            var credentials: LoginCredentials? = null
            ApplicationManager.getApplication().invokeAndWait {
                val dialog = CredentialsLoginDialog(method)
                confirmed = dialog.showAndGet()
                credentials = dialog.credentials()
            }
            if (!confirmed) throw LoginException.Cancelled("$method login cancelled by the user")
            return credentials!!
        }
    }
}
