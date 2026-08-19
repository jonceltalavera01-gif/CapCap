package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ── Colour tokens ─────────────────────────────────────────────────────────────
private val Green900  = Color(0xFF06402B)
private val Green700  = Color(0xFF0A5C3D)
private val Green100  = Color(0xFFE8F5E9)
private val SurfaceBg = Color(0xFFF4F6F5)
private val OnSurface = Color(0xFF1A1A1A)

// ── Utils ─────────────────────────────────────────────────────────────────────
private fun formatRelativeTime(timestamp: Timestamp?): String {
    if (timestamp == null) return ""
    val now = System.currentTimeMillis()
    val time = timestamp.toDate().time
    val diff = now - time

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 172800_000 -> "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(timestamp.toDate())
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(
    navController: NavController,
    paddingValues: PaddingValues,
    viewModel: ChatViewModel = viewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    var showSearch  by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showNewMessageDialog by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val friendsDrawerState = androidx.compose.material3.rememberDrawerState(
        initialValue = androidx.compose.material3.DrawerValue.Closed
    )
    val drawerScope = rememberCoroutineScope()

    val activeConversations by viewModel.activeConversations.collectAsStateWithLifecycle()
    val oldConversations by viewModel.oldConversations.collectAsStateWithLifecycle()
    val onlineUsers by viewModel.onlineUsers.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val myFriends by viewModel.myFriends.collectAsStateWithLifecycle()
    val currentUserId = viewModel.currentUserId

    val conversationsToDisplay = if (showHistory) oldConversations else activeConversations

    val filtered = if (searchQuery.isBlank()) conversationsToDisplay
    else conversationsToDisplay.filter {
        it.participantNames.values.any { name -> name.contains(searchQuery, ignoreCase = true) } ||
                it.lastMessage.contains(searchQuery, ignoreCase = true)
    }

    val totalUnread = activeConversations.sumOf { it.unreadCounts[currentUserId] ?: 0 }

    // ModalNavigationDrawer only opens from the layout "start" edge. Wrapping in
    // Rtl flips start to the right (matching TrainingScreen's PlanHistoryDrawer),
    // then each inner block flips back to Ltr so content renders normally.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        androidx.compose.material3.ModalNavigationDrawer(
            drawerState = friendsDrawerState,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    FriendsDrawer(
                        viewModel = viewModel,
                        navController = navController,
                        myFriends = myFriends,
                        onClose = { drawerScope.launch { friendsDrawerState.close() } },
                        onCreateGroup = {
                            drawerScope.launch { friendsDrawerState.close() }
                            showCreateGroupDialog = true
                        }
                    )
                }
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                if (showSearch) {
                                    OutlinedTextField(
                                        value         = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder   = { Text("Search messages…", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f)) },
                                        singleLine    = true,
                                        colors        = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor      = Color.White.copy(alpha = 0.5f),
                                            unfocusedBorderColor    = Color.White.copy(alpha = 0.3f),
                                            focusedContainerColor   = Color.White.copy(alpha = 0.12f),
                                            unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                                            cursorColor             = Color.White,
                                            focusedTextColor        = Color.White,
                                            unfocusedTextColor      = Color.White
                                        ),
                                        shape    = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().height(52.dp)
                                    )
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (showHistory) Icons.Default.Delete else Icons.Default.Chat, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (showHistory) "History" else "Messages", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 20.sp)
                                        if (totalUnread > 0 && !showHistory) {
                                            Spacer(Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFFF4444))
                                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(if (totalUnread > 99) "99+" else "$totalUnread", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = {
                                    if (showSearch) { showSearch = false; searchQuery = "" }
                                    else navController.popBackStack()
                                }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }
                            },
                            actions = {
                                if (showSearch) {
                                    IconButton(onClick = { showSearch = false; searchQuery = "" }) {
                                        Icon(Icons.Default.Close, null, tint = Color.White)
                                    }
                                } else {
                                    IconButton(onClick = { showSearch = true }) {
                                        Icon(Icons.Default.Search, null, tint = Color.White)
                                    }

                                    IconButton(onClick = { drawerScope.launch { friendsDrawerState.open() } }) {
                                        Icon(Icons.Default.People, "Friends", tint = Color.White)
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Green900)
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick        = { showHistory = !showHistory },
                            containerColor = if (showHistory) Color.Gray else Green900,
                            contentColor   = Color.White,
                            shape          = CircleShape,
                            modifier       = Modifier
                                .padding(bottom = paddingValues.calculateBottomPadding())
                                .size(56.dp)
                        ) {
                            Icon(if (showHistory) Icons.Default.Chat else Icons.Default.Delete, contentDescription = if (showHistory) "Messages" else "History", modifier = Modifier.size(24.dp))
                        }
                    },
                    containerColor = SurfaceBg
                ) { innerPadding ->
                    if (showCreateGroupDialog) {
                        CreateGroupDialog(
                            viewModel = viewModel,
                            myFriends = myFriends,
                            onDismiss = { showCreateGroupDialog = false },
                            onGroupCreated = { conversationId, name ->
                                showCreateGroupDialog = false
                                navController.navigate("chat/$conversationId/group/$name")
                            }
                        )
                    }
                    if (showNewMessageDialog) {
                        SearchUserDialog(
                            viewModel     = viewModel,
                            navController = navController,
                            onDismiss     = { showNewMessageDialog = false }
                        )
                    }

                    LazyColumn(
                        modifier        = Modifier.fillMaxSize().padding(innerPadding),
                        contentPadding  = PaddingValues(bottom = paddingValues.calculateBottomPadding() + 80.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {

                        // ── Online riders row ─────────────────────────────────────────────
                        if (!showSearch || searchQuery.isBlank()) {
                            item {
                                OnlineRidersRow(
                                    friends       = myFriends,
                                    onlineUsers   = onlineUsers,
                                    viewModel     = viewModel,
                                    navController = navController
                                )
                            }
                        }

                        // ── Search result label ───────────────────────────────────────────
                        if (showSearch && searchQuery.isNotBlank()) {
                            item {
                                Text(
                                    "${filtered.size} result${if (filtered.size != 1) "s" else ""} for \"$searchQuery\"",
                                    fontSize = 13.sp,
                                    color    = Color.Gray,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                                )
                            }
                        } else {
                            item {
                                Text(
                                    if (showHistory) "Archived Conversations" else "Recent",
                                    fontSize   = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = Color.Gray,
                                    modifier   = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                                )
                            }
                        }

                        // ── Empty search state ────────────────────────────────────────────
                        if (filtered.isEmpty()) {
                            item {
                                Box(
                                    modifier         = Modifier.fillMaxWidth().padding(48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.SearchOff,
                                            contentDescription = null,
                                            tint     = Color(0xFFCCCCCC),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text("No conversations found", color = Color.Gray, fontSize = 14.sp)
                                    }
                                }
                            }
                        }

                        // ── Contact list ──────────────────────────────────────────────────
                        items(filtered) { conversation ->
                            val otherParticipantId = conversation.participantIds.find { it != currentUserId }
                            val isGroup = conversation.isGroup || conversation.participantIds.size > 2
                            val otherParticipantName = if (isGroup) {
                                conversation.groupName ?: "Group Chat"
                            } else {
                                conversation.participantNames[otherParticipantId] ?: "Unknown"
                            }
                            val unreadCount = conversation.unreadCounts[currentUserId] ?: 0
                            val otherUserPresence = if (!isGroup) onlineUsers.find { it.userId == otherParticipantId } else null

                            // For 1:1, we need to find the other user's profile picture if we can.
                            // But OnlineRidersRow already sorts friends, maybe we can find it in myFriends?
                            val otherUser = if (!isGroup) myFriends.find { it.uid == otherParticipantId } else null
                            val photoToShow = if (isGroup) conversation.groupPhotoUrl else otherUser?.photoUrl

                            ContactItem(
                                name = otherParticipantName,
                                photoUrl = photoToShow,
                                lastMessage = conversation.lastMessage,
                                timestamp = formatRelativeTime(conversation.lastMessageTimestamp),
                                unreadCount = unreadCount,
                                isOnline = otherUserPresence?.isOnline ?: false,
                                isTyping = if (!isGroup) otherUserPresence?.typingIn == conversation.id else false,
                                isGroup = isGroup,
                                onClick = {
                                    viewModel.markAsRead(conversation.id)
                                    navController.navigate(
                                        "chat/${conversation.id}/${if (isGroup) "group" else (otherParticipantId ?: "")}/${otherParticipantName}"
                                    )
                                }
                            )
                            HorizontalDivider(
                                modifier  = Modifier.padding(start = 82.dp, end = 16.dp),
                                thickness = 0.5.dp,
                                color     = Color(0xFFF0F0F0)
                            )
                        }
                    }
                }
            } // end Ltr CompositionLocalProvider (main content)
        } // end ModalNavigationDrawer
    } // end Rtl CompositionLocalProvider
}

