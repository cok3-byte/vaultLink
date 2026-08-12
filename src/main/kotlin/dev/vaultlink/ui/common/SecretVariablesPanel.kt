package dev.vaultlink.ui.common

import dev.vaultlink.core.vault.model.VaultSecretData
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JButton
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
                rowsPanel.add(JLabel("$key = ${if (revealed) value else MASK}"))
            }
        }
        rowsPanel.revalidate()
        rowsPanel.repaint()
    }
}
