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
- Background update checks via WorkManager (battery-conscious, unmetered
  networks only), notifications (updates available + app updated), and
  system/light/dark appearance.

### Changed

- The **Updates** tab lists only apps that actually need an update per their
  configured channel (Stable/Snapshot) or are not installed: before
  downloading, a candidate whose tag-derived `versionCode` is not newer than
  the installed one is no longer offered, so up-to-date apps are neither
  listed nor re-installed. The My Apps tab always shows the installed
  version next to the latest.
- Removed the "Only on unmetered Wi-Fi" settings toggle (it was not usable);
  background checks now always run on unmetered networks.
- Settings: the "Check now" button is aligned with the "Update checks"
  heading in one row.
- Downloadable builds on the GitHub Releases page: every push to `main`
  publishes a signed **Snapshot** APK (prerelease), and every `vX.Y.Z` tag
  publishes a signed release with APK, AAB, and `SHA256SUMS`
  (`.github/workflows/release.yml`).
- Release signing is configurable via `SR_KEYSTORE_*` repository secrets or a
  local git-ignored `keystore.properties`; plain checkouts build unsigned
  release APKs. Never commit keystore material.
