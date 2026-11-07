package clone.backup

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import clone.app.CloneApp
import clone.data.BackupRepo
import clone.data.SettingsRepo
import kotlinx.coroutines.launch

class BackupViewModel(app: Application) : AndroidViewModel(app) {
    private val clone = app as CloneApp
    private val settings = clone.settings
    private val service = clone.backupService

    var connected by mutableStateOf(settings.getString(SettingsRepo.Keys.BACKUP_PROVIDER) == "gdrive"); private set
    var intervalDays by mutableStateOf(settings.getLong(SettingsRepo.Keys.BACKUP_INTERVAL_DAYS, 0)); private set
    var wifiOnly by mutableStateOf(settings.getBool(SettingsRepo.Keys.BACKUP_WIFI_ONLY, true)); private set
    var busy by mutableStateOf(false); private set
    var progress by mutableStateOf<Float?>(null); private set
    var message by mutableStateOf<String?>(null); private set
    var remote by mutableStateOf<List<RemoteBackup>>(emptyList()); private set
    var history by mutableStateOf<List<BackupRepo.Row>>(emptyList()); private set
    var consent by mutableStateOf<PendingIntent?>(null); private set
    var restartNeeded by mutableStateOf(PendingRestore.isStaged(app)); private set

    init { reloadHistory(); if (connected) refreshRemote() }

    fun connect() = launch("Connecting") {
        when (val t = clone.googleAuth.token()) {
            is TokenResult.Token -> markConnected()
            is TokenResult.NeedsConsent -> consent = t.pendingIntent
            is TokenResult.Failed -> message = t.message
        }
    }

    fun consentLaunched() { consent = null }

    fun onConsentResult(data: Intent?) = launch("Connecting") {
        try { clone.googleAuth.resultFromIntent(data); markConnected() } catch (e: Exception) { message = "Google Drive access was not granted" }
    }

    private suspend fun markConnected() {
        settings.putString(SettingsRepo.Keys.BACKUP_PROVIDER, "gdrive")
        if (intervalDays == 0L) setInterval(7)
        connected = true
        message = "Google Drive is connected."
        refreshRemoteQuiet()
    }

    fun backUpNow() = launch("Backing up") {
        when (val o = service.backUpToDrive { progress = it }) {
            is BackupOutcome.Done -> message = if (o.alreadyStored) "Your notes have not changed since the last backup." else "Backup finished."
            is BackupOutcome.NeedsConsent -> { consent = o.pendingIntent; message = "Connect Google Drive again." }
            is BackupOutcome.Failed -> message = o.message
        }
        reloadHistory(); refreshRemoteQuiet()
    }

    fun refreshRemote() = launch("Loading backups") { refreshRemoteQuiet() }

    private suspend fun refreshRemoteQuiet() {
        try { remote = service.listDriveBackups() } catch (e: BackupException) { message = e.message } catch (e: ConsentNeeded) { consent = e.pendingIntent }
    }

    fun restore(r: RemoteBackup) = launch("Downloading backup") {
        try { service.stageRestoreFromDrive(r) { progress = it }; restartNeeded = true; message = "Backup is ready. Restart the app to finish." }
        catch (e: BackupException) { message = e.message }
        catch (e: ConsentNeeded) { consent = e.pendingIntent }
    }

    fun deleteCloudBackups() = launch("Deleting") {
        try {
            val n = service.deleteAllDriveBackups()
            message = "Deleted $n backups from Google Drive."
            remote = emptyList()
        } catch (e: BackupException) { message = e.message } catch (e: ConsentNeeded) { consent = e.pendingIntent }
    }

    fun disconnect() {
        settings.putString(SettingsRepo.Keys.BACKUP_PROVIDER, "none")
        connected = false
        Schedules.apply(getApplication(), settings)
        message = "Automatic backups are off. Remove this app's access in your Google Account settings if you want."
    }

    fun setInterval(days: Long) {
        settings.putLong(SettingsRepo.Keys.BACKUP_INTERVAL_DAYS, days); intervalDays = days
        Schedules.apply(getApplication(), settings)
    }

    fun setWifiOnly(v: Boolean) {
        settings.putBool(SettingsRepo.Keys.BACKUP_WIFI_ONLY, v); wifiOnly = v
        Schedules.apply(getApplication(), settings)
    }

    fun saveToFile(target: Uri) = launch("Saving backup file") {
        try { service.exportTo(target); message = "Backup file saved." } catch (e: BackupException) { message = e.message }
    }

    fun restoreFromFile(source: Uri) = launch("Checking backup file") {
        try { service.stageRestoreFromUri(source); restartNeeded = true; message = "Backup is ready. Restart the app to finish." }
        catch (e: BackupException) { message = e.message } catch (e: Exception) { message = "That file could not be read as a backup" }
    }

    private fun reloadHistory() { history = clone.backupRepo.recent() }

    private fun launch(what: String, block: suspend () -> Unit) {
        if (busy) return
        viewModelScope.launch {
            busy = true; progress = null; message = null
            try { block() } catch (e: Exception) { message = "$what failed" } finally { busy = false; progress = null }
        }
    }
}
