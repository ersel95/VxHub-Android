package com.vxhub.ui.support

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vxhub.VxHub
import com.vxhub.ui.R
import com.vxhub.ui.internal.vxString

/**
 * iOS's `TicketsBottomSheetView`: the panel's support categories in a 349 dp sheet over a dimmed
 * screen. Choosing one starts a ticket in it; tapping the dimmed area closes the sheet.
 *
 * When the panel has no categories, iOS shows the sheet empty. Here it offers the panel's support
 * address instead, when there is one, so the sheet is never a dead end.
 */
@Composable
internal fun CategorySheet(
    visible: Boolean,
    colors: SupportColors,
    font: FontFamily,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val deviceInfo by VxHub.deviceInfo.collectAsStateWithLifecycle()
    val categories = deviceInfo?.support?.categories.orEmpty()
    val supportEmail = deviceInfo?.appConfig?.supportEmail?.takeIf { it.isNotBlank() }
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(ANIMATION_MS)), exit = fadeOut(tween(ANIMATION_MS))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClickLabel = stringResource(R.string.vxhub_close),
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(ANIMATION_MS, easing = LinearOutSlowInEasing)) { it } + fadeIn(tween(ANIMATION_MS)),
            exit = slideOutVertically(tween(ANIMATION_MS, easing = FastOutLinearInEasing)) { it } + fadeOut(tween(ANIMATION_MS)),
        ) {
            val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(349.dp)
                    .shadow(10.dp, shape, ambientColor = colors.sheetShadow, spotColor = colors.sheetShadow)
                    .background(colors.sheetBackground, shape)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 11.dp),
            ) {
                if (categories.isNotEmpty()) {
                    itemsIndexed(categories) { index, category ->
                        CategoryRow(vxString(category), colors, font, onClick = { onSelect(category) })
                        // iOS's separator: grey at 30 %, between rows only.
                        if (index < categories.lastIndex) HorizontalDivider(thickness = Dp.Hairline, color = Color.Gray.copy(alpha = 0.3f))
                    }
                } else if (supportEmail != null) {
                    item {
                        CategoryRow(supportEmail, colors, font, onClick = {
                            onDismiss()
                            composeEmail(context, supportEmail)
                        })
                    }
                }
            }
        }
    }
}

/** iOS's `TicketCell`: one category, 51 dp tall. */
@Composable
private fun CategoryRow(title: String, colors: SupportColors, font: FontFamily, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(51.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = title,
            color = colors.sheetText,
            fontFamily = font,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun composeEmail(context: Context, address: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null))
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No mail app: nothing sensible to fall back to.
    }
}

private const val ANIMATION_MS = 300
