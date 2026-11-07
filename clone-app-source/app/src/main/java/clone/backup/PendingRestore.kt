package clone.backup

import android.content.Context
import clone.data.NoteDb
import java.io.File
import java.util.zip.ZipFile

/**
 * A restore is staged now and applied at the next app start, before the database is opened.
 * That avoids swapping the file under an open connection. The previous database is kept once as notes.db.before-restore.
 */
object PendingRestore {
    private const val DIR = "restore-pending"
    private const val READY = "READY"

    fun stage(context: Context, archive: File, maxSchemaVersion: Int) {
        BackupArchive.verify(archive, maxSchemaVersion)
        val dir = File(context.filesDir, DIR)
        dir.deleteRecursively(); dir.mkdirs()
        ZipFile(archive).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory && it.name != BackupArchive.MANIFEST }.forEach { e ->
                require(BackupArchive.isSafeName(e.name))
                val target = File(dir, e.name)
                target.parentFile?.mkdirs()
                zip.getInputStream(e).use { input -> target.outputStream().use { input.copyTo(it) } }
            }
        }
        File(dir, READY).writeText("1")
    }

    fun isStaged(context: Context): Boolean = File(context.filesDir, "$DIR/$READY").exists()

    /** Call first thing in Application.onCreate, before anything touches the database. */
    fun applyIfAny(context: Context) {
        val dir = File(context.filesDir, DIR)
        if (!File(dir, READY).exists()) return
        val staged = File(dir, BackupArchive.DB_ENTRY)
        if (!staged.exists()) { dir.deleteRecursively(); return }

        val db = context.getDatabasePath(NoteDb.FILE_NAME)
        val keep = File(db.parentFile, NoteDb.FILE_NAME + ".before-restore")
        keep.delete()
        if (db.exists()) db.renameTo(keep)
        File(db.path + "-wal").delete(); File(db.path + "-shm").delete()
        staged.copyTo(db, overwrite = true)

        val assets = File(context.filesDir, "assets")
        val stagedAssets = File(dir, "assets")
        assets.deleteRecursively()
        if (stagedAssets.isDirectory) stagedAssets.copyRecursively(assets, overwrite = true)
        dir.deleteRecursively()
    }
}
