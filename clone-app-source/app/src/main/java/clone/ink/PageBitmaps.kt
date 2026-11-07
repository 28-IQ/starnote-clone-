package clone.ink

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import clone.data.PageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Turns a page background (one page of a PDF, or a picture) into a bitmap. Platform PdfRenderer: it gives
 * pictures of pages only, no text, so selecting or searching PDF text needs a PDF library later.
 * A small memory cache keeps recent pages. Bitmaps are white-filled before a PDF page is drawn onto them.
 */
class PageBitmaps {
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val lock = Mutex()
    private val renderers = LinkedHashMap<String, PdfRenderer>()
    private val descriptors = LinkedHashMap<String, ParcelFileDescriptor>()

    /** For the screen: cached, rendered off the main thread. Null if the file cannot be read. */
    suspend fun get(bg: PageBackground, widthPx: Int, heightPx: Int): Bitmap? = lock.withLock {
        val key = "${bg.file.path}#${bg.pdfPageNo}#$widthPx"
        cache.get(key) ?: withContext(Dispatchers.Default) { renderNow(bg, widthPx, heightPx) }?.also { cache.put(key, it) }
    }

    /** For export: not cached, the caller recycles the bitmap. */
    fun renderNow(bg: PageBackground, widthPx: Int, heightPx: Int): Bitmap? = try {
        when (bg.kind) {
            "pdf" -> renderPdf(bg.file, bg.pdfPageNo ?: 1, widthPx, heightPx)
            else -> decodeImage(bg.file, widthPx, heightPx)
        }
    } catch (e: Exception) {
        null    // a missing or damaged file leaves the page blank instead of crashing the editor
    }

    @Synchronized
    private fun renderPdf(file: File, pageNo: Int, widthPx: Int, heightPx: Int): Bitmap {
        val key = file.path
        val renderer = renderers.getOrPut(key) {
            while (renderers.size >= MAX_OPEN_PDFS) closeOldest()
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            descriptors[key] = pfd
            PdfRenderer(pfd)
        }
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val page = renderer.openPage(pageNo - 1)
        try {
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        } finally {
            page.close()
        }
        return bitmap
    }

    private fun closeOldest() {
        val oldest = renderers.keys.first()
        renderers.remove(oldest)?.close()
        descriptors.remove(oldest)?.close()
    }

    private fun decodeImage(file: File, targetW: Int, targetH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = Sizing.sampleSize(bounds.outWidth, bounds.outHeight, targetW, targetH) }
        return BitmapFactory.decodeFile(file.path, opts)
    }

    private companion object {
        const val CACHE_BYTES = 48 * 1024 * 1024
        const val MAX_OPEN_PDFS = 2
    }
}
