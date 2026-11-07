package clone.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import clone.data.EntitlementRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed interface UnlockState {
    data object Loading : UnlockState
    data class Unavailable(val reason: String) : UnlockState
    data class Ready(val price: String) : UnlockState
    data object Pending : UnlockState
    data object Owned : UnlockState
}

/**
 * One-time unlock through Google Play Billing (non-consumable). Order matters: check what Play says the
 * user owns, grant, then acknowledge within three days or Play refunds the purchase.
 * Pending purchases (slow payment methods) grant nothing. There is no server, so ownership is the signed
 * result Play returns to the device, cached in `entitlements`. That stops casual misuse, not a cracked app;
 * add server-side verification with the Play Developer API if the unlock ever protects something costly.
 */
class BillingRepo(
    context: Context,
    private val entitlements: EntitlementRepo,
    private val productId: String,
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<UnlockState>(UnlockState.Loading)
    val state: StateFlow<UnlockState> = _state.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private var details: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    /** Fresh look at the shop and at what the user owns. Safe to call often. */
    suspend fun refresh() {
        if (productId.isEmpty()) { _state.value = UnlockState.Unavailable("Purchases are not set up in this build"); return }
        if (!connect()) {
            _state.value = if (entitlements.isActive(productId)) UnlockState.Owned else UnlockState.Unavailable("Google Play is not available right now")
            return
        }
        loadDetails()
        refreshPurchases()
    }

    fun launchPurchase(activity: Activity) {
        val d = details ?: run { _error.value = "The product is not available yet"; return }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
            .build()
        val r = client.launchBillingFlow(activity, params)
        if (r.responseCode != BillingClient.BillingResponseCode.OK) _error.value = "Could not open the purchase screen"
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch { purchases?.forEach { process(it) } }
            BillingClient.BillingResponseCode.USER_CANCELED -> restoreReady()
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { refreshPurchases() }
            else -> { _error.value = "The purchase did not go through"; restoreReady() }
        }
    }

    private suspend fun connect(): Boolean = suspendCancellableCoroutine { cont ->
        if (client.isReady) { cont.resume(true); return@suspendCancellableCoroutine }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) { if (cont.isActive) cont.resume(r.responseCode == BillingClient.BillingResponseCode.OK) }
            override fun onBillingServiceDisconnected() { /* auto reconnection is on */ }
        })
    }

    private suspend fun loadDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(productId).setProductType(BillingClient.ProductType.INAPP).build()))
            .build()
        val r = client.queryProductDetails(params)
        details = r.productDetailsList?.firstOrNull()
        restoreReady()
    }

    private suspend fun refreshPurchases() {
        val r = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build())
        if (r.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return   // keep the cache as it is
        val ours = r.purchasesList.filter { productId in it.products }
        if (ours.isEmpty()) {
            if (entitlements.get(productId)?.state in setOf("active", "pending")) entitlements.upsert(productId, "revoked", true, null)   // refunded or charged back
            restoreReady()
        } else {
            ours.forEach { process(it) }
        }
    }

    private suspend fun process(p: Purchase) {
        when (p.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                var acknowledged = p.isAcknowledged
                if (!acknowledged) {
                    val ack = client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build())
                    acknowledged = ack.responseCode == BillingClient.BillingResponseCode.OK
                }
                entitlements.upsert(productId, "active", acknowledged, p.purchaseTime)   // acknowledged = false is retried by the daily check
                _state.value = UnlockState.Owned
            }
            Purchase.PurchaseState.PENDING -> {
                entitlements.upsert(productId, "pending", false, null)
                _state.value = UnlockState.Pending
            }
            else -> Unit
        }
    }

    private fun restoreReady() {
        if (entitlements.isActive(productId)) { _state.value = UnlockState.Owned; return }
        val price = details?.oneTimePurchaseOfferDetails?.formattedPrice
        _state.value = if (price != null) UnlockState.Ready(price) else UnlockState.Unavailable("The unlock is not available in your country or account")
    }
}
