package clone.app

import android.app.Application
import clone.backup.BackupService
import clone.backup.GoogleAuth
import clone.backup.PendingRestore
import clone.backup.Schedules
import clone.billing.BillingRepo
import clone.importer.ImportService
import clone.ink.PageBitmaps
import java.io.File
import clone.data.AssetRepo
import clone.data.BackupRepo
import clone.data.EntitlementRepo
import clone.data.FolderRepo
import clone.data.ObjectRepo
import clone.data.NoteDb
import clone.data.NotebookRepo
import clone.data.SettingsRepo
import clone.data.StrokeRepo

/** Plain singletons, no injection framework yet. */
class CloneApp : Application() {
    val db by lazy { NoteDb(this) }
    val assetsDir by lazy { File(filesDir, "assets") }
    val notebooks by lazy { NotebookRepo(db, assetsDir) }
    val assets by lazy { AssetRepo(db) }
    val folders by lazy { FolderRepo(db) }
    val objects by lazy { ObjectRepo(db) }
    val importer by lazy { ImportService(this, assets, notebooks, assetsDir) }
    val pageBitmaps by lazy { PageBitmaps() }
    val strokes by lazy { StrokeRepo(db) }
    val settings by lazy { SettingsRepo(db) }
    val backupRepo by lazy { BackupRepo(db) }
    val entitlements by lazy { EntitlementRepo(db) }
    val googleAuth by lazy { GoogleAuth(this) }
    val backupService by lazy { BackupService(this, db, backupRepo, settings, googleAuth) }
    val billing by lazy { BillingRepo(this, entitlements, BuildConfig.PLAY_UNLOCK_PRODUCT_ID) }

    override fun onCreate() {
        super.onCreate()
        PendingRestore.applyIfAny(this)      // must run before anything opens the database
        Schedules.apply(this, settings)
        if (BuildConfig.DEBUG) seedIfEmpty()
    }

    /** Debug builds start with one empty sample note so the library is not blank. */
    private fun seedIfEmpty() {
        if (notebooks.count() == 0) notebooks.create(title = "Sample sketchbook")
    }
}
