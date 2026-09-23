package dev.vaultlink.ui.common

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JPasswordField

/**
 * Overrides a single variable's injected value. Preloaded with the currently effective value
 * (Vault's, or a previous edit) and masked by default — [showCheckBox] toggles the field's echo
 * character, same masked-by-default posture as [SecretVariablesPanel]'s reveal toggle.
 */
class EditVariableValueDialog private constructor(
    project: Project,
    key: String,
    currentValue: String,
) : DialogWrapper(project, true) {

    private val valueField = JPasswordField(currentValue, 30).apply { echoChar = MASK_ECHO_CHAR }
    private val showCheckBox = JBCheckBox("Show value").apply {
        addActionListener { valueField.echoChar = if (isSelected) 0.toChar() else MASK_ECHO_CHAR }
    }

    init {
        title = "VaultLink — Edit $key"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val fieldRow = JPanel(BorderLayout(8, 0)).apply {
            add(JBLabel("Value:"), BorderLayout.WEST)
            add(valueField, BorderLayout.CENTER)
        }
        return JPanel(BorderLayout(0, 8)).apply {
            add(fieldRow, BorderLayout.NORTH)
            add(showCheckBox, BorderLayout.SOUTH)
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = valueField

    private fun value(): String = String(valueField.password)

    companion object {
        private const val MASK_ECHO_CHAR = '•'

        /** Returns the edited value, or null if the dialog was cancelled. */
        fun edit(project: Project, key: String, currentValue: String): String? {
            val dialog = EditVariableValueDialog(project, key, currentValue)
            return if (dialog.showAndGet()) dialog.value() else null
        }
    }
}
