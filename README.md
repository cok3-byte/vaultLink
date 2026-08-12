# VaultLink

An IntelliJ IDEA plugin that detects a [HashiCorp Vault](https://www.vaultproject.io/) mount/secret
encoded in the project name, authenticates against Vault, and exposes the secret's keys as
environment variables for running and debugging the project — no more copy-pasting values out of
the Vault UI/CLI.

## How it works

Name your project `<mount>.<secret>` (e.g. `team-ops.example-secret` → mount
`team-ops`, secret `example-secret`). VaultLink detects the pattern, logs you into
Vault, reads the secret from a **KV v2** engine, and applies its keys as environment variables —
either injected live into your Run/Debug Configuration, written to a `.env` file, or both.

## Features

- **Four authentication methods**, configurable per IDE installation: OIDC (browser login against
  your corporate IdP), LDAP, Userpass, and Token (bring an already-issued token).
- **Two ways to apply variables**: live injection into JVM Run/Debug Configurations (never
  written to the run configuration's XML) and/or a generated `.env` file — pick one, the other,
  or let it auto-decide.
- **Version picker**: pin a specific KV v2 secret version, or leave it on the default (latest).
- **Masked variables view** in the Tool Window, with a show/hide toggle — values stay hidden
  until you choose to reveal them.
- **Session indicator** showing whether you're currently authenticated, visible in both Settings
  and the Tool Window.
- **Enterprise-oriented hardening**: credentials and session tokens live in memory only (never on
  disk), TLS only extends via a custom CA (no certificate-verification bypass), retries never
  happen on 401/403, and the audit log structurally cannot record a secret's value.

## Requirements

- IntelliJ IDEA 2025.1 or newer (Community or Ultimate), verified compatible up to 2026.2
  (build range `251`–`262.*`)
- JDK 21 to build the plugin

## Installation

VaultLink isn't published to the JetBrains Marketplace (by design — see the plugin's own
description). Build it from source and install it manually:

```bash
./gradlew buildPlugin
```

This produces `build/distributions/vaultlink-<version>.zip`. In IntelliJ IDEA, go to
**Settings → Plugins → ⚙️ → Install Plugin from Disk...** and select that file.

## Configuration

Open **Settings → Tools → VaultLink**:

1. **Vault Server** — set the Vault URL, optional namespace (Vault Enterprise), and an optional
   custom CA certificate.
2. **Authentication** — pick a method; only the fields relevant to it are shown. Use
   **Test login** to validate your setup without needing a project open.
3. **Environment Variables** — choose how secrets get applied: auto-detect, Run Config only,
   `.env` only, or always both.

This configuration is global (Application-level), shared by every project open in that IDE
installation.

## Usage

1. Open a project named `<mount>.<secret>`.
2. Open the **VaultLink** Tool Window (right sidebar).
3. Click **Fetch secret** — VaultLink authenticates if needed and applies the secret per your
   configured strategy.
4. Optionally click **Choose version...** to pin a specific version, or use the masked variables
   view's eye toggle to inspect what was fetched.

## Development

```bash
./gradlew buildPlugin      # build the installable plugin ZIP
./gradlew runIde           # launch a sandbox IntelliJ instance with the plugin loaded
./gradlew verifyPlugin     # validate compatibility against the declared build range
```

## License

Licensed under the [Apache License 2.0](LICENSE).
