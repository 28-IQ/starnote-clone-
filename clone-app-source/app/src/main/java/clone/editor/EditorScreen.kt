package clone.editor

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.core.content.FileProvider
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.lifecycle.viewmodel.compose.viewModel
import clone.app.CloneApp
import clone.data.OpenPage
import clone.design.*
import clone.ink.BrushDefaults
import clone.ink.ObjectPainter
import clone.ink.Sizing

/**
 * S05 editor. [pickedPage] is a page chosen in the overview (S29); -1 means none. The screen reports it handled.
 */
@Composable
fun EditorScreen(
    notebookId: String,
    pickedPage: Int,
    onPickedHandled: () -> Unit,
    onBack: () -> Unit,
    onOverview: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as CloneApp
    val vm: EditorViewModel = viewModel(key = "editor-$notebookId", factory = EditorViewModel.Factory(app, notebookId))
    val c = NoteTheme.colors
    LaunchedEffect(c.inkBlack, c.hlYellow) { vm.ensureColors(c.inkBlack.toArgb(), c.hlYellow.toArgb()) }
    LaunchedEffect(pickedPage) { if (pickedPage >= 0) { vm.goTo(pickedPage); onPickedHandled() } }

    val page = vm.page
    if (page == null) {
        Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding().padding(Layout.pageMargin)) {
            NoteButton("Back", onBack, kind = ButtonKind.Ghost)
            InfoBanner("This note could not be opened.", BannerKind.Error)
        }
        return
    }

    val ctx = LocalContext.current
    LaunchedEffect(vm.exportedFile) {
        val file = vm.exportedFile ?: return@LaunchedEffect
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Share this note"))
        vm.exportShared()
    }

    Column(Modifier.fillMaxSize().background(c.surface).systemBarsPadding()) {
        TopBar(vm, page, onBack, onOverview)
        PageBar(vm, page)
        vm.notice?.let { InfoBanner(it, BannerKind.Error) }
        if (vm.skippedStrokes > 0) {
            InfoBanner("${vm.skippedStrokes} strokes could not be read and were left out.", BannerKind.Warning)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(Layout.pageMargin), contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val scale = Sizing.fitScale(with(density) { maxWidth.toPx() }, with(density) { maxHeight.toPx() }, page.widthPt, page.heightPt)
            key(page.pageId) { PageCanvas(vm, page, scale) }     // a fresh canvas for every page
        }
        SelectionBar(vm)
        ToolRow(vm)
    }

    vm.textDialog?.let { d ->
        TextBoxDialog(d.initial, isNew = d.editId == null, onConfirm = { vm.confirmText(it) }, onCancel = { vm.dismissTextDialog() })
    }
}

