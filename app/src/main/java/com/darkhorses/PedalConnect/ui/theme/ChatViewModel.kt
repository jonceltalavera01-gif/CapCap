package com.darkhorses.PedalConnect.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChatViewModel : ViewModel() {
    private val repository = ChatRepository()
    private val auth = FirebaseAuth.getInstance()
    private val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
    val currentUserId = auth.currentUser?.uid ?: ""
    var currentUserName = ""

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

    // ── Active chat thread ───────────────────────────────────────────────────
    private val _activeMessages = MutableStateFlow<List<Message>>(emptyList())
    val activeMessages: StateFlow<List<Message>> = _activeMessages.asStateFlow()
    private var activeMessagesJob: Job? = null

    init {
        if (currentUserId.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    val userDoc = db.collection("users").document(currentUserId).get().await()
                    currentUserName = userDoc.getString("username") ?: ""
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            viewModelScope.launch {
                try {
                    repository.getConversationsForUser(currentUserId).collect {
                        _conversations.value = it
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
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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

    fun sendFriendRequest(toUser: User) {
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
                if (currentUserName.isEmpty()) return@launch
                repository.sendFriendRequest(currentUserId, currentUserName, toUser.uid, toUser.username)
            } catch (e: Exception) {
                e.printStackTrace()
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

    fun openConversation(conversationId: String) {
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

    fun closeConversation() {
        activeMessagesJob?.cancel()
        activeMessagesJob = null
        _activeMessages.value = emptyList()
    }

    fun sendMessage(conversationId: String, otherUserId: String, text: String) {
        if (currentUserId.isEmpty() || text.isBlank()) return
        viewModelScope.launch {
            try {
                repository.sendMessage(
                    conversationId = conversationId,
                    senderId = currentUserId,
                    text = text,
                    participantIds = listOf(currentUserId, otherUserId)
                )
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
}