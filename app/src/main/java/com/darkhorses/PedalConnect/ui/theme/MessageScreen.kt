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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.firebase.Timestamp
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
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }

    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val onlineUsers by viewModel.onlineUsers.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val currentUserId = viewModel.currentUserId

    val filtered = if (searchQuery.isBlank()) conversations
    else conversations.filter {
        it.participantNames.values.any { name -> name.contains(searchQuery, ignoreCase = true) } ||
                it.lastMessage.contains(searchQuery, ignoreCase = true)
    }

    val totalUnread = conversations.sumOf { it.unreadCounts[currentUserId] ?: 0 }

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
                            Icon(Icons.Default.Chat, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Messages", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 20.sp)
                            if (totalUnread > 0) {
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

                        // ── Notifications bell with unread badge ──────────────
                        Box {
                            IconButton(onClick = { showNotificationsDialog = true }) {
                                Icon(Icons.Default.Notifications, "Notifications", tint = Color.White)
                            }
                            if (notifications.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-4).dp, y = 4.dp)
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF4444)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (notifications.size > 9) "9+" else "${notifications.size}",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        IconButton(onClick = { showAddFriendDialog = true }) {
                            Icon(Icons.Default.PersonAdd, "Add Friend", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Green900)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { /* New message */ },
                containerColor = Green900,
                contentColor   = Color.White,
                shape          = CircleShape,
                modifier       = Modifier
                    .padding(bottom = paddingValues.calculateBottomPadding())
                    .size(56.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = "New message", modifier = Modifier.size(24.dp))
            }
        },
        containerColor = SurfaceBg
    ) { innerPadding ->
        if (showAddFriendDialog) {
            SearchUserDialog(
                viewModel = viewModel,
                onDismiss = { showAddFriendDialog = false }
            )
        }

        if (showNotificationsDialog) {
            NotificationsDialog(
                viewModel     = viewModel,
                notifications = notifications,
                onDismiss     = { showNotificationsDialog = false }
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
                    OnlineRidersRow(onlineUsers = onlineUsers)
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
                        "Recent",
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
                val otherParticipantName = conversation.participantNames[otherParticipantId] ?: "Unknown"
                val unreadCount = conversation.unreadCounts[currentUserId] ?: 0
                val otherUserPresence = onlineUsers.find { it.userId == otherParticipantId }

                ContactItem(
                    name = otherParticipantName,
                    lastMessage = conversation.lastMessage,
                    timestamp = formatRelativeTime(conversation.lastMessageTimestamp),
                    unreadCount = unreadCount,
                    isOnline = otherUserPresence?.isOnline ?: false,
                    isTyping = otherUserPresence?.typingIn == conversation.id,
                    onClick = {
                        viewModel.markAsRead(conversation.id)
                        /* Navigate to chat */
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
                                            .background(Green100),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            notif.userName.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = Green900,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(notif.message, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
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

// ── Search User Dialog ────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchUserDialog(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
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
                    placeholder = { Text("Search by name…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
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
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = {
                                            viewModel.sendFriendRequest(user)
                                            android.widget.Toast.makeText(context, "Request sent!", android.widget.Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Green900)
                                    ) {
                                        Text("Add", fontSize = 12.sp)
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

// ── Online riders horizontal scroll ──────────────────────────────────────────
@Composable
fun OnlineRidersRow(onlineUsers: List<UserPresence>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${onlineUsers.size} Online Now",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color      = OnSurface
                )
            }
            Text("See all", fontSize = 12.sp, color = Green700, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(10.dp))

        LazyRow(
            contentPadding        = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(onlineUsers) { presence ->
                val name = presence.userName
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { /* Open chat */ }
                ) {
                    Box {
                        Box(
                            modifier         = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(Green900, Green700))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                name.take(1).uppercase(),
                                fontSize   = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color      = Color.White
                            )
                        }
                        // Online dot
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
                                    .background(Color(0xFF4CAF50))
                            )
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        name.split(" ").first(),
                        fontSize   = 11.sp,
                        color      = OnSurface,
                        fontWeight = FontWeight.Medium,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
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
    lastMessage: String,
    timestamp: String,
    unreadCount: Int,
    isOnline: Boolean,
    isTyping: Boolean,
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
                Text(
                    name.take(1).uppercase(),
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = Color.White
                )
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