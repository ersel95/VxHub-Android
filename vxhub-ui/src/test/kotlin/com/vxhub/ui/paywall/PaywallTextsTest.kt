package com.vxhub.ui.paywall

import androidx.compose.ui.text.font.FontWeight
import com.google.common.truth.Truth.assertThat
import com.vxhub.purchases.VxPeriodUnit
import org.junit.Test

// Texts resolve through VxHub.localize, which without an initialized SDK falls back to the
// bundled English (iOS's Localizable.strings) and then to the iOS defaults of the V3/V4 keys.
class PaywallTextsTest {

    @Test
    fun `trial duration names whole years, months and weeks as iOS does`() {
        assertThat(PaywallTexts.trialDuration(1)).isEqualTo("1 Day")
        assertThat(PaywallTexts.trialDuration(3)).isEqualTo("3 Days")
        assertThat(PaywallTexts.trialDuration(7)).isEqualTo("1 Week")
        assertThat(PaywallTexts.trialDuration(14)).isEqualTo("2 Weeks")
        assertThat(PaywallTexts.trialDuration(28)).isEqualTo("4 Weeks")
        assertThat(PaywallTexts.trialDuration(30)).isEqualTo("1 Month")
        assertThat(PaywallTexts.trialDuration(90)).isEqualTo("3 Months")
        assertThat(PaywallTexts.trialDuration(365)).isEqualTo("1 Year")
        assertThat(PaywallTexts.trialDuration(730)).isEqualTo("2 Years")
        assertThat(PaywallTexts.trialDuration(10)).isEqualTo("10 Days")
    }

    @Test
    fun `V4 call to action always states the billed price`() {
        assertThat(PaywallTexts.v4CallToAction(item(price = "\$9.99", trialDays = 7)))
            .isEqualTo("Start Free Trial — then \$9.99/month")
        assertThat(PaywallTexts.v4CallToAction(item(period = VxPeriodUnit.YEAR, price = "\$59.99")))
            .isEqualTo("Subscribe for \$59.99/year")
        // A trial the user cannot take is not offered.
        assertThat(PaywallTexts.v4CallToAction(item(trialDays = 7, eligible = false)))
            .isEqualTo("Subscribe for \$9.99/month")
        assertThat(PaywallTexts.v4CallToAction(null)).isEmpty()
    }

    @Test
    fun `V4 card puts the billed price first and the trial in the second line`() {
        val yearly = item(period = VxPeriodUnit.YEAR, price = "\$59.99", monthly = "\$5.00", trialDays = 7)
        assertThat(PaywallTexts.v4BilledPrice(yearly)).isEqualTo("\$59.99/year")
        assertThat(PaywallTexts.v4Details(yearly)).isEqualTo("\$5.00/mo · Includes 1 Week free trial")
        assertThat(PaywallTexts.v4Details(item(period = VxPeriodUnit.YEAR, monthly = "\$5.00"))).isEqualTo("\$5.00/mo")
        assertThat(PaywallTexts.v4Details(item(monthly = "\$9.99", trialDays = 3))).isEqualTo("Includes 3 Days free trial")
        assertThat(PaywallTexts.v4Details(item(monthly = "\$9.99"))).isNull()
    }

    @Test
    fun `V3 call to action and card texts`() {
        val yearly = item(period = VxPeriodUnit.YEAR, price = "\$59.99", monthly = "\$5.00", trialDays = 3)
        assertThat(PaywallTexts.v3CallToAction(yearly)).isEqualTo("Try Free for 3 Days")
        assertThat(PaywallTexts.v3FreeTrialLabel(yearly)).isEqualTo("FREE for 3 Days")
        assertThat(PaywallTexts.v3PriceAfterTrial(yearly)).isEqualTo("then \$59.99/year")
        assertThat(PaywallTexts.v3PerMonth(yearly)).isEqualTo("\$5.00/mo")

        val monthly = item(monthly = "\$9.99")
        assertThat(PaywallTexts.v3CallToAction(monthly)).isEqualTo("Subscribe")
        assertThat(PaywallTexts.v3CallToAction(null)).isEqualTo("Subscribe")
        assertThat(PaywallTexts.v3FreeTrialLabel(monthly)).isNull()
        assertThat(PaywallTexts.v3PerMonth(monthly)).isNull()
    }

