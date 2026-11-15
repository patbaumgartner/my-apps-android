package com.patbaumgartner.android.myapps.data.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

/**
 * Installs a verified APK through Android's PackageInstaller session flow. The system
 * shows the consent UI; the result arrives in [InstallResultReceiver]. There are no
 * silent installs: the user always approves.
 */
class InstallRepository(private val context: Context) {

    fun canRequestPackageInstalls(): Boolean =
        if (Build.VERSION.SDK_INT >= 26) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    fun settingsIntent(): Intent = Intent(
        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        android.net.Uri.parse("package:${context.packageName}"),
    )

    /**
     * Copies [apkFile] into a PackageInstaller session and commits it, which makes the
     * system show the install consent UI. The result arrives in [InstallResultReceiver].
     */
    fun install(apkFile: File, label: String, requestCode: Int): Result<Unit> = runCatching {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppLabel(label)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("myapps", 0, apkFile.length()).use { out ->
                apkFile.inputStream().use { it.copyTo(out) }
            }
            val intent = Intent(context, InstallResultReceiver::class.java).apply {
                action = InstallResultReceiver.ACTION_INSTALL_RESULT
                putExtra(InstallResultReceiver.EXTRA_REQUEST_CODE, requestCode)
            }
            val flags = if (Build.VERSION.SDK_INT >= 31) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                @Suppress("DEPRECATION") PendingIntent.FLAG_UPDATE_CURRENT
            }
            session.commit(PendingIntent.getBroadcast(context, requestCode, intent, flags).intentSender)
        }
    }

    companion object {
        fun statusMessage(status: Int, extra: String?): String = when (status) {
            PackageInstaller.STATUS_SUCCESS -> "Installed"
            PackageInstaller.STATUS_PENDING_USER_ACTION -> "Waiting for user approval"
            PackageInstaller.STATUS_FAILURE -> "Install failed${extra?.let { ": $it" } ?: ""}"
            PackageInstaller.STATUS_FAILURE_ABORTED -> "Install cancelled"
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "Install blocked by the system"
            PackageInstaller.STATUS_FAILURE_CONFLICT -> "Install conflict (signature or package mismatch)"
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "APK incompatible with this device"
            PackageInstaller.STATUS_FAILURE_INVALID -> "APK invalid"
            PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage"
            else -> "Install failed (status $status)"
        }
    }
}
