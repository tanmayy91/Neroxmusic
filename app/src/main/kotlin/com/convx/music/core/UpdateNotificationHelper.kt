package com.convx.music.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.convx.music.R

object UpdateNotificationHelper {
    private const val CHANNEL_ID = "updates"
    private const val NOTIFICATION_ID = 1001

    fun cancelUpdateNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun showUpdateNotification(context: Context, versionName: String, apkUrl: String? = null) {
        if (com.convx.music.core.updater.AUTOMATIC_UPDATE_ALERTS_PAUSED ||
            !com.convx.music.core.updater.getUpdateNotificationsSetting(context)
        ) {
            cancelUpdateNotification(context)
            return
        }
        val nm = context.getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.app_updates_title),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(channel)
        }

        // Download links for NexaMusic releases and nightly debug artifacts.
        val downloadUrl = apkUrl?.takeIf(String::isNotBlank) ?: if (versionName.contains("nightly", ignoreCase = true)) {
            "https://nightly.link/tanmayy91/NexaMusic/workflows/nightly.yml/main/nexamusic-gms-beta.zip"
        } else {
            "https://github.com/tanmayy91/NexaMusic/releases/download/$versionName/nexamusic-$versionName.apk"
        }
        val intent = Intent(Intent.ACTION_VIEW, downloadUrl.toUri())

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getActivity(context, NOTIFICATION_ID, intent, flags)

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.nexamusic_notification)
            .setContentTitle(context.getString(R.string.update_available_title))
            .setContentText(versionName)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notif)
        }
    }
}
