package com.vxhub.ui.store

import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vxhub.VxHub
import com.vxhub.ui.VxHubUI
import com.vxhub.ui.internal.VxScreenHost

/**
 * The store for coins and one-time products — iOS's `VxStoreView`. It sells the consumable and
 * non-consumable products of [VxHub.products]: a grid for [VxStoreV1Configuration], a list for
 * [VxStoreV2Configuration].
 *
 * With nothing to sell when it opens it draws nothing and calls [onDismiss], as iOS does. The
 * back gesture behaves like the close button: ignored while a purchase runs or while the close
 * button is hidden or disabled. A purchase that was charged but is not confirmed yet shows a
 * banner instead of a success.
 *
 * @param configuration read once, when the store opens.
 * @param onPurchaseSuccess called with the product identifier once the backend verified a purchase.
 *   The store stays on screen; remove it from composition to close it.
 * @param onDismiss the user closed the store without buying.
 */
@Composable
public fun VxStore(
    configuration: VxStoreConfiguration,
    onPurchaseSuccess: (productId: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel = rememberScreenViewModel { VxStoreViewModel(configuration, context.isDebuggableApp) }
    val currentOnPurchaseSuccess by rememberUpdatedState(onPurchaseSuccess)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    if (viewModel.hasNothingToShow) {
        LaunchedEffect(viewModel) {
            Log.w(LOG_TAG, "[VxStore] No products to show")
            currentOnDismiss()
        }
        return
    }

    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val balance by VxHub.balance.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    val purchasedId = state.purchasedId
    LaunchedEffect(purchasedId) {
        if (purchasedId != null) {
            viewModel.purchaseReported()
            currentOnPurchaseSuccess(purchasedId)
        }
    }
    BackHandler {
        if (viewModel.canClose) currentOnDismiss()
    }
    StoreScreen(
        configuration = viewModel.configuration,
        rows = rows,
        state = state,
        balance = balance,
        onClose = { if (viewModel.canClose) currentOnDismiss() },
        onSelect = viewModel::select,
        onBuy = { identifier -> viewModel.purchase(activity, identifier) },
        onDismissAlert = viewModel::dismissAlert,
        modifier = modifier,
    )
}

/**
 * Shows the store full screen — iOS's `showStore(from:v1Configuration:v2Configuration:…)`.
 *
 * With no product to sell the store does not open and [onResult] gets `(false, null)` right away.
 * Otherwise [onResult] is called once: `(true, productId)` after a verified purchase, which also
 * closes the store, or `(false, null)` when the user closes it.
 */
public fun VxHubUI.showStore(
    context: Context,
    configuration: VxStoreConfiguration,
    onResult: (purchased: Boolean, productId: String?) -> Unit,
) {
    val rows = StoreRows.build(configuration, VxHub.products.value, StoreOwnedProducts.current, context.isDebuggableApp)
    if (rows.rows.isEmpty()) {
        Log.w(LOG_TAG, "[VxStore] No products to show — store will not open")
        onResult(false, null)
        return
    }
    VxScreenHost.launch(context) {
        SystemBarIcons(darkIcons = configuration.isLightMode)
        var reported by rememberSaveable { mutableStateOf(false) }
        VxStore(
            configuration = configuration,
            onPurchaseSuccess = { productId ->
                if (!reported) {
                    reported = true
                    onResult(true, productId)
                }
                dismiss()
            },
            onDismiss = {
                if (!reported) {
                    reported = true
                    onResult(false, null)
                }
                dismiss()
            },
        )
    }
}
