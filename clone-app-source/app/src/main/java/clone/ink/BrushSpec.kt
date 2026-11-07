package clone.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import org.json.JSONObject

enum class PenKind { Pen, Marker, Highlighter }

/** What the user picked. Stored per stroke (strokes.brush_params) because ink-storage keeps only the inputs. */
data class BrushSpec(
    val kind: PenKind,
    val colorArgb: Int,
    val sizePt: Float,
    val epsilon: Float = DEFAULT_EPSILON,
) {
    fun toBrush(): Brush {
        val family = when (kind) {
            PenKind.Pen -> StockBrushes.pressurePenLatest()
            PenKind.Marker -> StockBrushes.markerLatest()
            PenKind.Highlighter -> StockBrushes.highlighterLatest()
        }
        return Brush.createWithColorIntArgb(family = family, colorIntArgb = colorArgb, size = sizePt, epsilon = epsilon)
    }

    fun toJson(): String = JSONObject()
        .put("kind", kind.name)
        .put("color", colorArgb)
        .put("size", sizePt.toDouble())
        .put("epsilon", epsilon.toDouble())
        .toString()

    companion object {
        /** World units are points. 0.05 pt is well below what a screen can show at normal zoom. */
        const val DEFAULT_EPSILON = 0.05f

        fun fromJson(json: String): BrushSpec {
            val o = JSONObject(json)
            return BrushSpec(
                kind = PenKind.valueOf(o.getString("kind")),
                colorArgb = o.getInt("color"),
                sizePt = o.getDouble("size").toFloat(),
                epsilon = o.getDouble("epsilon").toFloat(),
            )
        }
    }
}

object BrushDefaults {
    /** Thickness choices in points: fine, medium, bold. */
    val sizesPt = floatArrayOf(1.2f, 2.4f, 4.8f)
    /** Highlighter widths in points: thin, medium, wide. */
    val highlighterSizesPt = floatArrayOf(8f, 14f, 22f)
    const val DEFAULT_SIZE_INDEX = 1
    /** Eraser reach in points. */
    const val ERASER_RADIUS_PT = 8f
}
