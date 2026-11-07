package clone.data

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import android.content.Context

/** The slice exit test from architecture.md, on a real Android SQLite: strokes survive closing and reopening. Written, not run. */
class NoteDbInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "test-notes.db"

    @Before fun clean() { context.deleteDatabase(name) }
    @After fun remove() { context.deleteDatabase(name) }

    private fun stroke(pageId: String, layerId: String, z: Int, x: Float) =
        StoredStroke(0, pageId, layerId, z, ByteArray(300) { it.toByte() }, "Pen", "{}", x, x, x + 5, x + 5)

    @Test fun twoThousandStrokesSurviveReopen() {      // F01-H4 / exit test
        val db1 = NoteDb(context, name)
        val nb = NotebookRepo(db1, java.io.File(context.filesDir, "assets")).create("Persist")
        val page = NotebookRepo(db1, java.io.File(context.filesDir, "assets")).openFirstPage(nb)!!
        val strokes1 = StrokeRepo(db1)
        repeat(2000) { strokes1.insert(stroke(page.pageId, page.layerId, it + 1, it.toFloat())) }
        db1.close()

        val db2 = NoteDb(context, name)
        val strokes2 = StrokeRepo(db2)
        assertEquals(2000, strokes2.visibleOnPage(page.pageId).size)
        db2.close()
    }

    @Test fun eraserQueryFindsOnlyNearbyStrokes() {    // F04-E1
        val db = NoteDb(context, name)
        val page = NotebookRepo(db, java.io.File(context.filesDir, "assets")).let { it.openFirstPage(it.create("Near"))!! }
        val repo = StrokeRepo(db)
        val near = repo.insert(stroke(page.pageId, page.layerId, 1, 10f))
        repo.insert(stroke(page.pageId, page.layerId, 2, 300f))
        assertEquals(listOf(near), repo.candidates(page.pageId, 8f, 8f, 12f, 12f).map { it.id })
        db.close()
    }

    @Test fun erasedStrokeIsRestoredUnderItsOldId() {  // F04-E3 (undo of erase)
        val db = NoteDb(context, name)
        val page = NotebookRepo(db, java.io.File(context.filesDir, "assets")).let { it.openFirstPage(it.create("Undo"))!! }
        val repo = StrokeRepo(db)
        val id = repo.insert(stroke(page.pageId, page.layerId, 1, 10f))
        val row = repo.visibleOnPage(page.pageId).single()
        repo.delete(listOf(id))
        assertEquals(0, repo.visibleOnPage(page.pageId).size)
        repo.insert(row)
        assertEquals(id, repo.visibleOnPage(page.pageId).single().id)
        db.close()
    }
}
