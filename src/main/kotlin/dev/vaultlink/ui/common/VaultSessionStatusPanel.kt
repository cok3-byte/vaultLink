package dev.vaultlink.ui.common

import com.intellij.ui.components.JBLabel
import dev.vaultlink.core.credentials.VaultSessionStatus
import dev.vaultlink.ui.design.SessionStatusTokens
import dev.vaultlink.ui.design.VaultSpacing
import java.awt.FlowLayout
import javax.swing.JPanel

/**
 * Connection-status badge (active/inactive session) shared by Settings and the Tool Window.
 * [profileId] is read on every [refresh], so the badge follows whichever profile is being shown.
 */
class VaultSessionStatusPanel(
    private val profileId: () -> String,
) : JPanel(FlowLayout(FlowLayout.LEFT, VaultSpacing.px(VaultSpacing.XS), 0)) {

    private val label = JBLabel()

    init {
        add(label)
        refresh()
    }

    fun refresh() {
        val id = profileId()
        label.text = VaultSessionStatus.summary(id)
        label.foreground =
            if (VaultSessionStatus.isActive(id)) SessionStatusTokens.activeColor() else SessionStatusTokens.inactiveColor()
    }
}
