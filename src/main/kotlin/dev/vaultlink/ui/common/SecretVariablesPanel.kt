package dev.vaultlink.ui.common

import com.intellij.icons.AllIcons
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.components.JBLabel
import dev.vaultlink.core.vault.model.VaultSecretData
import java.awt.BorderLayout
import java.awt.Font
import java.awt.datatransfer.StringSelection
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

private const val MASK = "••••••••"

/**
 * Shows the keys of the last-fetched secret with masked values by default, with a single toggle
 * to reveal/hide all of them. The revealed value only ever lives in this Swing component's
 * memory — never logged, notified, or persisted.
 */
class SecretVariablesPanel : JPanel(BorderLayout(4, 4)) {

    private var secret: VaultSecretData? = null
    private var revealed = false

    private val rowsPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val toggleButton = JButton(toggleLabel()).apply {
        addActionListener {
            revealed = !revealed
            text = toggleLabel()
            renderRows()
        }
    }

    init {
        add(toggleButton, BorderLayout.NORTH)
        add(rowsPanel, BorderLayout.CENTER)
        renderRows()
    }

    /** Replaces the displayed secret and always resets to masked, so revealing one secret never leaves the next exposed. */
    fun setSecret(newSecret: VaultSecretData?) {
        secret = newSecret
        revealed = false
        toggleButton.text = toggleLabel()
        renderRows()
    }

    private fun toggleLabel() = if (revealed) "🙈 Hide values" else "👁 Show values"

    private fun renderRows() {
        rowsPanel.removeAll()
        val data = secret?.data
        if (data.isNullOrEmpty()) {
            rowsPanel.add(JLabel("No variables fetched yet."))
        } else {
            for ((key, value) in data) {
                rowsPanel.add(variableRow(key, value))
            }
        }
        rowsPanel.revalidate()
        rowsPanel.repaint()
    }

    private fun variableRow(key: String, value: String): JComponent {
        val keyLabel = JBLabel(key).apply { font = font.deriveFont(Font.BOLD) }
        val valueLabel = JBLabel(if (revealed) value else MASK).apply { font = Font(Font.MONOSPACED, Font.PLAIN, font.size) }
        val copyButton = JButton(AllIcons.Actions.Copy).apply {
            toolTipText = "Copy value"
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            addActionListener { CopyPasteManager.getInstance().setContents(StringSelection(value)) }
        }
        return JPanel(BorderLayout(6, 0)).apply {
            add(keyLabel, BorderLayout.WEST)
            add(valueLabel, BorderLayout.CENTER)
            add(copyButton, BorderLayout.EAST)
        }
    }
}
