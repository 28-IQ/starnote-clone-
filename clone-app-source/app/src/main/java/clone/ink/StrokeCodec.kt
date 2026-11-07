package clone.ink

import androidx.ink.storage.decode
import androidx.ink.storage.encode
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInputBatch
import clone.data.StoredStroke
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Turns a finished Stroke into a database row and back. Coordinates are page points. */
object StrokeCodec {

    fun toRow(stroke: Stroke, spec: BrushSpec, pageId: String, layerId: String, z: Int): StoredStroke {
        val bytes = ByteArrayOutputStream().use { out ->
            stroke.inputs.encode(out)
            out.toByteArray()
        }
        val b = StrokeGeometry.bounds(stroke, pad = spec.sizePt / 2f)
        return StoredStroke(
            id = 0, pageId = pageId, layerId = layerId, z = z, data = bytes,
            brushType = spec.kind.name, brushParams = spec.toJson(),
            minX = b[0], minY = b[1], maxX = b[2], maxY = b[3],
        )
    }

    fun decode(row: StoredStroke): Stroke {
        val inputs = ByteArrayInputStream(row.data).use { StrokeInputBatch.decode(it) }
        return Stroke(brush = BrushSpec.fromJson(row.brushParams).toBrush(), inputs = inputs)
    }
}
