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

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
yamllint .
find . -name '*.sh' -not -path './*/build/*' -exec shellcheck {} +
```

CI (`.github/workflows/ci.yml`) runs the same Gradle gate plus YAML/shell lint.
CodeQL runs in `.github/workflows/security.yml`.

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
  with a signed APK + `SHA256SUMS`.
- Pushing a tag `vX.Y.Z` publishes a **release**: signed APK + `SHA256SUMS`, with
  the signer certificate SHA-256 in the release notes. The tag sets the version
  (`v1.0.0` → versionName `1.0.0`, versionCode `10000`):

  ```bash
  git tag -a v1.0.0 -m "v1.0.0"
  git push origin main v1.0.0
  ```

Both jobs sign with the release key held in the repository secrets
`MA_KEYSTORE_BASE64`, `MA_KEYSTORE_PASSWORD`, `MA_KEY_ALIAS`, and
`MA_KEYSTORE_PASSWORD`; the workflow fails if they are missing. To set or
rotate the key, generate a keystore with `keytool` and upload it with
`gh secret set` — **never commit keystore material**. Keep one backup of the
keystore outside the repository (password manager / HSM); losing it means
updates can no longer be signed with the same key, and users would have to
uninstall and reinstall.

For local signed builds, either export the four `MA_*` environment variables
or create a git-ignored `keystore.properties` with `storeFile`,
`storePassword`, `keyAlias`, `keyPassword`, then run
`./gradlew :app:assembleRelease`.

## Security

Report vulnerabilities privately — see [SECURITY.md](SECURITY.md). Do not open
public issues for security problems.
