package dev.vaultlink.ui.common

import com.intellij.icons.AllIcons
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import dev.vaultlink.core.vault.EffectiveSecret
import dev.vaultlink.core.vault.model.SecretOverrides
import dev.vaultlink.core.vault.model.VaultSecretData
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.datatransfer.StringSelection
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

private const val MASK = "••••••••"
private const val DEFAULT_EMPTY_TEXT = "No variables yet — Fetch secret to load them."

/**
 * Shows the keys of the last-fetched secret with masked values by default, lets the user disable
 * a key (excluded from injection, reversibly — the row stays visible, greyed and struck through)
 * or override its value (injected instead of Vault's, marked with an "edited" chip), plus the
 * existing reveal/copy affordances. [onOverridesChanged] fires whenever the user's adjustments
 * change, so the owner can persist them (in memory only — see `SecretOverridesService`) and
 * re-apply the secret immediately. The revealed value only ever lives in this Swing component's
 * memory — never logged, notified, or persisted. Owns its own section header ("Variables" + count
 * + reveal/copy-all) so it renders as one self-contained payload block.
 */
class SecretVariablesPanel(private val project: Project) : JPanel(BorderLayout(0, 8)) {

    private var secret: VaultSecretData? = null
    private var overrides: SecretOverrides = SecretOverrides()
    private var revealed = false
    private var emptyText = DEFAULT_EMPTY_TEXT

    /** Fired after the user disables/enables a key or edits/reverts a value; the caller decides where it lives. */
    var onOverridesChanged: ((SecretOverrides) -> Unit)? = null

    private val countLabel = JBLabel().apply {
        foreground = JBColor.GRAY
        font = font.deriveFont(font.size2D - 1f)
    }
    private val revealButton = iconButton(AllIcons.General.InspectionsEye, "Show values") { toggleReveal() }
    private val copyAllButton = iconButton(AllIcons.Actions.Copy, "Copy all values") { copyAll() }
    private val rowsPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }

    init {
        add(header(), BorderLayout.NORTH)
        add(rowsPanel, BorderLayout.CENTER)
        renderRows()
    }

    private fun header(): JComponent {
        val title = JBLabel("Variables").apply { font = font.deriveFont(Font.BOLD) }
        val left = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            isOpaque = false
            add(title)
            add(countLabel)
        }
        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
            isOpaque = false
            add(revealButton)
            add(copyAllButton)
        }
        return JPanel(BorderLayout()).apply {
            add(left, BorderLayout.WEST)
            add(right, BorderLayout.EAST)
        }
    }

    /** Replaces the displayed secret and its overrides, and always resets to masked, so revealing one secret never leaves the next exposed. */
    fun setSecret(newSecret: VaultSecretData?, newOverrides: SecretOverrides = SecretOverrides()) {
        secret = newSecret
        overrides = newOverrides
        revealed = false
        renderRows()
    }

    /** Empty-state copy shown when nothing has been fetched yet; callers vary it by resolution state (AUTO/MANUAL/no match). */
    fun setEmptyText(text: String) {
        emptyText = text
        if (secret?.data.isNullOrEmpty()) renderRows()
    }

    private fun toggleReveal() {
        revealed = !revealed
        renderRows()
    }

    private fun copyAll() {
        val data = secret?.data ?: return
        val effective = EffectiveSecret.effective(data, overrides)
        val text = effective.entries.joinToString("\n") { (key, value) -> "$key=$value" }
        CopyPasteManager.getInstance().setContents(StringSelection(text))
    }

    /** Every mutation funnels through here: updates local state, notifies the owner, and re-renders. */
    private fun applyOverrides(newOverrides: SecretOverrides) {
        overrides = newOverrides
        onOverridesChanged?.invoke(overrides)
        renderRows()
    }

    private fun renderRows() {
        val data = secret?.data
        val hasRows = !data.isNullOrEmpty()
        countLabel.text = when {
            !hasRows -> ""
            overrides.disabledKeys.isEmpty() -> data!!.size.toString()
            else -> "${data!!.keys.count { it !in overrides.disabledKeys }} / ${data.size}"
        }
        revealButton.toolTipText = if (revealed) "Hide values" else "Show values"
        revealButton.isEnabled = hasRows
        copyAllButton.isEnabled = hasRows

        rowsPanel.removeAll()
        if (data.isNullOrEmpty()) {
            rowsPanel.add(emptyBox())
        } else {
            for ((key, value) in data) {
                rowsPanel.add(variableRow(key, value))
            }
        }
        rowsPanel.revalidate()
        rowsPanel.repaint()
    }

    private fun emptyBox(): JComponent {
        val label = JBLabel("<html><div style='text-align:center;width:220px;'>$emptyText</div></html>").apply {
            foreground = JBColor.GRAY
            horizontalAlignment = JLabel.CENTER
        }
        return JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                RoundedLineBorder(JBColor.GRAY, JBUI.scale(6)),
                JBUI.Borders.empty(16, 12),
            )
            add(label, BorderLayout.CENTER)
        }
    }

    private fun variableRow(key: String, vaultValue: String): JComponent {
        val enabled = key !in overrides.disabledKeys
        val edited = overrides.editedValues.containsKey(key)
        val effectiveValue = overrides.editedValues[key] ?: vaultValue

        val checkBox = JBCheckBox("", enabled).apply {
            toolTipText = if (enabled) "Injected — uncheck to exclude from Run Config / .env" else "Excluded — check to inject again"
            addActionListener { applyOverrides(overrides.withDisabled(key, !isSelected)) }
        }
        val keyLabel = JBLabel(key).apply {
            font = Font(Font.MONOSPACED, Font.BOLD, font.size)
            preferredSize = Dimension(110, preferredSize.height)
            foreground = if (enabled) JBColor.foreground() else JBColor.GRAY
        }
        val shownText = if (revealed) effectiveValue else MASK
        val valueLabel = JBLabel(if (enabled) shownText else "<html><strike>$shownText</strike></html>").apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, font.size)
            foreground = if (revealed && enabled) JBColor.foreground() else JBColor.GRAY
        }
        val rollbackButton = if (edited) {
            iconButton(AllIcons.Actions.Rollback, "Revert to the value from Vault") {
                applyOverrides(overrides.withValue(key, null))
            }
        } else {
            null
        }
        val editButton = iconButton(AllIcons.Actions.Edit, "Edit value") {
            val newValue = EditVariableValueDialog.edit(project, key, effectiveValue) ?: return@iconButton
            applyOverrides(overrides.withValue(key, newValue))
        }
        val copyButton = iconButton(AllIcons.Actions.Copy, "Copy value") {
            CopyPasteManager.getInstance().setContents(StringSelection(effectiveValue))
        }

        val west = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
            add(checkBox)
            add(keyLabel)
        }
        val center = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            isOpaque = false
            add(valueLabel)
            if (edited) add(chip("edited", ChipVariant.ACCENT))
        }
        val east = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
            isOpaque = false
            rollbackButton?.let { add(it) }
            add(editButton)
            add(copyButton)
        }
        return JPanel(BorderLayout(6, 0)).apply {
            border = JBUI.Borders.empty(3, 2)
            add(west, BorderLayout.WEST)
            add(center, BorderLayout.CENTER)
            add(east, BorderLayout.EAST)
        }
    }
}

private fun iconButton(icon: Icon, tooltip: String, onClick: () -> Unit): JButton =
    JButton(icon).apply {
        toolTipText = tooltip
        isBorderPainted = false
        isContentAreaFilled = false
        isFocusPainted = false
        addActionListener { onClick() }
    }
