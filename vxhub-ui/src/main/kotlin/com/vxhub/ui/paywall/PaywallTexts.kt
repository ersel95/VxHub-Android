package com.vxhub.ui.paywall

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.vxhub.purchases.VxPeriodUnit
import com.vxhub.ui.internal.paywall.PeriodText
import com.vxhub.ui.internal.replacingPlaceholder
import com.vxhub.ui.internal.vxString
import com.vxhub.ui.internal.withValues

/**
 * The texts the main paywalls compose from a product, built as iOS builds them. Templates come
 * from the panel under the iOS keys; the V3/V4 keys fall back to iOS's English when the panel
 * lacks them (iOS's `fallback(_:default:)`).
 */
internal object PaywallTexts {

    // region V1 / V2 rows (iOS VxMainPaywallTableViewCell)

    /** The row title — iOS's `generateProductDescriptionTitle`. */
    fun classicTitle(item: PaywallItem, type: VxMainPaywallType): String {
        if (type == VxMainPaywallType.V1 && item.isEligibleForTrial) {
            val days = (item.freeTrialDays ?: 0).toString()
            return item.period?.let(PeriodText::option).orEmpty().replace("{xxxfreeTrial}", days)
        }
        val key = if (type == VxMainPaywallType.V2 && item.index == 0) "Subscription_NotEligibleOption1" else "Subscription_NotEligibleOption2"
        return vxString(key).replace("{xxxsubPeriod}", item.period?.let(PeriodText::period).orEmpty())
    }

    /** V1's second line for a product without an offer: "{price} {period}", the price in bold. */
    fun v1Subtitle(item: PaywallItem): AnnotatedString = fill(
        vxString("Subscription_FirstIndexSubDescrtiption"),
        Slot("{xxxPrice}", item.localizedPrice, Bold14),
        Slot("{xxxPeriod}", item.period?.let(PeriodText::periodPer).orEmpty(), Regular12),
    )

    /**
     * V1's price: the best offer's price per compared period ("$1.15" a week for a yearly plan), or
     * "then {price}" for a product with an offer — iOS's `generatePriceDescriptionTitle(.v1)`.
     */
    fun v1PriceTitle(item: PaywallItem): AnnotatedString {
        if (!item.isEligibleForTrial) {
            val period = comparedPeriod(item)
            return AnnotatedString(period?.let { pricePer(item, it) } ?: item.localizedPrice)
        }
        return buildAnnotatedString {
            withStyle(Regular12) { append(item.period?.let(PeriodText::thenPeriodly).orEmpty()) }
            withStyle(Bold14) { append(item.localizedPrice) }
        }
    }

    /** V1's "/ week" under the price, naming the period the price is for. */
    fun v1PriceSubtitle(item: PaywallItem): String {
        val period = if (item.isEligibleForTrial) item.period else comparedPeriod(item) ?: item.period
        return period?.let(PeriodText::periodPer).orEmpty()
    }

    /** The best offer's compared period, when its price is known. */
    private fun comparedPeriod(item: PaywallItem): VxPeriodUnit? = item.comparedPeriod?.takeIf { pricePer(item, it) != null }

    private fun pricePer(item: PaywallItem, period: VxPeriodUnit): String? = when (period) {
        VxPeriodUnit.DAY -> item.dailyPrice
        VxPeriodUnit.WEEK -> item.weeklyPrice
        VxPeriodUnit.MONTH -> item.monthlyPrice
        VxPeriodUnit.YEAR -> item.localizedPrice
    }

    /** V2's price: "{price} / {period}", the price in bold. */
    fun v2PriceTitle(item: PaywallItem): AnnotatedString = buildAnnotatedString {
        withStyle(Bold12) { append(item.localizedPrice) }
        withStyle(Regular12) { append(" / " + singlePeriod(item)) }
    }

    /** V2's weekly equivalent of a monthly or yearly price: "($1.15 / week)". */
    fun v2PriceSubtitle(item: PaywallItem): String? {
        val weekly = item.weeklyPrice ?: return null
        if (item.period != VxPeriodUnit.MONTH && item.period != VxPeriodUnit.YEAR) return null
        return "($weekly / ${vxString("Subscription_SinglePeriodWeekText")})"
    }

    /** V2's coin line: "Get 500 coins" on the first product, "+500 bonus coins" on the others. */
    fun v2BonusText(item: PaywallItem): String? {
        val bonus = item.initialBonus?.takeIf { it != 0 } ?: return null
        val key = if (item.index == 0) "Subscription_PriceTitleWithInitialBonus1" else "Subscription_PriceTitleWithInitialBonus2"
        return vxString(key).replacingPlaceholder(bonus.toString())
    }

    /** V2's note about the coins every renewal of the selected product grants. */
    fun v2RenewalText(bonus: Int): String = vxString("Subscription_V2_RecurringCoinDescriptionLabel").withValues(bonus)

    fun bestOfferBadge(): String = vxString("Subscription_Discount")

    // endregion

    // region V3 / V4

    /** V3's accent-coloured trial label: "FREE for 1 Week". */
    fun v3FreeTrialLabel(item: PaywallItem): String? =
        if (item.hasFreeTrial) trialText(item, "Subscription_V3_FreeForDaysLabel", "FREE for {xxxTrialDuration}") else null

    /** V3's price line: "then $9.99/month". */
    fun v3PriceAfterTrial(item: PaywallItem): String = vxString("Subscription_V3_ThenPriceLabel", "then {xxxPrice}/{xxxPeriod}")
        .replace("{xxxPrice}", item.localizedPrice)
        .replace("{xxxPeriod}", singlePeriod(item))

