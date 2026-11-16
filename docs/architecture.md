# myApps — Architecture

Single-module Android app (`:app`), package
`com.patbaumgartner.android.myapps`. The domain layer is pure Kotlin (no Android
imports) so all security decisions are JVM-unit-testable; the `data/` layer
adapts Android and GitHub APIs to it.

```
app/src/main/kotlin/com/patbaumgartner/android/myapps/
├── MyAppsApp.kt                  Application + WorkManager Configuration.Provider
├── domain/                       pure Kotlin, unit-tested
│   ├── trust/TrustRegistry.kt    the allowlist: repo → packageId, signer
│   │                             fingerprints (lineage), APK mode
│   ├── model/                    Channel, ReleaseInfo, ApkSelection,
│   │                             UpdateDecision (versionCode-based rules)
│   ├── github/ReleaseParser.kt   channel rules (stable≠prerelease, snapshot=prerelease)
│   └── verify/                   Checksums (SHA-256), ApkVerifier (fail-closed)
├── data/
│   ├── github/GitHubClient.kt    unauthenticated REST client, ETag cache,
│   │                             timeouts, rate-limit/offline, HTTPS-only downloads
│   ├── settings/SettingsRepository.kt  channels, last-offered fingerprints, prefs
│   ├── device/                   InstalledAppRepository (sees only the registry
│   │                             package IDs via manifest <queries>, API 30+),
│   │                             ApkArchiveReader, DeviceInfo
│   ├── install/                  PackageInstaller flow + InstallResultReceiver
│   │                             (user-approved installs, pending-install recovery)
│   ├── UpdatePipeline.kt         UpdateChecker + download/verify/install pipeline
│   ├── work/UpdateCheckWorker.kt WorkManager background checks (battery-conscious, unmetered)
│   └── notify/Notifier.kt        update-available + app-updated notifications
└── ui/                           Compose: My Apps, Updates, Details, Settings;
                                  single ViewModel, brand-cyan theme, Manrope
```

## Data flow

1. **Check** — `UpdateChecker` reads the per-app channel from settings, fetches
   the release (ETag-cached), parses it (`ReleaseParser`), selects the APK
   (`ApkSelector`), computes a candidate fingerprint and tag-derived versionCode,
   and asks `UpdateDecision` whether to offer. A candidate not newer than the
   installed versionCode is never offered (`Offer.NO_UPDATE`), so the Updates
   tab lists only apps that actually need updating.
2. **Download** — `GitHubClient.download` streams the selected APK over HTTPS
   only, with progress callbacks.
3. **Verify** — `ApkArchiveReader` reads package ID / versionCode / minSdk /
   signer from the APK archive; `Checksums` hashes the file; `ApkVerifier`
   compares everything against `TrustRegistry` + `SHA256SUMS`. Any failure aborts
   the install with a clear message.
4. **Install** — `InstallRepository` hands the verified APK to
   `PackageInstaller`; the user approves; `InstallResultReceiver` records the
   outcome and pending installs survive process death.

## Key decisions

See [ADR-0001](adr/0001-trust-registry-and-verification.md) for the trust model.
Notable deviations from the sibling `dice-roller-android` patterns:

- **Networking** uses `HttpURLConnection` + `org.json` instead of adding an HTTP
  library — the app makes only a handful of GitHub API calls and needs ETag
  caching, not a full client.
- **No AAB/Play publishing**: myApps is sideloaded only, so the release workflow
  publishes a universal APK + `SHA256SUMS` (no `.aab`).
- **Self-update** is in-scope: `my-apps-android` is a registry entry and uses the
  same pipeline, which dice-roller does not do.
- **Per-ABI split APKs** (`PER_ABI` mode) are supported for String Telephone;
  universal APKs remain the default for the other apps.

## Testing

JUnit4 unit tests under `app/src/test/` cover the trust registry, release parsing
and channel rules, versionCode/channel update decisions, ABI selection and split
set completeness, checksum parsing, and the fail-closed verifier. Run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```
