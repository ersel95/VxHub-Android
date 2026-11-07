package com.vxhub.ui.paywall

import com.vxhub.purchases.VxIntroOffer
import com.vxhub.purchases.VxPeriod
import com.vxhub.purchases.VxPeriodUnit
import com.vxhub.purchases.VxProduct
import com.vxhub.purchases.VxProductType
import com.vxhub.purchases.VxStoreProduct

internal class FakeStoreProduct(
    override val id: String,
    override val period: VxPeriod?,
    override val priceAmountMicros: Long,
    override val formattedPrice: String,
    override val introOffer: VxIntroOffer? = null,
) : VxStoreProduct {
    override val productId: String get() = id.substringBefore(':')
    override val type: VxProductType get() = VxProductType.AUTO_RENEWABLE_SUBSCRIPTION
    override val title: String get() = id
    override val description: String get() = ""
    override val currencyCode: String get() = "USD"
    override val formattedPricePerWeek: String? get() = null
    override val formattedPricePerMonth: String? get() = null
}

internal fun product(id: String, unit: VxPeriodUnit, micros: Long, price: String, eligible: Boolean = false): VxProduct =
    VxProduct(FakeStoreProduct(id, VxPeriod(1, unit), micros, price), isTrialOrIntroEligible = eligible)

internal fun item(
    id: String = "premium:monthly",
    index: Int = 0,
    period: VxPeriodUnit? = VxPeriodUnit.MONTH,
    price: String = "\$9.99",
    trialDays: Int? = null,
    eligible: Boolean = trialDays != null,
    weekly: String? = null,
    monthly: String? = null,
    daily: String? = null,
    comparedPeriod: VxPeriodUnit? = null,
    initiallySelected: Boolean = false,
    isBestOffer: Boolean = false,
    initialBonus: Int? = null,
    renewalBonus: Int? = null,
    product: VxProduct? = null,
): PaywallItem = PaywallItem(
    index = index,
    id = id,
    product = product,
    localizedPrice = price,
    weeklyPrice = weekly,
    monthlyPrice = monthly,
    dailyPrice = daily,
    period = period,
    freeTrialDays = trialDays,
    initiallySelected = initiallySelected,
    isEligibleForTrial = eligible,
    comparedPeriod = comparedPeriod,
    isBestOffer = isBestOffer,
    initialBonus = initialBonus,
    renewalBonus = renewalBonus,
)
