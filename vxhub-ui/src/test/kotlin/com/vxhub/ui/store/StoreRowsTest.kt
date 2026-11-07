package com.vxhub.ui.store

import com.google.common.truth.Truth.assertThat
import com.vxhub.purchases.VxProduct
import com.vxhub.purchases.VxProductType
import org.junit.Test
import java.math.BigDecimal

class StoreRowsTest {

    private val coins100 = fakeProduct("coins_100", price = 0.99, initialBonus = 10)
    private val coins500 = fakeProduct("coins_500", price = 3.99)
    private val lifetime = fakeProduct("lifetime", type = VxProductType.NON_CONSUMABLE, price = 19.99)
    private val yearly = fakeProduct("premium:yearly", type = VxProductType.AUTO_RENEWABLE_SUBSCRIPTION, price = 29.99)

    private fun build(
        products: List<VxProduct>,
        configuration: VxStoreConfiguration = VxStoreV1Configuration(),
        owned: Set<String> = emptySet(),
        allowDummyProducts: Boolean = false,
    ) = StoreRows.build(configuration, products, owned, allowDummyProducts)

    @Test
    fun `sells consumables and non-consumables only, in VxHub's order`() {
        val rows = build(listOf(yearly, coins100, lifetime, coins500))

        assertThat(rows.isDummyMode).isFalse()
        assertThat(rows.rows.map { it.identifier }).containsExactly("coins_100", "lifetime", "coins_500").inOrder()
    }

    @Test
    fun `a row carries the store's texts, the price and the bonus`() {
        val row = build(listOf(coins100)).rows.single()

        assertThat(row.title).isEqualTo("Title of coins_100")
        assertThat(row.description).isEqualTo("Description of coins_100")
        assertThat(row.localizedPrice).isEqualTo("$0.99")
        assertThat(row.price).isEqualToIgnoringScale(BigDecimal("0.99"))
        assertThat(row.initialBonus).isEqualTo(10)
        assertThat(row.product).isSameInstanceAs(coins100)
    }

    @Test
    fun `an owned non-consumable is hidden by default`() {
        val rows = build(listOf(coins100, lifetime), owned = setOf("lifetime"))

        assertThat(rows.rows.map { it.identifier }).containsExactly("coins_100")
    }

    @Test
    fun `an owned non-consumable is marked when hiding is off`() {
        val rows = build(
            listOf(coins100, lifetime),
            configuration = VxStoreV2Configuration(hideAlreadyPurchasedNonConsumables = false),
            owned = setOf("lifetime"),
        )

        assertThat(rows.rows.map { it.identifier to it.isAlreadyPurchased })
            .containsExactly("coins_100" to false, "lifetime" to true).inOrder()
    }

    @Test
    fun `ownership never applies to consumables`() {
        val rows = build(listOf(coins100), owned = setOf("coins_100"))

        assertThat(rows.rows.single().isAlreadyPurchased).isFalse()
    }

    @Test
    fun `badges and images are matched by identifier, with the default image as fallback`() {
        val badge = VxStoreProductBadge("coins_500", "BEST VALUE")
        val image = VxStoreProductImage("coins_100", imageRes = 42)
        val rows = build(
            listOf(coins100, coins500),
            configuration = VxStoreV1Configuration(productBadges = listOf(badge), productImages = listOf(image)),
        )

        assertThat(rows.rows[0].badge).isNull()
        assertThat(rows.rows[0].image).isEqualTo(VxStoreImage.Resource(42))
        assertThat(rows.rows[1].badge).isSameInstanceAs(badge)
        assertThat(rows.rows[1].image).isSameInstanceAs(VxStoreImage.Bag)
    }

    @Test
    fun `without a default image a product has none`() {
        val rows = build(listOf(coins100), configuration = VxStoreV1Configuration(defaultProductImage = null))

        assertThat(rows.rows.single().image).isNull()
    }

    @Test
    fun `dummy products fill in only for a debuggable app without real products`() {
        val dummy = VxStoreDummyProduct("dummy_coins", "100 Coins", localizedPrice = "$0.99", price = BigDecimal("0.99"), initialBonus = 5)
        val configuration = VxStoreV1Configuration(dummyProducts = listOf(dummy))

        val debug = build(listOf(yearly), configuration, allowDummyProducts = true)
        assertThat(debug.isDummyMode).isTrue()
        assertThat(debug.rows.single().identifier).isEqualTo("dummy_coins")
        assertThat(debug.rows.single().product).isNull()

        assertThat(build(listOf(yearly), configuration, allowDummyProducts = false)).isEqualTo(StoreRows.EMPTY)
        assertThat(build(listOf(coins100), configuration, allowDummyProducts = true).isDummyMode).isFalse()
    }

    @Test
    fun `no product and no dummy means nothing to show`() {
        assertThat(build(emptyList(), allowDummyProducts = true).rows).isEmpty()
    }

    @Test
    fun `bonus line follows iOS's hard-coded text and only shows a positive bonus`() {
        assertThat(bonusText(showBonusLabel = true, initialBonus = 20)).isEqualTo("+20% Bonus")
        assertThat(bonusText(showBonusLabel = true, initialBonus = 0)).isNull()
        assertThat(bonusText(showBonusLabel = true, initialBonus = null)).isNull()
        assertThat(bonusText(showBonusLabel = false, initialBonus = 20)).isNull()
    }

    @Test
    fun `configuration defaults match iOS`() {
        val light = VxStoreV1Configuration()
        assertThat(light.columnsCount).isEqualTo(2)
        assertThat(light.purchaseMode).isEqualTo(VxStorePurchaseMode.PER_CARD)
        assertThat(light.textColor).isEqualTo(androidx.compose.ui.graphics.Color.Black)
        assertThat(light.ctaText).isEqualTo("Purchase")
        assertThat(light.balanceLabel).isEqualTo("Your Balance:")
        assertThat(light.purchasedBadgeText).isEqualTo("OWNED")
        assertThat(light.hideAlreadyPurchasedNonConsumables).isTrue()
        assertThat(light.balanceIcon).isSameInstanceAs(VxStoreImage.Coin)

        val dark = VxStoreV2Configuration(isLightMode = false)
        assertThat(dark.textColor).isEqualTo(androidx.compose.ui.graphics.Color.White)
        assertThat(dark.cardBackgroundColor).isEqualTo(StoreColors.secondarySystemBackground(isLightMode = false))
        assertThat(dark.socialProofIcon).isSameInstanceAs(VxStoreImage.Star)
        assertThat(dark.showProductDescription).isTrue()
    }
}
