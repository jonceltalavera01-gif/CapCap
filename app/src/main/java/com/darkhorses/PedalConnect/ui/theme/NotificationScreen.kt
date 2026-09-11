package com.darkhorses.PedalConnect.ui.theme

// ── NavGraph wiring required ──────────────────────────────────────────────────
// In your NavHost, add:
//   composable("home_alerts/{userName}") { back ->
//       val user = back.arguments?.getString("userName") ?: ""
//       HomeScreen(navController = navController, userName = user, openAlertsTab = true)
//   }
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material.icons.filled.Delete
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.text.buildString

// ── Colour tokens — reuse shared design system values ─────────────────────────
private val NGreen900  = Color(0xFF06402B)
private val NGreen700  = Color(0xFF0A5C3D)
private val NGreen100  = Color(0xFFDDF1E8)
private val NSurfaceBg = Color(0xFFF5F7F6)
private val NOnSurface = Color(0xFF111827)

// ── Notification type config ──────────────────────────────────────────────────
private data class NotifStyle(
    val icon:      ImageVector,
    val iconTint:  Color,
    val iconBg:    Color,
    val cardBg:    Color,
    val label:     String
)

private fun notifStyle(type: String): NotifStyle = when (type) {
    "accepted"  -> NotifStyle(Icons.Default.CheckCircle,      Color(0xFF388E3C), Color(0xFFE8F5E9), Color(0xFFF1FBF2), "Accepted")
    "rejected"  -> NotifStyle(Icons.Default.Cancel,           Color(0xFFD32F2F), Color(0xFFFFEBEE), Color(0xFFFFF5F5), "Rejected")
    "like"      -> NotifStyle(Icons.Default.Favorite,         Color(0xFFE91E63), Color(0xFFFCE4EC), Color(0xFFFFF0F4), "Like")
    "comment"   -> NotifStyle(Icons.Default.ChatBubble,       Color(0xFF1565C0), Color(0xFFE3F2FD), Color(0xFFF3F8FF), "Comment")
    "alert"     -> NotifStyle(Icons.Default.Warning,          Color(0xFFD32F2F), Color(0xFFFFEBEE), Color(0xFFFFF5F5), "Alert")
    "follow"    -> NotifStyle(Icons.Default.PersonAdd,        NGreen900,          NGreen100,          Color(0xFFF5FBF5), "Follow")
    "friend_request" -> NotifStyle(Icons.Default.PersonAdd,        NGreen900,          NGreen100,          Color(0xFFF5FBF5), "Friend Request")
    "ride"      -> NotifStyle(Icons.Default.DirectionsBike,   NGreen700,          NGreen100,          Color(0xFFF5FBF5), "Ride")
    "reply"     -> NotifStyle(Icons.AutoMirrored.Filled.Send, Color(0xFF7B1FA2),  Color(0xFFF3E5F5),  Color(0xFFFAF5FF), "Reply")
    "resolve_requested"-> NotifStyle(Icons.Default.HelpOutline,      Color(0xFFF57C00), Color(0xFFFFF3E0), Color(0xFFFFFBF0), "Resolve Request")
    "rating"           -> NotifStyle(Icons.Default.Star,              Color(0xFFF57C00), Color(0xFFFFF3E0), Color(0xFFFFFBF0), "Rating")
    "moderation"       -> NotifStyle(Icons.Default.Gavel,             Color(0xFFD32F2F), Color(0xFFFFEBEE), Color(0xFFFFF5F5), "Moderation")
    "moderation_restored" -> NotifStyle(Icons.Default.CheckCircle,    Color(0xFF388E3C), Color(0xFFE8F5E9), Color(0xFFF1FBF2), "Restored")
    "report"           -> NotifStyle(Icons.Default.Flag,             Color(0xFFD32F2F), Color(0xFFFFEBEE), Color(0xFFFFF5F5), "Report")
    "ride_resolved"    -> NotifStyle(Icons.Default.CheckCircle,       Color(0xFF388E3C), Color(0xFFE8F5E9), Color(0xFFF1FBF2), "Resolved")
    "ride_still_active"-> NotifStyle(Icons.Default.Warning,          Color(0xFFD32F2F), Color(0xFFFFEBEE), Color(0xFFFFF5F5), "Still Active")
    "friend_accepted"  -> NotifStyle(Icons.Default.PersonAdd,         Color(0xFF388E3C), Color(0xFFE8F5E9), Color(0xFFF1FBF2), "Accepted")
    "friend_declined"  -> NotifStyle(Icons.Default.Cancel,            Color(0xFF7A8F7A), Color(0xFFF0F0F0), Color.White,       "Declined")
    "message"          -> NotifStyle(Icons.Default.ChatBubble,        Color(0xFF1565C0), Color(0xFFE3F2FD), Color(0xFFF3F8FF), "Message")
    else               -> NotifStyle(Icons.Default.Notifications,    Color(0xFF7A8F7A), Color(0xFFF0F0F0), Color.White,       "Notification")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(navController: NavController, userName: String) {
    val db            = FirebaseFirestore.getInstance()
    val auth          = remember { com.google.firebase.auth.FirebaseAuth.getInstance() }
    val currentUserId = remember { auth.currentUser?.uid ?: "" }
    val notifications = remember { mutableStateListOf<NotificationItem>() }
    // Exclude resolve_requested from badge so it never blocks reaching zero
    val unreadCount   = notifications.count { !it.read && it.type != "resolve_requested" }
    var isRefreshing  by remember { mutableStateOf(false) }
    var isLoading     by remember { mutableStateOf(true) }
    var activeFilter     by remember { mutableStateOf("All") }
    val scope            = rememberCoroutineScope()

    val filterOptions = listOf("All", "Rides", "Alerts")

    val filteredNotifications = remember(notifications.toList(), activeFilter) {
        when (activeFilter) {
            "Rides"  -> notifications.filter { it.type == "ride" || it.type == "ride_resolved" || it.type == "ride_still_active" }
            "Alerts" -> notifications.filter { it.type == "alert" || it.type == "accepted" || it.type == "rating" || it.type == "resolve_requested" || it.type == "ride_resolved" || it.type == "ride_still_active" }
            else     -> notifications.filter { it.type != "message" }
        }
    }
    // Single real-time listener — handles both initial load and live updates
    // Pull-to-refresh is visual only; listener keeps data fresh automatically
    // IMPORTANT: query by toId (recipient's UID), NOT userName. userName on a
    // notification document refers to whoever triggered/sent the notification
    // (e.g. the sender's name in a friend request), not who it's intended for.
    DisposableEffect(currentUserId) {
        if (currentUserId.isEmpty()) {
            isLoading = false
            return@DisposableEffect onDispose { }
        }
        val registration = db.collection("notifications")
            .whereEqualTo("toId", currentUserId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) { isLoading = false; return@addSnapshotListener }
                snapshot?.let {
                    val loaded = mutableListOf<NotificationItem>()
                    val unreadIds = mutableListOf<String>()
                    for (doc in it.documents) {
                        loaded.add(
                            NotificationItem(
                                id        = doc.id,
                                message   = doc.getString("message") ?: "",
                                type      = doc.getString("type") ?: "",
                                timestamp = doc.getLong("timestamp") ?: 0L,
                                read      = doc.getBoolean("read") ?: false,
                                alertId   = doc.getString("alertId"),
                                postId    = doc.getString("postId"),
                                requestId = doc.getString("requestId"),
                                conversationId = doc.getString("conversationId"),
                                userName  = doc.getString("userName") ?: ""
                            )
                        )
                        // Collect unread IDs for bulk mark-as-read
                        if (doc.getBoolean("read") != true) {
                            unreadIds.add(doc.id)
                        }
                    }
                    notifications.clear()
                    notifications.addAll(
                        loaded.sortedWith(
                            compareByDescending<NotificationItem> { it.type == "resolve_requested" }
                                .thenByDescending { it.timestamp }
                        )
                    )
                    isLoading = false

                    // Mark all unread notifications as read in a single batch write
                    if (unreadIds.isNotEmpty()) {
                        val batch = db.batch()
                        unreadIds.forEach { id ->
                            batch.update(
                                db.collection("notifications").document(id),
                                "read", true
                            )
                        }
                        batch.commit()
                    }

                    // Auto-cleanup — only notifications that are BOTH read and
                    // older than 30 days get removed, mirroring the 30-day
                    // adminTrash retention convention used elsewhere. Anything
                    // unread, or newer than 30 days, is left alone; manual
                    // deletion (swipe / "Clear all") remains available anytime.
                    val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
                    val nowMs = System.currentTimeMillis()
                    val staleIds = it.documents.filter { doc ->
                        val isRead = doc.getBoolean("read") ?: false
                        val ts = doc.getLong("timestamp") ?: 0L
                        isRead && (nowMs - ts) > thirtyDaysMs
                    }.map { doc -> doc.id }
                    if (staleIds.isNotEmpty()) {
                        val cleanupBatch = db.batch()
                        staleIds.forEach { id ->
                            cleanupBatch.delete(db.collection("notifications").document(id))
                        }
                        cleanupBatch.commit()
                    }
                }
            }
        onDispose { registration.remove() }
    }


    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint     = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Notifications",
                            fontWeight    = FontWeight.ExtraBold,
                            color         = Color.White,
                            fontSize      = 20.sp,
                            letterSpacing = 0.3.sp
                        )
                        if (unreadCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier         = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF4444))
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (unreadCount > 99) "99+" else "$unreadCount",
                                    color      = Color.White,
                                    fontSize   = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (notifications.isNotEmpty()) {
                        TextButton(onClick = {
                            val toDelete = notifications.toList()
                            // Delete from Firestore immediately
                            toDelete.forEach { notif ->
                                db.collection("notifications")
                                    .document(notif.id)
                                    .delete()
                            }
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message     = "All notifications cleared",
                                    actionLabel = "Undo",
                                    duration    = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    // Restore all deleted notifications back to Firestore
                                    toDelete.forEach { notif ->
                                        db.collection("notifications")
                                            .document(notif.id)
                                            .set(hashMapOf(
                                                "toId"      to currentUserId,
                                                "userName"  to userName,
                                                "message"   to notif.message,
                                                "type"      to notif.type,
                                                "timestamp" to notif.timestamp,
                                                "read"      to notif.read,
                                                "alertId"   to notif.alertId,
                                                "postId"    to notif.postId,
                                                "conversationId" to notif.conversationId,
                                                "userName"  to notif.userName
                                            ))
                                    }
                                }
                            }
                        }) {
                            Text("Clear all", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NGreen900))
        },
        containerColor = NSurfaceBg
    ) { innerPadding ->

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = {
                // Visual-only refresh — real-time listener keeps data current
                scope.launch {
                    isRefreshing = true
                    kotlinx.coroutines.delay(600)
                    isRefreshing = false
                }
            },
            modifier     = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    // ── Loading state — shown until first snapshot arrives ─────────
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = NGreen900, strokeWidth = 2.5.dp,
                            modifier = Modifier.size(32.dp))
                    }
                }
                notifications.isEmpty() -> {
                    // ── Empty state — only shown after confirmed empty load ────────
                    Box(
                        modifier         = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier         = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(NGreen100),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.NotificationsNone,
                                    contentDescription = null,
                                    tint     = NGreen900,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Text("You're all caught up!",
                                fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NOnSurface)
                            Text(
                                "No notifications yet. We'll let you\nknow when something happens.",
                                fontSize  = 13.sp, color = Color.Gray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier        = Modifier.fillMaxSize(),
                        contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // ── Filter chips ──────────────────────────────────────────────
                        item {
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp)
                            ) {
                                items(filterOptions) { filter ->
                                    val isSelected = activeFilter == filter
                                    // Badge count per filter
                                    val badgeCount = when (filter) {
                                        "Rides"  -> notifications.count { it.type == "ride" || it.type == "ride_resolved" || it.type == "ride_still_active" }
                                        "Alerts" -> notifications.count { it.type == "alert" || it.type == "accepted" || it.type == "rating" || it.type == "resolve_requested" || it.type == "ride_resolved" || it.type == "ride_still_active" }
                                        else     -> 0
                                    }
                                    // Note: badgeCount is currently unused for Rides/Alerts styling beyond display —
                                    // consider whether resolved items should count toward the badge at all,
                                    // or just be included in the filtered list without inflating the number.
                                    FilterChip(
                                        selected = isSelected,
                                        onClick  = { activeFilter = filter },
                                        label    = {
                                            Row(
                                                verticalAlignment     = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    filter,
                                                    fontSize   = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold
                                                    else FontWeight.Normal
                                                )
                                                if (badgeCount > 0) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (isSelected) Color.White.copy(alpha = 0.25f)
                                                                else NGreen900.copy(alpha = 0.1f)
                                                            )
                                                            .padding(horizontal = 5.dp, vertical = 1.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            "$badgeCount",
                                                            fontSize   = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color      = if (isSelected) Color.White
                                                            else NGreen900
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor     = NGreen900,
                                            selectedLabelColor         = Color.White,
                                            containerColor             = Color.White,
                                            labelColor                 = NOnSurface
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled          = true,
                                            selected         = isSelected,
                                            borderColor      = Color(0xFFD1D5DB),
                                            selectedBorderColor = NGreen900,
                                            borderWidth      = 1.dp,
                                            selectedBorderWidth = 1.5.dp
                                        )
                                    )
                                }
                            }
                        }

                        // ── Section label ─────────────────────────────────────────────
                        item {
                            Text(
                                buildString {
                                    append("${filteredNotifications.size} ")
                                    when (activeFilter) {
                                        "All" -> append(if (filteredNotifications.size != 1) "notifications" else "notification")
                                        else  -> append(activeFilter.lowercase()) // "Rides" / "Alerts" already plural — don't append "s"
                                    }
                                },
                                fontSize   = 12.sp,
                                color      = Color.Gray,
                                fontWeight = FontWeight.Medium,
                                modifier   = Modifier.padding(start = 4.dp, bottom = 2.dp)
                            )
                        }

                        // ── Empty filter result ───────────────────────────────────────
                        if (filteredNotifications.isEmpty()) {
                            item {
                                Box(
                                    modifier         = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.FilterList, null,
                                            tint     = Color(0xFFD1D5DB),
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Text(
                                            "No ${activeFilter.lowercase()} notifications",
                                            fontSize = 14.sp, color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }

                        items(filteredNotifications, key = { it.id }) { notif ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        // Delete from Firestore immediately
                                        db.collection("notifications")
                                            .document(notif.id)
                                            .delete()
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message     = "Notification deleted",
                                                actionLabel = "Undo",
                                                duration    = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                // Restore the notification back to Firestore
                                                db.collection("notifications")
                                                    .document(notif.id)
                                                    .set(hashMapOf(
                                                        "toId"      to currentUserId,
                                                        "userName"  to userName,
                                                        "message"   to notif.message,
                                                        "type"      to notif.type,
                                                        "timestamp" to notif.timestamp,
                                                        "read"      to notif.read,
                                                        "alertId"   to notif.alertId,
                                                        "postId"    to notif.postId,
                                                        "conversationId" to notif.conversationId,
                                                        "userName"  to notif.userName
                                                    ))
                                            }
                                        }
                                        true
                                    } else false
                                }
                            )

                            SwipeToDismissBox(
                                state             = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    val alpha by animateFloatAsState(
                                        targetValue = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) 1f else 0f,
                                        label       = "delete_bg_alpha"
                                    )
                                    Box(
                                        modifier         = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFFD32F2F).copy(alpha = alpha)),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint     = Color.White,
                                            modifier = Modifier.padding(end = 20.dp)
                                        )
                                    }
                                }
                            ) {
                                NotificationCard(
                                    notif              = notif,
                                    currentUserId      = currentUserId,
                                    currentUserName    = userName,
                                    onNavigateToAlerts = {
                                        navController.navigate("home_alerts/$userName") {
                                            popUpTo("notifications/$userName") { inclusive = true }
                                        }
                                    },
                                    onNavigateToRides  = {
                                        navController.navigate("events/$userName") {
                                            popUpTo("notifications/$userName") { inclusive = true }
                                        }
                                    },
                                    onNavigateToFeed   = {
                                        navController.navigate("home_feed/$userName") {
                                            popUpTo("notifications/$userName") { inclusive = true }
                                        }
                                    },
                                    onNavigateToChat = { convId ->
                                        // We need the other user's info to open the chat properly
                                        // For now, we'll navigate to the messages list
                                        navController.navigate("messages")
                                    }
                                )
                            }
                        }
                    }
                } // end else
            } // end when
        } // end PullToRefreshBox
    } // end Scaffold content
} // end NotificationsScreen

