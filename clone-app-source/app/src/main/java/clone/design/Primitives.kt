package clone.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

enum class ButtonKind { Primary, Secondary, Ghost, Danger }
enum class ButtonSize(val height: Int) { Sm(32), Md(40), Lg(48) }

/** Touch area is never below Layout.touchMin, even for Sm. */
@Composable
fun NoteButton(
    label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary, size: ButtonSize = ButtonSize.Md,
    enabled: Boolean = true, loading: Boolean = false,
) {
    val c = NoteTheme.colors
    val (bg, fg, border) = when (kind) {
        ButtonKind.Primary -> Triple(c.accent, c.onAccent, Color.Transparent)
        ButtonKind.Secondary -> Triple(c.surfaceRaised, c.text, c.borderInput)
        ButtonKind.Ghost -> Triple(Color.Transparent, c.accent, Color.Transparent)
        ButtonKind.Danger -> Triple(Color.Transparent, c.danger, c.danger)
    }
    val alpha = if (enabled) 1f else 0.38f
    Box(
        modifier
            .heightIn(min = Layout.touchMin)
            .wrapContentSize(Alignment.Center),
    ) {
        Box(
            Modifier
                .height(size.height.dp)
                .background(bg.copy(alpha = bg.alpha * alpha), Radius.md)
                .border(BorderStroke(1.dp, border.copy(alpha = border.alpha * alpha)), Radius.md)
                .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
                .padding(horizontal = Space.s4),
            contentAlignment = Alignment.Center,
        ) {
            // the label stays while loading so screen readers keep the name
            Text(if (loading) "$label…" else label, color = fg.copy(alpha = alpha), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** One toolbar tool. Selected = accentTint background plus accent icon; never color alone: also a 2dp underline. */
@Composable
fun ToolButton(
    description: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val c = NoteTheme.colors
    Box(
        modifier
            .size(Layout.touchMin)
            .background(if (selected) c.accentTint else Color.Transparent, Radius.md)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        icon()
        if (selected) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).width(16.dp).height(2.dp).background(c.accent))
    }
}

/** Ink or tape color dot. Selected shows a ring in text color, so it works on every swatch color. */
@Composable
fun ColorSwatch(color: Color, name: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = NoteTheme.colors
    Box(modifier.size(Layout.touchMin).clickable(role = Role.RadioButton, onClick = onClick)
        .semantics { contentDescription = name }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(28.dp).background(color, Radius.pill).border(1.dp, c.border, Radius.pill))
        if (selected) Box(Modifier.size(36.dp).border(2.dp, c.text, Radius.pill))
    }
}

@Composable
fun TagChip(label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = NoteTheme.colors
    Row(
        modifier.heightIn(min = 32.dp).background(c.accentTint, Radius.pill)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = Space.s3), verticalAlignment = Alignment.CenterVertically,
    ) { Text("# $label", color = c.text, style = MaterialTheme.typography.labelMedium) }
}

@Composable
fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = NoteTheme.colors
    Row(modifier.background(c.surface, Radius.md).padding(2.dp).selectableGroup()) {
        options.forEachIndexed { i, o ->
            val sel = i == selectedIndex
            Box(
                Modifier.weight(1f).heightIn(min = 36.dp)
                    .background(if (sel) c.surfaceRaised else Color.Transparent, Radius.sm)
                    .selectable(selected = sel, role = Role.Tab, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) { Text(o, color = if (sel) c.text else c.textMuted, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

enum class BannerKind { Info, Warning, Error }

@Composable
fun InfoBanner(text: String, kind: BannerKind = BannerKind.Info, modifier: Modifier = Modifier) {
    val c = NoteTheme.colors
    val fg = when (kind) { BannerKind.Info -> c.text; BannerKind.Warning -> c.warning; BannerKind.Error -> c.danger }
    Row(modifier.fillMaxWidth().background(c.surface, Radius.md).border(1.dp, c.border, Radius.md).padding(Space.s3)) {
        // the kind is also in the text prefix so color is not the only signal
        val prefix = when (kind) { BannerKind.Info -> ""; BannerKind.Warning -> "Warning: "; BannerKind.Error -> "Error: " }
        Text(prefix + text, color = fg, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Row used by outline, layers, timers, pencil boxes. Drag handle and overflow are slots. */
@Composable
fun PanelRow(
    title: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit = {},
    leading: @Composable (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null,
) {
    val c = NoteTheme.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = Layout.touchMin)
            .background(if (selected) c.accentTint else Color.Transparent)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.s4),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        leading?.invoke()
        Text(title, Modifier.weight(1f), color = c.text, style = MaterialTheme.typography.bodyLarge)
        trailing?.invoke()
    }
}
