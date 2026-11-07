package com.vxhub.ui

/**
 * Amplitude events a ready-made screen logs when listed in its configuration — iOS's shared
 * `AnalyticEvents`, used by the paywalls, the promo offer and the store alike.
 */
public enum class VxAnalyticsEvent(public val eventName: String) {
    /**
     * "Select", with `product_identifier` and `page_name` (`subscription_landing` on a paywall,
     * `store` in the store), each time the user picks a product.
     */
    SELECT("Select"),

    /** Accepted for parity with iOS, whose screens never log it either. */
    PURCHASED("Purchased"),
}
