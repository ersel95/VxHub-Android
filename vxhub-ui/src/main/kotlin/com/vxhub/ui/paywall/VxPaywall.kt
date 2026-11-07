package com.vxhub.ui.paywall

import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vxhub.VxHub
import com.vxhub.ui.VxHubUI
import com.vxhub.ui.internal.VxScreenHost
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.time.Duration

/** How a main paywall ended — iOS's `completion(Bool, String?)`. */
public sealed interface VxPaywallResult {
    /**
     * The VxHub backend verified the purchase of [productId], a [com.vxhub.purchases.VxProduct.id].
     * iOS: `completion(true, productId)`.
     */
    public data class Purchased(val productId: String) : VxPaywallResult

    /** "Restore Purchase" found a subscription the backend counts as premium. iOS: `completion(true, nil)`. */
    public data object Restored : VxPaywallResult

    /** The user left with the close button or Back without buying. iOS: `completion(false, nil)`. */
    public data object Dismissed : VxPaywallResult
}

/**
 * VxHub's main paywall as a composable — iOS's `VxPaywallView` and `VxPaywallV3View`. The type of
 * [configuration] picks V1, V2, V3 or V4.
 *
 * Products come from [VxHub.products], chosen by the panel's `main_paywall` experiment and sorted
 * longest period first; until they load, placeholder rows show that cannot be bought (as on iOS).
 *
 * - A purchase the backend verified ends in [VxPaywallResult.Purchased].
 * - A restore that leaves the device premium ends in [VxPaywallResult.Restored], followed by
 *   `onRestoreStateChange(true)`; any other restore outcome calls `onRestoreStateChange(false)`.
 * - A purchase Google Play has not finished charging, or that the backend has not verified yet, is
 *   never reported as a failure: a banner tells the user, the paywall stays open, and VxHub
 *   verifies it on a later start ([VxHub.isPremium] follows).
 * - The close button, and Back while the close button shows, end in [VxPaywallResult.Dismissed].
 *   Without a close button (`isCloseButtonEnabled = false`, or during `closeButtonDelay`) Back does
 *   nothing: a hard paywall.
 *
 * The paywall does not remove itself: take it off screen on any [VxPaywallResult] —
 * [VxHubUI.showPaywall] does that for you. It fills the space it is given, draws its background
 * edge to edge and keeps its content inside the safe-drawing insets.
 *
 * @param onRestoreStateChange iOS's `onRestoreStateChange`: whether a restore made the device premium.
 * @param onRedeemCodeTapped iOS's `onReedemCodeButtonTapped`: the "Redeem Code" link (V2, V3, V4).
 */
