package com.patbaumgartner.android.myapps.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.patbaumgartner.android.myapps.data.UpdateChecker
import com.patbaumgartner.android.myapps.data.device.InstalledAppRepository
import com.patbaumgartner.android.myapps.data.github.GitHubClient
import com.patbaumgartner.android.myapps.data.notify.Notifier
import com.patbaumgartner.android.myapps.data.settings.SettingsRepository
import com.patbaumgartner.android.myapps.domain.trust.TrustRegistry
import java.util.concurrent.TimeUnit

/**
 * Battery-conscious background update check. Runs periodically only when enabled in
 * Settings; constraints keep it off metered networks unless the user opts in. It only
 * checks and notifies — it never installs anything.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
    private val checker: UpdateChecker,
    private val installedApps: InstalledAppRepository,
    private val settings: SettingsRepository,
    private val notifier: Notifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!settings.backgroundChecksEnabled) return Result.success()
        var updates = 0
        for (app in TrustRegistry.apps) {
            val installed = installedApps.get(app.packageId)
            when (val outcome = checker.check(app, installed)) {
                is com.patbaumgartner.android.myapps.data.CheckOutcome.UpdateAvailable -> updates++
                is com.patbaumgartner.android.myapps.data.CheckOutcome.NotInstalled -> Unit
                else -> Unit
            }
        }
        if (updates > 0 && settings.notificationsEnabled) {
            notifier.notifyUpdatesAvailable(updates)
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "myapps.updateCheck"

        fun schedule(context: Context, settings: SettingsRepository) {
            if (!settings.backgroundChecksEnabled) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                return
            }
            val networkType = if (settings.backgroundWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
