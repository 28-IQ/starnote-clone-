package clone.backup

/** Pure helpers for Google Drive's resumable upload and file search. No Android or network code, so they can be unit-tested. */
object DriveProtocol {
    /** Drive wants every chunk except the last to be a multiple of 256 KiB. */
    const val CHUNK_UNIT = 256 * 1024
    const val DEFAULT_CHUNK = 32 * CHUNK_UNIT          // 8 MiB
    const val MAX_CHUNK_ATTEMPTS = 6
    const val KEEP_REMOTE_BACKUPS = 5

    private val HEX64 = Regex("^[0-9a-f]{64}$")

    /** Index of the last byte of the chunk that starts at [start]. */
    fun chunkEnd(start: Long, total: Long, chunk: Int = DEFAULT_CHUNK): Long {
        require(chunk % CHUNK_UNIT == 0) { "chunk must be a multiple of 256 KiB" }
        require(start in 0 until total) { "start out of range" }
        return minOf(start + chunk, total) - 1
    }

    fun contentRange(start: Long, endInclusive: Long, total: Long) = "bytes $start-$endInclusive/$total"
    fun statusRange(total: Long) = "bytes */$total"

    /** "bytes=0-42" from a 308 reply means 43 bytes are stored, so the next byte to send is 43. No header means nothing is stored. */
    fun nextOffsetFromRange(rangeHeader: String?): Long {
        if (rangeHeader.isNullOrBlank()) return 0
        val last = rangeHeader.substringAfter('-', "").trim().toLongOrNull()
            ?: throw BackupException("Unexpected reply from Google Drive", retryable = true)
        return last + 1
    }

    /** Drive search for the backup holding this content. The hash is checked so it cannot break out of the query. */
    fun findQuery(contentHash: String): String {
        require(HEX64.matches(contentHash)) { "not a sha256 hex string" }
        return "appProperties has { key='sha256' and value='$contentHash' }"
    }

    fun isRetryable(code: Int, reason: String?): Boolean = when {
        code == 401 || code == 408 || code == 429 || code in 500..599 -> true
        code == 403 && reason in setOf("rateLimitExceeded", "userRateLimitExceeded", "backendError") -> true
        else -> false
    }

    /** Plain message for the user. Never includes file names or response bodies. */
    fun userMessage(code: Int, reason: String?): String = when {
        code == 401 -> "Google sign-in expired. Connect Google Drive again."
        code == 403 && reason == "storageQuotaExceeded" -> "Your Google Drive is full."
        code == 403 && reason in setOf("rateLimitExceeded", "userRateLimitExceeded") -> "Google Drive is busy. Trying again later."
        code == 403 -> "Google Drive refused access. Connect Google Drive again."
        code == 404 -> "The backup was not found on Google Drive."
        code == 429 -> "Google Drive is busy. Trying again later."
        code in 500..599 -> "Google Drive had a problem. Trying again later."
        else -> "Google Drive returned an error ($code)."
    }

    /** Wait before retry number [attempt] (1-based): 2, 4, 8 ... seconds, capped at 60. */
    fun backoffMs(attempt: Int): Long = minOf(60_000L, 1_000L shl attempt.coerceIn(1, 6))
}
