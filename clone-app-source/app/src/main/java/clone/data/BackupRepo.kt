package clone.data

/** Rows of the `backups` table. They double as the dead-letter log: a failed run keeps its error text. */
class BackupRepo(private val db: NoteDb) {

    data class Row(val id: String, val provider: String, val remoteId: String?, val status: String, val bytes: Long?, val error: String?, val createdAt: Long)

    fun start(provider: String): String {
        val id = Ids.uuid7()
        db.writableDatabase.execSQL(
            "insert into backups(id, provider, status, created_at) values (?,?, 'running', ?)",
            arrayOf<Any?>(id, provider, System.currentTimeMillis()),
        )
        return id
    }

    fun finishOk(id: String, remoteId: String?, bytes: Long) {
        db.writableDatabase.execSQL(
            "update backups set status = 'done', remote_id = ?, byte_size = ?, error = null where id = ?",
            arrayOf<Any?>(remoteId, bytes, id),
        )
    }

    /** Error text must never contain note titles or other user content. */
    fun finishFailed(id: String, error: String) {
        db.writableDatabase.execSQL("update backups set status = 'failed', error = ? where id = ?", arrayOf<Any?>(error.take(300), id))
    }

    /** A run left 'running' by a killed process is not coming back. */
    fun failStaleRunning(olderThanMs: Long) {
        db.writableDatabase.execSQL(
            "update backups set status = 'failed', error = 'Interrupted' where status = 'running' and created_at < ?",
            arrayOf<Any?>(System.currentTimeMillis() - olderThanMs),
        )
    }

    fun recent(limit: Int = 20): List<Row> {
        val out = ArrayList<Row>()
        db.readableDatabase.rawQuery(
            "select id, provider, remote_id, status, byte_size, error, created_at from backups order by created_at desc limit ?",
            arrayOf(limit.toString()),
        ).use { c ->
            while (c.moveToNext()) out.add(Row(c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                if (c.isNull(4)) null else c.getLong(4), c.getString(5), c.getLong(6)))
        }
        return out
    }

    fun clear() { db.writableDatabase.execSQL("delete from backups") }
}
