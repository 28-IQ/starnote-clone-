package clone.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

/**
 * Local database. Schema lives in assets/migrations/NNN_name.sql, generated from replica/schema.sql.
 * The database version is the number of migration files. A fresh install runs all of them;
 * an upgrade runs only the ones above the old version. Statements are separated by a "-- ;;" line.
 */
class NoteDb(private val context: Context, private val fileName: String = FILE_NAME) :
    SQLiteOpenHelper(context, fileName, null, migrationFiles(context).size) {

    /** Where this database lives. Tests open their own copies under another name. */
    val databaseFile: File get() = context.getDatabasePath(fileName)

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) = runMigrations(db, from = 0)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = runMigrations(db, from = oldVersion)

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Database is newer than this app ($oldVersion > $newVersion)")
    }

    private fun runMigrations(db: SQLiteDatabase, from: Int) {
        val files = migrationFiles(context)
        for (i in from until files.size) {
            val sql = context.assets.open("migrations/${files[i]}").bufferedReader().use { it.readText() }
            sql.split("-- ;;").map { it.trim() }.filter { it.isNotEmpty() }.forEach { db.execSQL(it) }
        }
    }

    companion object {
        const val FILE_NAME = "notes.db"
        private fun migrationFiles(context: Context): List<String> =
            (context.assets.list("migrations") ?: emptyArray()).filter { it.endsWith(".sql") }.sorted()
    }
}
