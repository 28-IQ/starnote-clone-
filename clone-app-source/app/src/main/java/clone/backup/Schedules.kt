package clone.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import clone.billing.EntitlementWorker
import clone.data.SettingsRepo
import java.util.concurrent.TimeUnit

/** Turns the saved settings into WorkManager jobs. Call at app start and whenever a setting changes. */
object Schedules {
    private const val BACKUP = "backup-periodic"
    private const val ENTITLEMENT = "entitlement-check"

    fun apply(context: Context, settings: SettingsRepo) {
        val wm = WorkManager.getInstance(context)
        val days = settings.getLong(SettingsRepo.Keys.BACKUP_INTERVAL_DAYS, 0)
        val enabled = settings.getString(SettingsRepo.Keys.BACKUP_PROVIDER) == "gdrive" && days > 0
        if (enabled) {
            val network = if (settings.getBool(SettingsRepo.Keys.BACKUP_WIFI_ONLY, true)) NetworkType.UNMETERED else NetworkType.CONNECTED
            val req = PeriodicWorkRequestBuilder<BackupWorker>(days, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(network).setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(BACKUP, ExistingPeriodicWorkPolicy.UPDATE, req)
        } else {
            wm.cancelUniqueWork(BACKUP)
        }
        val check = PeriodicWorkRequestBuilder<EntitlementWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(ENTITLEMENT, ExistingPeriodicWorkPolicy.KEEP, check)
    }
}