@Composable
private fun TopBar(vm: EditorViewModel, page: OpenPage, onBack: () -> Unit, onOverview: () -> Unit) {
    val c = NoteTheme.colors
    Row(
        Modifier.fillMaxWidth().height(Layout.toolbarH).background(c.surfaceRaised).padding(horizontal = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NoteButton("Back", onBack, kind = ButtonKind.Ghost)
        Text(page.notebookTitle, Modifier.weight(1f), color = c.text, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        NoteButton("Undo", { vm.undo() }, kind = ButtonKind.Ghost, enabled = vm.canUndo)
        NoteButton("Redo", { vm.redo() }, kind = ButtonKind.Ghost, enabled = vm.canRedo)
        NoteButton("Pages", onOverview, kind = ButtonKind.Ghost)
        NoteButton("Share PDF", { vm.exportPdf() }, kind = ButtonKind.Secondary, enabled = !vm.exporting, loading = vm.exporting)
    }
}

@Composable
private fun PageBar(vm: EditorViewModel, page: OpenPage) {
    val c = NoteTheme.colors
    Row(
        Modifier.fillMaxWidth().background(c.surfaceRaised).padding(horizontal = Space.s2),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        NoteButton("Previous", { vm.previousPage() }, kind = ButtonKind.Ghost, enabled = page.index > 0)
        Text("Page ${page.index + 1} of ${page.pageCount}", color = c.text, style = MaterialTheme.typography.bodyMedium)
        NoteButton("Next", { vm.nextPage() }, kind = ButtonKind.Ghost, enabled = page.index < page.pageCount - 1)
        NoteButton("Add page", { vm.addPage() }, kind = ButtonKind.Ghost)
    }
}

@Composable
private fun PageCanvas(vm: EditorViewModel, page: OpenPage, scale: Float) {
    val c = NoteTheme.colors
    val app = LocalContext.current.applicationContext as CloneApp
    val density = LocalDensity.current
    val renderer = remember { CanvasStrokeRenderer.create() }
    val toScreen = remember(scale) { Matrix().apply { setScale(scale, scale) } }
    val pointerToWorld = remember(scale) { Matrix().apply { setScale(1f / scale, 1f / scale) } }
    val brush = remember(vm.spec) { vm.spec.toBrush() }
    val widthPx = (page.widthPt * scale).toInt().coerceAtLeast(1)
    val heightPx = (page.heightPt * scale).toInt().coerceAtLeast(1)

    // the PDF page or picture behind the ink, rendered off the main thread
    val backdrop by produceState<Bitmap?>(null, page.pageId, widthPx) {
        value = page.background?.let { app.pageBitmaps.get(it, widthPx, heightPx) }
    }

    Box(Modifier.size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() }).background(c.canvas)) {
        Canvas(Modifier.fillMaxSize()) {
            backdrop?.let { drawImage(it.asImageBitmap(), dstSize = IntSize(size.width.toInt(), size.height.toInt())) }
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                nc.save(); nc.scale(scale, scale)                 // objects are in page points
                vm.objects.forEach { ObjectPainter.draw(nc, it, it.assetId?.let { id -> vm.objectBitmaps[id] }) }
                nc.restore()
                vm.strokes.forEach {
                    renderer.draw(canvas = canvas.nativeCanvas, stroke = it.stroke, strokeToScreenTransform = toScreen)
                }
            }
        }
        vm.selected?.let { o ->
            Canvas(Modifier.fillMaxSize()) {
                drawRect(c.accent, Offset(o.x * scale, o.y * scale), Size(o.w * scale, o.h * scale), style = DrawStroke(Layout.borderThick.toPx()))
            }
        }
        if (vm.tool == Tool.Pen || vm.tool == Tool.Highlighter) {
            if (vm.colorArgb != 0 && vm.highlightArgb != 0) {
                // live ("wet") ink, low latency
                InProgressStrokes(
                    defaultBrush = brush,
                    pointerEventToWorldTransform = pointerToWorld,
                    onStrokesFinished = { vm.onStrokesFinished(it) },
                )
            }
        } else if (vm.tool == Tool.Text) {
            Box(Modifier.fillMaxSize().pointerInput(scale) { detectTapGestures(onTap = { p -> vm.requestNewText(p.x / scale, p.y / scale) }) })
        } else if (vm.tool == Tool.Select) {
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(scale) { detectTapGestures(onTap = { p -> vm.selectAt(p.x / scale, p.y / scale) }) }
                    .pointerInput(scale) {
                        detectDragGestures(
                            onDragStart = { p -> vm.beginMove(p.x / scale, p.y / scale) },
                            onDrag = { change, drag -> change.consume(); vm.moveBy(drag.x / scale, drag.y / scale) },
                            onDragEnd = { vm.endMove() },
                            onDragCancel = { vm.endMove() },
                        )
                    },
            )
        } else if (vm.tool == Tool.Eraser) {
            val radius = BrushDefaults.ERASER_RADIUS_PT
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(scale) {
                        detectTapGestures(onTap = { p -> vm.eraseBegin(); vm.eraseAt(p.x / scale, p.y / scale, radius); vm.eraseEnd() })
                    }
                    .pointerInput(scale) {
                        detectDragGestures(
                            onDragStart = { p -> vm.eraseBegin(); vm.eraseAt(p.x / scale, p.y / scale, radius) },
                            onDrag = { change, _ -> vm.eraseAt(change.position.x / scale, change.position.y / scale, radius) },
                            onDragEnd = { vm.eraseEnd() },
                            onDragCancel = { vm.eraseEnd() },
                        )
                    },
            )
        }
    }
}

