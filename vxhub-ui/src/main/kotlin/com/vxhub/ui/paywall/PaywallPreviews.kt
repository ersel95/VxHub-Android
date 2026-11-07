package com.vxhub.ui.paywall

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.vxhub.purchases.VxPeriodUnit

// Fake rows: previews never touch the store. Prices are what a US account would see.

private fun previewItem(
    index: Int,
    id: String,
    price: String,
    period: VxPeriodUnit,
    weekly: String? = null,
    monthly: String? = null,
    trialDays: Int? = null,
    comparedPeriod: VxPeriodUnit? = null,
    initialBonus: Int? = null,
    renewalBonus: Int? = null,
): PaywallItem = PaywallItem(
    index = index,
    id = id,
    product = null,
    localizedPrice = price,
    weeklyPrice = weekly,
    monthlyPrice = monthly,
    dailyPrice = null,
    period = period,
    freeTrialDays = trialDays,
    initiallySelected = index == 0,
    isEligibleForTrial = trialDays != null,
    comparedPeriod = comparedPeriod,
    isBestOffer = index == 0,
    initialBonus = initialBonus,
    renewalBonus = renewalBonus,
)

private val ClassicItems = listOf(
    previewItem(0, "premium:yearly", "\$59.99", VxPeriodUnit.YEAR, weekly = "\$1.15", monthly = "\$5.00", comparedPeriod = VxPeriodUnit.WEEK, initialBonus = 500, renewalBonus = 100),
    previewItem(1, "premium:weekly", "\$6.99", VxPeriodUnit.WEEK, weekly = "\$6.99", trialDays = 3, initialBonus = 50),
)

private val HeroItems = listOf(
    previewItem(0, "premium:yearly", "\$59.99", VxPeriodUnit.YEAR, weekly = "\$1.15", monthly = "\$5.00", trialDays = 7),
    previewItem(1, "premium:monthly", "\$9.99", VxPeriodUnit.MONTH, weekly = "\$2.31", monthly = "\$9.99"),
    previewItem(2, "premium:weekly", "\$3.99", VxPeriodUnit.WEEK, weekly = "\$3.99", trialDays = 3),
)

private val PreviewFeatures = listOf(
    VxPaywallFeature("Unlimited scans"),
    VxPaywallFeature("[b]No ads[/b], ever"),
    VxPaywallFeature("Sync across all your devices"),
)

@Preview(name = "V1", widthDp = 390, heightDp = 844)
@Composable
private fun PaywallV1Preview() {
    PaywallScreen(
        configuration = VxMainPaywallConfiguration(
            paywallType = VxMainPaywallType.V1,
            descriptionItems = PreviewFeatures,
            mainButtonColor = Color(0xFF148CBE),
        ),
        state = PaywallUiState(items = ClassicItems, selectedId = "premium:yearly"),
        showsCloseButton = true,
        actions = PaywallActions.None,
    )
}

@Preview(name = "V2, dark", widthDp = 390, heightDp = 844)
@Composable
private fun PaywallV2Preview() {
    PaywallScreen(
        configuration = VxMainPaywallConfiguration(
            paywallType = VxMainPaywallType.V2,
            descriptionItems = PreviewFeatures,
            backgroundColor = Color(0xFF15101C),
            isLightMode = false,
            closeButtonColor = Color.White,
        ),
        state = PaywallUiState(items = ClassicItems, selectedId = "premium:yearly"),
        showsCloseButton = true,
        actions = PaywallActions.None,
    )
}

@Preview(name = "V3", widthDp = 390, heightDp = 844)
@Composable
private fun PaywallV3Preview() {
    PaywallScreen(
        configuration = VxMainPaywallV3Configuration(
            featureItems = PreviewFeatures,
            ratingValue = "4.8",
            ratingCount = "12.5K",
        ),
        state = PaywallUiState(items = HeroItems, selectedId = "premium:yearly"),
        showsCloseButton = true,
        actions = PaywallActions.None,
    )
}

@Preview(name = "V4, dark, purchasing", widthDp = 390, heightDp = 844)
@Composable
private fun PaywallV4Preview() {
    PaywallScreen(
        configuration = VxMainPaywallV4Configuration(
            backgroundColor = Color(0xFF0E0E10),
            isLightMode = false,
            featureItems = PreviewFeatures,
            ratingValue = "4.5",
            ctaButtonColor = Color(0xFF5E5CE6),
            ctaGradientEndColor = Color(0xFFBF5AF2),
        ),
        state = PaywallUiState(items = HeroItems, selectedId = "premium:monthly", isLoading = true),
        showsCloseButton = false,
        actions = PaywallActions.None,
    )
}
