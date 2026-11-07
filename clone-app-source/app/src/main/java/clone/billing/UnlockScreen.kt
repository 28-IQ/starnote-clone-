package clone.billing

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import clone.app.CloneApp
import clone.design.*

/** S38: the one-time unlock. What it unlocks, and the price, are set in Play Console and decided in /replica-launch. */
@Composable
fun UnlockScreen(onBack: () -> Unit) {
    val c = NoteTheme.colors
    val ctx = LocalContext.current
    val billing = (ctx.applicationContext as CloneApp).billing
    val state by billing.state.collectAsState()
    val error by billing.error.collectAsState()
    LaunchedEffect(Unit) { billing.refresh() }

    Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding().padding(Layout.pageMargin), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NoteButton("Back", onBack, kind = ButtonKind.Ghost)
            Text("Unlock", color = c.text, style = MaterialTheme.typography.titleLarge)
        }
        error?.let { InfoBanner(it, BannerKind.Error) }
        when (val s = state) {
            UnlockState.Loading -> Text("Checking Google Play…", color = c.textMuted, style = MaterialTheme.typography.bodyLarge)
            is UnlockState.Unavailable -> InfoBanner(s.reason, BannerKind.Warning)
            is UnlockState.Ready -> {
                Text("One payment, no subscription.", color = c.text, style = MaterialTheme.typography.bodyLarge)
                NoteButton("Unlock for ${s.price}", { (ctx as? Activity)?.let { billing.launchPurchase(it) } })
            }
            UnlockState.Pending -> InfoBanner("Your payment is still being processed. The unlock turns on when it completes.")
            UnlockState.Owned -> InfoBanner("Unlocked. Thank you.")
        }
        var checking by remember { mutableStateOf(false) }
        LaunchedEffect(checking) { if (checking) { billing.refresh(); checking = false } }
        NoteButton("Restore purchase", { checking = true }, kind = ButtonKind.Ghost, enabled = !checking)
    }
}
