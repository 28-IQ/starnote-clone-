package clone.data

import org.json.JSONObject

/** A text box or a picture placed on a page. Position and size are in page points. */
class PageObject(
    val id: String,
    val pageId: String,
    val layerId: String,
    val kind: String,              // "text" | "image"
    val assetId: String?,          // images
    val assetPath: String?,        // file name inside the assets folder, from the join
    val props: String,             // json: text boxes keep text, size, color
    val z: Int,
    val x: Float, val y: Float, val w: Float, val h: Float,
) {
    fun moved(nx: Float, ny: Float) = PageObject(id, pageId, layerId, kind, assetId, assetPath, props, z, nx, ny, w, h)
    fun resized(nw: Float, nh: Float) = PageObject(id, pageId, layerId, kind, assetId, assetPath, props, z, x, y, nw, nh)
    fun withProps(json: String, nh: Float = h) = PageObject(id, pageId, layerId, kind, assetId, assetPath, json, z, x, y, w, nh)
}

/** The json kept in `objects.props` for a text box. */
data class TextProps(val text: String, val sizePt: Float, val colorArgb: Int) {
    fun toJson(): String = JSONObject().put("text", text).put("size", sizePt.toDouble()).put("color", colorArgb).toString()

    companion object {
        val SIZES_PT = floatArrayOf(10f, 12f, 14f, 18f, 24f, 32f, 48f)
        const val DEFAULT_SIZE_INDEX = 2

        fun fromJson(json: String): TextProps {
            val o = JSONObject(json)
            // black only if the stored json has no colour
            return TextProps(o.optString("text"), o.optDouble("size", 14.0).toFloat(), o.optInt("color", android.graphics.Color.BLACK))
        }

        /** The next size up or down the list; stays at the ends. A size that is not on the list moves to its nearest neighbour in that direction. */
        fun stepSize(current: Float, up: Boolean): Float {
            val exact = SIZES_PT.indexOfFirst { it == current }
            val i = if (up) {
                if (exact >= 0) exact + 1 else SIZES_PT.indexOfFirst { it > current }.let { if (it < 0) SIZES_PT.size - 1 else it }
            } else {
                if (exact >= 0) exact - 1 else SIZES_PT.indexOfLast { it < current }.let { if (it < 0) 0 else it }
            }
            return SIZES_PT[i.coerceIn(0, SIZES_PT.size - 1)]
        }
    }
}

class ObjectRepo(private val db: NoteDb) {

    fun forPage(pageId: String): List<PageObject> {
        val out = ArrayList<PageObject>()
        db.readableDatabase.rawQuery(SQL_FOR_PAGE, arrayOf(pageId)).use { c ->
            while (c.moveToNext()) out.add(
                PageObject(
                    id = c.getString(0), pageId = pageId, layerId = c.getString(1), kind = c.getString(2),
                    assetId = if (c.isNull(3)) null else c.getString(3), assetPath = if (c.isNull(4)) null else c.getString(4),
                    props = c.getString(5), z = c.getInt(6), x = c.getFloat(7), y = c.getFloat(8), w = c.getFloat(9), h = c.getFloat(10),
                ),
            )
        }
        return out
    }

    fun nextZ(pageId: String): Int =
        db.readableDatabase.rawQuery("select coalesce(max(z), 0) + 1 from objects where page_id = ?", arrayOf(pageId)).use { it.moveToFirst(); it.getInt(0) }

    /** Also used to put a deleted object back (undo): the id is kept. */
    fun insert(o: PageObject) {
        val now = System.currentTimeMillis()
        db.writableDatabase.execSQL(
            SQL_INSERT,
            arrayOf<Any?>(o.id, o.pageId, o.layerId, o.kind, o.assetId, o.props, o.z, o.x.toDouble(), o.y.toDouble(), o.w.toDouble(), o.h.toDouble(), now, now),
        )
    }

    fun update(o: PageObject) {
        db.writableDatabase.execSQL(
            SQL_UPDATE,
            arrayOf<Any?>(o.x.toDouble(), o.y.toDouble(), o.w.toDouble(), o.h.toDouble(), o.props, System.currentTimeMillis(), o.id),
        )
    }

    fun delete(id: String) { db.writableDatabase.execSQL("delete from objects where id = ?", arrayOf<Any?>(id)) }

    private companion object {
        const val SQL_FOR_PAGE = """select o.id, o.layer_id, o.kind, o.asset_id, a.file_path, o.props, o.z, o.x, o.y, o.w, o.h
            from objects o left join assets a on a.id = o.asset_id
            where o.page_id = ? order by o.z"""
        const val SQL_INSERT = "insert into objects(id, page_id, layer_id, kind, asset_id, props, z, locked, x, y, w, h, rotation, created_at, updated_at) values (?,?,?,?,?,?,?,0,?,?,?,?,0,?,?)"
        const val SQL_UPDATE = "update objects set x = ?, y = ?, w = ?, h = ?, props = ?, updated_at = ? where id = ?"
    }
}
