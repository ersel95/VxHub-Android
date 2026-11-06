package com.vxhub.internal.purchase

import android.app.Activity
import com.vxhub.internal.VxLog
import com.vxhub.internal.VxStateHolder
import com.vxhub.internal.network.BackendProductDto
import com.vxhub.internal.network.VxApi
import com.vxhub.internal.storage.VxPreferences
import com.vxhub.providers.VxProviderRegistry
import com.vxhub.providers.VxPurchaseProvider
import com.vxhub.purchases.VxProduct
import com.vxhub.purchases.VxProductType
import com.vxhub.purchases.VxPurchaseResult
import com.vxhub.purchases.VxRestoreResult
import com.vxhub.purchases.VxStoreProduct
import com.vxhub.purchases.VxStorePurchaseOutcome
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Products, purchases and restores. The VxHub backend is the only authority on premium: a store
 * purchase is not done until `device/after-purchase` verified it, and a restore not until
 * `device/restore` did.
 */
internal class VxPurchaseManager(
    private val api: VxApi,
    private val prefs: VxPreferences,
    private val state: VxStateHolder,
    private val pending: PendingPurchaseStore,
    private val providerOf: () -> VxPurchaseProvider? = { VxProviderRegistry.purchase },
) {

    /**
     * Loads the offering, joins it with the panel's product list and publishes the result.
     * Only products the panel knows are offered, and an owned non-consumable is not offered again.
     */
    suspend fun loadProducts() {
        val provider = providerOf()?.takeIf { it.isConfigured } ?: return
        val (storeProducts, backendProducts) = coroutineScope {
            val store = async { runCatching { provider.products() }.getOrElse { emptyList() } }
            val backend = async { runCatching { api.backendProducts() }.getOrNull() }
            // Warms RevenueCat's customer cache, as iOS does, so eligibility checks below are fast.
            async { runCatching { provider.customerInfo() } }.await()
            store.await() to backend.await()
        }
        if (backendProducts == null) {
            VxLog.w("Panel product list unavailable; keeping the previous products")
            return
        }
        val owned = prefs[VxPreferences.Keys.ACTIVE_NON_CONSUMABLES].orEmpty()
        val products = coroutineScope {
            storeProducts.mapNotNull { product ->
                val match = backendProducts.matching(product) ?: return@mapNotNull null
                if (product.type == VxProductType.NON_CONSUMABLE && product.id in owned) return@mapNotNull null
                async {
                    VxProduct(
                        storeProduct = product,
                        isTrialOrIntroEligible = runCatching { provider.isTrialOrIntroEligible(product) }.getOrDefault(false),
                        initialBonus = match.initialBonus,
                        renewalBonus = match.renewalBonus,
                    )
                }
            }.awaitAll()
        }
        state.products.value = products
        VxLog.i("Products loaded: ${products.size} of ${storeProducts.size} store products are offered")
    }

    /**
     * Buys [product] and has the backend verify it. If the store charged the user but
     * verification cannot finish now, the transaction is persisted and re-sent on every start
     * until verified; the result is then [VxPurchaseResult.PendingVerification].
     */
    suspend fun purchase(activity: Activity, product: VxStoreProduct): VxPurchaseResult {
        val provider = providerOf() ?: return VxPurchaseResult.Failed("Purchase provider not registered; add vxhub-revenuecat")
        if (!provider.isConfigured) return VxPurchaseResult.Failed("Purchases are not configured yet")
        return when (val outcome = provider.purchase(activity, product)) {
            VxStorePurchaseOutcome.Cancelled -> VxPurchaseResult.Cancelled
            VxStorePurchaseOutcome.PaymentPending -> VxPurchaseResult.PaymentPending
            is VxStorePurchaseOutcome.Failed -> VxPurchaseResult.Failed(outcome.message)
            is VxStorePurchaseOutcome.Purchased -> {
                // Persist before any network call: from here on the user has paid.
                pending.add(outcome.transactionId, outcome.productId)
                verifyPending(outcome.transactionId, outcome.productId, product.type, product.id)
            }
        }
    }

    /** Re-sends every unverified transaction. Concurrent calls are coalesced. Returns how many verified. */
    suspend fun retryPendingVerifications(): Int {
        if (!pending.beginRetryPass()) return 0
        try {
            var verified = 0
            for (item in pending.all()) {
                val result = verifyPending(item.transactionId, item.productId, productType = null, productId = item.productId)
                if (result is VxPurchaseResult.Verified) verified++
            }
            if (verified > 0) VxLog.i("Verified $verified pending purchase(s)")
            return verified
        } finally {
            pending.endRetryPass()
        }
    }

    fun hasPendingVerifications(): Boolean = !pending.isEmptyBlocking()

    private suspend fun verifyPending(
        transactionId: String,
        storeProductId: String,
        productType: VxProductType?,
        productId: String,
    ): VxPurchaseResult = when (val outcome = api.verifyPurchase(transactionId, storeProductId)) {
        is VerificationOutcome.Verified -> {
            pending.remove(transactionId)
            outcome.balance?.let { state.balance.value = it }
            state.isPremium.value = outcome.isPremium
            if (productType == VxProductType.NON_CONSUMABLE) markNonConsumableOwned(productId)
            VxPurchaseResult.Verified(outcome.isPremium)
        }
        is VerificationOutcome.RetryLater, VerificationOutcome.Unsupported, is VerificationOutcome.Rejected -> {
            pending.recordFailedAttempt(transactionId)
            VxPurchaseResult.PendingVerification
        }
    }

    /**
     * Restores through the store, then has the backend re-verify the account. Backends without
     * `device/restore` fall back to a device registration, which returns the backend's premium.
     */
    suspend fun restore(registerFallback: suspend () -> Boolean): VxRestoreResult {
        val provider = providerOf() ?: return VxRestoreResult.Failed("Purchase provider not registered; add vxhub-revenuecat")
        if (!provider.isConfigured) return VxRestoreResult.Failed("Purchases are not configured yet")
        val snapshot = try {
            provider.restore()
        } catch (e: Exception) {
            return VxRestoreResult.Failed(e.message)
        }
        // An unverified transaction is the likeliest reason someone taps Restore.
        retryPendingVerifications()
        if (!snapshot.hasActiveSubscription && !snapshot.hasNonSubscriptionPurchases) {
            return VxRestoreResult.NothingToRestore
        }
        return when (val outcome = api.verifyRestore()) {
            is VerificationOutcome.Verified -> {
                outcome.balance?.let { state.balance.value = it }
                state.isPremium.value = outcome.isPremium
                VxRestoreResult.Restored(outcome.isPremium)
            }
            VerificationOutcome.Unsupported ->
                if (registerFallback()) VxRestoreResult.Restored(state.isPremium.value) else VxRestoreResult.PendingVerification
            is VerificationOutcome.RetryLater, is VerificationOutcome.Rejected -> VxRestoreResult.PendingVerification
        }
    }

    /** The store's own view: any active subscription. */
    suspend fun storePremiumState(): Boolean =
        providerOf()?.takeIf { it.isConfigured }?.let { provider ->
            runCatching { provider.customerInfo()?.activeSubscriptions?.isNotEmpty() == true }.getOrDefault(false)
        } ?: false

    suspend fun markNonConsumableOwned(productId: String) {
        prefs.edit {
            it[VxPreferences.Keys.ACTIVE_NON_CONSUMABLES] = it[VxPreferences.Keys.ACTIVE_NON_CONSUMABLES].orEmpty() + productId
        }
    }

    private fun List<BackendProductDto>.matching(product: VxStoreProduct): BackendProductDto? {
        // The panel may list a Play subscription by product id or by `productId:basePlanId`.
        firstOrNull { it.storeIdentifier == product.id }?.let { return it }
        return firstOrNull { it.storeIdentifier == product.productId }
    }

    companion object {
        /** Persistence of the pending queue in [VxPreferences]. */
        fun persistence(prefs: VxPreferences): PendingPurchasePersistence = object : PendingPurchasePersistence {
            override fun read(): String? = prefs[VxPreferences.Keys.PENDING_PURCHASES]

            override suspend fun write(json: String?) {
                prefs.edit {
                    if (json == null) it.remove(VxPreferences.Keys.PENDING_PURCHASES) else it[VxPreferences.Keys.PENDING_PURCHASES] = json
                }
            }
        }
    }
}
