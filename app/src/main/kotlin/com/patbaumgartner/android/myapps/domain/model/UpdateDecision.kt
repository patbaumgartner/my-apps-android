package com.patbaumgartner.android.myapps.domain.model

/**
 * Update logic driven by APK `versionCode`, never by tag names.
 *
 * Two decision points:
 * 1. [shouldOffer] — before downloading: skip a candidate identical to the last one
 *    offered (so an unchanged snapshot is never repeatedly offered), always offer when
 *    the app is not installed.
 * 2. [afterDownload] — after the APK is downloaded and verified: install only when the
 *    archive's versionCode is newer than the installed one. This refuses downgrades,
 *    including the Snapshot→Stable switch until a newer Stable exists.
 */
object UpdateDecision {

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
    }

    fun shouldOffer(
        installedVersionCode: Long?,
        lastOfferedFingerprint: String?,
        candidateFingerprint: String,
    ): Offer {
        if (installedVersionCode == null) return Offer.YES
        if (candidateFingerprint == lastOfferedFingerprint) return Offer.SKIP_UNCHANGED
        return Offer.YES
    }

    enum class Install {
        /** versionCode is newer (or the app is not installed): proceed to install. */
        PROCEED,

        /** Archive versionCode <= installed: no update, no downgrade. */
        NO_UPDATE,
    }

    fun afterDownload(installedVersionCode: Long?, archiveVersionCode: Long): Install =
        if (installedVersionCode == null || archiveVersionCode > installedVersionCode) {
            Install.PROCEED
        } else {
            Install.NO_UPDATE
        }

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
