# myApps

[![CI](https://github.com/patbaumgartner/my-apps-android/actions/workflows/ci.yml/badge.svg)](https://github.com/patbaumgartner/my-apps-android/actions/workflows/ci.yml)
[![Security](https://github.com/patbaumgartner/my-apps-android/actions/workflows/security.yml/badge.svg)](https://github.com/patbaumgartner/my-apps-android/actions/workflows/security.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

**myApps** is a lightweight, secure Android app manager for `patbaumgartner`'s
approved applications. It installs and updates **only** apps from a bundled,
version-controlled allowlist, served from public GitHub Releases — and it
updates **itself** through the same verified pipeline.

- No accounts, no tokens, no arbitrary URLs. GitHub API discovery is informational
  only; the allowlist in
  [`TrustRegistry.kt`](app/src/main/kotlin/com/patbaumgartner/android/myapps/domain/trust/TrustRegistry.kt)
  is the only source of installable apps.
- Every APK is verified before install: package ID, signer certificate against the
  trusted fingerprint lineage, SHA-256 against the release's `SHA256SUMS`,
  minimum SDK, version and ABI compatibility.
- Installs go through Android's `PackageInstaller` with explicit user approval —
  no silent installs.

## Screens

- **My Apps** — all approved apps with icon, description, installed/latest
  versions and status.
- **Updates** — apps that need a normal update/install, plus Snapshot→Stable
  choices marked **Manual action only** with a data-loss warning. Up-to-date apps
  are not listed.
- **Details** — release notes, per-app **Stable / Snapshot** channel toggle,
  normal Install/Update actions, and manual-channel-switch guidance.
- **Settings** — manual update checks, background checks (battery-conscious via
  WorkManager on unmetered networks), notifications, appearance
  (system/light/dark).

## Channels

- **Stable** (default): `GET /repos/{owner}/{repo}/releases/latest`.
- **Snapshot** (opt-in per app): `GET /repos/{owner}/{repo}/releases/tags/snapshot`,
  which must be a prerelease.

Updates are decided by APK `versionCode`, never by tags. Before downloading, a
candidate whose tag-derived versionCode is not newer than the installed one is
not offered at all. The app never downgrades automatically and never re-offers
an unchanged snapshot. One exception: switching from Snapshot back to Stable
while a snapshot is installed keeps the stable choice visible as **Manual action
only**, not an ordinary update. Android has no supported data-preserving in-place
downgrade, so myApps never uninstalls the snapshot automatically. A manual
uninstall erases app data, including decks, presets, credentials, and settings:
export or back up data where available first, then verify the stable APK against
`SHA256SUMS` before installing it. See `UpdateDecision`.

## Trust model

See [docs/adr/0001-trust-registry-and-verification.md](docs/adr/0001-trust-registry-and-verification.md)
and [docs/architecture.md](docs/architecture.md).

1. `TrustRegistry` maps each approved repository to its Android package ID,
   trusted signer certificate SHA-256 fingerprint(s) (historical lineage
   supported) and APK layout (universal vs per-ABI splits).
2. A release is selected per the app's APK rules; per-ABI releases must contain a
   complete split set.
3. The downloaded APK is verified (package ID, signer, SHA-256, minSdk, version)
   before `PackageInstaller` is invoked.
4. Self-update: `my-apps-android` is itself an entry in the registry, so the same
   pipeline verifies and installs myApps updates; pending installs are recovered
   after process death.

## Download

Signed builds are published on the
[Releases](https://github.com/patbaumgartner/my-apps-android/releases) page:

- **Releases** (`vX.Y.Z` tags): signed universal APK and AAB, plus `SHA256SUMS`
  and the signer certificate fingerprint in the release notes.
- **Snapshot** (prerelease, rebuilt on every push to `main`): the latest signed
  universal APK for testing, plus `SHA256SUMS`.

Both use the same release key. Normal upgrades install in place; Snapshot→Stable
uses the manual process above. Verify a download against `SHA256SUMS` before
installing. Sideloading on
Android 8+ requires allowing "Install unknown apps" for the app you open the
APK with.

## Requirements

- Android 8.0 (API 26) or newer
- JDK 27
- Android SDK with platform 37.1 (`compileSdkMinor = 1`) / build-tools 36.0.0

Validation also requires Node.js 18 or newer (`node:test`), Bash, jq, yamllint,
and ShellCheck. The JVM bytecode target remains 17.

Gradle needs the Android SDK path from `ANDROID_HOME` or an `sdk.dir` entry in
`local.properties` (see `local.properties.example`). The first build downloads
the Gradle distribution and Android dependencies.

## Building

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
./gradlew assembleDebugAndroidTest bundleRelease
```

Run the full local artifact and static gate (emulator execution is separate):

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
./gradlew assembleDebugAndroidTest bundleRelease
node scripts/check-ci-wiring.mjs
node --test scripts/check-ci-wiring.test.mjs
node scripts/check-manual-channel-switch.mjs
node --test scripts/auto-merge-preflight.test.mjs
node scripts/check-snapshot-base.mjs
yamllint .
find . -name '*.sh' -not -path './*/build/*' -exec shellcheck {} +
git diff --check
```

A release APK built from a plain source checkout is unsigned. To build a
signed release locally, provide the signing credentials via the
`SR_KEYSTORE_FILE`, `SR_KEYSTORE_PASSWORD`, `SR_KEY_ALIAS`, and
`SR_KEY_PASSWORD` environment variables or a local (git-ignored)
`keystore.properties` file — see [CONTRIBUTING.md](CONTRIBUTING.md#releases)
and [docs/adr/0001-trust-registry-and-verification.md](docs/adr/0001-trust-registry-and-verification.md).
Never commit keystore material.

## Documentation

- [docs/architecture.md](docs/architecture.md) — modules, runtime components,
  dependency boundaries.
- [docs/adr/0001-trust-registry-and-verification.md](docs/adr/0001-trust-registry-and-verification.md) —
  the trust model and APK verification pipeline.
- [CONTRIBUTING.md](CONTRIBUTING.md) — development setup, change workflow,
  validation gates, pull-request expectations.
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) — community expectations and reporting.
- [SECURITY.md](SECURITY.md) — how to report vulnerabilities privately.
- [CHANGELOG.md](CHANGELOG.md) — notable changes per release.
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) — third-party components
  and licences.

## Security

Report vulnerabilities privately as described in [SECURITY.md](SECURITY.md).

## About

Settings shows the app version, open-source notices, and author information.

© 2026 [Patrick Baumgartner](https://patbaumgartner.com/) - Crafted with ❤️

## License

myApps is licensed under the [Apache License 2.0](LICENSE). The bundled
Manrope typeface is licensed under the SIL Open Font License 1.1; see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
