package com.vxhub.ui.store

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.vxhub.purchases.VxProductType
import java.math.BigDecimal

private fun previewRow(
    identifier: String,
    title: String,
    price: String,
    bonus: Int? = null,
    type: VxProductType = VxProductType.CONSUMABLE,
    owned: Boolean = false,
    badge: VxStoreProductBadge? = null,
) = StoreRow(
    identifier = identifier,
    title = title,
    description = "Spend them on anything in the app.",
    localizedPrice = price,
    price = BigDecimal(price.removePrefix("$")),
    productType = type,
    initialBonus = bonus,
    renewalBonus = null,
    isAlreadyPurchased = owned,
    badge = badge,
    image = VxStoreImage.Bag,
    product = null,
)

private val previewRows = StoreRows(
    rows = listOf(
        previewRow("coins_100", "100 Coins", "$0.99"),
        previewRow("coins_500", "500 Coins", "$3.99", bonus = 10, badge = VxStoreProductBadge("coins_500", "POPULAR")),
        previewRow("coins_1200", "1200 Coins", "$7.99", bonus = 20),
        previewRow("no_ads", "Remove Ads", "$4.99", type = VxProductType.NON_CONSUMABLE, owned = true),
    ),
    isDummyMode = false,
)

@Preview(name = "Store V1, per card", widthDp = 393, heightDp = 852)
@Composable
private fun StoreV1Preview() {
    StoreScreen(
        configuration = VxStoreV1Configuration(
            headlineText = "Get [b]Coins[/b]",
            subtitleText = "Unlock more with every pack",
            hideAlreadyPurchasedNonConsumables = false,
        ),
        rows = previewRows,
        state = StoreUiState(purchasingId = "coins_500"),
        balance = 120,
        onClose = {},
        onSelect = {},
        onBuy = {},
        onDismissAlert = {},
    )
}

@Preview(name = "Store V2, select and buy, dark", widthDp = 393, heightDp = 852)
@Composable
private fun StoreV2Preview() {
    StoreScreen(
        configuration = VxStoreV2Configuration(
            backgroundColor = Color(0xFF0E0E12),
            isLightMode = false,
            headlineText = "Coin Store",
            socialProofText = "Trusted by 1M players",
            purchaseMode = VxStorePurchaseMode.SELECT_AND_BUY,
            productFeatures = mapOf("coins_1200" to listOf(VxStoreProductFeature(VxStoreImage.Star, "Best value per coin"))),
            ctaGradientEndColor = Color(0xFF8E44FF),
            hideAlreadyPurchasedNonConsumables = false,
        ),
        rows = previewRows.copy(isDummyMode = true),
        state = StoreUiState(selectedId = "coins_500"),
        balance = 120,
        onClose = {},
        onSelect = {},
        onBuy = {},
        onDismissAlert = {},
    )
}
