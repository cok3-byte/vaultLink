package dev.vaultlink.ui.common

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import dev.vaultlink.core.credentials.VaultSessionStatus
import java.awt.FlowLayout
import javax.swing.JPanel

/** Connection-status badge (active/inactive session) shared by Settings and the Tool Window. */
class VaultSessionStatusPanel : JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)) {

    private val label = JBLabel()

    init {
        add(label)
        refresh()
    }

    fun refresh() {
        label.text = VaultSessionStatus.summary()
        label.foreground = if (VaultSessionStatus.isActive()) JBColor.GREEN else JBColor.GRAY
    }
}
