package clone.editor

import clone.editor.ObjectGeometry.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectGeometryTest {
    @Test fun pointInsideAndJustOutsideTheSlop() {
        val b = Box(100f, 100f, 50f, 20f)
        assertTrue(ObjectGeometry.contains(b, 120f, 110f))
        assertTrue(ObjectGeometry.contains(b, 96f, 110f))      // inside the 6 pt slop
        assertFalse(ObjectGeometry.contains(b, 90f, 110f))
    }

    @Test fun topmostIsTheLastInDrawingOrder() {
        val boxes = listOf(Box(0f, 0f, 100f, 100f), Box(50f, 50f, 100f, 100f))
        assertEquals(1, ObjectGeometry.topmostAt(boxes, 75f, 75f))
        assertEquals(0, ObjectGeometry.topmostAt(boxes, 10f, 10f))
        assertEquals(-1, ObjectGeometry.topmostAt(boxes, 400f, 400f))
    }

    @Test fun boxesAreKeptInsideThePage() {
        assertEquals(0f to 0f, ObjectGeometry.clampToPage(-50f, -20f, 100f, 40f, 595f, 842f))
        assertEquals(495f to 802f, ObjectGeometry.clampToPage(900f, 900f, 100f, 40f, 595f, 842f))
        assertEquals(0f to 30f, ObjectGeometry.clampToPage(30f, 30f, 700f, 40f, 595f, 842f))   // wider than the page: pinned left, height still fits
    }

    @Test fun scalingKeepsTheCentreAndTheAspect() {
        val b = ObjectGeometry.scaleAbout(Box(100f, 100f, 100f, 50f), 2f, 595f, 842f)
        assertEquals(200f, b.w, 0.001f); assertEquals(100f, b.h, 0.001f)
        assertEquals(50f, b.x, 0.001f); assertEquals(75f, b.y, 0.001f)
    }

    @Test fun scalingStopsAtTheMinimumAndAtThePageWidth() {
        assertEquals(24f, ObjectGeometry.scaleAbout(Box(10f, 10f, 30f, 30f), 0.1f, 595f, 842f).w, 0.001f)
        assertEquals(595f, ObjectGeometry.scaleAbout(Box(10f, 10f, 400f, 100f), 5f, 595f, 842f).w, 0.001f)
    }

    @Test fun newPictureIsAtMost300WideAndKeepsItsShape() {
        val (w, h) = ObjectGeometry.defaultPictureSize(2000, 1000, 595f)
        assertEquals(300f, w, 0.001f); assertEquals(150f, h, 0.001f)
        assertEquals(60f, ObjectGeometry.defaultPictureSize(100, 100, 100f).first, 0.001f)
    }
}
