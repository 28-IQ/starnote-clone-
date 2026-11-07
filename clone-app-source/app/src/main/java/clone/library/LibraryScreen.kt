package clone.library

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import clone.app.BuildConfig
import clone.data.FolderRepo
import clone.data.NotebookSummary
import clone.design.*
import java.util.Date

private sealed interface Dialog {
    data object NewFolder : Dialog
    class RenameFolder(val folder: FolderRepo.Folder) : Dialog
    class DeleteFolder(val folder: FolderRepo.Folder) : Dialog
    class MoveNote(val note: NotebookSummary) : Dialog
    class DeleteNote(val note: NotebookSummary) : Dialog
}

/** S01 library: folders, notes, the new-note menu (S02). Sort, tags and search are not built yet. */
@Composable
fun LibraryScreen(
    onOpen: (String) -> Unit,
    onNewNote: () -> Unit,
    onPlanned: (id: String, title: String) -> Unit,
    onAllScreens: () -> Unit,
    onBackup: () -> Unit,
    onUnlock: () -> Unit,
    vm: LibraryViewModel = viewModel(),
) {
    val c = NoteTheme.colors
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    LifecycleResumeEffect(Unit) { vm.refresh(); onPauseOrDispose { } }
    BackHandler(enabled = vm.folderId != null) { vm.goUp() }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importPdf(uri, onOpen) }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) vm.importImages(uris, onOpen) }

    Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding().padding(Layout.pageMargin)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            Text("My notes", color = c.text, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(Space.s4))
            NoteButton("Search", { onPlanned("S28", "Search") }, kind = ButtonKind.Ghost)
            NoteButton("New folder", { dialog = Dialog.NewFolder }, kind = ButtonKind.Ghost)
            NoteButton("Backup", onBackup, kind = ButtonKind.Ghost)
            NoteButton("Unlock", onUnlock, kind = ButtonKind.Ghost)
            if (BuildConfig.DEBUG) NoteButton("All screens", onAllScreens, kind = ButtonKind.Ghost)
        }
        Breadcrumb(vm)
        Spacer(Modifier.height(Space.s2))
        if (vm.importing) Text("Importing…", color = c.textMuted, style = MaterialTheme.typography.bodyLarge)
        vm.message?.let { InfoBanner(it, BannerKind.Error) }
        if (vm.notebooks.isEmpty() && vm.folders.isEmpty()) {
            Text("Nothing here yet. Use the + tile to start a note, or New folder to organize.", color = c.textMuted, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(Space.s3))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(Layout.noteCardW),
            horizontalArrangement = Arrangement.spacedBy(Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            item(key = "new") { NewNoteTile(onNewNote, onPlanned, onImportPdf = { pickPdf.launch(arrayOf("application/pdf")) }, onImportImages = { pickImages.launch(arrayOf("image/*")) }) }
            items(vm.folders, key = { "f" + it.id }) { f -> FolderCard(f, onOpen = { vm.openFolder(f.id) }, onRename = { dialog = Dialog.RenameFolder(f) }, onDelete = { dialog = Dialog.DeleteFolder(f) }) }
            items(vm.notebooks, key = { "n" + it.id }) { nb -> NoteCard(nb, onClick = { onOpen(nb.id) }, onMove = { dialog = Dialog.MoveNote(nb) }, onDelete = { dialog = Dialog.DeleteNote(nb) }) }
        }
    }

    when (val d = dialog) {
        null -> Unit
        Dialog.NewFolder -> NameDialog("New folder", "", "Create", { vm.createFolder(it); dialog = null }, { dialog = null })
        is Dialog.RenameFolder -> NameDialog("Rename folder", d.folder.name, "Save", { vm.renameFolder(d.folder.id, it); dialog = null }, { dialog = null })
        is Dialog.DeleteFolder -> ConfirmDialog("Delete the folder “${d.folder.name}”?", "Only an empty folder can be deleted.", "Delete", { vm.deleteFolder(d.folder.id); dialog = null }, { dialog = null })
        is Dialog.DeleteNote -> ConfirmDialog("Delete “${d.note.title}”?", "The note and everything written in it will be removed. This cannot be undone.", "Delete", { vm.deleteNote(d.note.id); dialog = null }, { dialog = null })
        is Dialog.MoveNote -> MoveDialog(vm.allFolderPaths(), onPick = { vm.moveNote(d.note.id, it); dialog = null }, onCancel = { dialog = null })
    }
}

