package clone.data

import org.json.JSONObject
import org.json.JSONTokener

/** Key/value settings in the `settings` table. Values are stored as json literals. */
class SettingsRepo(private val db: NoteDb) {

    private fun raw(key: String): String? =
        db.readableDatabase.rawQuery("select value from settings where key = ?", arrayOf(key))
            .use { if (it.moveToFirst()) it.getString(0) else null }

    private fun putRaw(key: String, json: String) {
        db.writableDatabase.execSQL(
            "insert or replace into settings(key, value, updated_at) values (?,?,?)",   // no UPSERT before Android 11
            arrayOf<Any?>(key, json, System.currentTimeMillis()),
        )
    }

    fun getString(key: String): String? = raw(key)?.let { JSONTokener(it).nextValue() as? String }
    fun getBool(key: String, default: Boolean): Boolean = raw(key)?.let { JSONTokener(it).nextValue() as? Boolean } ?: default
    fun getLong(key: String, default: Long): Long = raw(key)?.let { (JSONTokener(it).nextValue() as? Number)?.toLong() } ?: default

    fun putString(key: String, value: String) = putRaw(key, JSONObject.quote(value))
    fun putBool(key: String, value: Boolean) = putRaw(key, value.toString())
    fun putLong(key: String, value: Long) = putRaw(key, value.toString())
    fun remove(key: String) { db.writableDatabase.execSQL("delete from settings where key = ?", arrayOf<Any?>(key)) }

    object Keys {
        const val BACKUP_PROVIDER = "backup.provider"          // "none" | "gdrive"
        const val BACKUP_INTERVAL_DAYS = "backup.interval_days" // 0 = off
        const val BACKUP_WIFI_ONLY = "backup.wifi_only"
        const val BACKUP_LAST_OK_AT = "backup.last_ok_at"
        fun uploadSession(sha256: String) = "backup.upload_session.$sha256"
    }
}
