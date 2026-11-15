package com.patbaumgartner.android.myapps.domain.model

import com.patbaumgartner.android.myapps.domain.trust.ApkMode
import com.patbaumgartner.android.myapps.domain.trust.ApprovedApp

/**
 * Selects the APK asset of a release for this device, per the app's APK-selection rules.
 *
 * Rules:
 * - Only `.apk` assets are candidates; `.aab` and checksum files are never installed.
 * - UNIVERSAL apps publish one architecture-independent APK; ABI-suffixed assets are
 *   ignored, and ambiguity (more than one candidate) is rejected.
 * - PER_ABI apps (e.g. String Telephone) publish one APK per ABI; the best matching
 *   device ABI wins. A release whose split set does not cover every supported ABI is
 *   rejected as incomplete.
 * - Every release must carry a SHA256SUMS asset; the selected APK must have an entry in
 *   it once the file is downloaded, otherwise integrity cannot be checked and the APK is
 *   rejected.
 */
object ApkSelector {

    /** ABIs the ecosystem publishes split APKs for, most preferred first. */
    val SUPPORTED_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    val CHECKSUM_NAMES = listOf("SHA256SUMS", "SHA256SUMS.txt")

    data class Selection(
        val apk: AssetInfo,
        val checksumsAsset: AssetInfo,
        val splitSetComplete: Boolean,
    )

    sealed class Failure(override val message: String) : Throwable(message)

    class NoApk(message: String) : Failure(message)
    class NoChecksums(message: String) : Failure(message)

    fun select(assets: List<AssetInfo>, app: ApprovedApp, deviceAbis: List<String>): Result<Selection> {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val selected = when (app.apkMode) {
            ApkMode.UNIVERSAL -> selectUniversal(apks)
            ApkMode.PER_ABI -> selectPerAbi(apks, deviceAbis)
        } ?: return Result.failure(NoApk(noApkMessage(app, apks, deviceAbis)))

        val checksumsAsset = assets.firstOrNull { it.name in CHECKSUM_NAMES }
            ?: return Result.failure(NoChecksums("release of ${app.repo} has no SHA256SUMS asset"))
        return Result.success(Selection(selected, checksumsAsset, splitSetComplete = true))
    }

    /** Resolves the expected lowercase hex SHA-256 for [apkName] from SHA256SUMS text. */
    fun expectedSha256(checksumText: String, apkName: String): String? = parseChecksumLines(checksumText)[apkName]

    private fun selectUniversal(apks: List<AssetInfo>): AssetInfo? {
        val candidates = apks.filter { apk -> SUPPORTED_ABIS.none { abi -> apk.name.contains("-$abi.") } }
        if (candidates.size != 1) return null // none, or ambiguous asset layout
        return candidates.single()
    }

    private fun selectPerAbi(apks: List<AssetInfo>, deviceAbis: List<String>): AssetInfo? {
        val byAbi = apks.mapNotNull { apk ->
            SUPPORTED_ABIS.firstOrNull { apk.name.contains("-$it.") }?.let { it to apk }
        }.groupBy({ it.first }, { it.second })
        val presentAbis = byAbi.keys
        if (presentAbis.isEmpty()) return null
        // Reject incomplete split sets: a PER_ABI release must cover every supported ABI.
        if (presentAbis.size < SUPPORTED_ABIS.size) return null
        val deviceAbi = deviceAbis.firstOrNull { it in presentAbis } ?: return null
        return byAbi[deviceAbi]?.singleOrNull()
    }

    private fun noApkMessage(app: ApprovedApp, apks: List<AssetInfo>, deviceAbis: List<String>): String = when (app.apkMode) {
        ApkMode.UNIVERSAL -> if (apks.isEmpty()) {
            "release of ${app.repo} has no APK asset"
        } else {
            "release of ${app.repo} has no single universal APK (found: ${apks.joinToString { it.name }})"
        }

        ApkMode.PER_ABI -> {
            val present = apks.mapNotNull { apk -> SUPPORTED_ABIS.firstOrNull { apk.name.contains("-$it.") } }.distinct()
            when {
                present.isEmpty() -> "release of ${app.repo} has no per-ABI APK assets"
                present.size < SUPPORTED_ABIS.size ->
                    "release of ${app.repo} has an incomplete split set (missing: " +
                        (SUPPORTED_ABIS - present.toSet()).joinToString() + ")"

                else -> "no APK for this device's ABIs (${deviceAbis.joinToString()}) in ${app.repo}"
            }
        }
    }

    /**
     * Parses `sha256sum` output: `<64 hex chars><space><space|*><file name>` per line.
     * Returns file name → lowercase hex digest.
     */
    fun parseChecksumLines(text: String): Map<String, String> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val digest = line.substringBefore(' ').trim().lowercase()
            val name = line.substringAfterLast(' ').trim().removePrefix("*")
            if (digest.matches(Regex("[0-9a-f]{64}")) && name.isNotEmpty()) name to digest else null
        }
        .toMap()
}
