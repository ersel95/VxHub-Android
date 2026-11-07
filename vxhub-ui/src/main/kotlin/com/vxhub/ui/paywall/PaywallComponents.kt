package com.vxhub.ui.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RawRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import com.vxhub.ui.R
import com.vxhub.ui.VxFont
import com.vxhub.ui.fontFamily
import com.vxhub.ui.internal.VxIcons
import com.vxhub.ui.internal.VxMarkupText
import com.vxhub.ui.internal.vxString

internal fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context != null) {
        if (context is Activity) return context
        context = (context as? ContextWrapper)?.baseContext
    }
    return null
}

/** iOS's `setFont(font, size:, weight:)` plus a colour. */
internal fun VxFont.textStyle(size: Int, weight: FontWeight, color: Color): TextStyle =
    TextStyle(color = color, fontSize = size.sp, fontWeight = weight, fontFamily = fontFamily)

/** A label — iOS's `VxLabel`: renders the panel's `[b]`, `[color]` and `[url]` markup. */
@Composable
internal fun PaywallText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    VxMarkupText(text, style, modifier, textAlign, maxLines)
}

@Composable
internal fun PaywallText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(text = text, modifier = modifier, textAlign = textAlign, overflow = TextOverflow.Ellipsis, maxLines = maxLines, style = style)
}

/** The purchase button: a spinner replaces the title while loading — iOS's `VxLoadingButton`. */
@Composable
internal fun PaywallButton(
    text: String,
    textStyle: TextStyle,
    background: Brush,
    shape: Shape,
    minHeight: Dp,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(shape)
            .background(background)
            .clickable(enabled = !isLoading, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Kept while loading, invisible, so the button keeps its size and its accessibility label.
        PaywallText(text, textStyle, Modifier.alpha(if (isLoading) 0f else 1f), textAlign = TextAlign.Center)
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.dp,
                trackColor = Color.Transparent,
            )
        }
    }
}

/**
 * The close button, drawn [visualSize] large with its top-start corner at iOS's [start] and [top]
 * (below the status bar), inside a 44 dp touch target.
 */
@Composable
internal fun PaywallCloseButton(
    start: Dp,
    top: Dp,
    visualSize: Dp,
    iconSize: Dp,
    tint: Color,
    background: Color,
    onClick: () -> Unit,
) {
    val inset = (CloseTouchTarget - visualSize) / 2
    Box(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start))
            .padding(start = (start - inset).coerceAtLeast(0.dp), top = (top - inset).coerceAtLeast(0.dp))
            .size(CloseTouchTarget)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(visualSize).background(background, CircleShape), contentAlignment = Alignment.Center) {
            Icon(VxIcons.Close, contentDescription = stringResource(R.string.vxhub_close), modifier = Modifier.size(iconSize), tint = tint)
        }
    }
}

private val CloseTouchTarget = 44.dp

/** "Restore Purchase | Terms of Use | Privacy Policy" — wraps when a translation is long. */
@Composable
internal fun PaywallFooter(
    links: List<Pair<String, () -> Unit>>,
    linkStyle: TextStyle,
    separator: String,
    separatorStyle: TextStyle,
    spacing: Dp,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        links.forEachIndexed { index, (title, onClick) ->
            key(index) {
                if (index > 0) PaywallText(separator, separatorStyle, maxLines = 1)
                PaywallText(
                    text = title,
                    style = linkStyle,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onClick)
                        .padding(vertical = 8.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

/** A benefit's icon: its drawable, else a check-mark, tinted as [VxPaywallFeature.tintIcon] says. */
@Composable
internal fun PaywallFeatureIcon(feature: VxPaywallFeature, size: Dp, tint: Color) {
    val icon = feature.icon
    if (icon == null) {
        Icon(VxIcons.CheckCircle, contentDescription = null, modifier = Modifier.size(size), tint = tint)
    } else {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(size),
            tint = if (feature.tintIcon) tint else Color.Unspecified,
        )
    }
}

/**
 * V1's and V2's benefit line — iOS's `VxPaywallDescriptionItem`: an icon in an [iconFrame] square
 * and the text beside it, 6 dp above and below.
 */
@Composable
internal fun PaywallDescriptionItem(feature: VxPaywallFeature, style: TextStyle, iconFrame: Dp, iconSize: Dp, tint: Color) {
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.padding(vertical = 6.dp).size(iconFrame), contentAlignment = Alignment.Center) {
            PaywallFeatureIcon(feature, iconSize, tint)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f)
                .padding(vertical = 6.dp)
                .heightIn(min = iconFrame),
            contentAlignment = Alignment.CenterStart,
        ) {
            PaywallText(feature.text, style)
        }
    }
}

