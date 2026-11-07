package clone.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FileNamesTest {
    @Test fun lettersFromAnyLanguageAreKept() {
        assertEquals("Конспект лекции", FileNames.safe("Конспект лекции"))
        assertEquals("Café notes", FileNames.safe("Café notes"))
        assertEquals("笔记", FileNames.safe("笔记"))
    }

    @Test fun symbolOnlyTitleGetsTheFallback() {
        assertEquals("note", FileNames.safe("😀😀"))
        assertEquals("note", FileNames.safe("   "))
        assertEquals("untitled", FileNames.safe("", fallback = "untitled"))
    }

    @Test fun pathCharactersAndLeadingDotsAreRemoved() {
        val name = FileNames.safe("../a/b:c*d")
        assertFalse(name.contains('/')); assertFalse(name.contains(':')); assertFalse(name.startsWith("."))
    }

    @Test fun longTitlesAreCut() {
        assertEquals(60, FileNames.safe("a".repeat(500)).length)
    }
}
