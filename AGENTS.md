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

Gradle needs the SDK location from `ANDROID_HOME` or `sdk.dir` in
`local.properties`, or it stops with `SDK location not found`. Builds on
JDK 27, same as CI (bytecode target stays 17).

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
yamllint .
find . -name '*.sh' -not -path './*/build/*' -exec shellcheck {} +
```

That is the core CI gate — run it before claiming a change is done.

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
decision logic out of `domain/`.

## Trust invariants (do not weaken)

- The bundled `TrustRegistry` is the **only** authorization source. GitHub API
  discovery is informational; never let a repo become installable without a
  reviewed registry entry.
- Verification (`ApkVerifier`) is fail-closed: package ID, signer certificate
  against the trusted lineage, SHA-256 vs `SHA256SUMS`, minSdk, version and ABI
  must all pass before `PackageInstaller` is invoked. No silent installs.
- Never infer a signer fingerprint from release notes; take it from the sibling
  repository's ADR and confirm with `apksigner verify --print-certs`.
- Updates are decided by APK `versionCode` only — never downgrade, never re-offer
  an unchanged snapshot (see `UpdateDecision`).

## Releases

Signed builds are published by `.github/workflows/release.yml` — see
CONTRIBUTING.md "Releases" and the ADR. Snapshot (`snapshot` tag, prerelease)
on every push to `main`; `vX.Y.Z` tags publish stable releases. Both sign with
the `SR_KEYSTORE_*` repository secrets and publish `SHA256SUMS`.
