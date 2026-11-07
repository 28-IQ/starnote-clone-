package clone.data

import java.io.File

class NotebookRepo(private val db: NoteDb, private val assetsDir: File) {

    /** Architecture call: NotebookRepo.create(kind, paper, cover, title, tags). Takes kind and title for now. */
    fun create(title: String, kind: String = "paged", folderId: String? = null): String =
        inFolder(folderId) { createWithPages(title, kind, null, listOf(NewPage(A4_WIDTH_PT.toFloat(), A4_HEIGHT_PT.toFloat()))) }

    /** A notebook made from an imported PDF or pictures: one page each. */
    fun createImported(title: String, sourceAssetId: String?, pages: List<NewPage>, folderId: String? = null): String {
        require(pages.isNotEmpty()) { "an import needs at least one page" }
        return inFolder(folderId) { createWithPages(title, "paged", sourceAssetId, pages) }
    }

    /** Runs [create] and files the new notebook in [folderId], all in one transaction. */
    private fun inFolder(folderId: String?, create: () -> String): String {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            val id = create()
            if (folderId != null) w.execSQL("update notebooks set folder_id = ? where id = ?", arrayOf<Any?>(folderId, id))
            w.setTransactionSuccessful()
            return id
        } finally {
            w.endTransaction()
        }
    }

    private fun createWithPages(title: String, kind: String, sourceAssetId: String?, pages: List<NewPage>): String {
        val now = System.currentTimeMillis()
        val notebookId = Ids.uuid7(now)
        val first = pages.first()
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            w.execSQL(
                "insert into notebooks(id, title, kind, page_width_pt, page_height_pt, orientation, source_asset_id, created_at, updated_at) values (?,?,?,?,?,?,?,?,?)",
                arrayOf<Any?>(notebookId, title, kind, first.widthPt.toDouble(), first.heightPt.toDouble(),
                    if (first.heightPt >= first.widthPt) "portrait" else "landscape", sourceAssetId, now, now),
            )
            pages.forEachIndexed { i, p ->
                val pageId = Ids.uuid7(now)
                w.execSQL(
                    "insert into pages(id, notebook_id, idx, kind, width_pt, height_pt, source_asset_id, source_page_no, template_asset_id, created_at, updated_at) values (?,?,?,?,?,?,?,?,?,?,?)",
                    arrayOf<Any?>(pageId, notebookId, i, "fixed", p.widthPt.toDouble(), p.heightPt.toDouble(), p.sourceAssetId, p.sourcePageNo, p.templateAssetId, now, now),
                )
                w.execSQL("insert into layers(id, page_id, idx, name) values (?,?,?,?)", arrayOf<Any?>(Ids.uuid7(now), pageId, 0, "Layer 1"))
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        return notebookId
    }

    /** Notes at the top level (null) or inside one folder, pinned first, then newest. */
    fun listIn(folderId: String?): List<NotebookSummary> {
        val out = ArrayList<NotebookSummary>()
        val cursor = if (folderId == null) db.readableDatabase.rawQuery(SQL_LIST_TOP, null)
                     else db.readableDatabase.rawQuery(SQL_LIST_IN, arrayOf(folderId))
        cursor.use { c -> while (c.moveToNext()) out.add(NotebookSummary(c.getString(0), c.getString(1), c.getLong(2))) }
        return out
    }

    fun moveToFolder(notebookId: String, folderId: String?) {
        db.writableDatabase.execSQL("update notebooks set folder_id = ?, updated_at = ? where id = ?", arrayOf<Any?>(folderId, System.currentTimeMillis(), notebookId))
    }

    /** Removes the note with its pages, layers, strokes and objects. Imported files stay until the asset sweeper runs. */
    fun delete(notebookId: String) {
        db.writableDatabase.execSQL("delete from notebooks where id = ?", arrayOf<Any?>(notebookId))
    }

    fun list(): List<NotebookSummary> {
        val out = ArrayList<NotebookSummary>()
        db.readableDatabase.rawQuery(
            "select id, title, updated_at from notebooks order by pinned_at is null, updated_at desc", null,
        ).use { c ->
            while (c.moveToNext()) out.add(NotebookSummary(c.getString(0), c.getString(1), c.getLong(2)))
        }
        return out
    }

    /** The page at [index] (0-based) with its first layer, its background and the notebook's page count. */
    fun openPage(notebookId: String, index: Int): OpenPage? {
        db.readableDatabase.rawQuery(
            """select n.title, p.id, p.idx, p.width_pt, p.height_pt, l.id,
                      coalesce(sa.kind, ta.kind), coalesce(sa.file_path, ta.file_path), p.source_page_no,
                      (select count(*) from pages where notebook_id = n.id)
               from notebooks n
               join pages p on p.notebook_id = n.id and p.idx = ?
               join layers l on l.page_id = p.id and l.idx = 0
               left join assets sa on sa.id = p.source_asset_id
               left join assets ta on ta.id = p.template_asset_id
               where n.id = ?""",
            arrayOf(index.toString(), notebookId),
        ).use { c ->
            if (!c.moveToFirst()) return null
            val kind = if (c.isNull(6)) null else c.getString(6)
            val path = if (c.isNull(7)) null else c.getString(7)
            val background = if (kind != null && path != null)
                PageBackground(kind, File(assetsDir, path), if (c.isNull(8)) null else c.getInt(8)) else null
            return OpenPage(notebookId, c.getString(0), c.getString(1), c.getString(5), c.getFloat(3), c.getFloat(4), c.getInt(2), c.getInt(9), background)
        }
    }

    fun openFirstPage(notebookId: String): OpenPage? = openPage(notebookId, 0)

    /** Adds an empty page at the end, the same size as the last one. Returns its index. */
    fun addBlankPage(notebookId: String): Int {
        val now = System.currentTimeMillis()
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            var lastIdx = -1; var width = A4_WIDTH_PT; var height = A4_HEIGHT_PT
            w.rawQuery("select idx, width_pt, height_pt from pages where notebook_id = ? order by idx desc limit 1", arrayOf(notebookId)).use { c ->
                if (c.moveToFirst()) { lastIdx = c.getInt(0); width = c.getDouble(1); height = c.getDouble(2) }
            }
            val pageId = Ids.uuid7(now)
            w.execSQL(
                "insert into pages(id, notebook_id, idx, kind, width_pt, height_pt, created_at, updated_at) values (?,?,?,?,?,?,?,?)",
                arrayOf<Any?>(pageId, notebookId, lastIdx + 1, "fixed", width, height, now, now),
            )
            w.execSQL("insert into layers(id, page_id, idx, name) values (?,?,?,?)", arrayOf<Any?>(Ids.uuid7(now), pageId, 0, "Layer 1"))
            w.execSQL("update notebooks set updated_at = ? where id = ?", arrayOf<Any?>(now, notebookId))
            w.setTransactionSuccessful()
            return lastIdx + 1
        } finally {
            w.endTransaction()
        }
    }

    fun touch(notebookId: String) {
        val now = System.currentTimeMillis()
        db.writableDatabase.execSQL(
            "update notebooks set updated_at = ?, last_opened_at = ? where id = ?",
            arrayOf<Any?>(now, now, notebookId),
        )
    }

    fun count(): Int = db.readableDatabase.rawQuery("select count(*) from notebooks", null).use { it.moveToFirst(); it.getInt(0) }

    companion object {
        private const val SQL_LIST_TOP = "select id, title, updated_at from notebooks where folder_id is null order by pinned_at is null, updated_at desc"
        private const val SQL_LIST_IN = "select id, title, updated_at from notebooks where folder_id = ? order by pinned_at is null, updated_at desc"
        const val A4_WIDTH_PT = 595.0
        const val A4_HEIGHT_PT = 842.0
    }
}
