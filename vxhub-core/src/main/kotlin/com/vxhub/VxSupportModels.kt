package com.vxhub

/** A support conversation, as listed by [VxHub.supportTickets]. Dates are the backend's ISO-8601 text. */
public class VxTicket internal constructor(
    public val id: String,
    public val category: String,
    public val status: String?,
    public val state: String?,
    public val lastMessage: String?,
    public val lastMessageAt: String?,
    public val createdAt: String?,
    /** The device has seen the latest answer. */
    public val isSeen: Boolean,
)

/** A support conversation with its messages, oldest first. */
public class VxTicketDetail internal constructor(
    public val id: String,
    public val category: String,
    public val status: String?,
    public val state: String?,
    public val createdAt: String?,
    public val messages: List<VxTicketMessage>,
)

public class VxTicketMessage internal constructor(
    public val id: String,
    public val text: String,
    /** Written by the user (true) or by a support agent (false). */
    public val isFromDevice: Boolean,
    public val createdAt: String?,
)