/** V3's and V4's benefit line — iOS's `makeFeatureRow`. */
@Composable
internal fun PaywallFeatureRow(feature: VxPaywallFeature, style: TextStyle, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PaywallFeatureIcon(feature, 20.dp, tint)
        Spacer(Modifier.width(10.dp))
        PaywallText(feature.text, style)
    }
}

/** iOS's "check your internet connection" alert. */
@Composable
internal fun PaywallConnectionAlert(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(vxString("InternetConnection_CheckYourInternetConnectionButtonLabel")) }
        },
        title = { Text(vxString("InternetConnection_CheckYourInternetConnection")) },
        text = { Text(vxString("InternetConnection_CheckYourInternetConnectionDescription")) },
    )
}

/**
 * Dark status and navigation bar icons on a light paywall, light ones on a dark paywall; the
 * window's previous appearance comes back when the paywall leaves.
 */
@Composable
internal fun PaywallSystemBars(isLightMode: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view, isLightMode) {
        val window = view.context.findActivity()?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val lightStatusBars = controller.isAppearanceLightStatusBars
        val lightNavigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = isLightMode
        controller.isAppearanceLightNavigationBars = isLightMode
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatusBars
            controller.isAppearanceLightNavigationBars = lightNavigationBars
        }
    }
}

/**
 * V2's background video: a `res/raw` video, muted, looping and stretched to the bounds (iOS's
 * `.resize` gravity). It pauses while the screen is stopped and never takes audio focus.
 */
@Composable
internal fun PaywallVideo(@RawRes video: Int, modifier: Modifier = Modifier) {
    key(video) {
        val context = LocalContext.current
        val player = remember { LoopingVideoPlayer(context.applicationContext, video) }
        LifecycleStartEffect(player) {
            player.play()
            onStopOrDispose { player.pause() }
        }
        DisposableEffect(player) {
            onDispose { player.release() }
        }
        AndroidView(factory = { TextureView(it).apply { surfaceTextureListener = player } }, modifier = modifier)
    }
}

private class LoopingVideoPlayer(private val context: Context, @RawRes private val video: Int) : TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var isPrepared = false
    private var shouldPlay = true

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        release()
        val output = Surface(texture)
        val mediaPlayer = MediaPlayer()
        surface = output
        player = mediaPlayer
        try {
            mediaPlayer.setDataSource(context, "android.resource://${context.packageName}/$video".toUri())
            mediaPlayer.setSurface(output)
            mediaPlayer.isLooping = true
            mediaPlayer.setVolume(0f, 0f)
            mediaPlayer.setOnPreparedListener { prepared ->
                isPrepared = true
                if (shouldPlay) prepared.start()
            }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                isPrepared = false
                true
            }
            mediaPlayer.prepareAsync()
        } catch (_: Exception) {
            // A missing or unreadable video leaves the area black, as a missing iOS bundle video does.
            release()
        }
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        release()
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    fun play() {
        shouldPlay = true
        val mediaPlayer = player ?: return
        if (isPrepared && !mediaPlayer.isPlaying) mediaPlayer.start()
    }

    fun pause() {
        shouldPlay = false
        val mediaPlayer = player ?: return
        if (isPrepared && mediaPlayer.isPlaying) mediaPlayer.pause()
    }

    fun release() {
        player?.release()
        player = null
        isPrepared = false
        surface?.release()
        surface = null
    }
}

/** iOS's `landing_bg` overlay for V2's video: clear at the top, black behind the products. */
internal val PaywallLandingGradient: Brush = Brush.verticalGradient(
    0f to Color.Transparent,
    0.46f to Color.Transparent,
    0.52f to Color.Black.copy(alpha = 0.33f),
    0.57f to Color.Black.copy(alpha = 0.6f),
    0.64f to Color.Black.copy(alpha = 0.77f),
    0.74f to Color.Black,
    1f to Color.Black,
)

