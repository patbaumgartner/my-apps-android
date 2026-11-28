# Changelog

All notable changes are documented here. The project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Unreleased

### Fixed

- Snapshot→Stable remains visible while a snapshot is installed, but is now
  **Manual action only** for every app. The previous automatic
  `PackageInstaller.uninstall` → reinstall path could erase decks, presets,
  credentials, and settings with no warning. That path, its receiver, and the
  delete-packages permission were removed. The UI now tells users to export/back
  up where available before a manual uninstall and verified stable installation;
  normal verified upgrades are unchanged. `scripts/check-snapshot-base.mjs`
  keeps the snapshot versionCode base in sync.
- CI wiring no longer pins exact dependency, Gradle, or wrapper-checksum values,
  so routine version updates do not fail structural checks. SDK levels and the
  presence of a wrapper SHA-256 remain enforced.
- Dependabot auto-merge now accepts only explicit semver-patch or semver-minor
  update types; empty, unknown, and major classifications fail closed. Its
  preflight requires an active main ruleset through metadata-readable branch
  rules; classic branch protection alone is not visible to `GITHUB_TOKEN`. Only
  the merge step uses the guarded `AUTO_MERGE_TOKEN`, whose fine-grained
  Contents/Pull requests/Workflows write permissions allow merges to trigger CI
  and signed snapshots.

## [0.2.0] - 2026-11-16

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

### Fixed

- Installed sibling apps showed "Not installed": on Android 11+ package
  visibility is filtered and `AndroidManifest.xml` had no `<queries>` block, so
  the PackageManager could not see them (myApps itself was always visible, which
  masked the bug). The manifest now declares exactly the approved registry
  package IDs; a unit test keeps the two in sync.
- The version line no longer prints a duplicated label ("Latest: Snapshot ·
  Snapshot"): the channel label is omitted when it matches the release name.
- Apps whose candidate build was already offered showed "Latest: —" because the
  `Unchanged` check outcome did not carry the release; it now does, so the
  latest tag is always visible.
- Downloadable builds on the GitHub Releases page: every push to `main`
  publishes a signed **Snapshot** APK (prerelease), and every `vX.Y.Z` tag
  publishes a signed release with APK, AAB, and `SHA256SUMS`
  (`.github/workflows/release.yml`).
- Release signing is configurable via `SR_KEYSTORE_*` repository secrets or a
  local git-ignored `keystore.properties`; plain checkouts build unsigned
  release APKs. Never commit keystore material.

## [0.1.0] - 2026-11-15

First public release.
