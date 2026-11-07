package com.vxhub.internal.purchase

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException

/** Mirrors iOS's VxPurchaseVerificationTests: the same responses must lead to the same decisions. */
class VerificationOutcomeTest {
    private fun body(verified: Boolean? = true, premium: Boolean = true, balance: String = "42") =
        """{"status":"success","vid":"v","${if (verified == null) "x" else "verified"}":${verified ?: true},""" +
            """"device":{"premium_status":$premium,"balance":$balance}}"""

    @Test
    fun successBodyIsVerifiedWithBackendPremiumAndBalance() {
        assertThat(VerificationOutcome.classify(200, body(), null, requireTransactionMatch = true))
            .isEqualTo(VerificationOutcome.Verified(isPremium = true, balance = 42))
    }

    @Test
    fun bodyWithoutVerifiedFieldStillDecodes() {
        assertThat(VerificationOutcome.classify(200, body(verified = null, premium = false), null, requireTransactionMatch = true))
            .isEqualTo(VerificationOutcome.Verified(isPremium = false, balance = 42))
    }

    @Test
    fun unverifiedTransactionStaysQueuedForAfterPurchaseOnly() {
        assertThat(VerificationOutcome.classify(200, body(verified = false), null, requireTransactionMatch = true))
            .isInstanceOf(VerificationOutcome.RetryLater::class.java)
        assertThat(VerificationOutcome.classify(200, body(verified = false), null, requireTransactionMatch = false))
            .isEqualTo(VerificationOutcome.Verified(isPremium = true, balance = 42))
    }

    @Test
    fun timeoutAndServerErrorsAreRetriedLater() {
        listOf(408, 429, 500, 502, 503).forEach { status ->
            assertThat(VerificationOutcome.classify(status, """{"status":"pending"}""", null))
                .isInstanceOf(VerificationOutcome.RetryLater::class.java)
        }
        assertThat(VerificationOutcome.classify(null, null, IOException("timeout")))
            .isInstanceOf(VerificationOutcome.RetryLater::class.java)
    }

    @Test
    fun undecodableSuccessIsNotTreatedAsVerified() {
        listOf("", "not json", """{"status":"success"}""").forEach { raw ->
            assertThat(VerificationOutcome.classify(200, raw, null)).isInstanceOf(VerificationOutcome.RetryLater::class.java)
        }
    }

    @Test
    fun missingEndpointAndClientErrors() {
        assertThat(VerificationOutcome.classify(404, "", null)).isEqualTo(VerificationOutcome.Unsupported)
        assertThat(VerificationOutcome.classify(400, """{"message":"Invalid VID"}""", null))
            .isEqualTo(VerificationOutcome.Rejected(400))
    }

    @Test
    fun balanceSentAsTextIsRead() {
        assertThat(VerificationOutcome.classify(200, body(balance = "\"15\""), null))
            .isEqualTo(VerificationOutcome.Verified(isPremium = true, balance = 15))
    }
}
