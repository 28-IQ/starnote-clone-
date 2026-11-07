package clone.design

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** The design check: every primitive, every state, in all three themes. Open it from a debug-only route. */
@Composable
fun PrimitiveGallery() {
    var seg by remember { mutableStateOf(0) }
    var tool by remember { mutableStateOf(0) }
    var swatch by remember { mutableStateOf(1) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s4)) {
        Text("Buttons"); Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            NoteButton("Create", {}); NoteButton("Cancel", {}, kind = ButtonKind.Secondary)
            NoteButton("Skip", {}, kind = ButtonKind.Ghost); NoteButton("Delete", {}, kind = ButtonKind.Danger)
            NoteButton("Disabled", {}, enabled = false); NoteButton("Saving", {}, loading = true)
        }
        Text("Tools"); Row { listOf("Pen", "Marker", "Eraser", "Lasso").forEachIndexed { i, n ->
            ToolButton(n, tool == i, { tool = i }) { Text(n.take(1)) } } }
        Text("Ink colors"); Row { listOf(Color(0xFF1B1B1F), Color(0xFFC23B3B), Color(0xFF2F55B8), Color(0xFF1D7A5A), Color(0xFFD99A00)).forEachIndexed { i, col ->
            ColorSwatch(col, "Color ${i + 1}", swatch == i, { swatch = i }) } }
        Text("Tags"); Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) { TagChip("study"); TagChip("ideas"); TagChip("science") }
        Text("Segmented"); SegmentedControl(listOf("Timer", "Stopwatch"), seg, { seg = it }, Modifier.width(240.dp))
        Text("Banners"); InfoBanner("Exportable PDF can be edited in other apps."); InfoBanner("A scanned source stays non-editable.", BannerKind.Warning)
        InfoBanner("Export failed. Check free space.", BannerKind.Error)
        Text("Rows"); PanelRow("Chapter 1", selected = true); PanelRow("Chapter 2")
    }
}

@Preview(widthDp = 600, heightDp = 900) @Composable fun GalleryLight() = NoteTheme(NoteThemeMode.Light) { PrimitiveGallery() }
@Preview(widthDp = 600, heightDp = 900) @Composable fun GalleryDark() = NoteTheme(NoteThemeMode.Dark) { PrimitiveGallery() }
@Preview(widthDp = 600, heightDp = 900) @Composable fun GalleryEye() = NoteTheme(NoteThemeMode.EyeComfort) { PrimitiveGallery() }
