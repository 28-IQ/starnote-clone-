package clone.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import clone.app.CloneApp
import clone.data.SettingsRepo

/** Scheduled backup. Retries a few times with backoff, then gives up and leaves the error in the backups table. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as CloneApp
        if (app.settings.getString(SettingsRepo.Keys.BACKUP_PROVIDER) != "gdrive") return Result.success()
        try { setForeground(foregroundInfo()) } catch (_: Exception) { /* not allowed to start a foreground service now; run without */ }
        return when (val outcome = app.backupService.backUpToDrive()) {
            is BackupOutcome.Done -> Result.success()
            is BackupOutcome.NeedsConsent -> Result.failure()          // the Backup screen shows "connect again"
            is BackupOutcome.Failed -> if (outcome.retryable && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo()

    private fun foregroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Backups", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Backing up your notes")
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION_ID, n)
    }

    private companion object {
        const val CHANNEL = "backup"
        const val NOTIFICATION_ID = 4101
        const val MAX_ATTEMPTS = 4
    }
}
