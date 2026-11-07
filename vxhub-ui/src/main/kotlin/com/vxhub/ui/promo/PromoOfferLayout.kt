package com.vxhub.ui.promo

import android.content.Context
import android.content.res.Resources
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RawRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import com.vxhub.ui.store.LOG_TAG
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * [top], then [flexible], then [bottom], all full width. [flexible] takes whatever height
 * [viewportHeight] leaves, but at least [flexibleMinHeight] — iOS's flexible spacer, and the V2
 * video's `height >= 200`. Taller than the viewport, the whole is meant to scroll (put
 * `verticalScroll` on [modifier]), so a small screen never loses the buy button.
 */
@Composable
internal fun StretchColumn(
    viewportHeight: Dp,
    flexibleMinHeight: Dp,
    top: @Composable ColumnScope.() -> Unit,
    flexible: @Composable () -> Unit,
    bottom: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        contents = listOf(
            { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, content = top) },
            flexible,
            { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, content = bottom) },
        ),
        modifier = modifier,
    ) { (topMeasurables, flexibleMeasurables, bottomMeasurables), constraints ->
        val width = constraints.maxWidth
        val columnConstraints = Constraints(minWidth = width, maxWidth = width)
        val topPlaceables = topMeasurables.map { it.measure(columnConstraints) }
        val bottomPlaceables = bottomMeasurables.map { it.measure(columnConstraints) }
        val topHeight = topPlaceables.sumOf { it.height }
        val bottomHeight = bottomPlaceables.sumOf { it.height }
        val flexibleHeight = max(flexibleMinHeight.roundToPx(), viewportHeight.roundToPx() - topHeight - bottomHeight)
        val flexiblePlaceables = flexibleMeasurables.map { it.measure(Constraints.fixed(width, flexibleHeight)) }
        layout(width, topHeight + flexibleHeight + bottomHeight) {
            var y = 0
            topPlaceables.forEach { it.place(0, y); y += it.height }
            flexiblePlaceables.forEach { it.place(0, y) }
            y += flexibleHeight
            bottomPlaceables.forEach { it.place(0, y); y += it.height }
        }
    }
}

/**
 * A row that scrolls by itself, forever — iOS's showcase scroll views, which move their content
 * 1 pt every 0.03 s and jump back by a third (one copy of the items) to loop. [content] is drawn
 * [MarqueeMath.COPIES] times side by side; a forward row moves left, a reversed one right.
 */
@Composable
internal fun Marquee(reverse: Boolean, speed: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val distance = remember { mutableFloatStateOf(0f) }
    val pixelsPerSecond = with(LocalDensity.current) { speed.toPx() }
    val isPreview = LocalInspectionMode.current
    LaunchedEffect(pixelsPerSecond, isPreview) {
        if (isPreview) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                distance.floatValue = MarqueeMath.advance(distance.floatValue, pixelsPerSecond, now - last)
                last = now
            }
        }
    }
    Layout(
        content = {
            repeat(MarqueeMath.COPIES) { copy ->
                // The copies only fill the loop: screen readers read the items once.
                Box(if (copy == 0) Modifier else Modifier.clearAndSetSemantics { }) { content() }
            }
        },
        modifier = modifier.clipToBounds(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints(maxHeight = constraints.maxHeight)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            // Read during placement only: moving the row re-places it without recomposing.
            val copyWidth = placeables.firstOrNull()?.width ?: 0
            val start = MarqueeMath.firstCopyX(distance.floatValue, copyWidth, reverse)
            placeables.forEachIndexed { index, placeable -> placeable.place(start + index * copyWidth, 0) }
        }
    }
}

/** The marquee's arithmetic, kept pure for tests. */
internal object MarqueeMath {
    /** iOS triples its items; two copies always cover the view, three leave slack. */
    const val COPIES: Int = 3

    /**
     * Where the first copy starts, in (-[copyWidth], 0]: a forward row has moved [distance] to the
     * left, a reversed row as far to the right. Copies are identical, so only the distance modulo
     * [copyWidth] matters — the loop has no visible seam.
     */
    fun firstCopyX(distance: Float, copyWidth: Int, reverse: Boolean): Int {
        if (copyWidth <= 0) return 0
        val shift = (distance % copyWidth).roundToInt() % copyWidth
        return if (reverse) {
            if (shift == 0) 0 else shift - copyWidth
        } else {
            -shift
        }
    }

    /** [distance] after [elapsedNanos] at [pixelsPerSecond]. */
    fun advance(distance: Float, pixelsPerSecond: Float, elapsedNanos: Long): Float =
        distance + pixelsPerSecond * elapsedNanos / 1_000_000_000f
}

/**
 * The app's raw video, muted and looping, stretched to fill [modifier]'s bounds — iOS's
 * `AVPlayerLooper` behind an `AVPlayerLayer` with `.resize` gravity. It pauses while the app is in
 * the background. A `TextureView` stretches its content as `.resize` does, and unlike `VideoView`
 * it takes no audio focus, so the user's music keeps playing.
 */
@Composable
internal fun LoopingVideo(@RawRes video: Int, modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        Box(modifier.background(Color.DarkGray))
        return
    }
    val context = LocalContext.current
    val player = remember(video) { LoopingVideoPlayer(context.applicationContext, video) }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    LifecycleStartEffect(player) {
        player.play()
        onStopOrDispose { player.pause() }
    }
    AndroidView(
        factory = { viewContext -> TextureView(viewContext).apply { surfaceTextureListener = player } },
        modifier = modifier,
    )
}

/** A [MediaPlayer] bound to a [TextureView]'s surface for as long as the surface exists. */
internal class LoopingVideoPlayer(private val context: Context, @param:RawRes private val video: Int) : TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var isPrepared = false
    private var wantsToPlay = false

    fun play() {
        wantsToPlay = true
        player?.takeIf { isPrepared && !it.isPlaying }?.start()
    }

    fun pause() {
        wantsToPlay = false
        player?.takeIf { isPrepared && it.isPlaying }?.pause()
    }

    fun release() {
        isPrepared = false
        player?.release()
        player = null
        surface?.release()
        surface = null
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        release()
        val output = Surface(texture)
        surface = output
        val mediaPlayer = MediaPlayer()
        try {
            // Null when the file is stored compressed; aapt leaves .mp4 uncompressed.
            val file = context.resources.openRawResourceFd(video) ?: throw IOException("raw resource $video is compressed")
            file.use { mediaPlayer.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
        } catch (e: Resources.NotFoundException) {
            Log.e(LOG_TAG, "[PromoOffer] Video resource not found: $video", e)
            mediaPlayer.release()
            return
        } catch (e: IOException) {
            Log.e(LOG_TAG, "[PromoOffer] Video could not be opened: $video", e)
            mediaPlayer.release()
            return
        } catch (e: IllegalArgumentException) {
            Log.e(LOG_TAG, "[PromoOffer] Video could not be opened: $video", e)
            mediaPlayer.release()
            return
        }
        mediaPlayer.setSurface(output)
        mediaPlayer.isLooping = true
        mediaPlayer.setVolume(0f, 0f)
        mediaPlayer.setOnPreparedListener { prepared ->
            isPrepared = true
            if (wantsToPlay) prepared.start()
        }
        mediaPlayer.setOnErrorListener { _, what, extra ->
            Log.e(LOG_TAG, "[PromoOffer] Video playback failed ($what, $extra)")
            true
        }
        player = mediaPlayer
        mediaPlayer.prepareAsync()
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int): Unit = Unit

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        release()
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture): Unit = Unit
}
