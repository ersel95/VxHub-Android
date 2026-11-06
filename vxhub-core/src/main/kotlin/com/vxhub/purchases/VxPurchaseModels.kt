package com.vxhub.purchases

/** Kind of store product. Same cases as iOS's `VxStoreProductType`. */
public enum class VxProductType {
    CONSUMABLE,
    NON_CONSUMABLE,
    NON_RENEWING_SUBSCRIPTION,
    AUTO_RENEWABLE_SUBSCRIPTION,
}

public enum class VxPeriodUnit { DAY, WEEK, MONTH, YEAR }

public data class VxPeriod(val value: Int, val unit: VxPeriodUnit) {
    /** Approximate length in days (months are 30 days, years 365), for per-week/per-month prices. */
    val approximateDays: Int
        get() = value * when (unit) {
            VxPeriodUnit.DAY -> 1
            VxPeriodUnit.WEEK -> 7
            VxPeriodUnit.MONTH -> 30
            VxPeriodUnit.YEAR -> 365
        }
}

public enum class VxPaymentMode { PAY_AS_YOU_GO, PAY_UP_FRONT, FREE_TRIAL }

/** The free trial or introductory price a subscription starts with. */
public data class VxIntroOffer(
    val paymentMode: VxPaymentMode,
    /** Length of one billing cycle of the offer. */
    val period: VxPeriod,
    /** How many cycles the offer lasts. */
    val cycles: Int,
    /** Price per cycle, formatted for the store's locale; "Free" style text for a trial. */
    val formattedPrice: String,
    val priceAmountMicros: Long,
)

/**
 * A product as the store describes it. Implemented by the purchase provider module
 * (`vxhub-revenuecat`); apps read it through [VxProduct.storeProduct].
 */
public interface VxStoreProduct {
    /** The id purchases are made with. For a Play subscription: `productId:basePlanId`. */
    public val id: String

    /** The Play Console product id (no base plan). */
    public val productId: String
    public val type: VxProductType
    public val title: String
    public val description: String
    public val formattedPrice: String
    public val priceAmountMicros: Long
    public val currencyCode: String

    /** Billing period of a subscription; null for one-time products. */
    public val period: VxPeriod?

    /** The trial or intro price of the default offer the user is eligible for. */
    public val introOffer: VxIntroOffer?
    public val formattedPricePerWeek: String?
    public val formattedPricePerMonth: String?
}

/**
 * A store product VxHub offers, joined with what the panel says about it. Populated in
 * [com.vxhub.VxHub.products] after initialization. Public so apps can build fakes for previews
 * and tests.
 */
public class VxProduct(
    public val storeProduct: VxStoreProduct,
    /** The user can still take the product's free trial or intro price. */
    public val isTrialOrIntroEligible: Boolean,
    /** Coins granted with the first purchase. */
    public val initialBonus: Int? = null,
    /** Coins granted with every renewal. */
    public val renewalBonus: Int? = null,
    /**
     * Google Play does not tell consumable and non-consumable one-time products apart, so a
     * one-time product is [VxProductType.CONSUMABLE] unless listed in
     * [com.vxhub.VxHubConfig.nonConsumableProductIds].
     */
    public val type: VxProductType = storeProduct.type,
) {
    public val id: String get() = storeProduct.id

    override fun toString(): String =
        "VxProduct(${storeProduct.id}, ${storeProduct.formattedPrice}, eligible=$isTrialOrIntroEligible)"
}

/** Outcome of [com.vxhub.VxHub.purchase]. */
public sealed interface VxPurchaseResult {
    /** The backend verified the transaction. [isPremium] is the backend's answer. */
    public data class Verified(val isPremium: Boolean) : VxPurchaseResult

    /**
     * The store charged the user but the backend could not verify it yet. The transaction is
     * queued and re-sent on every start until verified.
     */
    public data object PendingVerification : VxPurchaseResult

    /** Google Play accepted the order but the payment has not cleared (cash, bank transfer). */
    public data object PaymentPending : VxPurchaseResult

    /** The user closed the store sheet. */
    public data object Cancelled : VxPurchaseResult

    /** The store reported an error; nothing was charged. */
    public data class Failed(val message: String?) : VxPurchaseResult
}

/** Outcome of [com.vxhub.VxHub.restorePurchases]. */
public sealed interface VxRestoreResult {
    /** The backend re-verified the account. [isPremium] is the backend's answer. */
    public data class Restored(val isPremium: Boolean) : VxRestoreResult

    /** The store has no purchases for this Google account. */
    public data object NothingToRestore : VxRestoreResult

    /** The store found purchases but the backend could not verify them right now. */
    public data object PendingVerification : VxRestoreResult

    /** The store restore itself failed. */
    public data class Failed(val message: String?) : VxRestoreResult
}

/** What the store (not the backend) said about a purchase attempt. Returned by providers. */
public sealed interface VxStorePurchaseOutcome {
    /**
     * @property transactionId Google Play's order id (`GPA.…`); the purchase token when Play
     *   returned no order id. Sent to `device/after-purchase`.
     */
    public data class Purchased(val transactionId: String, val productId: String) : VxStorePurchaseOutcome
    public data object PaymentPending : VxStorePurchaseOutcome
    public data object Cancelled : VxStorePurchaseOutcome
    public data class Failed(val message: String?) : VxStorePurchaseOutcome
}

/** What a store restore found. */
public data class VxRestoreSnapshot(
    val hasActiveSubscription: Boolean,
    val hasNonSubscriptionPurchases: Boolean,
)

/** The store's view of the customer. */
public data class VxCustomerInfo(
    val activeSubscriptions: Set<String>,
    val activeEntitlements: Set<String>,
    val nonSubscriptionProductIds: List<String>,
)
