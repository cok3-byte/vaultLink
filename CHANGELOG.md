# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). While the major version is
`0`, minor releases may include breaking changes, per semver's initial-development clause.

## [Unreleased]

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

[Unreleased]: https://github.com/cok3-byte/vaultLink/compare/1.0.0...HEAD
[1.0.0]: https://github.com/cok3-byte/vaultLink/releases/tag/1.0.0
