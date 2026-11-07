package clone.ink

/** Turns a note title into a safe file name. Letters and digits from any language are kept. */
object FileNames {
    private val unsafe = Regex("[^\\p{L}\\p{N}._ -]")

    fun safe(title: String, fallback: String = "note", maxLength: Int = 60): String {
        val cleaned = title.trim().replace(unsafe, "_").take(maxLength).trim('.', ' ', '_')
        return cleaned.ifEmpty { fallback }
    }
}