    @Test
    fun `V1 and V2 titles`() {
        val yearly = item(period = VxPeriodUnit.YEAR, index = 0)
        val weeklyTrial = item(period = VxPeriodUnit.WEEK, index = 1, trialDays = 3)
        assertThat(PaywallTexts.classicTitle(yearly, VxMainPaywallType.V1)).isEqualTo("Yearly Access")
        assertThat(PaywallTexts.classicTitle(weeklyTrial, VxMainPaywallType.V1)).isEqualTo("3-day free trial, then weekly")
        assertThat(PaywallTexts.classicTitle(yearly, VxMainPaywallType.V2)).isEqualTo("Yearly Plan")
        assertThat(PaywallTexts.classicTitle(weeklyTrial, VxMainPaywallType.V2)).isEqualTo("Weekly Access")
    }

    @Test
    fun `V1 quotes the best offer in its compared period`() {
        val yearly = item(period = VxPeriodUnit.YEAR, price = "\$59.99", weekly = "\$1.15", comparedPeriod = VxPeriodUnit.WEEK)
        assertThat(PaywallTexts.v1PriceTitle(yearly).text).isEqualTo("\$1.15")
        assertThat(PaywallTexts.v1PriceSubtitle(yearly)).isEqualTo("/ week")

        val subtitle = PaywallTexts.v1Subtitle(yearly)
        assertThat(subtitle.text).isEqualTo("\$59.99 / year")
        val bold = subtitle.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertThat(subtitle.text.substring(bold.start, bold.end)).isEqualTo("\$59.99")
    }

    @Test
    fun `V1 falls back to the full price when the compared price is unknown`() {
        val yearly = item(period = VxPeriodUnit.YEAR, price = "\$59.99", weekly = null, comparedPeriod = VxPeriodUnit.WEEK)
        assertThat(PaywallTexts.v1PriceTitle(yearly).text).isEqualTo("\$59.99")
        assertThat(PaywallTexts.v1PriceSubtitle(yearly)).isEqualTo("/ year")
    }

    @Test
    fun `V1 shows a trial product's price after the trial in its own period`() {
        val weekly = item(period = VxPeriodUnit.WEEK, price = "\$6.99", trialDays = 3, comparedPeriod = VxPeriodUnit.DAY)
        assertThat(PaywallTexts.v1PriceTitle(weekly).text).isEqualTo("then \$6.99")
        assertThat(PaywallTexts.v1PriceSubtitle(weekly)).isEqualTo("/ week")
    }

    @Test
    fun `V2 prices and coin texts`() {
        val yearly = item(period = VxPeriodUnit.YEAR, price = "\$59.99", weekly = "\$1.15", index = 0, initialBonus = 500)
        assertThat(PaywallTexts.v2PriceTitle(yearly).text).isEqualTo("\$59.99 / year")
        assertThat(PaywallTexts.v2PriceSubtitle(yearly)).isEqualTo("(\$1.15 / week)")
        assertThat(PaywallTexts.v2BonusText(yearly)).isEqualTo("Get 500 coins")

        val weekly = item(period = VxPeriodUnit.WEEK, weekly = "\$6.99", index = 1, initialBonus = 50)
        assertThat(PaywallTexts.v2PriceSubtitle(weekly)).isNull()
        assertThat(PaywallTexts.v2BonusText(weekly)).isEqualTo("+50 bonus coins")
        assertThat(PaywallTexts.v2BonusText(item(initialBonus = 0))).isNull()
    }

    @Test
    fun `rating stars round to halves`() {
        val four = listOf(StarFill.FULL, StarFill.FULL, StarFill.FULL, StarFill.FULL)
        assertThat(ratingStars("4.5")).isEqualTo(four + StarFill.HALF)
        assertThat(ratingStars("4.8")).isEqualTo(four + StarFill.HALF)
        assertThat(ratingStars("4")).isEqualTo(four + StarFill.EMPTY)
        assertThat(ratingStars("5.0")).isEqualTo(four + StarFill.FULL)
        assertThat(ratingStars("12.5K reviews")).isEqualTo(ratingStars("4.5"))
    }

    @Test
    fun `adaptive height scales like iOS's layout helper`() {
        assertThat(adaptiveHeight(42f, 812f)).isWithin(0.001f).of(42f)
        assertThat(adaptiveHeight(42f, 667f)).isWithin(0.001f).of(42f * 667f / 812f * 0.75f)
        assertThat(adaptiveHeight(42f, 932f)).isWithin(0.001f).of(42f * 932f / 812f * 1.25f)
    }
}
