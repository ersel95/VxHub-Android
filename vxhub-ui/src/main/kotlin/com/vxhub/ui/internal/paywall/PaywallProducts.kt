package com.vxhub.ui.internal.paywall

import com.vxhub.VxHub
import com.vxhub.purchases.VxPaymentMode
import com.vxhub.purchases.VxPeriodUnit
import com.vxhub.purchases.VxProduct
import com.vxhub.purchases.VxProductType
import com.vxhub.ui.internal.replacingPlaceholder
import com.vxhub.ui.internal.vxString
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Which experiment decides a screen's products — iOS's `VxSubscriptionPageTypes`. */
internal enum class PaywallPage(val experimentKey: String) {
    MAIN_PAYWALL("main_paywall"),
    WELCOME_OFFER("welcome_offer"),
    ALL("all_available_products_9999"),
}

/**
 * One product row as the paywalls and the promo offer draw it — iOS's `SubData`, plus the
 * [product] itself so the screen can buy it.
 */
internal data class PaywallProduct(
    val index: Int,
    val product: VxProduct,
    val identifier: String,
    val title: String,
    val description: String,
    val localizedPrice: String,
    val weeklyPrice: String?,
    val monthlyPrice: String?,
    val dailyPrice: String?,
    val period: VxPeriodUnit?,
    /** Unit of the free trial, when the intro offer is a free trial. */
    val freeTrialPeriod: VxPeriodUnit?,
    /** Length of the intro offer in days (iOS's `freeTrialUnit`). */
    val freeTrialDays: Int?,
    val initiallySelected: Boolean,
    /** How much cheaper than the most expensive product, in percent. */
    val discountAmount: Int?,
    val eligibleForFreeTrialOrDiscount: Boolean,
    val comparedPeriod: VxPeriodUnit?,
    val isBestOffer: Boolean,
    val initialBonus: Int?,
    val renewalBonus: Int?,
    val productType: VxProductType,
    val nonDiscountedPrice: String?,
    val price: BigDecimal,
) {
    val hasFreeTrial: Boolean get() = eligibleForFreeTrialOrDiscount && freeTrialPeriod != null && (freeTrialDays ?: 0) > 0
}

/** An experiment payload: which products a page shows. iOS's `ExperimentPayload`. */
internal data class ExperimentPayload(
    val product: String?,
    val products: List<String>?,
    val nonDiscountedProductId: String?,
    val selectedIndex: Int?,
) {
    companion object {
        fun from(map: Map<String, Any?>?): ExperimentPayload? {
            if (map.isNullOrEmpty()) return null
            return ExperimentPayload(
                product = map["product"] as? String,
                products = (map["products"] as? List<*>)?.mapNotNull { it as? String },
                nonDiscountedProductId = map["non_discounted_product_id"] as? String,
                selectedIndex = (map["selectedIndex"] as? Number)?.toInt(),
            )
        }
    }
}

/**
 * Builds the rows of a paywall from [VxHub.products] — the port of iOS's `VxPaywallUtil`: the
 * page's Amplitude experiment picks the products (all auto-renewing subscriptions otherwise), the
 * main paywall lists longest period first, marks the first as best offer and pre-selects the
 * remote config's `subscription_selected_product_identifier`.
 */
internal object PaywallProducts {

    fun build(
        page: PaywallPage,
        products: List<VxProduct> = VxHub.products.value,
        payload: ExperimentPayload? = ExperimentPayload.from(VxHub.getVariantPayload(page.experimentKey)),
        initiallySelectedId: String? = VxHub.remoteConfig.value.getString("subscription_selected_product_identifier"),
        locale: Locale = Locale.getDefault(),
    ): List<PaywallProduct> {
        val renewable = products.filter { it.type == VxProductType.AUTO_RENEWABLE_SUBSCRIPTION }
        val wanted: List<VxProduct> = when {
            payload?.product != null -> renewable.filter { it.matches(payload.product) }
            payload?.products != null -> renewable.filter { p -> payload.products.any { p.matches(it) } }
            else -> renewable
        }
        if (wanted.isEmpty()) return emptyList()

        val maxPrice = wanted.maxOf { it.price }
        val nonDiscountedPrice = payload?.nonDiscountedProductId?.let { id ->
            products.firstOrNull { it.matches(id) }?.storeProduct?.formattedPrice
        }

        var rows = wanted.mapIndexed { index, product ->
            val store = product.storeProduct
            val price = product.price
            val period = store.period?.unit
            val intro = store.introOffer
            val discount = if (price < maxPrice && maxPrice > BigDecimal.ZERO) {
                maxPrice.subtract(price).divide(maxPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100)).toInt()
            } else {
                null
            }
            PaywallProduct(
                index = index,
                product = product,
                identifier = store.id,
                title = store.title,
                description = store.description,
                localizedPrice = store.formattedPrice,
                weeklyPrice = store.formattedPricePerWeek,
                monthlyPrice = if (period == VxPeriodUnit.YEAR) {
                    format(price.divide(BigDecimal(12), 6, RoundingMode.HALF_UP), store.currencyCode, locale)
                } else {
                    store.formattedPricePerMonth
                },
                dailyPrice = when (period) {
                    VxPeriodUnit.YEAR -> format(price.divide(BigDecimal(365), 6, RoundingMode.HALF_UP), store.currencyCode, locale)
                    VxPeriodUnit.MONTH -> format(price.divide(BigDecimal(30), 6, RoundingMode.HALF_UP), store.currencyCode, locale)
                    VxPeriodUnit.WEEK -> format(price.divide(BigDecimal(7), 6, RoundingMode.HALF_UP), store.currencyCode, locale)
                    else -> null
                },
                period = period,
                freeTrialPeriod = intro?.takeIf { it.paymentMode == VxPaymentMode.FREE_TRIAL }?.period?.unit,
                freeTrialDays = intro?.let { it.period.approximateDays * it.cycles.coerceAtLeast(1) },
                initiallySelected = false,
                discountAmount = discount,
                eligibleForFreeTrialOrDiscount = product.isTrialOrIntroEligible,
                comparedPeriod = null,
                isBestOffer = false,
                initialBonus = product.initialBonus,
                renewalBonus = product.renewalBonus,
                productType = product.type,
                nonDiscountedPrice = nonDiscountedPrice,
                price = price,
            )
        }

