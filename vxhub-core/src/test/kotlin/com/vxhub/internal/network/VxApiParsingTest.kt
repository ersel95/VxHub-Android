package com.vxhub.internal.network

import com.google.common.truth.Truth.assertThat
import com.vxhub.VxPromoCodeAction
import com.vxhub.VxPromoCodeResult
import com.vxhub.VxSpendCoinsResult
import org.junit.Test

/** Mirrors iOS's VxSpendCoinsParserTests and the promo code mapping in VxNetworkManager. */
class VxApiParsingTest {
    @Test
    fun spentCoinsReportTheRemainingBalance() {
        assertThat(VxApi.parseSpendCoins(200, """{"status":"success","balance":7,"premium":false}"""))
            .isEqualTo(VxSpendCoinsResult.Spent(7))
    }

    @Test
    fun premiumDevicesAreNeverCharged() {
        assertThat(VxApi.parseSpendCoins(200, """{"status":"premium","balance":7,"premium":true}"""))
            .isEqualTo(VxSpendCoinsResult.Premium(7))
    }

    @Test
    fun insufficientCreditsComeBackAs403() {
        assertThat(VxApi.parseSpendCoins(403, """{"errorCode":"INSUFFICIENT_CREDITS","balance":0}"""))
            .isEqualTo(VxSpendCoinsResult.Insufficient(0))
        assertThat(VxApi.parseSpendCoins(403, """{"errorCode":"OTHER"}""")).isInstanceOf(VxSpendCoinsResult.Failed::class.java)
    }

    @Test
    fun unreadableSpendIsAFailureNotASpend() {
        assertThat(VxApi.parseSpendCoins(200, "")).isInstanceOf(VxSpendCoinsResult.Failed::class.java)
        assertThat(VxApi.parseSpendCoins(500, "{}")).isInstanceOf(VxSpendCoinsResult.Failed::class.java)
    }

    @Test
    fun promoCodeActionsCarryTheirMeta() {
        val premium = VxApi.parsePromoCode(200, """{"success":true,"action_type":"premium","action_meta":"30"}""")
        assertThat(premium).isEqualTo(VxPromoCodeResult.Redeemed(VxPromoCodeAction.PREMIUM, null, 30, null, emptyMap()))

        val coin = VxApi.parsePromoCode(200, """{"success":true,"action_type":"coin","action_meta":50,"extra_data":{"k":"v"}}""")
        assertThat(coin).isEqualTo(VxPromoCodeResult.Redeemed(VxPromoCodeAction.COIN, null, null, 50, mapOf("k" to "v")))

        val discount = VxApi.parsePromoCode(200, """{"success":true,"action_type":"discount","action_meta":"pro_yearly_50"}""")
        assertThat(discount).isEqualTo(VxPromoCodeResult.Redeemed(VxPromoCodeAction.DISCOUNT, "pro_yearly_50", null, null, emptyMap()))
    }

    @Test
    fun promoCodeErrorsKeepTheBackendMessages() {
        assertThat(VxApi.parsePromoCode(400, """{"message":["Code expired","Try another"],"statusCode":400}"""))
            .isEqualTo(VxPromoCodeResult.Invalid(listOf("Code expired", "Try another")))
        assertThat(VxApi.parsePromoCode(404, """{"message":"Promo code not found"}"""))
            .isEqualTo(VxPromoCodeResult.Invalid(listOf("Promo code not found")))
        assertThat(VxApi.parsePromoCode(200, """{"success":false}""")).isInstanceOf(VxPromoCodeResult.Invalid::class.java)
    }
}
