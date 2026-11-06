package com.vxhub.ui.banner

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vxhub.VxHub
import com.vxhub.providers.VxBannerProvider
import com.vxhub.providers.VxBannerType
import com.vxhub.ui.internal.VxIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** One banner. */
internal data class VxBannerModel(
    val id: String = UUID.randomUUID().toString(),
    val message: String,
    val type: VxBannerType,
    val actionLabel: String?,
    val action: (() -> Unit)?,
)

/**
 * Queue of banners, shown one at a time — iOS's `VxBannerManager`. Every activity that hosts a
 * banner overlay renders [current].
 */
internal object VxBannerController {
    private val queue = ArrayDeque<VxBannerModel>()
    private val _current = MutableStateFlow<VxBannerModel?>(null)
    val current: StateFlow<VxBannerModel?> = _current.asStateFlow()

    @Synchronized
    fun enqueue(banner: VxBannerModel) {
        if (_current.value == null) _current.value = banner else queue.addLast(banner)
    }

    @Synchronized
    fun dismiss(id: String) {
        if (_current.value?.id != id) return
        _current.value = queue.removeFirstOrNull()
    }

    @Synchronized
    fun dismissAll() {
        queue.clear()
        _current.value = null
    }
}

/** Registered as [com.vxhub.providers.VxProviderRegistry.banner] by [com.vxhub.ui.VxUiInitializer]. */
internal object VxBannerProviderImpl : VxBannerProvider {
    override fun show(message: String, type: VxBannerType, actionLabel: String?, action: (() -> Unit)?) {
        VxBannerController.enqueue(VxBannerModel(message = message, type = type, actionLabel = actionLabel, action = action))
        (VxHub.Internal.currentActivity as? ComponentActivity)?.let { activity ->
            activity.runOnUiThread { attachOverlay(activity) }
        }
    }

    override fun dismissAll() = VxBannerController.dismissAll()

    /**
     * Adds a full-size transparent [ComposeView] over the activity's content the first time a banner
     * shows there. Works for View-based and Compose activities alike.
     */
    private fun attachOverlay(activity: ComponentActivity) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (root.findViewWithTag<ComposeView>(OVERLAY_TAG) != null) return
        val overlay = ComposeView(activity).apply {
            tag = OVERLAY_TAG
            setContent { VxBannerHost() }
        }
        root.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private const val OVERLAY_TAG = "com.vxhub.ui.banner.overlay"
}

/**
 * Renders VxHub banners. VxHub attaches it automatically; place it yourself only to control where
 * banners draw (for example above a bottom sheet).
 */
@Composable
public fun VxBannerHost(modifier: Modifier = Modifier, fontFamily: FontFamily = FontFamily.Default) {
    val banner by VxBannerController.current.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf<VxBannerModel?>(null) }
    LaunchedEffect(banner) {
        visible = banner
        val shown = banner ?: return@LaunchedEffect
        delay(if (shown.action != null) 5_000L else 3_000L)
        VxBannerController.dismiss(shown.id)
    }
    Box(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = visible != null && banner != null,
            enter = slideInVertically(tween(250)) { -it } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(200)) { -it } + fadeOut(tween(200)),
        ) {
            visible?.let { BannerCard(it, fontFamily) }
        }
    }
}

@Composable
private fun BannerCard(banner: VxBannerModel, fontFamily: FontFamily) {
    val dragState = rememberDraggableState { delta -> if (delta < -8f) VxBannerController.dismiss(banner.id) }
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .background(banner.type.background, RoundedCornerShape(16.dp))
            .clickable { VxBannerController.dismiss(banner.id) }
            .draggable(dragState, Orientation.Vertical)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(banner.type.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(
            text = banner.message,
            color = Color.White,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        if (banner.actionLabel != null) {
            Text(
                text = banner.actionLabel,
                color = Color.White,
                fontFamily = fontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier
                    .border(1.dp, Color.White, RoundedCornerShape(8.dp))
                    .clickable {
                        banner.action?.invoke()
                        VxBannerController.dismiss(banner.id)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

// Same colours as iOS's VxBannerTypes.
private val VxBannerType.background: Color
    get() = when (this) {
        VxBannerType.SUCCESS -> Color(0xFF39C675)
        VxBannerType.ERROR -> Color(0xFFDC2626)
        VxBannerType.WARNING -> Color(0xFFEAB308)
        VxBannerType.INFO -> Color(0xFF3B82F6)
        VxBannerType.DEBUG -> Color.Black.copy(alpha = 0.9f)
    }

private val VxBannerType.icon: ImageVector
    get() = when (this) {
        VxBannerType.SUCCESS -> VxIcons.CheckCircle
        VxBannerType.ERROR -> VxIcons.Error
        VxBannerType.WARNING -> VxIcons.Warning
        VxBannerType.INFO -> VxIcons.Info
        VxBannerType.DEBUG -> VxIcons.Search
    }
