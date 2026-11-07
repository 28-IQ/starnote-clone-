package clone.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import clone.design.*

/** Placeholder for every screen in the inventory that is not built yet. */
@Composable
fun StubScreen(id: String, title: String, onBack: () -> Unit) {
    val c = NoteTheme.colors
    Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding().padding(Layout.pageMargin)) {
        NoteButton("Back", onBack, kind = ButtonKind.Ghost)
        Spacer(Modifier.height(Space.s4))
        Text(title.ifEmpty { id }, color = c.text, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Space.s2))
        Text("$id is planned but not built yet. See replica/recon.md.", color = c.textMuted, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Debug-only list of all screens and their build status. */
@Composable
fun ScreenIndex(onBack: () -> Unit, onOpen: (ScreenInfo) -> Unit) {
    val c = NoteTheme.colors
    Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Layout.pageMargin), verticalAlignment = Alignment.CenterVertically) {
            NoteButton("Back", onBack, kind = ButtonKind.Ghost)
            Text("All screens", color = c.text, style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn {
            items(ScreenCatalog, key = { it.id + it.title }) { s ->
                PanelRow(
                    title = "${s.id}  ${s.title}",
                    onClick = { onOpen(s) },
                    trailing = { Text(s.status.name, color = c.textMuted, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
    }
}
