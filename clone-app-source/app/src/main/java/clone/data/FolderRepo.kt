package clone.data

import android.database.sqlite.SQLiteConstraintException

class FolderException(message: String) : Exception(message)

/** Folders of notes, nested without a depth limit. Names are unique inside their parent, ignoring case. */
class FolderRepo(private val db: NoteDb) {

    data class Folder(val id: String, val parentId: String?, val name: String, val noteCount: Int, val folderCount: Int)

    /** Folders at the top level, or inside [parentId]. */
    fun children(parentId: String?): List<Folder> =
        if (parentId == null) query(SQL_CHILDREN_TOP, emptyArray()) else query(SQL_CHILDREN_OF, arrayOf(parentId))

    /** Every folder, for the "move to" list. */
    fun all(): List<Folder> = query(SQL_ALL, emptyArray())

    /** From the top down to [folderId]: the breadcrumb. Empty for the top level. */
    fun path(folderId: String?): List<Pair<String, String>> {
        if (folderId == null) return emptyList()
        val out = ArrayList<Pair<String, String>>()
        db.readableDatabase.rawQuery(SQL_PATH, arrayOf(folderId)).use { c -> while (c.moveToNext()) out.add(c.getString(0) to c.getString(1)) }
        return out
    }

    fun create(parentId: String?, rawName: String): String {
        val name = cleanName(rawName)
        if (parentId != null && !exists(parentId)) throw FolderException("That folder no longer exists")
        val id = Ids.uuid7()
        val now = System.currentTimeMillis()
        try {
            db.writableDatabase.execSQL(
                "insert into folders(id, parent_id, name, sort_order, created_at, updated_at) values (?,?,?,0,?,?)",
                arrayOf<Any?>(id, parentId, name, now, now),
            )
        } catch (e: SQLiteConstraintException) {
            throw FolderException("A folder with that name already exists here")
        }
        return id
    }

    fun rename(id: String, rawName: String) {
        val name = cleanName(rawName)
        try {
            db.writableDatabase.execSQL("update folders set name = ?, updated_at = ? where id = ?", arrayOf<Any?>(name, System.currentTimeMillis(), id))
        } catch (e: SQLiteConstraintException) {
            throw FolderException("A folder with that name already exists here")
        }
    }

    /** Moves a folder under [newParentId] (null = top level). A folder cannot go inside itself or one of its own subfolders. */
    fun move(id: String, newParentId: String?) {
        if (newParentId != null) {
            val inside = db.readableDatabase.rawQuery(SQL_IS_INSIDE, arrayOf(id, newParentId)).use { it.moveToFirst(); it.getInt(0) }
            if (inside > 0) throw FolderException("A folder cannot be moved into itself")
        }
        try {
            db.writableDatabase.execSQL("update folders set parent_id = ?, updated_at = ? where id = ?", arrayOf<Any?>(newParentId, System.currentTimeMillis(), id))
        } catch (e: SQLiteConstraintException) {
            throw FolderException("A folder with that name already exists there")
        }
    }

    /** Only an empty folder can be deleted, so nothing is lost by accident. */
    fun delete(id: String) {
        val used = db.readableDatabase.rawQuery(SQL_CONTENT_COUNT, arrayOf(id, id)).use { it.moveToFirst(); it.getInt(0) }
        if (used > 0) throw FolderException("This folder is not empty. Move or delete what is inside first.")
        db.writableDatabase.execSQL("delete from folders where id = ?", arrayOf<Any?>(id))
    }

    private fun exists(id: String) =
        db.readableDatabase.rawQuery("select 1 from folders where id = ?", arrayOf(id)).use { it.moveToFirst() }

    private fun cleanName(raw: String): String {
        val name = raw.trim()
        if (name.isEmpty()) throw FolderException("Give the folder a name")
        if (name.length > MAX_NAME) throw FolderException("That name is too long (limit $MAX_NAME characters)")
        return name
    }

    private fun query(sql: String, args: Array<String>): List<Folder> {
        val out = ArrayList<Folder>()
        db.readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) out.add(Folder(c.getString(0), if (c.isNull(1)) null else c.getString(1), c.getString(2), c.getInt(3), c.getInt(4)))
        }
        return out
    }

    private companion object {
        const val MAX_NAME = 100

        const val SQL_CHILDREN_TOP = """select f.id, f.parent_id, f.name,
                (select count(*) from notebooks n where n.folder_id = f.id),
                (select count(*) from folders c where c.parent_id = f.id)
            from folders f where f.parent_id is null order by f.sort_order, f.name collate nocase"""

        const val SQL_CHILDREN_OF = """select f.id, f.parent_id, f.name,
                (select count(*) from notebooks n where n.folder_id = f.id),
                (select count(*) from folders c where c.parent_id = f.id)
            from folders f where f.parent_id = ? order by f.sort_order, f.name collate nocase"""

        const val SQL_ALL = """select f.id, f.parent_id, f.name,
                (select count(*) from notebooks n where n.folder_id = f.id),
                (select count(*) from folders c where c.parent_id = f.id)
            from folders f order by f.name collate nocase"""

        const val SQL_PATH = """with recursive up(id, name, depth) as (
                select id, name, 0 from folders where id = ?
                union all
                select f.id, f.name, up.depth + 1 from folders f join folders child on child.parent_id = f.id join up on up.id = child.id
            ) select id, name from up order by depth desc"""

        const val SQL_IS_INSIDE = """with recursive sub(id) as (
                select ? union all select f.id from folders f join sub on f.parent_id = sub.id
            ) select count(*) from sub where id = ?"""

        const val SQL_CONTENT_COUNT = """select (select count(*) from notebooks where folder_id = ?) + (select count(*) from folders where parent_id = ?)"""
    }
}
