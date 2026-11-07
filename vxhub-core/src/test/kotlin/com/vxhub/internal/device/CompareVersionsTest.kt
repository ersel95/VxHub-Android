package com.vxhub.internal.device

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CompareVersionsTest {
    @Test
    fun comparesNumericallyNotLexically() {
        assertThat(compareVersions("1.10.0", "1.9.3")).isGreaterThan(0)
        assertThat(compareVersions("2.0", "2.0.0")).isEqualTo(0)
        assertThat(compareVersions("1.2.3", "1.2.4")).isLessThan(0)
        assertThat(compareVersions("3.1.0-beta", "3.1.0")).isEqualTo(0)
        assertThat(compareVersions("", "1.0")).isLessThan(0)
    }
}
