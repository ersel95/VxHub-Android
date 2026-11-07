package com.vxhub.ui.support

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vxhub.VxHub
import com.vxhub.VxHubException
import com.vxhub.VxTicketMessage
import com.vxhub.providers.VxBannerType
import com.vxhub.ui.internal.vxString
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** What the support screens need from VxHub; a seam so the view model can be tested. */
internal interface SupportService {
    suspend fun tickets(): List<SupportTicket>

    /** Opens a ticket with its first message and returns the ticket's id. */
    suspend fun createTicket(category: String, message: String): String

    suspend fun conversation(ticketId: String): List<SupportMessage>

    suspend fun send(ticketId: String, message: String): SupportMessage

    val isConnected: Boolean

    fun showOfflineBanner()
}

internal object VxHubSupportService : SupportService {
    override suspend fun tickets(): List<SupportTicket> = VxHub.supportTickets().map {
        SupportTicket(it.id, it.category, it.lastMessage, it.lastMessageAt, it.isSeen)
    }

    override suspend fun createTicket(category: String, message: String): String =
        VxHub.createSupportTicket(category, message).id

    override suspend fun conversation(ticketId: String): List<SupportMessage> =
        VxHub.supportTicket(ticketId).messages.map { it.toSupportMessage() }

    override suspend fun send(ticketId: String, message: String): SupportMessage =
        VxHub.sendSupportMessage(ticketId, message).toSupportMessage()

    override val isConnected: Boolean get() = VxHub.isConnected.value

    override fun showOfflineBanner() {
        VxHub.showBanner(vxString("InternetConnection_CheckYourInternetConnection"), VxBannerType.WARNING)
    }

    private fun VxTicketMessage.toSupportMessage() = SupportMessage(id, text, isFromDevice, createdAt)
}

internal enum class SupportRoute { TICKETS, CONVERSATION }

internal data class TicketsState(
    val tickets: List<SupportTicket> = emptyList(),
    /** Loading with nothing to show yet: the list makes way for a spinner, as on iOS. */
    val isLoading: Boolean = false,
    /** Pull to refresh. */
    val isRefreshing: Boolean = false,
)

internal data class ConversationState(
    /** Null while the first message of a new ticket is being written. */
    val ticketId: String? = null,
    /** The category a new ticket is opened with. */
    val category: String? = null,
    /** Oldest first. */
    val messages: List<SupportMessage> = emptyList(),
    /** The first load of the conversation. Reloads after it are quiet. */
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
) {
    /** iOS's `isNewTicket`: the help prompt shows until the first message goes out. */
    val isNewTicket: Boolean get() = ticketId == null && !isSending
}

internal data class SupportUiState(
    val route: SupportRoute = SupportRoute.TICKETS,
    val tickets: TicketsState = TicketsState(isLoading = true),
    val conversation: ConversationState = ConversationState(),
    val isCategorySheetVisible: Boolean = false,
)

/**
 * The support screens' state — iOS's `VxSupportViewModel` plus the bits of its view controllers
 * that decide what shows. A view model so the screen survives configuration changes.
 */
internal class SupportViewModel(private val service: SupportService) : ViewModel() {

    /** The message being written, kept here rather than in composition so it survives rotation. */
    val messageField: TextFieldState = TextFieldState()

    private val _state = MutableStateFlow(SupportUiState())
    val state: StateFlow<SupportUiState> = _state.asStateFlow()

    private var ticketsJob: Job? = null
    private var conversationJob: Job? = null

    /** Changes whenever a conversation opens or closes, so late answers for the previous one are dropped. */
    private var conversationGeneration = 0

    /** Messages sent from here that no reload has returned yet. */
    private val pendingSent = mutableListOf<SupportMessage>()

    init {
        loadTickets(pullToRefresh = false)
    }

    /**
     * Reloads the ticket list. Without [pullToRefresh] it blocks with a spinner only while there
     * is nothing to show; iOS blanks the list on every visit, which flickers on the way back.
     */
    fun loadTickets(pullToRefresh: Boolean) {
        if (pullToRefresh && _state.value.tickets.isRefreshing) return
        ticketsJob?.cancel()
        _state.update {
            it.copy(tickets = it.tickets.copy(isLoading = !pullToRefresh && it.tickets.tickets.isEmpty(), isRefreshing = pullToRefresh))
        }
        ticketsJob = viewModelScope.launch {
            val loaded = attempt { service.tickets() }.getOrNull()
            // A failed load keeps what was there, as iOS does.
            _state.update { it.copy(tickets = TicketsState(tickets = loaded?.distinctBy { ticket -> ticket.id } ?: it.tickets.tickets)) }
        }
    }

    /** The category sheet. Like iOS's new-chat button, unavailable while the list is loading. */
    fun showCategories() {
        if (_state.value.tickets.isLoading) return
        _state.update { it.copy(isCategorySheetVisible = true) }
    }

