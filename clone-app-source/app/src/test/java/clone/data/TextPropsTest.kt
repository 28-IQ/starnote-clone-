package clone.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TextPropsTest {
    @Test fun sizeStepsUpAndDownTheList() {
        assertEquals(18f, TextProps.stepSize(14f, up = true), 0.001f)
        assertEquals(12f, TextProps.stepSize(14f, up = false), 0.001f)
    }

    @Test fun sizeStaysAtTheEnds() {
        assertEquals(48f, TextProps.stepSize(48f, up = true), 0.001f)
        assertEquals(10f, TextProps.stepSize(10f, up = false), 0.001f)
    }

    @Test fun anOffListSizeSnapsToTheNextOneUp() {
        assertEquals(18f, TextProps.stepSize(15f, up = true), 0.001f)
        assertEquals(14f, TextProps.stepSize(15f, up = false), 0.001f)
    }

    @Test fun jsonRoundTripKeepsEmojiCyrillicAndQuotes() {
        val p = TextProps("Конспект \"лекции\" 😀\nвторая строка", 18f, -16777216)
        assertEquals(p, TextProps.fromJson(p.toJson()))
    }
}
