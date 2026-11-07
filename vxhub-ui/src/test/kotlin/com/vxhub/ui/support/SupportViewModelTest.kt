package com.vxhub.ui.support

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.google.common.truth.Truth.assertThat
import com.vxhub.VxHubException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SupportViewModelTest {

    private class FakeSupport : SupportService {
        var tickets: List<SupportTicket> = emptyList()
        val conversations = mutableMapOf<String, MutableList<SupportMessage>>()
        val created = mutableListOf<Pair<String, String>>()
        var sendError: Exception? = null
        var createError: Exception? = null
        var connected = true
        var offlineBanners = 0
        private var nextId = 0

        override suspend fun tickets(): List<SupportTicket> = tickets

        override suspend fun createTicket(category: String, message: String): String {
            createError?.let { throw it }
            created += category to message
            conversations["new"] = mutableListOf(SupportMessage("first", message, true, "2025-02-05T10:00:00.000Z"))
            return "new"
        }

        override suspend fun conversation(ticketId: String): List<SupportMessage> = conversations[ticketId].orEmpty().toList()

        override suspend fun send(ticketId: String, message: String): SupportMessage {
            sendError?.let { throw it }
            val sent = SupportMessage("sent-${nextId++}", message, true, "2025-02-05T11:00:00.000Z")
            conversations.getOrPut(ticketId) { mutableListOf() } += sent
            return sent
        }

        override val isConnected: Boolean get() = connected

        override fun showOfflineBanner() {
            offlineBanners++
        }
    }

    private val service = FakeSupport()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val unread = SupportTicket("t1", "billing", "Any news?", "2025-02-05T09:00:00.000Z", isSeen = false)

    @Test
    fun `loads the tickets when it starts`() {
        service.tickets = listOf(unread)
        val viewModel = SupportViewModel(service)
        assertThat(viewModel.state.value.tickets.tickets).containsExactly(unread)
        assertThat(viewModel.state.value.tickets.isLoading).isFalse()
    }

    @Test
    fun `opening a ticket reads it and loads its conversation`() {
        service.tickets = listOf(unread)
        service.conversations["t1"] = mutableListOf(SupportMessage("a", "Hello", false, "2025-02-05T09:00:00.000Z"))
        val viewModel = SupportViewModel(service)

        viewModel.openTicket(unread)

        val state = viewModel.state.value
        assertThat(state.route).isEqualTo(SupportRoute.CONVERSATION)
        assertThat(state.tickets.tickets.single().isSeen).isTrue()
        assertThat(state.conversation.messages.map { it.text }).containsExactly("Hello")
        assertThat(state.conversation.isLoading).isFalse()
    }

    @Test
    fun `a new ticket opens with its first message`() {
        val viewModel = SupportViewModel(service)
        viewModel.showCategories()
        viewModel.startTicket("billing")
        assertThat(viewModel.state.value.isCategorySheetVisible).isFalse()
        assertThat(viewModel.state.value.conversation.isNewTicket).isTrue()

        viewModel.messageField.setTextAndPlaceCursorAtEnd("  Where is my receipt?  ")
        viewModel.send()

        assertThat(service.created).containsExactly("billing" to "Where is my receipt?")
        val conversation = viewModel.state.value.conversation
        assertThat(conversation.ticketId).isEqualTo("new")
        assertThat(conversation.isNewTicket).isFalse()
        assertThat(conversation.messages.map { it.text }).containsExactly("Where is my receipt?")
        assertThat(viewModel.messageField.text.toString()).isEmpty()
    }

    @Test
    fun `a ticket that fails to open gives the text back and the prompt returns`() {
        service.createError = VxHubException.RequestFailed(500)
        val viewModel = SupportViewModel(service)
        viewModel.startTicket("billing")
        viewModel.messageField.setTextAndPlaceCursorAtEnd("Help")

        viewModel.send()

        assertThat(viewModel.messageField.text.toString()).isEqualTo("Help")
        assertThat(viewModel.state.value.conversation.isNewTicket).isTrue()
        assertThat(service.offlineBanners).isEqualTo(0)
    }

    @Test
    fun `a sent message shows at once, and a failed one goes back into the field`() {
        service.conversations["t1"] = mutableListOf()
        val viewModel = SupportViewModel(service)
        viewModel.openTicket(unread)

        viewModel.messageField.setTextAndPlaceCursorAtEnd("Thanks")
        viewModel.send()
        assertThat(viewModel.state.value.conversation.messages.map { it.text }).containsExactly("Thanks")

        service.sendError = VxHubException.NetworkUnavailable()
        viewModel.messageField.setTextAndPlaceCursorAtEnd("Still there?")
        viewModel.send()
        assertThat(viewModel.messageField.text.toString()).isEqualTo("Still there?")
        assertThat(viewModel.state.value.conversation.isSending).isFalse()
        assertThat(service.offlineBanners).isEqualTo(1)
    }

    @Test
    fun `offline, nothing is sent and the banner says why`() {
        service.connected = false
        val viewModel = SupportViewModel(service)
        viewModel.startTicket("billing")
        viewModel.messageField.setTextAndPlaceCursorAtEnd("Hello")

        viewModel.send()

        assertThat(service.created).isEmpty()
        assertThat(service.offlineBanners).isEqualTo(1)
        assertThat(viewModel.messageField.text.toString()).isEqualTo("Hello")
    }

    @Test
    fun `back closes the sheet, then the conversation, then hands over`() {
        val viewModel = SupportViewModel(service)
        viewModel.showCategories()
        assertThat(viewModel.back()).isTrue()
        assertThat(viewModel.state.value.isCategorySheetVisible).isFalse()

        viewModel.startTicket("billing")
        assertThat(viewModel.back()).isTrue()
        assertThat(viewModel.state.value.route).isEqualTo(SupportRoute.TICKETS)

        assertThat(viewModel.back()).isFalse()
    }

    @Test
    fun `a quiet reload brings in the agent's answer`() {
        service.conversations["t1"] = mutableListOf(SupportMessage("a", "Hello", true, "2025-02-05T09:00:00.000Z"))
        val viewModel = SupportViewModel(service)
        viewModel.openTicket(unread)

        service.conversations.getValue("t1") += SupportMessage("b", "Hi, how can we help?", false, "2025-02-05T09:05:00.000Z")
        viewModel.reloadConversation()

        assertThat(viewModel.state.value.conversation.messages.map { it.id }).containsExactly("a", "b").inOrder()
    }
}
