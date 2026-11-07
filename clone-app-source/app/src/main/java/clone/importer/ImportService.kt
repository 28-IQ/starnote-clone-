package clone.importer

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import clone.data.AssetRepo
import clone.data.NewPage
import clone.data.NotebookRepo
import clone.ink.Sizing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.UUID

class ImportException(message: String) : Exception(message)

/**
 * Brings a PDF or pictures in: copies the file into app storage under its sha256 (the same file imported
 * twice is stored once), then creates a notebook with one page per PDF page or per picture.
 * Office files and e-books are not handled yet.
 */
class ImportService(
    private val context: Context,
    private val assets: AssetRepo,
    private val notebooks: NotebookRepo,
    private val assetsDir: File,
) {
    /** Returns the new notebook's id. */
    suspend fun importPdf(uri: Uri, folderId: String? = null): String = withContext(Dispatchers.IO) {
        val copied = copyIn(uri, "pdf")
        try {
            val sizes = readPdfPageSizes(copied.file)
            val asset = assets.findBySha(copied.sha256)
                ?: assets.insert("pdf", copied.file.name, "application/pdf", copied.sha256, copied.bytes, null, null, sizes.size).let { id ->
                    AssetRepo.Asset(id, "pdf", copied.file.name, sizes.size)
                }
            val pages = sizes.mapIndexed { i, (w, h) -> NewPage(w, h, sourceAssetId = asset.id, sourcePageNo = i + 1) }
            notebooks.createImported(displayName(uri, "Imported PDF"), asset.id, pages, folderId)
        } catch (e: Exception) {
            if (assets.findBySha(copied.sha256) == null) copied.file.delete()    // do not keep a file nothing refers to
            throw if (e is ImportException) e else ImportException("This file could not be imported")
        }
    }

    /** One notebook, one page per picture, in the order given. */
    suspend fun importImages(uris: List<Uri>, folderId: String? = null): String = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) throw ImportException("No pictures were chosen")
        val pages = ArrayList<NewPage>()
        for (uri in uris) {
            val copied = copyIn(uri, "img")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(copied.file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                if (assets.findBySha(copied.sha256) == null) copied.file.delete()
                throw ImportException("One of the files is not a picture this app can read")
            }
            val assetId = assets.findBySha(copied.sha256)?.id
                ?: assets.insert("image", copied.file.name, bounds.outMimeType ?: "image/*", copied.sha256, copied.bytes, bounds.outWidth, bounds.outHeight, null)
            val (w, h) = Sizing.imagePageSize(bounds.outWidth, bounds.outHeight)
            pages += NewPage(w, h, templateAssetId = assetId)
        }
        notebooks.createImported(displayName(uris.first(), "Pictures"), null, pages, folderId)
    }

    class Picture(val assetId: String, val fileName: String, val width: Int, val height: Int)

    /** One picture to place on an existing page. Stored once by content, like any import. */
    suspend fun importPicture(uri: Uri): Picture = withContext(Dispatchers.IO) {
        val copied = copyIn(uri, "img")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(copied.file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            if (assets.findBySha(copied.sha256) == null) copied.file.delete()
            throw ImportException("That file is not a picture this app can read")
        }
        val id = assets.findBySha(copied.sha256)?.id
            ?: assets.insert("image", copied.file.name, bounds.outMimeType ?: "image/*", copied.sha256, copied.bytes, bounds.outWidth, bounds.outHeight, null)
        Picture(id, copied.file.name, bounds.outWidth, bounds.outHeight)
    }

    private class Copied(val file: File, val sha256: String, val bytes: Long)

    private fun copyIn(uri: Uri, ext: String): Copied {
        assetsDir.mkdirs()
        val tmp = File(assetsDir, "import-${UUID.randomUUID()}.tmp")
        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw ImportException("That file could not be opened")
            input.use { src ->
                DigestOutputStream(tmp.outputStream(), digest).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = src.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n); bytes += n
                        if (bytes > MAX_IMPORT_BYTES) throw ImportException("That file is too large (limit 500 MB)")
                        if (bytes % (8 * 1024 * 1024) < buf.size && assetsDir.usableSpace < MIN_FREE) throw ImportException("Not enough free space on this device")
                    }
                }
            }
            if (bytes == 0L) throw ImportException("That file is empty")
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            val final = File(assetsDir, "$sha.$ext")
            if (final.exists()) tmp.delete() else if (!tmp.renameTo(final)) throw ImportException("Could not save the file")
            return Copied(final, sha, bytes)
        } catch (e: IOException) {
            tmp.delete(); throw ImportException("That file could not be read")
        } catch (e: ImportException) {
            tmp.delete(); throw e
        }
    }

    /** Page sizes in points, in order. PdfRenderer allows one open page at a time. */
    private fun readPdfPageSizes(file: File): List<Pair<Float, Float>> {
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                val renderer = PdfRenderer(pfd)
                try {
                    if (renderer.pageCount == 0) throw ImportException("This PDF has no pages")
                    return (0 until renderer.pageCount).map { i ->
                        val page = renderer.openPage(i)
                        try { page.width.toFloat() to page.height.toFloat() } finally { page.close() }
                    }
                } finally {
                    renderer.close()
                }
            }
        } catch (e: SecurityException) {
            throw ImportException("This PDF is password protected")
        } catch (e: IOException) {
            throw ImportException("This file could not be opened as a PDF")
        }
    }

    private fun displayName(uri: Uri, fallback: String): String {
        val name = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) { null }
        return name?.substringBeforeLast('.')?.trim()?.ifEmpty { null } ?: fallback
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 500L * 1024 * 1024
        const val MIN_FREE = 50L * 1024 * 1024
    }
}
