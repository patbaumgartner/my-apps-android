package com.patbaumgartner.android.myapps.data.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.annotation.SuppressLint
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.patbaumgartner.android.myapps.R
import com.patbaumgartner.android.myapps.ui.MainActivity

class Notifier(private val context: Context) {

    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_UPDATES, context.getString(R.string.notification_channel_updates),
                    NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // guarded by notificationsAllowed() above
    fun notifyUpdatesAvailable(count: Int) {
        if (!notificationsAllowed()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_updates_title))
            .setContentText(context.getString(R.string.notification_updates_body, count))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_UPDATES, notification)
    }

    @SuppressLint("MissingPermission") // guarded by notificationsAllowed() above
    fun notifyAppUpdated(appName: String) {
        if (!notificationsAllowed()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_app_updated_title))
            .setContentText(context.getString(R.string.notification_app_updated_body, appName))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_APP_UPDATED, notification)
    }

    companion object {
        const val CHANNEL_UPDATES = "updates"
        const val NOTIFICATION_ID_UPDATES = 1001
        const val NOTIFICATION_ID_APP_UPDATED = 1002
    }
}
