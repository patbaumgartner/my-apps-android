# Contributing to myApps

Thanks for your interest in **myApps**. This project installs and updates only a
curated set of approved apps, so changes to the trust and verification pipeline
get extra scrutiny.

## Workflow

1. Fork the repository and create a feature branch from `main`.
2. Make your change. Keep the domain layer (`domain/`) pure Kotlin and covered
   by JUnit4 tests in `app/src/test/`.
3. Run the full validation gate locally (see below).
4. Open a pull request with a clear description. Security-relevant changes
   (trust registry, verifier, update logic, install path) need a second review.

## Validate

Validation requires JDK 27 (bytecode target 17), Android SDK platform 37.1
(`compileSdkMinor = 1`), Node.js 18 or newer for `node:test`, Bash, jq,
yamllint, and ShellCheck. Set `ANDROID_HOME` as shown, then run the full local
artifact and static gate:

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

CI (`.github/workflows/ci.yml`) runs the same checks and executes instrumented
tests with `connectedDebugAndroidTest`; locally that command needs a running
emulator. Its required `Test, lint, and package` check is an aggregate: it
succeeds only when both `Build and unit tests` and the emulator job succeed.
CodeQL runs in `.github/workflows/security.yml`.

Snapshot→Stable appears as **Manual action only**, never an ordinary update or
automatic uninstall. The warning covers export/backup and app-data erasure;
normal verified upgrades still use `PackageInstaller`.

Dependabot auto-merge uses a read-only preflight and refuses to queue a merge
unless an active `main` ruleset requires `Test, lint, and package`. It reads only
paginated `rules/branches/main` metadata, which is available to `GITHUB_TOKEN`.
Classic branch protection alone is not visible through this endpoint and does
not satisfy the preflight; classic-protection and detailed-ruleset APIs require
Administration access that `GITHUB_TOKEN` does not have. The repository owner
must configure the active main ruleset; the workflow never changes hosted
settings. Only explicit semver-patch and semver-minor updates are eligible;
empty, unknown, and major update types are refused.

Only the final `gh pr merge` uses `AUTO_MERGE_TOKEN`; Dependabot metadata and the
preflight keep using `GITHUB_TOKEN`. The owner must configure a fine-grained
token for this repository with **Contents: write**, **Pull requests: write**,
and **Workflows: write**. An empty/missing secret stops before merge. Merges made
with this token trigger CI and snapshot workflows on `main`, including required
checks and signed snapshot publication.

## Adding an approved app

1. The app must live in a public `patbaumgartner/*-android` repository with a
   release workflow that publishes a signed APK and `SHA256SUMS`.
2. Take the signer certificate SHA-256 fingerprint from that repository's ADR
   (never from unverified release notes) and confirm it on a published APK:
   `apksigner verify --print-certs app.apk`.
3. Add an `ApprovedApp` entry to `TrustRegistry` — repository, package ID,
   display name, description, trusted fingerprint lineage, and APK layout
   (`UNIVERSAL` or `PER_ABI`).
4. Add/adjust unit tests (ABI selection, verification) and open a PR.

## Releases

Downloadable builds are published from `.github/workflows/release.yml` (see
[ADR-0001](docs/adr/0001-trust-registry-and-verification.md)):

- Every push to `main` rebuilds the **Snapshot** prerelease (`snapshot` tag)
  with a signed universal APK + `SHA256SUMS`.
- Pushing a tag `vX.Y.Z` publishes a **release**: signed universal APK + AAB +
  `SHA256SUMS`, with the signer certificate SHA-256 in the release notes. The
  tag sets the version (`v1.0.0` → versionName `1.0.0`, versionCode `10000`):

  ```bash
  git tag -a v1.0.0 -m "v1.0.0"
  git push origin main v1.0.0
  ```

Both jobs sign with the release key held in the repository secrets
`SR_KEYSTORE_BASE64`, `SR_KEYSTORE_PASSWORD`, `SR_KEY_ALIAS`, and
`SR_KEY_PASSWORD`; the workflow fails if they are missing. To set or
rotate the key, generate a keystore with `keytool` and upload it with
`gh secret set` — **never commit keystore material**. Keep one backup of the
keystore outside the repository (password manager / HSM); losing it means
updates can no longer be signed with the same key, and users would have to
uninstall and reinstall.

For local signed builds, either export `SR_KEYSTORE_FILE`,
`SR_KEYSTORE_PASSWORD`, `SR_KEY_ALIAS`, and `SR_KEY_PASSWORD`, or create a
git-ignored `keystore.properties` with `storeFile`,
`storePassword`, `keyAlias`, `keyPassword`, then run
`./gradlew :app:assembleRelease`.

## Security

Report vulnerabilities privately — see [SECURITY.md](SECURITY.md). Do not open
public issues for security problems.