@Composable
private fun Breadcrumb(vm: LibraryViewModel) {
    val c = NoteTheme.colors
    if (vm.path.isEmpty()) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        NoteButton("My notes", { vm.openFolder(null) }, kind = ButtonKind.Ghost, size = ButtonSize.Sm)
        vm.path.forEach { (id, name) ->
            Text("/", color = c.textMuted, style = MaterialTheme.typography.bodyMedium)
            NoteButton(name, { vm.openFolder(id) }, kind = ButtonKind.Ghost, size = ButtonSize.Sm)
        }
    }
}

@Composable
private fun NewNoteTile(onNewNote: () -> Unit, onPlanned: (String, String) -> Unit, onImportPdf: () -> Unit, onImportImages: () -> Unit) {
    val c = NoteTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.fillMaxWidth().height(Layout.noteCardH)
                .background(c.accentTint, Radius.lg)
                .border(BorderStroke(Layout.borderThick, c.accent), Radius.lg)
                .clickable(role = Role.Button) { open = true },
            contentAlignment = Alignment.Center,
        ) { Text("+ New", color = c.accent, style = MaterialTheme.typography.titleLarge) }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable fun item(label: String, action: () -> Unit) =
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; action() })
            item("New note") { onNewNote() }
            item("New infinite canvas") { onPlanned("S06", "Infinite canvas") }
            item("Import a PDF") { onImportPdf() }
            item("Import pictures") { onImportImages() }
            item("Take a photo") { onPlanned("S25", "Take a photo") }
            item("Quick note") { onPlanned("S02", "Quick note") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCard(f: FolderRepo.Folder, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val c = NoteTheme.colors
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier.fillMaxWidth().height(Layout.noteCardH)
                .background(c.surface, Radius.lg)
                .border(BorderStroke(Layout.borderThin, c.border), Radius.lg)
                .combinedClickable(role = Role.Button, onClick = onOpen, onLongClick = { menu = true })
                .padding(Space.s3),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text("Folder", color = c.textMuted, style = MaterialTheme.typography.labelMedium)
            Text(f.name, color = c.text, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${f.noteCount} notes, ${f.folderCount} folders", color = c.textMuted, style = MaterialTheme.typography.labelMedium)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(nb: NotebookSummary, onClick: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit) {
    val c = NoteTheme.colors
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val date = DateFormat.getMediumDateFormat(ctx).format(Date(nb.updatedAt))
    Box {
        Column(
            Modifier.fillMaxWidth().height(Layout.noteCardH)
                .background(c.surfaceRaised, Radius.lg)
                .border(BorderStroke(Layout.borderThin, c.border), Radius.lg)
                .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = { menu = true })
                .padding(Space.s3),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.fillMaxWidth().weight(1f).background(c.canvas, Radius.sm).border(BorderStroke(Layout.borderThin, c.border), Radius.sm))
            Spacer(Modifier.height(Space.s2))
            Text(nb.title, color = c.text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(date, color = c.textMuted, style = MaterialTheme.typography.labelMedium)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Move to…") }, onClick = { menu = false; onMove() })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, action: String, onConfirm: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onCancel, title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton({ onConfirm(text) }) { Text(action) } },
        dismissButton = { TextButton(onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmDialog(title: String, text: String, action: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onConfirm) { Text(action) } },
        dismissButton = { TextButton(onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun MoveDialog(folders: List<Pair<String, String>>, onPick: (String?) -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel, title = { Text("Move to") },
        text = {
            Column(Modifier.verticalScrollIfLong()) {
                PanelRow("My notes (top level)", onClick = { onPick(null) })
                folders.forEach { (id, label) -> PanelRow(label, onClick = { onPick(id) }) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun Modifier.verticalScrollIfLong(): Modifier = this.then(Modifier.verticalScroll(rememberScrollState()))