// ── Friends Drawer — friend requests, friend list w/ unfriend, search-to-add ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsDrawer(
    viewModel: ChatViewModel,
    navController: NavController,
    myFriends: List<User>,
    onClose: () -> Unit,
    onCreateGroup: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val sentRequestIds by viewModel.sentRequestIds.collectAsStateWithLifecycle()
    val friendRequests = notifications.filter { it.type == "friend_request" }
    var pendingUnfriend by remember { mutableStateOf<User?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.material3.ModalDrawerSheet(
        drawerContainerColor = Color.White,
        modifier = Modifier.widthIn(max = 320.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(vertical = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Friends", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Green900)
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFF3F4F6))
                ) { Icon(Icons.Default.Close, "Close", tint = Color.Gray, modifier = Modifier.size(16.dp)) }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it; viewModel.searchUsers(it) },
                placeholder = { Text("Search to add a friend…", fontSize = 13.sp, color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = OnSurface, unfocusedTextColor = OnSurface,
                    focusedBorderColor = Green700, unfocusedBorderColor = Color(0xFFCCCCCC),
                    cursorColor = Green900
                ),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; viewModel.searchUsers("") }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                }
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ── Search results (only shown while actively searching) ──────
                if (query.isNotBlank()) {
                    item {
                        Text("Results", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                    if (results.isEmpty()) {
                        item {
                            Text("No users found.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                    items(results.filter { it.uid != viewModel.currentUserId }) { user ->
                        val isFriend = myFriends.any { it.uid == user.uid }
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF8FBF9)).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FriendAvatar(user)
                            Spacer(Modifier.width(10.dp))
                            Text(user.username, fontWeight = FontWeight.SemiBold, color = OnSurface, modifier = Modifier.weight(1f))
                            if (isFriend) {
                                Button(
                                    onClick = {
                                        viewModel.getOrCreateConversation(user.uid, user.username) { conversationId ->
                                            onClose()
                                            navController.navigate("chat/$conversationId/${user.uid}/${user.username}")
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Green700)
                                ) { Text("Message", fontSize = 12.sp) }
                            } else {
                                val alreadySent = sentRequestIds.contains(user.uid)
                                Button(
                                    onClick = {
                                        viewModel.sendFriendRequest(user) { created ->
                                            val msg = if (created) "Request sent!" else "You already sent a request to this person."
                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = !alreadySent,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Green900,
                                        contentColor = Color.White,
                                        disabledContainerColor = Color(0xFFCDD8CD),
                                        disabledContentColor = Color.White
                                    )
                                ) { Text(if (alreadySent) "Sent" else "Add", fontSize = 12.sp) }
                            }
                        }
                    }
                } else {
                    // ── Pending friend requests ────────────────────────────────
                    if (friendRequests.isNotEmpty()) {
                        item {
                            Text("Requests", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        }
                        items(friendRequests) { notif ->
                            Column(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF8FBF9)).padding(10.dp)
                            ) {
                                Text(notif.message, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = OnSurface)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            viewModel.respondToFriendRequest(notif.requestId, true)
                                            viewModel.markNotificationRead(notif.id)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Green900),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) { Text("Accept", fontSize = 12.sp) }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.respondToFriendRequest(notif.requestId, false)
                                            viewModel.markNotificationRead(notif.id)
                                        },
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) { Text("Decline", fontSize = 12.sp, color = Green900) }
                                }
                            }
                        }
                    }

                    // ── My friends ──────────────────────────────────────────────
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("My Friends (${myFriends.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            TextButton(
                                onClick = onCreateGroup,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.GroupAdd, null, modifier = Modifier.size(16.dp), tint = Green700)
                                Spacer(Modifier.width(4.dp))
                                Text("New Group", fontSize = 12.sp, color = Green700)
                            }
                        }
                    }
                    if (myFriends.isEmpty()) {
                        item {
                            Text("No friends yet — search above to add someone.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                    items(myFriends, key = { it.uid }) { friend ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    onClose()
                                    navController.navigate("public_profile/${friend.username}")
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FriendAvatar(friend)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    friend.displayName.ifBlank { friend.username },
                                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OnSurface
                                )
                                if (friend.isOnline) {
                                    Text("Online", fontSize = 11.sp, color = Color(0xFF4CAF50))
                                }
                            }
                            IconButton(
                                onClick = {
                                    viewModel.getOrCreateConversation(friend.uid, friend.username) { conversationId ->
                                        onClose()
                                        navController.navigate("chat/$conversationId/${friend.uid}/${friend.username}")
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Message, "Message", tint = Green700, modifier = Modifier.size(17.dp))
                            }
                            IconButton(
                                onClick = { pendingUnfriend = friend },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.PersonRemove, "Unfriend", tint = Color(0xFFDC2626), modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Unfriend confirmation ──────────────────────────────────────────────────
    pendingUnfriend?.let { friend ->
        AlertDialog(
            onDismissRequest = { pendingUnfriend = null },
            title = { Text("Unfriend ${friend.displayName.ifBlank { friend.username }}?") },
            text = { Text("You'll need to send a new friend request to reconnect. Your message history will stay intact.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.unfriend(friend.uid); pendingUnfriend = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White)
                ) { Text("Unfriend") }
            },
            dismissButton = { TextButton(onClick = { pendingUnfriend = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun FriendAvatar(user: User) {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(Green100),
        contentAlignment = Alignment.Center
    ) {
        if (user.photoUrl != null) {
            coil.compose.AsyncImage(
                model = user.photoUrl, contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Text(user.username.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Green900)
        }
    }
}

// ── Notifications Dialog (friend requests + accepted alerts) ─────────────────
@Composable
fun NotificationsDialog(
    viewModel: ChatViewModel,
    notifications: List<AppNotification>,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Notifications", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Green900)
                Spacer(Modifier.height(12.dp))

                if (notifications.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), Alignment.Center) {
                        Text("No new notifications.", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(notifications) { notif ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF8FBF9))
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Brush.linearGradient(listOf(Green900, Green700))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            notif.userName.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            notif.userName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Green900
                                        )
                                        Text(
                                            notif.message,
                                            fontSize = 13.sp,
                                            color = OnSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                if (notif.type == "friend_request") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                viewModel.respondToFriendRequest(notif.requestId, true)
                                                viewModel.markNotificationRead(notif.id)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Green900),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) { Text("Accept", fontSize = 12.sp) }

                                        OutlinedButton(
                                            onClick = {
                                                viewModel.respondToFriendRequest(notif.requestId, false)
                                                viewModel.markNotificationRead(notif.id)
                                            },
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) { Text("Decline", fontSize = 12.sp, color = Green900) }
                                    }
                                } else {
                                    TextButton(onClick = { viewModel.markNotificationRead(notif.id) }) {
                                        Text("Mark as read", color = Green700, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close", color = Green900)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupDialog(
    viewModel: ChatViewModel,
    myFriends: List<User>,
    onDismiss: () -> Unit,
    onGroupCreated: (String, String) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    val selectedParticipants = remember { mutableStateListOf<User>() }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Create Group Chat",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = Green900
                )

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    placeholder = { Text("Group Name", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface,
                        focusedBorderColor = Green700,
                        unfocusedBorderColor = Color(0xFFCCCCCC),
                        cursorColor = Green900
                    )
                )

                Text(
                    "Select Friends (${selectedParticipants.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(myFriends) { friend ->
                        val isSelected = selectedParticipants.contains(friend)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Green100 else Color(0xFFF8FBF9))
                                .clickable {
                                    if (isSelected) selectedParticipants.remove(friend)
                                    else selectedParticipants.add(friend)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FriendAvatar(friend)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                friend.displayName.ifBlank { friend.username },
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Green900,
                                    checkmarkColor = Color.White
                                )
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (groupName.isNotBlank() && selectedParticipants.isNotEmpty()) {
                                viewModel.createGroupChat(groupName, selectedParticipants.toList()) { id ->
                                    onGroupCreated(id, groupName)
                                }
                            }
                        },
                        enabled = groupName.isNotBlank() && selectedParticipants.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Green900,
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFFCDD8CD),
                            disabledContentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
}

// ── Search User Dialog ────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchUserDialog(
    viewModel: ChatViewModel,
    navController: NavController,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val myFriendIds by viewModel.myFriendIds.collectAsStateWithLifecycle()
    val sentRequestIds by viewModel.sentRequestIds.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Add Friend",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = Green900
                )

                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        viewModel.searchUsers(it)
                    },
                    placeholder = { Text("Search by name…", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface,
                        focusedBorderColor = Green700,
                        unfocusedBorderColor = Color(0xFFCCCCCC),
                        cursorColor = Green900
                    ),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = ""; viewModel.searchUsers("") }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                    }
                )

                if (results.isEmpty() && query.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), Alignment.Center) {
                        Text("No users found.", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, false),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(results) { user ->
                            if (user.uid != viewModel.currentUserId) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFF8FBF9))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Green100),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (user.photoUrl != null) {
                                            coil.compose.AsyncImage(
                                                model = user.photoUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        } else {
                                            Text(
                                                user.username.take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                color = Green900
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        user.username,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OnSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (myFriendIds.contains(user.uid)) {
                                        Button(
                                            onClick = {
                                                viewModel.getOrCreateConversation(user.uid, user.username) { conversationId ->
                                                    onDismiss()
                                                    navController.navigate("chat/$conversationId/${user.uid}/${user.username}")
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Green700)
                                        ) {
                                            Text("Message", fontSize = 12.sp)
                                        }
                                    } else {
                                        val alreadySent = sentRequestIds.contains(user.uid)
                                        Button(
                                            onClick = {
                                                viewModel.sendFriendRequest(user) { created ->
                                                    val msg = if (created) "Request sent!" else "You already sent a request to this person."
                                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                                onDismiss()
                                            },
                                            enabled = !alreadySent,
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Green900,
                                                contentColor = Color.White,
                                                disabledContainerColor = Color(0xFFCDD8CD),
                                                disabledContentColor = Color.White
                                            )
                                        ) {
                                            Text(if (alreadySent) "Sent" else "Add", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close", color = Green900)
                }
            }
        }
    }
}

// ── Friends quick-access row (Messenger-style: online first, offline still tappable) ──
@Composable
fun OnlineRidersRow(
    friends: List<User>,
    onlineUsers: List<UserPresence>,
    viewModel: ChatViewModel,
    navController: NavController
) {
    if (friends.isEmpty()) return // nothing to show until the person has friends

    val onlineIds = remember(onlineUsers) { onlineUsers.map { it.userId }.toSet() }
    val sortedFriends = remember(friends, onlineIds) {
        friends.sortedByDescending { it.uid in onlineIds }
    }
    val onlineCount = sortedFriends.count { it.uid in onlineIds }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier           = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment  = Alignment.CenterVertically
        ) {
            Text("Friends", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnSurface)
            if (onlineCount > 0) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF4CAF50))
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "$onlineCount online",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color(0xFF4CAF50)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyRow(
            contentPadding        = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(sortedFriends, key = { it.uid }) { friend ->
                val isOnline = friend.uid in onlineIds
                val name     = friend.displayName.ifBlank { friend.username }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        viewModel.getOrCreateConversation(friend.uid, friend.username) { conversationId ->
                            navController.navigate("chat/$conversationId/${friend.uid}/${friend.username}")
                        }
                    }
                ) {
                    Box {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .alpha(if (isOnline) 1f else 0.45f)
                                .background(Brush.linearGradient(listOf(Green900, Green700))),
                            contentAlignment = Alignment.Center
                        ) {
                            if (friend.photoUrl != null) {
                                coil.compose.AsyncImage(
                                    model = friend.photoUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Text(
                                    name.take(1).uppercase(),
                                    fontSize   = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color      = Color.White
                                )
                            }
                        }
                        // Status dot — green when online, muted grey when offline
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .align(Alignment.BottomEnd),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isOnline) Color(0xFF4CAF50) else Color(0xFFBDBDBD))
                            )
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        name.split(" ").first(),
                        fontSize   = 11.sp,
                        color      = if (isOnline) OnSurface else Color.Gray,
                        fontWeight = FontWeight.Medium,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.alpha(if (isOnline) 1f else 0.7f)
                    )
                }
            }
        }
    }
    HorizontalDivider(color = Color(0xFFF0F0F0), thickness = 0.5.dp)
}

