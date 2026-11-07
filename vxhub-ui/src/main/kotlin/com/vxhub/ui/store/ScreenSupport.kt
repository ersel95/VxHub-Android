// Shared by the promo offer and the store. These helpers belong in com.vxhub.ui.internal; they
// live here only because this port was scoped to the promo and store packages.
package com.vxhub.ui.store

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vxhub.VxHub
import com.vxhub.providers.VxBannerType
import com.vxhub.purchases.VxProduct
import com.vxhub.purchases.VxPurchaseResult
import com.vxhub.purchases.VxRestoreResult
import com.vxhub.ui.VxFont
import com.vxhub.ui.fontFamily
import com.vxhub.ui.internal.VxScreenScope
import com.vxhub.ui.internal.vxString
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

internal const val LOG_TAG: String = "VxHub"

// region Screen-scoped view models

/**
 * One [ViewModelStore] per screen instance. A screen's state — an in-flight purchase above all —
 * then survives configuration changes, yet is cleared when the screen leaves composition, even
 * when the hosting activity (an app embedding the composable) lives on.
 */
internal class ScreenViewModelStores : ViewModel() {
    private val stores = HashMap<String, ViewModelStore>()

    fun storeFor(id: String): ViewModelStore = stores.getOrPut(id) { ViewModelStore() }

    fun release(id: String) {
        stores.remove(id)?.clear()
    }

    override fun onCleared() {
        stores.values.forEach(ViewModelStore::clear)
        stores.clear()
    }
}

/** A view model that lives exactly as long as this call site stays in composition. */
@Composable
internal inline fun <reified VM : ViewModel> rememberScreenViewModel(crossinline create: () -> VM): VM {
    val id = rememberSaveable { UUID.randomUUID().toString() }
    val stores = viewModel { ScreenViewModelStores() }
    val activity = LocalActivity.current
    DisposableEffect(stores, id) {
        onDispose {
            // A configuration change recomposes the screen with the same id: keep its state.
            if (activity?.isChangingConfigurations != true) stores.release(id)
        }
    }
    return remember(stores, id) {
        ViewModelProvider(stores.storeFor(id), viewModelFactory { initializer { create() } })[VM::class.java]
    }
}

// endregion

// region Purchases

/** What a screen makes of a purchase or restore result. */
internal enum class PurchaseFeedback {
    /** Verified by the backend: the screen reports success. */
    SUCCESS,

    /** Paid (or paying) but not confirmed yet: not a failure, so the user is told it is on its way. */
    PENDING,

    /** Cancelled or failed: nothing to report, as on iOS. */
    NONE,
}

internal val VxPurchaseResult.feedback: PurchaseFeedback
    get() = when (this) {
        is VxPurchaseResult.Verified -> PurchaseFeedback.SUCCESS
        VxPurchaseResult.PendingVerification, VxPurchaseResult.PaymentPending -> PurchaseFeedback.PENDING
        VxPurchaseResult.Cancelled, is VxPurchaseResult.Failed -> PurchaseFeedback.NONE
    }

/** A restore succeeds when it leaves the user premium — iOS's `hasActiveSubscription`. */
internal val VxRestoreResult.feedback: PurchaseFeedback
    get() = when (this) {
        is VxRestoreResult.Restored -> if (isPremium) PurchaseFeedback.SUCCESS else PurchaseFeedback.NONE
        VxRestoreResult.PendingVerification -> PurchaseFeedback.PENDING
        VxRestoreResult.NothingToRestore, is VxRestoreResult.Failed -> PurchaseFeedback.NONE
    }

/** [VxHub.purchase], with a thrown error (VxHub not initialized, a provider bug) reported as a failure. */
internal suspend fun purchaseCatching(activity: Activity, product: VxProduct): VxPurchaseResult = try {
    VxHub.purchase(activity, product)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.w(LOG_TAG, "Purchase of ${product.id} failed", e)
    VxPurchaseResult.Failed(e.message)
}

/** [VxHub.restorePurchases], with a thrown error reported as a failure. */
internal suspend fun restoreCatching(): VxRestoreResult = try {
    VxHub.restorePurchases()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.w(LOG_TAG, "Restore failed", e)
    VxRestoreResult.Failed(e.message)
}

/** Panel key of the banner shown while a paid purchase waits for confirmation. iOS has no such case. */
internal const val PURCHASE_PENDING_KEY: String = "Subscription_SubscriptionPending"

/** Tells the user a paid purchase is on its way, instead of leaving it looking lost. */
internal fun showPurchasePendingBanner() {
    VxHub.showBanner(
        vxString(PURCHASE_PENDING_KEY, "Your purchase is being processed and will be activated shortly."),
        VxBannerType.INFO,
    )
}

/** The app's debuggable flag: iOS's `#if DEBUG`, which the package sees as the app's build. */
internal val Context.isDebuggableApp: Boolean
    get() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

// endregion

// region Components

internal fun VxFont.textStyle(size: TextUnit, weight: FontWeight, color: Color = Color.Unspecified): TextStyle =
    TextStyle(fontFamily = fontFamily, fontSize = size, fontWeight = weight, color = color)

/** Title, or a spinner while [isLoading] — iOS's `VxLoadingButton`. Not clickable while loading. */
@Composable
internal fun VxLoadingButton(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    background: Brush,
    contentColor: Color,
    shape: Shape,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .clickable(enabled = enabled && !isLoading, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = contentColor,
                strokeWidth = 2.dp,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round,
            )
        } else {
            Text(
                text = text,
                style = textStyle,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

/** A one-button alert — iOS's `VxAlertManager.present`. */
internal data class VxAlertContent(val title: String, val message: String, val buttonTitle: String)

@Composable
internal fun VxAlert(alert: VxAlertContent, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(alert.buttonTitle) } },
        title = { Text(alert.title) },
        text = { Text(alert.message) },
    )
}

/**
 * Status and navigation bar icons that read on the screen's background. Only for screens VxHub
 * presents itself: an app embedding the composable keeps control of its own bars.
 */
@Composable
internal fun VxScreenScope.SystemBarIcons(darkIcons: Boolean) {
    val activity = activity
    DisposableEffect(activity, darkIcons) {
        val transparent = android.graphics.Color.TRANSPARENT
        if (darkIcons) {
            // Before API 26 navigation bar icons cannot turn dark, so that bar keeps a dark scrim there.
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(transparent, transparent),
                navigationBarStyle = SystemBarStyle.light(transparent, LEGACY_NAVIGATION_BAR_SCRIM),
            )
        } else {
            activity.enableEdgeToEdge(SystemBarStyle.dark(transparent), SystemBarStyle.dark(transparent))
        }
        onDispose { }
    }
}

/** androidx.activity's default dark scrim. */
private const val LEGACY_NAVIGATION_BAR_SCRIM: Int = 0x801B1B1B.toInt()

// endregion
