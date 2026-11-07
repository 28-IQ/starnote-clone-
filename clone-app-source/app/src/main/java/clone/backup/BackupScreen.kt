package clone.backup

import android.content.Intent
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import clone.design.*
import java.util.Date

/** S32: save and restore copies of the library, on this device or in the user's Google Drive. */
@Composable
fun BackupScreen(onBack: () -> Unit, vm: BackupViewModel = viewModel()) {
    val c = NoteTheme.colors
    val ctx = LocalContext.current
    var confirmRestore by remember { mutableStateOf<RemoteBackup?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmFile by remember { mutableStateOf<android.net.Uri?>(null) }

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { vm.onConsentResult(it.data) }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(vm::saveToFile) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { confirmFile = it }

    LaunchedEffect(vm.consent) {
        vm.consent?.let { consentLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build()); vm.consentLaunched() }
    }

    Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding().verticalScroll(rememberScrollState()).padding(Layout.pageMargin),
        verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NoteButton("Back", onBack, kind = ButtonKind.Ghost)
            Text("Backup", color = c.text, style = MaterialTheme.typography.titleLarge)
        }
        vm.message?.let { InfoBanner(it) }
        if (vm.busy) {
            val p = vm.progress
            Text(if (p != null) "Working… ${(p * 100).toInt()}%" else "Working…", color = c.textMuted, style = MaterialTheme.typography.bodyMedium)
        }
        if (vm.restartNeeded) {
            InfoBanner("A backup is ready to restore. Restart the app to finish. Your current notes are kept once as a safety copy.", BannerKind.Warning)
            NoteButton("Restart now", { restart(ctx) })
        }

        Text("On this device", color = c.text, style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            NoteButton("Save a backup file", { saveFile.launch("notebook-backup.zip") }, kind = ButtonKind.Secondary, enabled = !vm.busy)
            NoteButton("Restore from a file", { pickFile.launch(arrayOf("application/zip", "application/octet-stream")) }, kind = ButtonKind.Secondary, enabled = !vm.busy)
        }

        Text("Google Drive", color = c.text, style = MaterialTheme.typography.titleLarge)
        Text("Backups go to a hidden folder only this app can see. Nothing else in your Drive is read.", color = c.textMuted, style = MaterialTheme.typography.bodyMedium)
        if (!vm.connected) {
            NoteButton("Connect Google Drive", { vm.connect() }, enabled = !vm.busy)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                NoteButton("Back up now", { vm.backUpNow() }, enabled = !vm.busy, loading = vm.busy)
                NoteButton("Disconnect", { vm.disconnect() }, kind = ButtonKind.Ghost, enabled = !vm.busy)
            }
            Text("Automatic backup", color = c.text, style = MaterialTheme.typography.bodyLarge)
            val options = listOf("Off", "Daily", "Weekly")
            val days = listOf(0L, 1L, 7L)
            SegmentedControl(options, days.indexOf(vm.intervalDays).coerceAtLeast(0), { vm.setInterval(days[it]) }, Modifier.width(Layout.segmentedW))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Only on Wi-Fi", Modifier.weight(1f), color = c.text, style = MaterialTheme.typography.bodyLarge)
                Switch(vm.wifiOnly, { vm.setWifiOnly(it) })
            }
            Text("Backups in Google Drive (newest 5 are kept)", color = c.text, style = MaterialTheme.typography.bodyLarge)
            if (vm.remote.isEmpty()) Text("None yet.", color = c.textMuted, style = MaterialTheme.typography.bodyMedium)
            vm.remote.forEach { r ->
                PanelRow(
                    title = "${r.createdAt.take(10)}  ${r.sizeBytes / (1024 * 1024)} MB",
                    trailing = { NoteButton("Restore", { confirmRestore = r }, kind = ButtonKind.Secondary, size = ButtonSize.Sm, enabled = !vm.busy) },
                )
            }
            NoteButton("Delete all Google Drive backups", { confirmDelete = true }, kind = ButtonKind.Danger, enabled = !vm.busy)
        }

        if (vm.history.isNotEmpty()) {
            Text("Recent runs", color = c.text, style = MaterialTheme.typography.titleLarge)
            vm.history.take(5).forEach { h ->
                val whenText = DateFormat.getMediumDateFormat(ctx).format(Date(h.createdAt)) + " " + DateFormat.getTimeFormat(ctx).format(Date(h.createdAt))
                Text("$whenText  ${h.status}" + (h.error?.let { ": $it" } ?: ""), color = c.textMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    confirmRestore?.let { r ->
        ConfirmDialog("Restore this backup?", "Your current notes will be replaced when the app restarts. They are kept once as a safety copy.", "Restore",
            onConfirm = { confirmRestore = null; vm.restore(r) }, onDismiss = { confirmRestore = null })
    }
    confirmFile?.let { uri ->
        ConfirmDialog("Restore from this file?", "Your current notes will be replaced when the app restarts. They are kept once as a safety copy.", "Restore",
            onConfirm = { confirmFile = null; vm.restoreFromFile(uri) }, onDismiss = { confirmFile = null })
    }
    if (confirmDelete) {
        ConfirmDialog("Delete all Google Drive backups?", "This removes every backup this app made in your Google Drive. It cannot be undone.", "Delete",
            onConfirm = { confirmDelete = false; vm.deleteCloudBackups() }, onDismiss = { confirmDelete = false })
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onConfirm) { Text(action) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

private fun restart(context: android.content.Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(launch)
    Runtime.getRuntime().exit(0)
}
