package com.vxhub.ui.promo

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vxhub.VxHub
import com.vxhub.ui.VxHubUI
import com.vxhub.ui.internal.SystemBarIcons
import com.vxhub.ui.internal.VxAlert
import com.vxhub.ui.internal.VxScreenHost
import com.vxhub.ui.internal.rememberScreenViewModel

/**
 * A one-time discount on a subscription — iOS's `VxPromoOfferView`. Shows [productIdentifier]'s
 * price against [productToCompareIdentifier]'s, with the discount between them; buying goes
 * through [VxHub.purchase], and Restore through [VxHub.restorePurchases].
 *
 * The back gesture behaves like the close button, and is ignored while a purchase runs. A purchase
 * that was charged but is not confirmed yet shows a banner instead of a success. The products are
 * chosen once, when the offer opens.
 *
 * @param onPurchaseSuccess the backend verified the purchase, or a restore left the user premium.
 *   The offer stays on screen; remove it from composition to close it.
 * @param onDismiss the user closed the offer without buying.
 * @param productIdentifier the subscription on offer, by product id or `productId:basePlanId`.
 *   Without it — or when it is not found — the welcome offer's product, else the first subscription.
 * @param productToCompareIdentifier the subscription whose price is struck through. Without it, the
 *   first subscription; when it is not found, no old price and a "0%" discount, as on iOS.
 */
@Composable
public fun VxPromoOffer(
    onPurchaseSuccess: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    productIdentifier: String? = null,
    productToCompareIdentifier: String? = null,
    type: PromoOfferType = PromoOfferType.V1,
) {
    val viewModel = rememberScreenViewModel { PromoOfferViewModel(productIdentifier, productToCompareIdentifier) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnPurchaseSuccess by rememberUpdatedState(onPurchaseSuccess)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val activity = LocalActivity.current
    val context = LocalContext.current

    val succeeded = state.succeeded
    LaunchedEffect(succeeded) {
        if (succeeded) {
            viewModel.successReported()
            currentOnPurchaseSuccess()
        }
    }
    BackHandler {
        if (!viewModel.state.value.isLoading) currentOnDismiss()
    }
    val actions = remember(viewModel, activity, context) {
        PromoOfferActions(
            onClose = { if (!viewModel.state.value.isLoading) currentOnDismiss() },
            onClaim = { viewModel.purchase(activity) },
            onRestore = viewModel::restore,
            onTerms = { VxHub.showEula(context, fullScreen = false, showCloseButton = true) },
            onPrivacy = { VxHub.showPrivacy(context, fullScreen = false, showCloseButton = true) },
        )
    }
    when (type) {
        PromoOfferType.V1 -> PromoOfferV1Screen(viewModel.content, state.isLoading, actions, modifier)
        is PromoOfferType.V2 -> PromoOfferV2Screen(viewModel.content, type.video, state.isLoading, actions, modifier)
    }
    state.alert?.let { VxAlert(it, viewModel::dismissAlert) }
}

/**
 * Shows a promo offer full screen — iOS's `showPromoOffer(from:productIdentifier:…)`. [onResult]
 * is called once: `true` after a verified purchase (or a restore that left the user premium), which
 * also closes the offer, `false` when the user closes it.
 *
 * See [VxPromoOffer] for how the products are chosen.
 */
public fun VxHubUI.showPromoOffer(
    context: Context,
    productIdentifier: String? = null,
    productToCompareIdentifier: String? = null,
    type: PromoOfferType = PromoOfferType.V1,
    onResult: (purchased: Boolean) -> Unit,
) {
    VxScreenHost.launch(context) {
        SystemBarIcons(darkIcons = false)
        var reported by rememberSaveable { mutableStateOf(false) }
        VxPromoOffer(
            onPurchaseSuccess = {
                if (!reported) {
                    reported = true
                    onResult(true)
                }
                dismiss()
            },
            onDismiss = {
                if (!reported) {
                    reported = true
                    onResult(false)
                }
                dismiss()
            },
            productIdentifier = productIdentifier,
            productToCompareIdentifier = productToCompareIdentifier,
            type = type,
        )
    }
}
