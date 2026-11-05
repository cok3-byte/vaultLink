package dev.vaultlink.ui.common

import com.intellij.ui.components.JBLabel
import dev.vaultlink.core.credentials.VaultSessionStatus
import dev.vaultlink.ui.design.SessionStatusTokens
import dev.vaultlink.ui.design.VaultSpacing
import java.awt.FlowLayout
import javax.swing.JPanel

/** Connection-status badge (active/inactive session) shared by Settings and the Tool Window. */
class VaultSessionStatusPanel : JPanel(FlowLayout(FlowLayout.LEFT, VaultSpacing.px(VaultSpacing.XS), 0)) {

    private val label = JBLabel()

    init {
        add(label)
        refresh()
    }

    fun refresh() {
        label.text = VaultSessionStatus.summary()
        label.foreground = if (VaultSessionStatus.isActive()) SessionStatusTokens.activeColor() else SessionStatusTokens.inactiveColor()
    }
}
