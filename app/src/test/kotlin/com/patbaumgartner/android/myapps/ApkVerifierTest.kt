package com.patbaumgartner.android.myapps

import com.patbaumgartner.android.myapps.domain.trust.ApkMode
import com.patbaumgartner.android.myapps.domain.trust.ApprovedApp
import com.patbaumgartner.android.myapps.domain.verify.ApkMetadata
import com.patbaumgartner.android.myapps.domain.verify.ApkVerifier
import com.patbaumgartner.android.myapps.domain.verify.Checksums
import com.patbaumgartner.android.myapps.domain.verify.VerifyException
import com.patbaumgartner.android.myapps.domain.verify.VerifyFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkVerifierTest {

    private val trusted = "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"
    private val historical = "11:22:33:44:55:66:77:88:99:00:AA:BB:CC:DD:EE:FF:11:22:33:44:55:66:77:88:99:00:AA:BB:CC:DD:EE:FF"

    private val app = ApprovedApp(
        repo = "dice-roller-android",
        packageId = "com.patbaumgartner.android.diceroller",
        displayName = "Dice Roller",
        description = "",
        signerCertSha256 = listOf(trusted, historical),
        apkMode = ApkMode.UNIVERSAL,
    )

    private val sha = "a".repeat(64)
    private val goodMeta = ApkMetadata(
        packageId = "com.patbaumgartner.android.diceroller",
        versionCode = 10000,
        minSdk = 26,
        signerCertSha256 = trusted,
    )

    private fun failureOf(result: Result<ApkMetadata>): VerifyFailure =
        (result.exceptionOrNull() as VerifyException).failure

    @Test
    fun `valid apk passes all checks`() {
        val result = ApkVerifier.verify(app, goodMeta, sha, sha, deviceSdkInt = 35, deviceAbis = listOf("arm64-v8a"))
        assertTrue(result.isSuccess)
    }

    @Test
    fun `wrong package id is rejected`() {
        val result = ApkVerifier.verify(app, goodMeta.copy(packageId = "com.evil.app"), sha, sha, 35, listOf("arm64-v8a"))
        assertEquals(VerifyFailure.PACKAGE_MISMATCH, failureOf(result))
    }

    @Test
    fun `unsigned apk is rejected`() {
        val result = ApkVerifier.verify(app, goodMeta.copy(signerCertSha256 = null), sha, sha, 35, listOf("arm64-v8a"))
        assertEquals(VerifyFailure.UNSIGNED, failureOf(result))
    }

    @Test
    fun `untrusted signer is rejected`() {
        val meta = goodMeta.copy(signerCertSha256 = "FF".repeat(32).chunked(2).joinToString(":"))
        val result = ApkVerifier.verify(app, meta, sha, sha, 35, listOf("arm64-v8a"))
        assertEquals(VerifyFailure.UNTRUSTED_SIGNER, failureOf(result))
    }

    @Test
    fun `historical signer in the lineage is accepted`() {
        val meta = goodMeta.copy(signerCertSha256 = historical)
        val result = ApkVerifier.verify(app, meta, sha, sha, 35, listOf("arm64-v8a"))
        assertTrue("signing rotation must not force an uninstall", result.isSuccess)
    }

    @Test
    fun `fingerprint comparison is case and colon insensitive`() {
        val meta = goodMeta.copy(signerCertSha256 = trusted.lowercase())
        assertTrue(ApkVerifier.verify(app, meta, sha, sha, 35, listOf("arm64-v8a")).isSuccess)
    }

    @Test
    fun `minSdk above device is rejected`() {
        val meta = goodMeta.copy(minSdk = 36)
        val result = ApkVerifier.verify(app, meta, sha, sha, 35, listOf("arm64-v8a"))
        assertEquals(VerifyFailure.MIN_SDK_NOT_MET, failureOf(result))
    }

    @Test
    fun `checksum mismatch is rejected`() {
        val result = ApkVerifier.verify(app, goodMeta, "b".repeat(64), sha, 35, listOf("arm64-v8a"))
        assertEquals(VerifyFailure.CHECKSUM_MISMATCH, failureOf(result))
    }

    @Test
    fun `missing checksum entry is rejected`() {
        val result = ApkVerifier.verify(app, goodMeta, sha, null, 35, listOf("arm64-v8a"))
        assertEquals(VerifyFailure.MALFORMED_CHECKSUM_ENTRY, failureOf(result))
    }

    @Test
    fun `sha256 of known input matches reference vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Checksums.sha256Hex("abc".toByteArray()),
        )
    }

    @Test
    fun `fingerprint normalization strips colons and uppercases`() {
        assertEquals("AABB", Checksums.normalizeFingerprint("aa:bb"))
    }
}
