package com.vxhub.ui.store

import com.vxhub.purchases.VxIntroOffer
import com.vxhub.purchases.VxPeriod
import com.vxhub.purchases.VxPeriodUnit
import com.vxhub.purchases.VxProduct
import com.vxhub.purchases.VxProductType
import com.vxhub.purchases.VxStoreProduct

/** A store product for tests: one-time by default, with the price given in whole units. */
internal class FakeStoreProduct(
    override val id: String,
    override val type: VxProductType = VxProductType.CONSUMABLE,
    price: Double = 1.0,
    override val productId: String = id.substringBefore(':'),
    override val title: String = "Title of $id",
    override val description: String = "Description of $id",
    override val formattedPrice: String = "$%.2f".format(java.util.Locale.US, price),
    override val currencyCode: String = "USD",
    override val period: VxPeriod? = if (type == VxProductType.AUTO_RENEWABLE_SUBSCRIPTION) VxPeriod(1, VxPeriodUnit.YEAR) else null,
    override val introOffer: VxIntroOffer? = null,
    override val formattedPricePerWeek: String? = null,
    override val formattedPricePerMonth: String? = null,
) : VxStoreProduct {
    override val priceAmountMicros: Long = Math.round(price * 1_000_000)
}

internal fun fakeProduct(
    id: String,
    type: VxProductType = VxProductType.CONSUMABLE,
    price: Double = 1.0,
    initialBonus: Int? = null,
    period: VxPeriod? = if (type == VxProductType.AUTO_RENEWABLE_SUBSCRIPTION) VxPeriod(1, VxPeriodUnit.YEAR) else null,
): VxProduct = VxProduct(
    storeProduct = FakeStoreProduct(id = id, type = type, price = price, period = period),
    isTrialOrIntroEligible = false,
    initialBonus = initialBonus,
)
