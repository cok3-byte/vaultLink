# Contributing to VaultLink

## Getting started

- JDK 21 to build the plugin.
- `./gradlew runIde` launches a sandboxed IntelliJ IDEA instance with the plugin loaded, for
  manual testing.

## Before opening a PR

```bash
./gradlew buildPlugin      # builds the installable plugin ZIP
./gradlew verifyPlugin     # validates compatibility against the declared build range (251-262.*)
```

Both must pass — the `Build` workflow runs them on every push/PR.

If your change is user-visible, add an entry under `## [Unreleased]` in
[CHANGELOG.md](CHANGELOG.md), following [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Commit style

This repo uses [Conventional Commits](https://www.conventionalcommits.org/)-style prefixes:
`feat:`, `fix:`, `docs:`, `ci:`, `chore:`, `refactor:`. Check `git log` for examples.

## Security-sensitive code

Credentials and session tokens are kept in memory only (never written to disk — see
`VaultCredentialsStore`/`VaultSessionStatus`), and retries never happen on 401/403. If your change
touches auth, logging, or the audit trail, make sure a secret's value can never end up in a log,
notification, or exception message.
