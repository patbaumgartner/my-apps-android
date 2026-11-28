# myApps — Agent Guide

Secure Android app manager for `patbaumgartner`'s approved apps. Start with
[README.md](README.md) for the product, [docs/architecture.md](docs/architecture.md)
for the design, [docs/adr/0001-trust-registry-and-verification.md](docs/adr/0001-trust-registry-and-verification.md)
for the trust model, and [CONTRIBUTING.md](CONTRIBUTING.md) for the contributor
workflow. This file only covers what those don't, or what is easy to get wrong.

This guide describes conventions as they are today. If the trust registry, the
verification pipeline, or the update-decision rules ever change, correct this
file in the same commit — a stale guide misleads rather than merely ages.

## Build and validate

Validation requires JDK 27 (bytecode target 17), Android SDK platform 37.1
(`compileSdkMinor = 1`), Node.js 18 or newer for `node:test`, Bash, jq,
yamllint, and ShellCheck. Gradle needs the SDK location from `ANDROID_HOME` or
`sdk.dir` in `local.properties`, or it stops with `SDK location not found`.

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

That is the full local artifact and static gate — run it before claiming a
change is done. Instrumented UI tests also execute in CI via
`connectedDebugAndroidTest`; locally that command needs a running emulator.
`assembleDebugAndroidTest` only proves the tests compile and package.

## Build traps

- **Do not apply `org.jetbrains.kotlin.android`.** AGP 9 has Kotlin built in
  and the build fails with "no longer required since AGP 9.0".
- The configuration cache is on; a change that reads files at configuration
  time can break it.
- The release signing config in `app/build.gradle.kts` activates only when
  `SR_KEYSTORE_FILE` (or a local git-ignored `keystore.properties`) is present,
  so plain checkouts keep building unsigned release APKs. **Never commit
  keystore material** (`myapps-release.keystore` is git-ignored).

## Keep the domain layer JVM-testable

Everything under `domain/` is pure Kotlin — no Android imports — so trust
enforcement, release parsing, channel/version decisions, ABI selection and
checksum parsing are covered by plain JUnit4 tests under `app/src/test/`. The
`data/` layer adapts Android/GitHub APIs to those domain types; do not move
decision logic out of `domain/`. The Compose UI is covered by instrumented
suites in `app/src/androidTest` (`MyAppsAppTest` and `ManualChannelSwitchTest`,
run via `connectedDebugAndroidTest` in CI).

## Trust invariants (do not weaken)

- The bundled `TrustRegistry` is the **only** authorization source. GitHub API
  discovery is informational; never let a repo become installable without a
  reviewed registry entry.
- Verification (`ApkVerifier`) is fail-closed: package ID, signer certificate
  against the trusted lineage, SHA-256 vs `SHA256SUMS`, minSdk, version and ABI
  must all pass before `PackageInstaller` is invoked. No silent installs.
- Never infer a signer fingerprint from release notes; take it from the sibling
  repository's ADR and confirm with `apksigner verify --print-certs`.
- Every `TrustRegistry` packageId must also be declared in the `<queries>` block of
  `app/src/main/AndroidManifest.xml`: on API 30+ package visibility is filtered, so
  without it the installed sibling apps cannot be seen and show as "Not installed"
  (the app always sees itself, which masks the bug for myApps). `TrustRegistryTest`
  enforces the sync — update both in the same commit when approving a new app.
- Updates are decided by APK `versionCode` only — never downgrade automatically,
  never re-offer an unchanged snapshot (see `UpdateDecision`). Before downloading,
  a candidate whose tag-derived versionCode (`UpdateDecision.versionCodeFromTag`;
  `null` for the `snapshot` tag, where only the fingerprint rule applies) is not
  newer than the installed one is rejected with `Offer.NO_UPDATE`. The exception is
  a stable (`vX.Y.Z`) candidate while a snapshot is installed: Snapshot→Stable
  remains visible as **Manual action only**, never as an ordinary update. Android
  has no supported data-preserving in-place downgrade, so the app must never call
  `PackageInstaller.uninstall`. The UI warns that a manual uninstall erases app
  data (decks, presets, credentials/settings) and tells users to export/back up
  where available before installing a verified stable APK. Normal verified
  upgrades still use `PackageInstaller`. `SNAPSHOT_VERSION_CODE_BASE` in
  `UpdateDecision.kt` and the `2000000000` literal in
  `.github/workflows/release.yml` must stay in sync —
  `scripts/check-snapshot-base.mjs` enforces it in CI.

## Releases

Signed builds are published by `.github/workflows/release.yml` — see
CONTRIBUTING.md "Releases" and the ADR. Snapshot (`snapshot` tag, prerelease)
on every push to `main`; `vX.Y.Z` tags publish stable releases. Both sign with
the `SR_KEYSTORE_*` repository secrets and publish `SHA256SUMS`.
