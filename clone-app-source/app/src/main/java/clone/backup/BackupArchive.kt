package clone.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import clone.data.NoteDb
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Backup archive: a zip with `manifest.json`, `notes.db` and `assets/...`.
 * Built from a snapshot of the database taken while writers are blocked, then zipped as a stream
 * (never held in memory). Zip64 for archives over 4 GB depends on the platform ZipOutputStream: verify on a device.
 */
object BackupArchive {
    const val FORMAT = 1
    const val MANIFEST = "manifest.json"
    const val DB_ENTRY = "notes.db"
    private const val ASSETS_DIR = "assets"

    /** [contentHash] covers the files' contents only, so an unchanged library gives the same value every time. */
    class Built(val file: File, val sha256: String, val contentHash: String, val bytes: Long)
    class Manifest(val format: Int, val schemaVersion: Int, val createdAt: Long, val files: List<Item>) {
        class Item(val name: String, val size: Long, val sha256: String)
    }

    /** Returns the finished archive in [outDir], named by time and hash. Caller deletes it after upload. */
    fun build(context: Context, db: NoteDb, outDir: File): Built {
        outDir.mkdirs()
        val snapshot = File(outDir, "snapshot.db")
        snapshotDatabase(context, db, snapshot)

        val tmp = File(outDir, "archive.tmp")
        val archiveDigest = MessageDigest.getInstance("SHA-256")
        val items = ArrayList<Manifest.Item>()
        try {
            ZipOutputStream(DigestOutputStream(BufferedOutputStream(FileOutputStream(tmp)), archiveDigest)).use { zip ->
                zip.setLevel(Deflater.BEST_SPEED)   // stroke data is already gzip-compressed
                items += addFile(zip, DB_ENTRY, snapshot)
                val assets = File(context.filesDir, ASSETS_DIR)
                if (assets.isDirectory) {
                    assets.walkTopDown().filter { it.isFile }.forEach { f ->
                        items += addFile(zip, "$ASSETS_DIR/" + f.relativeTo(assets).path.replace(File.separatorChar, '/'), f)
                    }
                }
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(manifestJson(db.readableDatabase.version, items).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        } finally {
            snapshot.delete()
        }
        val sha = archiveDigest.digest().toHex()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val final = File(outDir, "notebook-backup-$stamp-${sha.take(8)}.zip")
        if (!tmp.renameTo(final)) throw BackupException("Could not finish the backup file")
        return Built(final, sha, contentHash(items), final.length())
    }

    /**
     * Consistent copy of the database. Folding the write-ahead log into the main file can be refused while
     * another connection is reading, and the result of that request is not reliable, so the copy takes the
     * main file and the log together while writers are blocked. The log is then folded into the copy, which
     * leaves one self-contained file for the archive. The lock is held only for the file copy.
     */
    private fun snapshotDatabase(context: Context, db: NoteDb, dest: File) {
        val src = db.databaseFile
        val w = db.writableDatabase
        w.rawQuery("PRAGMA wal_checkpoint(PASSIVE)", null).use { it.moveToFirst() }   // keeps the log short; not relied on
        w.beginTransaction()
        try {
            src.inputStream().use { input -> FileOutputStream(dest).use { out -> input.copyTo(out) } }
            val wal = File(src.path + "-wal")
            if (wal.exists() && wal.length() > 0) {
                wal.inputStream().use { input -> FileOutputStream(File(dest.path + "-wal")).use { out -> input.copyTo(out) } }
            }
        } finally {
            w.endTransaction()
        }
        SQLiteDatabase.openDatabase(dest.path, null, SQLiteDatabase.OPEN_READWRITE).use { copy ->
            copy.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
        }
        File(dest.path + "-wal").delete()
        File(dest.path + "-shm").delete()
    }

    private fun addFile(zip: ZipOutputStream, name: String, file: File): Manifest.Item {
        val digest = MessageDigest.getInstance("SHA-256")
        zip.putNextEntry(ZipEntry(name))
        var size = 0L
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                zip.write(buf, 0, n); digest.update(buf, 0, n); size += n
            }
        }
        zip.closeEntry()
        return Manifest.Item(name, size, digest.digest().toHex())
    }

    fun contentHash(items: List<Manifest.Item>): String {
        val d = MessageDigest.getInstance("SHA-256")
        items.sortedBy { it.name }.forEach { d.update("${it.name}:${it.sha256}\n".toByteArray(Charsets.UTF_8)) }
        return d.digest().toHex()
    }

    private fun manifestJson(schemaVersion: Int, items: List<Manifest.Item>): String {
        val files = JSONArray()
        items.forEach { files.put(JSONObject().put("name", it.name).put("size", it.size).put("sha256", it.sha256)) }
        return JSONObject().put("format", FORMAT).put("schemaVersion", schemaVersion)
            .put("createdAt", System.currentTimeMillis()).put("files", files).toString()
    }

    fun readManifest(zip: ZipFile): Manifest {
        val entry = zip.getEntry(MANIFEST) ?: throw BackupException("This file is not a backup (no manifest)")
        val o = JSONObject(zip.getInputStream(entry).bufferedReader().use { it.readText() })
        val arr = o.getJSONArray("files")
        val files = (0 until arr.length()).map {
            val f = arr.getJSONObject(it)
            Manifest.Item(f.getString("name"), f.getLong("size"), f.getString("sha256"))
        }
        return Manifest(o.getInt("format"), o.getInt("schemaVersion"), o.getLong("createdAt"), files)
    }

    /** Checks format, schema version and every file hash. Throws BackupException with a plain message. */
    fun verify(archive: File, maxSchemaVersion: Int): Manifest {
        val zip = try { ZipFile(archive) } catch (e: Exception) { throw BackupException("This file is not a valid backup", cause = e) }
        zip.use {
            val m = readManifest(it)
            if (m.format > FORMAT) throw BackupException("This backup was made by a newer version of the app")
            if (m.schemaVersion > maxSchemaVersion) throw BackupException("This backup was made by a newer version of the app")
            if (m.files.none { f -> f.name == DB_ENTRY }) throw BackupException("The backup has no notes database")
            m.files.forEach { item ->
                require(isSafeName(item.name)) { "Unsafe entry name" }
                val e = it.getEntry(item.name) ?: throw BackupException("The backup is missing ${item.name}")
                if (sha256Of(it.getInputStream(e)) != item.sha256) throw BackupException("The backup is damaged (${item.name})")
            }
            return m
        }
    }

    /** Rejects names that could escape the target folder when unpacked. */
    fun isSafeName(name: String): Boolean =
        name.isNotEmpty() && !name.startsWith("/") && !name.contains("..") && !name.contains('\\') && !name.contains('\u0000')

    fun sha256Of(input: InputStream): String {
        val d = MessageDigest.getInstance("SHA-256")
        input.use { s -> val buf = ByteArray(64 * 1024); while (true) { val n = s.read(buf); if (n < 0) break; d.update(buf, 0, n) } }
        return d.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

}