    fun hideCategories() {
        _state.update { it.copy(isCategorySheetVisible = false) }
    }

    fun openTicket(ticket: SupportTicket) {
        resetConversation()
        _state.update { current ->
            current.copy(
                route = SupportRoute.CONVERSATION,
                conversation = ConversationState(ticketId = ticket.id, category = ticket.category, isLoading = true),
                // Opening it reads it; the reload on the way back confirms that.
                tickets = current.tickets.copy(
                    tickets = current.tickets.tickets.map { if (it.id == ticket.id) it.copy(isSeen = true) else it },
                ),
            )
        }
        loadConversation(ticket.id)
    }

    /** A new ticket in [category]: an empty conversation that is created with its first message. */
    fun startTicket(category: String) {
        resetConversation()
        _state.update {
            it.copy(route = SupportRoute.CONVERSATION, isCategorySheetVisible = false, conversation = ConversationState(category = category))
        }
    }

    /** Back: closes the sheet, then leaves the conversation. False when the screen itself should close. */
    fun back(): Boolean {
        val current = _state.value
        return when {
            current.isCategorySheetVisible -> {
                hideCategories()
                true
            }
            current.route == SupportRoute.CONVERSATION -> {
                leaveConversation()
                true
            }
            else -> false
        }
    }

    private fun leaveConversation() {
        resetConversation()
        _state.update { it.copy(route = SupportRoute.TICKETS) }
        // iOS reloads the list whenever it reappears, so a ticket opened or answered meanwhile shows up.
        loadTickets(pullToRefresh = false)
    }

    /**
     * Reloads the open conversation without a spinner, so an answer shows up while the user waits
     * on the screen. Skipped while a load or a send is under way.
     */
    fun reloadConversation() {
        val current = _state.value
        val ticketId = current.conversation.ticketId ?: return
        if (current.route != SupportRoute.CONVERSATION || current.conversation.isSending || conversationJob?.isActive == true) return
        loadConversation(ticketId)
    }

    /**
     * Sends what is in [messageField]: as the first message of a new ticket, or into the open one.
     * On failure the text goes back into the field (iOS loses a new ticket's text).
     */
    fun send() {
        val current = _state.value
        val conversation = current.conversation
        if (current.route != SupportRoute.CONVERSATION || conversation.isSending) return
        val text = messageField.text.toString().trim()
        if (text.isEmpty()) return
        if (!service.isConnected) {
            service.showOfflineBanner()
            return
        }
        val generation = conversationGeneration
        val ticketId = conversation.ticketId
        messageField.clearText()
        _state.update { it.copy(conversation = it.conversation.copy(isSending = true, isLoading = ticketId == null)) }
        viewModelScope.launch {
            if (ticketId == null) {
                val created = attempt { service.createTicket(conversation.category.orEmpty(), text) }
                if (generation != conversationGeneration) return@launch
                created
                    .onSuccess { id ->
                        _state.update { it.copy(conversation = it.conversation.copy(ticketId = id, isSending = false, isLoading = true)) }
                        loadConversation(id)
                    }
                    .onFailure { sendFailed(text, it) }
            } else {
                val sent = attempt { service.send(ticketId, text) }
                if (generation != conversationGeneration) return@launch
                sent
                    .onSuccess { message ->
                        pendingSent += message
                        _state.update {
                            it.copy(conversation = it.conversation.copy(isSending = false, messages = mergeConversation(it.conversation.messages, listOf(message))))
                        }
                    }
                    .onFailure { sendFailed(text, it) }
            }
        }
    }

    private fun sendFailed(text: String, error: Throwable) {
        if (messageField.text.isEmpty()) messageField.setTextAndPlaceCursorAtEnd(text)
        _state.update { it.copy(conversation = it.conversation.copy(isSending = false, isLoading = false)) }
        if (error is VxHubException.NetworkUnavailable) service.showOfflineBanner()
    }

    private fun loadConversation(ticketId: String) {
        val generation = conversationGeneration
        conversationJob?.cancel()
        conversationJob = viewModelScope.launch {
            val fetched = attempt { service.conversation(ticketId) }.getOrNull()
            if (generation != conversationGeneration) return@launch
            if (fetched != null) pendingSent.removeAll { sent -> fetched.any { it.id == sent.id } }
            val pending = pendingSent.toList()
            _state.update {
                it.copy(
                    conversation = it.conversation.copy(
                        messages = fetched?.let { messages -> mergeConversation(messages, pending) } ?: it.conversation.messages,
                        isLoading = false,
                    ),
                )
            }
        }
    }

    private fun resetConversation() {
        conversationGeneration++
        conversationJob?.cancel()
        pendingSent.clear()
        messageField.clearText()
    }
}

/** Runs [block], turning any failure except cancellation into a failed [Result]. */
private inline fun <T> attempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
