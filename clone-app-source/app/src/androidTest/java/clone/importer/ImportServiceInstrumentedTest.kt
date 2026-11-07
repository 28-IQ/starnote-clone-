package clone.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import clone.data.AssetRepo
import clone.data.NoteDb
import clone.data.NotebookRepo
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.File

/** Cases F03-H2, F03-E2, F03-N2 and F03-H3 (pictures). Written, not run. */
class ImportServiceInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "test-import.db"
    private val assetsDir = File(context.cacheDir, "import-test-assets")
    private lateinit var db: NoteDb
    private lateinit var notebooks: NotebookRepo
    private lateinit var service: ImportService

    @Before fun setUp() {
        context.deleteDatabase(dbName); assetsDir.deleteRecursively()
        db = NoteDb(context, dbName)
        notebooks = NotebookRepo(db, assetsDir)
        service = ImportService(context, AssetRepo(db), notebooks, assetsDir)
    }

    @After fun tearDown() { db.close(); context.deleteDatabase(dbName); assetsDir.deleteRecursively() }

    private fun makePdf(name: String, pages: List<Pair<Int, Int>>): File {
        val doc = PdfDocument()
        pages.forEachIndexed { i, (w, h) ->
            val p = doc.startPage(PdfDocument.PageInfo.Builder(w, h, i + 1).create()); doc.finishPage(p)
        }
        val f = File(context.cacheDir, name)
        f.outputStream().use { doc.writeTo(it) }; doc.close()
        return f
    }

    @Test fun threePagePdfBecomesThreePages() = runBlocking {       // F03-H2
        val pdf = makePdf("three.pdf", listOf(595 to 842, 595 to 842, 842 to 595))
        val id = service.importPdf(pdf.toUri())
        val last = notebooks.openPage(id, 2)!!
        assertEquals(3, last.pageCount)
        assertEquals(842f, last.widthPt, 0.5f)                        // landscape page keeps its own size
        assertEquals("pdf", last.background!!.kind)
        assertEquals(3, last.background!!.pdfPageNo)
    }

    @Test fun theSameFileImportedTwiceIsStoredOnce() = runBlocking {   // F03-E2
        val pdf = makePdf("twice.pdf", listOf(595 to 842))
        service.importPdf(pdf.toUri()); service.importPdf(pdf.toUri())
        assertEquals(1, assetsDir.listFiles { f -> f.name.endsWith(".pdf") }!!.size)
        assertEquals(2, notebooks.list().size)
    }

    @Test fun aTextFileIsRefusedAndLeavesNothingBehind() {            // F03-N2
        val txt = File(context.cacheDir, "not-a-pdf.txt").apply { writeText("hello") }
        assertThrows(ImportException::class.java) { runBlocking { service.importPdf(txt.toUri()) } }
        assertEquals(0, notebooks.list().size)
        assertEquals(0, assetsDir.listFiles()?.size ?: 0)
    }

    @Test fun picturesBecomePagesInOrder() = runBlocking {           // F03-H3
        val files = listOf(200 to 100, 100 to 300).mapIndexed { i, (w, h) ->
            File(context.cacheDir, "pic$i.png").also { f ->
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        val id = service.importImages(files.map { it.toUri() })
        val first = notebooks.openPage(id, 0)!!; val second = notebooks.openPage(id, 1)!!
        assertEquals(2, first.pageCount)
        assertEquals(297.5f, first.heightPt, 0.5f)
        assertEquals(1785f, second.heightPt, 0.5f)
        assertNotNull(first.background)
    }
}
