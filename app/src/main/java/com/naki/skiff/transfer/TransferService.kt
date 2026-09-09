package com.naki.skiff.transfer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.naki.skiff.R
import com.naki.skiff.SkiffApplication
import com.naki.skiff.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps transfers alive when the app is not in front. Android will kill a plain background
 * coroutine within seconds of the last Activity going away, which for a multi-gigabyte
 * upload means it never finishes; a foreground service with a progress notification is the
 * supported way to say "this is work the user asked for and can see".
 */
class TransferService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val queue = (application as SkiffApplication).container.transferQueue

        startForeground(idleNotification())

        scope.launch {
            queue.jobs.collect { jobs ->
                val active = jobs.firstOrNull { !it.finished }
                if (active == null) {
                    // Nothing left to do; drop the notification and let the process idle.
                    ServiceCompat.stopForeground(this@TransferService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    notificationManager().notify(NOTIFICATION_ID, progressNotification(active, jobs.size))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startForeground(notification: android.app.Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun progressNotification(job: TransferJob, queued: Int): android.app.Notification {
        val title = getString(
            if (job.move) R.string.transfer_moving else R.string.transfer_copying,
            job.label,
        )
        val text = if (job.totalFiles > 1) {
            getString(R.string.transfer_progress_files, job.completedFiles + 1, job.totalFiles)
        } else {
            job.currentFileName
        }
        return baseNotification()
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(if (queued > 1) getString(R.string.transfer_queued, queued - 1) else null)
            // A job whose plan is still being walked has no total yet: show indeterminate.
            .setProgress(100, (job.fraction * 100).toInt(), job.totalBytes <= 0)
            .setOngoing(true)
            .build()
    }

    private fun idleNotification(): android.app.Notification =
        baseNotification().setContentTitle(getString(R.string.transfer_preparing)).build()

    private fun baseNotification(): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.transfer_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    companion object {
        private const val CHANNEL_ID = "transfers"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, TransferService::class.java))
        }
    }
}
