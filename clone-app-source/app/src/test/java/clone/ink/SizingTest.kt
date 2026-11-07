package clone.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SizingTest {
    @Test fun fitScaleUsesTheTighterSide() {
        assertEquals(2.0f, Sizing.fitScale(1190f, 2000f, 595f, 842f), 0.0001f)
        assertEquals(1.0f, Sizing.fitScale(2000f, 842f, 595f, 842f), 0.0001f)
    }

    @Test fun fitScaleRejectsAnEmptyPage() {
        assertThrows(IllegalArgumentException::class.java) { Sizing.fitScale(100f, 100f, 0f, 842f) }
    }

    @Test fun aPictureBecomesA595PointWidePage() {
        val (w, h) = Sizing.imagePageSize(1000, 500)
        assertEquals(595f, w, 0.001f); assertEquals(297.5f, h, 0.001f)
    }

    @Test fun veryTallPicturesAreCapped() {
        assertEquals(4000f, Sizing.imagePageSize(100, 100_000).second, 0.001f)
    }

    @Test fun decodeStepNeverGoesBelowTheTarget() {
        assertEquals(4, Sizing.sampleSize(4000, 3000, 1000, 750))
        assertEquals(1, Sizing.sampleSize(1000, 750, 1000, 750))
        assertEquals(1, Sizing.sampleSize(500, 500, 1000, 1000))
    }

    @Test fun pageIndexIsKeptInRange() {
        assertEquals(0, Sizing.clampPage(-3, 5)); assertEquals(4, Sizing.clampPage(9, 5)); assertEquals(2, Sizing.clampPage(2, 5))
        assertEquals(0, Sizing.clampPage(3, 0))
    }
}
