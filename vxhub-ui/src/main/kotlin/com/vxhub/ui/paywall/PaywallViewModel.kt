package com.vxhub.ui.paywall

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vxhub.VxHub
import com.vxhub.providers.VxBannerType
import com.vxhub.purchases.VxProduct
import com.vxhub.purchases.VxPurchaseResult
import com.vxhub.purchases.VxRestoreResult
import com.vxhub.ui.internal.vxString
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** One-off outcomes the screen turns into callbacks. */
internal sealed interface PaywallEvent {
    data class Purchased(val productId: String) : PaywallEvent

    data object Restored : PaywallEvent

    data object NotRestored : PaywallEvent
}

/**
 * Banners for purchases VxHub keeps but cannot grant yet. iOS has no such texts (it reports these
 * purchases as failures), so the keys are new; the English shows until the panel translates them.
 */
internal enum class PaywallNotice(val key: String, val defaultText: String) {
    PAYMENT_PENDING(
        "Subscription_PaymentPending",
        "Your payment is pending. Premium unlocks as soon as it is confirmed.",
    ),
    VERIFICATION_PENDING(
        "Subscription_VerificationPending",
        "We received your purchase and are confirming it. Premium unlocks shortly.",
    ),
}

/**
 * The main paywalls' logic — iOS's `VxMainSubscriptionViewModel`. A ViewModel, so a purchase in
 * flight survives a configuration change and its result still reaches the screen.
 */
internal class PaywallViewModel(
    private val analyticsEvents: Set<VxPaywallAnalyticsEvent>,
    items: Flow<List<PaywallItem>> = VxHub.products.map(::mainPaywallItems),
    private val isConnected: () -> Boolean = { VxHub.isConnected.value },
    private val restorePurchases: suspend () -> VxRestoreResult = { VxHub.restorePurchases() },
    private val notify: (PaywallNotice) -> Unit = { VxHub.showBanner(vxString(it.key, it.defaultText), VxBannerType.INFO) },
    private val logEvent: (String, Map<String, Any?>) -> Unit = { name, properties -> VxHub.logAmplitudeEvent(name, properties) },
) : ViewModel() {

    var state: PaywallUiState by mutableStateOf(PaywallUiState())
        private set

    /** iOS's "check your internet connection" alert, raised by a purchase attempt while offline. */
    var showsConnectionAlert: Boolean by mutableStateOf(false)
        private set

    private val eventChannel = Channel<PaywallEvent>(Channel.UNLIMITED)
    val events: Flow<PaywallEvent> = eventChannel.receiveAsFlow()

    init {
        // Products that load while the paywall is open replace the placeholder rows.
        items.onEach { state = state.withItems(it) }.launchIn(viewModelScope)
    }

    fun select(item: PaywallItem) {
        if (state.isLoading) return
        state = state.copy(selectedId = item.id)
        if (VxPaywallAnalyticsEvent.SELECT in analyticsEvents) {
            logEvent(
                VxPaywallAnalyticsEvent.SELECT.eventName,
                mapOf("product_identifier" to item.id, "page_name" to "subscription_landing"),
            )
        }
    }

    /** V1's free-trial switch: selects the first product with an offer (on) or without one (off). */
    fun setTrialEnabled(enabled: Boolean) {
        if (state.isLoading) return
        val target = trialToggleTarget(state.items, enabled) ?: return
        state = state.copy(selectedId = target.id)
    }

    /**
     * Buys the selected product through [buy]. A verified purchase is reported. A pending one —
     * the payment has not cleared, or the backend has not verified it yet and VxHub keeps
     * retrying — shows a banner rather than passing for a failure.
     */
    fun purchase(buy: suspend (VxProduct) -> VxPurchaseResult) {
        if (!isConnected()) {
            showsConnectionAlert = true
            return
        }
        if (state.isLoading) return
        val item = state.selected ?: return
        // Placeholder rows have nothing to buy, as on iOS.
        val product = item.product ?: return
        state = state.copy(isLoading = true)
        viewModelScope.launch {
            val result = try {
                buy(product)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                VxPurchaseResult.Failed(e.message)
            }
            when (result) {
                is VxPurchaseResult.Verified -> eventChannel.send(PaywallEvent.Purchased(item.id))
                VxPurchaseResult.PendingVerification -> notify(PaywallNotice.VERIFICATION_PENDING)
                VxPurchaseResult.PaymentPending -> notify(PaywallNotice.PAYMENT_PENDING)
                // The store shows its own errors; the paywall just stops loading, as on iOS.
                VxPurchaseResult.Cancelled, is VxPurchaseResult.Failed -> Unit
            }
            state = state.copy(isLoading = false)
        }
    }

    /** Restores; it counts as restored only when the backend then says premium, as on iOS. */
    fun restore() {
        if (state.isLoading) return
        state = state.copy(isLoading = true)
        viewModelScope.launch {
            val result = try {
                restorePurchases()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                VxRestoreResult.Failed(e.message)
            }
            if (result == VxRestoreResult.PendingVerification) notify(PaywallNotice.VERIFICATION_PENDING)
            val restored = result is VxRestoreResult.Restored && result.isPremium
            eventChannel.send(if (restored) PaywallEvent.Restored else PaywallEvent.NotRestored)
            state = state.copy(isLoading = false)
        }
    }

    fun dismissConnectionAlert() {
        showsConnectionAlert = false
    }
}
