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
