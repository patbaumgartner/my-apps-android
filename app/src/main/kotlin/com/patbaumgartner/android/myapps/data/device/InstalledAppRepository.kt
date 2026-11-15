package com.patbaumgartner.android.myapps.data.device

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.patbaumgartner.android.myapps.domain.verify.ApkMetadata
import java.security.MessageDigest

/** Information about an installed package, used to compare versionCodes and signers. */
data class InstalledApp(
    val packageId: String,
    val versionName: String,
    val versionCode: Long,
    val signerCertSha256: String,
)

class InstalledAppRepository(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    fun get(packageId: String): InstalledApp? = runCatching {
        val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageId, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            @Suppress("DEPRECATION") pm.getPackageInfo(packageId, PackageManager.GET_SIGNING_CERTIFICATES)
        }
        InstalledApp(
            packageId = info.packageName,
            versionName = info.versionName.orEmpty(),
            versionCode = packageInfoVersionCode(info),
            signerCertSha256 = signerFingerprint(info),
        )
    }.getOrNull()

    fun isInstalled(packageId: String): Boolean = runCatching {
        pm.getPackageInfo(packageId, 0)
        true
    }.getOrDefault(false)

    fun iconFor(packageId: String): Drawable? = runCatching { pm.getApplicationIcon(packageId) }.getOrNull()

    fun labelFor(packageId: String): String = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(packageId, 0)).toString()
    }.getOrDefault(packageId)

    companion object {
        fun packageInfoVersionCode(info: PackageInfo): Long =
            if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

        /** SHA-256 fingerprint of the first signer certificate, colon-separated uppercase hex. */
        fun signerFingerprint(info: PackageInfo): String {
            val certs: Array<ByteArray> = if (android.os.Build.VERSION.SDK_INT >= 28) {
                info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }?.toTypedArray() ?: emptyArray()
            } else {
                @Suppress("DEPRECATION") info.signatures?.map { it.toByteArray() }?.toTypedArray() ?: emptyArray()
            }
            val first = certs.firstOrNull() ?: return ""
            return MessageDigest.getInstance("SHA-256").digest(first).toColonHex()
        }

        private fun ByteArray.toColonHex(): String = joinToString(":") { "%02X".format(it) }
    }
}

/**
 * Reads [ApkMetadata] from a downloaded APK file without installing it, using
 * PackageManager's archive inspection.
 */
class ApkArchiveReader(private val context: Context) {

    fun read(apkFile: java.io.File): ApkMetadata? = runCatching {
        val pm = context.packageManager
        val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
            val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } ?: return@runCatching null
        val appInfo = info.applicationInfo ?: return@runCatching null
        appInfo.sourceDir = apkFile.absolutePath
        appInfo.publicSourceDir = apkFile.absolutePath
        ApkMetadata(
            packageId = info.packageName.orEmpty(),
            versionCode = InstalledAppRepository.packageInfoVersionCode(info),
            minSdk = appInfo.minSdkVersion,
            signerCertSha256 = InstalledAppRepository.signerFingerprint(info).ifEmpty { null },
        )
    }.getOrNull()
}
