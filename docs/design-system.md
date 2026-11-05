# VaultLink design system

VaultLink's UI is Swing running inside IntelliJ IDEA, so the design system is Kotlin code that sits
on top of the platform's own theming (`JBColor`, `JBUI`, `JBFont`), not CSS. It lives in
[`dev.vaultlink.ui.design`](../src/main/kotlin/dev/vaultlink/ui/design/).

**Goal:** VaultLink should look like part of the IDE. It follows the active theme (Light, Darcula,
New UI), scales with HiDPI and IDE zoom, and every panel uses the same colors, spacing and type.

## Three-layer architecture

```
Primitive   VaultPalette.kt     raw light/dark hues, named by hue        (internal)
    ↓
Semantic    VaultTokens.kt      VaultColors · VaultSpacing · VaultRadius · VaultTypography
    ↓
Component   VaultComponents.kt  ChipTokens · EmptyStateTokens · VariableRowTokens · SessionStatusTokens
                                + shared builders: chip, emptyState, iconButton, mutedLabel, …
```

- UI code reads **only** the semantic and component layers. Primitives are `internal` so a purpose
  can be pointed at a different hue in one place.
- To restyle **one component**, change its `*Tokens` object. To restyle **the whole plugin**,
  change the semantic layer.

## Colors: `VaultColors`

Each token is a `JBColor.namedColor("VaultLink.…", fallback)`, so a theme can override it.

| Token | Theme key | Fallback (light / dark) | Use |
|---|---|---|---|
| `textPrimary` | platform `Label.foreground` | — | Default text, revealed values |
| `textMuted` | `VaultLink.Text.muted` | `#6B6B6B` / `#9AA0A6` | Hints, counts, empty states, masked/disabled values, inactive session |
| `border` | `VaultLink.Border.default` | platform `JBColor.border()` | Outlined boxes (empty state) |
| `statusNeutral` | `VaultLink.Status.neutral` | `#6B6B6B` / `#9AA0A6` | Mode chip, "latest" chip |
| `statusSuccess` | `VaultLink.Status.success` | `#368947` / `#59A869` | Active session |
| `statusWarning` | `VaultLink.Status.warning` | `#9A7B23` / `#D9A544` | "no match" chip |
| `statusError` | `VaultLink.Status.error` | `#C94F4F` / `#DB5C5C` | Error chips |
| `accent` | `VaultLink.Accent` | `#3B72B0` / `#5C9BD8` | User-driven state: pinned version, edited value |

Theme override example (`*.theme.json`):

```json
{ "ui": { "VaultLink.Status.success": "#3FB950", "VaultLink.Accent": "#58A6FF" } }
```

The fallbacks match IntelliJ's inspection-severity hues, so they meet contrast requirements on the
stock themes.

## Spacing: `VaultSpacing`

The scale is in **unscaled px**: `XXS 2 · XS 4 · SM 6 · MD 8 · LG 12 · XL 16`.

| Where it goes | How to use it |
|---|---|
| Borders / insets | `JBUI.Borders.empty(VaultSpacing.LG)` (JBUI scales it for you) |
| `FlowLayout` / `BorderLayout` gaps, struts | `VaultSpacing.px(VaultSpacing.SM)` (scales it) |
| Fixed sizes | `JBUI.size(w, h)` / `JBUI.scale(token)` |

Conventions:
- Outer padding of a panel or settings page: `LG`.
- Gap between sibling inline items (title + count, chips): `SM`.
- Gap between stacked blocks (header → actions → variables): `MD`.

## Radius: `VaultRadius`

`SM 6` for boxes (empty state) and `PILL 10` for chips. Apply them with `VaultRadius.px(...)`.

## Typography: `VaultTypography`

All styles derive from the platform label font, so they follow the IDE font settings and zoom.
They are functions, so call them when you build the component.

| Style | Base | Use |
|---|---|---|
| `body()` | `JBFont.label()` | Default |
| `title()` | label, bold | Section headings ("Variables") |
| `caption()` | `JBFont.small()` | Counts, secondary text |
| `badge()` | small, bold | Chips (uppercase) |
| `mono()` / `monoBold()` | monospaced, body size | Secret paths, variable keys and values |

## Components

| Builder | Tokens | Notes |
|---|---|---|
| `chip(text, ChipVariant)` | `ChipTokens` | Outlined, uppercase, no fill. Variants: `NEUTRAL`, `OK`, `WARNING`, `ERROR`, `ACCENT` |
| `emptyState(text)` | `EmptyStateTokens` | Rounded outlined box with centered muted text that wraps at 220px |
| `iconButton(icon, tooltip) { }` | — | Borderless and icon-only. The tooltip is required and also sets the accessible name |
| `sectionTitle`, `captionLabel`, `mutedLabel`, `monoLabel` | typography and colors | The standard text roles |
| `hFlow(align, gap, children…)` | spacing | Transparent horizontal row with a scaled gap |
| Variable row (in `SecretVariablesPanel`) | `VariableRowTokens` | 110px minimum key column, mask `••••••••` |
| `VaultSessionStatusPanel` | `SessionStatusTokens` | `statusSuccess` when active, `textMuted` otherwise |

### Component state matrix

| Component | Default | Disabled / excluded | Revealed | Edited |
|---|---|---|---|---|
| Variable key | `monoBold`, `textPrimary` | `textMuted` | — | — |
| Variable value | `mono`, mask, `textMuted` | struck through, `textMuted` | `textPrimary` | + `ACCENT` "edited" chip |
| Icon button | icon | platform-disabled icon | — | — |
| Session status | `textMuted` (inactive) | — | — | `statusSuccess` (active) |

## Forms and dialogs

- Build forms and dialog bodies with the **Kotlin UI DSL v2** (`panel { row("Label:") { … } }`).
  Don't use hand-built `GridLayout`/`BorderLayout` forms. The DSL already applies the platform's
  label column, spacing, and validation.
- Use `JB*` widgets (`JBTextField`, `JBPasswordField`, `JBList`, `JBScrollPane`, `JBCheckBox`)
  instead of their plain Swing equivalents.
- Override `getPreferredFocusedComponent()` so the first field gets keyboard focus.
- Use platform icons (`AllIcons.*`) only. Don't use emoji in the UI.

## Enforcement

`./gradlew checkDesignTokens` runs as part of `check`, and therefore as part of `build` in CI. It
scans `**/ui/**/*.kt` (excluding `ui/design/`) and fails the build on:

- hex or `JBColor.GRAY/GREEN/…` colors → use `VaultColors`
- `Font(Font.…)` / `deriveFont(` → use `VaultTypography`
- `FlowLayout`/`BorderLayout` with literal gaps → use `VaultSpacing.px(...)`
- `Dimension(` / struts with literal sizes → use `JBUI.size` / `JBUI.scale`

## Adding a new token or component

1. Need a new purpose? Add a semantic token in `VaultTokens.kt` with a `VaultLink.*` theme key,
   pointing at an existing primitive (only add a primitive if no hue fits).
2. New reusable widget? Add its `*Tokens` object and builder to `VaultComponents.kt`, referencing
   semantic tokens only.
3. Document it in the tables above.
