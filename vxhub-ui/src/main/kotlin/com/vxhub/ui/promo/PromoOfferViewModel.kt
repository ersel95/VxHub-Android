package com.vxhub.ui.promo

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vxhub.ui.internal.paywall.PaywallPage
import com.vxhub.ui.internal.paywall.PaywallProduct
import com.vxhub.ui.internal.paywall.PaywallProducts
import com.vxhub.ui.internal.vxString
import com.vxhub.ui.store.LOG_TAG
import com.vxhub.ui.store.PurchaseFeedback
import com.vxhub.ui.store.VxAlertContent
import com.vxhub.ui.store.feedback
import com.vxhub.ui.store.purchaseCatching
import com.vxhub.ui.store.restoreCatching
import com.vxhub.ui.store.showPurchasePendingBanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

/** The prices and discount a promo offer shows. */
internal data class PromoOfferContent(
    /** The compared product's price, struck through. */
    val oldPrice: String,
    /** The offered product's price. */
    val newPrice: String,
    /** "50%", or "0" when there is nothing to compare — iOS's `calculateDiscountPercentage`. */
    val discountPercentage: String,
)

internal data class PromoOfferUiState(
    val isLoading: Boolean = false,
    val alert: VxAlertContent? = null,
    /** A purchase or restore to report to the caller once. */
    val succeeded: Boolean = false,
)

/** iOS's `PromoOfferViewModel`. */
internal class PromoOfferViewModel(productIdentifier: String?, productToCompareIdentifier: String?) : ViewModel() {

    private val selection = PromoOfferLogic.select(
        all = PaywallProducts.build(PaywallPage.ALL),
        welcomeOffer = { PaywallProducts.build(PaywallPage.WELCOME_OFFER) },
        productIdentifier = productIdentifier,
        productToCompareIdentifier = productToCompareIdentifier,
    )

    val content: PromoOfferContent = PromoOfferContent(
        oldPrice = selection.productToCompare?.localizedPrice.orEmpty(),
        newPrice = selection.product?.localizedPrice.orEmpty(),
        discountPercentage = PromoOfferLogic.discountPercentage(selection.product?.price, selection.productToCompare?.price),
    )

    private val mutableState = MutableStateFlow(PromoOfferUiState())
    val state: StateFlow<PromoOfferUiState> = mutableState.asStateFlow()

    fun purchase(activity: Activity?) {
        if (state.value.isLoading) return
        val product = selection.product?.product ?: return
        if (activity == null) {
            Log.w(LOG_TAG, "[PromoOffer] No activity to buy ${product.id} from")
            return
        }
        mutableState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val feedback = purchaseCatching(activity, product).feedback
            if (feedback == PurchaseFeedback.PENDING) showPurchasePendingBanner()
            mutableState.update { it.copy(isLoading = false, succeeded = it.succeeded || feedback == PurchaseFeedback.SUCCESS) }
        }
    }

    /** Succeeds when the restore leaves the user premium; anything else but a pending one says "nothing to restore". */
    fun restore() {
        if (state.value.isLoading) return
        mutableState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val feedback = restoreCatching().feedback
            if (feedback == PurchaseFeedback.PENDING) showPurchasePendingBanner()
            mutableState.update {
                it.copy(
                    isLoading = false,
                    succeeded = it.succeeded || feedback == PurchaseFeedback.SUCCESS,
                    alert = if (feedback == PurchaseFeedback.NONE) nothingToRestoreAlert() else it.alert,
                )
            }
        }
    }

    fun dismissAlert() {
        mutableState.update { it.copy(alert = null) }
    }

    fun successReported() {
        mutableState.update { it.copy(succeeded = false) }
    }

    private fun nothingToRestoreAlert() = VxAlertContent(
        title = vxString("Subscription_NothingToRestore"),
        message = vxString("Subscription_NothingToRestoreDescription"),
        buttonTitle = vxString("Subscription_NothingToRestoreButtonLabel"),
    )
}

/** The promo offer's decisions, free of Android and of VxHub's state so they can be tested. */
internal object PromoOfferLogic {

    data class Selection(val product: PaywallProduct?, val productToCompare: PaywallProduct?)

    /**
     * iOS's choice: the product asked for, else the welcome offer's first product, else the first
     * subscription; compared against the product asked for — which stays empty when it is not
     * found — or else the first subscription.
     */
    fun select(
        all: List<PaywallProduct>,
        welcomeOffer: () -> List<PaywallProduct>,
        productIdentifier: String?,
        productToCompareIdentifier: String?,
    ): Selection {
        val product = productIdentifier?.let { id -> all.firstOrNull { it.matches(id) } }
            ?: welcomeOffer().firstOrNull()
            ?: all.firstOrNull()
        val productToCompare = if (productToCompareIdentifier != null) {
            all.firstOrNull { it.matches(productToCompareIdentifier) }
        } else {
            all.firstOrNull()
        }
        return Selection(product, productToCompare)
    }

    /**
     * How much cheaper [price] is than [comparePrice], rounded: "50%". "0" when either is missing,
     * when the offer is the dearer one, or when the compared price is zero.
     */
    fun discountPercentage(price: BigDecimal?, comparePrice: BigDecimal?): String {
        if (price == null || comparePrice == null || comparePrice.signum() <= 0) return "0"
        val discount = comparePrice.subtract(price).multiply(HUNDRED).divide(comparePrice, 6, RoundingMode.HALF_UP)
        if (discount.signum() < 0) return "0"
        return "${discount.setScale(0, RoundingMode.HALF_UP).toInt()}%"
    }

    /** Fills VxLabel's `{{value_1}}`, `{{value_2}}`… placeholders (`replaceValues`). */
    fun fillValues(template: String, vararg values: String): String =
        values.foldIndexed(template) { index, text, value -> text.replace("{{value_${index + 1}}}", value) }

    /**
     * [fillValues] with the discount. The bundled texts already follow the placeholder with "%"
     * ("{{value_1}}% OFF") while the value carries its own, so iOS shows "50%% OFF"; one is meant.
     */
    fun discountText(template: String, percentage: String): String = fillValues(template, percentage).replace("%%", "%")

    private fun PaywallProduct.matches(identifier: String): Boolean =
        this.identifier == identifier || product.storeProduct.productId == identifier

    private val HUNDRED = BigDecimal(100)
}
