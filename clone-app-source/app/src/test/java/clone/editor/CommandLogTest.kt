package clone.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandLogTest {

    @Test fun emptyLogHasNothingToUndoOrRedo() {
        val log = CommandLog<String>()
        assertFalse(log.canUndo); assertFalse(log.canRedo)
        assertNull(log.popUndo()); assertNull(log.popRedo())
    }

    @Test fun undoReturnsNewestFirstAndRedoReplaysIt() {
        val log = CommandLog<String>()
        log.push("a"); log.push("b")
        assertEquals("b", log.popUndo())
        assertTrue(log.canRedo)
        assertEquals("b", log.popRedo())
        assertEquals("b", log.popUndo())
        assertEquals("a", log.popUndo())
        assertNull(log.popUndo())
    }

    @Test fun pushingAfterUndoDropsTheRedoBranch() {
        val log = CommandLog<String>()
        log.push("a"); log.push("b")
        log.popUndo()
        log.push("c")
        assertFalse(log.canRedo)
        assertEquals("c", log.popUndo())
        assertEquals("a", log.popUndo())
    }

    @Test fun historyIsBounded() {
        val log = CommandLog<Int>(limit = 3)
        (1..5).forEach { log.push(it) }
        assertEquals(5, log.popUndo()); assertEquals(4, log.popUndo()); assertEquals(3, log.popUndo())
        assertNull(log.popUndo())
    }

    @Test fun clearEmptiesBothStacks() {
        val log = CommandLog<String>()
        log.push("a"); log.popUndo(); log.clear()
        assertFalse(log.canUndo); assertFalse(log.canRedo)
    }
}