@Composable
private fun SelectionBar(vm: EditorViewModel) {
    val c = NoteTheme.colors
    val o = vm.selected ?: return
    if (vm.tool != Tool.Select) return
    Row(
        Modifier.fillMaxWidth().background(c.accentTint).horizontalScroll(rememberScrollState()).padding(horizontal = Space.s2),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s1),
    ) {
        if (o.kind == "text") {
            NoteButton("Edit text", { vm.requestEditText() }, kind = ButtonKind.Ghost)
            NoteButton("Smaller text", { vm.changeTextSize(false) }, kind = ButtonKind.Ghost)
            NoteButton("Larger text", { vm.changeTextSize(true) }, kind = ButtonKind.Ghost)
        } else {
            NoteButton("Smaller", { vm.scaleSelected(0.8f) }, kind = ButtonKind.Ghost)
            NoteButton("Larger", { vm.scaleSelected(1.25f) }, kind = ButtonKind.Ghost)
        }
        NoteButton("Delete", { vm.deleteSelected() }, kind = ButtonKind.Danger)
    }
}

@Composable
private fun TextBoxDialog(initial: String, isNew: Boolean, onConfirm: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (isNew) "New text box" else "Edit text") },
        text = { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 3, label = { Text("Text") }) },
        confirmButton = { TextButton({ onConfirm(text) }, enabled = text.isNotBlank()) { Text(if (isNew) "Add" else "Save") } },
        dismissButton = { TextButton(onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun ToolRow(vm: EditorViewModel) {
    val c = NoteTheme.colors
    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.insertPicture(uri) }
    val inkColors = listOf("Black" to c.inkBlack, "Red" to c.inkRed, "Blue" to c.inkBlue, "Green" to c.inkGreen)
    val highlightColors = listOf("Yellow" to c.hlYellow, "Green" to c.hlGreen, "Pink" to c.hlPink, "Blue" to c.hlBlue)
    Row(
        Modifier.fillMaxWidth().background(c.surfaceRaised).horizontalScroll(rememberScrollState()).padding(horizontal = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s1),
    ) {
        ToolButton("Pen", vm.tool == Tool.Pen, { vm.chooseTool(Tool.Pen) }) { Text("Pen", color = c.text, style = MaterialTheme.typography.labelMedium) }
        ToolButton("Highlighter", vm.tool == Tool.Highlighter, { vm.chooseTool(Tool.Highlighter) }) { Text("Mark", color = c.text, style = MaterialTheme.typography.labelMedium) }
        ToolButton("Eraser", vm.tool == Tool.Eraser, { vm.chooseTool(Tool.Eraser) }) { Text("Erase", color = c.text, style = MaterialTheme.typography.labelMedium) }
        ToolButton("Text box", vm.tool == Tool.Text, { vm.chooseTool(Tool.Text) }) { Text("Text", color = c.text, style = MaterialTheme.typography.labelMedium) }
        ToolButton("Select and move", vm.tool == Tool.Select, { vm.chooseTool(Tool.Select) }) { Text("Move", color = c.text, style = MaterialTheme.typography.labelMedium) }
        ToolButton("Insert picture", false, { pickPicture.launch(arrayOf("image/*")) }) { Text("Image", color = c.text, style = MaterialTheme.typography.labelMedium) }
        Spacer(Modifier.width(Space.s3))
        SegmentedControl(listOf("Fine", "Medium", "Bold"), vm.sizeIndex, { vm.sizeIndex = it }, Modifier.width(Layout.segmentedW))
        Spacer(Modifier.width(Space.s3))
        if (vm.tool == Tool.Highlighter) {
            highlightColors.forEach { (name, color) ->
                ColorSwatch(color, "$name highlighter", vm.highlightArgb == color.toArgb(), { vm.highlightArgb = color.toArgb() })
            }
        } else {
            inkColors.forEach { (name, color) ->
                ColorSwatch(color, name, vm.colorArgb == color.toArgb(), { vm.colorArgb = color.toArgb(); if (vm.tool != Tool.Text) vm.chooseTool(Tool.Pen) })
            }
        }
    }
}
