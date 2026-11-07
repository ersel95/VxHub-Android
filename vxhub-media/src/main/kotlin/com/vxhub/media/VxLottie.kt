package com.vxhub.media

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import java.io.FileInputStream

/**
 * Plays a Lottie animation by [name]: a file downloaded with [VxMedia.downloadLottieAnimation],
 * else an asset (`name` or `name.json`), else — when [name] is a URL — straight from the network.
 * The Compose form of iOS's `createAndPlayAnimation`.
 */
@Composable
public fun VxLottie(
    name: String,
    modifier: Modifier = Modifier,
    loop: Boolean = false,
    speed: Float = 1f,
    contentScale: ContentScale = ContentScale.Fit,
    onFinished: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val spec = remember(name) { lottieSpec(context, name) }
    val composition by rememberLottieComposition(spec)
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = if (loop) LottieConstants.IterateForever else 1,
        speed = speed,
    )
    if (!loop && onFinished != null) {
        LaunchedEffect(composition, progress) {
            if (composition != null && progress >= 1f) onFinished()
        }
    }
    LottieAnimation(composition = composition, progress = { progress }, modifier = modifier, contentScale = contentScale)
}

private fun lottieSpec(context: Context, name: String): LottieCompositionSpec {
    VxMedia.downloadedAnimation(context, name)?.let { return LottieCompositionSpec.File(it.absolutePath) }
    if (name.startsWith("http://") || name.startsWith("https://")) return LottieCompositionSpec.Url(name)
    val assets = runCatching { context.assets.list("").orEmpty().toSet() }.getOrDefault(emptySet())
    return LottieCompositionSpec.Asset(if (name in assets || name.endsWith(".json")) name else "$name.json")
}

/**
 * Lottie for View-based screens, keyed by an integer tag — iOS's `createAndPlayAnimation(name:in:tag:)`,
 * `stopAnimation(with:)`, `removeAnimation(with:)` and friends.
 */
public object VxLottieViews {
    private val views = mutableMapOf<Int, LottieAnimationView>()

    /** Adds a full-size animation view to [parent] and plays it. */
    public fun createAndPlayAnimation(
        name: String,
        parent: ViewGroup,
        tag: Int,
        removeOnFinish: Boolean = true,
        loop: Boolean = false,
        speed: Float = 1f,
        scaleType: ImageView.ScaleType = ImageView.ScaleType.FIT_CENTER,
        onComplete: (() -> Unit)? = null,
    ) {
        removeAnimation(tag)
        val context = parent.context
        val view = LottieAnimationView(context).apply {
            this.scaleType = scaleType
            repeatCount = if (loop) LottieDrawable.INFINITE else 0
            this.speed = speed
            val downloaded = VxMedia.downloadedAnimation(context, name)
            when {
                downloaded != null -> setAnimation(FileInputStream(downloaded), downloaded.absolutePath)
                name.startsWith("http") -> setAnimationFromUrl(name)
                name.endsWith(".json") -> setAnimation(name)
                else -> setAnimation("$name.json")
            }
            addAnimatorListener(
                object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        onComplete?.invoke()
                        if (removeOnFinish && !loop) removeAnimation(tag)
                    }
                },
            )
        }
        parent.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        views[tag] = view
        view.playAnimation()
    }

    public fun stopAnimation(tag: Int) {
        views[tag]?.pauseAnimation()
    }

    public fun stopAllAnimations() {
        views.values.forEach { it.pauseAnimation() }
    }

    public fun removeAnimation(tag: Int) {
        views.remove(tag)?.let { view ->
            view.cancelAnimation()
            (view.parent as? ViewGroup)?.removeView(view)
        }
    }

    public fun removeAllAnimations() {
        views.keys.toList().forEach(::removeAnimation)
    }

    public fun isAnimationActive(tag: Int): Boolean = views[tag]?.isAnimating == true
}
