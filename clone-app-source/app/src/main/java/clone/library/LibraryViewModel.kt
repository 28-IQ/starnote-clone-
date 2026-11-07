package clone.library

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import clone.app.CloneApp
import clone.data.FolderException
import clone.data.FolderRepo
import clone.data.NotebookSummary
import clone.importer.ImportException
import kotlinx.coroutines.launch

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val clone = app as CloneApp
    private val repo = clone.notebooks
    private val folderRepo = clone.folders

    /** The folder being shown; null is the top level. */
    var folderId by mutableStateOf<String?>(null); private set
    var path by mutableStateOf<List<Pair<String, String>>>(emptyList()); private set   // id to name, top down
    var folders by mutableStateOf<List<FolderRepo.Folder>>(emptyList()); private set
    var notebooks by mutableStateOf<List<NotebookSummary>>(emptyList()); private set
    var importing by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set

    init { refresh() }

    fun refresh() {
        // the folder may have been removed since it was opened
        if (folderId != null && folderRepo.path(folderId).isEmpty()) folderId = null
        path = folderRepo.path(folderId)
        folders = folderRepo.children(folderId)
        notebooks = repo.listIn(folderId)
    }

    fun openFolder(id: String?) { folderId = id; message = null; refresh() }

    /** One level up; false when already at the top. */
    fun goUp(): Boolean {
        if (folderId == null) return false
        openFolder(if (path.size >= 2) path[path.size - 2].first else null)
        return true
    }

    fun create(title: String): String {
        val id = repo.create(title.trim().ifEmpty { "Untitled note" }, folderId = folderId)
        refresh()
        return id
    }

    // ---- folders ----

    fun createFolder(name: String) = folderAction { folderRepo.create(folderId, name) }
    fun renameFolder(id: String, name: String) = folderAction { folderRepo.rename(id, name) }
    fun deleteFolder(id: String) = folderAction { folderRepo.delete(id) }

    /** Every folder with its full path, for the "move to" list. */
    fun allFolderPaths(): List<Pair<String, String>> {
        val all = folderRepo.all()
        val byId = all.associateBy { it.id }
        fun label(f: FolderRepo.Folder): String = if (f.parentId == null) f.name else (byId[f.parentId]?.let { label(it) + " / " } ?: "") + f.name
        return all.map { it.id to label(it) }.sortedBy { it.second.lowercase() }
    }

    fun moveNote(noteId: String, toFolderId: String?) { repo.moveToFolder(noteId, toFolderId); refresh() }
    fun deleteNote(noteId: String) { repo.delete(noteId); refresh() }

    private fun folderAction(block: () -> Unit) {
        message = null
        try { block() } catch (e: FolderException) { message = e.message }
        refresh()
    }

    // ---- imports go into the folder on screen ----

    fun importPdf(uri: Uri, onDone: (String) -> Unit) = runImport(onDone) { clone.importer.importPdf(uri, folderId) }

    fun importImages(uris: List<Uri>, onDone: (String) -> Unit) = runImport(onDone) { clone.importer.importImages(uris, folderId) }

    fun dismissMessage() { message = null }

    private fun runImport(onDone: (String) -> Unit, block: suspend () -> String) {
        if (importing) return
        importing = true; message = null
        viewModelScope.launch {
            try {
                val id = block()
                refresh()
                onDone(id)
            } catch (e: ImportException) {
                message = e.message
            } catch (e: Exception) {
                message = "The import failed"
            } finally {
                importing = false
            }
        }
    }
}
