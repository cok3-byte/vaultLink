package dev.vaultlink.ui.design

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Font

/*
 * Layer 2 — semantic tokens. Named by *purpose*. This is the only layer UI code should read
 * colors, spacing, radii and fonts from (directly or through the component layer in
 * VaultComponents.kt). Rules:
 *  - Never write a hex value, `JBColor.GRAY/GREEN/...`, or `Font(Font.MONOSPACED, ...)` in UI code.
 *  - Never pass a raw pixel number to a layout/border; use a [VaultSpacing] step, and scale it.
 *  - Fonts and scaled sizes are functions, not vals: they must be read at build time so they follow
 *    IDE zoom / LaF changes.
 */

/**
 * Colors by purpose. Every token is a [JBColor.namedColor] with a `VaultLink.*` key, so a theme
 * (`*.theme.json` → `"ui": { "VaultLink.Status.success": "#..." }`) can override it; the fallback
 * is the [VaultPalette] primitive or the platform's own color.
 */
object VaultColors {
    /** Default text — follows the platform label color. */
    val textPrimary: Color get() = JBUI.CurrentTheme.Label.foreground()

    /** Secondary text: counts, hints, empty states, masked/disabled values. */
    val textMuted: JBColor = JBColor.namedColor("VaultLink.Text.muted", VaultPalette.gray500)

    /** Hairline borders around boxes such as the empty state. */
    val border: JBColor = JBColor.namedColor("VaultLink.Border.default", JBColor.border())

    val statusNeutral: JBColor = JBColor.namedColor("VaultLink.Status.neutral", VaultPalette.gray500)
    val statusSuccess: JBColor = JBColor.namedColor("VaultLink.Status.success", VaultPalette.green600)
    val statusWarning: JBColor = JBColor.namedColor("VaultLink.Status.warning", VaultPalette.amber600)
    val statusError: JBColor = JBColor.namedColor("VaultLink.Status.error", VaultPalette.red600)

    /** Highlights user-driven state: pinned version, edited value. */
    val accent: JBColor = JBColor.namedColor("VaultLink.Accent", VaultPalette.blue600)
}

/**
 * Spacing scale in *unscaled* px (2 · 4 · 6 · 8 · 12 · 16). Pass these to `JBUI.Borders.empty(...)`
 * / `JBUI.size(...)` (which scale for you) or wrap them in [px] for AWT layouts' gaps.
 */
object VaultSpacing {
    const val XXS = 2
    const val XS = 4
    const val SM = 6
    const val MD = 8
    const val LG = 12
    const val XL = 16

    /** Scales a spacing step for the current HiDPI / IDE zoom — use for `FlowLayout`/`BorderLayout` gaps and struts. */
    fun px(step: Int): Int = JBUI.scale(step)
}

/** Corner radii in unscaled px (`RoundedLineBorder` takes the arc diameter). */
object VaultRadius {
    const val SM = 6
    const val PILL = 10

    fun px(radius: Int): Int = JBUI.scale(radius)
}

/** Type scale. All derived from the platform label font so they track the IDE's font settings and zoom. */
object VaultTypography {
    /** Body text — the platform label font. */
    fun body(): JBFont = JBFont.label()

    /** Section titles ("Variables") and emphasized labels. */
    fun title(): JBFont = JBFont.label().asBold()

    /** Counts, hints and other secondary text. */
    fun caption(): JBFont = JBFont.small()

    /** Chips: small and bold so uppercase text stays compact. */
    fun badge(): JBFont = JBFont.small().asBold()

    /** Secret paths, variable keys and values — same size as body text. */
    fun mono(): JBFont = monospaced(Font.PLAIN)

    fun monoBold(): JBFont = monospaced(Font.BOLD)

    private fun monospaced(style: Int): JBFont {
        val base = JBFont.label()
        // `base.size` is already scaled, so tell JBFont not to scale again.
        return JBFont.create(Font(Font.MONOSPACED, style, base.size), false)
    }
}
