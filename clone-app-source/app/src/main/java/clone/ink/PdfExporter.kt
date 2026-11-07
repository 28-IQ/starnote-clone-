package clone.ink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import clone.data.PageObject
import java.io.File

/** One page to write: its size in points, an optional background picture, and the ink on top. */
class ExportPage(
    val widthPt: Int, val heightPt: Int, val background: Bitmap?, val strokes: List<Stroke>,
    val objects: List<PageObject> = emptyList(), val objectBitmaps: Map<String, Bitmap> = emptyMap(),
)

/**
 * Flattened export through the platform PdfDocument, all pages. Backgrounds are pictures drawn behind the ink
 * (an imported PDF page is rasterised, so its text is no longer selectable). Page size is in points, the same
 * unit as stroke coordinates. Later: vector merge and editable PDF through a PDF library (architecture.md).
 */
object PdfExporter {

    /** [pageAt] is called once per page, in order, and the page's bitmap is recycled right after it is written. */
    fun export(context: Context, title: String, pageCount: Int, pageAt: (Int) -> ExportPage, onProgress: (Int) -> Unit = {}): File {
        val doc = PdfDocument()
        try {
            val renderer = CanvasStrokeRenderer.create()
            val identity = Matrix()
            for (i in 0 until pageCount) {
                val p = pageAt(i)
                val page = doc.startPage(PdfDocument.PageInfo.Builder(p.widthPt, p.heightPt, i + 1).create())
                val canvas = page.canvas
                canvas.drawColor(android.graphics.Color.WHITE)
                p.background?.let { canvas.drawBitmap(it, null, Rect(0, 0, p.widthPt, p.heightPt), null) }
                p.objects.forEach { ObjectPainter.draw(canvas, it, it.assetId?.let { id -> p.objectBitmaps[id] }) }
                p.strokes.forEach { renderer.draw(canvas = canvas, stroke = it, strokeToScreenTransform = identity) }
                doc.finishPage(page)
                p.background?.recycle()
                p.objectBitmaps.values.forEach { it.recycle() }
                onProgress(i + 1)
            }
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, FileNames.safe(title) + ".pdf")
            file.outputStream().use { doc.writeTo(it) }
            return file
        } finally {
            doc.close()
        }
    }
}
