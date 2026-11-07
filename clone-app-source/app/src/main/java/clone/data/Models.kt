package clone.data

data class NotebookSummary(val id: String, val title: String, val updatedAt: Long)

/** What sits behind the ink on a page: one page of a PDF, or a picture. */
data class PageBackground(val kind: String, val file: java.io.File, val pdfPageNo: Int?)

/** A page open in the editor, with its first layer. */
data class OpenPage(
    val notebookId: String,
    val notebookTitle: String,
    val pageId: String,
    val layerId: String,
    val widthPt: Float,
    val heightPt: Float,
    val index: Int,
    val pageCount: Int,
    val background: PageBackground?,
)

/** A page to create when a notebook is made from an import. */
data class NewPage(
    val widthPt: Float,
    val heightPt: Float,
    val sourceAssetId: String? = null,     // the PDF
    val sourcePageNo: Int? = null,         // 1-based page in that PDF
    val templateAssetId: String? = null,   // a picture used as the page background
)

class StoredStroke(
    val id: Long,                 // 0 or less = let the database assign one
    val pageId: String,
    val layerId: String,
    val z: Int,
    val data: ByteArray,          // androidx.ink encoded inputs
    val brushType: String,
    val brushParams: String,      // json from BrushSpec
    val minX: Float, val minY: Float, val maxX: Float, val maxY: Float,
) {
    fun withId(newId: Long) = StoredStroke(newId, pageId, layerId, z, data, brushType, brushParams, minX, minY, maxX, maxY)
}