/**
 * The paywalls' own glyphs, drawn as paths because `vxhub-ui` ships no image assets: SF Symbols
 * get their Material counterparts, iOS's bundled PNGs are redrawn.
 */
internal object PaywallIcons {
    /** SF `clock.arrow.circlepath`, V1's "Cancel anytime". */
    val History: ImageVector by lazy {
        icon(
            "History",
            "M13,3c-4.97,0 -9,4.03 -9,9L1,12l3.89,3.89 0.07,0.14L9,12L6,12c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7" +
                "c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9z" +
                "M12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08L13.5,8L12,8z",
        )
    }

    /** SF `lock.shield.fill`, V3's and V4's trust line: a shield with a padlock cut out. */
    val LockShield: ImageVector by lazy {
        icon(
            "LockShield",
            "M12,1L3,5v6c0,5.55 3.84,10.74 9,12c5.16,-1.26 9,-6.45 9,-12V5L12,1z" +
                "M9,11h6v5h-6z" +
                "M10,11V9.5a2,2 0,0 1,4 0V11h-1V9.5a1,1 0,0 0,-2 0V11z",
            fillType = PathFillType.EvenOdd,
        )
    }

    val Star: ImageVector by lazy {
        icon("Star", "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z")
    }

    /** SF `star.leadinghalf.filled`: mirrored in right-to-left layouts. */
    val StarHalf: ImageVector by lazy {
        icon(
            "StarHalf",
            "M22,9.24l-7.19,-0.62L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21 12,17.27 18.18,21l-1.63,-7.03L22,9.24z" +
                "M12,15.4V6.1l1.71,4.04 4.38,0.38 -3.32,2.88 1,4.28L12,15.4z",
            autoMirror = true,
        )
    }

    val StarOutline: ImageVector by lazy {
        icon(
            "StarOutline",
            "M22,9.24l-7.19,-0.62L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21 12,17.27 18.18,21l-1.63,-7.03L22,9.24z" +
                "M12,15.4l-3.76,2.27 1,-4.28 -3.32,-2.88 4.38,-0.38L12,6.1l1.71,4.04 4.38,0.38 -3.32,2.88 1,4.28L12,15.4z",
        )
    }

    /** iOS's `best-offer-badge`: a ribbon tab hanging from a product's top border. */
    val BestOfferBadgeV1: ImageVector by lazy { badge("BestOfferBadgeV1", ears = Color(0xFF92D8FF), body = Color(0xFF2998D6)) }

    /** iOS's `best-offer-badge-v2`. */
    val BestOfferBadgeV2: ImageVector by lazy { badge("BestOfferBadgeV2", ears = Color(0xFF49233D), body = Color(0xFFBE0DA7)) }

    private fun icon(
        name: String,
        pathData: String,
        fillType: PathFillType = PathFillType.NonZero,
        autoMirror: Boolean = false,
    ): ImageVector = ImageVector.Builder(
        name = "VxPaywall.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror,
    ).addPath(pathData = PathParser().parsePathString(pathData).toNodes(), pathFillType = fillType, fill = SolidColor(Color.Black))
        .build()

    // Traced from the iOS asset (346 × 59 px at 3x): folded "ears" at the top corners, then a tab
    // narrowing towards its rounded bottom.
    private const val BADGE_EARS =
        "M18,2C8,3 1,8 0,16L38,16C36,10 28,4 18,2ZM328,2C338,3 345,8 346,16L308,16C310,10 318,4 328,2Z"
    private const val BADGE_BODY =
        "M18,2L328,2C318,4 310,10 308,16L295,43C290,52 280,59 264,59L82,59C66,59 56,52 51,43L38,16C36,10 28,4 18,2Z"

    private fun badge(name: String, ears: Color, body: Color): ImageVector = ImageVector.Builder(
        name = "VxPaywall.$name",
        defaultWidth = 115.dp,
        defaultHeight = 19.dp,
        viewportWidth = 346f,
        viewportHeight = 59f,
    ).addPath(pathData = PathParser().parsePathString(BADGE_EARS).toNodes(), fill = SolidColor(ears))
        .addPath(pathData = PathParser().parsePathString(BADGE_BODY).toNodes(), fill = SolidColor(body))
        .build()
}
