package com.darkhorses.PedalConnect.ui.theme

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset

// ── Colour tokens ─────────────────────────────────────────────────────────────
private val PGreen900 = Color(0xFF06402B)
private val PGreen100 = Color(0xFFE8F5E9)

// ─────────────────────────────────────────────────────────────────────────────
// Data models
// ─────────────────────────────────────────────────────────────────────────────
private data class ProfilePost(
    val id:          String,
    val description: String,
    val activity:    String,
    val distance:    String,
    val timestamp:   Long,
    val likes:       Int,
    val status:      String,
    val imageUrl:    String,
    val rideStats:   Map<String, Any>?
)

private data class JoinedEvent(
    val id:          String,
    val title:       String,
    val route:       String,
    val date:        Long,
    val time:        String,
    val difficulty:  String,
    val distanceKm:  Double,
    val isOrganizer: Boolean
)

private data class SavedRide(
    val id:          String,
    val name:        String,
    val distanceKm:  Double,
    val durationMin: Long,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val elevationM:  Double,
    val timesRidden: Int,
    val lastRidden:  String,
    val timestamp:   Long
)

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController, userName: String) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val db      = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()

    var photoUrl         by remember { mutableStateOf<String?>(null) }
    var isUploadingPhoto by remember { mutableStateOf(false) }
    var userDocId        by remember { mutableStateOf<String?>(null) }

    var selectedTab by remember { mutableIntStateOf(0) }

    val myPosts      = remember { mutableStateListOf<ProfilePost>() }
    val joinedEvents = remember { mutableStateListOf<JoinedEvent>() }
    val myRides      = remember { mutableStateListOf<SavedRide>() }

    var isLoadingPosts  by remember { mutableStateOf(true) }
    var isLoadingEvents by remember { mutableStateOf(true) }
    var isLoadingRides  by remember { mutableStateOf(true) }

    val totalRides   = myRides.size
    val totalKm      = myRides.sumOf { it.distanceKm }
    val totalMinutes = myRides.sumOf { it.durationMin }
    val bestSpeedKmh = myRides.maxOfOrNull { it.maxSpeedKmh } ?: 0.0

    // ── Photo picker ──────────────────────────────────────────────────────────
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            isUploadingPhoto = true
            scope.launch {
                try {
                    val ref = storage.reference.child("profilePhotos/$userName.jpg")
                    ref.putFile(uri).await()
                    val url = ref.downloadUrl.await().toString()
                    // Save URL to Firestore — query by username field, NOT document ID
                    val docId = userDocId ?: run {
                        val snap = db.collection("users")
                            .whereEqualTo("username", userName)
                            .limit(1).get().await()
                        snap.documents.firstOrNull()?.id
                    }
                    if (docId != null) {
                        userDocId = docId
                        db.collection("users").document(docId)
                            .update("photoUrl", url).await()
                    }
                    photoUrl         = url
                    isUploadingPhoto = false
                    Toast.makeText(context, "Profile photo updated! 🎉", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    isUploadingPhoto = false
                    Toast.makeText(context, "Upload failed: ${e.message?.take(60)}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ── Load user doc (photo + doc ID) ────────────────────────────────────────
    LaunchedEffect(userName) {
        db.collection("users")
            .whereEqualTo("username", userName)
            .limit(1).get()
            .addOnSuccessListener { snap ->
                snap.documents.firstOrNull()?.let { doc ->
                    userDocId = doc.id
                    photoUrl  = doc.getString("photoUrl")
                }
            }
    }

    // ── Load my posts ─────────────────────────────────────────────────────────
    LaunchedEffect(userName) {
        db.collection("posts")
            .whereEqualTo("userName", userName)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { isLoadingPosts = false; return@addSnapshotListener }
                myPosts.clear()
                for (doc in snap.documents) {
                    myPosts.add(ProfilePost(
                        id          = doc.id,
                        description = doc.getString("description") ?: "",
                        activity    = doc.getString("activity")    ?: "Cycling Ride",
                        distance    = doc.getString("distance")    ?: "0",
                        timestamp   = doc.getLong("timestamp")     ?: 0L,
                        likes       = (doc.getLong("likes") ?: 0L).toInt(),
                        status      = doc.getString("status")      ?: "pending",
                        imageUrl    = doc.getString("imageUrl")    ?: "",
                        rideStats   = doc.get("rideStats") as? Map<String, Any>
                    ))
                }
                isLoadingPosts = false
            }
    }

    // ── Load joined ride events ───────────────────────────────────────────────
    LaunchedEffect(userName) {
        db.collection("rideEvents")
            .whereArrayContains("participants", userName)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { isLoadingEvents = false; return@addSnapshotListener }
                val startOfToday = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                joinedEvents.clear()
                for (doc in snap.documents) {
                    if (doc.getString("status") == "rejected") continue
                    val eventDate = doc.getLong("date") ?: 0L
                    if (eventDate < startOfToday) continue  // skip past events
                    joinedEvents.add(JoinedEvent(
                        id          = doc.id,
                        title       = doc.getString("title")      ?: "Unnamed Ride",
                        route       = doc.getString("route")      ?: "",
                        date        = doc.getLong("date")         ?: 0L,
                        time        = doc.getString("time")       ?: "",
                        difficulty  = doc.getString("difficulty") ?: "Easy",
                        distanceKm  = doc.getDouble("distanceKm") ?: 0.0,
                        isOrganizer = doc.getString("organizer")  == userName
                    ))
                }
                isLoadingEvents = false
            }
    }

    // ── Load saved rides ──────────────────────────────────────────────────────
    LaunchedEffect(userName) {
        db.collection("savedRoutes")
            .whereEqualTo("userName", userName)
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

    // ── Helpers ───────────────────────────────────────────────────────────────
    fun formatTs(ts: Long): String =
        if (ts == 0L) "" else SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ts))

    fun formatEventDate(ts: Long): String =
        if (ts == 0L) "Date TBA" else SimpleDateFormat("EEE, MMM d · yyyy", Locale.getDefault()).format(Date(ts))

    fun formatDuration(min: Long): String {
        val h = min / 60; val m = min % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────────────────────────────────────────
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF7F9F7)),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {

        // ── Hero ──────────────────────────────────────────────────────────────
        item {
            Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                Box(modifier = Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(PGreen900, Color(0xFF0A7A4C)))))

                IconButton(
                    onClick  = { navController.navigate("settings") },
                    modifier = Modifier.align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars).padding(8.dp)
                ) {
                    Icon(Icons.Default.Settings, "Settings", tint = Color.White.copy(alpha = 0.85f))
                }

                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier.size(96.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .border(3.dp, Color.White, CircleShape)
                                .clickable { if (!isUploadingPhoto) photoPicker.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (photoUrl != null) {
                                AsyncImage(
                                    model              = photoUrl,
                                    contentDescription = "Profile photo",
                                    contentScale       = ContentScale.Crop,
                                    modifier           = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Text(userName.take(1).uppercase(),
                                    fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                            // Upload spinner overlay
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isUploadingPhoto, enter = fadeIn(), exit = fadeOut()
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Color.White,
                                        modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                                }
                            }
                        }
                        // Camera badge
                        Box(
                            modifier = Modifier.size(28.dp).clip(CircleShape)
                                .background(Color.White).border(2.dp, PGreen900, CircleShape)
                                .clickable { if (!isUploadingPhoto) photoPicker.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, "Change photo",
                                tint = PGreen900, modifier = Modifier.size(14.dp))
                        }
                    }

                    Text(userName, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    if (isUploadingPhoto) {
                        Text("Uploading photo…", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }

        // ── Stat strip ────────────────────────────────────────────────────────
        item {
            Row(modifier = Modifier.fillMaxWidth().background(Color.White).padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                StatCell("Rides",     "$totalRides")
                VerticalDivider(modifier = Modifier.height(36.dp))
                StatCell("Total km",  String.format("%.1f", totalKm))
                VerticalDivider(modifier = Modifier.height(36.dp))
                StatCell("Time",      formatDuration(totalMinutes))
            }
        }

        // ── Tabs ──────────────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(10.dp))
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = Color.White,
                contentColor     = PGreen900,
                indicator        = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color    = PGreen900
                    )
                }
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Feed, null, modifier = Modifier.size(15.dp))
                        Text("Posts & Events", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.DirectionsBike, null, modifier = Modifier.size(15.dp))
                        Text("Saved Rides", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                })
            }
            Spacer(Modifier.height(10.dp))
        }

        // ═══════════════════════════════════════════════════════════════════
        // TAB 0 — Posts + Joined Events
        // ═══════════════════════════════════════════════════════════════════
        if (selectedTab == 0) {
            val loading = isLoadingPosts || isLoadingEvents
            if (loading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(56.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PGreen900)
                    }
                }
            } else if (myPosts.isEmpty() && joinedEvents.isEmpty()) {
                item { ProfileEmptyState(Icons.Default.Groups, "No posts or events yet.\nShare a ride or join a group ride!") }
            } else {
                if (myPosts.isNotEmpty()) {
                    item { SectionHeader(Icons.Default.Groups, "Community Posts", "${myPosts.size}") }
                    items(myPosts, key = { "post_${it.id}" }) { post ->
                        PostCard(post = post, formatTs = ::formatTs)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (joinedEvents.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        SectionHeader(Icons.AutoMirrored.Filled.DirectionsBike, "Ride Events", "${joinedEvents.size}")
                    }
                    items(joinedEvents, key = { "event_${it.id}" }) { event ->
                        EventCard(event = event, formatEventDate = ::formatEventDate)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // TAB 1 — Saved Rides
        // ═══════════════════════════════════════════════════════════════════
        if (selectedTab == 1) {
            if (isLoadingRides) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(56.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PGreen900)
                    }
                }
            } else if (myRides.isEmpty()) {
                item { ProfileEmptyState(Icons.AutoMirrored.Filled.DirectionsBike,
                    "No saved rides yet.\nComplete a ride and save the route!") }
            } else {
                items(myRides, key = { it.id }) { ride ->
                    RideCard(ride = ride, formatDuration = ::formatDuration)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SectionHeader(icon: ImageVector, title: String, count: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = PGreen900, modifier = Modifier.size(16.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            color = Color(0xFF1A1A1A), modifier = Modifier.weight(1f))
        Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(PGreen100)
            .padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text(count, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PGreen900)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stat cell
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = PGreen900)
        Text(label, fontSize = 11.sp, color = Color(0xFF9E9E9E), fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ProfileEmptyState(icon: ImageVector, message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(PGreen100),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = PGreen900, modifier = Modifier.size(34.dp))
        }
        Text(message, fontSize = 14.sp, color = Color(0xFF9E9E9E),
            textAlign = TextAlign.Center, lineHeight = 20.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Post card — shows attached image, caption, stat chips, status badge, likes
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PostCard(post: ProfilePost, formatTs: (Long) -> String) {
    val isPending   = post.status == "pending"
    val statusColor = if (isPending) Color(0xFFF57C00) else Color(0xFF2E7D32)
    val statusLabel = if (isPending) "Pending review" else "Published"

    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Full-bleed photo if present
            if (post.imageUrl.isNotBlank()) {
                AsyncImage(
                    model              = post.imageUrl,
                    contentDescription = "Ride photo",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxWidth().height(200.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                )
            }

            Column(modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {

                // Header: icon + date + status badge
                Row(modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(PGreen100), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.DirectionsBike, null,
                                tint = PGreen900, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text(post.activity, fontWeight = FontWeight.Bold,
                                fontSize = 13.sp, color = Color(0xFF1A1A1A))
                            Text(formatTs(post.timestamp), fontSize = 11.sp, color = Color(0xFF9E9E9E))
                        }
                    }
                    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.10f))
                        .padding(horizontal = 9.dp, vertical = 4.dp)) {
                        Text(statusLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor)
                    }
                }

                // Stat chips
                if (post.distance != "0" || post.rideStats != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (post.distance != "0") {
                            PChip(Icons.AutoMirrored.Filled.DirectionsBike, "${post.distance} km")
                        }
                        post.rideStats?.let { stats ->
                            val dur = (stats["durationSec"] as? Long)?.let { it / 60 } ?: 0L
                            if (dur > 0) PChip(Icons.Default.Timer, "${dur}m")
                            val avg = stats["avgSpeedKmh"] as? Double ?: 0.0
                            if (avg > 0) PChip(Icons.Default.Speed, String.format("%.1f km/h", avg))
                        }
                    }
                }

                // Caption
                if (post.description.isNotBlank()) {
                    Text(post.description, fontSize = 13.sp, color = Color(0xFF444444),
                        lineHeight = 19.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }

                HorizontalDivider(color = Color(0xFFF0F0F0))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.Favorite, null, tint = Color(0xFFE57373), modifier = Modifier.size(14.dp))
                    Text("${post.likes} like${if (post.likes != 1) "s" else ""}",
                        fontSize = 12.sp, color = Color(0xFF9E9E9E))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Event card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EventCard(event: JoinedEvent, formatEventDate: (Long) -> String) {
    val diffFg = when (event.difficulty) {
        "Easy" -> Color(0xFF166534); "Moderate" -> Color(0xFF9A3412); "Hard" -> Color(0xFF991B1B); else -> Color(0xFF374151)
    }
    val diffBg = when (event.difficulty) {
        "Easy" -> Color(0xFFDCFCE7); "Moderate" -> Color(0xFFFFEDD5); "Hard" -> Color(0xFFFFE4E6); else -> Color(0xFFF3F4F6)
    }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().height(4.dp)
                .background(diffFg, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)))
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(event.title, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                        color = Color(0xFF1A1A1A), modifier = Modifier.weight(1f),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    val roleColor = if (event.isOrganizer) PGreen900 else Color(0xFF059669)
                    val roleBg    = if (event.isOrganizer) PGreen100 else Color(0xFFECFDF5)
                    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(roleBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text(if (event.isOrganizer) "Organizer" else "Joined",
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = roleColor)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(diffBg)
                        .padding(horizontal = 7.dp, vertical = 3.dp)) {
                        Text(event.difficulty, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = diffFg)
                    }
                    if (event.distanceKm > 0) PChip(Icons.Default.Route, String.format("%.0f km", event.distanceKm))
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.CalendarMonth, null, tint = PGreen900, modifier = Modifier.size(14.dp))
                    Text(buildString {
                        append(formatEventDate(event.date))
                        if (event.time.isNotBlank()) append(" · ${event.time}")
                    }, fontSize = 12.sp, color = Color(0xFF555555))
                }

                if (event.route.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.LocationOn, null, tint = Color(0xFF9E9E9E), modifier = Modifier.size(14.dp))
                        Text(event.route, fontSize = 12.sp, color = Color(0xFF777777),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ride card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun RideCard(ride: SavedRide, formatDuration: (Long) -> String) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(PGreen100),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.DirectionsBike, null, tint = PGreen900, modifier = Modifier.size(20.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ride.name, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                            color = Color(0xFF1A1A1A), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (ride.lastRidden.isNotBlank())
                            Text(ride.lastRidden, fontSize = 11.sp, color = Color(0xFF9E9E9E))
                    }
                }
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(PGreen100)
                    .padding(horizontal = 9.dp, vertical = 4.dp)) {
                    Text("×${ride.timesRidden}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PGreen900)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniStat("Distance",  String.format("%.2f km", ride.distanceKm))
                MiniStatDivider()
                MiniStat("Duration",  formatDuration(ride.durationMin))
                MiniStatDivider()
                MiniStat("Avg speed", String.format("%.1f km/h", ride.avgSpeedKmh))
            }

            HorizontalDivider(color = Color(0xFFF0F0F0))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniStat("Max speed", String.format("%.1f km/h", ride.maxSpeedKmh))
                MiniStatDivider()
                MiniStat("Elevation", String.format("%.0f m ↑", ride.elevationM))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Atoms
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PChip(icon: ImageVector, text: String) {
    Row(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFF2F5F3))
        .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = PGreen900, modifier = Modifier.size(12.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF444444))
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = PGreen900)
        Text(label, fontSize = 10.sp, color = Color(0xFF9E9E9E))
    }
}

@Composable
private fun MiniStatDivider() {
    Box(modifier = Modifier.width(1.dp).height(28.dp).background(Color(0xFFF0F0F0)))
}