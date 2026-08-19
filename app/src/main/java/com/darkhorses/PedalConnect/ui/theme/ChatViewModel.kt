package com.darkhorses.PedalConnect.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChatViewModel : ViewModel() {
    private val repository = ChatRepository()
    private val auth = FirebaseAuth.getInstance()
    private val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""
    var currentUserName = ""
    var currentUserPhotoUrl: String? = null

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _onlineUsers = MutableStateFlow<List<UserPresence>>(emptyList())
    val onlineUsers: StateFlow<List<UserPresence>> = _onlineUsers.asStateFlow()

    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults: StateFlow<List<User>> = _searchResults.asStateFlow()

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private val _myFriendIds = MutableStateFlow<List<String>>(emptyList())
    val myFriendIds: StateFlow<List<String>> = _myFriendIds.asStateFlow()

    private val _isFriendsLoaded = MutableStateFlow(false)

    val activeConversations = combine(_conversations, _myFriendIds, _isFriendsLoaded) { convs, friends, loaded ->
        convs.filter { conv ->
            val isInChat = conv.participantIds.contains(currentUserId)
            if (!isInChat) return@filter false
            
            val isGroupChat = conv.isGroup || conv.participantIds.size > 2
            if (isGroupChat) {
                true
            } else {
                val otherId = conv.participantIds.find { it != currentUserId }
                !loaded || otherId == null || friends.contains(otherId)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val oldConversations = combine(_conversations, _myFriendIds, _isFriendsLoaded) { convs, friends, loaded ->
        if (!loaded) return@combine emptyList<Conversation>()
        convs.filter { conv ->
            val isInChat = conv.participantIds.contains(currentUserId)
            val isGroupChat = conv.isGroup || conv.participantIds.size > 2 || conv.groupName != null
            
            if (isGroupChat) {
                !isInChat
            } else {
                val otherId = conv.participantIds.find { it != currentUserId }
                !isInChat || (otherId != null && !friends.contains(otherId))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _myFriends = MutableStateFlow<List<User>>(emptyList())
    val myFriends: StateFlow<List<User>> = _myFriends.asStateFlow()

    // ── Active chat thread ───────────────────────────────────────────────────
    private val _activeConversation = MutableStateFlow<Conversation?>(null)
    val activeConversation: StateFlow<Conversation?> = _activeConversation.asStateFlow()

    private val _activeMessages = MutableStateFlow<List<Message>>(emptyList())
    val activeMessages: StateFlow<List<Message>> = _activeMessages.asStateFlow()
    private var activeMessagesJob: Job? = null

    private val _activeParticipants = MutableStateFlow<Map<String, User>>(emptyMap())
    val activeParticipants: StateFlow<Map<String, User>> = _activeParticipants.asStateFlow()
    private var participantsJob: Job? = null

    init {
        if (currentUserId.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    val userDoc = db.collection("users").document(currentUserId).get().await()
                    currentUserName = userDoc.getString("username") ?: ""
                    currentUserPhotoUrl = userDoc.getString("photoUrl")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            viewModelScope.launch {
                try {
                    repository.getConversationsForUser(currentUserId).collect { convs ->
                        _conversations.value = convs
                        // Repair: Ensure historicalParticipantIds is populated for old conversations
                        convs.forEach { conv ->
                            if (conv.historicalParticipantIds.isEmpty() && conv.participantIds.isNotEmpty()) {
                                db.collection("conversations").document(conv.id)
                                    .update("historicalParticipantIds", conv.participantIds)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            viewModelScope.launch {
                try {
                    repository.getOnlineFriends(currentUserId).collect {
                        _onlineUsers.value = it
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            viewModelScope.launch {
                try {
                    repository.getNotificationsForUser(currentUserId).collect {
                        _notifications.value = it
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            viewModelScope.launch {
                try {
                    repository.getMyFriendIds(currentUserId).collect {
                        _myFriendIds.value = it
                        _isFriendsLoaded.value = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            viewModelScope.launch {
                try {
                    repository.getMyFriends(currentUserId).collect {
                        _myFriends.value = it
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /** Removes a friend both ways. Call from a confirmation dialog — this fires immediately. */
    fun unfriend(otherUserId: String) {
        if (currentUserId.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.unfriendUser(currentUserId, otherUserId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun searchUsers(query: String) {
        viewModelScope.launch {
            try {
                _searchResults.value = repository.searchUsers(query)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val _sentRequestIds = MutableStateFlow<Set<String>>(emptySet())
    val sentRequestIds: StateFlow<Set<String>> = _sentRequestIds.asStateFlow()

    /** Sends a friend request. [onResult] gets true if a new request was created,
     *  false if one was already pending or the call failed — use it to show the
     *  right toast instead of assuming success. */
    fun sendFriendRequest(toUser: User, onResult: (Boolean) -> Unit = {}) {
        if (currentUserId.isEmpty()) return
        viewModelScope.launch {
            try {
                var attempts = 0
                while (currentUserName.isEmpty() && attempts < 5) {
                    try {
                        val userDoc = db.collection("users").document(currentUserId).get().await()
                        currentUserName = userDoc.getString("username") ?: ""
                    } catch (readError: Exception) {
                        readError.printStackTrace()
                    }
                    if (currentUserName.isEmpty()) {
                        attempts++
                        kotlinx.coroutines.delay(400)
                    }
                }
                if (currentUserName.isEmpty()) {
                    onResult(false)
                    return@launch
                }
                val created = repository.sendFriendRequest(currentUserId, currentUserName, toUser.uid, toUser.username)
                if (created) {
                    _sentRequestIds.value = _sentRequestIds.value + toUser.uid
                }
                onResult(created)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    fun respondToFriendRequest(requestId: String, accept: Boolean) {
        if (currentUserId.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.respondToFriendRequest(requestId, accept, currentUserId, currentUserName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            try {
                repository.markNotificationRead(notificationId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setOnlineStatus(isOnline: Boolean) {
        if (currentUserId.isNotEmpty()) {
            viewModelScope.launch {
                repository.setUserOnlineStatus(currentUserId, isOnline)
            }
        }
    }

    fun markAsRead(conversationId: String) {
        if (currentUserId.isNotEmpty()) {
            viewModelScope.launch {
                repository.markConversationAsRead(conversationId, currentUserId)
            }
        }
    }

    /** Marks the other participant's messages in this conversation as read (read receipts). */
    fun markMessagesAsRead(conversationId: String) {
        if (currentUserId.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.markMessagesAsRead(conversationId, currentUserId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ── Starting / opening a conversation ────────────────────────────────────

    /**
     * Finds an existing 1:1 conversation with [otherUserId], or creates one if
     * none exists yet. Calls [onResult] with the resulting conversationId.
     */
    fun getOrCreateConversation(otherUserId: String, otherUserName: String, onResult: (String) -> Unit) {
        if (currentUserId.isEmpty()) return
        viewModelScope.launch {
            try {
                val existingId = repository.findExistingConversation(currentUserId, otherUserId)
                if (existingId != null) {
                    onResult(existingId)
                } else {
                    var myName = currentUserName
                    if (myName.isEmpty()) {
                        val userDoc = db.collection("users").document(currentUserId).get().await()
                        myName = userDoc.getString("username") ?: "Rider"
                        currentUserName = myName
                    }
                    val newId = repository.createConversation(
                        currentUserId, myName, otherUserId, otherUserName
                    )
                    onResult(newId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createGroupChat(groupName: String, participants: List<User>, onResult: (String) -> Unit) {
        if (currentUserId.isEmpty()) return
        viewModelScope.launch {
            try {
                var myName = currentUserName
                if (myName.isEmpty()) {
                    val userDoc = db.collection("users").document(currentUserId).get().await()
                    myName = userDoc.getString("username") ?: "Rider"
                    currentUserName = myName
                }

                val participantIds = participants.map { it.uid }.toMutableList()
                participantIds.add(currentUserId)

                val participantNames = participants.associate { it.uid to it.username }.toMutableMap()
                participantNames[currentUserId] = myName

                val conversation = hashMapOf(
                    "participantIds" to participantIds,
                    "historicalParticipantIds" to participantIds,
                    "participantNames" to participantNames,
                    "lastMessage" to "Group created",
                    "lastMessageSenderId" to currentUserId,
                    "lastMessageTimestamp" to com.google.firebase.Timestamp.now(),
                    "unreadCounts" to participantIds.associateWith { 0 },
                    "isGroup" to true,
                    "groupName" to groupName,
                    "adminIds" to listOf(currentUserId)
                )

                val docRef = db.collection("conversations").add(conversation).await()

                // Send initial system message
                repository.sendMessage(
                    conversationId = docRef.id,
                    senderId = currentUserId,
                    text = "$myName created the group \"$groupName\"",
                    participantIds = participantIds,
                    isSystemMessage = true
                )

                onResult(docRef.id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun openConversation(conversationId: String) {
        val cached = _conversations.value.find { it.id == conversationId }
        _activeConversation.value = cached

        if (cached != null) {
            startParticipantsListener(cached.participantIds)
        }

        if (_activeConversation.value == null) {
            // Fallback: fetch from Firestore if not in the cached list
            viewModelScope.launch {
                try {
                    val doc = db.collection("conversations").document(conversationId).get().await()
                    val conv = doc.toObject(Conversation::class.java)?.copy(id = doc.id)
                    _activeConversation.value = conv
                    conv?.let { startParticipantsListener(it.participantIds) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        activeMessagesJob?.cancel()
        activeMessagesJob = viewModelScope.launch {
            try {
                repository.getMessagesForConversation(conversationId).collect {
                    _activeMessages.value = it
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startParticipantsListener(userIds: List<String>) {
        participantsJob?.cancel()
        participantsJob = viewModelScope.launch {
            repository.getUsers(userIds).collect { users ->
                _activeParticipants.value = users.associateBy { it.uid }
            }
        }
    }

    fun closeConversation() {
        activeMessagesJob?.cancel()
        activeMessagesJob = null
        participantsJob?.cancel()
        participantsJob = null
        _activeMessages.value = emptyList()
        _activeConversation.value = null
        _activeParticipants.value = emptyMap()
    }

    fun sendMessage(
        conversationId: String,
        otherUserId: String,
        text: String,
        imageUrl: String? = null,
        replyToId: String? = null,
        replyToText: String? = null,
        replyToSenderName: String? = null,
        onResult: (Boolean) -> Unit = {}
    ) {
        if (currentUserId.isEmpty() || (text.isBlank() && imageUrl == null)) return
        viewModelScope.launch {
            val conversation = _conversations.value.find { it.id == conversationId }
            val participantIds = conversation?.participantIds ?: if (otherUserId != "group" && otherUserId.isNotEmpty()) {
                listOf(currentUserId, otherUserId)
            } else {
                try {
                    val doc = db.collection("conversations").document(conversationId).get().await()
                    (doc.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: listOf(currentUserId)
                } catch (e: Exception) {
                    listOf(currentUserId)
                }
            }

            val success = try {
                kotlinx.coroutines.withTimeoutOrNull(SEND_TIMEOUT_MS) {
                    repository.sendMessage(
                        conversationId = conversationId,
                        senderId = currentUserId,
                        text = text,
                        participantIds = participantIds,
                        imageUrl = imageUrl,
                        replyToId = replyToId,
                        replyToText = replyToText,
                        replyToSenderName = replyToSenderName
                    )
                } ?: false
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
            onResult(success)
        }
    }

    fun unsendMessage(conversationId: String, messageId: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.unsendMessage(conversationId, messageId)
                onResult(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    fun uploadImage(uri: android.net.Uri, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference
        val imageRef = storageRef.child("chat_images/${java.util.UUID.randomUUID()}")
        imageRef.putFile(uri)
            .addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { url ->
                    onSuccess(url.toString())
                }
            }
            .addOnFailureListener {
                onFailure(it)
            }
    }

    companion object {
        private const val SEND_TIMEOUT_MS = 8000L
    }

    /** Toggles the current user's reaction on a message — tapping the same emoji again
     *  clears it, tapping a different one replaces it (one reaction per person, per message). */
    fun toggleReaction(conversationId: String, message: Message, emoji: String) {
        if (currentUserId.isEmpty()) return
        viewModelScope.launch {
            try {
                val current = message.reactions[currentUserId]
                val newEmoji = if (current == emoji) null else emoji
                repository.setReaction(conversationId, message.id, currentUserId, newEmoji)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setTyping(conversationId: String, isTyping: Boolean) {
        if (currentUserId.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.setTypingStatus(currentUserId, if (isTyping) conversationId else null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeParticipant(conversationId: String, userId: String) {
        viewModelScope.launch {
            try {
                repository.removeParticipant(conversationId, userId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addParticipants(conversationId: String, newParticipants: List<User>) {
        viewModelScope.launch {
            try {
                repository.addParticipants(conversationId, newParticipants, currentUserName, currentUserId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun promoteToAdmin(conversationId: String, userId: String) {
        viewModelScope.launch {
            try {
                repository.promoteToAdmin(conversationId, userId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateGroupName(conversationId: String, newName: String) {
        viewModelScope.launch {
            try {
                repository.updateGroupName(conversationId, newName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateGroupPhoto(conversationId: String, photoUrl: String) {
        viewModelScope.launch {
            try {
                repository.updateGroupPhoto(conversationId, photoUrl)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun leaveGroup(conversationId: String, onResult: (Boolean) -> Unit = {}) {
        if (currentUserId.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.leaveGroup(conversationId, currentUserId, currentUserName)
                onResult(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }
}
