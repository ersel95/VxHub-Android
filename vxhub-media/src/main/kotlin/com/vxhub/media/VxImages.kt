package com.vxhub.media

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.vxhub.VxHub

/**
 * An image from a URL, cached by Coil — iOS's SDWebImage-backed `vxSetImage`, for Compose.
 * Images VxHub already downloaded ([VxHub.downloadImage]) are read from disk instead of the network.
 */
@Composable
public fun VxAsyncImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    isLocalized: Boolean = false,
    placeholder: Painter? = null,
    contentScale: ContentScale = ContentScale.Fit,
    showLoadingIndicator: Boolean = true,
    loadingIndicatorColor: Color = Color.Gray,
) {
    val model: Any? = url?.let { VxMedia.localFileOrUrl(it, isLocalized) }
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = {
            when {
                placeholder != null -> androidx.compose.foundation.Image(placeholder, contentDescription, contentScale = contentScale)
                showLoadingIndicator -> Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = loadingIndicatorColor, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
            }
        },
        error = { placeholder?.let { androidx.compose.foundation.Image(it, contentDescription, contentScale = contentScale) } },
    )
}

/**
 * Loads [url] into this view with Coil — iOS's `vxSetImage(on:with:placeholderImage:completion:)`.
 * [onResult] receives whether the image loaded.
 */
public fun ImageView.vxSetImage(
    url: String?,
    placeholder: Drawable? = null,
    isLocalized: Boolean = false,
    onResult: ((Boolean) -> Unit)? = null,
) {
    val model: Any? = url?.let { VxMedia.localFileOrUrl(it, isLocalized) }
    load(model) {
        placeholder(placeholder)
        error(placeholder)
        listener(
            onSuccess = { _, _ -> onResult?.invoke(true) },
            onError = { _, _ -> onResult?.invoke(false) },
        )
    }
}
