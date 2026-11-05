package dev.vaultlink.ui.design

import com.intellij.ui.JBColor

/**
 * Layer 1 — primitives. Raw light/dark color pairs, named by hue (never by purpose). Nothing
 * outside `dev.vaultlink.ui.design` should reference these directly: UI code goes through the
 * semantic layer ([VaultColors]), which is what lets a purpose be re-pointed at another hue in one
 * place. Values match IntelliJ's own inspection-severity hues so the plugin blends into Light,
 * Darcula and New UI themes.
 */
internal object VaultPalette {
    // (light, dark)
    val gray500 = JBColor(0x6B6B6B, 0x9AA0A6)
    val green600 = JBColor(0x368947, 0x59A869)
    val amber600 = JBColor(0x9A7B23, 0xD9A544)
    val red600 = JBColor(0xC94F4F, 0xDB5C5C)
    val blue600 = JBColor(0x3B72B0, 0x5C9BD8)
}
