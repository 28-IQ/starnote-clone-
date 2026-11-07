package clone.editor

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import clone.app.CloneApp
import clone.design.*

/** S29: every page of the note as a numbered tile. Tiles of imported PDFs and pictures show the page behind the ink; ink is not drawn yet. */
@Composable
fun PageOverviewScreen(notebookId: String, onPick: (Int) -> Unit, onBack: () -> Unit) {
    val c = NoteTheme.colors
    val app = LocalContext.current.applicationContext as CloneApp
    val count = remember(notebookId) { app.notebooks.openPage(notebookId, 0)?.pageCount ?: 0 }

    Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding().padding(Layout.pageMargin)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NoteButton("Back", onBack, kind = ButtonKind.Ghost)
            Text("Pages", color = c.text, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(Space.s3))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(Layout.pageThumbW),
            horizontalArrangement = Arrangement.spacedBy(Space.s3),
            verticalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            items((0 until count).toList(), key = { it }) { index -> PageTile(notebookId, index) { onPick(index) } }
        }
    }
}

@Composable
private fun PageTile(notebookId: String, index: Int, onClick: () -> Unit) {
    val c = NoteTheme.colors
    val app = LocalContext.current.applicationContext as CloneApp
    val density = LocalDensity.current
    val page = remember(notebookId, index) { app.notebooks.openPage(notebookId, index) }
    val widthPx = with(density) { Layout.pageThumbW.toPx() }.toInt()
    val heightPx = page?.let { (widthPx * it.heightPt / it.widthPt).toInt().coerceAtLeast(1) } ?: widthPx
    val thumb by produceState<Bitmap?>(null, notebookId, index) {
        value = page?.background?.let { app.pageBitmaps.get(it, widthPx, heightPx) }
    }
    Column(
        Modifier.clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "Page ${index + 1}" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.width(Layout.pageThumbW).height(with(density) { heightPx.toDp() })
                .background(c.canvas, Radius.sm).border(BorderStroke(Layout.borderThin, c.border), Radius.sm),
            contentAlignment = Alignment.Center,
        ) {
            thumb?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
        }
        Text("${index + 1}", color = c.textMuted, style = MaterialTheme.typography.labelMedium)
    }
}