// ── Notification card ─────────────────────────────────────────────────────────
@Composable
private fun NotificationCard(
    notif: NotificationItem,
    currentUserId: String,
    currentUserName: String,
    onNavigateToAlerts: () -> Unit = {},
    onNavigateToRides:  () -> Unit = {},
    onNavigateToFeed:   () -> Unit = {},
    onNavigateToChat:   (String) -> Unit = {}
) {
    val style   = notifStyle(notif.type)
    val db      = FirebaseFirestore.getInstance()
    var loading by remember { mutableStateOf(false) }

    // Show buttons if type matches — even if alertId is null (we'll fetch it live if needed)
    val isResolveRequest = notif.type == "resolve_requested"
    val isFriendRequest = notif.type == "friend_request"

    fun sendNotificationToUsername(targetUsername: String, message: String, type: String) {
        sendUserNotification(db, targetUsername, message, type, displayUserName = currentUserName)
    }

    fun sendResponderNotification(responder: String, riderName: String, eType: String, confirmed: Boolean) {
        if (responder.isBlank()) return
        // Fetch display name for the rider
        db.collection("users").whereEqualTo("username", riderName)
            .limit(1).get()
            .addOnSuccessListener { riderSnap ->
                val riderDisplay = riderSnap.documents.firstOrNull()
                    ?.getString("displayName")?.takeIf { it.isNotBlank() } ?: riderName
                val message = if (confirmed)
                    "$riderDisplay confirmed the $eType has been resolved. Thank you!"
                else
                    "$riderDisplay still needs help with $eType."
                sendNotificationToUsername(
                    targetUsername = responder,
                    message        = message,
                    type           = if (confirmed) "accepted" else "alert"
                )
            }
            .addOnFailureListener {
                // Fallback to username if fetch fails
                val message = if (confirmed)
                    "$riderName confirmed the $eType has been resolved. Thank you!"
                else
                    "$riderName still needs help with $eType."
                sendNotificationToUsername(
                    targetUsername = responder,
                    message        = message,
                    type           = if (confirmed) "accepted" else "alert"
                )
            }
    }

    fun act(confirmed: Boolean) {
        if (loading) return
        loading = true
        val alertId = notif.alertId

        fun finish(aId: String) {
            // Read the alert FIRST so we capture the responder's name before
            // any update can clear it — previously "Need Help" cleared
            // responderName to "" and only THEN tried to read it back to
            // notify them, so the responder was never actually told.
            db.collection("alerts").document(aId).get()
                .addOnSuccessListener { alertDoc ->
                    val currentStatus = alertDoc.getString("status") ?: "active"
                    if (currentStatus == "resolved" || currentStatus == "cancelled" || currentStatus == "expired") {
                        // Alert already reached a terminal state through another
                        // path (rider cancelled it, it auto-expired, etc.) since
                        // this notification was sent — don't resurrect it, just
                        // acknowledge the notification so the buttons go away.
                        db.collection("notifications").document(notif.id).update(
                            mapOf(
                                "type"    to if (confirmed) "ride_resolved" else "ride_still_active",
                                "message" to "This alert was already closed.",
                                "read"    to true
                            )
                        )
                        loading = false
                        return@addOnSuccessListener
                    }

                    val responder = alertDoc.getString("responderName") ?: ""
                    val riderName = alertDoc.getString("riderName") ?: ""
                    val eType     = alertDoc.getString("emergencyType") ?: "alert"

                    // "Need Help" only re-notifies the current responder that the
                    // rider still needs help — it does NOT unassign them or reopen
                    // the alert. Unassigning a responder is a deliberately
                    // higher-friction action elsewhere (Find Another Helper needs
                    // a 3-minute wait plus a confirmation dialog); a single
                    // notification tap shouldn't do the same thing silently.
                    val updates = if (confirmed) mapOf("status" to "resolved") else emptyMap()

                    fun afterUpdate() {
                        db.collection("notifications").document(notif.id).update(
                            mapOf(
                                "type"    to if (confirmed) "ride_resolved" else "ride_still_active",
                                "message" to if (confirmed)
                                    "You confirmed this ride alert as resolved."
                                else
                                    "You indicated you still need help with this alert.",
                                "read" to true
                            )
                        )
                        if (responder.isNotBlank()) {
                            sendResponderNotification(responder, riderName, eType, confirmed)
                        }
                        if (confirmed) {
                            // Mirror AlertsScreen's resolve flow — revoke live
                            // location access so a stale helper marker doesn't
                            // linger on the rider's map after resolution.
                            db.collection("live_locations").document(aId).delete()
                        }
                        loading = false
                    }

                    if (updates.isEmpty()) {
                        afterUpdate()
                    } else {
                        db.collection("alerts").document(aId)
                            .update(updates)
                            .addOnSuccessListener { afterUpdate() }
                            .addOnFailureListener { loading = false }
                    }
                }
                .addOnFailureListener { loading = false }
        }

        if (!alertId.isNullOrEmpty()) {
            // alertId already on the object — use it directly
            finish(alertId)
        } else {
            // alertId missing — look up the notification doc live to get it
            db.collection("notifications").document(notif.id).get()
                .addOnSuccessListener { doc ->
                    val aId = doc.getString("alertId")
                    if (!aId.isNullOrEmpty()) {
                        finish(aId)
                    } else {
                        loading = false
                    }
                }
                .addOnFailureListener { loading = false }
        }
    }

    val currentOnNavigateToAlerts by rememberUpdatedState(onNavigateToAlerts)
    val currentOnNavigateToRides  by rememberUpdatedState(onNavigateToRides)
    val currentOnNavigateToFeed   by rememberUpdatedState(onNavigateToFeed)
    val currentOnNavigateToChat   by rememberUpdatedState(onNavigateToChat)

    var hasNavigated by remember { mutableStateOf(false) }
    val isTappable = notif.type in listOf("ride", "accepted", "rejected", "alert", "like", "comment", "reply", "moderation", "moderation_restored", "message")
    // "accepted" and "rejected" refer to post moderation — navigate to feed, not ride events

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .animateContentSize(
                if (notif.type == "resolve_requested")
                    spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)
                else
                    spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
            )
            .then(
                if (isTappable) Modifier.clickable {
                    if (!hasNavigated) {
                        hasNavigated = true
                        when (notif.type) {
                            "ride"               -> currentOnNavigateToRides()
                            "alert"              -> currentOnNavigateToAlerts()
                            "accepted",
                            "rejected",
                            "like",
                            "comment",
                            "reply",
                            "moderation",
                            "moderation_restored" -> currentOnNavigateToFeed()
                            "message"            -> currentOnNavigateToChat(notif.conversationId ?: "")
                            else                 -> {}
                        }
                    }
                } else Modifier
            ),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = style.cardBg
        ),
        elevation = CardDefaults.cardElevation(if (!notif.read) 2.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icon badge
                Box(
                    modifier         = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(style.iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        style.icon,
                        contentDescription = null,
                        tint     = style.iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (notif.type == "message") {
                        Text(
                            text = notif.userName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = NGreen900
                        )
                    }
                    Text(
                        text       = notif.message,
                        fontWeight = if (!notif.read) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize   = 14.sp,
                        color      = NOnSurface
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text     = formatNotifTimestamp(notif.timestamp),
                        fontSize = 11.sp,
                        color    = Color.Gray
                    )
                }

                if (!notif.read) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(NGreen900)
                    )
                }
            }

            // ── Inline action buttons for resolve_requested ───────────────────
            if (isResolveRequest) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFFFE0B2), thickness = 0.8.dp)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Confirm resolved
                    Button(
                        onClick  = { act(confirmed = true) },
                        enabled  = !loading,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32),
                            contentColor   = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Resolved", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    // Still need help
                    OutlinedButton(
                        onClick  = { act(confirmed = false) },
                        enabled  = !loading,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape    = RoundedCornerShape(10.dp),
                        border   = BorderStroke(1.5.dp, Color(0xFFD32F2F)),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F)),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Need Help", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }

            // ── Inline action buttons for friend_request ───────────────────
            if (isFriendRequest) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = NGreen100, thickness = 0.8.dp)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val repo = remember { ChatRepository() }
                    val coroutineScope = rememberCoroutineScope()

                    // Accept
                    Button(
                        onClick  = {
                            if (!loading && notif.requestId != null && currentUserId.isNotEmpty()) {
                                loading = true
                                coroutineScope.launch {
                                    repo.respondToFriendRequest(notif.requestId, true, currentUserId, currentUserName)
                                    // Keep the notification as a record — mark it handled
                                    // rather than deleting; user can clear it themselves.
                                    db.collection("notifications").document(notif.id).update(
                                        mapOf(
                                            "type"    to "friend_accepted",
                                            "message" to "${notif.message} — Accepted",
                                            "read"    to true
                                        )
                                    )
                                    loading = false
                                }
                            }
                        },
                        enabled  = !loading,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = NGreen900,
                            contentColor   = Color.White
                        )
                    ) {
                        if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        else Text("Accept", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    // Reject
                    OutlinedButton(
                        onClick  = {
                            if (!loading && notif.requestId != null && currentUserId.isNotEmpty()) {
                                loading = true
                                coroutineScope.launch {
                                    repo.respondToFriendRequest(notif.requestId, false, currentUserId, currentUserName)
                                    db.collection("notifications").document(notif.id).update(
                                        mapOf(
                                            "type"    to "friend_declined",
                                            "message" to "${notif.message} — Declined",
                                            "read"    to true
                                        )
                                    )
                                    loading = false
                                }
                            }
                        },
                        enabled  = !loading,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape    = RoundedCornerShape(10.dp),
                        border   = BorderStroke(1.5.dp, Color(0xFFD32F2F)),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))
                    ) {
                        Text("Reject", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Timestamp helper — outside all composables at file level ─────────────────
private fun formatNotifTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return "Just now"
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000     -> "Just now"
        diff < 3_600_000  -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val postYear    = Calendar.getInstance().apply {
                timeInMillis = timestamp
            }.get(Calendar.YEAR)
            if (postYear == currentYear)
                SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
            else
                SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
        }
    }
}