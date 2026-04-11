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
    "ride"      -> NotifStyle(Icons.Default.DirectionsBike,   NGreen700,          NGreen100,          Color(0xFFF5FBF5), "Ride")
    "reply"     -> NotifStyle(Icons.AutoMirrored.Filled.Send, Color(0xFF7B1FA2),  Color(0xFFF3E5F5),  Color(0xFFFAF5FF), "Reply")
    "resolve_requested"-> NotifStyle(Icons.Default.HelpOutline,      Color(0xFFF57C00), Color(0xFFFFF3E0), Color(0xFFFFFBF0), "Resolve Request")
    "rating"           -> NotifStyle(Icons.Default.Star,              Color(0xFFF57C00), Color(0xFFFFF3E0), Color(0xFFFFFBF0), "Rating")
    "moderation"       -> NotifStyle(Icons.Default.Gavel,             Color(0xFFD32F2F), Color(0xFFFFEBEE), Color(0xFFFFF5F5), "Moderation")
    "moderation_restored" -> NotifStyle(Icons.Default.CheckCircle,    Color(0xFF388E3C), Color(0xFFE8F5E9), Color(0xFFF1FBF2), "Restored")
    "rating"           -> NotifStyle(Icons.Default.Star,             Color(0xFFF57C00), Color(0xFFFFF3E0), Color(0xFFFFFBF0), "Rating")
    else               -> NotifStyle(Icons.Default.Notifications,    Color(0xFF7A8F7A), Color(0xFFF0F0F0), Color.White,       "Notification")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(navController: NavController, userName: String) {
    val db            = FirebaseFirestore.getInstance()
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
            "Rides"  -> notifications.filter { it.type == "ride" }
            "Alerts" -> notifications.filter { it.type == "alert" || it.type == "resolve_requested" }
            else     -> notifications.toList()
        }
    }

    // Single real-time listener — handles both initial load and live updates
    // Pull-to-refresh is visual only; listener keeps data fresh automatically
    DisposableEffect(userName) {
        val registration = db.collection("notifications")
            .whereEqualTo("userName", userName)
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
                                postId    = doc.getString("postId")
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
                                                "userName"  to userName,
                                                "message"   to notif.message,
                                                "type"      to notif.type,
                                                "timestamp" to notif.timestamp,
                                                "read"      to notif.read,
                                                "alertId"   to notif.alertId,
                                                "postId"    to notif.postId
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
                                        "Rides"  -> notifications.count { it.type == "ride" }
                                        "Alerts" -> notifications.count { it.type == "alert" || it.type == "resolve_requested" }
                                        else     -> 0
                                    }
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
                                    append(if (activeFilter == "All") "notification" else activeFilter.lowercase())
                                    if (filteredNotifications.size != 1) append("s")
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
                                                        "userName"  to userName,
                                                        "message"   to notif.message,
                                                        "type"      to notif.type,
                                                        "timestamp" to notif.timestamp,
                                                        "read"      to notif.read,
                                                        "alertId"   to notif.alertId,
                                                        "postId"    to notif.postId
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
    onNavigateToAlerts: () -> Unit = {},
    onNavigateToRides:  () -> Unit = {},
    onNavigateToFeed:   () -> Unit = {}
) {
    val style   = notifStyle(notif.type)
    val db      = FirebaseFirestore.getInstance()
    var loading by remember { mutableStateOf(false) }

    // Show buttons if type matches — even if alertId is null (we'll fetch it live if needed)
    val isResolveRequest = notif.type == "resolve_requested"

    fun sendResponderNotification(alertId: String, confirmed: Boolean) {
        db.collection("alerts").document(alertId).get()
            .addOnSuccessListener { doc ->
                val responder = doc.getString("responderName") ?: return@addOnSuccessListener
                val riderName = doc.getString("riderName") ?: ""
                val eType     = doc.getString("emergencyType") ?: "alert"
                // Fetch display names for both rider and responder
                db.collection("users").whereEqualTo("username", riderName)
                    .limit(1).get()
                    .addOnSuccessListener { riderSnap ->
                        val riderDisplay = riderSnap.documents.firstOrNull()
                            ?.getString("displayName")?.takeIf { it.isNotBlank() } ?: riderName
                        db.collection("notifications").add(hashMapOf(
                            "userName"  to responder,
                            "message"   to if (confirmed)
                                "$riderDisplay confirmed the $eType has been resolved. Thank you!"
                            else
                                "$riderDisplay still needs help with $eType.",
                            "type"      to if (confirmed) "accepted" else "alert",
                            "timestamp" to System.currentTimeMillis(),
                            "read"      to false
                        ))
                    }
                    .addOnFailureListener {
                        // Fallback to username if fetch fails
                        db.collection("notifications").add(hashMapOf(
                            "userName"  to responder,
                            "message"   to if (confirmed)
                                "$riderName confirmed the $eType has been resolved. Thank you!"
                            else
                                "$riderName still needs help with $eType.",
                            "type"      to if (confirmed) "accepted" else "alert",
                            "timestamp" to System.currentTimeMillis(),
                            "read"      to false
                        ))
                    }
            }
    }

    fun act(confirmed: Boolean) {
        if (loading) return
        loading = true
        val alertId = notif.alertId

        fun finish(aId: String) {
            val newStatus = if (confirmed) "resolved" else "active"
            val updates = if (confirmed) {
                mapOf("status" to newStatus)
            } else {
                mapOf(
                    "status"               to newStatus,
                    "responderName"        to "",
                    "responderDisplayName" to ""
                )
            }
            db.collection("alerts").document(aId)
                .update(updates)
                .addOnSuccessListener {
                    db.collection("notifications").document(notif.id).delete()
                    sendResponderNotification(aId, confirmed)
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

    var hasNavigated by remember { mutableStateOf(false) }
    val isTappable = notif.type in listOf("ride", "accepted", "rejected", "alert", "like", "comment", "reply", "moderation", "moderation_restored")
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