package com.patbaumgartner.android.myapps

import com.patbaumgartner.android.myapps.domain.model.UpdateDecision
import org.junit.Assert.assertEquals
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
    fun `newer archive versionCode proceeds`() {
        assertEquals(UpdateDecision.Install.PROCEED, UpdateDecision.afterDownload(10000, 10001))
    }

    @Test
    fun `equal versionCode does not update`() {
        assertEquals(UpdateDecision.Install.NO_UPDATE, UpdateDecision.afterDownload(10000, 10000))
    }

    @Test
    fun `older versionCode is a refused downgrade (snapshot to stable switch waits)`() {
        // Installed snapshot 2_000_000_005; stable offers 10203 -> must not downgrade.
        assertEquals(
            UpdateDecision.Install.NO_UPDATE,
            UpdateDecision.afterDownload(2_000_000_005L, 10203L),
        )
    }

    @Test
    fun `initial install proceeds even with low versionCode`() {
        assertEquals(UpdateDecision.Install.PROCEED, UpdateDecision.afterDownload(null, 1L))
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
