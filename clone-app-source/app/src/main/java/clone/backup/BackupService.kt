package clone.backup

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import clone.data.BackupRepo
import clone.data.NoteDb
import clone.data.SettingsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class ConsentNeeded(val pendingIntent: PendingIntent) : Exception("Google sign-in needed")

/** Runs backups and restores. One at a time. Nothing here logs note titles or file contents. */
class BackupService(
    private val context: Context,
    private val db: NoteDb,
    private val backups: BackupRepo,
    private val settings: SettingsRepo,
    private val auth: GoogleAuth,
) {
    private val lock = Mutex()
    private val work get() = File(context.cacheDir, "backup")

    private fun drive() = GoogleDriveProvider(
        tokens = {
            when (val t = auth.token()) {
                is TokenResult.Token -> t.value
                is TokenResult.NeedsConsent -> throw ConsentNeeded(t.pendingIntent)
                is TokenResult.Failed -> throw BackupException(t.message, retryable = true)
            }
        },
        sessions = object : GoogleDriveProvider.SessionStore {
            override fun get(contentHash: String) = settings.getString(SettingsRepo.Keys.uploadSession(contentHash))
            override fun put(contentHash: String, sessionUrl: String) = settings.putString(SettingsRepo.Keys.uploadSession(contentHash), sessionUrl)
            override fun clear(contentHash: String) = settings.remove(SettingsRepo.Keys.uploadSession(contentHash))
        },
    )

    /** Backs up to the user's Google Drive app folder. Safe to retry: unchanged notes are not uploaded twice. */
    suspend fun backUpToDrive(onProgress: (Float) -> Unit = {}): BackupOutcome = lock.withLock {
        withContext(Dispatchers.IO) {
            backups.failStaleRunning(STALE_AFTER_MS)
            val row = backups.start("gdrive")
            try {
                requireFreeSpace()
                val built = BackupArchive.build(context, db, work)
                try {
                    val provider = drive()
                    val existing = provider.find(built.contentHash)
                    val remote = existing ?: provider.upload(built.file, built.file.name, built.contentHash) { sent, total ->
                        onProgress(sent.toFloat() / total)
                    }
                    backups.finishOk(row, remote.id, built.bytes)
                    settings.putLong(SettingsRepo.Keys.BACKUP_LAST_OK_AT, System.currentTimeMillis())
                    prune(provider)
                    BackupOutcome.Done(remote, alreadyStored = existing != null)
                } finally {
                    built.file.delete()
                }
            } catch (e: ConsentNeeded) {
                backups.finishFailed(row, "Needs Google sign-in")
                BackupOutcome.NeedsConsent(e.pendingIntent)
            } catch (e: BackupException) {
                backups.finishFailed(row, e.message.orEmpty())
                BackupOutcome.Failed(e.message.orEmpty(), e.retryable)
            } catch (e: IOException) {
                backups.finishFailed(row, "Storage or network problem")
                BackupOutcome.Failed("Storage or network problem", retryable = true)
            } catch (e: Exception) {
                backups.finishFailed(row, "Unexpected error")
                BackupOutcome.Failed("Backup failed", retryable = false)
            } finally {
                work.deleteRecursively()
            }
        }
    }

    suspend fun listDriveBackups(): List<RemoteBackup> = withContext(Dispatchers.IO) { drive().list() }

    /** Downloads and checks a backup, then stages it. It is applied at the next app start. */
    suspend fun stageRestoreFromDrive(remote: RemoteBackup, onProgress: (Float) -> Unit = {}) = lock.withLock {
        withContext(Dispatchers.IO) {
            val dest = File(context.cacheDir, "restore/${remote.id}.zip")
            try {
                drive().download(remote, dest) { got, total -> onProgress(if (total > 0) got.toFloat() / total else 0f) }
                PendingRestore.stage(context, dest, db.readableDatabase.version)
            } finally {
                dest.delete()
            }
        }
    }

    /** "Save a backup file": builds the archive and copies it to a place the user chose. */
    suspend fun exportTo(target: Uri) = lock.withLock {
        withContext(Dispatchers.IO) {
            requireFreeSpace()
            val built = BackupArchive.build(context, db, work)
            try {
                val out = context.contentResolver.openOutputStream(target) ?: throw BackupException("Could not write to that location")
                out.use { o -> built.file.inputStream().use { it.copyTo(o) } }
            } finally {
                built.file.delete(); work.deleteRecursively()
            }
        }
    }

    suspend fun stageRestoreFromUri(source: Uri) = lock.withLock {
        withContext(Dispatchers.IO) {
            val tmp = File(context.cacheDir, "restore/picked.zip").apply { parentFile?.mkdirs() }
            try {
                val input = context.contentResolver.openInputStream(source) ?: throw BackupException("Could not read that file")
                input.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                PendingRestore.stage(context, tmp, db.readableDatabase.version)
            } finally {
                tmp.delete()
            }
        }
    }

    /** Removes every backup this app made in the user's Drive. Part of "delete my data". */
    suspend fun deleteAllDriveBackups(): Int = lock.withLock { withContext(Dispatchers.IO) { drive().deleteAll() } }

    private suspend fun prune(provider: BackupProvider) {
        try {
            provider.list().drop(DriveProtocol.KEEP_REMOTE_BACKUPS).forEach { provider.delete(it) }
        } catch (_: Exception) { /* pruning is housekeeping, never a failure */ }
    }

    private fun requireFreeSpace() {
        val need = context.getDatabasePath(NoteDb.FILE_NAME).length() * 2 + File(context.filesDir, "assets").walkTopDown().sumOf { if (it.isFile) it.length() else 0L }
        if (context.cacheDir.usableSpace < need + MIN_FREE) throw BackupException("Not enough free space on this device for a backup")
    }

    private companion object {
        const val STALE_AFTER_MS = 6 * 60 * 60 * 1000L
        const val MIN_FREE = 50L * 1024 * 1024
    }
}
