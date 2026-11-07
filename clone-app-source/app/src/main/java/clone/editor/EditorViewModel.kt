package clone.editor

import android.app.Application
import androidx.compose.runtime.getValue
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.ink.strokes.Stroke
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import clone.app.CloneApp
import clone.data.Ids
import clone.data.OpenPage
import clone.data.PageBackground
import clone.data.PageObject
import clone.data.TextProps
import clone.data.StoredStroke
import clone.ink.BrushDefaults
import clone.ink.BrushSpec
import clone.importer.ImportException
import clone.ink.ExportPage
import clone.ink.ObjectPainter
import clone.ink.PdfExporter
import clone.ink.PenKind
import clone.ink.Sizing
import clone.ink.StrokeCodec
import clone.ink.StrokeGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.min
import kotlin.math.sqrt

enum class Tool { Pen, Highlighter, Eraser, Text, Select }

class DrawnStroke(val rowId: Long, val z: Int, val stroke: Stroke)

sealed interface EditCommand {
    class AddStroke(val row: StoredStroke) : EditCommand
    class EraseStrokes(val rows: List<StoredStroke>) : EditCommand
    class AddObject(val obj: PageObject) : EditCommand
    class DeleteObject(val obj: PageObject) : EditCommand
    class ChangeObject(val before: PageObject, val after: PageObject) : EditCommand
}

/** The text box dialog: a new box at a point, or the text of an existing one. */
class TextDialog(val editId: String?, val x: Float, val y: Float, val initial: String)

/**
 * Holds the open page in memory and writes every change straight to the database. Undo history belongs to
 * the page on screen and is dropped when the page changes.
 * Slice shortcut: database writes happen on the main thread (one small row per pen-up).
 * Next step is a single writer thread with client-side ids, so a stroke can be undone before its row exists.
 */
class EditorViewModel(app: Application, private val notebookId: String) : AndroidViewModel(app) {

    private val clone = app as CloneApp
    private val log = CommandLog<EditCommand>()
    private val byId = HashMap<Long, DrawnStroke>()

    var page by mutableStateOf<OpenPage?>(null); private set
    val strokes = mutableStateListOf<DrawnStroke>()

    var tool by mutableStateOf(Tool.Pen); private set
    var sizeIndex by mutableStateOf(BrushDefaults.DEFAULT_SIZE_INDEX)
    var colorArgb by mutableStateOf(0)           // 0 until the screen supplies theme colors
    var highlightArgb by mutableStateOf(0)
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set
    var skippedStrokes by mutableStateOf(0); private set

    var exporting by mutableStateOf(false); private set
    var exportedFile by mutableStateOf<File?>(null); private set
    var notice by mutableStateOf<String?>(null); private set      // export and picture errors

    val objects = mutableStateListOf<PageObject>()
    val objectBitmaps = mutableStateMapOf<String, Bitmap>()        // picture asset id -> decoded picture
    var selectedId by mutableStateOf<String?>(null); private set
    var textDialog by mutableStateOf<TextDialog?>(null); private set
    private var moveStart: PageObject? = null

    /** What the next stroke will look like. The eraser keeps the pen's look so switching back is instant. */
    val spec: BrushSpec
        get() = if (tool == Tool.Highlighter)
            BrushSpec(PenKind.Highlighter, highlightArgb, BrushDefaults.highlighterSizesPt[sizeIndex])
        else
            BrushSpec(PenKind.Pen, colorArgb, BrushDefaults.sizesPt[sizeIndex])

    private var erasing: MutableList<StoredStroke>? = null

    init {
        goTo(0)
        if (page != null) clone.notebooks.touch(notebookId)
    }

    fun ensureColors(ink: Int, highlight: Int) {
        if (colorArgb == 0) colorArgb = ink
        if (highlightArgb == 0) highlightArgb = highlight
    }

    // ---- pages ----

    fun goTo(index: Int) {
        val target = clone.notebooks.openPage(notebookId, index) ?: return
        strokes.clear(); byId.clear(); log.clear()
        erasing = null
        objects.clear(); selectedId = null; textDialog = null; moveStart = null
        page = target
        objects.addAll(clone.objects.forPage(target.pageId))
        loadPictures()
        var skipped = 0
        clone.strokes.visibleOnPage(target.pageId).forEach { row ->
            try {
                add(DrawnStroke(row.id, row.z, StrokeCodec.decode(row)))
            } catch (e: Exception) {      // a corrupt row must not stop the page from opening
                skipped++
            }
        }
        skippedStrokes = skipped
        refreshFlags()
    }

