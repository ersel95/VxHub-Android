package com.vxhub.ui.internal

import com.google.common.truth.Truth.assertThat
import com.vxhub.purchases.VxPurchaseResult
import com.vxhub.purchases.VxRestoreResult
import org.junit.Test

class PurchaseFeedbackTest {

    @Test
    fun `only a verified purchase is a success, whatever the premium answer`() {
        assertThat(VxPurchaseResult.Verified(isPremium = true).feedback).isEqualTo(PurchaseFeedback.SUCCESS)
        assertThat(VxPurchaseResult.Verified(isPremium = false).feedback).isEqualTo(PurchaseFeedback.SUCCESS)
    }

    @Test
    fun `a charged but unconfirmed purchase is pending, not lost`() {
        assertThat(VxPurchaseResult.PendingVerification.feedback).isEqualTo(PurchaseFeedback.PENDING)
        assertThat(VxPurchaseResult.PaymentPending.feedback).isEqualTo(PurchaseFeedback.PENDING)
    }

    @Test
    fun `cancelled and failed purchases report nothing`() {
        assertThat(VxPurchaseResult.Cancelled.feedback).isEqualTo(PurchaseFeedback.NONE)
        assertThat(VxPurchaseResult.Failed("billing unavailable").feedback).isEqualTo(PurchaseFeedback.NONE)
    }

    @Test
    fun `a restore succeeds only when it leaves the user premium`() {
        assertThat(VxRestoreResult.Restored(isPremium = true).feedback).isEqualTo(PurchaseFeedback.SUCCESS)
        assertThat(VxRestoreResult.Restored(isPremium = false).feedback).isEqualTo(PurchaseFeedback.NONE)
        assertThat(VxRestoreResult.NothingToRestore.feedback).isEqualTo(PurchaseFeedback.NONE)
        assertThat(VxRestoreResult.Failed("offline").feedback).isEqualTo(PurchaseFeedback.NONE)
        assertThat(VxRestoreResult.PendingVerification.feedback).isEqualTo(PurchaseFeedback.PENDING)
    }
}
