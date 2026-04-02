    package com.darkhorses.PedalConnect.ui.theme
    
    import android.widget.Toast
    import androidx.compose.animation.core.animateFloat
    import androidx.compose.animation.core.rememberInfiniteTransition
    import androidx.compose.foundation.background
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
    import androidx.compose.animation.core.rememberInfiniteTransition
    import androidx.compose.animation.core.animateFloat
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
    data class PostItem(
        val id: String            = "",
        val userName: String      = "",
        val displayName: String   = "",
        val description: String   = "",
        val activity: String      = "",
        val distance: String      = "",
        val timestamp: Long       = 0,
        val likes: Int            = 0,
        val comments: Int         = 0,
        val likedBy: List<String> = emptyList(),
        val status: String        = "",
        val imageUrl: String      = "",
        val imageDeleteUrl: String = ""
    )
    
    data class NotificationItem(
        val id: String       = "",
        val message: String  = "",
        val type: String     = "",
        val timestamp: Long  = 0,
        val read: Boolean    = false,
        val alertId: String? = null
    )
    
    data class CommentItem(
        val id: String       = "",
        val userName: String = "",
        val text: String     = "",
        val timestamp: Long  = 0
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
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Homepage(
        navController: NavController,
        paddingValues: PaddingValues,
        userName: String,
        userLat: Double = 14.5995,
        userLon: Double = 120.9842,
        onExploreRides: (eventId: String?) -> Unit = {},
        isAdmin: Boolean = false
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
        val userPhotoCache = remember { mutableStateMapOf<String, String>() }

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
                }
        }

        // Pre-fetch photos for all visible post authors
        LaunchedEffect(posts.size) {
            val uniqueNames = posts.map { it.userName }.distinct()
                .filter { it !in userPhotoCache }
            for (name in uniqueNames) {
                db.collection("users").whereEqualTo("username", name)
                    .limit(1).get()
                    .addOnSuccessListener { snap ->
                        val url = snap.documents.firstOrNull()?.getString("photoUrl")
                        if (url != null) userPhotoCache[name] = url
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
                    unreadCount = notifications.size
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
                                        .mapNotNull { it.toObject(PostItem::class.java)?.copy(id = it.id) }
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
                            doc.toObject(PostItem::class.java)?.copy(
                                id          = doc.id,
                                displayName = doc.getString("displayName") ?: ""
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
                            doc.toObject(PostItem::class.java)?.copy(
                                id          = doc.id,
                                displayName = doc.getString("displayName") ?: ""
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
                            doc.toObject(CommentItem::class.java)?.copy(id = doc.id)?.let { postComments.add(it) }
                        }
                    }
            } else {
                commentsReg?.remove(); commentsReg = null
                postComments.clear(); commentText = ""
            }
        }
    
        // Actions
        fun saveEdit() {
            val post = editingPost ?: return
            if (editDescription.isBlank()) return
            isSavingEdit = true
            db.collection("posts").document(post.id).update("description", editDescription.trim())
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
            if (post.likedBy.contains(userName))
                ref.update("likedBy", FieldValue.arrayRemove(userName), "likes", FieldValue.increment(-1))
            else
                ref.update("likedBy", FieldValue.arrayUnion(userName), "likes", FieldValue.increment(1))
        }
    
        fun submitComment() {
            if (commentText.isBlank()) return
            db.collection("posts").document(selectedPostId).collection("comments")
                .add(hashMapOf("userName" to userName, "text" to commentText.trim(), "timestamp" to System.currentTimeMillis()))
                .addOnSuccessListener {
                    db.collection("posts").document(selectedPostId).update("comments", FieldValue.increment(1))
                    commentText = ""; focusManager.clearFocus()
                    Toast.makeText(context, "Comment added!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { Toast.makeText(context, "Failed to post comment.", Toast.LENGTH_SHORT).show() }
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
                                focusedLabelColor = Green700, cursorColor = Green700),
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
            ModalBottomSheet(
                onDismissRequest = { showCommentsSheet = false; commentsReg?.remove(); commentsReg = null; postComments.clear(); commentText = "" },
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
                        Text("Comments", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        if (postComments.isNotEmpty()) {
                            Text("${postComments.size}", fontSize = 13.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                        }
                    }
                    HorizontalDivider(color = DividerColor)
                    Spacer(Modifier.height(8.dp))
    
                    // Comments list
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
                        items(postComments, key = { it.id }) { comment ->
                            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                                // Avatar
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
                                Column(
                                    Modifier.background(Color(0xFFF3F4F6), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 9.dp).weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(comment.userName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                                    Text(comment.text, fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp)
                                }
                            }
                        }
                    }
    
                    HorizontalDivider(color = DividerColor)
                    Spacer(Modifier.height(8.dp))
    
                    // Comment input row
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(Modifier.size(34.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Green900, Green700))),
                            Alignment.Center) {
                            Text(userInitials(userName), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        OutlinedTextField(
                            value = commentText, onValueChange = { commentText = it },
                            placeholder = { Text("Add a comment…", fontSize = 13.sp, color = TextMuted) },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp), maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Green700, unfocusedBorderColor = BorderDefault, cursorColor = Green700),
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                        )
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (commentText.isBlank()) Color(0xFFF3F4F6) else Green900)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { submitComment() },
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
        onLike: () -> Unit = {}, onComment: () -> Unit = {},
        onEdit: () -> Unit = {}, onDelete: () -> Unit = {},
        photoUrl: String? = null,
        isAdmin: Boolean = false
    ) {
        val isOwner      = post.userName == currentUser
        var menuExpanded by remember { mutableStateOf(false) }
        var expanded     by remember { mutableStateOf(false) }
        val isLiked      = post.likedBy.contains(currentUser)
        var hasReported  by remember { mutableStateOf(false) }

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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Activity badge
                            Box(
                                Modifier.clip(RoundedCornerShape(6.dp)).background(Green50)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(post.activity, fontSize = 11.sp, color = Green800, fontWeight = FontWeight.Medium)
                            }
                            // Distance
                            if (post.distance.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Icon(Icons.Default.Route, null, tint = TextMuted, modifier = Modifier.size(11.dp))
                                    val cleanDist = post.distance.trim().removeSuffix("km").removeSuffix("KM").trim()
                                    Text("$cleanDist km", fontSize = 11.sp, color = TextMuted)
                                }
                            }
                            // Timestamp
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Icon(Icons.Default.Schedule, null, tint = TextMuted, modifier = Modifier.size(11.dp))
                                Text(formatTimestamp(post.timestamp), fontSize = 11.sp, color = TextMuted)
                            }
                        }
                        // Pending badge
                        if (post.status == "pending") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.HourglassTop, null, tint = Amber500, modifier = Modifier.size(12.dp))
                                Text("Pending approval", color = Amber500, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    // Action: owner menu (edit/delete) or non-owner menu (report) — hidden for admin
                    if (!isAdmin && (isOwner || post.status == "accepted")) {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, "Options", tint = TextMuted)
                            }
                            DropdownMenu(
                                expanded = menuExpanded, onDismissRequest = { menuExpanded = false },
                                containerColor = BgSurface, shape = RoundedCornerShape(14.dp)
                            ) {
                                if (isOwner) {
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
                                                    "postId"     to post.id,
                                                    "reportedBy" to currentUser,
                                                    "userName"   to post.userName,
                                                    "timestamp"  to System.currentTimeMillis()
                                                )).addOnSuccessListener {
                                                    hasReported = true
                                                    // Check total report count
                                                    db.collection("reportedPosts")
                                                        .whereEqualTo("postId", post.id)
                                                        .get()
                                                        .addOnSuccessListener { snap ->
                                                            val count = snap.size()
                                                            if (count >= 3) {
                                                                // Auto-hide post
                                                                db.collection("posts")
                                                                    .document(post.id)
                                                                    .update("status", "hidden")
                                                                // Notify admin
                                                                db.collection("notifications").add(hashMapOf(
                                                                    "userName"  to "Admin",
                                                                    "message"   to "⚠️ Post by ${post.userName} was auto-hidden after $count reports.",
                                                                    "type"      to "alert",
                                                                    "timestamp" to System.currentTimeMillis(),
                                                                    "read"      to false
                                                                ))
                                                            } else {
                                                                // Notify admin of new report
                                                                db.collection("notifications").add(hashMapOf(
                                                                    "userName"  to "Admin",
                                                                    "message"   to "🚩 $currentUser reported a post by ${post.userName}. ($count/3 reports)",
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
    
                HorizontalDivider(Modifier.padding(horizontal = 14.dp), thickness = 0.5.dp, color = DividerColor)
    
                // ── Action bar ────────────────────────────────────────────────────
                val isAdminViewing = isAdmin
                val canLike = !isAdminViewing && post.status != "pending" && post.userName != currentUser

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    FeedActionButton(
                        icon  = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        label = if (post.likes > 0) post.likes.toString() else "Like",
                        tint  = when {
                            !canLike -> TextMuted.copy(alpha = 0.4f)
                            isLiked  -> Color(0xFFEF4444)
                            else     -> TextMuted
                        },
                        onClick = { if (canLike) onLike() }
                    )
                    FeedActionButton(
                        icon = Icons.Default.ChatBubbleOutline,
                        label = if (post.comments > 0) post.comments.toString() else "Comment",
                        tint = if (post.status == "pending" || isAdminViewing) TextMuted.copy(alpha = 0.4f) else TextMuted,
                        onClick = { if (post.status != "pending" && !isAdminViewing) onComment() }
                    )
                    val shareContext = LocalContext.current
                    FeedActionButton(
                        icon = Icons.Default.Share,
                        label = "Share",
                        tint = if (post.status == "pending" || isAdminViewing) TextMuted.copy(alpha = 0.4f) else TextMuted
                    ) {
                        if (post.status != "pending" && !isAdminViewing) {
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