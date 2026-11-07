package clone.editor

import kotlin.math.max
import kotlin.math.min

/** Pure geometry for text boxes and pictures on a page, in points. Kept apart from Android so it can be unit-tested. */
object ObjectGeometry {
    const val MIN_SIZE = 24f
    const val HIT_SLOP = 6f

    class Box(val x: Float, val y: Float, val w: Float, val h: Float)

    fun contains(b: Box, px: Float, py: Float, slop: Float = HIT_SLOP): Boolean =
        px >= b.x - slop && px <= b.x + b.w + slop && py >= b.y - slop && py <= b.y + b.h + slop

    /** Index of the topmost box under the point (the last one in drawing order), or -1. */
    fun topmostAt(boxes: List<Box>, px: Float, py: Float): Int {
        for (i in boxes.indices.reversed()) if (contains(boxes[i], px, py)) return i
        return -1
    }

    /** Keeps the box inside the page. A box larger than the page is pinned to the top-left corner. */
    fun clampToPage(x: Float, y: Float, w: Float, h: Float, pageW: Float, pageH: Float): Pair<Float, Float> {
        val nx = if (w >= pageW) 0f else x.coerceIn(0f, pageW - w)
        val ny = if (h >= pageH) 0f else y.coerceIn(0f, pageH - h)
        return nx to ny
    }

    /** Scales a box around its centre, never below [MIN_SIZE] wide and never wider than the page. Height follows the aspect. */
    fun scaleAbout(b: Box, factor: Float, pageW: Float, pageH: Float): Box {
        require(factor > 0f) { "factor must be positive" }
        val aspect = if (b.w > 0f) b.h / b.w else 1f
        val nw = (b.w * factor).coerceIn(MIN_SIZE, max(MIN_SIZE, pageW))
        val nh = nw * aspect
        val cx = b.x + b.w / 2f; val cy = b.y + b.h / 2f
        val (nx, ny) = clampToPage(cx - nw / 2f, cy - nh / 2f, nw, nh, pageW, pageH)
        return Box(nx, ny, nw, nh)
    }

    /** A new picture is at most 300 pt wide and 60% of the page width, with the picture's own shape. */
    fun defaultPictureSize(pictureW: Int, pictureH: Int, pageW: Float): Pair<Float, Float> {
        require(pictureW > 0 && pictureH > 0) { "picture size must be positive" }
        val w = min(300f, pageW * 0.6f)
        return w to w * pictureH / pictureW
    }
}
