package com.vxhub.ui.support

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vxhub.ui.R
import com.vxhub.ui.VxHubUI
import com.vxhub.ui.fontFamily
import com.vxhub.ui.internal.VxIcons
import com.vxhub.ui.internal.VxScreenHost
import com.vxhub.ui.internal.vxString
import java.util.UUID

/**
 * The support screens — iOS's `VxSupportView` / `VxSupportViewController`: the user's tickets
 * (pull to refresh, unread dots), a new ticket through the panel's categories, and each ticket's
 * conversation.
 *
 * A plain composable; give it the whole screen. Back goes from a conversation to the list, and
 * from the list to [onClose]. Text comes from the panel's translations (`Support_…` keys).
 *
 * ```
 * VxSupport(onClose = { showSupport = false })
 * ```
 *
 * @param configuration colours, font and artwork; the defaults match iOS.
 * @param onClose the user left the screen with back or the back arrow.
 */
@Composable
public fun VxSupport(
    configuration: VxSupportConfiguration = VxSupportConfiguration(),
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // One view model per appearance of the screen, kept across configuration changes.
    val instance = rememberSaveable { UUID.randomUUID().toString() }
    val viewModel = viewModel(key = "com.vxhub.ui.support:$instance") { SupportViewModel(VxHubSupportService) }
    SupportScreen(viewModel, configuration, onClose, modifier)
}

/**
 * Shows the support screens full screen from any context — iOS's `showContactUs(from:configuration:)`.
 * For Compose apps that present it themselves, see [VxSupport].
 */
public fun VxHubUI.showSupport(context: Context, configuration: VxSupportConfiguration = VxSupportConfiguration()) {
    VxScreenHost.launch(context) {
        val colors = configuration.rememberColors()
        HostedSystemBars(activity, colors.background)
        MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
            VxSupport(configuration = configuration, onClose = dismiss)
        }
    }
}

@Composable
private fun SupportScreen(
    viewModel: SupportViewModel,
    configuration: VxSupportConfiguration,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = configuration.rememberColors()
    val font = configuration.font.fontFamily
    val currentOnClose by rememberUpdatedState(onClose)
    val focusManager = LocalFocusManager.current
    val back = { if (!viewModel.back()) currentOnClose() }
    BackHandler(onBack = back)

    // The host's typography (line heights, letter spacing) must not leak into iOS's metrics.
    CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = font)) {
        Box(modifier.fillMaxSize().background(colors.background)) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
                SupportTopBar(
                    colors = colors,
                    font = font,
                    onBack = back,
                    showsNewChat = state.route == SupportRoute.TICKETS,
                    newChatEnabled = !state.tickets.isLoading,
                    onNewChat = {
                        focusManager.clearFocus()
                        viewModel.showCategories()
                    },
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.headerLine))
                AnimatedContent(
                    targetState = state.route,
                    transitionSpec = {
                        // iOS pushes the conversation on a navigation stack.
                        if (targetState == SupportRoute.CONVERSATION) {
                            (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                        } else {
                            (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                        }
                    },
                    label = "VxSupportRoute",
                ) { route ->
                    when (route) {
                        SupportRoute.TICKETS -> TicketList(
                            state = state.tickets,
                            colors = colors,
                            font = font,
                            onRefresh = { viewModel.loadTickets(pullToRefresh = true) },
                            onOpen = viewModel::openTicket,
                            onNewChat = viewModel::showCategories,
                        )
                        SupportRoute.CONVERSATION -> Conversation(
                            state = state.conversation,
                            field = viewModel.messageField,
                            configuration = configuration,
                            colors = colors,
                            font = font,
                            onSend = viewModel::send,
                            onReload = viewModel::reloadConversation,
                        )
                    }
                }
            }
            CategorySheet(
                visible = state.isCategorySheetVisible,
                colors = colors,
                font = font,
                onSelect = viewModel::startTicket,
                onDismiss = viewModel::hideCategories,
            )
        }
    }
}

/** iOS's navigation bar: back arrow, centred "Support", and the new-chat button on the list. */
@Composable
private fun SupportTopBar(
    colors: SupportColors,
    font: FontFamily,
    onBack: () -> Unit,
    showsNewChat: Boolean,
    newChatEnabled: Boolean,
    onNewChat: () -> Unit,
) {
    val mirrored = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(Modifier.fillMaxWidth().height(56.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
            Icon(
                imageVector = VxIcons.Back,
                contentDescription = stringResource(R.string.vxhub_back),
                tint = colors.navigationTint,
                modifier = if (mirrored) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier,
            )
        }
        Text(
            text = vxString("Support_NavigationTitle"),
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 64.dp).semantics { heading() },
            color = colors.navigationTint,
            fontFamily = font,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showsNewChat) {
            IconButton(
                onClick = onNewChat,
                enabled = newChatEnabled,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
            ) {
                Icon(
                    imageVector = SupportArtwork.NewChat,
                    contentDescription = vxString("Support_NewChatButtonText"),
                    tint = if (newChatEnabled) colors.navigationTint else colors.navigationTint.copy(alpha = 0.4f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** Light or dark status and navigation bar icons for the screen's own background, when VxHub hosts it. */
@Composable
private fun HostedSystemBars(activity: Activity, background: Color) {
    val view = LocalView.current
    val lightBars = background.luminance() > 0.5f
    SideEffect {
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }
}
