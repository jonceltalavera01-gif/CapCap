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
                                    ).addOnFailureListener {
                                        likeOverrides[post.id] = currentlyLiked
                                    }
                                }
                            },
                            onEdit          = {},
                            onDelete        = {},
                            photoUrl        = photoUrl,
                            isAdmin         = false
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