    fun v3PerMonth(item: PaywallItem): String? = perMonth(item, "Subscription_V3_PerMonthLabel")

    /** V3's call to action: "Try Free for 1 Week", or "Subscribe" without a trial. */
    fun v3CallToAction(selected: PaywallItem?): String =
        if (selected != null && selected.hasFreeTrial) {
            trialText(selected, "Subscription_V3_TryFreeButtonLabel", "Try Free for {xxxTrialDuration}")
        } else {
            vxString("Subscription_SubscribeButtonLabel", "Subscribe")
        }

    fun bestValueBadge(): String = vxString("Subscription_V3_BestValueBadge", "BEST VALUE")

    /** V4's most prominent text: the billed price, "$9.99/month". */
    fun v4BilledPrice(item: PaywallItem): String = item.localizedPrice + "/" + singlePeriod(item)

    /**
     * V4's subordinate second line: the monthly equivalent of a yearly plan and the trial, e.g.
     * "$4.17/mo · Includes 1 Week free trial"; null hides the line.
     */
    fun v4Details(item: PaywallItem): String? {
        val perMonth = perMonth(item, "Subscription_V4_PerMonthLabel")
        if (!item.hasFreeTrial) return perMonth
        val trial = trialText(item, "Subscription_V4_TrialIncludedLabel", "Includes {xxxTrialDuration} free trial")
        return listOfNotNull(perMonth, trial).joinToString(" · ")
    }

    /**
     * V4's call to action, which always states the billed price: "Start Free Trial — then
     * $9.99/month" or "Subscribe for $9.99/month". Empty without a product, as iOS leaves it.
     */
    fun v4CallToAction(selected: PaywallItem?): String {
        val item = selected ?: return ""
        val template = if (item.hasFreeTrial) {
            vxString("Subscription_V4_CTATrialLabel", "Start Free Trial — then {xxxPrice}/{xxxPeriod}")
        } else {
            vxString("Subscription_V4_CTASubscribeLabel", "Subscribe for {xxxPrice}/{xxxPeriod}")
        }
        return template.replace("{xxxPrice}", item.localizedPrice).replace("{xxxPeriod}", singlePeriod(item))
    }

    // endregion

    /**
     * iOS's `trialDurationString(days:)`: 7 → "1 Week", 30 → "1 Month", 3 → "3 Days". In English,
     * as iOS hard-codes it.
     */
    fun trialDuration(days: Int): String = when {
        days >= 365 && days % 365 == 0 -> count(days / 365, "Year")
        days >= 28 && days % 30 == 0 -> count(days / 30, "Month")
        days >= 7 && days % 7 == 0 -> count(days / 7, "Week")
        else -> count(days, "Day")
    }

    private fun count(value: Int, unit: String): String = if (value == 1) "1 $unit" else "$value ${unit}s"

    private fun singlePeriod(item: PaywallItem): String = item.period?.let(PeriodText::singlePeriod).orEmpty()

    /** "{price}/mo" for a yearly plan. */
    private fun perMonth(item: PaywallItem, key: String): String? {
        val monthly = item.monthlyPrice ?: return null
        if (item.period != VxPeriodUnit.YEAR) return null
        return vxString(key, "{xxxPrice}/mo").replace("{xxxPrice}", monthly)
    }

    private fun trialText(item: PaywallItem, key: String, default: String): String =
        vxString(key, default).replace("{xxxTrialDuration}", trialDuration(item.freeTrialDays ?: 0))

    private class Slot(val placeholder: String, val text: String, val style: SpanStyle)

    /** Fills [template]'s placeholders, each value in its own style (iOS's attributed replacements). */
    private fun fill(template: String, vararg slots: Slot): AnnotatedString = buildAnnotatedString {
        var cursor = 0
        while (true) {
            var next: Slot? = null
            var at = -1
            for (candidate in slots) {
                val index = template.indexOf(candidate.placeholder, cursor)
                if (index >= 0 && (at < 0 || index < at)) {
                    at = index
                    next = candidate
                }
            }
            val slot = next
            if (slot == null) {
                append(template.substring(cursor))
                return@buildAnnotatedString
            }
            append(template.substring(cursor, at))
            withStyle(slot.style) { append(slot.text) }
            cursor = at + slot.placeholder.length
        }
    }
}

/** How each of the five rating stars is drawn. */
internal enum class StarFill { FULL, HALF, EMPTY }

/** iOS's `setupStars`: five stars for [rating], with a half star from .5; unparsable shows 4.5. */
internal fun ratingStars(rating: String): List<StarFill> {
    val value = rating.trim().toDoubleOrNull() ?: 4.5
    return List(5) { index ->
        val threshold = index + 1.0
        when {
            value >= threshold -> StarFill.FULL
            value >= threshold - 0.5 -> StarFill.HALF
            else -> StarFill.EMPTY
        }
    }
}

/**
 * iOS's `VxLayoutHelper.adaptiveHeight`: scales a value designed for an 812-pt tall screen, a
 * quarter less on small screens (≤ 667) and a quarter more on large ones (≥ 926).
 */
internal fun adaptiveHeight(value: Float, screenHeight: Float): Float {
    val scaled = value * screenHeight / 812f
    return when {
        screenHeight <= 667f -> scaled * 0.75f
        screenHeight >= 926f -> scaled * 1.25f
        else -> scaled
    }
}

private val Regular12 = SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)
private val Bold12 = SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
private val Bold14 = SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
