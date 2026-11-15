# my-apps-android

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
- **Updates** — actionable updates and not-installed apps, with progress and
  clear failures.
- **Details** — release notes, per-app **Stable / Snapshot** channel toggle,
  Install/Update actions.
- **Settings** — manual update checks, background checks (battery-conscious via
  WorkManager, Wi-Fi-only option), notifications, appearance (system/light/dark).

## Channels

- **Stable** (default): `GET /repos/{owner}/{repo}/releases/latest`.
- **Snapshot** (opt-in per app): `GET /repos/{owner}/{repo}/releases/tags/snapshot`,
  which must be a prerelease.

Updates are decided by APK `versionCode`, never by tags. The app never downgrades,
never re-offers an unchanged snapshot, and a Snapshot → Stable switch that would
downgrade waits for a newer Stable release (see `UpdateDecision`).

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

## Building

```bash
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Requires JDK 27 and Android SDK with platform 37 / build-tools 36.0.0.

## Releases

Releases are built by GitHub Actions (`.github/workflows/release.yml`) and signed
with a release key stored **only** in repository secrets (`MA_KEYSTORE_*` — see
[CONTRIBUTING.md](CONTRIBUTING.md#releases)):

- push to `main` → `snapshot` prerelease (`my-apps-snapshot.apk` + `SHA256SUMS`)
- tag `vX.Y.Z` → release (`my-apps-X.Y.Z.apk` + `SHA256SUMS`, notes include the
  signer certificate SHA-256)

## Security

Report vulnerabilities privately — see [SECURITY.md](SECURITY.md).

## License

[LICENSE](LICENSE)
