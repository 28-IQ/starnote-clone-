package clone.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupArchiveTest {

    @Test fun entryNamesThatCouldEscapeTheTargetFolderAreRejected() {
        assertTrue(BackupArchive.isSafeName("notes.db"))
        assertTrue(BackupArchive.isSafeName("assets/fonts/a.ttf"))
        assertFalse(BackupArchive.isSafeName("../notes.db"))
        assertFalse(BackupArchive.isSafeName("assets/../../x"))
        assertFalse(BackupArchive.isSafeName("/etc/passwd"))
        assertFalse(BackupArchive.isSafeName("a\\b"))
        assertFalse(BackupArchive.isSafeName(""))
    }

    @Test fun sha256OfKnownInput() {
        val empty = BackupArchive.sha256Of("".byteInputStream())
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", empty)
        val abc = BackupArchive.sha256Of("abc".byteInputStream())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", abc)
    }

    @Test fun contentHashIgnoresOrderButNotContent() {
        val a = BackupArchive.Manifest.Item("notes.db", 10, "aa")
        val b = BackupArchive.Manifest.Item("assets/x", 5, "bb")
        assertEquals(BackupArchive.contentHash(listOf(a, b)), BackupArchive.contentHash(listOf(b, a)))
        val changed = BackupArchive.Manifest.Item("notes.db", 10, "cc")
        assertFalse(BackupArchive.contentHash(listOf(a, b)) == BackupArchive.contentHash(listOf(changed, b)))
    }
}
