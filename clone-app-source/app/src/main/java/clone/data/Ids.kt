package clone.data

import java.security.SecureRandom

object Ids {
    private val rng = SecureRandom()

    /** UUIDv7: 48 bits of Unix milliseconds, then random bits. Sorts by creation time. */
    fun uuid7(now: Long = System.currentTimeMillis()): String {
        val b = ByteArray(16)
        rng.nextBytes(b)
        for (i in 0..5) b[i] = (now shr (8 * (5 - i))).toByte()
        b[6] = ((b[6].toInt() and 0x0F) or 0x70).toByte()
        b[8] = ((b[8].toInt() and 0x3F) or 0x80).toByte()
        val hex = b.joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