@Composable
public fun VxPaywall(
    configuration: VxPaywallConfiguration,
    onResult: (VxPaywallResult) -> Unit,
    modifier: Modifier = Modifier,
    onRestoreStateChange: (Boolean) -> Unit = {},
    onRedeemCodeTapped: () -> Unit = {},
) {
    // One ViewModel per paywall shown: it outlives configuration changes, so a purchase in flight
    // still reports to the recreated screen, and a paywall shown again starts afresh.
    val instance = rememberSaveable { UUID.randomUUID().toString() }
    val viewModel = viewModel(key = "com.vxhub.ui.paywall.$instance") {
        PaywallViewModel(configuration.analyticsEventSet)
    }
    val context = LocalContext.current
    val latestOnResult by rememberUpdatedState(onResult)
    val latestOnRestoreStateChange by rememberUpdatedState(onRestoreStateChange)
    val latestOnRedeemCodeTapped by rememberUpdatedState(onRedeemCodeTapped)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PaywallEvent.Purchased -> latestOnResult(VxPaywallResult.Purchased(event.productId))
                PaywallEvent.Restored -> {
                    latestOnResult(VxPaywallResult.Restored)
                    latestOnRestoreStateChange(true)
                }
                PaywallEvent.NotRestored -> latestOnRestoreStateChange(false)
            }
        }
    }

    val closeDelay = configuration.closeDelay
    var isCloseRevealed by rememberSaveable { mutableStateOf(closeDelay <= Duration.ZERO) }
    LaunchedEffect(closeDelay) {
        if (!isCloseRevealed) {
            delay(closeDelay)
            isCloseRevealed = true
        }
    }
    val state = viewModel.state
    val showsCloseButton = configuration.closeEnabled && isCloseRevealed && !state.isLoading

    val actions = remember(viewModel, context) {
        PaywallActions(
            onSelect = viewModel::select,
            onTrialToggle = viewModel::setTrialEnabled,
            onPurchase = {
                val activity = context.findActivity() ?: VxHub.Internal.currentActivity
                if (activity != null) viewModel.purchase { product -> VxHub.purchase(activity, product) }
            },
            onRestore = viewModel::restore,
            onTerms = { VxHub.showEula(context, fullScreen = false, showCloseButton = true) },
            onPrivacy = { VxHub.showPrivacy(context, fullScreen = false, showCloseButton = true) },
            onRedeemCode = { latestOnRedeemCodeTapped() },
            onClose = { if (!viewModel.state.isLoading) latestOnResult(VxPaywallResult.Dismissed) },
        )
    }

    BackHandler { if (showsCloseButton) actions.onClose() }
    PaywallSystemBars(configuration.lightMode)
    PaywallScreen(configuration, state, showsCloseButton, actions, modifier)
    if (viewModel.showsConnectionAlert) {
        PaywallConnectionAlert(onDismiss = viewModel::dismissConnectionAlert)
    }
}

/**
 * Shows the main paywall full screen from any context — iOS's `showMainPaywall`, `showPaywallV3`
 * and `showPaywallV4` presented over the current screen. The paywall closes itself after
 * [onResult] reports a purchase, a restore or a dismissal. See [VxPaywall] for what each callback
 * receives.
 *
 * ```
 * VxHubUI.showPaywall(
 *     context = activity,
 *     configuration = VxMainPaywallV4Configuration(ctaButtonColor = brandColor, trustText = "Cancel anytime"),
 *     onResult = { result -> if (result !is VxPaywallResult.Dismissed) unlockPremium() },
 * )
 * ```
 */
public fun VxHubUI.showPaywall(
    context: Context,
    configuration: VxPaywallConfiguration,
    onResult: (VxPaywallResult) -> Unit,
    onRestoreStateChange: (Boolean) -> Unit = {},
    onRedeemCodeTapped: () -> Unit = {},
) {
    VxScreenHost.launch(context) {
        var hasFinished by remember { mutableStateOf(false) }
        VxPaywall(
            configuration = configuration,
            onResult = { result ->
                if (!hasFinished) {
                    hasFinished = true
                    onResult(result)
                    dismiss()
                }
            },
            onRestoreStateChange = onRestoreStateChange,
            onRedeemCodeTapped = onRedeemCodeTapped,
        )
    }
}

/** Draws the layout [configuration] asks for. */
@Composable
internal fun PaywallScreen(
    configuration: VxPaywallConfiguration,
    state: PaywallUiState,
    showsCloseButton: Boolean,
    actions: PaywallActions,
    modifier: Modifier = Modifier,
) {
    when (configuration) {
        is VxMainPaywallConfiguration -> when (configuration.paywallType) {
            VxMainPaywallType.V1 -> PaywallV1(configuration, state, showsCloseButton, actions, modifier)
            VxMainPaywallType.V2 -> PaywallV2(configuration, state, showsCloseButton, actions, modifier)
        }
        is VxMainPaywallV3Configuration -> {
            val style = remember(configuration) { configuration.toHeroStyle() }
            HeroPaywall(style, state, showsCloseButton, actions, modifier)
        }
        is VxMainPaywallV4Configuration -> {
            val style = remember(configuration) { configuration.toHeroStyle() }
            HeroPaywall(style, state, showsCloseButton, actions, modifier)
        }
    }
}
