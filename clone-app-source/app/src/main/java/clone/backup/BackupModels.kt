package clone.backup

import java.io.File

class BackupException(message: String, val retryable: Boolean = false, cause: Throwable? = null) : Exception(message, cause)

data class RemoteBackup(val id: String, val name: String, val sizeBytes: Long, val createdAt: String, val sha256: String?)

/** Storage a backup archive can be sent to. Only the user's own account, official APIs. */
interface BackupProvider {
    val id: String
    /** An archive with this hash is already stored: uploading again would only create a copy. */
    suspend fun find(sha256: String): RemoteBackup?
    suspend fun upload(file: File, name: String, sha256: String, onProgress: (sent: Long, total: Long) -> Unit): RemoteBackup
    suspend fun list(): List<RemoteBackup>
    suspend fun download(remote: RemoteBackup, dest: File, onProgress: (received: Long, total: Long) -> Unit)
    suspend fun delete(remote: RemoteBackup)
    suspend fun deleteAll(): Int
}

sealed interface BackupOutcome {
    data class Done(val remote: RemoteBackup, val alreadyStored: Boolean) : BackupOutcome
    /** The user has to approve access in a Google screen first. */
    data class NeedsConsent(val pendingIntent: android.app.PendingIntent) : BackupOutcome
    data class Failed(val message: String, val retryable: Boolean) : BackupOutcome
}
