package com.vxhub.ui.paywall

import com.vxhub.purchases.VxPeriodUnit
import com.vxhub.purchases.VxProduct
import com.vxhub.ui.internal.paywall.PaywallPage
import com.vxhub.ui.internal.paywall.PaywallProduct
import com.vxhub.ui.internal.paywall.PaywallProducts

/**
 * One product row of the main paywalls — iOS's `VxMainSubscriptionDataSourceModel`.
 *
 * @property index position on screen (iOS numbers the rows after sorting them).
 * @property product what a purchase buys; null for the placeholder rows shown when no product
 *   loaded, which cannot be bought (iOS's dummy data).
 * @property freeTrialDays length of the intro offer in days — iOS's `freeTrialUnit`.
 * @property comparedPeriod set on the best offer: the period V1 quotes its price in.
 */
internal data class PaywallItem(
    val index: Int,
    val id: String,
    val product: VxProduct?,
    val localizedPrice: String,
    val weeklyPrice: String?,
    val monthlyPrice: String?,
    val dailyPrice: String?,
    val period: VxPeriodUnit?,
    val freeTrialDays: Int?,
    val initiallySelected: Boolean,
    val isEligibleForTrial: Boolean,
    val comparedPeriod: VxPeriodUnit?,
    val isBestOffer: Boolean,
    val initialBonus: Int?,
    val renewalBonus: Int?,
) {
    /** iOS's `freeTrialUnit > 0 && eligibleForFreeTrialOrDiscount == true`, which V3 and V4 test. */
    val hasFreeTrial: Boolean get() = (freeTrialDays ?: 0) > 0 && isEligibleForTrial
}

/** The main paywall's rows, longest period first; iOS's placeholder rows when none loaded. */
internal fun mainPaywallItems(products: List<VxProduct>): List<PaywallItem> {
    val rows = PaywallProducts.build(PaywallPage.MAIN_PAYWALL, products = products)
    if (rows.isEmpty()) return placeholderItems()
    return rows.mapIndexed { position, row -> row.toItem(position) }
}

private fun PaywallProduct.toItem(position: Int): PaywallItem = PaywallItem(
    index = position,
    id = identifier,
    product = product,
    localizedPrice = localizedPrice,
    weeklyPrice = weeklyPrice,
    monthlyPrice = monthlyPrice,
    dailyPrice = dailyPrice,
    period = period,
    freeTrialDays = freeTrialDays,
    initiallySelected = initiallySelected,
    isEligibleForTrial = eligibleForFreeTrialOrDiscount,
    comparedPeriod = comparedPeriod,
    isBestOffer = isBestOffer,
    initialBonus = initialBonus,
    renewalBonus = renewalBonus,
)

/** iOS's `getDummyData()`: what the paywall draws before products load. Nothing can be bought. */
internal fun placeholderItems(): List<PaywallItem> = listOf(
    PaywallItem(
        index = 0,
        id = "yearly_subscription",
        product = null,
        localizedPrice = PLACEHOLDER_PRICE,
        weeklyPrice = PLACEHOLDER_PRICE,
        monthlyPrice = PLACEHOLDER_PRICE,
        dailyPrice = PLACEHOLDER_PRICE,
        period = VxPeriodUnit.YEAR,
        freeTrialDays = null,
        initiallySelected = true,
        isEligibleForTrial = false,
        comparedPeriod = VxPeriodUnit.MONTH,
        isBestOffer = true,
        initialBonus = null,
        renewalBonus = null,
    ),
    PaywallItem(
        index = 1,
        id = "monthly_trial",
        product = null,
        localizedPrice = PLACEHOLDER_PRICE,
        weeklyPrice = PLACEHOLDER_PRICE,
        monthlyPrice = PLACEHOLDER_PRICE,
        dailyPrice = PLACEHOLDER_PRICE,
        period = VxPeriodUnit.MONTH,
        freeTrialDays = 3,
        initiallySelected = false,
        isEligibleForTrial = true,
        comparedPeriod = null,
        isBestOffer = false,
        initialBonus = null,
        renewalBonus = null,
    ),
)

private const val PLACEHOLDER_PRICE = "\$0.00"

/** The product a paywall opens with: the remote config's pick, else the first. */
internal fun initialSelection(items: List<PaywallItem>): PaywallItem? =
    items.firstOrNull { it.initiallySelected } ?: items.firstOrNull()

/** The product V1's free-trial switch selects when turned on (one with an offer) or off (one without). */
internal fun trialToggleTarget(items: List<PaywallItem>, enabled: Boolean): PaywallItem? =
    items.firstOrNull { it.isEligibleForTrial == enabled }

/** What a paywall screen draws. */
internal data class PaywallUiState(
    val items: List<PaywallItem> = emptyList(),
    val selectedId: String? = null,
    /** A purchase or restore is running: the button spins and the close button hides. */
    val isLoading: Boolean = false,
) {
    val selected: PaywallItem? get() = items.firstOrNull { it.id == selectedId } ?: initialSelection(items)

    /** V1's free-trial switch shows only when some products have an offer and some do not. */
    val isTrialSwitchVisible: Boolean
        get() = items.any { it.isEligibleForTrial } && !items.all { it.isEligibleForTrial }

    /** The switch mirrors the selected product, as on iOS. */
    val isTrialSwitchOn: Boolean get() = selected?.isEligibleForTrial == true

    /** New products keep the selection when the selected one is still offered. */
    fun withItems(newItems: List<PaywallItem>): PaywallUiState {
        val keep = newItems.any { it.id == selectedId }
        return copy(items = newItems, selectedId = if (keep) selectedId else initialSelection(newItems)?.id)
    }
}

/** What a paywall screen can ask for. */
internal class PaywallActions(
    val onSelect: (PaywallItem) -> Unit,
    val onTrialToggle: (Boolean) -> Unit,
    val onPurchase: () -> Unit,
    val onRestore: () -> Unit,
    val onTerms: () -> Unit,
    val onPrivacy: () -> Unit,
    val onRedeemCode: () -> Unit,
    val onClose: () -> Unit,
) {
    companion object {
        /** For previews. */
        val None: PaywallActions = PaywallActions({}, {}, {}, {}, {}, {}, {}, {})
    }
}
