# Changelog

All notable changes are documented here. The project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Unreleased

No public release has been published yet.

### Added

- Secure app manager for `patbaumgartner`'s approved apps: installs and updates
  only APKs from the bundled, version-controlled allowlist in
  `TrustRegistry`, verified before install (package ID, signer certificate
  fingerprint lineage, SHA-256 against `SHA256SUMS`, minSdk, version, ABI)
  through Android's `PackageInstaller` with explicit user approval.
- Self-update: myApps is itself a registry entry and updates through the same
  verified pipeline.
- Per-app **Stable / Snapshot** release channels; updates are decided by APK
  `versionCode` only — never a downgrade, never a re-offered unchanged snapshot.
- Background update checks via WorkManager (battery-conscious, optional
  Wi-Fi-only), notifications, and system/light/dark appearance.
- Downloadable builds on the GitHub Releases page: every push to `main`
  publishes a signed **Snapshot** APK (prerelease), and every `vX.Y.Z` tag
  publishes a signed release with APK, AAB, and `SHA256SUMS`
  (`.github/workflows/release.yml`).
- Release signing is configurable via `SR_KEYSTORE_*` repository secrets or a
  local git-ignored `keystore.properties`; plain checkouts build unsigned
  release APKs. Never commit keystore material.
