package com.darkhorses.PedalConnect.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
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
        if (currentUserId.isEmpty() || currentUserName.isEmpty()) return
        viewModelScope.launch {
            try {
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
}