    fun nextPage() { page?.let { goTo(Sizing.clampPage(it.index + 1, it.pageCount)) } }
    fun previousPage() { page?.let { goTo(Sizing.clampPage(it.index - 1, it.pageCount)) } }
    fun addPage() { goTo(clone.notebooks.addBlankPage(notebookId)) }

    fun chooseTool(t: Tool) {
        tool = t
        if (t != Tool.Select) selectedId = null
    }

    // ---- text boxes and pictures ----

    val selected: PageObject? get() = objects.firstOrNull { it.id == selectedId }

    fun requestNewText(x: Float, y: Float) { textDialog = TextDialog(null, x, y, "") }

    fun requestEditText() {
        val o = selected ?: return
        if (o.kind == "text") textDialog = TextDialog(o.id, 0f, 0f, TextProps.fromJson(o.props).text)
    }

    fun dismissTextDialog() { textDialog = null }

    fun confirmText(text: String) {
        val d = textDialog ?: return
        textDialog = null
        val p = page ?: return
        if (text.isBlank()) return
        if (d.editId == null) {
            val props = TextProps(text, TextProps.SIZES_PT[TextProps.DEFAULT_SIZE_INDEX], colorArgb)
            val w = min(DEFAULT_TEXT_WIDTH_PT, p.widthPt)
            val h = ObjectPainter.measureTextHeight(props, w)
            val (x, y) = ObjectGeometry.clampToPage(d.x, d.y, w, h, p.widthPt, p.heightPt)
            val obj = PageObject(Ids.uuid7(), p.pageId, p.layerId, "text", null, null, props.toJson(), clone.objects.nextZ(p.pageId), x, y, w, h)
            clone.objects.insert(obj)
            objects.add(obj)
            log.push(EditCommand.AddObject(obj))
            selectedId = obj.id; tool = Tool.Select
        } else {
            val before = objects.firstOrNull { it.id == d.editId } ?: return
            val props = TextProps.fromJson(before.props).copy(text = text)
            changeObject(before, before.withProps(props.toJson(), ObjectPainter.measureTextHeight(props, before.w)))
        }
        refreshFlags()
    }

    fun changeTextSize(up: Boolean) {
        val before = selected?.takeIf { it.kind == "text" } ?: return
        val old = TextProps.fromJson(before.props)
        val props = old.copy(sizePt = TextProps.stepSize(old.sizePt, up))
        if (props.sizePt == old.sizePt) return
        changeObject(before, before.withProps(props.toJson(), ObjectPainter.measureTextHeight(props, before.w)))
        refreshFlags()
    }

    fun scaleSelected(factor: Float) {
        val p = page ?: return
        val before = selected?.takeIf { it.kind == "image" } ?: return
        val box = ObjectGeometry.scaleAbout(ObjectGeometry.Box(before.x, before.y, before.w, before.h), factor, p.widthPt, p.heightPt)
        changeObject(before, before.moved(box.x, box.y).resized(box.w, box.h))
        refreshFlags()
    }

    fun deleteSelected() {
        val o = selected ?: return
        clone.objects.delete(o.id)
        objects.removeAll { it.id == o.id }
        log.push(EditCommand.DeleteObject(o))
        selectedId = null
        refreshFlags()
    }

    /** Taps in the Select tool: pick the topmost object under the point, or nothing. */
    fun selectAt(x: Float, y: Float) { selectedId = hit(x, y)?.id }

    /** Start of a drag: picks the object under the finger and remembers where it was. Returns false if there is none. */
    fun beginMove(x: Float, y: Float): Boolean {
        val o = hit(x, y) ?: run { selectedId = null; moveStart = null; return false }
        selectedId = o.id; moveStart = o
        return true
    }

    fun moveBy(dx: Float, dy: Float) {
        val p = page ?: return
        val start = moveStart ?: return
        val current = selected ?: return
        val (x, y) = ObjectGeometry.clampToPage(current.x + dx, current.y + dy, start.w, start.h, p.widthPt, p.heightPt)
        replaceObject(current.moved(x, y))
    }

