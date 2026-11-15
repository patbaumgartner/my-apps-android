package com.patbaumgartner.android.myapps

import com.patbaumgartner.android.myapps.domain.trust.ApkMode
import com.patbaumgartner.android.myapps.domain.trust.TrustRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustRegistryTest {

    @Test
    fun `registry contains the self entry for my-apps-android`() {
        val self = TrustRegistry.findByRepo(TrustRegistry.SELF_REPO)
        assertNotNull("self-update requires a my-apps-android entry", self)
        assertEquals("com.patbaumgartner.android.myapps", self!!.packageId)
    }

    @Test
    fun `registry contains the four sibling apps with expected package ids`() {
        val byPackage = TrustRegistry.apps.associateBy { it.packageId }
        assertEquals("com.patbaumgartner.android.diceroller", byPackage.keys.firstOrNull { it.endsWith("diceroller") })
        assertTrue(byPackage.containsKey("com.patbaumgartner.android.spacedrepeater"))
        assertTrue(byPackage.containsKey("com.patbaumgartner.android.roomwatchdog"))
        assertTrue(byPackage.containsKey("com.patbaumgartner.android.stringtelephone"))
    }

    @Test
    fun `repos and package ids are unique`() {
        assertEquals(TrustRegistry.apps.size, TrustRegistry.apps.map { it.repo }.distinct().size)
        assertEquals(TrustRegistry.apps.size, TrustRegistry.apps.map { it.packageId }.distinct().size)
    }

    @Test
    fun `every entry has at least one well-formed sha256 fingerprint`() {
        val fingerprint = Regex("[0-9A-F]{2}(:[0-9A-F]{2}){31}")
        TrustRegistry.apps.forEach { app ->
            assertTrue("no fingerprint for ${app.repo}", app.signerCertSha256.isNotEmpty())
            app.signerCertSha256.forEach {
                assertTrue("malformed fingerprint for ${app.repo}: $it", fingerprint.matches(it))
            }
        }
    }

    @Test
    fun `string telephone uses per-ABI selection, the others universal`() {
        assertEquals(ApkMode.PER_ABI, TrustRegistry.findByRepo("string-telephone-android")!!.apkMode)
        TrustRegistry.apps.filter { it.repo != "string-telephone-android" }.forEach {
            assertEquals(ApkMode.UNIVERSAL, it.apkMode)
        }
    }

    @Test
    fun `lookup by package id finds the owning app`() {
        assertEquals("dice-roller-android", TrustRegistry.findByPackageId("com.patbaumgartner.android.diceroller")?.repo)
    }
}
