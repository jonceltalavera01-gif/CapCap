package com.darkhorses.PedalConnect.ui.theme

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ChatRepository {
    private val db = FirebaseFirestore.getInstance()

    fun getConversationsForUser(userId: String): Flow<List<Conversation>> = callbackFlow {
        // Query without orderBy first to avoid crash if index is missing.
        // We will sort locally in the collector if needed.
        val registration = db.collection("conversations")
            .whereArrayContains("participantIds", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Log error but don't crash
                    error.printStackTrace()
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val conversations = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Conversation::class.java)?.copy(id = doc.id)
                }?.sortedByDescending { it.lastMessageTimestamp } ?: emptyList()
                trySend(conversations)
            }
        awaitClose { registration.remove() }
    }

    fun getOnlineUsers(): Flow<List<UserPresence>> = callbackFlow {
        val registration = db.collection("users")
            .whereEqualTo("isOnline", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val onlineUsers = snapshot?.documents?.mapNotNull { doc ->
                    UserPresence(
                        userId = doc.id,
                        userName = doc.getString("username") ?: "User",
                        isOnline = true,
                        typingIn = doc.getString("typingIn")
                    )
                } ?: emptyList()
                trySend(onlineUsers)
            }
        awaitClose { registration.remove() }
    }

    fun getOnlineFriends(userId: String): Flow<List<UserPresence>> = callbackFlow {
        val registration = db.collection("users").document(userId)
            .addSnapshotListener { userDoc, _ ->
                val friends = (userDoc?.get("friends") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                db.collection("users")
                    .whereEqualTo("isOnline", true)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) return@addSnapshotListener
                        val onlineFriends = snapshot?.documents?.mapNotNull { doc ->
                            if (friends.contains(doc.id)) {
                                UserPresence(
                                    userId = doc.id,
                                    userName = doc.getString("username") ?: "User",
                                    isOnline = true,
                                    typingIn = doc.getString("typingIn")
                                )
                            } else null
                        } ?: emptyList()
                        trySend(onlineFriends)
                    }
            }
        awaitClose { registration.remove() }
    }

    suspend fun searchUsers(query: String): List<User> {
        if (query.isBlank()) return emptyList()
        val snapshot = db.collection("users")
            .whereGreaterThanOrEqualTo("username", query)
            .whereLessThanOrEqualTo("username", query + "\uf8ff")
            .limit(10)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toObject(User::class.java)?.copy(uid = it.id) }
    }

    // ── Friend requests ─────────────────────────────────────────────────────
    suspend fun sendFriendRequest(fromId: String, fromName: String, toId: String, toName: String) {
        val existing = db.collection("friend_requests")
            .whereEqualTo("fromId", fromId)
            .whereEqualTo("toId", toId)
            .whereEqualTo("status", "pending")
            .get().await()
        if (!existing.isEmpty) return

        val request = hashMapOf(
            "fromId" to fromId,
            "fromName" to fromName,
            "toId" to toId,
            "timestamp" to Timestamp.now(),
            "status" to "pending"
        )
        val docRef = db.collection("friend_requests").add(request).await()

        // Notification goes to the RECEIVER (toId), showing the SENDER's name
        db.collection("notifications").add(hashMapOf(
            "toId" to toId,
            "userName" to fromName,
            "message" to "$fromName sent you a friend request.",
            "type" to "friend_request",
            "timestamp" to System.currentTimeMillis(),
            "read" to false,
            "requestId" to docRef.id
        )).await()
    }

    suspend fun respondToFriendRequest(requestId: String, accept: Boolean, currentUserId: String, currentUserName: String) {
        val requestRef = db.collection("friend_requests").document(requestId)
        val snapshot = requestRef.get().await()
        if (!snapshot.exists()) return

        val fromId = snapshot.getString("fromId") ?: ""

        if (accept) {
            db.runBatch { batch ->
                batch.update(requestRef, "status", "accepted")
                batch.update(db.collection("users").document(fromId), "friends", com.google.firebase.firestore.FieldValue.arrayUnion(currentUserId))
                batch.update(db.collection("users").document(currentUserId), "friends", com.google.firebase.firestore.FieldValue.arrayUnion(fromId))

                // Notify the original sender that their request was accepted
                batch.set(db.collection("notifications").document(), hashMapOf(
                    "toId" to fromId,
                    "userName" to currentUserName,
                    "message" to "$currentUserName accepted your friend request.",
                    "type" to "accepted",
                    "timestamp" to System.currentTimeMillis(),
                    "read" to false
                ))
            }.await()
        } else {
            requestRef.update("status", "rejected").await()
        }
    }

    // ── Notifications ───────────────────────────────────────────────────────
    fun getNotificationsForUser(userId: String): Flow<List<AppNotification>> = callbackFlow {
        val registration = db.collection("notifications")
            .whereEqualTo("toId", userId)
            .whereEqualTo("read", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val notifs = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(AppNotification::class.java)?.copy(id = doc.id)
                }?.sortedByDescending { it.timestamp } ?: emptyList()
                trySend(notifs)
            }
        awaitClose { registration.remove() }
    }

    suspend fun markNotificationRead(notificationId: String) {
        db.collection("notifications").document(notificationId)
            .update("read", true)
            .await()
    }

    // ── Messaging ────────────────────────────────────────────────────────────
    suspend fun setTypingStatus(userId: String, conversationId: String?) {
        db.collection("users").document(userId)
            .update("typingIn", conversationId)
            .await()
    }

    suspend fun sendMessage(conversationId: String, senderId: String, text: String, participantIds: List<String>) {
        val timestamp = Timestamp.now()
        val message = hashMapOf(
            "conversationId" to conversationId,
            "senderId" to senderId,
            "text" to text,
            "timestamp" to timestamp,
            "isRead" to false
        )

        db.runTransaction { transaction ->
            val convRef = db.collection("conversations").document(conversationId)
            val snapshot = transaction.get(convRef)

            // Add message
            val msgRef = db.collection("conversations").document(conversationId).collection("messages").document()
            transaction.set(msgRef, message)

            // Update conversation
            val currentUnread = snapshot.get("unreadCounts") as? Map<String, Long> ?: emptyMap()
            val newUnread = currentUnread.toMutableMap()
            participantIds.forEach { id ->
                if (id != senderId) {
                    newUnread[id] = (newUnread[id] ?: 0L) + 1L
                }
            }

            transaction.update(convRef, mapOf(
                "lastMessage" to text,
                "lastMessageSenderId" to senderId,
                "lastMessageTimestamp" to timestamp,
                "unreadCounts" to newUnread
            ))
        }.await()
    }

    suspend fun markConversationAsRead(conversationId: String, userId: String) {
        val convRef = db.collection("conversations").document(conversationId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(convRef)
            val unreadCounts = snapshot.get("unreadCounts") as? Map<String, Long> ?: emptyMap()
            val newUnread = unreadCounts.toMutableMap()
            newUnread[userId] = 0L
            transaction.update(convRef, "unreadCounts", newUnread)
        }.await()
    }

    suspend fun setUserOnlineStatus(userId: String, isOnline: Boolean) {
        if (userId.isEmpty()) return
        try {
            db.collection("users").document(userId)
                .set(mapOf(
                    "isOnline" to isOnline,
                    "lastSeen" to Timestamp.now()
                ), com.google.firebase.firestore.SetOptions.merge())
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}