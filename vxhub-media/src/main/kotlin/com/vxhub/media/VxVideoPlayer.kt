package com.vxhub.media

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import com.vxhub.VxHub
import java.io.File

/**
 * Plays a video — typically an onboarding or paywall background. A file VxHub downloaded with
 * [VxHub.downloadVideo] plays from disk; otherwise it streams. The player is released with the
 * composable.
 */
// ContentFrame is Media3's Compose video surface; it is still marked unstable.
@OptIn(UnstableApi::class)
@Composable
public fun VxVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    loop: Boolean = true,
    muted: Boolean = true,
    autoPlay: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val player = remember(url) {
        val source: File? = runCatching { VxHub.downloadedVideoFile(url) }.getOrNull()
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(source?.let { MediaItem.fromUri(it.toURI().toString()) } ?: MediaItem.fromUri(url))
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (muted) 0f else 1f
            playWhenReady = autoPlay
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    ContentFrame(player = player, modifier = modifier, contentScale = contentScale)
}
