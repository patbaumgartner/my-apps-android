package com.patbaumgartner.android.myapps.data.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

/**
 * Receives the PackageInstaller result for a committed session. Registered in the
 * manifest; results are forwarded to [listener] (the ViewModel) and the pending-install
 * marker is reconciled on next app start if the process was killed.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_RESULT) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, -1)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // The system does not show the consent UI by itself for a committed session:
            // it hands it to us via EXTRA_INTENT and we must launch it. Without this the
            // install waits forever and no final status is ever delivered.
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.let { confirm ->
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
            }
        }
        listener?.onInstallResult(requestCode, status, message)
    }

    fun interface Listener {
        fun onInstallResult(requestCode: Int, status: Int, message: String?)
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "com.patbaumgartner.android.myapps.INSTALL_RESULT"
        const val EXTRA_REQUEST_CODE = "requestCode"

        @Volatile
        var listener: Listener? = null
    }
}
