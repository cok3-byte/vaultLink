package dev.vaultlink.ui.common

import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.SwingConstants

/** Small rounded-outline status badge (mode/version/warning), styled like an IDE inspection severity chip. */
enum class ChipVariant(val color: JBColor) {
    NEUTRAL(JBColor(0x6B6B6B, 0x9AA0A6)),
    OK(JBColor(0x368947, 0x59A869)),
    WARNING(JBColor(0x9A7B23, 0xD9A544)),
    ERROR(JBColor(0xC94F4F, 0xDB5C5C)),
    ACCENT(JBColor(0x3B72B0, 0x5C9BD8)),
}

/** A compact, non-opaque pill: colored border + colored uppercase text. No fill, so it never clips. */
fun chip(text: String, variant: ChipVariant = ChipVariant.NEUTRAL): JComponent {
    val label = JBLabel(text.uppercase(), SwingConstants.CENTER).apply {
        foreground = variant.color
        font = font.deriveFont(Font.BOLD, font.size2D - 1.5f)
        border = BorderFactory.createCompoundBorder(
            RoundedLineBorder(variant.color, JBUI.scale(10)),
            JBUI.Borders.empty(1, 7),
        )
    }
    return label
}
