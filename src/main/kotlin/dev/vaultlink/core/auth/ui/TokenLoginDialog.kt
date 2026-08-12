package dev.vaultlink.core.auth.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogWrapper
import dev.vaultlink.core.auth.LoginException
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField

/** Paste an already-issued Vault token; validated with lookup-self before accepting it (see TokenAuthStrategy). */
class TokenLoginDialog : DialogWrapper(true) {

    private val tokenField = JPasswordField()

    init {
        title = "VaultLink — Login with existing token"
        init()
    }

    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout(8, 8)).apply {
            add(JLabel("Vault token:"), BorderLayout.WEST)
            add(tokenField, BorderLayout.CENTER)
        }
    }

    private fun token(): String = String(tokenField.password)

    companion object {
        /** Safe to call from a background thread — see CredentialsLoginDialog.promptOrThrow. */
        fun promptOrThrow(): String {
            var confirmed = false
            var token = ""
            ApplicationManager.getApplication().invokeAndWait {
                val dialog = TokenLoginDialog()
                confirmed = dialog.showAndGet()
                token = dialog.token()
            }
            if (!confirmed) throw LoginException.Cancelled("Token login cancelled by the user")
            if (token.isBlank()) throw LoginException.Failed("The token cannot be empty")
            return token
        }
    }
}
