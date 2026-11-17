package com.patbaumgartner.android.myapps.domain.model

/**
 * Update logic driven by APK `versionCode`, never by tag names.
 *
 * Two decision points:
 * 1. [shouldOffer] — before downloading: always offer when the app is not installed;
 *    when installed, never offer a candidate whose versionCode is not newer
 *    ([Offer.NO_UPDATE], so up-to-date apps are not listed or re-installed), and skip a
 *    candidate identical to the last one offered (so an unchanged snapshot is never
 *    repeatedly offered). The single exception is a stable (`vX.Y.Z`) candidate while a
 *    snapshot is installed: that is the user switching channels back to Stable, so it is
 *    offered even though its versionCode is lower (see [isDowngradeInstall]).
 * 2. [afterDownload] — after the APK is downloaded and verified: install only when the
 *    archive's versionCode is newer than the installed one, or when a stable release is
 *    installed over a snapshot (the channel switch, applied as uninstall + reinstall
 *    because Android cannot downgrade in place). Every other downgrade is refused.
 */
object UpdateDecision {

    /**
     * Base of snapshot versionCodes (`2_000_000_000 + GITHUB_RUN_NUMBER`, set by the
     * release workflow), so any versionCode at or above this value is a snapshot build.
     * Must stay in sync with the `2000000000` literal in `.github/workflows/release.yml` —
     * `scripts/check-snapshot-base.mjs` enforces that in CI.
     */
    const val SNAPSHOT_VERSION_CODE_BASE = 2_000_000_000L

    /** True when [versionCode] belongs to a snapshot build (`>= [SNAPSHOT_VERSION_CODE_BASE]`). */
    fun isSnapshotVersionCode(versionCode: Long): Boolean = versionCode >= SNAPSHOT_VERSION_CODE_BASE

    /**
     * Identity of a candidate release build: release id + selected asset id + tag.
     * Two checks seeing the same fingerprint are looking at the same unchanged build.
     */
    fun candidateFingerprint(release: ReleaseInfo, apkAssetId: Long): String = "${release.id}:$apkAssetId:${release.tag}"

    enum class Offer {
        /** Download, verify and prompt installation. */
        YES,

        /** Same build as the last one offered while the app was installed; do not re-offer. */
        SKIP_UNCHANGED,

        /** Candidate versionCode is not newer than the installed one; nothing to do. */
        NO_UPDATE,
    }

    /**
     * @param candidateVersionCode versionCode derived from the candidate tag
     *   ([versionCodeFromTag]); `null` when the tag carries no version (e.g. `snapshot`),
     *   in which case only the fingerprint rule applies. A non-null value also marks the
     *   candidate as a stable release, which unlocks the Snapshot→Stable channel switch.
     */
    fun shouldOffer(
        installedVersionCode: Long?,
        lastOfferedFingerprint: String?,
        candidateFingerprint: String,
        candidateVersionCode: Long? = null,
    ): Offer {
        if (installedVersionCode == null) return Offer.YES
        val channelSwitch = candidateVersionCode != null && isSnapshotVersionCode(installedVersionCode)
        if (!channelSwitch && candidateVersionCode != null && candidateVersionCode <= installedVersionCode) {
            return Offer.NO_UPDATE
        }
        if (candidateFingerprint == lastOfferedFingerprint) return Offer.SKIP_UNCHANGED
        return Offer.YES
    }

    enum class Install {
        /** versionCode is newer, the app is not installed, or this is the Snapshot→Stable channel switch: proceed. */
        PROCEED,

        /** Archive versionCode <= installed and not a channel switch: no update, no downgrade. */
        NO_UPDATE,
    }

    /**
     * @param stableRelease true when the candidate is a stable (`vX.Y.Z`) release, i.e. the
     *   user switched the channel from Snapshot back to Stable. Android has no in-place
     *   downgrade, so this case is applied as uninstall + reinstall by the caller
     *   ([isDowngradeInstall]); every other older archive is refused.
     */
    fun afterDownload(installedVersionCode: Long?, archiveVersionCode: Long, stableRelease: Boolean = false): Install =
        when {
            installedVersionCode == null || archiveVersionCode > installedVersionCode -> Install.PROCEED
            stableRelease && isSnapshotVersionCode(installedVersionCode) -> Install.PROCEED
            else -> Install.NO_UPDATE
        }

    /**
     * True when installing [candidateVersionCode] over [installedVersionCode] is the
     * Snapshot→Stable channel switch: a lower versionCode over an installed snapshot.
     * Android refuses downgrades at install time, so the caller must uninstall first and
     * reinstall the verified APK afterwards.
     */
    fun isDowngradeInstall(installedVersionCode: Long?, candidateVersionCode: Long): Boolean =
        installedVersionCode != null &&
            candidateVersionCode <= installedVersionCode &&
            isSnapshotVersionCode(installedVersionCode)

    /**
     * Derives a display versionCode from a `vX.Y.Z` tag using the ecosystem release
     * scheme (major*10000 + minor*100 + patch), matching the release workflow. Used for
     * pre-filtering and display only; the authoritative comparison always uses the APK's
     * own versionCode.
     */
    fun versionCodeFromTag(tag: String): Long? {
        val match = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(tag.trim()) ?: return null
        val (major, minor, patch) = match.destructured
        return major.toLong() * 10000 + minor.toLong() * 100 + patch.toLong()
    }
}
