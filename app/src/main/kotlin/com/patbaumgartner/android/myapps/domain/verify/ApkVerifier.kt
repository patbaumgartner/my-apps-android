package com.patbaumgartner.android.myapps.domain.verify

import com.patbaumgartner.android.myapps.domain.model.ApkSelector
import com.patbaumgartner.android.myapps.domain.trust.ApprovedApp

/**
 * Metadata read from a downloaded APK archive (via PackageManager on device). Kept
 * Android-free so the verification decision is pure and JVM-testable.
 */
data class ApkMetadata(
    val packageId: String,
    val versionCode: Long,
    val minSdk: Int,
    /** Colon-separated uppercase SHA-256 of the APK signer certificate, null if unsigned. */
    val signerCertSha256: String?,
)

enum class VerifyFailure {
    PACKAGE_MISMATCH,
    UNSIGNED,
    UNTRUSTED_SIGNER,
    MIN_SDK_NOT_MET,
    CHECKSUM_MISMATCH,
    MALFORMED_CHECKSUM_ENTRY,
}

/**
 * Verifies a downloaded APK against the trust registry before installation. Every check
 * must pass; the result is fail-closed.
 */
object ApkVerifier {

    /**
     * @param actualSha256 lowercase hex SHA-256 of the downloaded file.
     * @param expectedSha256 lowercase hex digest from the release's SHA256SUMS entry.
     * @param deviceSdkInt Android SDK level of the device.
     * @param deviceAbis ABIs of the device, most preferred first.
     */
    fun verify(
        app: ApprovedApp,
        meta: ApkMetadata,
        actualSha256: String,
        expectedSha256: String?,
        deviceSdkInt: Int,
        deviceAbis: List<String>,
    ): Result<ApkMetadata> {
        if (meta.packageId != app.packageId) {
            return Result.failure(VerifyException(VerifyFailure.PACKAGE_MISMATCH, app, meta))
        }
        if (meta.signerCertSha256 == null) {
            return Result.failure(VerifyException(VerifyFailure.UNSIGNED, app, meta))
        }
        val normalized = Checksums.normalizeFingerprint(meta.signerCertSha256)
        val trusted = app.signerCertSha256.any { Checksums.normalizeFingerprint(it) == normalized }
        if (!trusted) {
            return Result.failure(VerifyException(VerifyFailure.UNTRUSTED_SIGNER, app, meta))
        }
        if (meta.minSdk > deviceSdkInt) {
            return Result.failure(VerifyException(VerifyFailure.MIN_SDK_NOT_MET, app, meta))
        }
        if (expectedSha256 == null || !Checksums.isValidSha256Hex(expectedSha256)) {
            return Result.failure(VerifyException(VerifyFailure.MALFORMED_CHECKSUM_ENTRY, app, meta))
        }
        if (!Checksums.isValidSha256Hex(actualSha256) ||
            !actualSha256.equals(expectedSha256, ignoreCase = true)
        ) {
            return Result.failure(VerifyException(VerifyFailure.CHECKSUM_MISMATCH, app, meta))
        }
        // For per-ABI releases the selected asset must match one of the device's ABIs.
        if (app.apkMode == com.patbaumgartner.android.myapps.domain.trust.ApkMode.PER_ABI) {
            // The asset name is checked during selection; here we only require the device
            // to actually support one of the published ABIs.
            if (deviceAbis.none { it in ApkSelector.SUPPORTED_ABIS }) {
                return Result.failure(VerifyException(VerifyFailure.PACKAGE_MISMATCH, app, meta))
            }
        }
        return Result.success(meta)
    }
}

class VerifyException(val failure: VerifyFailure, val app: ApprovedApp, val meta: ApkMetadata) :
    Exception("verification failed (${failure}) for ${app.repo}: ${describe(failure)}")

private fun describe(failure: VerifyFailure): String = when (failure) {
    VerifyFailure.PACKAGE_MISMATCH -> "package id does not match the approved entry"
    VerifyFailure.UNSIGNED -> "APK is not signed"
    VerifyFailure.UNTRUSTED_SIGNER -> "signer certificate is not in the trusted lineage"
    VerifyFailure.MIN_SDK_NOT_MET -> "APK requires a newer Android version"
    VerifyFailure.CHECKSUM_MISMATCH -> "SHA-256 does not match SHA256SUMS"
    VerifyFailure.MALFORMED_CHECKSUM_ENTRY -> "SHA256SUMS entry missing or malformed"
}
