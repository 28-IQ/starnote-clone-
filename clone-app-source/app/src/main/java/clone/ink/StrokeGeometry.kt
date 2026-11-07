package clone.ink

import androidx.ink.strokes.Stroke
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The only file that reads raw input points from a Stroke (inputs.size and inputs[i].x / .y).
 * If those accessors differ in the Ink version you build against, fix them here only.
 */
object StrokeGeometry {

    /** [minX, minY, maxX, maxY], padded by half the stroke width. */
    fun bounds(stroke: Stroke, pad: Float): FloatArray {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        val n = stroke.inputs.size
        for (i in 0 until n) {
            val p = stroke.inputs[i]
            minX = min(minX, p.x); maxX = max(maxX, p.x)
            minY = min(minY, p.y); maxY = max(maxY, p.y)
        }
        return floatArrayOf(minX - pad, minY - pad, maxX + pad, maxY + pad)
    }

    /** Shortest distance from (x, y) to the stroke's centre line. Infinity for an empty stroke. */
    fun distance(stroke: Stroke, x: Float, y: Float): Float {
        val n = stroke.inputs.size
        if (n == 0) return Float.POSITIVE_INFINITY
        var best = Float.POSITIVE_INFINITY
        var prev = stroke.inputs[0]
        best = min(best, hypot(prev.x - x, prev.y - y))
        for (i in 1 until n) {
            val cur = stroke.inputs[i]
            best = min(best, distanceToSegment(x, y, prev.x, prev.y, cur.x, cur.y))
            prev = cur
        }
        return best
    }

    fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 == 0f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}
