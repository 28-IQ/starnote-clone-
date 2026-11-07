package clone.editor

/** Bounded undo/redo history. Pure Kotlin so it can be unit-tested without Android. */
class CommandLog<C : Any>(private val limit: Int = 200) {
    private val undoStack = ArrayDeque<C>()
    private val redoStack = ArrayDeque<C>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun push(command: C) {
        undoStack.addLast(command)
        redoStack.clear()
        while (undoStack.size > limit) undoStack.removeFirst()
    }

    /** The command to reverse, or null. It moves onto the redo stack. */
    fun popUndo(): C? = undoStack.removeLastOrNull()?.also { redoStack.addLast(it) }

    /** The command to apply again, or null. It moves back onto the undo stack. */
    fun popRedo(): C? = redoStack.removeLastOrNull()?.also { undoStack.addLast(it) }

    fun clear() { undoStack.clear(); redoStack.clear() }
}