    fun endMove() {
        val start = moveStart ?: return
        moveStart = null
        val end = selected ?: return
        if (end.x == start.x && end.y == start.y) return
        clone.objects.update(end)
        log.push(EditCommand.ChangeObject(start, end))
        refreshFlags()
    }

    fun insertPicture(uri: android.net.Uri) {
        val p = page ?: return
        viewModelScope.launch {
            try {
                val pic = clone.importer.importPicture(uri)
                val (w, h) = ObjectGeometry.defaultPictureSize(pic.width, pic.height, p.widthPt)
                val (x, y) = ObjectGeometry.clampToPage((p.widthPt - w) / 2f, p.heightPt / 4f, w, h, p.widthPt, p.heightPt)
                val obj = PageObject(Ids.uuid7(), p.pageId, p.layerId, "image", pic.assetId, pic.fileName, "{}", clone.objects.nextZ(p.pageId), x, y, w, h)
                if (page?.pageId != p.pageId) return@launch        // the user turned the page while the picture was loading
                clone.objects.insert(obj)
                objects.add(obj)
                log.push(EditCommand.AddObject(obj))
                selectedId = obj.id; tool = Tool.Select
                loadPictures()
                refreshFlags()
            } catch (e: ImportException) {
                notice = e.message
            } catch (e: Exception) {
                notice = "The picture could not be added"
            }
        }
    }

    private fun hit(x: Float, y: Float): PageObject? {
        val i = ObjectGeometry.topmostAt(objects.map { ObjectGeometry.Box(it.x, it.y, it.w, it.h) }, x, y)
        return if (i >= 0) objects[i] else null
    }

    private fun changeObject(before: PageObject, after: PageObject) {
        clone.objects.update(after)
        replaceObject(after)
        log.push(EditCommand.ChangeObject(before, after))
    }

    private fun replaceObject(o: PageObject) {
        val i = objects.indexOfFirst { it.id == o.id }
        if (i >= 0) objects[i] = o
    }

    private fun loadPictures() {
        objects.filter { it.kind == "image" && it.assetId != null && it.assetId !in objectBitmaps }.forEach { o ->
            val path = o.assetPath ?: return@forEach
            viewModelScope.launch {
                clone.pageBitmaps.get(PageBackground("image", File(clone.assetsDir, path), null), PICTURE_DECODE_PX, PICTURE_DECODE_PX)
                    ?.let { objectBitmaps[o.assetId!!] = it }
            }
        }
    }

    // ---- drawing ----

    fun onStrokesFinished(finished: List<Stroke>) {
        val p = page ?: return
        val usedSpec = spec
        finished.forEach { stroke ->
            val z = clone.strokes.nextZ(p.pageId)
            val row = StrokeCodec.toRow(stroke, usedSpec, p.pageId, p.layerId, z)
            val id = clone.strokes.insert(row)
            add(DrawnStroke(id, z, stroke))
            log.push(EditCommand.AddStroke(row.withId(id)))
        }
        refreshFlags()
    }

    // ---- stroke eraser (whole strokes). Coordinates are page points. ----

    fun eraseBegin() { erasing = mutableListOf() }

    fun eraseAt(x: Float, y: Float, radius: Float) {
        val p = page ?: return
        val acc = erasing ?: return
        val hits = clone.strokes.candidates(p.pageId, x - radius, y - radius, x + radius, y + radius)
            .filter { row ->
                val drawn = byId[row.id] ?: return@filter false
                val half = BrushSpec.fromJson(row.brushParams).sizePt / 2f      // reach depends on the target's own width
                StrokeGeometry.distance(drawn.stroke, x, y) <= radius + half
            }
        if (hits.isEmpty()) return
        clone.strokes.delete(hits.map { it.id })
        hits.forEach { remove(it.id) }
        acc.addAll(hits)
    }

    fun eraseEnd() {
        val acc = erasing
        erasing = null
        if (acc != null && acc.isNotEmpty()) log.push(EditCommand.EraseStrokes(acc))
        refreshFlags()
    }

    // ---- undo and redo ----

    fun undo() {
        when (val c = log.popUndo() ?: return) {
            is EditCommand.AddStroke -> removeRows(listOf(c.row))
            is EditCommand.EraseStrokes -> restoreRows(c.rows)
            is EditCommand.AddObject -> removeObject(c.obj.id)
            is EditCommand.DeleteObject -> restoreObject(c.obj)
            is EditCommand.ChangeObject -> applyObject(c.before)
        }
        refreshFlags()
    }

