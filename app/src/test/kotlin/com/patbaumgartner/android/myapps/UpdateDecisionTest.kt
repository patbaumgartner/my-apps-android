package com.patbaumgartner.android.myapps

import com.patbaumgartner.android.myapps.domain.model.UpdateDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDecisionTest {

    @Test
    fun `not installed always offers`() {
        assertEquals(UpdateDecision.Offer.YES, UpdateDecision.shouldOffer(null, "fp", "fp"))
        assertEquals(UpdateDecision.Offer.YES, UpdateDecision.shouldOffer(null, null, "fp"))
    }

    @Test
    fun `unchanged candidate is not offered again when installed`() {
        assertEquals(UpdateDecision.Offer.SKIP_UNCHANGED, UpdateDecision.shouldOffer(10000, "fp", "fp"))
    }

    @Test
    fun `new candidate is offered when installed`() {
        assertEquals(UpdateDecision.Offer.YES, UpdateDecision.shouldOffer(10000, "fp-old", "fp-new"))
        assertEquals(UpdateDecision.Offer.YES, UpdateDecision.shouldOffer(10000, null, "fp"))
    }

    @Test
    fun `candidate with equal versionCode is not offered when installed`() {
        assertEquals(UpdateDecision.Offer.NO_UPDATE, UpdateDecision.shouldOffer(10203, "fp-old", "fp-new", 10203))
        // Even the same fingerprint: an already-installed versionCode is never re-offered.
        assertEquals(UpdateDecision.Offer.NO_UPDATE, UpdateDecision.shouldOffer(10203, "fp", "fp", 10203))
    }

    @Test
    fun `candidate with older versionCode is not offered when installed stable (no downgrade)`() {
        assertEquals(UpdateDecision.Offer.NO_UPDATE, UpdateDecision.shouldOffer(20000, null, "fp", 10203))
    }

    @Test
    fun `stable candidate is offered over an installed snapshot (channel switch back to stable)`() {
        // Installed snapshot 2_000_000_005; stable v1.2.3 = 10203 is lower, but switching
        // channels back to Stable must still be offered (applied as uninstall + reinstall).
        assertEquals(UpdateDecision.Offer.YES, UpdateDecision.shouldOffer(2_000_000_005L, null, "fp", 10203))
        // The fingerprint rule still wins once this build was offered.
        assertEquals(UpdateDecision.Offer.SKIP_UNCHANGED, UpdateDecision.shouldOffer(2_000_000_005L, "fp", "fp", 10203))
    }

    @Test
    fun `candidate with newer versionCode is offered when installed`() {
        assertEquals(UpdateDecision.Offer.YES, UpdateDecision.shouldOffer(10000, "fp-old", "fp-new", 10203))
    }

    @Test
    fun `already-offered build stays skipped even when its versionCode is newer`() {
        // The fingerprint rule wins for a build the user already chose to install:
        // it is not nagged again until a new build appears.
        assertEquals(UpdateDecision.Offer.SKIP_UNCHANGED, UpdateDecision.shouldOffer(10000, "fp", "fp", 10203))
    }

    @Test
    fun `unversioned candidate tag falls back to the fingerprint rule`() {
        // Snapshot tag yields no versionCode: unchanged snapshot is skipped, new one offered.
        assertEquals(UpdateDecision.Offer.SKIP_UNCHANGED, UpdateDecision.shouldOffer(10000, "fp", "fp", null))
        assertEquals(UpdateDecision.Offer.YES, UpdateDecision.shouldOffer(10000, "fp-old", "fp-new", null))
        // A new snapshot is also offered over an installed snapshot (fingerprint differs).
        assertEquals(UpdateDecision.Offer.YES, UpdateDecision.shouldOffer(2_000_000_005L, "fp-old", "fp-new", null))
    }

    @Test
    fun `not installed always offers regardless of candidate versionCode`() {
        assertEquals(UpdateDecision.Offer.YES, UpdateDecision.shouldOffer(null, "fp", "fp", 1))
    }

    @Test
    fun `snapshot versionCode detection follows the release workflow base`() {
        assertTrue(UpdateDecision.isSnapshotVersionCode(UpdateDecision.SNAPSHOT_VERSION_CODE_BASE))
        assertTrue(UpdateDecision.isSnapshotVersionCode(2_000_000_005L))
        assertFalse(UpdateDecision.isSnapshotVersionCode(UpdateDecision.SNAPSHOT_VERSION_CODE_BASE - 1))
        assertFalse(UpdateDecision.isSnapshotVersionCode(10203L))
    }

    @Test
    fun `newer archive versionCode proceeds`() {
        assertEquals(UpdateDecision.Install.PROCEED, UpdateDecision.afterDownload(10000, 10001))
    }

    @Test
    fun `equal versionCode does not update`() {
        assertEquals(UpdateDecision.Install.NO_UPDATE, UpdateDecision.afterDownload(10000, 10000))
    }

    @Test
    fun `older versionCode over an installed stable is a refused downgrade`() {
        assertEquals(UpdateDecision.Install.NO_UPDATE, UpdateDecision.afterDownload(20000, 10203))
    }

    @Test
    fun `older stable archive over an installed snapshot proceeds as the channel switch`() {
        // Installed snapshot 2_000_000_005; stable archive 10203: PROCEED only when the
        // candidate is a stable release — the caller then uninstalls and reinstalls.
        assertEquals(
            UpdateDecision.Install.PROCEED,
            UpdateDecision.afterDownload(2_000_000_005L, 10203L, stableRelease = true),
        )
        // Without the stable-release flag the same numbers are a refused downgrade.
        assertEquals(UpdateDecision.Install.NO_UPDATE, UpdateDecision.afterDownload(2_000_000_005L, 10203L))
    }

    @Test
    fun `initial install proceeds even with low versionCode`() {
        assertEquals(UpdateDecision.Install.PROCEED, UpdateDecision.afterDownload(null, 1L))
    }

    @Test
    fun `downgrade install is detected only for a lower code over an installed snapshot`() {
        assertTrue(UpdateDecision.isDowngradeInstall(2_000_000_005L, 10203L))
        assertTrue(UpdateDecision.isDowngradeInstall(2_000_000_005L, 2_000_000_005L))
        assertFalse(UpdateDecision.isDowngradeInstall(20000L, 10203L)) // stable → stable is never allowed
        assertFalse(UpdateDecision.isDowngradeInstall(null, 10203L)) // not installed
        assertFalse(UpdateDecision.isDowngradeInstall(2_000_000_005L, 2_000_000_006L)) // newer snapshot is an upgrade
    }

    @Test
    fun `tag versionCode derivation matches the release workflow scheme`() {
        assertEquals(10000L, UpdateDecision.versionCodeFromTag("v1.0.0"))
        assertEquals(10203L, UpdateDecision.versionCodeFromTag("v1.2.3"))
        assertEquals(10203L, UpdateDecision.versionCodeFromTag("1.2.3"))
        assertEquals(null, UpdateDecision.versionCodeFromTag("snapshot"))
    }

    @Test
    fun `fingerprint combines release asset and tag`() {
        val release = com.patbaumgartner.android.myapps.domain.model.ReleaseInfo(
            id = 42, tag = "v1.0.0", name = "v1.0.0", notes = "", prerelease = false,
            publishedAt = "", assets = emptyList(),
        )
        assertEquals("42:7:v1.0.0", UpdateDecision.candidateFingerprint(release, 7))
    }
}
