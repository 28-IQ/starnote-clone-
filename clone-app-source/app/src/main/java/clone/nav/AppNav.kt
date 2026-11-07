package clone.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import clone.backup.BackupScreen
import clone.billing.UnlockScreen
import clone.editor.EditorScreen
import clone.editor.PageOverviewScreen
import clone.library.CreateNoteScreen
import clone.library.LibraryScreen
import clone.library.LibraryViewModel

object Routes {
    const val LIBRARY = "library"
    const val CREATE = "create"
    const val EDITOR = "editor/{id}"
    const val PAGES = "pages/{id}"
    const val STUB = "stub/{id}/{title}"
    const val SCREENS = "screens"
    const val BACKUP = "backup"
    const val UNLOCK = "unlock"
    fun editor(id: String) = "editor/$id"
    fun pages(id: String) = "pages/$id"
    fun stub(id: String, title: String) = "stub/$id/${Uri.encode(title)}"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onOpen = { nav.navigate(Routes.editor(it)) },
                onNewNote = { nav.navigate(Routes.CREATE) },
                onPlanned = { id, title -> nav.navigate(Routes.stub(id, title)) },
                onAllScreens = { nav.navigate(Routes.SCREENS) },
                onBackup = { nav.navigate(Routes.BACKUP) },
                onUnlock = { nav.navigate(Routes.UNLOCK) },
            )
        }
        composable(Routes.CREATE) {
            val library: LibraryViewModel = viewModel(nav.getBackStackEntry(Routes.LIBRARY))
            CreateNoteScreen(
                onCancel = { nav.popBackStack() },
                onCreate = { title ->
                    val id = library.create(title)
                    nav.navigate(Routes.editor(id)) { popUpTo(Routes.LIBRARY) }
                },
            )
        }
        composable(Routes.EDITOR, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val picked by entry.savedStateHandle.getStateFlow("page", -1).collectAsState()
            EditorScreen(
                notebookId = id,
                pickedPage = picked,
                onPickedHandled = { entry.savedStateHandle["page"] = -1 },
                onBack = { nav.popBackStack() },
                onOverview = { nav.navigate(Routes.pages(id)) },
            )
        }
        composable(Routes.PAGES, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            PageOverviewScreen(
                notebookId = entry.arguments?.getString("id").orEmpty(),
                onPick = { index -> nav.previousBackStackEntry?.savedStateHandle?.set("page", index); nav.popBackStack() },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.STUB) { entry ->
            StubScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                title = Uri.decode(entry.arguments?.getString("title").orEmpty()),
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.BACKUP) { BackupScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.UNLOCK) { UnlockScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SCREENS) {
            ScreenIndex(onBack = { nav.popBackStack() }, onOpen = {
                when (it.id) {
                    "S32" -> nav.navigate(Routes.BACKUP)
                    "S38" -> nav.navigate(Routes.UNLOCK)
                    else -> nav.navigate(Routes.stub(it.id, it.title))
                }
            })
        }
    }
}
