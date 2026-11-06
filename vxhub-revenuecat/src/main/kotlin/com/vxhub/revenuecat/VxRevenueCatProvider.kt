package com.vxhub.revenuecat

import android.app.Activity
import android.content.Context
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitLogOut
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.awaitSyncAttributesAndOfferingsIfNeeded
import com.revenuecat.purchases.awaitSyncPurchases
import com.revenuecat.purchases.models.PurchaseState
import com.vxhub.providers.VxPurchaseProvider
import com.vxhub.purchases.VxCustomerInfo
import com.vxhub.purchases.VxRestoreSnapshot
import com.vxhub.purchases.VxStoreProduct
import com.vxhub.purchases.VxStorePurchaseOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Google Play purchases through RevenueCat. Registered automatically by [VxRevenueCatInitializer];
 * VxHub configures it with the panel's RevenueCat key and the device's vid as app user id.
 */
public class VxRevenueCatProvider : VxPurchaseProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val isConfigured: Boolean get() = Purchases.isConfigured

    override fun configure(context: Context, apiKey: String, appUserId: String) {
        if (Purchases.isConfigured) {
            // The app configured RevenueCat itself; align its user with the device instead.
            scope.launch { runCatching { Purchases.sharedInstance.awaitLogIn(appUserId) } }
            return
        }
        Purchases.logLevel = LogLevel.WARN
        Purchases.configure(
            PurchasesConfiguration.Builder(context.applicationContext, apiKey)
                .appUserID(appUserId)
                .build(),
        )
    }

    override suspend fun products(): List<VxStoreProduct> {
        if (!Purchases.isConfigured) return emptyList()
        val offerings = try {
            Purchases.sharedInstance.awaitOfferings()
        } catch (e: PurchasesException) {
            return emptyList()
        }
        return offerings.current?.availablePackages.orEmpty().map { RevenueCatStoreProduct(it.product) }
    }

    override suspend fun purchase(activity: Activity, product: VxStoreProduct): VxStorePurchaseOutcome {
        if (!Purchases.isConfigured) return VxStorePurchaseOutcome.Failed("RevenueCat is not configured")
        val rcProduct = (product as? RevenueCatStoreProduct)?.revenueCatProduct
            ?: return VxStorePurchaseOutcome.Failed("Unsupported product type")
        return try {
            val result = Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, rcProduct).build())
            val transaction = result.storeTransaction
            if (transaction.purchaseState == PurchaseState.PENDING) return VxStorePurchaseOutcome.PaymentPending
            // Google's order id is what RevenueCat records as the store purchase identifier, so it is
            // what the backend can find; the purchase token is the fallback when Play gives none.
            val transactionId = transaction.orderId?.takeIf { it.isNotBlank() } ?: transaction.purchaseToken
            if (transactionId.isBlank()) return VxStorePurchaseOutcome.Failed("The store returned no transaction")
            VxStorePurchaseOutcome.Purchased(transactionId = transactionId, productId = rcProduct.id)
        } catch (e: PurchasesTransactionException) {
            when {
                e.userCancelled -> VxStorePurchaseOutcome.Cancelled
                e.code == PurchasesErrorCode.PaymentPendingError -> VxStorePurchaseOutcome.PaymentPending
                else -> VxStorePurchaseOutcome.Failed(e.message)
            }
        }
    }

    override suspend fun restore(): VxRestoreSnapshot {
        check(Purchases.isConfigured) { "RevenueCat is not configured" }
        val info = Purchases.sharedInstance.awaitRestore()
        return VxRestoreSnapshot(
            hasActiveSubscription = info.activeSubscriptions.isNotEmpty(),
            hasNonSubscriptionPurchases = info.nonSubscriptionTransactions.isNotEmpty(),
        )
    }

    override suspend fun customerInfo(): VxCustomerInfo? {
        if (!Purchases.isConfigured) return null
        return try {
            Purchases.sharedInstance.awaitCustomerInfo().toVx()
        } catch (e: PurchasesException) {
            null
        }
    }

    /**
     * Google Play only offers a free trial or intro price to users eligible for it, and RevenueCat's
     * default option is the best offer the user can take — so a trial or intro phase on it *is* the
     * eligibility answer iOS gets from `checkTrialOrIntroDiscountEligibility`.
     */
    override suspend fun isTrialOrIntroEligible(product: VxStoreProduct): Boolean {
        val option = (product as? RevenueCatStoreProduct)?.revenueCatProduct?.defaultOption ?: return false
        return option.freePhase != null || option.introPhase != null
    }

    override suspend fun logIn(appUserId: String): VxCustomerInfo? =
        if (Purchases.isConfigured) Purchases.sharedInstance.awaitLogIn(appUserId).customerInfo.toVx() else null

    override suspend fun logOut() {
        if (Purchases.isConfigured) Purchases.sharedInstance.awaitLogOut()
    }

    override suspend fun syncPurchases(): VxCustomerInfo? =
        if (Purchases.isConfigured) Purchases.sharedInstance.awaitSyncPurchases().toVx() else null

    override fun syncAttributesAndOfferingsIfNeeded() {
        if (!Purchases.isConfigured) return
        scope.launch { runCatching { Purchases.sharedInstance.awaitSyncAttributesAndOfferingsIfNeeded() } }
    }

    override fun setAttributes(attributes: Map<String, String>): Unit = ifConfigured { setAttributes(attributes) }

    override fun setOneSignalId(id: String): Unit = ifConfigured { setOnesignalID(id) }

    override fun setFacebookAnonymousId(id: String): Unit = ifConfigured { setFBAnonymousID(id) }

    override fun setAppsflyerId(id: String): Unit = ifConfigured { setAppsflyerID(id) }

    override fun setFirebaseAppInstanceId(id: String): Unit = ifConfigured { setFirebaseAppInstanceID(id) }

    override fun setEmail(email: String?): Unit = ifConfigured { setEmail(email) }

    override fun setDisplayName(name: String?): Unit = ifConfigured { setDisplayName(name) }

    override fun collectDeviceIdentifiers(): Unit = ifConfigured { collectDeviceIdentifiers() }

    private inline fun ifConfigured(block: Purchases.() -> Unit) {
        if (Purchases.isConfigured) Purchases.sharedInstance.block()
    }
}

private fun CustomerInfo.toVx(): VxCustomerInfo = VxCustomerInfo(
    activeSubscriptions = activeSubscriptions,
    activeEntitlements = entitlements.active.keys,
    nonSubscriptionProductIds = nonSubscriptionTransactions.map { it.productIdentifier },
)
