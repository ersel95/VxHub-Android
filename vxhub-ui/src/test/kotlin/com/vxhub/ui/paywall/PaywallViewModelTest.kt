package com.vxhub.ui.paywall

import com.vxhub.ui.VxAnalyticsEvent
import com.google.common.truth.Truth.assertThat
import com.vxhub.purchases.VxPeriodUnit
import com.vxhub.purchases.VxProduct
import com.vxhub.purchases.VxPurchaseResult
import com.vxhub.purchases.VxRestoreResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {

    private val yearlyProduct = product("premium:yearly", VxPeriodUnit.YEAR, 59_990_000, "\$59.99")
    private val weeklyProduct = product("premium:weekly", VxPeriodUnit.WEEK, 6_990_000, "\$6.99", eligible = true)
    private val yearly = item(id = "premium:yearly", index = 0, period = VxPeriodUnit.YEAR, isBestOffer = true, product = yearlyProduct)
    private val weekly = item(id = "premium:weekly", index = 1, period = VxPeriodUnit.WEEK, trialDays = 3, initiallySelected = true, product = weeklyProduct)

    private val notices = mutableListOf<PaywallNotice>()
    private val logged = mutableListOf<Pair<String, Map<String, Any?>>>()
    private var isOnline = true
    private var restoreResult: VxRestoreResult = VxRestoreResult.NothingToRestore

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        items: List<PaywallItem> = listOf(yearly, weekly),
        analytics: Set<VxAnalyticsEvent> = emptySet(),
    ) = PaywallViewModel(
        analyticsEvents = analytics,
        items = flowOf(items),
        isConnected = { isOnline },
        restorePurchases = { restoreResult },
        notify = { notices += it },
        logEvent = { name, properties -> logged += name to properties },
    )

    private suspend fun PaywallViewModel.nextEvent(): PaywallEvent? = withTimeoutOrNull(100) { events.first() }

    @Test
    fun `opens on the remote config's product and keeps the selection when products reload`() {
        val products = MutableStateFlow(listOf(yearly, weekly))
        val viewModel = PaywallViewModel(analyticsEvents = emptySet(), items = products)
        assertThat(viewModel.state.selected).isEqualTo(weekly)

        viewModel.select(yearly)
        products.value = listOf(yearly.copy(localizedPrice = "\$49.99"), weekly)
        assertThat(viewModel.state.selected?.id).isEqualTo("premium:yearly")

        products.value = listOf(weekly)
        assertThat(viewModel.state.selected).isEqualTo(weekly)
    }

    @Test
    fun `placeholder rows show while no product loaded`() {
        val placeholders = mainPaywallItems(emptyList())
        assertThat(placeholders.map { it.id }).containsExactly("yearly_subscription", "monthly_trial").inOrder()
        assertThat(placeholders.all { it.product == null }).isTrue()
        assertThat(initialSelection(placeholders)?.id).isEqualTo("yearly_subscription")
    }

    @Test
    fun `rows are numbered in on-screen order, longest period first`() {
        val items = mainPaywallItems(listOf(weeklyProduct, yearlyProduct))
        assertThat(items.map { it.id }).containsExactly("premium:yearly", "premium:weekly").inOrder()
        assertThat(items.map { it.index }).containsExactly(0, 1).inOrder()
        assertThat(items.first().isBestOffer).isTrue()
        assertThat(items.first().comparedPeriod).isEqualTo(VxPeriodUnit.WEEK)
        assertThat(items.first().product).isSameInstanceAs(yearlyProduct)
    }

    @Test
    fun `tapping a product logs Select only when configured`() {
        val silent = viewModel()
        silent.select(yearly)
        assertThat(logged).isEmpty()

        val logging = viewModel(analytics = setOf(VxAnalyticsEvent.SELECT))
        logging.select(yearly)
        assertThat(logging.state.selected).isEqualTo(yearly)
        assertThat(logged).containsExactly(
            "Select" to mapOf<String, Any?>("product_identifier" to "premium:yearly", "page_name" to "subscription_landing"),
        )
    }

    @Test
    fun `the free-trial switch follows the selection and selects by eligibility`() {
        val viewModel = viewModel()
        assertThat(viewModel.state.isTrialSwitchVisible).isTrue()
        assertThat(viewModel.state.isTrialSwitchOn).isTrue()

        viewModel.setTrialEnabled(false)
        assertThat(viewModel.state.selected).isEqualTo(yearly)
        assertThat(viewModel.state.isTrialSwitchOn).isFalse()

        viewModel.setTrialEnabled(true)
        assertThat(viewModel.state.selected).isEqualTo(weekly)

        val allTrials = viewModel(items = listOf(weekly, weekly.copy(id = "premium:monthly")))
        assertThat(allTrials.state.isTrialSwitchVisible).isFalse()
    }

    @Test
    fun `a verified purchase is reported and loading ends`() = runTest {
        val viewModel = viewModel()
        val result = CompletableDeferred<VxPurchaseResult>()
        var bought: VxProduct? = null
        viewModel.purchase { product ->
            bought = product
            result.await()
        }
        assertThat(viewModel.state.isLoading).isTrue()
        viewModel.select(yearly)
        assertThat(viewModel.state.selected).isEqualTo(weekly)

        result.complete(VxPurchaseResult.Verified(isPremium = true))
        assertThat(bought).isSameInstanceAs(weeklyProduct)
        assertThat(viewModel.nextEvent()).isEqualTo(PaywallEvent.Purchased("premium:weekly"))
        assertThat(viewModel.state.isLoading).isFalse()
    }

    @Test
    fun `pending purchases show a banner and are not reported`() = runTest {
        val viewModel = viewModel()
        viewModel.purchase { VxPurchaseResult.PendingVerification }
        viewModel.purchase { VxPurchaseResult.PaymentPending }
        assertThat(notices).containsExactly(PaywallNotice.VERIFICATION_PENDING, PaywallNotice.PAYMENT_PENDING).inOrder()
        assertThat(viewModel.nextEvent()).isNull()
        assertThat(viewModel.state.isLoading).isFalse()
    }

    @Test
    fun `cancelled, failed and throwing purchases just stop loading`() = runTest {
        val viewModel = viewModel()
        viewModel.purchase { VxPurchaseResult.Cancelled }
        viewModel.purchase { VxPurchaseResult.Failed("declined") }
        viewModel.purchase { error("not initialized") }
        assertThat(viewModel.nextEvent()).isNull()
        assertThat(notices).isEmpty()
        assertThat(viewModel.state.isLoading).isFalse()
    }

    @Test
    fun `offline purchases raise the connection alert instead`() {
        isOnline = false
        val viewModel = viewModel()
        var called = false
        viewModel.purchase {
            called = true
            VxPurchaseResult.Cancelled
        }
        assertThat(called).isFalse()
        assertThat(viewModel.showsConnectionAlert).isTrue()
        viewModel.dismissConnectionAlert()
        assertThat(viewModel.showsConnectionAlert).isFalse()
    }

    @Test
    fun `placeholder rows cannot be bought`() {
        val viewModel = viewModel(items = placeholderItems())
        var called = false
        viewModel.purchase {
            called = true
            VxPurchaseResult.Cancelled
        }
        assertThat(called).isFalse()
        assertThat(viewModel.state.isLoading).isFalse()
    }

    @Test
    fun `restore counts only when the backend says premium`() = runTest {
        val viewModel = viewModel()

        restoreResult = VxRestoreResult.Restored(isPremium = true)
        viewModel.restore()
        assertThat(viewModel.nextEvent()).isEqualTo(PaywallEvent.Restored)

        restoreResult = VxRestoreResult.Restored(isPremium = false)
        viewModel.restore()
        assertThat(viewModel.nextEvent()).isEqualTo(PaywallEvent.NotRestored)

        restoreResult = VxRestoreResult.NothingToRestore
        viewModel.restore()
        assertThat(viewModel.nextEvent()).isEqualTo(PaywallEvent.NotRestored)
        assertThat(notices).isEmpty()

        restoreResult = VxRestoreResult.PendingVerification
        viewModel.restore()
        assertThat(viewModel.nextEvent()).isEqualTo(PaywallEvent.NotRestored)
        assertThat(notices).containsExactly(PaywallNotice.VERIFICATION_PENDING)
        assertThat(viewModel.state.isLoading).isFalse()
    }
}
