package com.vxhub.ui.support

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vxhub.ui.internal.vxString

/** The ticket list — iOS's `VxSupportRootView`: a spinner, the empty state, or the tickets. */
@Composable
internal fun TicketList(
    state: TicketsState,
    colors: SupportColors,
    font: FontFamily,
    onRefresh: () -> Unit,
    onOpen: (SupportTicket) -> Unit,
    onNewChat: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.navigationTint)
            state.tickets.isEmpty() -> EmptyTickets(colors, font, onNewChat)
            else -> {
                val pullState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                    state = pullState,
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = pullState,
                            isRefreshing = state.isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter),
                            color = colors.navigationTint,
                        )
                    },
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 32.dp),
                    ) {
                        items(state.tickets, key = { it.id }) { ticket ->
                            TicketRow(ticket, colors, font, onClick = { onOpen(ticket) })
                        }
                    }
                }
            }
        }
    }
}

/** No tickets yet: iOS's empty-chat stack and its "New Chat" button. */
@Composable
private fun EmptyTickets(colors: SupportColors, font: FontFamily, onNewChat: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 24.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 104.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            EmptyTicketsIcon()
            Text(
                text = vxString("Support_EmptyTicketTitleLabel"),
                color = colors.listingDescription,
                fontFamily = font,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .padding(bottom = 11.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.listingAction)
                .clickable(role = Role.Button, onClick = onNewChat),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = vxString("Support_NewChatButtonText"),
                color = colors.listingActionText,
                fontFamily = font,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** iOS's `TicketListCell`: unread dot, category and last message, and the time on the right. */
@Composable
private fun TicketRow(ticket: SupportTicket, colors: SupportColors, font: FontFamily, onClick: () -> Unit) {
    // The dot and the time sit on the title's first line, which grows with the font scale.
    val firstLine = with(LocalDensity.current) { TITLE_LINE_HEIGHT.toDp() }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!ticket.isSeen) {
                Box(Modifier.height(firstLine), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(8.dp).background(colors.listingUnread, CircleShape))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = vxString(ticket.category),
                    color = colors.listingItemTitle,
                    fontFamily = font,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = TITLE_LINE_HEIGHT,
                )
                val lastMessage = ticket.lastMessage
                if (!lastMessage.isNullOrBlank()) {
                    Text(
                        text = lastMessage,
                        color = colors.listingDescription,
                        fontFamily = font,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val time = SupportTime.format(ticket.lastMessageAt)
            if (time != null) {
                Box(Modifier.height(firstLine), contentAlignment = Alignment.Center) {
                    Text(
                        text = time,
                        color = colors.listingDate,
                        fontFamily = font,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
        // iOS's separator: white at 10 %, which only shows on a dark background.
        HorizontalDivider(thickness = 1.dp, color = Color.White.copy(alpha = 0.1f))
    }
}

private val TITLE_LINE_HEIGHT = 18.sp
