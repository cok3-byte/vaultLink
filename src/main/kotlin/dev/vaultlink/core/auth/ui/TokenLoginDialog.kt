package dev.vaultlink.core.auth.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogWrapper
import dev.vaultlink.core.auth.LoginException
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/** Paste an already-issued Vault token; validated with lookup-self before accepting it (see TokenAuthStrategy). */
class TokenLoginDialog : DialogWrapper(true) {

    private val tokenField = JBPasswordField()

    init {
        title = "VaultLink — Login with existing token"
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Vault token:") { cell(tokenField).columns(COLUMNS_MEDIUM) }
    }

    override fun getPreferredFocusedComponent(): JComponent = tokenField

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
