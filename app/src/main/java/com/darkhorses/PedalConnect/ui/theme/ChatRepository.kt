package com.darkhorses.PedalConnect.ui.theme

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ChatRepository {
    private val db = FirebaseFirestore.getInstance()

    fun getConversationsForUser(userId: String): Flow<List<Conversation>> = callbackFlow {
        val activeMap = mutableMapOf<String, Conversation>()
        val historyMap = mutableMapOf<String, Conversation>()

        fun emitMerged() {
            // Use a safe sort that handles null timestamps
            val all = (activeMap.values + historyMap.values).distinctBy { it.id }
                .sortedWith(compareByDescending<Conversation> { it.lastMessageTimestamp?.toDate()?.time ?: 0L }
                    .thenByDescending { it.id })
            trySend(all)
        }

        // Listen for chats where user is CURRENTLY active
        val reg1 = db.collection("conversations")
            .whereArrayContains("participantIds", userId)
            .addSnapshotListener { snap, error ->
                if (error != null) return@addSnapshotListener
                activeMap.clear()
                snap?.documents?.forEach { doc ->
                    doc.toObject(Conversation::class.java)?.copy(id = doc.id)?.let { activeMap[it.id] = it }
                }
                emitMerged()
            }

        // Listen for chats where user was HISTORICALLY active (left or kicked)
        val reg2 = db.collection("conversations")
            .whereArrayContains("historicalParticipantIds", userId)
            .addSnapshotListener { snap, error ->
                if (error != null) return@addSnapshotListener
                historyMap.clear()
                snap?.documents?.forEach { doc ->
                    doc.toObject(Conversation::class.java)?.copy(id = doc.id)?.let { historyMap[it.id] = it }
                }
                emitMerged()
            }

        awaitClose { reg1.remove(); reg2.remove() }
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
    /** Returns true if a new request was created, false if one was already pending
     *  (or fromId == toId) — callers should surface this instead of assuming success. */
    suspend fun sendFriendRequest(fromId: String, fromName: String, toId: String, toName: String): Boolean {
        if (fromId == toId) return false  // never allow self-friending

        val existing = db.collection("friend_requests")
            .whereEqualTo("fromId", fromId)
            .whereEqualTo("toId", toId)
            .whereEqualTo("status", "pending")
            .get().await()
        if (!existing.isEmpty) return false

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
        return true
    }

    suspend fun respondToFriendRequest(requestId: String, accept: Boolean, currentUserId: String, currentUserName: String) {
        val requestRef = db.collection("friend_requests").document(requestId)
        val snapshot = requestRef.get().await()
        if (!snapshot.exists()) return

        val fromId = snapshot.getString("fromId") ?: ""
        if (fromId == currentUserId) return  // safety guard, should never happen

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

    /** Removes the friendship both ways. Silent — no notification, matching how
     *  unfriending works on most platforms (as opposed to accept/decline). */
    suspend fun unfriendUser(currentUserId: String, otherUserId: String) {
        db.runBatch { batch ->
            batch.update(
                db.collection("users").document(currentUserId),
                "friends",
                com.google.firebase.firestore.FieldValue.arrayRemove(otherUserId)
            )
            batch.update(
                db.collection("users").document(otherUserId),
                "friends",
                com.google.firebase.firestore.FieldValue.arrayRemove(currentUserId)
            )
        }.await()
    }

    /** Live stream of the current user's friends as full User objects (name, photo, online),
     *  for rendering the Friends drawer. Chunks to 30 since whereIn caps there. */
    fun getMyFriends(userId: String): Flow<List<User>> = callbackFlow {
        var friendsListener: com.google.firebase.firestore.ListenerRegistration? = null
        val userRegistration = db.collection("users").document(userId)
            .addSnapshotListener { userDoc, _ ->
                val friendIds = (userDoc?.get("friends") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                friendsListener?.remove()
                if (friendIds.isEmpty()) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                friendsListener = db.collection("users")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), friendIds.take(30))
                    .addSnapshotListener { snap, _ ->
                        val users = snap?.documents?.mapNotNull {
                            it.toObject(User::class.java)?.copy(uid = it.id)
                        } ?: emptyList()
                        trySend(users)
                    }
            }
        awaitClose { userRegistration.remove(); friendsListener?.remove() }
    }

    // ── Notifications ───────────────────────────────────────────────────────

    /** Live stream of the current user's own friends array. */
    fun getMyFriendIds(userId: String): Flow<List<String>> = callbackFlow {
        val registration = db.collection("users").document(userId)
            .addSnapshotListener { doc, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val friends = (doc?.get("friends") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                trySend(friends)
            }
        awaitClose { registration.remove() }
    }

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

    // ── Conversations ────────────────────────────────────────────────────────

    /** Returns the id of an existing 1:1 conversation between these two users, or null. */
    suspend fun findExistingConversation(userId: String, otherUserId: String): String? {
        val snapshot = db.collection("conversations")
            .whereArrayContains("participantIds", userId)
            .get()
            .await()
        return snapshot.documents.firstOrNull { doc ->
            val ids = doc.get("participantIds") as? List<*>
            val isGroup = doc.getBoolean("isGroup") ?: false
            val groupName = doc.getString("groupName")
            // A 1:1 conversation must have exactly 2 participants, not be a group, and have no group name.
            ids != null && ids.size == 2 && ids.contains(otherUserId) && !isGroup && groupName == null
        }?.id
    }

    /** Creates a new empty conversation between two users and returns its id. */
    suspend fun createConversation(
        userId: String, userName: String,
        otherUserId: String, otherUserName: String
    ): String {
        val participantIds = listOf(userId, otherUserId)
        val conversation = hashMapOf(
            "participantIds" to participantIds,
            "historicalParticipantIds" to participantIds,
            "participantNames" to mapOf(userId to userName, otherUserId to otherUserName),
            "lastMessage" to "",
            "lastMessageSenderId" to "",
            "lastMessageTimestamp" to Timestamp.now(),
            "unreadCounts" to mapOf(userId to 0, otherUserId to 0),
            "isGroup" to false
        )
        val docRef = db.collection("conversations").add(conversation).await()
        return docRef.id
    }

    /** Creates a new group conversation and returns its id. */
    suspend fun createGroupConversation(
        creatorId: String,
        groupName: String,
        participants: List<User>
    ): String {
        val participantIds = participants.map { it.uid } + creatorId
        val participantNames = participants.associate { it.uid to it.username }.toMutableMap()

        // We need the creator's username too. We'll fetch it if not provided or assume it's handled in ViewModel
        // But for simplicity, let's assume the caller provides names or we fetch them.
        // Actually, let's pass a map of names.

        val conversation = hashMapOf(
            "participantIds" to participantIds,
            "historicalParticipantIds" to participantIds,
            "participantNames" to participantNames, // This needs to include creator, will fix in ViewModel
            "lastMessage" to "Group created",
            "lastMessageSenderId" to creatorId,
            "lastMessageTimestamp" to Timestamp.now(),
            "unreadCounts" to participantIds.associateWith { 0 },
            "isGroup" to true,
            "groupName" to groupName
        )
        val docRef = db.collection("conversations").add(conversation).await()
        return docRef.id
    }

    /** Real-time stream of messages within a single conversation, oldest first. */
    fun getMessagesForConversation(conversationId: String): Flow<List<Message>> = callbackFlow {
        val registration = db.collection("conversations").document(conversationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Message::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(messages)
            }
        awaitClose { registration.remove() }
    }

    // ── Messaging ────────────────────────────────────────────────────────────
    suspend fun setTypingStatus(userId: String, conversationId: String?) {
        db.collection("users").document(userId)
            .update("typingIn", conversationId)
            .await()
    }

    /** Returns true once the write is confirmed. Note: with offline persistence enabled,
     *  a failed/timed-out result doesn't mean the message is lost — Firestore keeps it
     *  queued locally and will retry once connectivity returns — but callers shouldn't
     *  block on that indefinitely, so surface it as "not sent yet". */
    suspend fun sendMessage(
        conversationId: String,
        senderId: String,
        text: String,
        participantIds: List<String>,
        imageUrl: String? = null,
        replyToId: String? = null,
        replyToText: String? = null,
        replyToSenderName: String? = null,
        isSystemMessage: Boolean = false
    ): Boolean {
        val timestamp = Timestamp.now()
        val message = hashMapOf(
            "conversationId" to conversationId,
            "senderId" to senderId,
            "text" to text,
            "timestamp" to timestamp,
            "isRead" to false,
            "imageUrl" to imageUrl,
            "replyToId" to replyToId,
            "replyToText" to replyToText,
            "replyToSenderName" to replyToSenderName,
            "isSystemMessage" to isSystemMessage,
            "isUnsent" to false,
            "seenBy" to emptyList<String>()
        )

        val convRef = db.collection("conversations").document(conversationId)
        val msgRef = convRef.collection("messages").document()

        return try {
            msgRef.set(message).await()

            // Fetch conversation to check for group info
            val convSnapshot = convRef.get().await()
            val isGroup = convSnapshot.getBoolean("isGroup") ?: false
            val groupName = convSnapshot.getString("groupName")

            val updates = mutableMapOf<String, Any>(
                "lastMessage" to if (isSystemMessage) text else if (imageUrl != null) "📷 Photo" else text,
                "lastMessageSenderId" to senderId,
                "lastMessageTimestamp" to timestamp
            )
            participantIds.forEach { id ->
                if (id != senderId) {
                    updates["unreadCounts.$id"] = com.google.firebase.firestore.FieldValue.increment(1)
                    
                    // Also send a notification to the receiver for background alerts
                    // We'll fetch the sender's name for a better notification message
                    db.collection("users").document(senderId).get().addOnSuccessListener { senderDoc ->
                        val senderName = senderDoc.getString("displayName")
                            ?.takeIf { it.isNotBlank() } ?: senderDoc.getString("username") ?: "Someone"
                        
                        val notificationMessage = if (isGroup && !groupName.isNullOrBlank()) {
                            if (isSystemMessage) text else "$senderName: ${if (imageUrl != null) "sent a photo" else text}"
                        } else {
                            if (imageUrl != null) "sent you a photo" else text
                        }

                        val notifData = hashMapOf<String, Any>(
                            "toId" to id,
                            "userName" to (if (isGroup && !groupName.isNullOrBlank()) groupName else senderName),
                            "message" to notificationMessage,
                            "type" to "message",
                            "timestamp" to System.currentTimeMillis(),
                            "read" to false,
                            "conversationId" to conversationId
                        )
                        db.collection("notifications").add(notifData)
                    }
                }
            }
            convRef.update(updates).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun unsendMessage(conversationId: String, messageId: String) {
        val msgRef = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)

        val snapshot = msgRef.get().await()
        val timestamp = snapshot.getTimestamp("timestamp")

        msgRef.update("isUnsent", true).await()

        // Also update conversation preview if this was the last message
        val convRef = db.collection("conversations").document(conversationId)
        val convSnap = convRef.get().await()
        val lastMsgTime = convSnap.getTimestamp("lastMessageTimestamp")

        if (timestamp != null && lastMsgTime != null && timestamp == lastMsgTime) {
            convRef.update("lastMessage", "Message was unsent").await()
        }
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

    /** Marks all unread messages from the OTHER participant as read.
     *  Call when the current user opens/views the conversation. */
    suspend fun markMessagesAsRead(conversationId: String, currentUserId: String) {
        val messages = db.collection("conversations").document(conversationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(30)
            .get()
            .await()

        val toMark = messages.documents.filter { doc ->
            val senderId = doc.getString("senderId")
            val seenBy = (doc.get("seenBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            senderId != currentUserId && !seenBy.contains(currentUserId)
        }

        if (toMark.isEmpty()) return
        
        db.runBatch { batch ->
            toMark.forEach { doc -> 
                batch.update(doc.reference, mapOf(
                    "isRead" to true,
                    "seenBy" to com.google.firebase.firestore.FieldValue.arrayUnion(currentUserId)
                ))
            }
        }.await()
    }

    /** Sets or clears the current user's reaction on a message. Pass emoji = null to remove.
     *  Plain update() on a single field — no transaction, so it gets Firestore's local-cache
     *  latency compensation and reflects instantly via the messages snapshot listener. */
    suspend fun setReaction(conversationId: String, messageId: String, userId: String, emoji: String?) {
        val msgRef = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
        if (emoji == null) {
            msgRef.update("reactions.$userId", com.google.firebase.firestore.FieldValue.delete()).await()
        } else {
            msgRef.update("reactions.$userId", emoji).await()
        }
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

    suspend fun removeParticipant(conversationId: String, userId: String) {
        val convRef = db.collection("conversations").document(conversationId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(convRef)
            val participantIds = (snapshot.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            
            // Ensure we have historical tracking, even for old groups
            val historicalParticipantIds = (snapshot.get("historicalParticipantIds") as? List<*>)?.filterIsInstance<String>()?.toMutableList() 
                ?: participantIds.toMutableList()
            
            if (!historicalParticipantIds.contains(userId)) {
                historicalParticipantIds.add(userId)
            }

            val participantNames = (snapshot.get("participantNames") as? Map<*, *>)?.toMutableMap() ?: mutableMapOf()
            val adminIds = (snapshot.get("adminIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val unreadCounts = (snapshot.get("unreadCounts") as? Map<*, *>)?.toMutableMap() ?: mutableMapOf()

            val newParticipantIds = participantIds.filter { it != userId }
            participantNames.remove(userId)
            val newAdminIds = adminIds.filter { it != userId }
            unreadCounts.remove(userId)

            // Never delete the conversation, so users can view history
            transaction.update(convRef, mapOf(
                "participantIds" to newParticipantIds,
                "historicalParticipantIds" to historicalParticipantIds,
                "participantNames" to participantNames,
                "adminIds" to newAdminIds,
                "unreadCounts" to unreadCounts
            ))
        }.await()
    }

    suspend fun leaveGroup(conversationId: String, userId: String, userName: String) {
        removeParticipant(conversationId, userId)

        // If there are still participants, send a system message
        val convSnap = db.collection("conversations").document(conversationId).get().await()
        if (convSnap.exists()) {
            val participantIds = (convSnap.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            if (participantIds.isNotEmpty()) {
                sendMessage(
                    conversationId = conversationId,
                    senderId = userId,
                    text = "$userName left the group",
                    participantIds = participantIds,
                    isSystemMessage = true
                )
            }
        }
    }

    suspend fun addParticipants(conversationId: String, newParticipants: List<User>, currentUserName: String, currentUserId: String) {
        val convRef = db.collection("conversations").document(conversationId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(convRef)
            val participantIds = (snapshot.get("participantIds") as? List<*>)?.filterIsInstance<String>()?.toMutableList() ?: mutableListOf()
            val historicalParticipantIds = (snapshot.get("historicalParticipantIds") as? List<*>)?.filterIsInstance<String>()?.toMutableList() ?: mutableListOf()
            val participantNames = (snapshot.get("participantNames") as? Map<*, *>)?.toMutableMap() ?: mutableMapOf()
            val unreadCounts = (snapshot.get("unreadCounts") as? Map<*, *>)?.toMutableMap() ?: mutableMapOf()

            newParticipants.forEach { user ->
                if (!participantIds.contains(user.uid)) {
                    participantIds.add(user.uid)
                    participantNames[user.uid] = user.username
                    unreadCounts[user.uid] = 0L
                }
                if (!historicalParticipantIds.contains(user.uid)) {
                    historicalParticipantIds.add(user.uid)
                }
            }

            transaction.update(convRef, mapOf(
                "participantIds" to participantIds,
                "historicalParticipantIds" to historicalParticipantIds,
                "participantNames" to participantNames,
                "unreadCounts" to unreadCounts
            ))
        }.await()

        val addedNames = newParticipants.joinToString(", ") { it.username }
        sendMessage(
            conversationId = conversationId,
            senderId = currentUserId,
            text = "$currentUserName added $addedNames to the group",
            participantIds = (db.collection("conversations").document(conversationId).get().await().get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            isSystemMessage = true
        )
    }

    suspend fun promoteToAdmin(conversationId: String, userId: String) {
        db.collection("conversations").document(conversationId)
            .update("adminIds", com.google.firebase.firestore.FieldValue.arrayUnion(userId))
            .await()
    }

    suspend fun updateGroupName(conversationId: String, newName: String) {
        db.collection("conversations").document(conversationId)
            .update("groupName", newName)
            .await()
    }

    suspend fun updateGroupPhoto(conversationId: String, photoUrl: String) {
        db.collection("conversations").document(conversationId)
            .update("groupPhotoUrl", photoUrl)
            .await()
    }

    fun getUsers(userIds: List<String>): Flow<List<User>> = callbackFlow {
        if (userIds.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = db.collection("users")
            .whereIn(com.google.firebase.firestore.FieldPath.documentId(), userIds.take(30))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val users = snapshot?.documents?.mapNotNull {
                    it.toObject(User::class.java)?.copy(uid = it.id)
                } ?: emptyList()
                trySend(users)
            }
        awaitClose { registration.remove() }
    }
}
