package dev.vaultlink.ui.design

import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/*
 * Layer 3 — component tokens and the shared components built from them. A component reads its
 * look only from its own `*Tokens` object, which in turn only references semantic tokens
 * (VaultTokens.kt). To restyle one component, change its tokens here; to restyle the plugin,
 * change the semantic layer.
 */

// ---------------------------------------------------------------------------------------------
// Chip
// ---------------------------------------------------------------------------------------------

/** Status badge variants (mode / version / warning / edited). */
enum class ChipVariant(internal val color: () -> JBColor) {
    NEUTRAL({ VaultColors.statusNeutral }),
    OK({ VaultColors.statusSuccess }),
    WARNING({ VaultColors.statusWarning }),
    ERROR({ VaultColors.statusError }),
    ACCENT({ VaultColors.accent }),
}

object ChipTokens {
    const val PADDING_V = VaultSpacing.XXS
    const val PADDING_H = VaultSpacing.SM
    const val RADIUS = VaultRadius.PILL
    fun font() = VaultTypography.badge()
}

/**
 * Compact, non-opaque pill: colored outline + colored uppercase text, styled like an IDE inspection
 * severity chip. No fill, so it never clips against the panel background.
 */
fun chip(text: String, variant: ChipVariant = ChipVariant.NEUTRAL): JComponent {
    val color = variant.color()
    return JBLabel(text.uppercase(), SwingConstants.CENTER).apply {
        foreground = color
        font = ChipTokens.font()
        border = BorderFactory.createCompoundBorder(
            RoundedLineBorder(color, VaultRadius.px(ChipTokens.RADIUS)),
            JBUI.Borders.empty(ChipTokens.PADDING_V, ChipTokens.PADDING_H),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------------------------

object EmptyStateTokens {
    const val PADDING_V = VaultSpacing.XL
    const val PADDING_H = VaultSpacing.LG
    const val RADIUS = VaultRadius.SM

    /** Max text width (unscaled px) before wrapping. */
    const val TEXT_WIDTH = 220
}

/** Rounded, outlined box with centered muted text — the "nothing here yet" placeholder. */
fun emptyState(text: String): JComponent {
    val width = JBUI.scale(EmptyStateTokens.TEXT_WIDTH)
    val label = mutedLabel("<html><div style='text-align:center;width:${width}px;'>$text</div></html>").apply {
        horizontalAlignment = JLabel.CENTER
    }
    return JPanel(BorderLayout()).apply {
        border = BorderFactory.createCompoundBorder(
            RoundedLineBorder(VaultColors.border, VaultRadius.px(EmptyStateTokens.RADIUS)),
            JBUI.Borders.empty(EmptyStateTokens.PADDING_V, EmptyStateTokens.PADDING_H),
        )
        add(label, BorderLayout.CENTER)
    }
}

// ---------------------------------------------------------------------------------------------
// Text
// ---------------------------------------------------------------------------------------------

/** Secondary text (hints, counts, explanations). */
fun mutedLabel(text: String = "", icon: Icon? = null): JBLabel =
    JBLabel(text).apply {
        foreground = VaultColors.textMuted
        this.icon = icon
    }

/** Bold section heading, e.g. "Variables". */
fun sectionTitle(text: String): JBLabel = JBLabel(text).apply { font = VaultTypography.title() }

/** Small muted counter next to a [sectionTitle]. */
fun captionLabel(text: String = ""): JBLabel =
    mutedLabel(text).apply { font = VaultTypography.caption() }

/** Monospaced label for secret paths, keys and values. */
fun monoLabel(text: String, bold: Boolean = false): JBLabel =
    JBLabel(text).apply { font = if (bold) VaultTypography.monoBold() else VaultTypography.mono() }

// ---------------------------------------------------------------------------------------------
// Icon button
// ---------------------------------------------------------------------------------------------

/** Borderless, icon-only inline button (reveal, copy, edit, rollback). Always carries a tooltip / accessible name. */
fun iconButton(icon: Icon, tooltip: String, onClick: () -> Unit): JButton =
    JButton(icon).apply {
        toolTipText = tooltip
        accessibleContext.accessibleName = tooltip
        isBorderPainted = false
        isContentAreaFilled = false
        isFocusPainted = false
        addActionListener { onClick() }
    }

// ---------------------------------------------------------------------------------------------
// Layout helpers
// ---------------------------------------------------------------------------------------------

/** Transparent horizontal flow with a scaled spacing-step gap — replaces ad-hoc `FlowLayout(..., 6, 0)`. */
fun hFlow(align: Int = FlowLayout.LEFT, gap: Int = VaultSpacing.SM, vararg children: JComponent): JPanel =
    JPanel(FlowLayout(align, VaultSpacing.px(gap), 0)).apply {
        isOpaque = false
        children.forEach { add(it) }
    }

// ---------------------------------------------------------------------------------------------
// Variable row (SecretVariablesPanel)
// ---------------------------------------------------------------------------------------------

object VariableRowTokens {
    /** Minimum (not fixed) key column width, unscaled px — long keys keep their full text. */
    const val KEY_MIN_WIDTH = 110
    const val PADDING_V = VaultSpacing.XS
    const val PADDING_H = VaultSpacing.XXS
    const val GAP = VaultSpacing.SM
    const val KEY_GAP = VaultSpacing.XS
    const val MASK = "••••••••"
}

/** Session indicator (Settings + Tool Window header). */
object SessionStatusTokens {
    fun activeColor() = VaultColors.statusSuccess
    fun inactiveColor() = VaultColors.textMuted
}
