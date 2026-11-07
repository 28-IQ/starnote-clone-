package clone.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

enum class NoteThemeMode { Light, Dark, EyeComfort, System }

val LocalNoteColors = staticCompositionLocalOf { LightColors }

object NoteTheme {
    val colors: NoteColors
        @Composable get() = LocalNoteColors.current
}

@Composable
fun NoteTheme(mode: NoteThemeMode = NoteThemeMode.System, content: @Composable () -> Unit) {
    val dark = mode == NoteThemeMode.Dark || (mode == NoteThemeMode.System && isSystemInDarkTheme())
    val c = when {
        mode == NoteThemeMode.EyeComfort -> EyeColors
        dark -> DarkColors
        else -> LightColors
    }
    val scheme = if (dark) {
        darkColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.bg, onBackground = c.text,
            surface = c.surface, onSurface = c.text, error = c.danger, outline = c.borderInput, outlineVariant = c.border)
    } else {
        lightColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.bg, onBackground = c.text,
            surface = c.surface, onSurface = c.text, error = c.danger, outline = c.borderInput, outlineVariant = c.border)
    }
    CompositionLocalProvider(LocalNoteColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = NoteType, content = content)
    }
}
