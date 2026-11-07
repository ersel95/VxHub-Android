package com.vxhub.ui.promo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MarqueeMathTest {

    @Test
    fun `a forward row moves left and loops after one copy`() {
        assertThat(MarqueeMath.firstCopyX(distance = 0f, copyWidth = 1000, reverse = false)).isEqualTo(0)
        assertThat(MarqueeMath.firstCopyX(distance = 250f, copyWidth = 1000, reverse = false)).isEqualTo(-250)
        assertThat(MarqueeMath.firstCopyX(distance = 999.6f, copyWidth = 1000, reverse = false)).isEqualTo(0)
        assertThat(MarqueeMath.firstCopyX(distance = 1250f, copyWidth = 1000, reverse = false)).isEqualTo(-250)
    }

    @Test
    fun `a reversed row moves right, keeping a copy to its left`() {
        assertThat(MarqueeMath.firstCopyX(distance = 0f, copyWidth = 1000, reverse = true)).isEqualTo(0)
        assertThat(MarqueeMath.firstCopyX(distance = 250f, copyWidth = 1000, reverse = true)).isEqualTo(-750)
        assertThat(MarqueeMath.firstCopyX(distance = 1250f, copyWidth = 1000, reverse = true)).isEqualTo(-750)
    }

    @Test
    fun `the copies always cover two copies' width from the left edge`() {
        for (distance in 0..3000 step 7) {
            for (reverse in listOf(false, true)) {
                val start = MarqueeMath.firstCopyX(distance.toFloat(), copyWidth = 1000, reverse = reverse)
                assertThat(start).isAtMost(0)
                assertThat(start + MarqueeMath.COPIES * 1000).isAtLeast(2000)
            }
        }
    }

    @Test
    fun `an unmeasured row stays put`() {
        assertThat(MarqueeMath.firstCopyX(distance = 120f, copyWidth = 0, reverse = false)).isEqualTo(0)
    }

    @Test
    fun `distance grows with elapsed time`() {
        // iOS: 1 pt every 0.03 s is 33.3 pt a second.
        assertThat(MarqueeMath.advance(distance = 10f, pixelsPerSecond = 100f, elapsedNanos = 500_000_000L)).isEqualTo(60f)
    }
}
