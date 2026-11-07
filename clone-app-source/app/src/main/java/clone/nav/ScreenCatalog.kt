package clone.nav

enum class BuildStatus { Partial, Stub }

data class ScreenInfo(val id: String, val title: String, val status: BuildStatus)

/** Every screen in replica/recon.md. Generated from its screen table; edit the status here as screens are built. */
val ScreenCatalog = listOf(
    ScreenInfo("S01", "Library: All notes", BuildStatus.Partial),
    ScreenInfo("S02", "New-note menu", BuildStatus.Partial),
    ScreenInfo("S03", "Create new note dialog", BuildStatus.Partial),
    ScreenInfo("S04", "Custom paper import", BuildStatus.Stub),
    ScreenInfo("S05", "Note editor, paged", BuildStatus.Partial),
    ScreenInfo("S06", "Note editor, infinite", BuildStatus.Stub),
    ScreenInfo("S07", "Brush settings sheet", BuildStatus.Partial),
    ScreenInfo("S08", "Color picker", BuildStatus.Stub),
    ScreenInfo("S09", "Pencil box", BuildStatus.Stub),
    ScreenInfo("S10", "Text box + format bar", BuildStatus.Partial),
    ScreenInfo("S11", "Font picker", BuildStatus.Stub),
    ScreenInfo("S12", "Outline panel", BuildStatus.Stub),
    ScreenInfo("S13", "Page context menu", BuildStatus.Stub),
    ScreenInfo("S14", "Excerpt pen options", BuildStatus.Stub),
    ScreenInfo("S15", "Excerpt panel", BuildStatus.Stub),
    ScreenInfo("S16", "Excerpt jump menus", BuildStatus.Stub),
    ScreenInfo("S17", "Layers panel", BuildStatus.Stub),
    ScreenInfo("S18", "Dual-note view", BuildStatus.Stub),
    ScreenInfo("S19", "Page link in note", BuildStatus.Stub),
    ScreenInfo("S20", "Tag sidebar", BuildStatus.Stub),
    ScreenInfo("S21", "Page tag in note", BuildStatus.Stub),
    ScreenInfo("S22", "Other settings panel", BuildStatus.Stub),
    ScreenInfo("S23", "Page margin adjustment", BuildStatus.Stub),
    ScreenInfo("S24", "Export sheet", BuildStatus.Stub),
    ScreenInfo("S25", "Import flow", BuildStatus.Partial),
    ScreenInfo("S26", "Timer", BuildStatus.Stub),
    ScreenInfo("S27", "Top-right overflow menu", BuildStatus.Stub),
    ScreenInfo("S28", "Search", BuildStatus.Stub),
    ScreenInfo("S29", "Page overview", BuildStatus.Partial),
    ScreenInfo("S30", "Audio recorder + playback", BuildStatus.Stub),
    ScreenInfo("S31", "Settings", BuildStatus.Stub),
    ScreenInfo("S32", "Backup", BuildStatus.Partial),
    ScreenInfo("S33", "Asset center", BuildStatus.Stub),
    ScreenInfo("S34", "Sign in", BuildStatus.Stub),
    ScreenInfo("S35", "Stickers & images", BuildStatus.Partial),
    ScreenInfo("S36", "Drawing aids", BuildStatus.Stub),
    ScreenInfo("S37", "Presentation mode", BuildStatus.Stub),
    ScreenInfo("S38", "Paywall / Pro", BuildStatus.Partial),
)
