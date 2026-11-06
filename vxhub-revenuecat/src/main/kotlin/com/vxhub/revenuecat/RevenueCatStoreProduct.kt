package com.vxhub.revenuecat

import com.revenuecat.purchases.ProductType
import com.revenuecat.purchases.models.OfferPaymentMode
import com.revenuecat.purchases.models.Period
import com.revenuecat.purchases.models.PricingPhase
import com.revenuecat.purchases.models.StoreProduct
import com.revenuecat.purchases.models.googleProduct
import com.vxhub.purchases.VxIntroOffer
import com.vxhub.purchases.VxPaymentMode
import com.vxhub.purchases.VxPeriod
import com.vxhub.purchases.VxPeriodUnit
import com.vxhub.purchases.VxProductType
import com.vxhub.purchases.VxStoreProduct
import java.util.Locale

/**
 * A RevenueCat product seen through VxHub's [VxStoreProduct] — iOS's `RevenueCatProductAdapter`.
 * [revenueCatProduct] is public so an app can hand it to RevenueCat APIs VxHub does not wrap.
 */
public class RevenueCatStoreProduct(public val revenueCatProduct: StoreProduct) : VxStoreProduct {

    override val id: String get() = revenueCatProduct.id

    override val productId: String
        get() = revenueCatProduct.googleProduct?.productId ?: revenueCatProduct.id.substringBefore(':')

    override val type: VxProductType
        get() = when (revenueCatProduct.type) {
            ProductType.SUBS ->
                if (revenueCatProduct.defaultOption?.isPrepaid == true) {
                    VxProductType.NON_RENEWING_SUBSCRIPTION
                } else {
                    VxProductType.AUTO_RENEWABLE_SUBSCRIPTION
                }
            // Play does not distinguish consumable from non-consumable; VxHubConfig does.
            ProductType.INAPP, ProductType.UNKNOWN -> VxProductType.CONSUMABLE
        }

    override val title: String get() = revenueCatProduct.title
    override val description: String get() = revenueCatProduct.description
    override val formattedPrice: String get() = revenueCatProduct.price.formatted
    override val priceAmountMicros: Long get() = revenueCatProduct.price.amountMicros
    override val currencyCode: String get() = revenueCatProduct.price.currencyCode

    override val period: VxPeriod? get() = revenueCatProduct.period?.toVx()

    override val introOffer: VxIntroOffer?
        get() {
            val option = revenueCatProduct.defaultOption ?: return null
            val phase = option.freePhase ?: option.introPhase ?: return null
            val period = phase.billingPeriod.toVx() ?: return null
            return VxIntroOffer(
                paymentMode = phase.paymentMode(),
                period = period,
                cycles = phase.billingCycleCount ?: 1,
                formattedPrice = phase.price.formatted,
                priceAmountMicros = phase.price.amountMicros,
            )
        }

    override val formattedPricePerWeek: String?
        get() = revenueCatProduct.period?.let { revenueCatProduct.pricePerWeek(Locale.getDefault())?.formatted }

    override val formattedPricePerMonth: String?
        get() = revenueCatProduct.period?.let { revenueCatProduct.pricePerMonth(Locale.getDefault())?.formatted }

    override fun equals(other: Any?): Boolean = other is RevenueCatStoreProduct && other.revenueCatProduct.id == revenueCatProduct.id

    override fun hashCode(): Int = revenueCatProduct.id.hashCode()

    override fun toString(): String = "RevenueCatStoreProduct($id, $formattedPrice)"
}

internal fun Period.toVx(): VxPeriod? {
    val unit = when (unit) {
        Period.Unit.DAY -> VxPeriodUnit.DAY
        Period.Unit.WEEK -> VxPeriodUnit.WEEK
        Period.Unit.MONTH -> VxPeriodUnit.MONTH
        Period.Unit.YEAR -> VxPeriodUnit.YEAR
        Period.Unit.UNKNOWN -> return null
    }
    return VxPeriod(value, unit)
}

private fun PricingPhase.paymentMode(): VxPaymentMode = when (offerPaymentMode) {
    OfferPaymentMode.FREE_TRIAL -> VxPaymentMode.FREE_TRIAL
    OfferPaymentMode.SINGLE_PAYMENT -> VxPaymentMode.PAY_UP_FRONT
    OfferPaymentMode.DISCOUNTED_RECURRING_PAYMENT -> VxPaymentMode.PAY_AS_YOU_GO
    null -> if (price.amountMicros == 0L) VxPaymentMode.FREE_TRIAL else VxPaymentMode.PAY_AS_YOU_GO
}
