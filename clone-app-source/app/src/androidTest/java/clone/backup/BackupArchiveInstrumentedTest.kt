package clone.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import clone.data.NoteDb
import clone.data.NotebookRepo
import clone.data.StoredStroke
import clone.data.StrokeRepo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/** Cases F17-H1, F17-E1 (BUG-003), F17-E2, F17-N1. Written, not run. */
class BackupArchiveInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "test-backup.db"
    private val work = File(context.cacheDir, "backup-test")
    private lateinit var db: NoteDb

    @Before fun open() { context.deleteDatabase(name); work.deleteRecursively(); db = NoteDb(context, name) }
    @After fun close() { db.close(); context.deleteDatabase(name); work.deleteRecursively() }

    private fun fill(count: Int) {
        val nb = NotebookRepo(db, java.io.File(context.filesDir, "assets")).create("Backup me")
        val page = NotebookRepo(db, java.io.File(context.filesDir, "assets")).openFirstPage(nb)!!
        val repo = StrokeRepo(db)
        repeat(count) { repo.insert(StoredStroke(0, page.pageId, page.layerId, it + 1, ByteArray(200), "Pen", "{}", 0f, 0f, 1f, 1f)) }
    }

    private fun strokesIn(archive: File): Int {
        val out = File(work, "extracted.db")
        ZipFile(archive).use { z -> z.getInputStream(z.getEntry(BackupArchive.DB_ENTRY)).use { i -> out.outputStream().use { i.copyTo(it) } } }
        return android.database.sqlite.SQLiteDatabase.openDatabase(out.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use {
            it.rawQuery("select count(*) from strokes", null).use { c -> c.moveToFirst(); c.getInt(0) }
        }
    }

    @Test fun archiveVerifiesAndHoldsEveryStroke() {   // F17-H1
        fill(500)
        val built = BackupArchive.build(context, db, work)
        BackupArchive.verify(built.file, db.readableDatabase.version)
        assertEquals(500, strokesIn(built.file))
    }

    @Test fun strokesStillInTheWriteAheadLogAreInTheSnapshot() {   // F17-E1 / BUG-003
        db.writableDatabase.rawQuery("PRAGMA wal_autocheckpoint = 0", null).use { it.moveToFirst() }   // keep everything in the log
        fill(800)
        val built = BackupArchive.build(context, db, work)
        assertEquals(800, strokesIn(built.file))
    }

    @Test fun unchangedNotesGiveTheSameContentHash() {  // F17-E2
        fill(50)
        val a = BackupArchive.build(context, db, File(work, "a"))
        Thread.sleep(1500)
        val b = BackupArchive.build(context, db, File(work, "b"))
        assertEquals(a.contentHash, b.contentHash)
    }

    @Test fun aDamagedArchiveIsRejected() {            // F17-N1
        fill(50)
        val built = BackupArchive.build(context, db, work)
        val bytes = built.file.readBytes()
        bytes[bytes.size / 3] = (bytes[bytes.size / 3].toInt() xor 0xFF).toByte()
        val damaged = File(work, "damaged.zip").apply { writeBytes(bytes) }
        assertThrows(Exception::class.java) { BackupArchive.verify(damaged, db.readableDatabase.version) }
    }
}
