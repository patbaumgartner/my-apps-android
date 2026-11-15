package com.patbaumgartner.android.myapps

import com.patbaumgartner.android.myapps.domain.github.ReleaseParser
import com.patbaumgartner.android.myapps.domain.model.Channel
import com.patbaumgartner.android.myapps.domain.model.GhAsset
import com.patbaumgartner.android.myapps.domain.model.GhRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseParserTest {

    private fun release(tag: String, prerelease: Boolean, draft: Boolean = false, assets: List<GhAsset> = emptyList()) =
        GhRelease(id = 1L, tagName = tag, name = null, body = "notes", prerelease = prerelease, draft = draft,
            publishedAt = "2026-11-15T10:00:00Z", assets = assets)

    @Test
    fun `stable channel accepts a non-prerelease latest release`() {
        val result = ReleaseParser.parse(release("v1.2.3", prerelease = false), Channel.STABLE)
        assertTrue(result.isSuccess)
        assertEquals("v1.2.3", result.getOrThrow().tag)
    }

    @Test
    fun `stable channel rejects a prerelease returned as latest`() {
        val result = ReleaseParser.parse(release("snapshot", prerelease = true), Channel.STABLE)
        assertTrue(result.isFailure)
    }

    @Test
    fun `snapshot channel requires the release to be a prerelease`() {
        assertTrue(ReleaseParser.parse(release("snapshot", prerelease = true), Channel.SNAPSHOT).isSuccess)
        val notPre = ReleaseParser.parse(release("snapshot", prerelease = false), Channel.SNAPSHOT)
        assertTrue("a non-prerelease snapshot tag must be rejected", notPre.isFailure)
    }

    @Test
    fun `drafts are always rejected`() {
        assertTrue(ReleaseParser.parse(release("v9.9.9", prerelease = false, draft = true), Channel.STABLE).isFailure)
    }

    @Test
    fun `assets are mapped to domain model`() {
        val gh = release("v1.0.0", prerelease = false, assets = listOf(
            GhAsset(id = 7, name = "app-1.0.0.apk", size = 123, browserDownloadUrl = "https://example.com/app.apk"),
        ))
        val info = ReleaseParser.parse(gh, Channel.STABLE).getOrThrow()
        assertEquals(1, info.assets.size)
        assertEquals("app-1.0.0.apk", info.assets[0].name)
        assertEquals("https://example.com/app.apk", info.assets[0].downloadUrl)
    }
}
