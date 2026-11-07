package clone.billing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import clone.app.CloneApp

/** Daily: re-reads purchases from Play. Catches refunds and retries an acknowledgement that failed (the three-day rule). */
class EntitlementWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CloneApp
        return try {
            app.billing.refresh()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
