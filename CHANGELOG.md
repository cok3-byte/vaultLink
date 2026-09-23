# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). While the major version is
`0`, minor releases may include breaking changes, per semver's initial-development clause.

## [Unreleased]

### Added

- Tool Window: each variable row can now be disabled (excluded from injection, reversibly — the row
  stays visible, greyed and struck through) or edited (overrides the value that gets injected,
  marked with an "edited" chip and revertible to Vault's value). Changes apply immediately to
  whichever destination is active (live Run Config injection and/or `.env`), without a refetch. The
  secret itself is never modified in Vault, and edited values live only in memory — lost when the
  IDE closes.
- Tool Window: an **Apply to:** selector in the Advanced section that overrides, per project, the
  global "Environment Variables" strategy from Settings → Tools → VaultLink. A new `Both` strategy
  applies the secret to both the live Run Config injection and the `.env` file, matching what the
  README already described.

### Fixed

- Tool Window: **Auto** now really decides between live Run/Debug injection and a `.env` file based
  on whether the project has a JVM-compatible Run Configuration — previously it always wrote `.env`
  regardless.

## [1.2.1] - 2026-09-16

### Fixed

- Mount and secret-version picker dialogs: replaced the now-deprecated
  `SimpleListCellRenderer.create(...)` factory (scheduled for removal in a future IntelliJ Platform
  release) with a direct `SimpleListCellRenderer` subclass — same rendering, no deprecated API.

## [1.2.0] - 2026-09-16

### Added

- Tool Window: title-bar actions (Fetch secret, Choose version, Login/Logout, Settings) that stay
  reachable while the panel is scrolled.
- Tool Window: a "copy all" action next to the existing reveal toggle in the variables list.
- Settings: new **Advanced** group exposing the connect/read timeout, cache TTL, and OIDC
  callback path fields, previously persisted but not reachable from any UI.
- Settings: inline validation on Vault URL (required, must start with `http://`/`https://`) and
  Namespace (no spaces); Callback port is now constrained to 1–65535.

### Changed

- Tool Window: reordered around the redesigned hierarchy — session and resolved secret lead in a
  header card, Fetch secret is the primary action right below it, Variables comes next, and
  Mode/Browse/Run Config Target move into a collapsed **Advanced** section.
- Tool Window: the resolved secret now displays as `mount.secret` instead of `mount=X, secret=Y`.
- Variables panel: the reveal toggle uses a standard icon instead of an emoji.
- Settings: "Vault Server" group renamed to **Connection**; selecting Token no longer leaves
  Authentication empty (it now explains where the token is entered); OIDC and Userpass are marked
  not implemented yet.
- Mount and secret-version picker dialogs: the selected row is now visibly highlighted.

## [1.1.0] - 2026-08-12

### Added

- Tool Window: explicit **Login** and **Logout** actions, so a session can be started/ended
  without needing to fetch a secret first.
- Tool Window: per-variable copy-to-clipboard button in the masked variables view — copies the
  real value even while it's hidden.
- Tool Window: content now scrolls, so it stays usable as more groups/variables are added.
- Plugin marketplace icon (`pluginIcon.png` / `pluginIcon@2x.png`), shown in Settings → Plugins.

### Changed

- Tool Window: "Action" group renamed to "Actions" and reorganized into a 2×2 grid (Login/Fetch
  secret, Choose version/Logout) with icons for each action.
- Session status summary no longer shows an expiry countdown — just whether a session is active
  and via which method.
- `plugin.xml` vendor URL now points to the project's GitHub repository instead of a placeholder.

## [1.0.0] - 2026-08-12

### Added

- Detect a `<mount>.<secret>` pattern in the project name and resolve it against a HashiCorp
  Vault KV v2 secrets engine.
- Four authentication methods: OIDC (browser login), LDAP, Userpass, and Token.
- Apply fetched secrets as environment variables via live injection into JVM Run/Debug
  Configurations and/or a generated `.env` file, with an `AUTO` / `RUN_CONFIG_ONLY` /
  `DOTENV_ONLY` strategy setting.
- Global Settings page (Settings → Tools → VaultLink): grouped sections, fields that show/hide
  based on the selected auth method, a "Test login" action, and a live connection-status
  indicator.
- Tool Window: detected mount/secret, session indicator, "Fetch secret", "Choose version..."
  (pin a specific KV v2 version or reset to latest), and a masked variables view with a
  show/hide toggle.
- In-memory-only credential and session storage (never persisted to disk).
- TLS support for a custom internal CA, with no option to bypass certificate verification.
- Retry with exponential backoff on network/5xx errors only — never on 401/403.
- Audit logging whose event type structurally has no field for a secret's value.
- Verified compatible with IntelliJ Platform builds `251` through `262.*` (2025.1–2026.2) via the
  JetBrains Plugin Verifier.

[Unreleased]: https://github.com/cok3-byte/vaultLink/compare/1.2.1...HEAD
[1.2.1]: https://github.com/cok3-byte/vaultLink/compare/1.2.0...1.2.1
[1.2.0]: https://github.com/cok3-byte/vaultLink/compare/1.1.0...1.2.0
[1.1.0]: https://github.com/cok3-byte/vaultLink/compare/1.0.0...1.1.0
[1.0.0]: https://github.com/cok3-byte/vaultLink/releases/tag/1.0.0
