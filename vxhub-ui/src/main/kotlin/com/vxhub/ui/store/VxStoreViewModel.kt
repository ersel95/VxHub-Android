package com.vxhub.ui.store

import com.vxhub.ui.VxAnalyticsEvent
import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vxhub.VxHub
import com.vxhub.purchases.VxProductType
import com.vxhub.ui.internal.vxString
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration

internal data class StoreUiState(
    /** The card [VxStorePurchaseMode.SELECT_AND_BUY] buys. */
    val selectedId: String? = null,
    /** The product being bought; the screen is busy while it is set. */
    val purchasingId: String? = null,
    /** False until [VxStoreConfiguration.closeButtonDelay] has passed. */
    val isCloseDelayOver: Boolean = true,
    val alert: VxAlertContent? = null,
    /** A purchase to report to the caller once. */
    val purchasedId: String? = null,
) {
    val isLoading: Boolean get() = purchasingId != null
}

/** iOS's `VxStoreViewModel`. */
internal class VxStoreViewModel(
    val configuration: VxStoreConfiguration,
    allowDummyProducts: Boolean,
) : ViewModel() {

    val rows: StateFlow<StoreRows> = combine(VxHub.products, StoreOwnedProducts.ids) { products, owned ->
        StoreRows.build(configuration, products, owned, allowDummyProducts)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = StoreRows.build(configuration, VxHub.products.value, StoreOwnedProducts.current, allowDummyProducts),
    )

    /** Like iOS, the store decides once, as it opens, whether it has anything to show. */
    val hasNothingToShow: Boolean = rows.value.rows.isEmpty()

    private val mutableState = MutableStateFlow(
        StoreUiState(isCloseDelayOver = configuration.closeButtonDelay <= Duration.ZERO),
    )
    val state: StateFlow<StoreUiState> = mutableState.asStateFlow()

    init {
        if (!state.value.isCloseDelayOver) {
            viewModelScope.launch {
                delay(configuration.closeButtonDelay)
                mutableState.update { it.copy(isCloseDelayOver = true) }
            }
        }
        if (configuration.purchaseMode == VxStorePurchaseMode.SELECT_AND_BUY) {
            rows.value.rows.firstOrNull()?.let { select(it.identifier) }
        }
    }

    /** Whether the close button (and the back gesture) can close the store right now. */
    val canClose: Boolean
        get() = configuration.isCloseButtonEnabled && state.value.isCloseDelayOver && !state.value.isLoading

    fun select(identifier: String) {
        if (state.value.isLoading || configuration.purchaseMode != VxStorePurchaseMode.SELECT_AND_BUY) return
        if (rows.value.row(identifier) == null) return
        mutableState.update { it.copy(selectedId = identifier) }
        if (VxAnalyticsEvent.SELECT in configuration.analyticsEvents) {
            VxHub.logAmplitudeEvent(
                VxAnalyticsEvent.SELECT.eventName,
                mapOf("product_identifier" to identifier, "page_name" to "store"),
            )
        }
    }

    /** Buys [identifier], or the selected card when null (the call-to-action button). */
    fun purchase(activity: Activity?, identifier: String?) {
        val current = rows.value
        if (current.isDummyMode) {
            val productId = identifier ?: state.value.selectedId ?: return
            Log.w(LOG_TAG, "[VxStore] Dummy mode — purchase simulated for $productId")
            mutableState.update { it.copy(purchasedId = productId) }
            return
        }
        if (!VxHub.isConnected.value) {
            mutableState.update {
                it.copy(
                    alert = VxAlertContent(
                        title = vxString("InternetConnection_CheckYourInternetConnection"),
                        message = vxString("InternetConnection_CheckYourInternetConnectionDescription"),
                        buttonTitle = vxString("InternetConnection_CheckYourInternetConnectionButtonLabel"),
                    ),
                )
            }
            return
        }
        if (state.value.isLoading) return
        val row = current.row(identifier ?: state.value.selectedId) ?: return
        // iOS lets an owned non-consumable be bought again from the call-to-action; Play refuses it anyway.
        if (row.isAlreadyPurchased) return
        val product = row.product ?: return
        if (activity == null) {
            Log.w(LOG_TAG, "[VxStore] No activity to buy ${row.identifier} from")
            return
        }
        mutableState.update { it.copy(purchasingId = row.identifier) }
        viewModelScope.launch {
            val feedback = purchaseCatching(activity, product).feedback
            if (feedback == PurchaseFeedback.SUCCESS && row.productType == VxProductType.NON_CONSUMABLE) {
                runCatchingNonCancellation { VxHub.saveNonConsumablePurchase(row.identifier) }
                StoreOwnedProducts.add(row.identifier)
            }
            if (feedback == PurchaseFeedback.PENDING) showPurchasePendingBanner()
            mutableState.update {
                it.copy(purchasingId = null, purchasedId = if (feedback == PurchaseFeedback.SUCCESS) row.identifier else it.purchasedId)
            }
        }
    }

    fun dismissAlert() {
        mutableState.update { it.copy(alert = null) }
    }

    fun purchaseReported() {
        mutableState.update { it.copy(purchasedId = null) }
    }

    private suspend fun runCatchingNonCancellation(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(LOG_TAG, "[VxStore] Could not record the non-consumable purchase", e)
        }
    }
}
