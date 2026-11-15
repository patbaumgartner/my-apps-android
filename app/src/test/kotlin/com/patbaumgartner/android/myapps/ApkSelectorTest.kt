package com.patbaumgartner.android.myapps

import com.patbaumgartner.android.myapps.domain.model.ApkSelector
import com.patbaumgartner.android.myapps.domain.model.AssetInfo
import com.patbaumgartner.android.myapps.domain.trust.ApkMode
import com.patbaumgartner.android.myapps.domain.trust.ApprovedApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkSelectorTest {

    private val universalApp = ApprovedApp(
        repo = "dice-roller-android",
        packageId = "com.patbaumgartner.android.diceroller",
        displayName = "Dice Roller",
        description = "",
        signerCertSha256 = listOf("D9:57:90:7F:65:14:F2:43:9A:13:2F:F9:23:A4:46:C3:EC:FB:73:F1:20:96:07:DB:4E:63:1A:45:45:D2:F2:46"),
        apkMode = ApkMode.UNIVERSAL,
    )
    private val perAbiApp = universalApp.copy(
        repo = "string-telephone-android",
        packageId = "com.patbaumgartner.android.stringtelephone",
        apkMode = ApkMode.PER_ABI,
    )

    private fun asset(name: String, id: Long = name.hashCode().toLong()) =
        AssetInfo(id = id, name = name, size = 1000, downloadUrl = "https://example.com/$name")

    private val checksums = asset("SHA256SUMS", id = 1)

    @Test
    fun `universal release selects the plain apk and ignores aab`() {
        val assets = listOf(
            asset("dice-roller-1.0.0.apk"),
            asset("dice-roller-1.0.0.aab"),
            checksums,
        )
        val selection = ApkSelector.select(assets, universalApp, listOf("arm64-v8a")).getOrThrow()
        assertEquals("dice-roller-1.0.0.apk", selection.apk.name)
        assertEquals("SHA256SUMS", selection.checksumsAsset.name)
    }

    @Test
    fun `universal release with abi-suffixed apks only is rejected`() {
        val assets = listOf(asset("app-1.0.0-arm64-v8a.apk"), checksums)
        assertTrue(ApkSelector.select(assets, universalApp, listOf("arm64-v8a")).isFailure)
    }

    @Test
    fun `per-ABI release selects the device primary abi`() {
        val assets = ApkSelector.SUPPORTED_ABIS.map { asset("string-telephone-0.1.0-$it.apk") } + checksums
        val selection = ApkSelector.select(assets, perAbiApp, listOf("arm64-v8a", "x86")).getOrThrow()
        assertEquals("string-telephone-0.1.0-arm64-v8a.apk", selection.apk.name)
    }

    @Test
    fun `per-ABI falls back to a secondary device abi`() {
        val assets = ApkSelector.SUPPORTED_ABIS.map { asset("app-0.1.0-$it.apk") } + checksums
        val selection = ApkSelector.select(assets, perAbiApp, listOf("armeabi-v7a")).getOrThrow()
        assertEquals("app-0.1.0-armeabi-v7a.apk", selection.apk.name)
    }

    @Test
    fun `incomplete split set is rejected`() {
        val assets = listOf(
            asset("app-0.1.0-arm64-v8a.apk"),
            asset("app-0.1.0-x86_64.apk"),
            checksums,
        )
        val result = ApkSelector.select(assets, perAbiApp, listOf("arm64-v8a"))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("incomplete split set"))
    }

    @Test
    fun `per-ABI release without the device abi is rejected`() {
        val assets = ApkSelector.SUPPORTED_ABIS.map { asset("app-0.1.0-$it.apk") } + checksums
        assertTrue(ApkSelector.select(assets, perAbiApp, listOf("riscv64")).isFailure)
    }

    @Test
    fun `release without checksums asset is rejected`() {
        val assets = listOf(asset("dice-roller-1.0.0.apk"))
        assertTrue(ApkSelector.select(assets, universalApp, listOf("arm64-v8a")).isFailure)
    }

    @Test
    fun `checksum parser maps file names to digests`() {
        val digest1 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val digest2 = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"
        val text = """
            $digest1  dice-roller-1.0.0.apk
            $digest2 *spaced-repeater-snapshot.apk
        """.trimIndent()
        val map = ApkSelector.parseChecksumLines(text)
        assertEquals(digest1, map["dice-roller-1.0.0.apk"])
        assertEquals(digest2, map["spaced-repeater-snapshot.apk"])
    }

    @Test
    fun `expectedSha256 resolves the selected apk entry`() {
        val digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val text = "$digest  app.apk\n"
        assertEquals(digest, ApkSelector.expectedSha256(text, "app.apk"))
        assertEquals(null, ApkSelector.expectedSha256(text, "other.apk"))
    }
}