        if (page == PaywallPage.MAIN_PAYWALL) {
            rows = rows.sortedByDescending { it.period?.ordinal ?: -1 }
            val selected = rows.indexOfFirst { initiallySelectedId != null && it.product.matches(initiallySelectedId) }
                .takeIf { it >= 0 } ?: 0
            val comparedPeriod = rows.last().period ?: VxPeriodUnit.YEAR
            rows = rows.mapIndexed { i, row ->
                row.copy(
                    initiallySelected = i == selected,
                    isBestOffer = i == 0,
                    comparedPeriod = if (i == 0) comparedPeriod else null,
                )
            }
        }
        return rows
    }

    fun format(amount: BigDecimal, currencyCode: String, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getCurrencyInstance(locale).apply {
            runCatching { currency = Currency.getInstance(currencyCode) }
        }.format(amount)

    private val VxProduct.price: BigDecimal
        get() = BigDecimal.valueOf(storeProduct.priceAmountMicros).divide(BigDecimal(1_000_000), 6, RoundingMode.HALF_UP)

    /** The panel and experiments may name a Play subscription by `productId` or `productId:basePlanId`. */
    private fun VxProduct.matches(identifier: String): Boolean =
        storeProduct.id == identifier || storeProduct.productId == identifier
}

/** Paywall texts per period unit — iOS's `SubPreiod` computed properties, same keys. */
internal object PeriodText {
    fun content(unit: VxPeriodUnit): String = pick(unit, "Subscription_WelcomeOffer", "Text", daily = "Daily", weekly = "Weekly", monthly = "Monthly", yearly = "Yearly")

    fun notEligibleContent(unit: VxPeriodUnit): String =
        pick(unit, "Subscription_NotEligibleWelcomeOffer", "Text", daily = "Daily", weekly = "Weekly", monthly = "Monthly", yearly = "Yearly")

    fun option(unit: VxPeriodUnit): String = pick(unit, "Subscription_", "OfferOptionText", daily = "Daily", weekly = "Weekly", monthly = "Monthly", yearly = "Yearly")

    fun thenPeriodly(unit: VxPeriodUnit): String = pick(unit, "Subscription_", "ThenText", daily = "Daily", weekly = "Weekly", monthly = "Monthly", yearly = "Yearly")

    /** "/ day", "/ week", "/ month", or the yearly "just" text. */
    fun justPeriod(unit: VxPeriodUnit): String = when (unit) {
        VxPeriodUnit.YEAR -> vxString("Subscription_YearlyJustText")
        else -> periodPer(unit)
    }

    /** "/ day", "/ week", "/ month", "/ year". */
    fun periodPer(unit: VxPeriodUnit): String = pick(unit, "Subscription_", "PerText", daily = "Daily", weekly = "Weekly", monthly = "Monthly", yearly = "Yearly")

    /** "Daily", "Weekly", "Monthly", "Yearly". */
    fun period(unit: VxPeriodUnit): String = pick(unit, "Subscription_Period", "Text", daily = "Daily", weekly = "Weekly", monthly = "Monthly", yearly = "Yearly")

    /** "day", "week", "month", "year". */
    fun singlePeriod(unit: VxPeriodUnit): String = pick(unit, "Subscription_SinglePeriod", "Text", daily = "Day", weekly = "Week", monthly = "Month", yearly = "Year")

    /** "{n} days free trial" and friends; singular for a non-positive count, as iOS. */
    fun freeTrial(unit: VxPeriodUnit, value: Int): String {
        val name = when (unit) {
            VxPeriodUnit.DAY -> "Day"
            VxPeriodUnit.WEEK -> "Week"
            VxPeriodUnit.MONTH -> "Month"
            VxPeriodUnit.YEAR -> "Year"
        }
        val key = if (value <= 0) "Subscription_FreeTrial$name" else "Subscription_FreeTrialMultiple$name"
        return vxString(key).replacingPlaceholder(value.toString())
    }

    private fun pick(
        unit: VxPeriodUnit,
        prefix: String,
        suffix: String,
        daily: String,
        weekly: String,
        monthly: String,
        yearly: String,
    ): String = vxString(
        prefix + when (unit) {
            VxPeriodUnit.DAY -> daily
            VxPeriodUnit.WEEK -> weekly
            VxPeriodUnit.MONTH -> monthly
            VxPeriodUnit.YEAR -> yearly
        } + suffix,
    )
}
