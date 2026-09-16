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

## Releasing a new version

`main` is protected, so a release always goes through a PR first:

1. Bump `version` in `build.gradle.kts`.
2. Move the `## [Unreleased]` entries in `CHANGELOG.md` into a new `## [X.Y.Z] - YYYY-MM-DD`
   section, and update the compare links at the bottom of the file.
3. Commit as `chore(release): X.Y.Z` and open a PR.
4. Once the PR is merged, on an up-to-date `main`, tag the merge commit and push the tag:
   ```bash
   git checkout main && git pull
   git tag -a X.Y.Z -m "X.Y.Z"
   git push origin X.Y.Z
   ```

Pushing that tag is what triggers [`release.yml`](.github/workflows/release.yml): it builds, runs
`verifyPlugin`, extracts the matching `CHANGELOG.md` section, and publishes a GitHub Release with
the plugin ZIP attached.

**Merging the PR does not publish anything by itself.** `release.yml`'s only trigger is a pushed
tag matching `X.Y.Z` (see its `on.push.tags`) — a push/merge to `main` doesn't match that and
doesn't run it. [`build.yml`](.github/workflows/build.yml) is what runs on every push/PR to `main`
(compile + `verifyPlugin`), and it has no permission to create a Release. `release.yml` also
double-checks the tag is actually reachable from `main` before doing anything, so tagging a stray
branch by mistake is a no-op instead of an accidental release.

## Security-sensitive code

Credentials and session tokens are kept in memory only (never written to disk — see
`VaultCredentialsStore`/`VaultSessionStatus`), and retries never happen on 401/403. If your change
touches auth, logging, or the audit trail, make sure a secret's value can never end up in a log,
notification, or exception message.
