    package com.darkhorses.PedalConnect.ui.theme
    
    import android.widget.Toast
    import androidx.compose.animation.core.animateFloat
    import androidx.compose.animation.core.rememberInfiniteTransition
    import androidx.compose.foundation.background
    import androidx.compose.foundation.border
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.horizontalScroll
    import androidx.compose.foundation.interaction.MutableInteractionSource
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.lazy.items
    import androidx.compose.foundation.rememberScrollState
    import androidx.compose.foundation.shape.CircleShape
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.automirrored.filled.Send
    import androidx.compose.material.icons.automirrored.filled.Sort
    import androidx.compose.material.icons.filled.*
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.runtime.saveable.rememberSaveable
    import androidx.compose.animation.core.RepeatMode
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.draw.shadow
    import androidx.compose.ui.graphics.Brush
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.graphics.vector.ImageVector
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.platform.LocalFocusManager
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.style.TextAlign
    import androidx.compose.ui.text.style.TextOverflow
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import androidx.compose.ui.window.Dialog
    import androidx.navigation.NavController
    import coil.compose.AsyncImage
    import com.google.firebase.firestore.FieldValue
    import com.google.firebase.firestore.FirebaseFirestore
    import com.google.firebase.firestore.ListenerRegistration
    import com.google.firebase.firestore.Query
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.awaitCancellation
    import kotlinx.coroutines.delay
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.withContext
    import org.json.JSONObject
    import java.net.URL
    import java.text.SimpleDateFormat
    import java.util.*
    import androidx.compose.material3.ExperimentalMaterial3Api
    import androidx.compose.material3.pulltorefresh.PullToRefreshBox
    import androidx.compose.ui.layout.ContentScale
    import androidx.compose.ui.text.withStyle
    import androidx.compose.foundation.ExperimentalFoundationApi
    import androidx.compose.foundation.combinedClickable

    // ─────────────────────────────────────────────────────────────────────────────
    // Design Tokens — shared with RidingEventsScreen
    // ─────────────────────────────────────────────────────────────────────────────
    private val Green950    = Color(0xFF052818)
    private val Green900    = Color(0xFF06402B)
    private val Green800    = Color(0xFF0A5C3D)
    private val Green700    = Color(0xFF0D7050)
    private val Green500    = Color(0xFF1A9E6E)
    private val Green100    = Color(0xFFDDF1E8)
    private val Green50     = Color(0xFFF0FAF5)
    
    private val Amber500    = Color(0xFFF59E0B)
    private val Amber50     = Color(0xFFFFFBEB)
    private val Red600      = Color(0xFFDC2626)
    private val Red50       = Color(0xFFFEF2F2)
    private val Orange600   = Color(0xFFEA580C)
    private val Orange50    = Color(0xFFFFF7ED)
    
    private val BgCanvas    = Color(0xFFF5F7F6)
    private val BgSurface   = Color(0xFFFFFFFF)
    private val TextPrimary   = Color(0xFF111827)
    private val TextSecondary = Color(0xFF374151)
    private val TextMuted     = Color(0xFF6B7280)
    private val DividerColor  = Color(0xFFE5E7EB)
    private val BorderDefault = Color(0xFFD1D5DB)
    
    // ─────────────────────────────────────────────────────────────────────────────
    // Weather helpers
    // ─────────────────────────────────────────────────────────────────────────────
    data class WeatherState(
        val tempC: Int          = 0,
        val description: String = "Loading…",
        val emoji: String       = "🌤",
        val rideAdvice: String  = "Checking conditions…",
        val isLoading: Boolean  = true
    )
    
    private fun weatherFromCode(code: Int, isDay: Boolean): Pair<String, String> = when (code) {
        0         -> if (isDay) Pair("☀️", "Clear Sky")    else Pair("🌙", "Clear Night")
        1         -> if (isDay) Pair("🌤", "Mostly Clear") else Pair("🌙", "Mostly Clear")
        2         -> Pair("⛅", "Partly Cloudy")
        3         -> Pair("☁️", "Overcast")
        in 45..48 -> Pair("🌫", "Foggy")
        in 51..55 -> Pair("🌦", "Drizzle")
        in 61..65 -> Pair("🌧", "Rain")
        in 71..75 -> Pair("❄️", "Snow")
        in 80..82 -> Pair("🌧", "Rain Showers")
        in 95..99 -> Pair("⛈", "Thunderstorm")
        else      -> Pair("🌡", "Unknown")
    }
    
    private fun rideAdvice(code: Int, tempC: Int): String = when {
        code in 95..99  -> "⚠️ Thunderstorm — avoid riding today"
        code in 61..82  -> "🌧 Rainy — ride with caution"
        code in 45..48  -> "🌫 Foggy — use lights and be visible"
        tempC >= 36     -> "🥵 Very hot — ride early morning"
        tempC in 30..35 -> "🌡 Hot — stay hydrated!"
        tempC in 22..29 -> "✅ Great conditions for a ride!"
        else            -> "🌤 Good to ride — dress appropriately"
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // Data models (unchanged)
    // ─────────────────────────────────────────────────────────────────────────────
    typealias PostItem = Post

    data class NotificationItem(
        val id: String       = "",
        val message: String  = "",
        val type: String     = "",
        val timestamp: Long  = 0,
        val read: Boolean    = false,
        val alertId: String? = null,
        val postId: String?  = null
    )

    data class CommentItem(
        val id: String               = "",
        val userName: String         = "",
        val text: String             = "",
        val timestamp: Long          = 0,
        val likes: Int               = 0,
        val likedBy: List<String>    = emptyList(),
        val replyTo: String?         = null,
        val replyToUserName: String? = null,
        val editedAt: Long?          = null
    )
    // ─────────────────────────────────────────────────────────────────────────────
    // Avatar helper
    // ─────────────────────────────────────────────────────────────────────────────
    fun userInitials(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}".uppercase()
            parts.size == 1 -> parts.first().take(2).uppercase()
            else            -> "?"
        }
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // Homepage
    // ─────────────────────────────────────────────────────────────────────────────
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    @Composable
    fun Homepage(
        navController: NavController,
        paddingValues: PaddingValues,
        userName: String,
        userLat: Double = 14.5995,
        userLon: Double = 120.9842,
        onExploreRides: (eventId: String?) -> Unit = {},
        isAdmin: Boolean = false,
        notificationsEnabled: Boolean = true
    ) {
        val db           = FirebaseFirestore.getInstance()
        val context      = LocalContext.current
        val scope        = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current
        val posts        = remember { mutableStateListOf<PostItem>() }
    
        var sortMode by rememberSaveable { mutableIntStateOf(0) }
        val sortedPosts by remember {
            derivedStateOf {
                when (sortMode) {
                    1    -> posts.sortedBy { it.timestamp }
                    2    -> posts.sortedByDescending { it.likes }
                    else -> posts.sortedByDescending { it.timestamp }
                }
            }
        }
    
        var showCommentsSheet by remember { mutableStateOf(false) }
        var selectedPostId    by remember { mutableStateOf("") }
        val notifications     = remember { mutableStateListOf<NotificationItem>() }
        var unreadCount       by remember { mutableIntStateOf(0) }
        var commentText       by remember { mutableStateOf("") }
        val postComments      = remember { mutableStateListOf<CommentItem>() }
        val sheetState        = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        var replyingTo         by remember { mutableStateOf<CommentItem?>(null) }
        var replyParentId      by remember { mutableStateOf<String?>(null) }
        var showEditDialog  by remember { mutableStateOf(false) }
        var editingPost     by remember { mutableStateOf<PostItem?>(null) }
        var editDescription by remember { mutableStateOf("") }
        var isSavingEdit    by remember { mutableStateOf(false) }
    
        var showDeleteDialog by remember { mutableStateOf(false) }
        var deletingPost     by remember { mutableStateOf<PostItem?>(null) }
        var isDeletingPost   by remember { mutableStateOf(false) }
    
        var isRefreshing   by remember { mutableStateOf(false) }
        var isLoadingFeed  by remember { mutableStateOf(true) }
        var errorMessage   by remember { mutableStateOf<String?>(null) }
        var weather        by remember { mutableStateOf(WeatherState()) }

        var nextEvent      by remember { mutableStateOf<RideEvent?>(null) }
        var isLoadingEvent by remember { mutableStateOf(true) }
        var userPhotoUrl   by remember { mutableStateOf<String?>(null) }
        var userDisplayName  by remember { mutableStateOf("") }
        val userPhotoCache       = remember { mutableStateMapOf<String, String>() }
        val userDisplayNameCache = remember { mutableStateMapOf<String, String>() }

        LaunchedEffect(errorMessage) {
            errorMessage?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); errorMessage = null }
        }

        LaunchedEffect(userName) {
            db.collection("users").whereEqualTo("username", userName)
                .limit(1).addSnapshotListener { snap, _ ->
                    val doc = snap?.documents?.firstOrNull()
                    val url = doc?.getString("photoUrl")
                    userPhotoUrl = url
                    if (url != null) userPhotoCache[userName] = url
                    userDisplayName = doc?.getString("displayName") ?: ""
                    if (userDisplayName.isNotBlank()) userDisplayNameCache[userName] = userDisplayName
                }
        }

        // Pre-fetch photos and display names for all visible post authors
        LaunchedEffect(posts.size) {
            val uniqueNames = posts.map { it.userName }.distinct()
                .filter { it !in userPhotoCache }
            for (name in uniqueNames) {
                db.collection("users").whereEqualTo("username", name)
                    .limit(1).get()
                    .addOnSuccessListener { snap ->
                        val doc = snap.documents.firstOrNull()
                        val url = doc?.getString("photoUrl")
                        if (url != null) userPhotoCache[name] = url
                        val dn = doc?.getString("displayName")?.takeIf { it.isNotBlank() }
                        if (dn != null) userDisplayNameCache[name] = dn
                    }
            }
        }
    
        // Weather polling
        LaunchedEffect(userLat, userLon) {
            while (true) {
                try {
                    val json = withContext(Dispatchers.IO) {
                        JSONObject(URL(
                            "https://api.open-meteo.com/v1/forecast" +
                                    "?latitude=$userLat&longitude=$userLon" +
                                    "&current=temperature_2m,weathercode,is_day&timezone=auto"
                        ).readText())
                    }
                    val cur           = json.getJSONObject("current")
                    val tempC         = cur.getDouble("temperature_2m").toInt()
                    val code          = cur.getInt("weathercode")
                    val isDay         = cur.getInt("is_day") == 1
                    val (emoji, desc) = weatherFromCode(code, isDay)
                    weather = WeatherState(tempC, desc, emoji, rideAdvice(code, tempC), false)
                } catch (e: Exception) {
                    weather = WeatherState(0, "Unavailable", "🌡", "Weather data unavailable", false)
                }
                delay(10 * 60 * 1000L)
            }
        }
    
        // Single source of truth for posts — merges accepted + user's pending
        fun mergePosts(accepted: List<PostItem>, pending: List<PostItem>): List<PostItem> {
            return (accepted + pending)
                .distinctBy { it.id }
                .sortedByDescending { it.timestamp }
        }
    
        fun refreshData() {
            scope.launch {
                isRefreshing = true
                delay(600)
                isRefreshing = false
            }
        }
    
        // Listeners
        LaunchedEffect(userName) {
            val reg = db.collection("notifications")
                .whereEqualTo("userName", userName).whereEqualTo("read", false)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) { errorMessage = "Failed to load notifications."; return@addSnapshotListener }
                    notifications.clear()
                    snapshot?.documents?.forEach { doc ->
                        notifications.add(NotificationItem(
                            id = doc.id, message = doc.getString("message") ?: "",
                            type = doc.getString("type") ?: "", timestamp = doc.getLong("timestamp") ?: 0,
                            read = doc.getBoolean("read") ?: false, alertId = doc.getString("alertId")
                        ))
                    }
                    unreadCount = if (notificationsEnabled) notifications.size else 0
                    // Badge suppressed if user disabled in-app notifications
                }
            try { awaitCancellation() } finally { reg.remove() }
        }
    
        // Accepted posts — community feed
        val acceptedPostsCache = remember { mutableStateListOf<PostItem>() }
        val pendingPostsCache  = remember { mutableStateListOf<PostItem>() }
    
        // Sync merged list whenever either cache changes
        fun syncPosts() {
            val merged = mergePosts(acceptedPostsCache.toList(), pendingPostsCache.toList())
            posts.clear()
            posts.addAll(merged)
        }
    
        LaunchedEffect(Unit) {
            val reg = db.collection("posts")
                .whereEqualTo("status", "accepted")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        isLoadingFeed = false
                        if (e.message?.contains("index", ignoreCase = true) == true) {
                            // Missing Firestore index — fall back to unordered query
                            db.collection("posts").whereEqualTo("status", "accepted").get()
                                .addOnSuccessListener { snap ->
                                    val accepted = snap.documents
                                        .mapNotNull { doc ->
                                            val poly = doc.safePolyline()
                                            Post(
                                                id            = doc.id,
                                                userName      = doc.getString("userName")      ?: "",
                                                displayName   = doc.getString("displayName")   ?: "",
                                                description   = doc.getString("description")   ?: "",
                                                activity      = doc.getString("activity")      ?: "Cycling Ride",
                                                distance      = doc.getString("distance")      ?: "0",
                                                timestamp     = doc.getLong("timestamp")       ?: 0L,
                                                likes         = (doc.getLong("likes") ?: 0L).toInt(),
                                                comments      = (doc.getLong("comments") ?: 0L).toInt(),
                                                likedBy       = (doc.get("likedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                                                status        = doc.getString("status")        ?: "",
                                                imageUrl      = doc.getString("imageUrl")      ?: "",
                                                imageDeleteUrl = doc.getString("imageDeleteUrl") ?: "",
                                                polyline      = poly,
                                                routeImageUrl = doc.getString("routeImageUrl") ?: "",
                                                editedAt      = doc.getLong("editedAt")        ?: 0L,
                                                rideStats     = null
                                            )
                                        }
                                        .sortedByDescending { it.timestamp }
                                    acceptedPostsCache.clear()
                                    acceptedPostsCache.addAll(accepted)
                                    syncPosts()
                                }
                        } else if (acceptedPostsCache.isEmpty()) {
                            errorMessage = "Could not load posts. Pull down to retry."
                        }
                        return@addSnapshotListener
                    }
                    val accepted = snapshot?.documents
                        ?.mapNotNull { doc ->
                            val poly = doc.safePolyline()
                            Post(
                                id            = doc.id,
                                userName      = doc.getString("userName")      ?: "",
                                displayName   = doc.getString("displayName")   ?: "",
                                description   = doc.getString("description")   ?: "",
                                activity      = doc.getString("activity")      ?: "Cycling Ride",
                                distance      = doc.getString("distance")      ?: "0",
                                timestamp     = doc.getLong("timestamp")       ?: 0L,
                                likes         = (doc.getLong("likes") ?: 0L).toInt(),
                                comments      = (doc.getLong("comments") ?: 0L).toInt(),
                                likedBy       = (doc.get("likedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                                status        = doc.getString("status")        ?: "",
                                imageUrl      = doc.getString("imageUrl")      ?: "",
                                imageDeleteUrl = doc.getString("imageDeleteUrl") ?: "",
                                polyline      = poly,
                                routeImageUrl = doc.getString("routeImageUrl") ?: "",
                                editedAt      = doc.getLong("editedAt")        ?: 0L,
                                rideStats     = null
                            )
                        }
                        ?: emptyList()
                    acceptedPostsCache.clear()
                    acceptedPostsCache.addAll(accepted)
                    syncPosts()
                    isLoadingFeed = false
                }
            try { awaitCancellation() } finally { reg.remove() }
        }
    
        LaunchedEffect(userName) {
            val reg = db.collection("posts")
                .whereEqualTo("userName", userName)
                .whereEqualTo("status", "pending")
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    val pending = snapshot?.documents
                        ?.mapNotNull { doc ->
                            val poly = doc.safePolyline()
                            Post(
                                id            = doc.id,
                                userName      = doc.getString("userName")      ?: "",
                                displayName   = doc.getString("displayName")   ?: "",
                                description   = doc.getString("description")   ?: "",
                                activity      = doc.getString("activity")      ?: "Cycling Ride",
                                distance      = doc.getString("distance")      ?: "0",
                                timestamp     = doc.getLong("timestamp")       ?: 0L,
                                likes         = (doc.getLong("likes") ?: 0L).toInt(),
                                comments      = (doc.getLong("comments") ?: 0L).toInt(),
                                likedBy       = (doc.get("likedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                                status        = doc.getString("status")        ?: "pending",
                                imageUrl      = doc.getString("imageUrl")      ?: "",
                                imageDeleteUrl = doc.getString("imageDeleteUrl") ?: "",
                                polyline      = poly,
                                routeImageUrl = doc.getString("routeImageUrl") ?: "",
                                editedAt      = doc.getLong("editedAt")        ?: 0L,
                                rideStats     = null
                            )
                        }
                        ?: emptyList()
                    pendingPostsCache.clear()
                    pendingPostsCache.addAll(pending)
                    syncPosts()
                }
            try { awaitCancellation() } finally { reg.remove() }
        }

        LaunchedEffect(Unit) {
            val reg = db.collection("rideEvents")
                .whereEqualTo("status", "approved")
                .orderBy("date", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        if (e.message?.contains("index", ignoreCase = true) == true ||
                            e.message?.contains("FAILED_PRECONDITION", ignoreCase = true) == true) {
                            db.collection("rideEvents")
                                .whereEqualTo("status", "approved")
                                .get()
                                .addOnSuccessListener { snap ->
                                    val startOfToday = Calendar.getInstance().apply {
                                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                    }.timeInMillis
                                    val doc = snap.documents
                                        .filter { (it.getLong("date") ?: 0L) >= startOfToday }
                                        .minByOrNull { it.getLong("date") ?: Long.MAX_VALUE }
                                    nextEvent = if (doc != null) try {
                                        RideEvent(
                                            id = doc.id, title = doc.getString("title") ?: "",
                                            route = doc.getString("route") ?: "", date = doc.getLong("date") ?: 0L,
                                            time = doc.getString("time") ?: "", organizer = doc.getString("organizer") ?: "",
                                            participants = (doc.get("participants") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                                            maxParticipants = (doc.getLong("maxParticipants") ?: 0L).toInt(),
                                            difficulty = doc.getString("difficulty") ?: "Easy",
                                            distanceKm = doc.getDouble("distanceKm") ?: 0.0
                                        )
                                    } catch (ex: Exception) { null } else null
                                    isLoadingEvent = false
                                }
                                .addOnFailureListener { isLoadingEvent = false }
                        } else {
                            isLoadingEvent = false
                        }
                        return@addSnapshotListener
                    }
                    val startOfToday = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val nowMs = System.currentTimeMillis()
                    val doc = snapshot?.documents
                        ?.filter { doc ->
                            val date = doc.getLong("date") ?: 0L
                            if (date < startOfToday) return@filter false
                            // Also exclude events that have ended based on time
                            val timeStr = doc.getString("time") ?: ""
                            val startMs = if (timeStr.isNotBlank()) {
                                try {
                                    val parsed = SimpleDateFormat("h:mm a", Locale.getDefault()).parse(timeStr)
                                    val timeCal = Calendar.getInstance().apply { timeInMillis = parsed?.time ?: 0L }
                                    Calendar.getInstance().apply {
                                        timeInMillis = date
                                        set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                                        set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                    }.timeInMillis
                                } catch (e: Exception) { date }
                            } else date
                            val endMs = startMs + 4 * 60 * 60 * 1000L
                            nowMs <= endMs  // keep if not yet ended
                        }
                        ?.minByOrNull { it.getLong("date") ?: Long.MAX_VALUE }
                    nextEvent = if (doc != null) {
                        try {
                            RideEvent(
                                id = doc.id, title = doc.getString("title") ?: "",
                                route = doc.getString("route") ?: "", date = doc.getLong("date") ?: 0L,
                                time = doc.getString("time") ?: "", organizer = doc.getString("organizer") ?: "",
                                participants = (doc.get("participants") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                                maxParticipants = (doc.getLong("maxParticipants") ?: 0L).toInt(),
                                difficulty = doc.getString("difficulty") ?: "Easy",
                                distanceKm = doc.getDouble("distanceKm") ?: 0.0
                            )
                        } catch (ex: Exception) { null }
                    } else null
                    isLoadingEvent = false
                }
            try { awaitCancellation() } finally { reg.remove() }
        }

        var commentsReg by remember { mutableStateOf<ListenerRegistration?>(null) }
        LaunchedEffect(selectedPostId, showCommentsSheet) {
            if (showCommentsSheet && selectedPostId.isNotEmpty()) {
                commentsReg?.remove()
                commentsReg = db.collection("posts").document(selectedPostId)
                    .collection("comments").orderBy("timestamp", Query.Direction.ASCENDING)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) return@addSnapshotListener
                        postComments.clear()
                        snapshot?.documents?.forEach { doc ->
                            val status = doc.getString("status") ?: "visible"
                            if (status != "hidden") {
                                doc.toObject(CommentItem::class.java)?.copy(
                                    id              = doc.id,
                                    likes           = (doc.getLong("likes") ?: 0L).toInt(),
                                    likedBy         = (doc.get("likedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                                    replyTo         = doc.getString("replyTo"),
                                    replyToUserName = doc.getString("replyToUserName"),
                                    editedAt        = doc.getLong("editedAt")
                                )?.let { postComments.add(it) }
                            }
                        }
                        // Pre-fetch display names for comment authors not yet cached
                        val uniqueCommenters = postComments.map { it.userName }.distinct()
                            .filter { it !in userDisplayNameCache }
                        for (name in uniqueCommenters) {
                            db.collection("users").whereEqualTo("username", name)
                                .limit(1).get()
                                .addOnSuccessListener { snap ->
                                    val doc = snap.documents.firstOrNull()
                                    val url = doc?.getString("photoUrl")
                                    if (url != null) userPhotoCache[name] = url
                                    val dn = doc?.getString("displayName")?.takeIf { it.isNotBlank() }
                                    if (dn != null) userDisplayNameCache[name] = dn
                                }
                        }
                    }
            } else {
                commentsReg?.remove(); commentsReg = null
                postComments.clear(); commentText = ""; replyingTo = null; replyParentId = null
            }
        }
    
        // Actions
        fun saveEdit() {
            val post = editingPost ?: return
            if (editDescription.isBlank()) return
            val originalDescription = editDescription
            val (wasCensored, cleanedDescription) = ProfanityFilter.censorText(editDescription)
            if (wasCensored) {
                editDescription = cleanedDescription
                Toast.makeText(context, "⚠️ Some words were replaced to keep the feed respectful.", Toast.LENGTH_SHORT).show()
                db.collection("moderationLogs").add(hashMapOf(
                    "userName"     to userName,
                    "originalText" to originalDescription,
                    "censoredText" to cleanedDescription,
                    "context"      to "post_edit",
                    "timestamp"    to System.currentTimeMillis()
                ))
            }
            isSavingEdit = true
            db.collection("posts").document(post.id).update(
                "description", editDescription.trim(),
                "editedAt", System.currentTimeMillis()
            )
                .addOnSuccessListener {
                    isSavingEdit = false; showEditDialog = false; editingPost = null
                    Toast.makeText(context, "Post updated!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    isSavingEdit = false
                    Toast.makeText(context, "Failed to update.", Toast.LENGTH_SHORT).show()
                }
        }
    
        fun deletePost() {
            val post = deletingPost ?: return
            isDeletingPost = true
            db.collection("posts").document(post.id).delete()
                .addOnSuccessListener {
                    isDeletingPost = false; showDeleteDialog = false; deletingPost = null
                    posts.removeAll { it.id == post.id }
                    Toast.makeText(context, "Post deleted.", Toast.LENGTH_SHORT).show()
                    // Clean up ImgBB image if one exists
                    if (post.imageDeleteUrl.isNotBlank()) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val conn = java.net.URL(post.imageDeleteUrl)
                                    .openConnection() as java.net.HttpURLConnection
                                conn.requestMethod = "GET"
                                conn.connectTimeout = 10_000
                                conn.responseCode // fire and forget
                                conn.disconnect()
                            } catch (e: Exception) { /* ignore — image cleanup is best-effort */ }
                        }
                    }
                }
                .addOnFailureListener {
                    isDeletingPost = false
                    Toast.makeText(context, "Failed to delete.", Toast.LENGTH_SHORT).show()
                }
        }

        fun toggleLike(post: PostItem) {
            val ref = db.collection("posts").document(post.id)
            if (post.likedBy.contains(userName)) {
                ref.update("likedBy", FieldValue.arrayRemove(userName), "likes", FieldValue.increment(-1))
            } else {
                ref.update("likedBy", FieldValue.arrayUnion(userName), "likes", FieldValue.increment(1))
                // Notify post owner — skip if liker is the owner
                if (post.userName != userName) {
                    // Fetch liker's display name first
                    db.collection("users").whereEqualTo("username", userName)
                        .limit(1).get()
                        .addOnSuccessListener { snap ->
                            val displayName = snap.documents.firstOrNull()
                                ?.getString("displayName")?.takeIf { it.isNotBlank() } ?: userName
                            db.collection("notifications").add(hashMapOf(
                                "userName"  to post.userName,
                                "message"   to "$displayName liked your post.",
                                "type"      to "like",
                                "timestamp" to System.currentTimeMillis(),
                                "read"      to false,
                                "postId"    to post.id
                            ))
                        }
                }
            }
        }

        fun submitComment() {
            if (commentText.isBlank()) return
            val originalComment = commentText
            val (wasCensored, cleanedComment) = ProfanityFilter.censorText(commentText)
            if (wasCensored) {
                commentText = cleanedComment
                Toast.makeText(context, "⚠️ Some words were replaced to keep the feed respectful.", Toast.LENGTH_SHORT).show()
                // Log to Firestore for admin visibility
                db.collection("moderationLogs").add(hashMapOf(
                    "userName"     to userName,
                    "originalText" to originalComment,
                    "censoredText" to cleanedComment,
                    "context"      to "comment",
                    "timestamp"    to System.currentTimeMillis()
                ))
            }
            val replyTarget = replyingTo
            val parentId    = replyParentId
            val payload = hashMapOf<String, Any>(
                "userName"  to userName,
                "text"      to commentText.trim(),
                "timestamp" to System.currentTimeMillis(),
                "likes"     to 0,
                "likedBy"   to emptyList<String>()
            )
            replyTarget?.let {
                // Always attach to the top-level parent so replies stay flat
                payload["replyTo"]         = parentId ?: it.id
                payload["replyToUserName"] = it.userName
            }
            db.collection("posts").document(selectedPostId).collection("comments")
                .add(payload)
                .addOnSuccessListener {
                    db.collection("posts").document(selectedPostId).update("comments", FieldValue.increment(1))

                    // Fetch commenter's display name once, then send notifications
                    db.collection("users").whereEqualTo("username", userName)
                        .limit(1).get()
                        .addOnSuccessListener { snap ->
                            val displayName = snap.documents.firstOrNull()
                                ?.getString("displayName")?.takeIf { it.isNotBlank() } ?: userName

                            // Notify post owner if commenter is not the owner
                            db.collection("posts").document(selectedPostId).get()
                                .addOnSuccessListener { postDoc ->
                                    val postOwner = postDoc.getString("userName") ?: ""
                                    if (postOwner.isNotBlank() && postOwner != userName) {
                                        db.collection("notifications").add(hashMapOf(
                                            "userName"  to postOwner,
                                            "message"   to "$displayName commented on your post.",
                                            "type"      to "comment",
                                            "timestamp" to System.currentTimeMillis(),
                                            "read"      to false,
                                            "postId"    to selectedPostId
                                        ))
                                    }

                                    // Notify the person being replied to — if different from post owner and commenter
                                    if (replyTarget != null
                                        && replyTarget.userName != userName
                                        && replyTarget.userName != postOwner) {
                                        val preview = replyTarget.text.take(50).let {
                                            if (replyTarget.text.length > 50) "$it…" else it
                                        }
                                        db.collection("notifications").add(hashMapOf(
                                            "userName"  to replyTarget.userName,
                                            "message"   to "$displayName replied to your comment: \"$preview\"",
                                            "type"      to "reply",
                                            "timestamp" to System.currentTimeMillis(),
                                            "read"      to false,
                                            "postId"    to selectedPostId
                                        ))
                                    }
                                }
                        }

                    commentText = ""
                    replyingTo = null
                    replyParentId = null
                    focusManager.clearFocus()
                    Toast.makeText(context, "Comment added!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { Toast.makeText(context, "Failed to post comment.", Toast.LENGTH_SHORT).show() }
        }

        fun toggleCommentLike(comment: CommentItem) {
            val ref = db.collection("posts").document(selectedPostId)
                .collection("comments").document(comment.id)
            if (comment.likedBy.contains(userName)) {
                ref.update("likedBy", FieldValue.arrayRemove(userName), "likes", FieldValue.increment(-1))
            } else {
                ref.update("likedBy", FieldValue.arrayUnion(userName), "likes", FieldValue.increment(1))
            }
        }
    
        // ── Edit dialog ───────────────────────────────────────────────────────────
        if (showEditDialog) {
            Dialog(onDismissRequest = { if (!isSavingEdit) { showEditDialog = false; editingPost = null } }) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = BgSurface),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Header
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Green50), Alignment.Center) {
                                Icon(Icons.Default.Edit, null, tint = Green900, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("Edit Post", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                                Text("Update your description", fontSize = 12.sp, color = TextMuted)
                            }
                        }
                        HorizontalDivider(color = DividerColor)
                        OutlinedTextField(
                            value = editDescription,
                            onValueChange = { if (it.length <= 300) editDescription = it },
                            label = { Text("Description", fontSize = 13.sp) },
                            placeholder = { Text("What was your ride like?", color = Color(0xFFD1D5DB), fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                            shape = RoundedCornerShape(12.dp), maxLines = 6,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Green700, unfocusedBorderColor = BorderDefault,
                                focusedLabelColor = Green700, cursorColor = Green700,
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                            supportingText = {
                                Text("${editDescription.length}/300",
                                    color = if (editDescription.length > 280) Red600 else TextMuted,
                                    fontSize = 11.sp, modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End)
                            }
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { showEditDialog = false; editingPost = null },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp), enabled = !isSavingEdit,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDefault),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                            ) { Text("Cancel", fontWeight = FontWeight.Medium) }
                            Button(
                                onClick = { saveEdit() },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isSavingEdit && editDescription.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = Green900, contentColor = Color.White)
                            ) {
                                if (isSavingEdit) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text("Save", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    
        // ── Delete dialog ─────────────────────────────────────────────────────────
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { if (!isDeletingPost) { showDeleteDialog = false; deletingPost = null } },
                shape = RoundedCornerShape(24.dp), containerColor = BgSurface,
                icon = {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(Red50), Alignment.Center) {
                        Icon(Icons.Default.DeleteForever, null, tint = Red600, modifier = Modifier.size(28.dp))
                    }
                },
                title = { Text("Delete this post?", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary, textAlign = TextAlign.Center) },
                text  = {
                    Text("This will permanently remove your post from the community feed.",
                        fontSize = 14.sp, color = TextSecondary, lineHeight = 22.sp, textAlign = TextAlign.Center)
                },
                confirmButton = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { deletePost() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp), enabled = !isDeletingPost,
                            colors = ButtonDefaults.buttonColors(containerColor = Red600, contentColor = Color.White)
                        ) {
                            if (isDeletingPost) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("Delete post", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                        OutlinedButton(
                            onClick = { showDeleteDialog = false; deletingPost = null },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp), enabled = !isDeletingPost,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDefault),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                        ) { Text("Cancel", fontWeight = FontWeight.Medium, fontSize = 15.sp) }
                    }
                }
            )
        }

        // ── Comments sheet ────────────────────────────────────────────────────────
        if (showCommentsSheet) {

            var commentSortNewest by remember { mutableStateOf(true) }
            var expandedReplies   by remember { mutableStateOf<Set<String>>(emptySet()) }
            var editingComment      by remember { mutableStateOf<CommentItem?>(null) }
            var editCommentText     by remember { mutableStateOf("") }
            var deletingComment     by remember { mutableStateOf<CommentItem?>(null) }
            var contextMenuComment  by remember { mutableStateOf<CommentItem?>(null) }
            val contextSheetState   = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            val sortedTopComments by remember(commentSortNewest) {
                derivedStateOf {
                    postComments
                        .filter { it.replyTo == null }
                        .let { if (commentSortNewest) it.sortedByDescending { c -> c.timestamp } else it.sortedBy { c -> c.timestamp } }
                }
            }

            // Edit comment dialog
            if (editingComment != null) {
                Dialog(onDismissRequest = { editingComment = null }) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = BgSurface),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Green50), Alignment.Center) {
                                    Icon(Icons.Default.Edit, null, tint = Green900, modifier = Modifier.size(20.dp))
                                }
                                Text("Edit Comment", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                            }
                            HorizontalDivider(color = DividerColor)
                            OutlinedTextField(
                                value = editCommentText,
                                onValueChange = { if (it.length <= 300) editCommentText = it },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                                shape = RoundedCornerShape(12.dp),
                                maxLines = 5,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = Green700,
                                    unfocusedBorderColor = BorderDefault,
                                    cursorColor          = Green700,
                                    focusedTextColor     = TextPrimary,
                                    unfocusedTextColor   = TextPrimary
                                )
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { editingComment = null },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDefault),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                                ) { Text("Cancel", fontWeight = FontWeight.Medium) }
                                Button(
                                    onClick = {
                                        val c = editingComment ?: return@Button
                                        if (editCommentText.isBlank()) return@Button
                                        val originalEditComment = editCommentText
                                        val (wasCensored, cleanedComment) = ProfanityFilter.censorText(editCommentText)
                                        if (wasCensored) {
                                            editCommentText = cleanedComment
                                            Toast.makeText(context, "⚠️ Some words were replaced to keep the feed respectful.", Toast.LENGTH_SHORT).show()
                                            db.collection("moderationLogs").add(hashMapOf(
                                                "userName"     to userName,
                                                "originalText" to originalEditComment,
                                                "censoredText" to cleanedComment,
                                                "context"      to "comment_edit",
                                                "timestamp"    to System.currentTimeMillis()
                                            ))
                                        }
                                        db.collection("posts").document(selectedPostId)
                                            .collection("comments").document(c.id)
                                            .update(
                                                "text", editCommentText.trim(),
                                                "editedAt", System.currentTimeMillis()
                                            )
                                            .addOnSuccessListener { editingComment = null }
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = editCommentText.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Green900, contentColor = Color.White)
                                ) { Text("Save", fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    }
                }
            }

            // Delete comment dialog
            if (deletingComment != null) {
                AlertDialog(
                    onDismissRequest = { deletingComment = null },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = BgSurface,
                    icon = {
                        Box(Modifier.size(56.dp).clip(CircleShape).background(Red50), Alignment.Center) {
                            Icon(Icons.Default.DeleteForever, null, tint = Red600, modifier = Modifier.size(28.dp))
                        }
                    },
                    title = { Text("Delete comment?", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary, textAlign = TextAlign.Center) },
                    text  = { Text("This will permanently remove your comment.", fontSize = 14.sp, color = TextSecondary, textAlign = TextAlign.Center) },
                    confirmButton = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val c = deletingComment ?: return@Button
                                    db.collection("posts").document(selectedPostId)
                                        .collection("comments").document(c.id).delete()
                                        .addOnSuccessListener {
                                            db.collection("posts").document(selectedPostId)
                                                .update("comments", FieldValue.increment(-1))
                                            deletingComment = null
                                        }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Red600, contentColor = Color.White)
                            ) { Text("Delete", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
                            OutlinedButton(
                                onClick = { deletingComment = null },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDefault),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                            ) { Text("Cancel", fontWeight = FontWeight.Medium, fontSize = 15.sp) }
                        }
                    }
                )
            }

            // ── Long-press context sheet ──────────────────────────────────────────
            if (contextMenuComment != null) {
                val c = contextMenuComment!!
                ModalBottomSheet(
                    onDismissRequest = { contextMenuComment = null },
                    sheetState = contextSheetState,
                    containerColor = BgSurface,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    dragHandle = {
                        Box(Modifier.padding(top = 12.dp, bottom = 4.dp)
                            .width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFD1D5DB)))
                    }
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp).navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Preview bubble
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFF3F4F6))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(32.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Green900, Green700))),
                                Alignment.Center) {
                                val contextMenuPhoto = userPhotoCache[c.userName]
                                if (contextMenuPhoto != null) {
                                    AsyncImage(
                                        model              = contextMenuPhoto,
                                        contentDescription = "User avatar",
                                        contentScale       = ContentScale.Crop,
                                        modifier           = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                } else {
                                    Text(userInitials(c.userName), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    userDisplayNameCache[c.userName] ?: c.userName,
                                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary
                                )
                                Text(c.text, fontSize = 13.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // Edit option — only for comment owner
                        if (c.userName == userName) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        editingComment = c; editCommentText = c.text; contextMenuComment = null
                                    }
                                    .background(Color(0xFFF9FAFB))
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Green50), Alignment.Center) {
                                    Icon(Icons.Default.Edit, null, tint = Green900, modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text("Edit comment", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
                                    Text("Change what you wrote", fontSize = 12.sp, color = TextMuted)
                                }
                            }
                        }
                        // Delete option — only for comment owner
                        if (c.userName == userName) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        deletingComment = c; contextMenuComment = null
                                    }
                                    .background(Color(0xFFF9FAFB))
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Red50), Alignment.Center) {
                                    Icon(Icons.Default.DeleteForever, null, tint = Red600, modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text("Delete comment", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Red600)
                                    Text("Remove permanently", fontSize = 12.sp, color = TextMuted)
                                }
                            }
                        }

                        // Report option — only for comments the user does NOT own and viewer is not admin
                        if (c.userName != userName && !isAdmin) {
                            val isAlreadyReported = remember(c.id) { mutableStateOf(false) }
                            LaunchedEffect(c.id) {
                                db.collection("reportedComments")
                                    .whereEqualTo("commentId", c.id)
                                    .whereEqualTo("reportedBy", userName)
                                    .limit(1)
                                    .get()
                                    .addOnSuccessListener { snap -> isAlreadyReported.value = !snap.isEmpty }
                            }
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        enabled = !isAlreadyReported.value
                                    ) {
                                        val reportRef = db.collection("reportedComments")
                                        reportRef.add(hashMapOf(
                                            "commentId"  to c.id,
                                            "postId"     to selectedPostId,
                                            "reportedBy" to userName,
                                            "userName"   to c.userName,
                                            "text"       to c.text,
                                            "timestamp"  to System.currentTimeMillis()
                                        )).addOnSuccessListener {
                                            isAlreadyReported.value = true
                                            contextMenuComment = null
                                            // Check total reports on this comment
                                            reportRef.whereEqualTo("commentId", c.id).get()
                                                .addOnSuccessListener { snap ->
                                                    val count = snap.size()
                                                    if (count >= 3) {
                                                        // Auto-hide the comment
                                                        db.collection("posts").document(selectedPostId)
                                                            .collection("comments").document(c.id)
                                                            .update("status", "hidden")
                                                        // Decrement comment count on post
                                                        db.collection("posts").document(selectedPostId)
                                                            .update("comments", FieldValue.increment(-1))
                                                        // Notify admin
                                                        db.collection("notifications").add(hashMapOf(
                                                            "userName"  to "Admin",
                                                            "message"   to "⚠️ Comment by ${c.userName} was auto-hidden after $count reports: \"${c.text.take(60)}\"",
                                                            "type"      to "alert",
                                                            "timestamp" to System.currentTimeMillis(),
                                                            "read"      to false
                                                        ))
                                                    } else {
                                                        // Notify admin of new report
                                                        db.collection("notifications").add(hashMapOf(
                                                            "userName"  to "Admin",
                                                            "message"   to "🚩 $userName reported a comment by ${c.userName}. ($count/3 reports): \"${c.text.take(60)}\"",
                                                            "type"      to "alert",
                                                            "timestamp" to System.currentTimeMillis(),
                                                            "read"      to false
                                                        ))
                                                    }
                                                }
                                        }
                                    }
                                    .background(Color(0xFFF9FAFB))
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                                        .background(if (isAlreadyReported.value) Color(0xFFF3F4F6) else Orange50),
                                    Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Flag, null,
                                        tint = if (isAlreadyReported.value) TextMuted else Orange600,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        if (isAlreadyReported.value) "Already reported" else "Report comment",
                                        fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                                        color = if (isAlreadyReported.value) TextMuted else Orange600
                                    )
                                    Text(
                                        if (isAlreadyReported.value) "You've already flagged this" else "Flag as rude or inappropriate",
                                        fontSize = 12.sp, color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }

                // Admin moderation options
                if (isAdmin && c.userName != userName) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                db.collection("posts").document(selectedPostId)
                                    .collection("comments").document(c.id)
                                    .update("status", "hidden")
                                    .addOnSuccessListener {
                                        db.collection("posts").document(selectedPostId)
                                            .update("comments", FieldValue.increment(-1))
                                        db.collection("notifications").add(hashMapOf(
                                            "userName"  to c.userName,
                                            "message"   to "Your comment was hidden by an admin for violating community guidelines.",
                                            "type"      to "moderation",
                                            "timestamp" to System.currentTimeMillis(),
                                            "read"      to false
                                        ))
                                        contextMenuComment = null
                                    }
                            }
                            .background(Color(0xFFF9FAFB))
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Amber50), Alignment.Center) {
                            Icon(Icons.Default.VisibilityOff, null, tint = Amber500, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Hide comment", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
                            Text("Remove from view, keeps data", fontSize = 12.sp, color = TextMuted)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                deletingComment = c
                                contextMenuComment = null
                            }
                            .background(Color(0xFFF9FAFB))
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Red50), Alignment.Center) {
                            Icon(Icons.Default.DeleteForever, null, tint = Red600, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Delete comment", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Red600)
                            Text("Permanently remove", fontSize = 12.sp, color = TextMuted)
                        }
                    }
                }
            }

            ModalBottomSheet(
                onDismissRequest = {
                    showCommentsSheet = false; commentsReg?.remove(); commentsReg = null
                    postComments.clear(); commentText = ""; replyingTo = null; replyParentId = null
                },
                sheetState = sheetState, containerColor = BgSurface,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                dragHandle = {
                    Box(Modifier.padding(top = 12.dp, bottom = 4.dp)
                        .width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFD1D5DB)))
                }
            ) {
                Column(
                    Modifier.fillMaxHeight(0.85f).fillMaxWidth()
                        .padding(horizontal = 16.dp).navigationBarsPadding()
                ) {
                    // Sheet header
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Comments", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            if (postComments.isNotEmpty()) {
                                Text("${postComments.size}", fontSize = 13.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                            }
                        }
                        if (postComments.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Green50)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { commentSortNewest = !commentSortNewest }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    if (commentSortNewest) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    null, tint = Green900, modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    if (commentSortNewest) "Newest" else "Oldest",
                                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Green900
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = DividerColor)
                    Spacer(Modifier.height(8.dp))

                    LazyColumn(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        if (postComments.isEmpty()) {
                            item {
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(Modifier.size(56.dp).clip(CircleShape).background(Green50), Alignment.Center) {
                                        Icon(Icons.Default.ChatBubbleOutline, null, tint = Green700, modifier = Modifier.size(24.dp))
                                    }
                                    Text("No comments yet", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Be the first to say something!", color = TextMuted, fontSize = 13.sp)
                                }
                            }
                        }
                        items(sortedTopComments, key = { it.id }) { comment ->
                            val replies = postComments.filter { it.replyTo == comment.id }
                            val isLiked = comment.likedBy.contains(userName)
                            val repliesExpanded = comment.id in expandedReplies
                            var hasReportedComment by remember(comment.id) { mutableStateOf(false) }
                            LaunchedEffect(comment.id) {
                                if (comment.userName != userName) {
                                    db.collection("reportedComments")
                                        .whereEqualTo("commentId", comment.id)
                                        .whereEqualTo("reportedBy", userName)
                                        .limit(1)
                                        .get()
                                        .addOnSuccessListener { snap ->
                                            hasReportedComment = !snap.isEmpty
                                        }
                                }
                            }
                            Column {
                                Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                                    Box(Modifier.size(34.dp).clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(Green900, Green700))),
                                        Alignment.Center) {
                                        val commentPhoto = userPhotoCache[comment.userName]
                                        if (commentPhoto != null) {
                                            AsyncImage(
                                                model              = commentPhoto,
                                                contentDescription = "User avatar",
                                                contentScale       = ContentScale.Crop,
                                                modifier           = Modifier.fillMaxSize().clip(CircleShape)
                                            )
                                        } else {
                                            Text(userInitials(comment.userName), fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Column(
                                            Modifier
                                                .combinedClickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                    onClick = {},
                                                    onLongClick = { contextMenuComment = comment }
                                                )
                                                .background(Color(0xFFF3F4F6), RoundedCornerShape(12.dp))
                                                .padding(horizontal = 12.dp, vertical = 9.dp)
                                                .fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                userDisplayNameCache[comment.userName] ?: comment.userName,
                                                fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary
                                            )
                                            Text(comment.text, fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp)
                                            if ((comment.editedAt ?: 0L) > 0L) {
                                                Text(
                                                    "edited",
                                                    fontSize = 10.sp,
                                                    color = TextMuted,
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                                )
                                            }
                                        }
                                        // Timestamp + action row
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(start = 4.dp)
                                        ) {
                                            Text(
                                                formatTimestamp(comment.timestamp),
                                                fontSize = 10.sp,
                                                color = TextMuted
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier
                                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { toggleCommentLike(comment) }
                                                    .padding(vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                    null,
                                                    tint = if (isLiked) Color(0xFFEF4444) else TextMuted,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                if (comment.likes > 0) {
                                                    Text("${comment.likes}", fontSize = 11.sp, color = if (isLiked) Color(0xFFEF4444) else TextMuted)
                                                }
                                            }
                                            Text(
                                                "Reply", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextMuted,
                                                modifier = Modifier
                                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                                        replyingTo = comment
                                                        replyParentId = comment.id
                                                        // Auto-expand replies when user starts replying
                                                        expandedReplies = expandedReplies + comment.id
                                                    }
                                                    .padding(vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                // ── Replies section ───────────────────────────────────
                                if (replies.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    // "View X replies" / "Hide replies" toggle
                                    Row(
                                        modifier = Modifier
                                            .padding(start = 44.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                expandedReplies = if (repliesExpanded)
                                                    expandedReplies - comment.id
                                                else
                                                    expandedReplies + comment.id
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            Modifier.width(20.dp).height(1.dp)
                                                .background(Green700.copy(alpha = 0.4f))
                                        )
                                        Text(
                                            if (repliesExpanded) "Hide replies"
                                            else "View ${replies.size} repl${if (replies.size == 1) "y" else "ies"}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Green700
                                        )
                                        Icon(
                                            if (repliesExpanded) Icons.Default.KeyboardArrowUp
                                            else Icons.Default.KeyboardArrowDown,
                                            null,
                                            tint = Green700,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    // Expanded replies list
                                    if (repliesExpanded) {
                                        Spacer(Modifier.height(4.dp))
                                        replies.forEach { reply ->
                                            val isReplyLiked = reply.likedBy.contains(userName)
                                            Row(
                                                verticalAlignment = Alignment.Top,
                                                modifier = Modifier.fillMaxWidth().padding(start = 44.dp)
                                            ) {
                                                Box(Modifier.size(26.dp).clip(CircleShape)
                                                    .background(Brush.linearGradient(listOf(Green700, Green500))),
                                                    Alignment.Center) {
                                                    val replyPhoto = userPhotoCache[reply.userName]
                                                    if (replyPhoto != null) {
                                                        AsyncImage(
                                                            model              = replyPhoto,
                                                            contentDescription = "User avatar",
                                                            contentScale       = ContentScale.Crop,
                                                            modifier           = Modifier.fillMaxSize().clip(CircleShape)
                                                        )
                                                    } else {
                                                        Text(userInitials(reply.userName), fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold, color = Color.White)
                                                    }
                                                }
                                                Spacer(Modifier.width(8.dp))
                                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Column(
                                                        Modifier
                                                            .combinedClickable(
                                                                interactionSource = remember { MutableInteractionSource() },
                                                                indication = null,
                                                                onClick = {},
                                                                onLongClick = { contextMenuComment = reply }
                                                            )
                                                            .background(Color(0xFFF3F4F6), RoundedCornerShape(10.dp))
                                                            .padding(horizontal = 10.dp, vertical = 7.dp)
                                                            .fillMaxWidth(),
                                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        // Name + "replying to" label on same row
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Text(
                                                                userDisplayNameCache[reply.userName] ?: reply.userName,
                                                                fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextPrimary
                                                            )
                                                            val replyTargetName = reply.replyToUserName
                                                            if (!replyTargetName.isNullOrBlank()) {
                                                                val targetDisplay = userDisplayNameCache[replyTargetName] ?: replyTargetName
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                                ) {
                                                                    Icon(
                                                                        Icons.AutoMirrored.Filled.Send,
                                                                        null,
                                                                        tint = Green700.copy(alpha = 0.7f),
                                                                        modifier = Modifier.size(9.dp)
                                                                    )
                                                                    Text(
                                                                        targetDisplay,
                                                                        fontSize = 11.sp,
                                                                        color = Green700,
                                                                        fontWeight = FontWeight.SemiBold
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        Text(reply.text, fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp)
                                                        if ((reply.editedAt ?: 0L) > 0L) {
                                                            Text(
                                                                "edited",
                                                                fontSize = 10.sp,
                                                                color = TextMuted,
                                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                                            )
                                                        }
                                                    }
                                                    // Timestamp + action row for reply
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                        modifier = Modifier.padding(start = 4.dp)
                                                    ) {
                                                        Text(
                                                            formatTimestamp(reply.timestamp),
                                                            fontSize = 10.sp,
                                                            color = TextMuted
                                                        )
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                            modifier = Modifier
                                                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { toggleCommentLike(reply) }
                                                                .padding(vertical = 2.dp)
                                                        ) {
                                                            Icon(
                                                                if (isReplyLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                                null,
                                                                tint = if (isReplyLiked) Color(0xFFEF4444) else TextMuted,
                                                                modifier = Modifier.size(11.dp)
                                                            )
                                                            if (reply.likes > 0) {
                                                                Text("${reply.likes}", fontSize = 10.sp, color = if (isReplyLiked) Color(0xFFEF4444) else TextMuted)
                                                            }
                                                        }
                                                        Text(
                                                            "Reply",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = TextMuted,
                                                            modifier = Modifier
                                                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                                                    replyingTo = reply
                                                                    replyParentId = comment.id
                                                                    expandedReplies = expandedReplies + comment.id
                                                                }
                                                                .padding(vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(6.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = DividerColor)

                    androidx.compose.animation.AnimatedVisibility(visible = replyingTo != null) {
                        Row(
                            Modifier.fillMaxWidth().background(Green50).padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Green700, modifier = Modifier.size(12.dp))
                                Column {
                                    Text(
                                        "Replying to ${replyingTo?.userName?.let { userDisplayNameCache[it] ?: it }}",
                                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Green900
                                    )
                                    replyingTo?.text?.let { text ->
                                        val preview = text.take(60).let { if (text.length > 60) "$it…" else it }
                                        Text(
                                            preview,
                                            fontSize = 11.sp,
                                            color = Green700,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { replyingTo = null; replyParentId = null }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(Modifier.size(34.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Green900, Green700))),
                            Alignment.Center) {
                            val myPhoto = userPhotoCache[userName]
                            if (myPhoto != null) {
                                AsyncImage(
                                    model              = myPhoto,
                                    contentDescription = "Your avatar",
                                    contentScale       = ContentScale.Crop,
                                    modifier           = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Text(userInitials(userName), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        OutlinedTextField(
                            value = commentText, onValueChange = { commentText = it },
                            placeholder = {
                                Text(
                                    if (replyingTo != null) "Write a reply…" else "Add a comment…",
                                    fontSize = 13.sp, color = TextMuted
                                )
                            },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp), maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor      = Green700,
                                unfocusedBorderColor    = BorderDefault,
                                cursorColor             = Green700,
                                focusedTextColor        = TextPrimary,
                                unfocusedTextColor      = TextPrimary,
                                focusedContainerColor   = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                        )
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (commentText.isBlank()) Color(0xFFF3F4F6) else Green900)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { submitComment() },
                            Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send",
                                tint = if (commentText.isBlank()) TextMuted else Color.White,
                                modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    
        // ── Scaffold ──────────────────────────────────────────────────────────────
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.PedalBike, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            Text("PedalConnect", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White, letterSpacing = 0.3.sp)
                        }
                    },
                    actions = {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) Badge(containerColor = Color(0xFFEF4444), contentColor = Color.White) {
                                    Text(if (unreadCount > 99) "99+" else "$unreadCount", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            IconButton(onClick = { navController.navigate("notifications/$userName") }) {
                                Icon(Icons.Default.Notifications, "Notifications", tint = Color.White)
                            }
                        }
                        IconButton(onClick = { navController.navigate("message") }) {
                            Icon(Icons.Filled.Message, "Messages", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Green900),
                    modifier = Modifier.shadow(elevation = 2.dp)
                )
            },
            floatingActionButton = {
                if (!isAdmin) {
                    FloatingActionButton(
                        onClick = { navController.navigate("add_post/$userName") },
                        containerColor = Green900, contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding()).size(56.dp)
                    ) {
                        Icon(Icons.Filled.Add, "Add Post", modifier = Modifier.size(26.dp))
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.End,
            containerColor = BgCanvas
        ) { innerPadding ->
            PullToRefreshBox(
                isRefreshing = isRefreshing, onRefresh = { refreshData() },
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = paddingValues.calculateBottomPadding() + 80.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // Hero
                    item { HeroHeader(userName = userName, userDisplayName = userDisplayName, weather = weather, isAdmin = isAdmin, photoUrl = userPhotoUrl) }

                    // Ride events card — hidden for Admin
                    if (isAdmin) item { Spacer(Modifier.height(16.dp)) }
                    if (!isAdmin) item {
                        Spacer(Modifier.height(12.dp))
                        RidingEventsCard(
                            onExploreRides = {
                                if (nextEvent?.organizer == userName) onExploreRides(null)
                                else onExploreRides(nextEvent?.id)
                            },
                            nextEvent   = nextEvent,
                            isLoading   = isLoadingEvent,
                            isOrganizer = nextEvent?.organizer == userName
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    // Feed header + sort chips
                    item {
                        Column(Modifier.fillMaxWidth().background(BgCanvas).padding(horizontal = 16.dp)) {
                            val acceptedCount = posts.count { it.status == "accepted" }
                            Text("Community Feed", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(
                                "$acceptedCount ride${if (acceptedCount != 1) "s" else ""} shared",
                                fontSize = 12.sp, color = TextMuted
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        // Sort chips
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Sort, null, tint = TextMuted, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(2.dp))
                            listOf(
                                Triple(0, Icons.Default.KeyboardArrowDown, "Newest"),
                                Triple(1, Icons.Default.KeyboardArrowUp,   "Oldest"),
                                Triple(2, Icons.Default.Favorite,          "Top Liked")
                            ).forEach { (mode, icon, label) ->
                                val selected = sortMode == mode
                                FilterChip(
                                    selected = selected, onClick = { sortMode = mode },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(icon, null, modifier = Modifier.size(13.dp))
                                            Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Green900, selectedLabelColor = Color.White,
                                        selectedLeadingIconColor = Color.White,
                                        containerColor = BgSurface, labelColor = TextSecondary),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true, selected = selected,
                                        borderColor = BorderDefault, selectedBorderColor = Green900,
                                        borderWidth = 1.dp, selectedBorderWidth = 1.5.dp),
                                    shape = RoundedCornerShape(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
    
                    // Feed content
                    if (isLoadingFeed) {
                        items(3) {
                            ShimmerFeedCard(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                            )
                        }
                    } else {
                        val acceptedPosts = sortedPosts.filter { it.status == "accepted" }
                        val pendingPosts  = sortedPosts.filter { it.status == "pending" }
    
                        if (pendingPosts.isNotEmpty()) {
                            item {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                                        .clip(RoundedCornerShape(10.dp)).background(Amber50)
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.HourglassTop, null, tint = Amber500, modifier = Modifier.size(14.dp))
                                    Text(
                                        "${pendingPosts.size} post${if (pendingPosts.size != 1) "s" else ""} awaiting approval",
                                        fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF92400E)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            items(pendingPosts, key = { it.id }) { post ->
                                CommunityFeedCard(
                                    post = post, currentUser = userName,
                                    currentUserDisplayName = userDisplayName,
                                    onLike    = { toggleLike(post) },
                                    onComment = { selectedPostId = post.id; showCommentsSheet = true },
                                    onEdit    = { editingPost = post; editDescription = post.description; showEditDialog = true },
                                    onDelete  = { deletingPost = post; showDeleteDialog = true },
                                    photoUrl  = userPhotoCache[post.userName],
                                    isAdmin   = isAdmin
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            item { HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = DividerColor) }
                        }
    
                        if (acceptedPosts.isEmpty()) {
                            item {
                                EmptyFeedCard(
                                    message = if (pendingPosts.isNotEmpty())
                                        "No community posts yet — yours are pending approval"
                                    else "No rides posted yet"
                                )
                            }
                        } else {
                            items(acceptedPosts, key = { it.id }) { post ->
                                CommunityFeedCard(
                                    post = post, currentUser = userName,
                                    currentUserDisplayName = userDisplayName,
                                    onLike    = { toggleLike(post) },
                                    onComment = { selectedPostId = post.id; showCommentsSheet = true },
                                    onEdit    = { editingPost = post; editDescription = post.description; showEditDialog = true },
                                    onDelete  = { deletingPost = post; showDeleteDialog = true },
                                    photoUrl  = userPhotoCache[post.userName],
                                    isAdmin   = isAdmin
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // Hero Header
    // ─────────────────────────────────────────────────────────────────────────────
    @Composable
    fun HeroHeader(userName: String, userDisplayName: String = "", weather: WeatherState, isAdmin: Boolean = false, photoUrl: String? = null) {
        val hour     = Calendar.getInstance(TimeZone.getDefault()).get(Calendar.HOUR_OF_DAY)
        val greeting = when { hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }
        val today    = SimpleDateFormat("EEEE, MMM dd yyyy", Locale.getDefault()).format(Date())
    
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Green950, Green800)))
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Greeting row
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("$greeting 👋", fontSize = 12.sp, color = Color.White.copy(alpha = 0.65f), fontWeight = FontWeight.Medium)
                        Text(
                            userDisplayName.ifBlank { userName }.split(" ").first(),
                            fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            color = Color.White, letterSpacing = (-0.5).sp
                        )
                        Text(today, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                    // Avatar
                    Box(
                        Modifier.size(48.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f)),
                        Alignment.Center
                    ) {
                        if (photoUrl != null) {
                            var isImageLoading by remember { mutableStateOf(true) }
                            AsyncImage(
                                model              = photoUrl,
                                contentDescription = "Profile photo",
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier.fillMaxSize().clip(CircleShape),
                                onLoading  = { isImageLoading = true },
                                onSuccess  = { isImageLoading = false },
                                onError    = { isImageLoading = false }
                            )
                            if (isImageLoading) {
                                Box(
                                    Modifier.fillMaxSize()
                                        .background(Color.White.copy(alpha = 0.15f), CircleShape),
                                    Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        } else {
                            Text(userInitials(userName), fontSize = 16.sp,
                                fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
    
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
    
                // Weather row
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        // Weather icon box
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.1f)),
                            Alignment.Center
                        ) {
                            if (weather.isLoading) {
                                CircularProgressIndicator(color = Amber500, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                val (weatherIcon, weatherTint) = when {
                                    weather.emoji.contains("⛈") || weather.emoji.contains("🌩") -> Pair(Icons.Default.Thunderstorm, Color(0xFFB0BEC5))
                                    weather.emoji.contains("🌧") || weather.emoji.contains("🌦") -> Pair(Icons.Default.Grain, Color(0xFF90CAF9))
                                    weather.emoji.contains("🌫") -> Pair(Icons.Default.WaterDrop, Color(0xFFB0BEC5))
                                    weather.emoji.contains("☁") || weather.emoji.contains("⛅") -> Pair(Icons.Default.Cloud, Color(0xFFB0BEC5))
                                    weather.emoji.contains("🌙") -> Pair(Icons.Default.NightsStay, Color(0xFFCE93D8))
                                    weather.emoji.contains("❄")  -> Pair(Icons.Default.AcUnit, Color(0xFF90CAF9))
                                    else -> Pair(Icons.Default.WbSunny, Amber500)
                                }
                                Icon(weatherIcon, null, tint = weatherTint, modifier = Modifier.size(18.dp))
                            }
                        }
                        Column {
                            Text(
                                if (weather.isLoading) "Fetching weather…"
                                else "${weather.tempC}°C · ${weather.description}",
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            if (!weather.isLoading && !isAdmin) {
                                Text(weather.rideAdvice, fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (!weather.isLoading) {
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text("Live", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Amber500, letterSpacing = 0.5.sp)
                        }
                    }
                }
            }
        }
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // Riding Events Card
    // ─────────────────────────────────────────────────────────────────────────────
    @Composable
    fun RidingEventsCard(
        onExploreRides: () -> Unit = {},
        nextEvent: RideEvent?      = null,
        isLoading: Boolean         = false,
        isOrganizer: Boolean       = false
    ) {
        val hasEvent   = nextEvent != null
        val title      = nextEvent?.title?.takeIf { it.isNotBlank() } ?: "Community Ride"
        val route      = nextEvent?.route?.takeIf { it.isNotBlank() } ?: "Metro Manila & nearby"
        val dateText   = if (nextEvent != null && nextEvent.date > 0)
            SimpleDateFormat("EEE, MMM dd", Locale.getDefault()).format(Date(nextEvent.date)) else "Every week"
        val timeText   = nextEvent?.time?.takeIf { it.isNotBlank() } ?: "Early morning"
        val riderCount = nextEvent?.participants?.size ?: 0
    
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Green900),
            shape = RoundedCornerShape(18.dp),
            elevation = CardDefaults.cardElevation(3.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                // Title + badge row
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (isLoading) {
                            Box(Modifier.width(160.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.15f)))
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.width(100.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.1f)))
                        } else {
                            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = (-0.3).sp)
                            Text(
                                if (hasEvent && riderCount > 0) "$riderCount rider${if (riderCount != 1) "s" else ""} joined"
                                else "No upcoming rides yet",
                                color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    if (hasEvent) {
                        val isTodayEvent = nextEvent?.date?.let { d ->
                            val ev = Calendar.getInstance().apply { timeInMillis = d }
                            val now = Calendar.getInstance()
                            ev.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                                    ev.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
                        } ?: false
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(if (isTodayEvent) Amber500 else Green500)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(if (isTodayEvent) "TODAY" else "UPCOMING",
                                color = Color.White, fontWeight = FontWeight.Bold,
                                fontSize = 10.sp, letterSpacing = 0.8.sp)
                        }
                    }
                }
    
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Spacer(Modifier.height(12.dp))
    
                if (!isLoading) {
                    if (hasEvent) {
                        // Date + route chips
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(Icons.Default.CalendarMonth, null, tint = Amber500, modifier = Modifier.size(14.dp))
                                Text("$dateText · $timeText", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.LocationOn, null, tint = Amber500, modifier = Modifier.size(14.dp))
                                Text(route, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    } else {
                        // Empty state inline
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.08f)).padding(12.dp)
                        ) {
                            Icon(Icons.Default.DirectionsBike, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                            Column {
                                Text("No upcoming rides yet", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Be the first to create one", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                            }
                        }
                    }
                }
    
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onExploreRides,
                    colors  = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Green900),
                    shape   = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Icon(Icons.Default.DirectionsBike, null, tint = Green900, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when { !hasEvent -> "Create a Ride"; isOrganizer -> "See All Rides"; else -> "Join this Ride" },
                        color = Green900, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                    )
                }
            }
        }
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // Empty Feed Card
    // ─────────────────────────────────────────────────────────────────────────────
    @Composable
    fun EmptyFeedCard(message: String = "No rides posted yet") {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(Green50), Alignment.Center) {
                Icon(Icons.Default.PedalBike, null, tint = Green700, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(message, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary, textAlign = TextAlign.Center)
            Text("Be the first to share your ride!", fontSize = 13.sp, color = TextSecondary)
        }
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // Community Feed Card — redesigned
    // ─────────────────────────────────────────────────────────────────────────────
    @Composable
    fun CommunityFeedCard(
        post: PostItem, currentUser: String,
        currentUserDisplayName: String = "",
        onLike: () -> Unit = {}, onComment: () -> Unit = {},
        onEdit: () -> Unit = {}, onDelete: () -> Unit = {},
        photoUrl: String? = null,
        isAdmin: Boolean = false,
        viewerIsAuthor: Boolean = post.userName == currentUser
    ) {
        val isOwner      = viewerIsAuthor
        var menuExpanded by remember { mutableStateOf(false) }
        var expanded     by remember { mutableStateOf(false) }
        var isLiked      by remember(post.id) { mutableStateOf(post.likedBy.contains(currentUser)) }
        var localLikes   by remember(post.id) { mutableIntStateOf(post.likes) }
        var hasReported       by remember { mutableStateOf(false) }
        var showRideDetail    by remember { mutableStateOf(false) }

        // Check if current user already reported this post
        LaunchedEffect(post.id) {
            if (!isOwner && !isAdmin) {
                FirebaseFirestore.getInstance()
                    .collection("reportedPosts")
                    .whereEqualTo("postId", post.id)
                    .whereEqualTo("reportedBy", currentUser)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { snap ->
                        hasReported = !snap.isEmpty
                    }
            }
        }
    
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = BgSurface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                // ── Header ────────────────────────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar
                    Box(
                        Modifier.size(42.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Green900, Green700))),
                        Alignment.Center
                    ) {
                        if (photoUrl != null) {
                            AsyncImage(
                                model              = photoUrl,
                                contentDescription = "User avatar",
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Text(userInitials(post.userName), fontWeight = FontWeight.Bold,
                                fontSize = 14.sp, color = Color.White)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(post.displayName.ifBlank { post.userName }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Activity badge
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp)).background(Green50)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(post.activity, fontSize = 11.sp, color = Green800, fontWeight = FontWeight.Medium, maxLines = 1)
                            }
                            // Distance
                            if (post.distance.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Icon(Icons.Default.Route, null, tint = TextMuted, modifier = Modifier.size(11.dp))
                                    val cleanDist = post.distance.trim().removeSuffix("km").removeSuffix("KM").trim()
                                    Text("$cleanDist km", fontSize = 11.sp, color = TextMuted, maxLines = 1)
                                }
                            }
                            // Timestamp
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Schedule, null, tint = TextMuted, modifier = Modifier.size(11.dp))
                                Text(
                                    formatTimestamp(post.timestamp),
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        // Pending badge
                        if (post.status == "pending") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.HourglassTop, null, tint = Amber500, modifier = Modifier.size(12.dp))
                                Text("Pending approval", color = Amber500, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        // Edited label
                        if (post.editedAt > 0L) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Default.Edit, null, tint = TextMuted, modifier = Modifier.size(10.dp))
                                Text(
                                    "(Edited)",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                    // Action: owner menu (edit/delete), non-owner menu (report), or admin menu (hide/delete)
                    if (isAdmin || (!isAdmin && (isOwner || post.status == "accepted"))) {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, "Options", tint = TextMuted)
                            }
                            DropdownMenu(
                                expanded = menuExpanded, onDismissRequest = { menuExpanded = false },
                                containerColor = BgSurface, shape = RoundedCornerShape(14.dp)
                            ) {
                                if (isAdmin) {
                                    // ── Admin menu: Hide + Delete ─────────────────────────────────────
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Amber50), Alignment.Center) {
                                                    Icon(Icons.Default.VisibilityOff, null, tint = Amber500, modifier = Modifier.size(15.dp))
                                                }
                                                Column {
                                                    Text("Hide post", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TextPrimary)
                                                    Text("Remove from feed, keeps data", fontSize = 11.sp, color = TextMuted)
                                                }
                                            }
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            val db = FirebaseFirestore.getInstance()
                                            db.collection("posts").document(post.id)
                                                .update("status", "hidden")
                                                .addOnSuccessListener {
                                                    // Notify post owner
                                                    db.collection("notifications").add(hashMapOf(
                                                        "userName"  to post.userName,
                                                        "message"   to "Your post was removed by an admin for violating community guidelines.",
                                                        "type"      to "moderation",
                                                        "timestamp" to System.currentTimeMillis(),
                                                        "read"      to false,
                                                        "postId"    to post.id
                                                    ))
                                                }
                                        },
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = DividerColor)
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Red50), Alignment.Center) {
                                                    Icon(Icons.Default.Delete, null, tint = Red600, modifier = Modifier.size(15.dp))
                                                }
                                                Column {
                                                    Text("Delete post", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Red600)
                                                    Text("Permanently remove", fontSize = 11.sp, color = TextMuted)
                                                }
                                            }
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onDelete()
                                        },
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                } else if (isOwner) {
                                    // ── Owner menu: Edit + Delete ─────────────────────────────────────
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Green50), Alignment.Center) {
                                                    Icon(Icons.Default.Edit, null, tint = Green900, modifier = Modifier.size(15.dp))
                                                }
                                                Column {
                                                    Text("Edit post", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TextPrimary)
                                                    Text("Update description", fontSize = 11.sp, color = TextMuted)
                                                }
                                            }
                                        },
                                        onClick = { menuExpanded = false; onEdit() },
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = DividerColor)
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Red50), Alignment.Center) {
                                                    Icon(Icons.Default.Delete, null, tint = Red600, modifier = Modifier.size(15.dp))
                                                }
                                                Column {
                                                    Text("Delete post", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Red600)
                                                    Text("Remove permanently", fontSize = 11.sp, color = TextMuted)
                                                }
                                            }
                                        },
                                        onClick = { menuExpanded = false; onDelete() },
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                } else {
                                    // ── Non-owner menu: Report ────────────────────────────────────────
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                                                    .background(if (hasReported) Color(0xFFF3F4F6) else Red50),
                                                    Alignment.Center) {
                                                    Icon(Icons.Default.Flag, null,
                                                        tint = if (hasReported) TextMuted else Red600,
                                                        modifier = Modifier.size(15.dp))
                                                }
                                                Column {
                                                    Text(
                                                        if (hasReported) "Already reported" else "Report post",
                                                        fontWeight = FontWeight.Medium, fontSize = 14.sp,
                                                        color = if (hasReported) TextMuted else Red600
                                                    )
                                                    Text(
                                                        if (hasReported) "You've already flagged this" else "Flag inappropriate content",
                                                        fontSize = 11.sp, color = TextMuted
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            if (!hasReported) {
                                                menuExpanded = false
                                                val db = FirebaseFirestore.getInstance()
                                                db.collection("reportedPosts").add(hashMapOf(
                                                    "postId"            to post.id,
                                                    "reportedBy"        to currentUser,
                                                    "reportedByDisplay" to currentUserDisplayName.ifBlank { currentUser },
                                                    "userName"          to post.userName,
                                                    "displayName"       to post.displayName.ifBlank { post.userName },
                                                    "descriptionSnap"   to post.description.take(200),
                                                    "timestamp"         to System.currentTimeMillis()
                                                )).addOnSuccessListener {
                                                    hasReported = true
                                                    db.collection("reportedPosts")
                                                        .whereEqualTo("postId", post.id)
                                                        .get()
                                                        .addOnSuccessListener { snap ->
                                                            val count = snap.size()
                                                            if (count >= 3) {
                                                                db.collection("posts").document(post.id)
                                                                    .update("status", "hidden")
                                                                db.collection("notifications").add(hashMapOf(
                                                                    "userName"  to "Admin",
                                                                    "message"   to "⚠️ Post by ${post.displayName.ifBlank { post.userName }} was auto-hidden after $count reports.",
                                                                    "type"      to "alert",
                                                                    "timestamp" to System.currentTimeMillis(),
                                                                    "read"      to false
                                                                ))
                                                            } else {
                                                                db.collection("notifications").add(hashMapOf(
                                                                    "userName"  to "Admin",
                                                                    "message"   to "🚩 ${currentUserDisplayName.ifBlank { currentUser }} reported a post by ${post.displayName.ifBlank { post.userName }}. ($count/3 reports)",
                                                                    "type"      to "alert",
                                                                    "timestamp" to System.currentTimeMillis(),
                                                                    "read"      to false
                                                                ))
                                                            }
                                                        }
                                                }
                                            } else {
                                                menuExpanded = false
                                            }
                                        },
                                        enabled = !hasReported,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
    
                // ── Post image (full-bleed, no padding) ──────────────────────────
                if (post.imageUrl.isNotBlank()) {
                    var showFullImage by remember { mutableStateOf(false) }

                    if (showFullImage) {
                        Dialog(onDismissRequest = { showFullImage = false }) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { showFullImage = false }
                            ) {
                                AsyncImage(
                                    model = post.imageUrl,
                                    contentDescription = "Ride photo full view",
                                    modifier = Modifier.fillMaxWidth(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                                )
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp)
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f)),
                                    Alignment.Center
                                ) {
                                    Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showFullImage = true }
                    ) {
                        AsyncImage(
                            model = post.imageUrl, contentDescription = "Ride photo",
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                }
    
                // ── Description ───────────────────────────────────────────────────
                if (post.description.isNotBlank()) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            text = post.description, fontSize = 14.sp, color = TextSecondary,
                            lineHeight = 21.sp,
                            maxLines   = if (expanded) Int.MAX_VALUE else 4,
                            overflow   = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis
                        )
                        if (!expanded && post.description.length > 120) {
                            Text("Read more", fontSize = 13.sp, color = Green700, fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { expanded = true }.padding(top = 4.dp))
                        } else if (expanded) {
                            Text("Show less", fontSize = 13.sp, color = TextMuted,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { expanded = false }.padding(top = 4.dp))
                        }
                    }
                }

                // ── Route preview — static image if available, canvas fallback ──
                if (post.routeImageUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, DividerColor, RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showRideDetail = true }
                    ) {
                        coil.compose.AsyncImage(
                            model              = post.routeImageUrl,
                            contentDescription = "Route preview",
                            contentScale       = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize()
                        )
                    }
                } else if (post.polyline.size >= 2) {
                    val points = post.polyline.mapNotNull { pt ->
                        val lat = pt["lat"] ?: return@mapNotNull null
                        val lon = pt["lon"] ?: return@mapNotNull null
                        Pair(lat, lon)
                    }
                    if (points.size >= 2) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, DividerColor, RoundedCornerShape(12.dp))
                                .background(Color(0xFFF0F4F0))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { showRideDetail = true }
                        ) {
                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                val minLat = points.minOf { it.first }
                                val maxLat = points.maxOf { it.first }
                                val minLon = points.minOf { it.second }
                                val maxLon = points.maxOf { it.second }
                                val latRange = (maxLat - minLat).takeIf { it > 0 } ?: 0.0001
                                val lonRange = (maxLon - minLon).takeIf { it > 0 } ?: 0.0001
                                val padding  = 36f

                                fun toX(lon: Double) = (padding + ((lon - minLon) / lonRange) * (size.width  - padding * 2)).toFloat()
                                fun toY(lat: Double) = (padding + ((maxLat - lat) / latRange) * (size.height - padding * 2)).toFloat()

                                // Draw polyline path
                                val path = androidx.compose.ui.graphics.Path()
                                points.forEachIndexed { i, (lat, lon) ->
                                    if (i == 0) path.moveTo(toX(lon), toY(lat))
                                    else        path.lineTo(toX(lon), toY(lat))
                                }
                                drawPath(
                                    path  = path,
                                    color = Color(0xFF00B464),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width     = 6f,
                                        cap       = androidx.compose.ui.graphics.StrokeCap.Round,
                                        join      = androidx.compose.ui.graphics.StrokeJoin.Round
                                    )
                                )

                                // Start dot — yellow
                                points.firstOrNull()?.let { (lat, lon) ->
                                    drawCircle(color = Color(0xFFFFD600), radius = 10f, center = androidx.compose.ui.geometry.Offset(toX(lon), toY(lat)))
                                    drawCircle(color = Color.White,       radius = 5f,  center = androidx.compose.ui.geometry.Offset(toX(lon), toY(lat)))
                                }

                                // End dot — red
                                points.lastOrNull()?.let { (lat, lon) ->
                                    drawCircle(color = Color(0xFFD32F2F), radius = 10f, center = androidx.compose.ui.geometry.Offset(toX(lon), toY(lat)))
                                    drawCircle(color = Color.White,       radius = 5f,  center = androidx.compose.ui.geometry.Offset(toX(lon), toY(lat)))
                                }
                            }


                        }
                    }
                }

                HorizontalDivider(Modifier.padding(horizontal = 14.dp), thickness = 0.5.dp, color = DividerColor)

                // ── Action bar ────────────────────────────────────────────────────
                val canLike = !isAdmin && post.status != "pending"

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    FeedActionButton(
                        icon  = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        label = if (localLikes > 0) localLikes.toString() else "Like",
                        tint  = when {
                            !canLike -> TextMuted.copy(alpha = 0.4f)
                            isLiked  -> Color(0xFFEF4444)
                            else     -> TextMuted
                        },
                        onClick = {
                            if (canLike) {
                                isLiked = !isLiked
                                localLikes = if (isLiked) localLikes + 1 else localLikes - 1
                                onLike()
                            }
                        }
                    )
                    FeedActionButton(
                        icon = Icons.Default.ChatBubbleOutline,
                        label = if (post.comments > 0) post.comments.toString() else "Comment",
                        tint  = if (post.status == "pending") TextMuted.copy(alpha = 0.4f) else TextMuted,
                        onClick = { if (post.status != "pending") onComment() }
                    )
                    val shareContext = LocalContext.current
                    FeedActionButton(
                        icon = Icons.Default.Share,
                        label = "Share",
                        tint = if (post.status == "pending") TextMuted.copy(alpha = 0.4f) else TextMuted
                    ) {
                        if (post.status != "pending") {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT,
                                    "Check out ${post.userName}'s post on PedalConnect! 🚴")
                            }
                            shareContext.startActivity(android.content.Intent.createChooser(intent, "Share post"))
                        }
                    }
                }
            }
        }

        if (showRideDetail) {
            RideDetailSheet(post = post, onDismiss = { showRideDetail = false })
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
// Feed action button
    // ─────────────────────────────────────────────────────────────────────────────
    @Composable
    fun FeedActionButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 13.sp, color = tint, fontWeight = FontWeight.Normal)
        }
    }
    // Keep old name as alias for any callers that still use ActionButton
    @Composable
    fun ActionButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) =
        FeedActionButton(icon, label, tint, onClick)
    
    // ─────────────────────────────────────────────────────────────────────────────
    // Timestamp helper  (package-level — used by CommunityFeedCard)
    // ─────────────────────────────────────────────────────────────────────────────
    @Composable
    private fun ShimmerFeedCard(modifier: Modifier = Modifier) {
        val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
        val shimmerAlpha by infiniteTransition.animateFloat(
            initialValue  = 0.3f,
            targetValue   = 0.7f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation  = androidx.compose.animation.core.tween(900),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "shimmerAlpha"
        )
        val shimmer = Color(0xFFE0E0E0).copy(alpha = shimmerAlpha)

        Card(
            modifier  = modifier.fillMaxWidth(),
            colors    = CardDefaults.cardColors(containerColor = BgSurface),
            shape     = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Avatar + name row
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(shimmer))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.width(120.dp).height(12.dp)
                            .clip(RoundedCornerShape(6.dp)).background(shimmer))
                        Box(Modifier.width(80.dp).height(10.dp)
                            .clip(RoundedCornerShape(6.dp)).background(shimmer))
                    }
                }
                // Image placeholder
                Box(Modifier.fillMaxWidth().height(180.dp)
                    .clip(RoundedCornerShape(10.dp)).background(shimmer))
                // Text lines
                Box(Modifier.fillMaxWidth().height(10.dp)
                    .clip(RoundedCornerShape(6.dp)).background(shimmer))
                Box(Modifier.width(200.dp).height(10.dp)
                    .clip(RoundedCornerShape(6.dp)).background(shimmer))
                // Action bar
                Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Box(Modifier.width(50.dp).height(10.dp)
                        .clip(RoundedCornerShape(6.dp)).background(shimmer))
                    Box(Modifier.width(60.dp).height(10.dp)
                        .clip(RoundedCornerShape(6.dp)).background(shimmer))
                    Box(Modifier.width(40.dp).height(10.dp)
                        .clip(RoundedCornerShape(6.dp)).background(shimmer))
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
// Ride Detail Sheet — opened by tapping the route preview on a post card
// ─────────────────────────────────────────────────────────────────────────────
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RideDetailSheet(
        post: PostItem,
        onDismiss: () -> Unit
    ) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val stats = post.rideStats

        val distanceKm  = (stats?.get("distanceKm")  as? Double) ?: 0.0
        val durationSec = (stats?.get("durationSec") as? Number)?.toLong() ?: 0L
        val avgSpeed    = (stats?.get("avgSpeedKmh") as? Double) ?: 0.0
        val maxSpeed    = (stats?.get("maxSpeedKmh") as? Double) ?: 0.0
        val elevation   = (stats?.get("elevationM")  as? Double) ?: 0.0
        val hasStats    = stats != null

        fun formatDuration(sec: Long): String {
            val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
            return if (h > 0) "${h}h ${m}m" else if (m > 0) "${m}m ${s}s" else "${s}s"
        }

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState       = sheetState,
            containerColor   = BgSurface,
            shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    Modifier.padding(top = 12.dp, bottom = 8.dp)
                        .width(40.dp).height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(DividerColor)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Header ────────────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                                .background(Green50),
                            Alignment.Center
                        ) {
                            Icon(Icons.Default.Route, null,
                                tint = Green900, modifier = Modifier.size(20.dp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Ride Details", fontWeight = FontWeight.Bold,
                                fontSize = 16.sp, color = TextPrimary)
                            Text(
                                "by ${post.displayName.ifBlank { post.userName }}",
                                fontSize = 12.sp, color = TextMuted
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close",
                            tint = TextMuted, modifier = Modifier.size(20.dp))
                    }
                }

                HorizontalDivider(color = DividerColor)

                // ── Route map ─────────────────────────────────────────────────────
                val points = post.polyline.mapNotNull { pt ->
                    val lat = pt["lat"] ?: return@mapNotNull null
                    val lon = pt["lon"] ?: return@mapNotNull null
                    Pair(lat, lon)
                }

                if (post.routeImageUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, DividerColor, RoundedCornerShape(16.dp))
                    ) {
                        AsyncImage(
                            model = post.routeImageUrl,
                            contentDescription = "Route map",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Route label overlay
                        Box(
                            Modifier.align(Alignment.TopStart).padding(10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Green900.copy(alpha = 0.85f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Route", fontSize = 10.sp,
                                fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                } else if (points.size >= 2) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, DividerColor, RoundedCornerShape(16.dp))
                            .background(Color(0xFFF0F4F0))
                    ) {
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val minLat = points.minOf { it.first }
                            val maxLat = points.maxOf { it.first }
                            val minLon = points.minOf { it.second }
                            val maxLon = points.maxOf { it.second }
                            val latRange = (maxLat - minLat).takeIf { it > 0 } ?: 0.0001
                            val lonRange = (maxLon - minLon).takeIf { it > 0 } ?: 0.0001
                            val padding = 40f

                            fun toX(lon: Double) = (padding + ((lon - minLon) / lonRange) * (size.width - padding * 2)).toFloat()
                            fun toY(lat: Double) = (padding + ((maxLat - lat) / latRange) * (size.height - padding * 2)).toFloat()

                            val path = androidx.compose.ui.graphics.Path()
                            points.forEachIndexed { i, (lat, lon) ->
                                if (i == 0) path.moveTo(toX(lon), toY(lat))
                                else path.lineTo(toX(lon), toY(lat))
                            }
                            drawPath(
                                path = path,
                                color = Color(0xFF00B464),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 8f,
                                    cap   = androidx.compose.ui.graphics.StrokeCap.Round,
                                    join  = androidx.compose.ui.graphics.StrokeJoin.Round
                                )
                            )
                            points.firstOrNull()?.let { (lat, lon) ->
                                drawCircle(Color(0xFFFFD600), 12f, androidx.compose.ui.geometry.Offset(toX(lon), toY(lat)))
                                drawCircle(Color.White, 6f, androidx.compose.ui.geometry.Offset(toX(lon), toY(lat)))
                            }
                            points.lastOrNull()?.let { (lat, lon) ->
                                drawCircle(Color(0xFFD32F2F), 12f, androidx.compose.ui.geometry.Offset(toX(lon), toY(lat)))
                                drawCircle(Color.White, 6f, androidx.compose.ui.geometry.Offset(toX(lon), toY(lat)))
                            }
                        }
                        Box(
                            Modifier.align(Alignment.TopStart).padding(10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Green900.copy(alpha = 0.85f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Route", fontSize = 10.sp,
                                fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                // ── Stats ─────────────────────────────────────────────────────────
                if (!hasStats) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF3F4F6))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.BarChart, null,
                            tint = TextMuted, modifier = Modifier.size(20.dp))
                        Text("Stats not available for this ride",
                            fontSize = 13.sp, color = TextMuted,
                            fontWeight = FontWeight.Medium)
                    }
                } else {
                    // Primary stats — distance + duration
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Green900),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(String.format("%.2f", distanceKm),
                                    fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                                    color = Color.White)
                                Text("km", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                Text("Distance", fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontWeight = FontWeight.Medium)
                            }
                            Box(Modifier.width(1.dp).height(48.dp)
                                .background(Color.White.copy(alpha = 0.2f)))
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(formatDuration(durationSec),
                                    fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                                    color = Color.White)
                                Text("time", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                Text("Duration", fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // Secondary stats — speed + elevation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(
                            Triple("Avg Speed", String.format("%.1f", avgSpeed), "km/h"),
                            Triple("Max Speed", String.format("%.1f", maxSpeed), "km/h"),
                            Triple("Elevation", String.format("%.0f", elevation),  "m ↑")
                        ).forEach { (label, value, unit) ->
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Green50),
                                elevation = CardDefaults.cardElevation(0.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(value, fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold, color = Green900)
                                    Text(unit, fontSize = 10.sp, color = TextMuted)
                                    Text(label, fontSize = 10.sp,
                                        color = TextMuted, fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    // Profanity filtering delegated to ProfanityFilter singleton — see ProfanityFilter.kt

    fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return "Just now"
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60_000     -> "Just now"
            diff < 3_600_000  -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            else -> {
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val postYear    = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.YEAR)
                if (postYear == currentYear) SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
                else SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }