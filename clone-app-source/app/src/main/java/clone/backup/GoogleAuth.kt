package clone.backup

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

sealed interface TokenResult {
    data class Token(val value: String) : TokenResult
    data class NeedsConsent(val pendingIntent: PendingIntent) : TokenResult
    data class Failed(val message: String) : TokenResult
}

/**
 * Google's Authorization API with the narrowest Drive scope: the app's own hidden data folder.
 * No client id or secret is in the app. Google matches the app by package name and signing SHA-1,
 * so the Android OAuth client you create in Google Cloud must use this app's package and your signing key.
 */
class GoogleAuth(private val context: Context) {

    private val request: AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_APPDATA)))
        .build()

    /** Works from a background job when access was granted before; otherwise returns NeedsConsent. */
    suspend fun token(): TokenResult = try {
        val result = Identity.getAuthorizationClient(context).authorize(request).await()
        when {
            result.hasResolution() -> TokenResult.NeedsConsent(result.pendingIntent!!)
            result.accessToken != null -> TokenResult.Token(result.accessToken!!)
            else -> TokenResult.Failed("Google did not return an access token")
        }
    } catch (e: Exception) {
        TokenResult.Failed(e.message ?: "Could not reach Google")
    }

    /** Called with the data from the consent screen's activity result. */
    fun resultFromIntent(data: Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)

    /** Takes back the app's access to the user's Google account data. Best effort. */
    suspend fun revoke(account: Account?) {
        if (account == null) return
        val req = RevokeAccessRequest.builder().setAccount(account).setScopes(listOf(Scope(DRIVE_APPDATA))).build()
        try { Identity.getAuthorizationClient(context).revokeAccess(req).await() } catch (_: Exception) { }
    }

    companion object {
        const val DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    }
}
