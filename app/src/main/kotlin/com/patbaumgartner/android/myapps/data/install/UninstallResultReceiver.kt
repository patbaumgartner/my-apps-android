package com.patbaumgartner.android.myapps.data.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

/**
 * Receives the PackageInstaller result of an [InstallRepository.uninstall] request
 * (the Snapshot→Stable channel switch). Registered in the manifest; results are
 * forwarded to [listener] (the ViewModel), which then installs the staged APK.
 */
class UninstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_UNINSTALL_RESULT) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, -1)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // The system does not show the confirmation UI by itself: it hands it to us
            // via EXTRA_INTENT and we must launch it (same as for install sessions).
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.let { confirm ->
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
            }
        }
        listener?.onUninstallResult(requestCode, status, message)
    }

    fun interface Listener {
        fun onUninstallResult(requestCode: Int, status: Int, message: String?)
    }

    companion object {
        const val ACTION_UNINSTALL_RESULT = "com.patbaumgartner.android.myapps.UNINSTALL_RESULT"
        const val EXTRA_REQUEST_CODE = "uninstallRequestCode"

        @Volatile
        var listener: Listener? = null
    }
}
