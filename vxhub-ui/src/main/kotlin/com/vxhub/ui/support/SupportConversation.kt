package com.vxhub.ui.support

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.vxhub.ui.internal.vxString
import kotlinx.coroutines.delay

/**
 * A conversation — iOS's `TicketDetailRootView`: the messages newest at the bottom (or, for a
 * new ticket, the help prompt) above the message bar.
 */
@Composable
internal fun Conversation(
    state: ConversationState,
    field: TextFieldState,
    configuration: VxSupportConfiguration,
    colors: SupportColors,
    font: FontFamily,
    onSend: () -> Unit,
    onReload: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val fieldFocus = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnReload by rememberUpdatedState(onReload)
    val ticketId = state.ticketId

    // iOS loads a conversation once, when it appears. A chat should show the answer without
    // leaving it, so while it is on screen it also reloads quietly, and once on returning to it.
    LaunchedEffect(ticketId, lifecycleOwner) {
        if (ticketId == null) return@LaunchedEffect
        var resumedBefore = false
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (resumedBefore) currentOnReload() else resumedBefore = true
            while (true) {
                delay(RELOAD_INTERVAL_MS)
                currentOnReload()
            }
        }
    }
    // A new ticket starts with the keyboard up, as iOS's becomeFirstResponder.
    LaunchedEffect(state.isNewTicket) {
        if (state.isNewTicket) fieldFocus.requestFocus()
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // With the keyboard up on a short screen the help artwork can be taller than this
                // area; it must not draw over the title bar.
                .clipToBounds()
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) },
        ) {
            if (state.isNewTicket) {
                HelpPrompt(configuration.detailHelpImage, colors, font, Modifier.align(Alignment.Center))
            } else {
                Messages(state.messages, colors, font)
            }
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.navigationTint)
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.bottomLine)
        MessageBar(field, state.isSending, configuration.detailSendButtonActiveImage, colors, font, fieldFocus, onSend)
    }
}

/** A new ticket's prompt: the help artwork and "How can we help you?". */
@Composable
private fun HelpPrompt(@DrawableRes image: Int?, colors: SupportColors, font: FontFamily, modifier: Modifier) {
    Column(modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (image != null) Image(painterResource(image), contentDescription = null) else HelpOrb()
        Text(
            text = vxString("Support_HelpTitleLabel"),
            color = colors.help,
            fontFamily = font,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

/** The messages, newest at the bottom; jumps to the newest whenever one arrives, as iOS does. */
@Composable
private fun Messages(messages: List<SupportMessage>, colors: SupportColors, font: FontFamily) {
    val newestFirst = remember(messages) { messages.asReversed() }
    val listState = rememberLazyListState()
    val newestId = newestFirst.firstOrNull()?.id
    LaunchedEffect(newestId) {
        if (newestId != null) listState.scrollToItem(0)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(newestFirst, key = { it.id }) { message -> MessageBubble(message, colors, font) }
    }
}

/**
 * iOS's `ChatMessageCell`: the user's messages on the trailing side, filled; the agent's on the
 * leading side, outlined; at most 85 % of the width, the time after the text's last line.
 */
@Composable
private fun MessageBubble(message: SupportMessage, colors: SupportColors, font: FontFamily) {
    val fromUser = message.isFromDevice
    val shape = if (fromUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 0.dp, bottomStart = 16.dp)
    } else {
        RoundedCornerShape(topStart = 0.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        if (fromUser) Spacer(Modifier.weight(1f - BUBBLE_WIDTH_FRACTION))
        Row(
            Modifier
                .weight(BUBBLE_WIDTH_FRACTION, fill = false)
                .then(
                    if (fromUser) {
                        Modifier.background(colors.userBackground, shape)
                    } else {
                        Modifier.background(colors.adminBackground, shape).border(1.dp, colors.adminBorder, shape)
                    },
                )
                .padding(16.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = message.text,
                modifier = Modifier.weight(1f, fill = false),
                color = if (fromUser) colors.userMessage else colors.adminMessage,
                fontFamily = font,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            val time = SupportTime.format(message.createdAt)
            if (time != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = time,
                    color = if (fromUser) colors.userDate else colors.adminDate,
                    fontFamily = font,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
        if (!fromUser) Spacer(Modifier.weight(1f - BUBBLE_WIDTH_FRACTION))
    }
}

/** The message field and, once there is text, the send button. Return sends, as iOS's `.send` key. */
@Composable
private fun MessageBar(
    field: TextFieldState,
    isSending: Boolean,
    @DrawableRes sendImage: Int?,
    colors: SupportColors,
    font: FontFamily,
    focusRequester: FocusRequester,
    onSend: () -> Unit,
) {
    val hasText by remember(field) { derivedStateOf { field.text.isNotEmpty() } }
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.messageBar)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            state = field,
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            textStyle = TextStyle(color = colors.messageFieldText, fontFamily = font, fontSize = 14.sp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
            onKeyboardAction = { onSend() },
            lineLimits = TextFieldLineLimits.SingleLine,
            cursorBrush = SolidColor(colors.messageFieldText),
            decorator = { innerField ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(43.dp)
                        .background(colors.messageFieldBackground, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (field.text.isEmpty()) {
                        Text(
                            text = vxString("Support_TextFieldPlaceholder"),
                            color = colors.placeholder,
                            fontFamily = font,
                            fontSize = 14.sp,
                            maxLines = 1,
                        )
                    }
                    innerField()
                }
            },
        )
        AnimatedVisibility(
            visible = hasText,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Row {
                Spacer(Modifier.width(16.dp))
                SendButton(sendImage, enabled = !isSending, colors = colors, onClick = onSend)
            }
        }
    }
}

/** A 32 dp circle in the user's bubble colour with the paper plane, or the app's own image. */
@Composable
private fun SendButton(@DrawableRes image: Int?, enabled: Boolean, colors: SupportColors, onClick: () -> Unit) {
    val label = vxString("VxSupport_SendButton", "Send")
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .size(32.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(painterResource(image), contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(colors.userBackground, CircleShape))
            Icon(SupportArtwork.Send, contentDescription = null, tint = colors.userMessage, modifier = Modifier.fillMaxSize())
        }
    }
}

private const val BUBBLE_WIDTH_FRACTION = 0.85f
private const val RELOAD_INTERVAL_MS = 15_000L
