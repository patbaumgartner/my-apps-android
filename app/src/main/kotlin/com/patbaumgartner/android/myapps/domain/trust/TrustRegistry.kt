package com.patbaumgartner.android.myapps.domain.trust

/**
 * How a repository publishes installable APKs on its GitHub Releases.
 */
enum class ApkMode {
    /** One architecture-independent APK per release, e.g. `dice-roller-1.0.0.apk`. */
    UNIVERSAL,

    /** One APK per ABI, e.g. `string-telephone-0.1.0-arm64-v8a.apk`. */
    PER_ABI,
}

/**
 * One approved application. The registry is bundled and version-controlled: it is the
 * only source of installable apps. GitHub API discovery can list repositories, but a
 * repository becomes installable only by adding an entry here (see docs/adr/0001).
 *
 * @param repo repository name under [owner], e.g. `dice-roller-android`.
 * @param packageId Android applicationId the APK must carry.
 * @param signerCertSha256 trusted signer certificate SHA-256 fingerprints, colon-separated
 *   uppercase hex. The first entry is the current signer; earlier entries are historical
 *   signers kept so signing-key rotations do not force an uninstall. Never infer a
 *   fingerprint from release notes — take it from the sibling repository's ADR and verify
 *   it with `apksigner verify --print-certs` on a published APK.
 * @param apkMode which APK asset layout the release workflow publishes.
 */
data class ApprovedApp(
    val repo: String,
    val owner: String = "patbaumgartner",
    val packageId: String,
    val displayName: String,
    val description: String,
    val signerCertSha256: List<String>,
    val apkMode: ApkMode,
) {
    init {
        require(repo.matches(Regex("[A-Za-z0-9._-]+"))) { "invalid repo name: $repo" }
        require(packageId.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) {
            "invalid packageId: $packageId"
        }
        require(signerCertSha256.isNotEmpty()) { "at least one trusted signer is required: $repo" }
        signerCertSha256.forEach { fingerprint ->
            require(fingerprint.matches(Regex("[0-9A-F]{2}(:[0-9A-F]{2}){31}"))) {
                "malformed SHA-256 fingerprint for $repo: $fingerprint"
            }
        }
    }

    val releasesUrl: String get() = "repos/$owner/$repo/releases"
    val latestReleaseUrl: String get() = "repos/$owner/$repo/releases/latest"
    val snapshotReleaseUrl: String get() = "repos/$owner/$repo/releases/tags/snapshot"
}

/**
 * The bundled allowlist. To approve a future official app, add an entry here in a
 * reviewed commit: repository, package ID, trusted signer fingerprint(s) from that
 * repository's ADR, and its APK layout.
 *
 * Fingerprint sources (each repository's version-controlled ADR-0001):
 * - dice-roller-android: docs/adr/0001-release-signing-and-distribution.md
 * - spaced-repeater-android, esp32-room-watchdog-android, string-telephone-android:
 *   the `Signer certificate SHA-256` line of their release-signing ADR.
 * - my-apps-android: the release key generated for this repository; the fingerprint is
 *   printed by `keytool -list -v` / `apksigner verify --print-certs` and pinned here.
 *   The keystore itself lives only in repository secrets (MA_KEYSTORE_*), never in git.
 */
object TrustRegistry {
    const val GITHUB_OWNER = "patbaumgartner"

    /** This app's own repository — treated like any other approved app for self-update. */
    const val SELF_REPO = "my-apps-android"

    val apps: List<ApprovedApp> = listOf(
        ApprovedApp(
            repo = "dice-roller-android",
            packageId = "com.patbaumgartner.android.diceroller",
            displayName = "Dice Roller",
            description = "Offline dice roller for board games and tabletop RPGs.",
            signerCertSha256 = listOf(
                "D9:57:90:7F:65:14:F2:43:9A:13:2F:F9:23:A4:46:C3:EC:FB:73:F1:20:96:07:DB:4E:63:1A:45:45:D2:F2:46",
            ),
            apkMode = ApkMode.UNIVERSAL,
        ),
        ApprovedApp(
            repo = "spaced-repeater-android",
            packageId = "com.patbaumgartner.android.spacedrepeater",
            displayName = "Spaced Repeater",
            description = "Offline flashcard study with Markdown decks and SM-2 spaced repetition.",
            signerCertSha256 = listOf(
                "A0:27:DF:5E:5A:6E:55:94:F1:8E:18:6D:58:41:50:EB:E4:D5:52:C1:70:8B:B0:1F:14:49:27:B9:BA:BC:B4:BB",
            ),
            apkMode = ApkMode.UNIVERSAL,
        ),
        ApprovedApp(
            repo = "esp32-room-watchdog-android",
            packageId = "com.patbaumgartner.android.roomwatchdog",
            displayName = "Room Watchdog",
            description = "Android companion for ESP32 Room Watchdog: presence, audio and alerts.",
            signerCertSha256 = listOf(
                "7B:38:EA:6A:1B:4B:86:26:A0:38:41:21:C6:A2:16:4E:00:14:68:F2:F0:A8:9D:AA:47:BC:1B:73:87:41:87:BB",
            ),
            apkMode = ApkMode.UNIVERSAL,
        ),
        ApprovedApp(
            repo = "string-telephone-android",
            packageId = "com.patbaumgartner.android.stringtelephone",
            displayName = "String Telephone",
            description = "One-to-one calls and messages over SIP with end-to-end encryption.",
            signerCertSha256 = listOf(
                "7F:73:86:D7:FC:E8:03:38:E1:8D:C0:DD:8C:04:9C:77:1A:36:F5:36:62:7D:48:6F:C9:E3:90:A7:DE:A6:DD:6A",
            ),
            apkMode = ApkMode.PER_ABI,
        ),
        ApprovedApp(
            repo = SELF_REPO,
            packageId = "com.patbaumgartner.android.myapps",
            displayName = "myApps",
            description = "This app manager. It updates itself through the same verified pipeline.",
            signerCertSha256 = listOf(
                // Release key for this repository (myapps-release.keystore, git-ignored).
                // Pinned here so self-updates verify against the same trust rules.
                "CC:75:A2:DE:4B:86:87:09:49:5E:F3:4F:61:DC:CC:A4:B9:0E:48:70:B7:60:37:13:0F:01:B5:90:17:E1:4F:4D",
            ),
            apkMode = ApkMode.UNIVERSAL,
        ),
    )

    fun findByRepo(repo: String): ApprovedApp? = apps.firstOrNull { it.repo == repo }

    fun findByPackageId(packageId: String): ApprovedApp? = apps.firstOrNull { it.packageId == packageId }

    /** The approved app backing this running process, used for self-update. */
    val self: ApprovedApp get() = findByRepo(SELF_REPO)!!
}
