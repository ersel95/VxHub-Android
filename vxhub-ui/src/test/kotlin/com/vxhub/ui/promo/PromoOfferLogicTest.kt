package com.vxhub.ui.promo

import com.google.common.truth.Truth.assertThat
import com.vxhub.purchases.VxPeriod
import com.vxhub.purchases.VxPeriodUnit
import com.vxhub.purchases.VxProduct
import com.vxhub.purchases.VxProductType
import com.vxhub.ui.internal.paywall.PaywallPage
import com.vxhub.ui.internal.paywall.PaywallProduct
import com.vxhub.ui.internal.paywall.PaywallProducts
import com.vxhub.ui.store.fakeProduct
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class PromoOfferLogicTest {

    private fun subscription(id: String, price: Double, unit: VxPeriodUnit = VxPeriodUnit.YEAR): VxProduct =
        fakeProduct(id, type = VxProductType.AUTO_RENEWABLE_SUBSCRIPTION, price = price, period = VxPeriod(1, unit))

    private fun rows(vararg products: VxProduct): List<PaywallProduct> =
        PaywallProducts.build(PaywallPage.ALL, products.toList(), payload = null, initiallySelectedId = null, locale = Locale.US)

    private val all = rows(
        subscription("premium:monthly", 9.99, VxPeriodUnit.MONTH),
        subscription("premium:yearly", 59.99),
        subscription("premium:yearly-offer", 29.99),
    )

    @Test
    fun `the product asked for wins, by full id or by Play product id`() {
        val selection = PromoOfferLogic.select(all, { emptyList() }, "premium:yearly-offer", "premium:yearly")

        assertThat(selection.product?.identifier).isEqualTo("premium:yearly-offer")
        assertThat(selection.productToCompare?.identifier).isEqualTo("premium:yearly")

        val byProductId = PromoOfferLogic.select(all, { emptyList() }, "premium", null)
        assertThat(byProductId.product?.identifier).isEqualTo("premium:monthly")
    }

    @Test
    fun `without a match the welcome offer's first product is offered, else the first subscription`() {
        val welcome = rows(subscription("welcome:yearly", 19.99))

        assertThat(PromoOfferLogic.select(all, { welcome }, "missing", null).product?.identifier).isEqualTo("welcome:yearly")
        assertThat(PromoOfferLogic.select(all, { welcome }, null, null).product?.identifier).isEqualTo("welcome:yearly")
        assertThat(PromoOfferLogic.select(all, { emptyList() }, null, null).product?.identifier).isEqualTo("premium:monthly")
    }

    @Test
    fun `the compared product defaults to the first subscription but stays empty when not found`() {
        assertThat(PromoOfferLogic.select(all, { emptyList() }, null, null).productToCompare?.identifier).isEqualTo("premium:monthly")
        assertThat(PromoOfferLogic.select(all, { emptyList() }, null, "missing").productToCompare).isNull()
    }

    @Test
    fun `no subscriptions means nothing to offer`() {
        val selection = PromoOfferLogic.select(emptyList(), { emptyList() }, "premium:yearly", null)

        assertThat(selection.product).isNull()
        assertThat(selection.productToCompare).isNull()
    }

    @Test
    fun `discount is rounded half up with a percent sign`() {
        assertThat(PromoOfferLogic.discountPercentage(BigDecimal("29.99"), BigDecimal("59.99"))).isEqualTo("50%")
        assertThat(PromoOfferLogic.discountPercentage(BigDecimal("1"), BigDecimal("3"))).isEqualTo("67%")
        assertThat(PromoOfferLogic.discountPercentage(BigDecimal("1.5"), BigDecimal("2"))).isEqualTo("25%")
        assertThat(PromoOfferLogic.discountPercentage(BigDecimal("0.995"), BigDecimal("2"))).isEqualTo("50%")
        assertThat(PromoOfferLogic.discountPercentage(BigDecimal("9.99"), BigDecimal("9.99"))).isEqualTo("0%")
    }

    @Test
    fun `discount is a bare 0 when it cannot be worked out or the offer costs more`() {
        assertThat(PromoOfferLogic.discountPercentage(null, BigDecimal("10"))).isEqualTo("0")
        assertThat(PromoOfferLogic.discountPercentage(BigDecimal("10"), null)).isEqualTo("0")
        assertThat(PromoOfferLogic.discountPercentage(BigDecimal("12"), BigDecimal("10"))).isEqualTo("0")
        assertThat(PromoOfferLogic.discountPercentage(BigDecimal("0"), BigDecimal("0"))).isEqualTo("0")
    }

    @Test
    fun `placeholders fill in order`() {
        assertThat(PromoOfferLogic.fillValues("{{value_1}} and {{value_2}}, {{value_1}}", "a", "b")).isEqualTo("a and b, a")
        assertThat(PromoOfferLogic.fillValues("nothing to fill", "a")).isEqualTo("nothing to fill")
    }

    @Test
    fun `the discount text keeps a single percent sign`() {
        assertThat(PromoOfferLogic.discountText("{{value_1}}% OFF", "50%")).isEqualTo("50% OFF")
        assertThat(PromoOfferLogic.discountText("Save {{value_1}} on the yearly plan", "50%")).isEqualTo("Save 50% on the yearly plan")
        assertThat(PromoOfferLogic.discountText("{{value_1}}% OFF", "0")).isEqualTo("0% OFF")
    }
}
