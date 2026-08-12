package dev.vaultlink.core.auth.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogWrapper
import dev.vaultlink.core.auth.AuthMethod
import dev.vaultlink.core.auth.LoginException
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JTextField

data class LoginCredentials(val username: String, val password: String)

/** Dialog shared by LDAP and Userpass. The password is never persisted, it only lives in memory. */
class CredentialsLoginDialog(method: AuthMethod) : DialogWrapper(true) {

    private val usernameField = JTextField()
    private val passwordField = JPasswordField()

    init {
        title = "VaultLink — $method Login"
        init()
    }

    override fun createCenterPanel(): JComponent {
        return JPanel(GridLayout(2, 2, 8, 8)).apply {
            add(JLabel("Username:"))
            add(usernameField)
            add(JLabel("Password:"))
            add(passwordField)
        }
    }

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
