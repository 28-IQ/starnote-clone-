package clone.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import clone.data.NotebookRepo
import clone.design.*

/** S03, defaults only: a title and one plain A4 portrait page. Paper styles, covers, tags come later. */
@Composable
fun CreateNoteScreen(onCancel: () -> Unit, onCreate: (title: String) -> Unit) {
    val c = NoteTheme.colors
    var title by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding().padding(Layout.pageMargin)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NoteButton("Cancel", onCancel, kind = ButtonKind.Ghost)
            Text("New note", Modifier.weight(1f), color = c.text, style = MaterialTheme.typography.titleLarge)
            NoteButton("Create", { onCreate(title) })
        }
        Spacer(Modifier.height(Space.s4))
        OutlinedTextField(
            value = title, onValueChange = { title = it }, label = { Text("Title") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.s4))
        Text("Plain page, A4 portrait. More paper styles are planned.", color = c.textMuted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Space.s3))
        Box(
            Modifier.height(Layout.noteCardH).aspectRatio((NotebookRepo.A4_WIDTH_PT / NotebookRepo.A4_HEIGHT_PT).toFloat())
                .background(c.canvas, Radius.sm).border(BorderStroke(Layout.borderThin, c.border), Radius.sm),
        )
    }
}
