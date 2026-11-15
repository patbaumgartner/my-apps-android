# Security Policy

## Reporting a vulnerability

Please **do not** open a public issue for security vulnerabilities.

Report privately to the maintainer (Patrick Baumgartner) via GitHub's private
vulnerability reporting on this repository, or by email as listed on the
maintainer's GitHub profile. You will receive an acknowledgement within 72 hours.

## Scope

myApps installs and updates Android apps. The security-relevant surface is:

- the **trust registry** (`TrustRegistry.kt`) — the allowlist of repositories,
  package IDs and signer certificate fingerprints;
- the **download + verification pipeline** (`ApkSelector`, `ApkVerifier`,
  `Checksums`, `UpdatePipeline`) — everything that runs before an APK reaches
  `PackageInstaller`;
- the **self-update** path, which uses the same pipeline for `my-apps-android`;
- the **release signing key**, which exists only as GitHub repository secrets
  (`SR_KEYSTORE_*`) and never in git.

## Design invariants

- GitHub API discovery is informational only; the bundled allowlist is the sole
  authorization source. No arbitrary URLs, repos, packages or third-party APKs.
- Every APK is verified before install: package ID, signer certificate against
  the trusted lineage, SHA-256 against the release's `SHA256SUMS`, minimum SDK,
  version and ABI compatibility. Verification is fail-closed.
- No silent installs: installation always requires explicit user approval via
  Android's `PackageInstaller`. No tokens or keys are embedded in the app.
- Downloads only over HTTPS; ETags are used for caching; rate limits and offline
  states are handled explicitly.
