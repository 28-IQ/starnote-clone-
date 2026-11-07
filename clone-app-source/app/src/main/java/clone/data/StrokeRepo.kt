package clone.data

import android.content.ContentValues
import android.database.Cursor

class StrokeRepo(private val db: NoteDb) {

    fun nextZ(pageId: String): Int =
        db.readableDatabase.rawQuery("select coalesce(max(z), 0) + 1 from strokes where page_id = ?", arrayOf(pageId))
            .use { it.moveToFirst(); it.getInt(0) }

    /** Architecture call: StrokeRepo.commit. Also used to restore a deleted stroke under its old id (undo). */
    fun insert(s: StoredStroke): Long {
        val v = ContentValues().apply {
            if (s.id > 0) put("id", s.id)
            put("page_id", s.pageId)
            put("layer_id", s.layerId)
            put("z", s.z)
            put("data", s.data)
            put("brush_type", s.brushType)
            put("brush_params", s.brushParams)
            put("min_x", s.minX); put("min_y", s.minY); put("max_x", s.maxX); put("max_y", s.maxY)
            put("created_at", System.currentTimeMillis())
        }
        return db.writableDatabase.insertOrThrow("strokes", null, v)
    }

    fun delete(ids: List<Long>) {
        if (ids.isEmpty()) return
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            ids.forEach { w.execSQL("delete from strokes where id = ?", arrayOf<Any?>(it)) }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    /** Strokes on visible layers, in drawing order. */
    fun visibleOnPage(pageId: String): List<StoredStroke> = query(
        """select s.id, s.page_id, s.layer_id, s.z, s.data, s.brush_type, s.brush_params, s.min_x, s.min_y, s.max_x, s.max_y
           from strokes s join layers l on l.id = s.layer_id
           where s.page_id = ? and l.hidden = 0 order by s.z""",
        arrayOf(pageId),
    )

    /** Strokes whose bounding box touches the rectangle, found through the R-tree. */
    fun candidates(pageId: String, minX: Float, minY: Float, maxX: Float, maxY: Float): List<StoredStroke> = query(
        """select s.id, s.page_id, s.layer_id, s.z, s.data, s.brush_type, s.brush_params, s.min_x, s.min_y, s.max_x, s.max_y
           from strokes s join stroke_rtree r on r.id = s.id
           join layers l on l.id = s.layer_id
           where s.page_id = ? and l.hidden = 0 and l.locked = 0
             and r.max_x >= cast(? as real) and r.min_x <= cast(? as real) and r.max_y >= cast(? as real) and r.min_y <= cast(? as real)""",
        arrayOf(pageId, minX.toString(), maxX.toString(), minY.toString(), maxY.toString()),
    )

    private fun query(sql: String, args: Array<String>): List<StoredStroke> {
        val out = ArrayList<StoredStroke>()
        db.readableDatabase.rawQuery(sql, args).use { c -> while (c.moveToNext()) out.add(c.toStroke()) }
        return out
    }

    private fun Cursor.toStroke() = StoredStroke(
        id = getLong(0), pageId = getString(1), layerId = getString(2), z = getInt(3), data = getBlob(4),
        brushType = getString(5), brushParams = getString(6),
        minX = getFloat(7), minY = getFloat(8), maxX = getFloat(9), maxY = getFloat(10),
    )
}
