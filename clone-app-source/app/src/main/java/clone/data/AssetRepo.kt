package clone.data

/** Rows of the `assets` table. The files themselves live in filesDir/assets, named by their sha256. */
class AssetRepo(private val db: NoteDb) {

    data class Asset(val id: String, val kind: String, val filePath: String, val pageCount: Int?)

    fun findBySha(sha256: String): Asset? =
        db.readableDatabase.rawQuery("select id, kind, file_path, page_count from assets where sha256 = ?", arrayOf(sha256)).use { c ->
            if (c.moveToFirst()) Asset(c.getString(0), c.getString(1), c.getString(2), if (c.isNull(3)) null else c.getInt(3)) else null
        }

    fun insert(kind: String, filePath: String, mime: String, sha256: String, byteSize: Long, width: Int?, height: Int?, pageCount: Int?): String {
        val id = Ids.uuid7()
        db.writableDatabase.execSQL(
            "insert into assets(id, kind, source, file_path, mime, sha256, byte_size, width, height, page_count, created_at) values (?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(id, kind, "imported", filePath, mime, sha256, byteSize, width, height, pageCount, System.currentTimeMillis()),
        )
        return id
    }
}