    fun redo() {
        when (val c = log.popRedo() ?: return) {
            is EditCommand.AddStroke -> restoreRows(listOf(c.row))
            is EditCommand.EraseStrokes -> removeRows(c.rows)
            is EditCommand.AddObject -> restoreObject(c.obj)
            is EditCommand.DeleteObject -> removeObject(c.obj.id)
            is EditCommand.ChangeObject -> applyObject(c.after)
        }
        refreshFlags()
    }

    private fun removeObject(id: String) {
        clone.objects.delete(id)
        objects.removeAll { it.id == id }
        if (selectedId == id) selectedId = null
    }

    private fun restoreObject(o: PageObject) {
        clone.objects.insert(o)
        objects.add(o); objects.sortBy { it.z }
        loadPictures()
    }

    private fun applyObject(o: PageObject) {
        clone.objects.update(o)
        replaceObject(o)
    }

    // ---- export (every page) ----

    fun exportPdf() {
        val p = page ?: return
        if (exporting) return
        exporting = true; notice = null
        val onScreen = strokes.map { it.stroke }       // taken on the main thread
        val screenIndex = p.index
        viewModelScope.launch(Dispatchers.Default) {
            try {
                exportedFile = PdfExporter.export(getApplication<Application>(), p.notebookTitle, p.pageCount, pageAt = { i -> exportPage(i, screenIndex, onScreen, objectsOnScreen) })
            } catch (e: Exception) {
                notice = "The PDF could not be created"
            } finally {
                exporting = false
            }
        }
    }

    fun exportShared() { exportedFile = null }

    private fun exportPage(index: Int, screenIndex: Int, onScreen: List<Stroke>, objectsOnScreen: List<PageObject>): ExportPage {
        val op = clone.notebooks.openPage(notebookId, index) ?: throw IllegalStateException("page missing")
        val pageStrokes = if (index == screenIndex) onScreen else clone.strokes.visibleOnPage(op.pageId).mapNotNull {
            try { StrokeCodec.decode(it) } catch (e: Exception) { null }
        }
        // 2x for sharper backgrounds, but never more than about 12 million pixels per page
        val factor = min(2f, sqrt(12_000_000f / (op.widthPt * op.heightPt)))
        val bitmap = op.background?.let { clone.pageBitmaps.renderNow(it, (op.widthPt * factor).toInt().coerceAtLeast(1), (op.heightPt * factor).toInt().coerceAtLeast(1)) }
        val pageObjects = if (index == screenIndex) objectsOnScreen else clone.objects.forPage(op.pageId)
        val pictures = HashMap<String, Bitmap>()
        pageObjects.filter { it.kind == "image" && it.assetId != null && it.assetPath != null }.forEach { o ->
            if (o.assetId!! !in pictures) {
                clone.pageBitmaps.renderNow(PageBackground("image", File(clone.assetsDir, o.assetPath!!), null), (o.w * factor).toInt().coerceAtLeast(1), (o.h * factor).toInt().coerceAtLeast(1))
                    ?.let { pictures[o.assetId] = it }
            }
        }
        return ExportPage(op.widthPt.toInt(), op.heightPt.toInt(), bitmap, pageStrokes, pageObjects, pictures)
    }

    // ---- helpers ----

    private fun add(d: DrawnStroke) { strokes.add(d); byId[d.rowId] = d }

    private fun remove(rowId: Long) {
        val d = byId.remove(rowId) ?: return
        strokes.remove(d)
    }

    private fun removeRows(rows: List<StoredStroke>) {
        clone.strokes.delete(rows.map { it.id })
        rows.forEach { remove(it.id) }
    }

    private fun restoreRows(rows: List<StoredStroke>) {
        rows.forEach { row ->
            clone.strokes.insert(row)
            add(DrawnStroke(row.id, row.z, StrokeCodec.decode(row)))
        }
        strokes.sortBy { it.z }
    }

    private fun refreshFlags() { canUndo = log.canUndo; canRedo = log.canRedo }

    private companion object {
        const val DEFAULT_TEXT_WIDTH_PT = 220f
        const val PICTURE_DECODE_PX = 900
    }

    class Factory(private val app: Application, private val notebookId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = EditorViewModel(app, notebookId) as T
    }
}
