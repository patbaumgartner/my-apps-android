# ADR-0001: Trust Registry and APK Verification

- **Status:** accepted
- **Date:** 2026-11-15
- **Deciders:** maintainer (patbaumgartner)

## Context

myApps installs and updates Android apps. The security requirement is that **only
approved applications from `patbaumgartner`'s public GitHub repositories** can
ever be installed, and that every APK is verified before installation. The app
must also update itself through the same pipeline.

GitHub Releases of the sibling `*-android` repositories are public, so "the file
came from GitHub" is not a trust decision — anyone can upload to a public repo's
releases if the repo were compromised. The app therefore needs a **bundled,
version-controlled allowlist** (trust registry) that is the sole authorization
source, plus **full pre-install verification** of every download.

## Decision

### 1. Bundled trust registry (`TrustRegistry`)

`TrustRegistry.kt` is a version-controlled list of `ApprovedApp` entries. Each
entry maps:

- `repo` (+ `owner`, always `patbaumgartner`) → the GitHub repository;
- `packageId` → the Android application ID the APK must carry;
- `signerCertSha256` → trusted signer certificate SHA-256 fingerprints
  (colon-separated uppercase hex). The first entry is the current signer;
  earlier entries are a **historical lineage** so signing-key rotations do not
  force an uninstall or lose data;
- `apkMode` → `UNIVERSAL` (one architecture-independent APK) or `PER_ABI`
  (one APK per ABI, e.g. String Telephone).

GitHub API repository discovery is **informational only** — a repository becomes
installable solely by adding a reviewed entry here. There are no arbitrary URLs,
repos, packages or third-party APK sources anywhere in the app.

Approving a future official app = adding one entry in a reviewed commit. The
fingerprint must come from the sibling repository's own ADR and be confirmed on a
published APK with `apksigner verify --print-certs` — never inferred from
unverified release notes.

### 2. Channels

Per app, independently:

- **Stable** (default): `GET /repos/{owner}/{repo}/releases/latest` — must not be
  a prerelease (GitHub guarantees this; still rejected defensively).
- **Snapshot** (opt-in): `GET /repos/{owner}/{repo}/releases/tags/snapshot` —
  must be a prerelease.

### 3. Update decisions by `versionCode`, never by tags

`UpdateDecision.shouldOffer(installedVersionCode, lastOfferedFingerprint,
candidateFingerprint, candidateVersionCode)` decides offers:

- offer only when the candidate `versionCode` is strictly greater than the
  installed one — **never downgrade**;
- never re-offer an unchanged build (fingerprint match → skip);
- exception: a stable (`vX.Y.Z`) candidate while a snapshot is installed is the
  Snapshot → Stable channel switch and is offered even with a lower versionCode.

Snapshot `versionCode` is `2_000_000_000 + GITHUB_RUN_NUMBER`; stable
`vX.Y.Z` maps to `major*10000 + minor*100 + patch`, so snapshots always sort
above stables of the same era. The two constants (`SNAPSHOT_VERSION_CODE_BASE`
in `UpdateDecision.kt`, the `2000000000` literal in `release.yml`) must stay in
sync — `scripts/check-snapshot-base.mjs` fails CI otherwise.

After download, `UpdateDecision.afterDownload` re-checks against the APK's own
versionCode. The Snapshot → Stable switch returns `MANUAL_ACTION_REQUIRED` for
all apps, including myApps. Android has no supported data-preserving in-place
downgrade API, and automatic uninstall would erase app data, so the app never
calls `PackageInstaller.uninstall` or requests `REQUEST_DELETE_PACKAGES`. The UI
keeps the choice visible as **Manual action only** and warns the user to export
or back up decks, presets, credentials, and settings where available before a
manual uninstall and verified stable installation. Every other older archive is
refused; normal upgrades continue through the verified install pipeline.

### 4. APK selection (`ApkSelector`)

- Only `.apk` assets are candidates; `.aab` and checksum files are never installed.
- `UNIVERSAL`: exactly one non-ABI-suffixed APK must exist; ambiguity is rejected.
- `PER_ABI`: one APK per supported ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`,
  `x86`); the best matching device ABI wins; an **incomplete split set is
  rejected**.
- Every release must carry a `SHA256SUMS` asset.

### 5. Pre-install verification (`ApkVerifier`) — fail-closed

Before `PackageInstaller` is invoked, the downloaded APK must pass **all** of:

1. package ID equals the registry entry;
2. APK is signed and the signer certificate SHA-256 matches one of the trusted
   fingerprints (lineage);
3. SHA-256 of the file matches the release's `SHA256SUMS` entry;
4. APK `minSdk` ≤ device SDK;
5. ABI compatibility (per-ABI releases require a device ABI in the split set).

### 6. Installation

Android `PackageInstaller` with explicit **user approval** — no silent installs.
No tokens or keys are embedded; the GitHub REST API is used unauthenticated
(60 req/h per IP, ETag-cached, timeouts, rate-limit/offline handling). Pending
installs are recovered after process death (`InstallResultReceiver`).

### 7. Self-update

`my-apps-android` is itself an entry in `TrustRegistry` (with its own release
key's certificate fingerprint pinned as the trust anchor). Self-updates flow
through the identical selection → download → verification → PackageInstaller
pipeline. The release keystore exists only as `SR_KEYSTORE_*` repository secrets,
never in git.

## Consequences

- Adding an app requires a reviewed code change — deliberate friction for a
  security boundary.
- A compromised GitHub account or repo cannot push a malicious update: wrong
  signer or package ID fails verification.
- A tampered download fails the SHA-256 check.
- Signing-key rotation is supported without uninstall: add the new fingerprint
  to the lineage list in the same release that introduces it.
- Trade-off: the app cannot install arbitrary apps by design; that is the point.
