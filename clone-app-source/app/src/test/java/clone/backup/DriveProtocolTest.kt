package clone.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveProtocolTest {
    private val mib = 1024L * 1024L

    @Test fun chunksAreEightMiBAndTheLastOneIsShort() {
        val total = 20 * mib
        assertEquals(8 * mib - 1, DriveProtocol.chunkEnd(0, total))
        assertEquals(16 * mib - 1, DriveProtocol.chunkEnd(8 * mib, total))
        assertEquals(total - 1, DriveProtocol.chunkEnd(16 * mib, total))
    }

    @Test fun smallFileIsOneChunk() {
        assertEquals(999, DriveProtocol.chunkEnd(0, 1000))
    }

    @Test fun chunkSizeMustBeAMultipleOf256KiB() {
        assertThrows(IllegalArgumentException::class.java) { DriveProtocol.chunkEnd(0, mib, chunk = 1000) }
    }

    @Test fun rangeHeadersAreFormattedAsDriveExpects() {
        assertEquals("bytes 0-8388607/20971520", DriveProtocol.contentRange(0, 8 * mib - 1, 20 * mib))
        assertEquals("bytes */20971520", DriveProtocol.statusRange(20 * mib))
    }

    @Test fun nextOffsetComesFromTheRangeTheServerStored() {
        assertEquals(0, DriveProtocol.nextOffsetFromRange(null))
        assertEquals(0, DriveProtocol.nextOffsetFromRange(""))
        assertEquals(8 * mib, DriveProtocol.nextOffsetFromRange("bytes=0-8388607"))
    }

    @Test fun garbledRangeIsARetryableError() {
        val e = assertThrows(BackupException::class.java) { DriveProtocol.nextOffsetFromRange("bytes=oops") }
        assertTrue(e.retryable)
    }

    @Test fun findQueryAcceptsOnlyAHexHash() {
        val ok = "a".repeat(64)
        assertEquals("appProperties has { key='sha256' and value='$ok' }", DriveProtocol.findQuery(ok))
        assertThrows(IllegalArgumentException::class.java) { DriveProtocol.findQuery("x' or name contains 'a") }
        assertThrows(IllegalArgumentException::class.java) { DriveProtocol.findQuery("A".repeat(64)) }
    }

    @Test fun retryRules() {
        assertTrue(DriveProtocol.isRetryable(503, null))
        assertTrue(DriveProtocol.isRetryable(429, null))
        assertTrue(DriveProtocol.isRetryable(401, null))
        assertTrue(DriveProtocol.isRetryable(403, "rateLimitExceeded"))
        assertFalse(DriveProtocol.isRetryable(403, "storageQuotaExceeded"))
        assertFalse(DriveProtocol.isRetryable(404, null))
    }

    @Test fun messagesNeverEchoServerText() {
        assertEquals("Your Google Drive is full.", DriveProtocol.userMessage(403, "storageQuotaExceeded"))
        assertEquals("Google Drive returned an error (418).", DriveProtocol.userMessage(418, "secret details"))
    }

    @Test fun backoffDoublesAndCaps() {
        assertEquals(2_000, DriveProtocol.backoffMs(1))
        assertEquals(4_000, DriveProtocol.backoffMs(2))
        assertEquals(60_000, DriveProtocol.backoffMs(6))
        assertEquals(60_000, DriveProtocol.backoffMs(40))
    }
}