// ── Contact row ───────────────────────────────────────────────────────────────
@Composable
fun ContactItem(
    name: String,
    photoUrl: String? = null,
    lastMessage: String,
    timestamp: String,
    unreadCount: Int,
    isOnline: Boolean,
    isTyping: Boolean,
    isGroup: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .background(if (unreadCount > 0) Color(0xFFF9FFF9) else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar + online dot
        Box(modifier = Modifier.size(54.dp)) {
            Box(
                modifier         = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        if (unreadCount > 0)
                            Brush.linearGradient(listOf(Green900, Green700))
                        else
                            Brush.linearGradient(listOf(Color(0xFF9E9E9E), Color(0xFF757575)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (photoUrl != null) {
                    coil.compose.AsyncImage(
                        model = photoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else if (isGroup) {
                    Icon(Icons.Default.People, null, tint = Color.White)
                } else {
                    Text(
                        name.take(1).uppercase(),
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = Color.White
                    )
                }
            }
            if (isOnline) {
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .align(Alignment.BottomEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // Name + last message
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = name,
                fontWeight = if (unreadCount > 0) FontWeight.ExtraBold else FontWeight.SemiBold,
                fontSize   = 15.sp,
                color      = OnSurface,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            if (isTyping) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "typing…",
                        fontSize   = 13.sp,
                        color      = Green700,
                        fontWeight = FontWeight.Medium,
                        fontStyle  = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            } else {
                Text(
                    text     = lastMessage,
                    color    = if (unreadCount > 0) OnSurface else Color.Gray,
                    fontSize = 13.sp,
                    fontWeight = if (unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Timestamp + unread badge
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text     = timestamp,
                fontSize = 11.sp,
                color    = if (unreadCount > 0) Green900 else Color.Gray,
                fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal
            )
            Spacer(Modifier.height(4.dp))
            if (unreadCount > 0) {
                Box(
                    modifier         = Modifier
                        .clip(CircleShape)
                        .background(Green900)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (unreadCount > 99) "99+" else "${unreadCount}",
                        color      = Color.White,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // Spacer to keep alignment consistent
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

// ── Status badge (kept for backward compat) ───────────────────────────────────
@Composable
fun StatusBadge(isOnline: Boolean) {
    val bg         = if (isOnline) Color.DarkGray else Color.Gray
    val statusText = if (isOnline) "Active" else "Offline"
    val dotColor   = if (isOnline) Color(0xFF4CAF50) else Color.Red
    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
        Text(statusText, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}