package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.*

// ── Design tokens ─────────────────────────────────────────────────────────────
private val PPGreen950  = Color(0xFF052818)
private val PPGreen900  = Color(0xFF06402B)
private val PPGreen800  = Color(0xFF0A5C3D)
private val PPGreen700  = Color(0xFF0D7050)
private val PPGreen100  = Color(0xFFDDF1E8)
private val PPGreen50   = Color(0xFFE8F5EE)
private val PPBgCanvas  = Color(0xFFF5F7F6)
private val PPBgSurface = Color(0xFFFFFFFF)
private val PPTextPrimary   = Color(0xFF111827)
private val PPTextSecondary = Color(0xFF374151)
private val PPTextMuted     = Color(0xFF6B7280)
private val PPDivider       = Color(0xFFE5E7EB)
private val PPAmber500  = Color(0xFFF59E0B)

// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    navController   : NavController,
    targetUserName  : String,
    currentUserName : String
) {
    val db = FirebaseFirestore.getInstance()

    // ── Profile state ─────────────────────────────────────────────────────────
    var displayName  by remember { mutableStateOf("") }
    var bio          by remember { mutableStateOf<String?>(null) }
    var bikeTypes    by remember { mutableStateOf<List<String>>(emptyList()) }
    var skillLevel   by remember { mutableStateOf<String?>(null) }
    var photoUrl     by remember { mutableStateOf<String?>(null) }
    var isLoading    by remember { mutableStateOf(true) }

    // ── Tab + content state ───────────────────────────────────────────────────
    var subTab       by remember { mutableIntStateOf(0) }
    val posts        = remember { mutableStateListOf<Post>() }
    val events       = remember { mutableStateListOf<JoinedEvent>() }
    var isLoadingPosts  by remember { mutableStateOf(true) }
    var isLoadingEvents by remember { mutableStateOf(true) }

    // ── Like overrides — optimistic updates ───────────────────────────────────
    val likeOverrides = remember { mutableStateMapOf<String, Boolean>() }

    // ── Comments sheet state ──────────────────────────────────────────────────
    val scope                = rememberCoroutineScope()
    val focusManager         = LocalFocusManager.current
    var showCommentsSheet    by remember { mutableStateOf(false) }
    var selectedPostId       by remember { mutableStateOf("") }
    var commentText          by remember { mutableStateOf("") }
    val postComments         = remember { mutableStateListOf<CommentItem>() }
    val commentsSheetState   = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val userPhotoCache       = remember { mutableStateMapOf<String, String>() }
    val userDisplayNameCache = remember { mutableStateMapOf<String, String>() }
    var commentsListener     by remember { mutableStateOf<com.google.firebase.firestore.ListenerRegistration?>(null) }
    var replyingTo           by remember { mutableStateOf<CommentItem?>(null) }
    var replyParentId        by remember { mutableStateOf<String?>(null) }

    // ── isAdmin — loaded from Firestore ───────────────────────────────────────
    var isAdmin by remember { mutableStateOf(false) }
    LaunchedEffect(currentUserName) {
        db.collection("users").whereEqualTo("username", currentUserName)
            .limit(1).get()
            .addOnSuccessListener { snap ->
                isAdmin = snap.documents.firstOrNull()?.getBoolean("isAdmin") ?: false
            }
    }

    // ── Comment editing state ─────────────────────────────────────────────────
    var editingComment    by remember { mutableStateOf<CommentItem?>(null) }
    var editCommentText   by remember { mutableStateOf("") }
    var deletingComment   by remember { mutableStateOf<CommentItem?>(null) }
    var contextMenuComment by remember { mutableStateOf<CommentItem?>(null) }
    val contextSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showCommentReasonDialog   by remember { mutableStateOf(false) }
    var commentReasonTarget       by remember { mutableStateOf<CommentItem?>(null) }
    var selectedCommentReason     by remember { mutableStateOf("") }
    var otherCommentReasonText    by remember { mutableStateOf("") }
    var commentReasonError        by remember { mutableStateOf<String?>(null) }

    var showHideCommentReasonDialog  by remember { mutableStateOf(false) }
    var hideCommentReasonTarget      by remember { mutableStateOf<CommentItem?>(null) }
    var selectedHideCommentReason    by remember { mutableStateOf("") }
    var otherHideCommentReasonText   by remember { mutableStateOf("") }
    var hideCommentReasonError       by remember { mutableStateOf<String?>(null) }

    var showCommentReportDialog      by remember { mutableStateOf(false) }
    var commentReportTarget          by remember { mutableStateOf<CommentItem?>(null) }
    var selectedCommentReportReason  by remember { mutableStateOf("") }
    var otherCommentReportReasonText by remember { mutableStateOf("") }
    var commentReportReasonError     by remember { mutableStateOf<String?>(null) }

    // ── Selected event for detail sheet ──────────────────────────────────────
    var selectedEvent by remember { mutableStateOf<RideEvent?>(null) }

    // ── Saved routes sheet ────────────────────────────────────────────────────
    val myRides          = remember { mutableStateListOf<SavedRide>() }
    var isLoadingRides   by remember { mutableStateOf(true) }
    var showRidesSheet   by remember { mutableStateOf(false) }
    val ridesSheetState  = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ── Stats ─────────────────────────────────────────────────────────────────
    var totalPostCount  by remember { mutableIntStateOf(0) }
    var totalEventCount by remember { mutableIntStateOf(0) }
    val totalLikesCount by remember {
        derivedStateOf {
            posts.sumOf { post ->
                val wasLikedInFirestore = post.likedBy.contains(currentUserName)
                val isLiked = likeOverrides[post.id] ?: wasLikedInFirestore
                when {
                    isLiked && !wasLikedInFirestore -> post.likes + 1
                    !isLiked && wasLikedInFirestore -> post.likes - 1
                    else                            -> post.likes
                }
            }
        }
    }


    // ── Load profile ──────────────────────────────────────────────────────────
    LaunchedEffect(targetUserName) {
        try {
            val snap = db.collection("users")
                .whereEqualTo("username", targetUserName)
                .limit(1).get().await()
            val doc = snap.documents.firstOrNull()
            if (doc != null) {
                displayName = doc.getString("displayName")?.takeIf { it.isNotBlank() }
                    ?: targetUserName
                bio         = doc.getString("bio")
                bikeTypes   = when (val raw = doc.get("bikeTypes")) {
                    is List<*> -> raw.filterIsInstance<String>()
                    else -> {
                        val legacy = doc.getString("bikeType") ?: ""
                        if (legacy.isNotBlank()) listOf(legacy) else emptyList()
                    }
                }
                skillLevel  = doc.getString("skillLevel")
                photoUrl    = doc.getString("photoUrl")
            }
        } catch (_: Exception) { }
        isLoading = false
    }

    // ── Load posts ────────────────────────────────────────────────────────────
    LaunchedEffect(targetUserName) {
        db.collection("posts")
            .whereEqualTo("userName", targetUserName)
            .whereEqualTo("status", "accepted")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { isLoadingPosts = false; return@addSnapshotListener }
                posts.clear()
                for (doc in snap.documents) {
                    val poly = doc.safePolyline()
                    posts.add(Post(
                        id            = doc.id,
                        userName      = doc.getString("userName")      ?: targetUserName,
                        displayName   = doc.getString("displayName")   ?: "",
                        description   = doc.getString("description")   ?: "",
                        activity      = doc.getString("activity")      ?: "Cycling Ride",
                        distance      = doc.getString("distance")      ?: "0",
                        timestamp     = doc.getLong("timestamp")       ?: 0L,
                        likes         = (doc.getLong("likes") ?: 0L).toInt(),
                        status        = doc.getString("status")        ?: "accepted",
                        imageUrl      = doc.getString("imageUrl")      ?: "",
                        imageDeleteUrl = doc.getString("imageDeleteUrl") ?: "",
                        polyline      = poly,
                        routeImageUrl = doc.getString("routeImageUrl") ?: "",
                        editedAt      = doc.getLong("editedAt")        ?: 0L,
                        likedBy       = (doc.get("likedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        comments      = (doc.getLong("comments") ?: 0L).toInt(),
                        rideStats     = doc.get("rideStats") as? Map<String, Any>
                    ))
                }
                totalPostCount  = posts.size
                isLoadingPosts  = false
            }
    }

    // ── Load events ───────────────────────────────────────────────────────────
    LaunchedEffect(targetUserName) {
        // Firestore doesn't support OR queries on different fields in one snapshot,
        // so we run two listeners and merge the results.
        val merged = mutableMapOf<String, JoinedEvent>()

        fun buildJoinedEvent(doc: com.google.firebase.firestore.DocumentSnapshot): JoinedEvent? {
            if (doc.getString("status") == "rejected") return null
            return JoinedEvent(
                id          = doc.id,
                title       = doc.getString("title")       ?: "Unnamed Ride",
                route       = doc.getString("route")       ?: "",
                date        = doc.getLong("date")          ?: 0L,
                time        = doc.getString("time")        ?: "",
                difficulty  = doc.getString("difficulty")  ?: "Easy",
                distanceKm  = doc.getDouble("distanceKm") ?: 0.0,
                isOrganizer = doc.getString("organizer")   == targetUserName,
                status      = doc.getString("status")      ?: "approved"
            )
        }

        fun pushUpdates() {
            val now = System.currentTimeMillis()
            val loaded = merged.values.toList()
            events.clear()
            events.addAll(
                loaded.sortedWith(
                    compareBy<JoinedEvent> { it.date < now }.thenByDescending { it.date }
                )
            )
            totalEventCount = loaded.size
            isLoadingEvents = false
        }

        // Query 1 — events where user is a participant
        db.collection("rideEvents")
            .whereArrayContains("participants", targetUserName)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { isLoadingEvents = false; return@addSnapshotListener }
                for (doc in snap.documents) {
                    val event = buildJoinedEvent(doc) ?: continue
                    merged[doc.id] = event
                }
                pushUpdates()
            }

        // Query 2 — events where user is the organizer (may not be in participants array)
        db.collection("rideEvents")
            .whereEqualTo("organizer", targetUserName)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                for (doc in snap.documents) {
                    val event = buildJoinedEvent(doc) ?: continue
                    merged[doc.id] = event
                }
                pushUpdates()
            }
    }

    LaunchedEffect(targetUserName) {
        db.collection("savedRoutes").whereEqualTo("userName", targetUserName)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { isLoadingRides = false; return@addSnapshotListener }
                myRides.clear()
                for (doc in snap.documents) {
                    myRides.add(SavedRide(
                        id          = doc.id,
                        name        = doc.getString("name")        ?: "Unnamed Route",
                        distanceKm  = doc.getDouble("distanceKm") ?: 0.0,
                        durationMin = doc.getLong("durationMin")   ?: 0L,
                        avgSpeedKmh = doc.getDouble("avgSpeedKmh") ?: 0.0,
                        maxSpeedKmh = doc.getDouble("maxSpeedKmh") ?: 0.0,
                        elevationM  = doc.getDouble("elevationM")  ?: 0.0,
                        timesRidden = (doc.getLong("timesRidden")  ?: 1L).toInt(),
                        lastRidden  = doc.getString("lastRidden")  ?: "",
                        timestamp   = doc.getLong("timestamp")     ?: 0L
                    ))
                }
                isLoadingRides = false
            }
    }

    fun formatEventDate(ts: Long): String =
        if (ts == 0L) "Date TBA"
        else SimpleDateFormat("EEE, MMM d · yyyy", Locale.getDefault()).format(Date(ts))

    // ── Event detail sheet ────────────────────────────────────────────────────
    selectedEvent?.let { event ->
        EventDetailSheet(
            event               = event,
            userName            = currentUserName,
            onJoin              = {
                val ref = db.collection("rideEvents").document(event.id)
                if (event.participants.contains(currentUserName)) {
                    ref.update("participants",
                        com.google.firebase.firestore.FieldValue.arrayRemove(currentUserName))
                } else {
                    ref.update("participants",
                        com.google.firebase.firestore.FieldValue.arrayUnion(currentUserName))
                }
            },
            onDelete            = {},
            onEdit              = {},
            onCheckIn           = {
                db.collection("rideEvents").document(event.id)
                    .update("attendees",
                        com.google.firebase.firestore.FieldValue.arrayUnion(currentUserName))
                    .addOnSuccessListener {
                        db.collection("rideEvents").document(event.id)
                            .get()
                            .addOnSuccessListener { doc ->
                                if (doc != null && doc.exists()) {
                                    selectedEvent = RideEvent(
                                        id              = doc.id,
                                        title           = doc.getString("title")           ?: "",
                                        description     = doc.getString("description")     ?: "",
                                        route           = doc.getString("route")           ?: "",
                                        date            = doc.getLong("date")              ?: 0L,
                                        time            = doc.getString("time")            ?: "",
                                        organizer       = doc.getString("organizer")       ?: "",
                                        participants    = (doc.get("participants") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                                        maxParticipants = (doc.getLong("maxParticipants")  ?: 0L).toInt(),
                                        difficulty      = doc.getString("difficulty")      ?: "Easy",
                                        distanceKm      = doc.getDouble("distanceKm")      ?: 0.0,
                                        attendees       = (doc.get("attendees") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                                        checkInOpen     = doc.getBoolean("checkInOpen")    ?: false,
                                        status          = doc.getString("status")          ?: "approved",
                                        durationHours   = (doc.getLong("durationHours")    ?: 0L).toInt(),
                                        isEdited        = doc.getBoolean("isEdited")       ?: false,
                                        editedAt        = doc.getLong("editedAt")          ?: 0L
                                    )
                                }
                            }
                    }
            },
            onToggleAttendance  = {},
            onToggleCheckInOpen = {},
            onDismiss           = { selectedEvent = null },
            onNavigate          = { destination ->
                val ctx = navController.context
                val prefs = ctx.getSharedPreferences(
                    "PedalConnectPrefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putString("pending_destination", destination).apply()
                navController.navigate("home/$currentUserName") {
                    popUpTo("home/$currentUserName") { inclusive = false }
                }
            },
            onViewProfile       = { targetUser ->
                if (targetUser != currentUserName)
                    navController.navigate("public_profile/$targetUser")
            }
        )
    }

    // ── Comments sheet dialogs ────────────────────────────────────────────────
    if (editingComment != null) {
        Dialog(onDismissRequest = { editingComment = null }) {
            Card(
                shape  = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = PPBgSurface),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(PPGreen50), Alignment.Center) {
                            Icon(Icons.Default.Edit, null, tint = PPGreen900, modifier = Modifier.size(20.dp))
                        }
                        Text("Edit Comment", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PPTextPrimary)
                    }
                    HorizontalDivider(color = PPDivider)
                    OutlinedTextField(
                        value         = editCommentText,
                        onValueChange = { if (it.length <= 300) editCommentText = it },
                        modifier      = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                        shape         = RoundedCornerShape(12.dp),
                        maxLines      = 5,
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = PPGreen700,
                            unfocusedBorderColor = PPDivider,
                            cursorColor          = PPGreen700,
                            focusedTextColor     = PPTextPrimary,
                            unfocusedTextColor   = PPTextPrimary
                        )
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick   = { editingComment = null },
                            modifier  = Modifier.weight(1f).height(48.dp),
                            shape     = RoundedCornerShape(12.dp),
                            border    = androidx.compose.foundation.BorderStroke(1.dp, PPDivider),
                            colors    = ButtonDefaults.outlinedButtonColors(contentColor = PPTextSecondary)
                        ) { Text("Cancel", fontWeight = FontWeight.Medium) }
                        Button(
                            onClick  = {
                                val c = editingComment ?: return@Button
                                if (editCommentText.isBlank()) return@Button
                                db.collection("posts").document(selectedPostId)
                                    .collection("comments").document(c.id)
                                    .update("text", editCommentText.trim(), "editedAt", System.currentTimeMillis())
                                    .addOnSuccessListener { editingComment = null }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape    = RoundedCornerShape(12.dp),
                            enabled  = editCommentText.isNotBlank(),
                            colors   = ButtonDefaults.buttonColors(containerColor = PPGreen900, contentColor = Color.White)
                        ) { Text("Save", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }

    if (deletingComment != null) {
        AlertDialog(
            onDismissRequest = { deletingComment = null },
            shape            = RoundedCornerShape(20.dp),
            containerColor   = PPBgSurface,
            icon = {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Color(0xFFFEF2F2)), Alignment.Center) {
                    Icon(Icons.Default.DeleteForever, null, tint = Color(0xFFDC2626), modifier = Modifier.size(28.dp))
                }
            },
            title = { Text("Delete comment?", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PPTextPrimary, textAlign = TextAlign.Center) },
            text  = { Text("This will permanently remove your comment.", fontSize = 14.sp, color = PPTextSecondary, textAlign = TextAlign.Center) },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick  = {
                            val c = deletingComment ?: return@Button
                            db.collection("posts").document(selectedPostId)
                                .collection("comments").document(c.id).delete()
                                .addOnSuccessListener {
                                    db.collection("posts").document(selectedPostId)
                                        .update("comments", com.google.firebase.firestore.FieldValue.increment(-1))
                                    if (isAdmin && c.userName != currentUserName) {
                                        db.collection("notifications").add(hashMapOf(
                                            "userName"  to c.userName,
                                            "message"   to "Your comment was permanently removed by an admin for violating community guidelines.",
                                            "type"      to "moderation",
                                            "timestamp" to System.currentTimeMillis(),
                                            "read"      to false
                                        ))
                                    }
                                    deletingComment = null
                                }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White)
                    ) { Text("Delete", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
                    OutlinedButton(
                        onClick  = { deletingComment = null },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(14.dp),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, PPDivider),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = PPTextSecondary)
                    ) { Text("Cancel", fontWeight = FontWeight.Medium, fontSize = 15.sp) }
                }
            }
        )
    }

    // Admin delete reason dialog
    if (showCommentReasonDialog && commentReasonTarget != null) {
        val commentDeleteReasons = listOf("Swearing / Offensive Language", "Harassment", "Spam", "Personal Attack", "Inappropriate Content", "Other")
        AlertDialog(
            onDismissRequest = {
                showCommentReasonDialog = false; commentReasonTarget = null
                selectedCommentReason = ""; otherCommentReasonText = ""; commentReasonError = null
            },
            shape          = RoundedCornerShape(20.dp),
            containerColor = PPBgSurface,
            icon = {
                Box(Modifier.size(56.dp).background(Color(0xFFFFEBEE), CircleShape), Alignment.Center) {
                    Icon(Icons.Default.DeleteForever, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(28.dp))
                }
            },
            title = { Text("Delete Comment", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = PPTextPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Select a reason for removing this comment:", fontSize = 13.sp, color = PPTextSecondary)
                    commentDeleteReasons.forEach { reason ->
                        val isSelected = selectedCommentReason == reason
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFFFFEBEE) else Color(0xFFF3F4F6))
                                .clickable { selectedCommentReason = reason; if (reason != "Other") otherCommentReasonText = ""; commentReasonError = null }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(Modifier.size(20.dp).clip(CircleShape).background(if (isSelected) Color(0xFFD32F2F) else Color(0xFFD1D5DB)), Alignment.Center) {
                                if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                            Text(reason, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, color = PPTextPrimary)
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = selectedCommentReason == "Other") {
                        OutlinedTextField(
                            value         = otherCommentReasonText,
                            onValueChange = { if (it.length <= 150) { otherCommentReasonText = it; commentReasonError = null } },
                            placeholder   = { Text("Please describe the issue…", fontSize = 13.sp, color = PPTextSecondary.copy(alpha = 0.5f)) },
                            singleLine    = false, maxLines = 3,
                            shape         = RoundedCornerShape(10.dp),
                            modifier      = Modifier.fillMaxWidth(),
                            colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFD32F2F), unfocusedBorderColor = PPDivider, focusedTextColor = PPTextPrimary, unfocusedTextColor = PPTextPrimary)
                        )
                    }
                    if (commentReasonError != null) Text(commentReasonError!!, fontSize = 12.sp, color = Color(0xFFD32F2F))
                }
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick  = {
                            if (selectedCommentReason.isBlank()) { commentReasonError = "Please select a reason."; return@Button }
                            if (selectedCommentReason == "Other" && otherCommentReasonText.trim().length < 10) { commentReasonError = "Please describe the issue (min 10 characters)."; return@Button }
                            val finalReason = if (selectedCommentReason == "Other") otherCommentReasonText.trim() else selectedCommentReason
                            val c = commentReasonTarget ?: return@Button
                            showCommentReasonDialog = false
                            db.collection("posts").document(selectedPostId).collection("comments").document(c.id).delete()
                                .addOnSuccessListener {
                                    db.collection("posts").document(selectedPostId).update("comments", com.google.firebase.firestore.FieldValue.increment(-1))
                                    db.collection("notifications").add(hashMapOf("userName" to c.userName, "message" to "Your comment was removed by an admin. Reason: $finalReason", "type" to "moderation", "timestamp" to System.currentTimeMillis(), "read" to false))
                                    commentReasonTarget = null; selectedCommentReason = ""; otherCommentReasonText = ""; commentReasonError = null
                                }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White)
                    ) { Text("Delete Comment", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    OutlinedButton(
                        onClick  = { showCommentReasonDialog = false; commentReasonTarget = null; selectedCommentReason = ""; otherCommentReasonText = ""; commentReasonError = null },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape    = RoundedCornerShape(12.dp)
                    ) { Text("Cancel", color = PPTextSecondary, fontWeight = FontWeight.Medium) }
                }
            }
        )
    }

    // Admin hide reason dialog
    if (showHideCommentReasonDialog && hideCommentReasonTarget != null) {
        val commentHideReasons = listOf("Swearing / Offensive Language", "Harassment", "Spam", "Personal Attack", "Inappropriate Content", "Other")
        AlertDialog(
            onDismissRequest = {
                showHideCommentReasonDialog = false; hideCommentReasonTarget = null
                selectedHideCommentReason = ""; otherHideCommentReasonText = ""; hideCommentReasonError = null
            },
            shape          = RoundedCornerShape(20.dp),
            containerColor = PPBgSurface,
            icon = {
                Box(Modifier.size(56.dp).background(Color(0xFFFFFBEB), CircleShape), Alignment.Center) {
                    Icon(Icons.Default.VisibilityOff, null, tint = PPAmber500, modifier = Modifier.size(28.dp))
                }
            },
            title = { Text("Hide Comment", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = PPTextPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Select a reason for hiding this comment:", fontSize = 13.sp, color = PPTextSecondary)
                    commentHideReasons.forEach { reason ->
                        val isSelected = selectedHideCommentReason == reason
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFFFFFBEB) else Color(0xFFF3F4F6))
                                .clickable { selectedHideCommentReason = reason; if (reason != "Other") otherHideCommentReasonText = ""; hideCommentReasonError = null }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(Modifier.size(20.dp).clip(CircleShape).background(if (isSelected) PPAmber500 else Color(0xFFD1D5DB)), Alignment.Center) {
                                if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                            Text(reason, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, color = PPTextPrimary)
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = selectedHideCommentReason == "Other") {
                        OutlinedTextField(
                            value         = otherHideCommentReasonText,
                            onValueChange = { if (it.length <= 150) { otherHideCommentReasonText = it; hideCommentReasonError = null } },
                            placeholder   = { Text("Please describe the issue…", fontSize = 13.sp, color = PPTextSecondary.copy(alpha = 0.5f)) },
                            singleLine    = false, maxLines = 3,
                            shape         = RoundedCornerShape(10.dp),
                            modifier      = Modifier.fillMaxWidth(),
                            colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = PPAmber500, unfocusedBorderColor = PPDivider, focusedTextColor = PPTextPrimary, unfocusedTextColor = PPTextPrimary)
                        )
                    }
                    if (hideCommentReasonError != null) Text(hideCommentReasonError!!, fontSize = 12.sp, color = Color(0xFFD32F2F))
                }
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick  = {
                            if (selectedHideCommentReason.isBlank()) { hideCommentReasonError = "Please select a reason."; return@Button }
                            if (selectedHideCommentReason == "Other" && otherHideCommentReasonText.trim().length < 10) { hideCommentReasonError = "Please describe the issue (min 10 characters)."; return@Button }
                            val finalReason = if (selectedHideCommentReason == "Other") otherHideCommentReasonText.trim() else selectedHideCommentReason
                            val c = hideCommentReasonTarget ?: return@Button
                            showHideCommentReasonDialog = false
                            db.collection("posts").document(selectedPostId).collection("comments").document(c.id)
                                .update("status", "hidden")
                                .addOnSuccessListener {
                                    db.collection("posts").document(selectedPostId).update("comments", com.google.firebase.firestore.FieldValue.increment(-1))
                                    db.collection("reportedComments").add(hashMapOf("commentId" to c.id, "postId" to selectedPostId, "userName" to c.userName, "text" to c.text, "reason" to finalReason, "reportedBy" to currentUserName, "source" to "admin", "timestamp" to System.currentTimeMillis()))
                                    db.collection("notifications").add(hashMapOf("userName" to c.userName, "message" to "Your comment was hidden by an admin. Reason: $finalReason", "type" to "moderation", "timestamp" to System.currentTimeMillis(), "read" to false))
                                    hideCommentReasonTarget = null; selectedHideCommentReason = ""; otherHideCommentReasonText = ""; hideCommentReasonError = null; contextMenuComment = null
                                }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = PPAmber500, contentColor = Color.White)
                    ) { Text("Hide Comment", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    OutlinedButton(
                        onClick  = { showHideCommentReasonDialog = false; hideCommentReasonTarget = null; selectedHideCommentReason = ""; otherHideCommentReasonText = ""; hideCommentReasonError = null },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape    = RoundedCornerShape(12.dp)
                    ) { Text("Cancel", color = PPTextSecondary, fontWeight = FontWeight.Medium) }
                }
            }
        )
    }

    // User report reason dialog
    if (showCommentReportDialog && commentReportTarget != null) {
        val reportReasons = listOf("Swearing / Offensive Language", "Harassment", "Spam", "Personal Attack", "Inappropriate Content", "Other")
        val c = commentReportTarget!!
        AlertDialog(
            onDismissRequest = {
                showCommentReportDialog = false; commentReportTarget = null
                selectedCommentReportReason = ""; otherCommentReportReasonText = ""; commentReportReasonError = null
            },
            shape          = RoundedCornerShape(20.dp),
            containerColor = PPBgSurface,
            icon = {
                Box(Modifier.size(56.dp).background(Color(0xFFFFF7ED), CircleShape), Alignment.Center) {
                    Icon(Icons.Default.Flag, null, tint = Color(0xFFEA580C), modifier = Modifier.size(28.dp))
                }
            },
            title = { Text("Report Comment", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = PPTextPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Why are you reporting this comment?", fontSize = 13.sp, color = PPTextSecondary)
                    reportReasons.forEach { reason ->
                        val isSelected = selectedCommentReportReason == reason
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFFFFF7ED) else Color(0xFFF3F4F6))
                                .clickable { selectedCommentReportReason = reason; if (reason != "Other") otherCommentReportReasonText = ""; commentReportReasonError = null }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(Modifier.size(20.dp).clip(CircleShape).background(if (isSelected) Color(0xFFEA580C) else Color(0xFFD1D5DB)), Alignment.Center) {
                                if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                            Text(reason, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, color = PPTextPrimary)
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = selectedCommentReportReason == "Other") {
                        OutlinedTextField(
                            value         = otherCommentReportReasonText,
                            onValueChange = { if (it.length <= 150) { otherCommentReportReasonText = it; commentReportReasonError = null } },
                            placeholder   = { Text("Please describe the issue…", fontSize = 13.sp, color = PPTextSecondary.copy(alpha = 0.5f)) },
                            singleLine    = false, maxLines = 3,
                            shape         = RoundedCornerShape(10.dp),
                            modifier      = Modifier.fillMaxWidth(),
                            colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFEA580C), unfocusedBorderColor = PPDivider, focusedTextColor = PPTextPrimary, unfocusedTextColor = PPTextPrimary)
                        )
                    }
                    if (commentReportReasonError != null) Text(commentReportReasonError!!, fontSize = 12.sp, color = Color(0xFFD32F2F))
                }
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick  = {
                            if (selectedCommentReportReason.isBlank()) { commentReportReasonError = "Please select a reason."; return@Button }
                            if (selectedCommentReportReason == "Other" && otherCommentReportReasonText.trim().length < 10) { commentReportReasonError = "Please describe the issue (min 10 characters)."; return@Button }
                            val finalReason = if (selectedCommentReportReason == "Other") otherCommentReportReasonText.trim() else selectedCommentReportReason
                            showCommentReportDialog = false
                            db.collection("reportedComments").add(hashMapOf("commentId" to c.id, "postId" to selectedPostId, "reportedBy" to currentUserName, "userName" to c.userName, "text" to c.text, "reason" to finalReason, "timestamp" to System.currentTimeMillis()))
                                .addOnSuccessListener {
                                    db.collection("reportedComments").whereEqualTo("commentId", c.id).get()
                                        .addOnSuccessListener { snap ->
                                            val count = snap.size()
                                            if (count >= 3) {
                                                db.collection("posts").document(selectedPostId).collection("comments").document(c.id).update("status", "hidden")
                                                db.collection("posts").document(selectedPostId).update("comments", com.google.firebase.firestore.FieldValue.increment(-1))
                                                db.collection("notifications").add(hashMapOf("userName" to c.userName, "message" to "Your comment was hidden by the community. Reason: $finalReason", "type" to "moderation", "timestamp" to System.currentTimeMillis(), "read" to false))
                                                db.collection("notifications").add(hashMapOf("userName" to "Admin", "message" to "⚠️ Comment auto-hidden after $count reports. Last reason: $finalReason", "type" to "alert", "timestamp" to System.currentTimeMillis(), "read" to false))
                                            } else {
                                                db.collection("notifications").add(hashMapOf("userName" to "Admin", "message" to "🚩 $currentUserName reported a comment. ($count/3 reports) Reason: $finalReason", "type" to "alert", "timestamp" to System.currentTimeMillis(), "read" to false))
                                            }
                                        }
                                    commentReportTarget = null; selectedCommentReportReason = ""; otherCommentReportReasonText = ""; commentReportReasonError = null
                                }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C), contentColor = Color.White)
                    ) { Text("Submit Report", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    OutlinedButton(
                        onClick  = { showCommentReportDialog = false; commentReportTarget = null; selectedCommentReportReason = ""; otherCommentReportReasonText = ""; commentReportReasonError = null },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape    = RoundedCornerShape(12.dp)
                    ) { Text("Cancel", color = PPTextSecondary, fontWeight = FontWeight.Medium) }
                }
            }
        )
    }

    // Long-press context sheet
    if (contextMenuComment != null) {
        val c = contextMenuComment!!
        ModalBottomSheet(
            onDismissRequest = { contextMenuComment = null },
            sheetState       = contextSheetState,
            containerColor   = PPBgSurface,
            shape            = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            dragHandle = {
                Box(Modifier.padding(top = 12.dp, bottom = 4.dp).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(PPDivider))
            }
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Preview bubble
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF3F4F6)).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Brush.linearGradient(listOf(PPGreen900, PPGreen700))),
                        Alignment.Center
                    ) {
                        val p = userPhotoCache[c.userName]
                        if (p != null) AsyncImage(model = p, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                        else Text(userInitials(c.userName), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(userDisplayNameCache[c.userName] ?: c.userName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = PPTextPrimary)
                        Text(c.text, fontSize = 13.sp, color = PPTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Edit — only comment owner
                if (c.userName == currentUserName) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                editingComment = c; editCommentText = c.text; contextMenuComment = null
                            }
                            .background(Color(0xFFF9FAFB)).padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(PPGreen50), Alignment.Center) {
                            Icon(Icons.Default.Edit, null, tint = PPGreen900, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Edit comment", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = PPTextPrimary)
                            Text("Change what you wrote", fontSize = 12.sp, color = PPTextMuted)
                        }
                    }
                }

                // Delete — comment owner
                if (c.userName == currentUserName) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                deletingComment = c; contextMenuComment = null
                            }
                            .background(Color(0xFFF9FAFB)).padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFEF2F2)), Alignment.Center) {
                            Icon(Icons.Default.DeleteForever, null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Delete comment", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFFDC2626))
                            Text("Remove permanently", fontSize = 12.sp, color = PPTextMuted)
                        }
                    }
                }

                // Report — non-owner, non-admin
                if (c.userName != currentUserName && !isAdmin) {
                    val isAlreadyReported = remember(c.id) { mutableStateOf(false) }
                    LaunchedEffect(c.id) {
                        db.collection("reportedComments").whereEqualTo("commentId", c.id).whereEqualTo("reportedBy", currentUserName).limit(1).get()
                            .addOnSuccessListener { snap -> isAlreadyReported.value = !snap.isEmpty }
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !isAlreadyReported.value) {
                                if (!isAlreadyReported.value) {
                                    commentReportTarget = c; selectedCommentReportReason = ""; otherCommentReportReasonText = ""; commentReportReasonError = null
                                    showCommentReportDialog = true; contextMenuComment = null
                                }
                            }
                            .background(Color(0xFFF9FAFB)).padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (isAlreadyReported.value) Color(0xFFF3F4F6) else Color(0xFFFFF7ED)), Alignment.Center) {
                            Icon(Icons.Default.Flag, null, tint = if (isAlreadyReported.value) PPTextMuted else Color(0xFFEA580C), modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(if (isAlreadyReported.value) "Already reported" else "Report comment", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (isAlreadyReported.value) PPTextMuted else Color(0xFFEA580C))
                            if (isAlreadyReported.value) Text("You've already flagged this", fontSize = 12.sp, color = PPTextMuted)
                        }
                    }
                }

                // Admin options — hide/restore + delete
                if (c.userName != currentUserName && isAdmin) {
                    if (!c.isHidden) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    hideCommentReasonTarget = c; selectedHideCommentReason = ""; otherHideCommentReasonText = ""; hideCommentReasonError = null
                                    showHideCommentReasonDialog = true
                                }
                                .background(Color(0xFFF9FAFB)).padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFFBEB)), Alignment.Center) {
                                Icon(Icons.Default.VisibilityOff, null, tint = PPAmber500, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("Hide comment", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = PPTextPrimary)
                                Text("Remove from view, keeps data", fontSize = 12.sp, color = PPTextMuted)
                            }
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    db.collection("posts").document(selectedPostId).collection("comments").document(c.id)
                                        .update("status", "visible")
                                        .addOnSuccessListener {
                                            db.collection("posts").document(selectedPostId).update("comments", com.google.firebase.firestore.FieldValue.increment(1))
                                            db.collection("notifications").add(hashMapOf("userName" to c.userName, "message" to "✅ Your comment has been restored by an admin.", "type" to "moderation_restored", "timestamp" to System.currentTimeMillis(), "read" to false))
                                            contextMenuComment = null
                                        }
                                }
                                .background(Color(0xFFF9FAFB)).padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(PPGreen50), Alignment.Center) {
                                Icon(Icons.Default.Visibility, null, tint = PPGreen900, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("Restore comment", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = PPGreen900)
                                Text("Make visible again", fontSize = 12.sp, color = PPTextMuted)
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                commentReasonTarget = c; selectedCommentReason = ""; otherCommentReasonText = ""; commentReasonError = null
                                showCommentReasonDialog = true; contextMenuComment = null
                            }
                            .background(Color(0xFFF9FAFB)).padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFEF2F2)), Alignment.Center) {
                            Icon(Icons.Default.DeleteForever, null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Delete comment", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFFDC2626))
                            Text("Permanently remove", fontSize = 12.sp, color = PPTextMuted)
                        }
                    }
                }
            }
        }
    }

    // ── Main comments sheet ───────────────────────────────────────────────────
    if (showCommentsSheet) {
        var commentSortNewest by remember { mutableStateOf(true) }
        var expandedReplies   by remember { mutableStateOf<Set<String>>(emptySet()) }

        val sortedTopComments by remember(commentSortNewest) {
            derivedStateOf {
                postComments.filter { it.replyTo == null }
                    .let { if (commentSortNewest) it.sortedByDescending { c -> c.timestamp } else it.sortedBy { c -> c.timestamp } }
            }
        }

        ModalBottomSheet(
            onDismissRequest = {
                showCommentsSheet = false
                commentsListener?.remove(); commentsListener = null
                postComments.clear(); commentText = ""
                replyingTo = null; replyParentId = null
            },
            sheetState     = commentsSheetState,
            containerColor = PPBgSurface,
            shape          = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            dragHandle = {
                Box(Modifier.padding(top = 12.dp, bottom = 4.dp).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(PPDivider))
            }
        ) {
            Column(
                Modifier.fillMaxHeight(0.85f).fillMaxWidth()
                    .padding(horizontal = 16.dp).navigationBarsPadding()
            ) {
                // Header
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Comments", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = PPTextPrimary)
                        if (postComments.isNotEmpty()) {
                            val visibleCount = postComments.count { !it.isHidden }
                            Text("$visibleCount", fontSize = 13.sp, color = PPTextMuted, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (postComments.isNotEmpty()) {
                        Row(
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(PPGreen50)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { commentSortNewest = !commentSortNewest }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(if (commentSortNewest) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, null, tint = PPGreen900, modifier = Modifier.size(13.dp))
                            Text(if (commentSortNewest) "Newest" else "Oldest", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PPGreen900)
                        }
                    }
                }
                HorizontalDivider(color = PPDivider)
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding      = PaddingValues(bottom = 8.dp)
                ) {
                    if (postComments.isEmpty()) {
                        item {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(Modifier.size(56.dp).clip(CircleShape).background(PPGreen50), Alignment.Center) {
                                    Icon(Icons.Default.ChatBubbleOutline, null, tint = PPGreen700, modifier = Modifier.size(24.dp))
                                }
                                Text("No comments yet", color = PPTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Be the first to say something!", color = PPTextMuted, fontSize = 13.sp)
                            }
                        }
                    }

                    items(sortedTopComments, key = { it.id }) { comment ->
                        val replies        = postComments.filter { it.replyTo == comment.id }
                        val isLiked        = comment.likedBy.contains(currentUserName)
                        val repliesExpanded = comment.id in expandedReplies

                        Column {
                            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(listOf(PPGreen900, PPGreen700))),
                                    Alignment.Center
                                ) {
                                    val p = userPhotoCache[comment.userName]
                                    if (p != null) AsyncImage(model = p, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                                    else Text(userInitials(comment.userName), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Column(
                                        Modifier
                                            .combinedClickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication        = null,
                                                onClick           = {},
                                                onLongClick       = { contextMenuComment = comment }
                                            )
                                            .background(
                                                if (comment.isHidden) Color(0xFFF3F4F6) else Color(0xFFF3F4F6),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .then(if (comment.isHidden) Modifier.border(1.dp, PPDivider, RoundedCornerShape(12.dp)) else Modifier)
                                            .padding(horizontal = 12.dp, vertical = 9.dp)
                                            .fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Row(
                                            verticalAlignment     = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier              = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    userDisplayNameCache[comment.userName] ?: comment.userName,
                                                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                                    color      = if (comment.isHidden) PPTextMuted else PPTextPrimary
                                                )
                                                val replyTargetName = comment.replyToUserName
                                                if (!replyTargetName.isNullOrBlank()) {
                                                    val targetDisplay = userDisplayNameCache[replyTargetName] ?: replyTargetName
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = PPGreen700.copy(alpha = 0.7f), modifier = Modifier.size(9.dp))
                                                        Text(targetDisplay, fontSize = 11.sp, color = PPGreen700, fontWeight = FontWeight.SemiBold)
                                                    }
                                                }
                                            }
                                            if (comment.isHidden) {
                                                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFFFEBEE)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                    Text("Hidden", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFD32F2F), letterSpacing = 0.5.sp)
                                                }
                                            }
                                        }
                                        Text(comment.text, fontSize = 14.sp, color = if (comment.isHidden) PPTextMuted.copy(alpha = 0.6f) else PPTextSecondary, lineHeight = 20.sp)
                                        if ((comment.editedAt ?: 0L) > 0L) {
                                            Text("edited", fontSize = 10.sp, color = PPTextMuted, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                        }
                                    }
                                    // Timestamp + like + reply row
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment     = Alignment.CenterVertically,
                                        modifier              = Modifier.padding(start = 4.dp)
                                    ) {
                                        Text(formatTimestamp(comment.timestamp), fontSize = 10.sp, color = PPTextMuted)
                                        if (!isAdmin) {
                                            Row(
                                                verticalAlignment     = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier              = Modifier
                                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                                        val ref = db.collection("posts").document(selectedPostId).collection("comments").document(comment.id)
                                                        if (isLiked) ref.update("likedBy", com.google.firebase.firestore.FieldValue.arrayRemove(currentUserName), "likes", com.google.firebase.firestore.FieldValue.increment(-1))
                                                        else ref.update("likedBy", com.google.firebase.firestore.FieldValue.arrayUnion(currentUserName), "likes", com.google.firebase.firestore.FieldValue.increment(1))
                                                    }
                                                    .padding(vertical = 2.dp)
                                            ) {
                                                Icon(if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (isLiked) Color(0xFFEF4444) else PPTextMuted, modifier = Modifier.size(13.dp))
                                                if (comment.likes > 0) Text("${comment.likes}", fontSize = 11.sp, color = if (isLiked) Color(0xFFEF4444) else PPTextMuted)
                                            }
                                        }
                                        if (!isAdmin) {
                                            Text(
                                                "Reply", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PPTextMuted,
                                                modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                                    replyingTo = comment; replyParentId = comment.id
                                                    expandedReplies = expandedReplies + comment.id
                                                }.padding(vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Replies
                            if (replies.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.padding(start = 44.dp)
                                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                            expandedReplies = if (repliesExpanded) expandedReplies - comment.id else expandedReplies + comment.id
                                        }.padding(vertical = 4.dp),
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(Modifier.width(20.dp).height(1.dp).background(PPGreen700.copy(alpha = 0.4f)))
                                    Text(
                                        if (repliesExpanded) "Hide replies" else "View ${replies.size} repl${if (replies.size == 1) "y" else "ies"}",
                                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PPGreen700
                                    )
                                    Icon(if (repliesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = PPGreen700, modifier = Modifier.size(14.dp))
                                }

                                if (repliesExpanded) {
                                    Spacer(Modifier.height(4.dp))
                                    replies.forEach { reply ->
                                        val isReplyLiked = reply.likedBy.contains(currentUserName)
                                        Row(
                                            verticalAlignment = Alignment.Top,
                                            modifier          = Modifier.fillMaxWidth().padding(start = 44.dp)
                                        ) {
                                            Box(
                                                Modifier.size(26.dp).clip(CircleShape).background(Brush.linearGradient(listOf(PPGreen700, PPGreen900))),
                                                Alignment.Center
                                            ) {
                                                val rp = userPhotoCache[reply.userName]
                                                if (rp != null) AsyncImage(model = rp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                                                else Text(userInitials(reply.userName), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Column(
                                                    Modifier
                                                        .combinedClickable(
                                                            interactionSource = remember { MutableInteractionSource() },
                                                            indication        = null,
                                                            onClick           = {},
                                                            onLongClick       = { contextMenuComment = reply }
                                                        )
                                                        .background(Color(0xFFF3F4F6), RoundedCornerShape(10.dp))
                                                        .then(if (reply.isHidden) Modifier.border(1.dp, PPDivider, RoundedCornerShape(10.dp)) else Modifier)
                                                        .padding(horizontal = 10.dp, vertical = 7.dp).fillMaxWidth(),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Text(
                                                            userDisplayNameCache[reply.userName] ?: reply.userName,
                                                            fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                                                            color      = if (reply.isHidden) PPTextMuted else PPTextPrimary
                                                        )
                                                        if (reply.isHidden) {
                                                            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFFFEBEE)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                                Text("Hidden", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFD32F2F), letterSpacing = 0.5.sp)
                                                            }
                                                        }
                                                        val rtn = reply.replyToUserName
                                                        if (!rtn.isNullOrBlank()) {
                                                            val rd = userDisplayNameCache[rtn] ?: rtn
                                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                                Icon(Icons.AutoMirrored.Filled.Send, null, tint = PPGreen700.copy(alpha = 0.7f), modifier = Modifier.size(9.dp))
                                                                Text(rd, fontSize = 11.sp, color = PPGreen700, fontWeight = FontWeight.SemiBold)
                                                            }
                                                        }
                                                    }
                                                    Text(reply.text, fontSize = 13.sp, color = if (reply.isHidden) PPTextMuted.copy(alpha = 0.6f) else PPTextSecondary, lineHeight = 19.sp)
                                                    if ((reply.editedAt ?: 0L) > 0L) Text("edited", fontSize = 10.sp, color = PPTextMuted, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                                }
                                                Row(
                                                    verticalAlignment     = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    modifier              = Modifier.padding(start = 4.dp)
                                                ) {
                                                    Text(formatTimestamp(reply.timestamp), fontSize = 10.sp, color = PPTextMuted)
                                                    if (!isAdmin) {
                                                        Row(
                                                            verticalAlignment     = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                            modifier              = Modifier
                                                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                                                    val ref = db.collection("posts").document(selectedPostId).collection("comments").document(reply.id)
                                                                    if (isReplyLiked) ref.update("likedBy", com.google.firebase.firestore.FieldValue.arrayRemove(currentUserName), "likes", com.google.firebase.firestore.FieldValue.increment(-1))
                                                                    else ref.update("likedBy", com.google.firebase.firestore.FieldValue.arrayUnion(currentUserName), "likes", com.google.firebase.firestore.FieldValue.increment(1))
                                                                }.padding(vertical = 2.dp)
                                                        ) {
                                                            Icon(if (isReplyLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (isReplyLiked) Color(0xFFEF4444) else PPTextMuted, modifier = Modifier.size(11.dp))
                                                            if (reply.likes > 0) Text("${reply.likes}", fontSize = 10.sp, color = if (isReplyLiked) Color(0xFFEF4444) else PPTextMuted)
                                                        }
                                                    }
                                                    Text(
                                                        "Reply", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PPTextMuted,
                                                        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                                            replyingTo = reply; replyParentId = comment.id
                                                            expandedReplies = expandedReplies + comment.id
                                                        }.padding(vertical = 2.dp)
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

                HorizontalDivider(color = PPDivider)

                // Reply banner
                androidx.compose.animation.AnimatedVisibility(visible = replyingTo != null) {
                    Row(
                        Modifier.fillMaxWidth().background(PPGreen50).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, tint = PPGreen700, modifier = Modifier.size(12.dp))
                            Column {
                                Text(
                                    "Replying to ${replyingTo?.userName?.let { userDisplayNameCache[it] ?: it }}",
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PPGreen900
                                )
                                replyingTo?.text?.let { txt ->
                                    val preview = txt.take(60).let { if (txt.length > 60) "$it…" else it }
                                    Text(preview, fontSize = 11.sp, color = PPGreen700, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        IconButton(onClick = { replyingTo = null; replyParentId = null }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, null, tint = PPTextMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                if (!isAdmin) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(listOf(PPGreen900, PPGreen700))),
                            Alignment.Center
                        ) {
                            val myPhoto = userPhotoCache[currentUserName]
                            if (myPhoto != null) AsyncImage(model = myPhoto, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                            else Text(userInitials(currentUserName), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        OutlinedTextField(
                            value         = commentText,
                            onValueChange = { commentText = it },
                            placeholder   = { Text(if (replyingTo != null) "Write a reply…" else "Add a comment…", fontSize = 13.sp, color = PPTextMuted) },
                            modifier      = Modifier.weight(1f),
                            shape         = RoundedCornerShape(24.dp),
                            maxLines      = 3,
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor      = PPGreen700,
                                unfocusedBorderColor    = PPDivider,
                                cursorColor             = PPGreen700,
                                focusedTextColor        = PPTextPrimary,
                                unfocusedTextColor      = PPTextPrimary,
                                focusedContainerColor   = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                        )
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (commentText.isBlank()) Color(0xFFF3F4F6) else PPGreen900)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    if (commentText.isBlank()) return@clickable
                                    val text        = commentText.trim()
                                    val replyTarget = replyingTo
                                    val parentId    = replyParentId
                                    commentText     = ""
                                    replyingTo      = null
                                    replyParentId   = null
                                    focusManager.clearFocus()

                                    val payload = hashMapOf<String, Any>(
                                        "userName"  to currentUserName,
                                        "text"      to text,
                                        "timestamp" to System.currentTimeMillis(),
                                        "likes"     to 0,
                                        "likedBy"   to emptyList<String>()
                                    )
                                    replyTarget?.let {
                                        payload["replyTo"]         = parentId ?: it.id
                                        payload["replyToUserName"] = it.userName
                                    }

                                    db.collection("posts").document(selectedPostId)
                                        .collection("comments").add(payload)
                                        .addOnSuccessListener {
                                            db.collection("posts").document(selectedPostId)
                                                .update("comments", com.google.firebase.firestore.FieldValue.increment(1))
                                            db.collection("users").whereEqualTo("username", currentUserName).limit(1).get()
                                                .addOnSuccessListener { snap ->
                                                    val dn = snap.documents.firstOrNull()?.getString("displayName")?.takeIf { it.isNotBlank() } ?: currentUserName
                                                    db.collection("posts").document(selectedPostId).get()
                                                        .addOnSuccessListener { postDoc ->
                                                            val postOwner = postDoc.getString("userName") ?: ""
                                                            if (postOwner.isNotBlank() && postOwner != currentUserName) {
                                                                db.collection("notifications").add(hashMapOf("userName" to postOwner, "message" to "$dn commented on your post.", "type" to "comment", "timestamp" to System.currentTimeMillis(), "read" to false, "postId" to selectedPostId))
                                                            }
                                                            if (replyTarget != null && replyTarget.userName != currentUserName && replyTarget.userName != postOwner) {
                                                                val preview = replyTarget.text.take(50).let { if (replyTarget.text.length > 50) "$it…" else it }
                                                                db.collection("notifications").add(hashMapOf("userName" to replyTarget.userName, "message" to "$dn replied to your comment: \"$preview\"", "type" to "reply", "timestamp" to System.currentTimeMillis(), "read" to false, "postId" to selectedPostId))
                                                            }
                                                        }
                                                }
                                        }
                                },
                            Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (commentText.isBlank()) PPTextMuted else Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    // ── Saved Routes sheet ────────────────────────────────────────────────────
    if (showRidesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRidesSheet = false },
            sheetState       = ridesSheetState,
            containerColor   = PPBgCanvas,
            dragHandle       = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .width(40.dp).height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(PPDivider)
                )
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PPGreen50),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Bookmarks, null,
                            tint     = PPGreen900,
                            modifier = Modifier.size(16.dp))
                    }
                    Text(
                        "${displayName.ifBlank { targetUserName }}'s Routes",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color      = PPTextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(PPGreen100)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("${myRides.size}", fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, color = PPGreen900)
                    }
                }
                IconButton(onClick = { showRidesSheet = false }) {
                    Icon(Icons.Default.Close, "Close",
                        tint     = PPTextMuted,
                        modifier = Modifier.size(20.dp))
                }
            }
            HorizontalDivider(color = PPDivider, thickness = 1.dp)
            Spacer(Modifier.height(8.dp))
            if (isLoadingRides) {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color       = PPGreen900,
                        strokeWidth = 2.5.dp,
                        modifier    = Modifier.size(32.dp)
                    )
                }
            } else if (myRides.isEmpty()) {
                PPEmptyState(
                    icon    = Icons.AutoMirrored.Filled.DirectionsBike,
                    title   = "No saved rides yet",
                    message = "${displayName.ifBlank { targetUserName }} hasn't saved any routes yet."
                )
            } else {
                val formatDuration: (Long) -> String = { min ->
                    val h = min / 60; val m = min % 60
                    if (h > 0) "${h}h ${m}m" else "${m}m"
                }
                LazyColumn(
                    contentPadding      = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(myRides, key = { it.id }) { ride ->
                        RideCard(ride = ride, formatDuration = formatDuration)
                    }
                }
            }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Profile",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp,
                        color      = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors  = TopAppBarDefaults.topAppBarColors(containerColor = PPGreen900),
                modifier = Modifier.shadow(elevation = 2.dp)
            )
        },
        containerColor = PPBgCanvas
    ) { innerPadding ->

        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                CircularProgressIndicator(
                    color       = PPGreen900,
                    strokeWidth = 2.5.dp,
                    modifier    = Modifier.size(32.dp)
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {

            // ── Hero ──────────────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(PPGreen900, PPGreen800))
                        )
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                                .padding(top = 8.dp, bottom = 16.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Avatar
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!photoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model              = photoUrl,
                                        contentDescription = "Profile photo",
                                        contentScale       = ContentScale.Crop,
                                        modifier           = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                } else {
                                    Text(
                                        displayName.take(1).uppercase(),
                                        fontSize   = 28.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color      = Color.White
                                    )
                                }
                            }
                            // Name
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    displayName.ifBlank { targetUserName },
                                    fontSize      = 18.sp,
                                    fontWeight    = FontWeight.Bold,
                                    color         = Color.White,
                                    letterSpacing = (-0.3).sp,
                                    maxLines      = 1,
                                    overflow      = TextOverflow.Ellipsis
                                )
                                Text(
                                    "@$targetUserName",
                                    fontSize = 12.sp,
                                    color    = Color.White.copy(alpha = 0.55f)
                                )
                            }
                        }

                        // Bio + pills
                        if (!bio.isNullOrBlank() || bikeTypes.isNotEmpty() || !skillLevel.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.18f))
                                    .padding(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (!bio.isNullOrBlank()) {
                                        Text(
                                            bio!!,
                                            fontSize   = 13.sp,
                                            color      = Color.White.copy(alpha = 0.85f),
                                            lineHeight = 19.sp
                                        )
                                    }
                                    if (bikeTypes.isNotEmpty() || !skillLevel.isNullOrBlank()) {
                                        androidx.compose.foundation.layout.FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement   = Arrangement.spacedBy(6.dp)
                                        ) {
                                            bikeTypes.forEach { bikeType ->
                                                PPPill(Icons.Default.DirectionsBike, bikeType)
                                            }
                                            if (!skillLevel.isNullOrBlank()) {
                                                PPPill(Icons.Default.Star, skillLevel!!)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Stats card ────────────────────────────────────────────────────
            item {
                Card(
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp),
                    shape     = RoundedCornerShape(16.dp),
                    colors    = CardDefaults.cardColors(containerColor = PPBgSurface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier          = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PPStatCell(Icons.Default.Article,  "$totalPostCount",  "Posts",  Modifier.weight(1f))
                        Box(Modifier.width(1.dp).height(32.dp).background(PPDivider))
                        PPStatCell(Icons.Default.Groups,   "$totalEventCount", "Events", Modifier.weight(1f))
                        Box(Modifier.width(1.dp).height(32.dp).background(PPDivider))
                        PPStatCell(Icons.Default.Favorite, "$totalLikesCount", "Likes",  Modifier.weight(1f))
                    }
                }
            }

            // ── Tabs ──────────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    TabRow(
                        selectedTabIndex = subTab,
                        containerColor   = PPBgSurface,
                        contentColor     = PPGreen900,
                        modifier         = Modifier.padding(end = 52.dp),
                        indicator = { tabPositions ->
                            SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[subTab]),
                                color    = PPGreen900
                            )
                        },
                        divider = { HorizontalDivider(color = PPDivider, thickness = 1.dp) }
                    ) {
                        listOf("Community Posts", "Ride Events").forEachIndexed { index, label ->
                            Tab(
                                selected               = subTab == index,
                                onClick                = { subTab = index },
                                selectedContentColor   = PPGreen900,
                                unselectedContentColor = PPTextMuted,
                                text = {
                                    Text(
                                        label,
                                        fontWeight = if (subTab == index) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize   = 13.sp,
                                        modifier   = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp)
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PPGreen700)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication        = null
                            ) { showRidesSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Bookmarks,
                            contentDescription = "Saved Routes",
                            tint     = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        if (myRides.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 6.dp, end = 6.dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(PPGreen900),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (myRides.size > 9) "9+" else "${myRides.size}",
                                    fontSize   = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // ── Posts tab ─────────────────────────────────────────────────────
            if (subTab == 0) {
                if (isLoadingPosts) {
                    item {
                        Box(
                            modifier         = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color       = PPGreen900,
                                strokeWidth = 2.5.dp,
                                modifier    = Modifier.size(32.dp)
                            )
                        }
                    }
                } else if (posts.isEmpty()) {
                    item {
                        PPEmptyState(
                            icon    = Icons.Default.Feed,
                            title   = "No posts yet",
                            message = "${displayName.ifBlank { targetUserName }} hasn't shared a ride yet."
                        )
                    }
                } else {
                    items(posts, key = { it.id }) { post ->
                        val wasLikedInFirestore = post.likedBy.contains(currentUserName)
                        val isLiked = likeOverrides[post.id] ?: wasLikedInFirestore
                        val displayedLikes = when {
                            isLiked && !wasLikedInFirestore -> post.likes + 1
                            !isLiked && wasLikedInFirestore -> post.likes - 1
                            else                            -> post.likes
                        }
                        val displayedPost = post.copy(
                            likedBy = if (isLiked)
                                (post.likedBy + currentUserName).distinct()
                            else
                                post.likedBy - currentUserName,
                            likes = displayedLikes
                        )
                        CommunityFeedCard(
                            post            = displayedPost,
                            currentUser     = currentUserName,
                            viewerIsAuthor  = false,
                            onLike          = {
                                val currentlyLiked = likeOverrides[post.id]
                                    ?: post.likedBy.contains(currentUserName)
                                likeOverrides[post.id] = !currentlyLiked
                                val ref = db.collection("posts").document(post.id)
                                if (currentlyLiked) {
                                    ref.update(
                                        "likedBy", com.google.firebase.firestore.FieldValue.arrayRemove(currentUserName),
                                        "likes",   com.google.firebase.firestore.FieldValue.increment(-1)
                                    ).addOnFailureListener {
                                        likeOverrides[post.id] = currentlyLiked
                                    }
                                } else {
                                    ref.update(
                                        "likedBy", com.google.firebase.firestore.FieldValue.arrayUnion(currentUserName),
                                        "likes",   com.google.firebase.firestore.FieldValue.increment(1)
                                    ).addOnSuccessListener {
                                        // Send like notification to post author
                                        if (post.userName != currentUserName) {
                                            db.collection("users")
                                                .whereEqualTo("username", currentUserName)
                                                .limit(1).get()
                                                .addOnSuccessListener { snap ->
                                                    val displayName = snap.documents.firstOrNull()
                                                        ?.getString("displayName")
                                                        ?.takeIf { it.isNotBlank() }
                                                        ?: currentUserName
                                                    db.collection("notifications").add(
                                                        hashMapOf(
                                                            "userName"  to post.userName,
                                                            "message"   to "$displayName liked your post.",
                                                            "type"      to "like",
                                                            "timestamp" to System.currentTimeMillis(),
                                                            "read"      to false,
                                                            "postId"    to post.id
                                                        )
                                                    )
                                                }
                                        }
                                    }.addOnFailureListener {
                                        likeOverrides[post.id] = currentlyLiked
                                    }
                                }
                            },
                            onEdit          = {},
                            onDelete        = {},
                            photoUrl        = photoUrl,
                            isAdmin         = false,
                            onComment       = {
                                selectedPostId = post.id
                                postComments.clear()
                                commentText = ""
                                showCommentsSheet = true
                                // Start listening to comments for this post
                                commentsListener?.remove()
                                commentsListener = db.collection("posts").document(post.id)
                                    .collection("comments")
                                    .orderBy("timestamp", Query.Direction.ASCENDING)
                                    .addSnapshotListener { snap, _ ->
                                        if (snap == null) return@addSnapshotListener
                                        postComments.clear()
                                        snap.documents.forEach { doc ->
                                            val status = doc.getString("status") ?: "visible"
                                            if (status != "hidden") {
                                                postComments.add(CommentItem(
                                                    id        = doc.id,
                                                    userName  = doc.getString("userName") ?: "",
                                                    text      = doc.getString("text") ?: "",
                                                    timestamp = doc.getLong("timestamp") ?: 0L,
                                                    likes     = (doc.getLong("likes") ?: 0L).toInt(),
                                                    likedBy   = (doc.get("likedBy") as? List<*>)
                                                        ?.filterIsInstance<String>() ?: emptyList()
                                                ))
                                            }
                                        }
                                        // Pre-fetch display names + photos for commenters
                                        val unknown = postComments.map { it.userName }
                                            .distinct().filter { it !in userDisplayNameCache }
                                        unknown.forEach { name ->
                                            db.collection("users")
                                                .whereEqualTo("username", name)
                                                .limit(1).get()
                                                .addOnSuccessListener { s ->
                                                    val d = s.documents.firstOrNull()
                                                    d?.getString("photoUrl")
                                                        ?.let { userPhotoCache[name] = it }
                                                    d?.getString("displayName")
                                                        ?.takeIf { it.isNotBlank() }
                                                        ?.let { userDisplayNameCache[name] = it }
                                                }
                                        }
                                    }
                                // Pre-fetch current user photo
                                if (currentUserName !in userPhotoCache) {
                                    db.collection("users")
                                        .whereEqualTo("username", currentUserName)
                                        .limit(1).get()
                                        .addOnSuccessListener { snap ->
                                            val doc = snap.documents.firstOrNull()
                                            doc?.getString("photoUrl")
                                                ?.let { userPhotoCache[currentUserName] = it }
                                            doc?.getString("displayName")
                                                ?.takeIf { it.isNotBlank() }
                                                ?.let { userDisplayNameCache[currentUserName] = it }
                                        }
                                }
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            // ── Events tab ────────────────────────────────────────────────────
            if (subTab == 1) {
                if (isLoadingEvents) {
                    item {
                        Box(
                            modifier         = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color       = PPGreen900,
                                strokeWidth = 2.5.dp,
                                modifier    = Modifier.size(32.dp)
                            )
                        }
                    }
                } else if (events.isEmpty()) {
                    item {
                        PPEmptyState(
                            icon    = Icons.AutoMirrored.Filled.DirectionsBike,
                            title   = "No events yet",
                            message = "${displayName.ifBlank { targetUserName }} hasn't joined any rides yet."
                        )
                    }
                } else {
                    items(events, key = { "pub_event_${it.id}" }) { event ->
                        val rideEvent = RideEvent(
                            id           = event.id,
                            title        = event.title,
                            route        = event.route,
                            date         = event.date,
                            time         = event.time,
                            difficulty   = event.difficulty,
                            distanceKm   = event.distanceKm,
                            status       = event.status,
                            participants = listOf(targetUserName),
                            organizer    = if (event.isOrganizer) targetUserName else ""
                        )
                        // Reuse the private EventCard from ProfileScreen via the
                        // internal JoinedEvent — tap opens the full detail sheet
                        PPEventCard(
                            event           = event,
                            formatEventDate = ::formatEventDate,
                            onTap           = {
                                db.collection("rideEvents").document(event.id)
                                    .get()
                                    .addOnSuccessListener { doc ->
                                        if (doc != null && doc.exists()) {
                                            selectedEvent = RideEvent(
                                                id              = doc.id,
                                                title           = doc.getString("title")           ?: "",
                                                description     = doc.getString("description")     ?: "",
                                                route           = doc.getString("route")           ?: "",
                                                date            = doc.getLong("date")              ?: 0L,
                                                time            = doc.getString("time")            ?: "",
                                                organizer       = doc.getString("organizer")       ?: "",
                                                participants    = (doc.get("participants") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                                                maxParticipants = (doc.getLong("maxParticipants")  ?: 0L).toInt(),
                                                difficulty      = doc.getString("difficulty")      ?: "Easy",
                                                distanceKm      = doc.getDouble("distanceKm")      ?: 0.0,
                                                attendees       = (doc.get("attendees") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                                                checkInOpen     = doc.getBoolean("checkInOpen")    ?: false,
                                                status          = doc.getString("status")          ?: "approved",
                                                durationHours   = (doc.getLong("durationHours")    ?: 0L).toInt(),
                                                isEdited        = doc.getBoolean("isEdited")       ?: false,
                                                editedAt        = doc.getLong("editedAt")          ?: 0L
                                            )
                                        }
                                    }
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

// ── Atoms ─────────────────────────────────────────────────────────────────────
@Composable
private fun PPPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier
            .wrapContentWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(10.dp))
        Text(text, fontSize = 10.sp, color = Color.White.copy(alpha = 0.85f),
            fontWeight = FontWeight.Medium, softWrap = false)
    }
}

@Composable
private fun PPStatCell(
    icon     : ImageVector,
    value    : String,
    label    : String,
    modifier : Modifier = Modifier
) {
    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(PPGreen50),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = PPGreen900, modifier = Modifier.size(18.dp))
        }
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = PPTextPrimary)
        Text(label, fontSize = 10.sp, color = PPTextMuted, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PPEmptyState(icon: ImageVector, title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(PPGreen50),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = PPGreen700, modifier = Modifier.size(32.dp))
        }
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            color = PPTextPrimary, textAlign = TextAlign.Center)
        Text(message, fontSize = 13.sp, color = PPTextMuted,
            textAlign = TextAlign.Center, lineHeight = 20.sp)
    }
}

@Composable
private fun PPEventCard(
    event           : JoinedEvent,
    formatEventDate : (Long) -> String,
    onTap           : () -> Unit
) {
    val diffFg = when (event.difficulty) {
        "Easy"     -> Color(0xFF166534)
        "Moderate" -> Color(0xFF9A3412)
        "Hard"     -> Color(0xFF991B1B)
        else       -> PPTextSecondary
    }
    val diffBg = when (event.difficulty) {
        "Easy"     -> Color(0xFFDCFCE7)
        "Moderate" -> Color(0xFFFFEDD5)
        "Hard"     -> Color(0xFFFFE4E6)
        else       -> Color(0xFFF3F4F6)
    }
    val rideEvent  = RideEvent(id = event.id, date = event.date, time = event.time,
        difficulty = event.difficulty, distanceKm = event.distanceKm, status = event.status)
    val timeStatus = getEventTimeStatus(rideEvent)
    val isPast     = timeStatus == EventStatus.ENDED

    val headerGradient = if (isPast)
        Brush.horizontalGradient(listOf(Color(0xFF6B7280), Color(0xFF9CA3AF)))
    else if (event.isOrganizer)
        Brush.horizontalGradient(listOf(PPGreen950, PPGreen800))
    else
        Brush.horizontalGradient(listOf(PPGreen900, PPGreen700))

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onTap() },
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = PPBgSurface),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPast) 0.dp else 2.dp,
            pressedElevation = 4.dp
        )
    ) {
        Column {
            // ── Gradient header ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerGradient)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        event.title,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 15.sp,
                        color      = Color.White,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis,
                        lineHeight = 20.sp,
                        modifier   = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            if (event.isOrganizer) "Organizer" else "Joined",
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = Color.White
                        )
                    }
                }
            }

            // ── Card body ─────────────────────────────────────────────────
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Badges row
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement   = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isPast) Color(0xFFF3F4F6) else diffBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            event.difficulty,
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (isPast) PPTextMuted else diffFg
                        )
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when (timeStatus) {
                                    EventStatus.ENDED         -> Color(0xFFF3F4F6)
                                    EventStatus.HAPPENING_NOW -> Color(0xFFFEF3C7)
                                    EventStatus.UPCOMING      -> Color(0xFFECFDF5)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            when (timeStatus) {
                                EventStatus.ENDED         -> "Completed"
                                EventStatus.HAPPENING_NOW -> "🔴 Live Now"
                                EventStatus.UPCOMING      -> "Upcoming"
                            },
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = when (timeStatus) {
                                EventStatus.ENDED         -> Color(0xFF6B7280)
                                EventStatus.HAPPENING_NOW -> Color(0xFF92400E)
                                EventStatus.UPCOMING      -> Color(0xFF059669)
                            }
                        )
                    }
                    if (event.distanceKm > 0) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PPGreen50)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Route, null,
                                    tint     = PPGreen700,
                                    modifier = Modifier.size(10.dp))
                                Text(
                                    String.format("%.0f km", event.distanceKm),
                                    fontSize   = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = PPGreen700
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = PPDivider, thickness = 0.5.dp)

                // Date + time
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isPast) Color(0xFFF3F4F6) else PPGreen50),
                        Alignment.Center
                    ) {
                        Icon(Icons.Default.CalendarMonth, null,
                            tint     = if (isPast) PPTextMuted else PPGreen900,
                            modifier = Modifier.size(14.dp))
                    }
                    Text(
                        buildString {
                            append(formatEventDate(event.date))
                            if (event.time.isNotBlank()) append("  ·  ${event.time}")
                        },
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color      = if (isPast) PPTextMuted else PPTextSecondary
                    )
                }

                // Route
                if (event.route.isNotBlank()) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF3F4F6)),
                            Alignment.Center
                        ) {
                            Icon(Icons.Default.LocationOn, null,
                                tint     = PPTextMuted,
                                modifier = Modifier.size(14.dp))
                        }
                        Text(
                            event.route,
                            fontSize = 12.sp,
                            color    = PPTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}