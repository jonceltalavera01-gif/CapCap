package com.darkhorses.PedalConnect.ui.theme

import com.google.firebase.Timestamp

data class Conversation(
    val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageTimestamp: Timestamp? = null,
    val unreadCounts: Map<String, Int> = emptyMap(),
    val isGroup: Boolean = false
)

data class Message(
    val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Timestamp? = null,
    val isRead: Boolean = false
)

data class UserPresence(
    val userId: String = "",
    val userName: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Timestamp? = null,
    val typingIn: String? = null
)

data class FriendRequest(
    val id: String = "",
    val fromId: String = "",
    val fromName: String = "",
    val toId: String = "",
    val timestamp: Timestamp? = null,
    val status: String = "pending"
)

data class User(
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val isOnline: Boolean = false,
    val friends: List<String> = emptyList()
)

// ── Notifications (friend requests, accepted requests, etc.) ──────────────────
data class AppNotification(
    val id: String = "",
    val toId: String = "",
    val userName: String = "",
    val message: String = "",
    val type: String = "",
    val timestamp: Long = 0L,
    val read: Boolean = false,
    val requestId: String = ""
)