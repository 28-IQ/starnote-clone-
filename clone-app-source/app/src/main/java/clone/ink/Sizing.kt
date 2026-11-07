package clone.ink

import kotlin.math.max
import kotlin.math.min

/** Pure size arithmetic, kept apart from Android so it can be unit-tested. All page sizes are in points. */
object Sizing {
    const val IMPORT_PAGE_WIDTH_PT = 595f          // A4 width; imported pictures are fitted to it
    const val MAX_PAGE_HEIGHT_PT = 4000f

    /** Scale that fits the whole page inside the available area (pixels per point). */
    fun fitScale(availW: Float, availH: Float, pageW: Float, pageH: Float): Float {
        require(pageW > 0 && pageH > 0) { "page size must be positive" }
        return max(0.01f, min(availW / pageW, availH / pageH))
    }

    /** A picture becomes a page 595 pt wide, as tall as its shape needs, never taller than 4000 pt. */
    fun imagePageSize(imageW: Int, imageH: Int): Pair<Float, Float> {
        require(imageW > 0 && imageH > 0) { "image size must be positive" }
        val h = IMPORT_PAGE_WIDTH_PT * imageH / imageW
        return IMPORT_PAGE_WIDTH_PT to min(h, MAX_PAGE_HEIGHT_PT)
    }

    /** Power-of-two decode step so the decoded picture is not smaller than the target. */
    fun sampleSize(srcW: Int, srcH: Int, targetW: Int, targetH: Int): Int {
        var s = 1
        while (srcW / (s * 2) >= targetW && srcH / (s * 2) >= targetH) s *= 2
        return s
    }

    /** Keeps a page index inside 0 until count. */
    fun clampPage(index: Int, count: Int): Int = if (count <= 0) 0 else index.coerceIn(0, count - 1)
}
