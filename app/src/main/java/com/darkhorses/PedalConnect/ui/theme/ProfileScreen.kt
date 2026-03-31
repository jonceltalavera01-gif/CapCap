package com.darkhorses.PedalConnect.ui.theme

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// ── Design tokens — consistent with app-wide system ──────────────────────────
private val PGreen950  = Color(0xFF052818)
private val PGreen900  = Color(0xFF06402B)
private val PGreen800  = Color(0xFF0A5C3D)
private val PGreen700  = Color(0xFF0D7050)
private val PGreen100  = Color(0xFFDDF1E8)
private val PGreen50   = Color(0xFFE8F5EE)
private val PBgCanvas  = Color(0xFFF5F7F6)
private val PBgSurface = Color(0xFFFFFFFF)
private val PTextPrimary   = Color(0xFF111827)
private val PTextSecondary = Color(0xFF374151)
private val PTextMuted     = Color(0xFF6B7280)
private val PDivider       = Color(0xFFE5E7EB)
private val PAmber500  = Color(0xFFF59E0B)

// ── Data models ───────────────────────────────────────────────────────────────
private data class ProfilePost(
    val id: String, val description: String, val activity: String,
    val distance: String, val timestamp: Long, val likes: Int,
    val status: String, val imageUrl: String,
    val rideStats: Map<String, Any>?
)

private data class JoinedEvent(
    val id: String, val title: String, val route: String,
    val date: Long, val time: String, val difficulty: String,
    val distanceKm: Double, val isOrganizer: Boolean
)

private data class SavedRide(
    val id: String, val name: String, val distanceKm: Double,
    val durationMin: Long, val avgSpeedKmh: Double, val maxSpeedKmh: Double,
    val elevationM: Double, val timesRidden: Int,
    val lastRidden: String, val timestamp: Long
)

// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController, userName: String, paddingValues: PaddingValues = PaddingValues()) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val db      = FirebaseFirestore.getInstance()

    var userEmail     by remember { mutableStateOf<String?>(null) }
    var userCreatedAt by remember { mutableStateOf<String?>(null) }
    var userBio       by remember { mutableStateOf<String?>(null) }
    var userBikeType  by remember { mutableStateOf<String?>(null) }
    var userSkillLevel by remember { mutableStateOf<String?>(null) }
    var photoUrl         by remember { mutableStateOf<String?>(null) }
    var isUploadingPhoto by remember { mutableStateOf(false) }
    var userDocId        by remember { mutableStateOf<String?>(null) }
    var selectedTab      by remember { mutableIntStateOf(0) }

    val myPosts      = remember { mutableStateListOf<ProfilePost>() }
    val joinedEvents = remember { mutableStateListOf<JoinedEvent>() }
    val myRides      = remember { mutableStateListOf<SavedRide>() }

    var isLoadingPosts  by remember { mutableStateOf(true) }
    var isLoadingEvents by remember { mutableStateOf(true) }
    var isLoadingRides  by remember { mutableStateOf(true) }

    // Stats — all posts (accepted + pending) as rider's activity count
    val totalPostCount  by remember { derivedStateOf { myPosts.size } }
    val totalLikesCount by remember { derivedStateOf {
        myPosts.filter { it.status == "accepted" }.sumOf { it.likes }
    } }
    // Events: tracked separately via totalEventsJoinedCount (all events, not just upcoming)

    // ── Photo picker ──────────────────────────────────────────────────────────
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isUploadingPhoto = true
            scope.launch {
                try {
                    val resolvedDocId: String? = userDocId
                        ?: run {
                            val snap = db.collection("users")
                                .whereEqualTo("username", userName)
                                .limit(1)
                                .get()
                                .await()
                            val doc = snap.documents.firstOrNull()
                            doc?.id?.also { userDocId = it }
                        }

                    if (resolvedDocId == null) {
                        isUploadingPhoto = false
                        Toast.makeText(context,
                            "Could not find your account. Please sign out and back in.",
                            Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    val imgBBApiKey = "697c37daa7aa4705b309befb3de1d384"

                    // Read bytes and upload entirely on IO thread
                    val url = withContext(Dispatchers.IO) {
                        val bytes  = context.contentResolver.openInputStream(uri)!!.readBytes()
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)

                        val connection = java.net.URL("https://api.imgbb.com/1/upload").openConnection()
                                as java.net.HttpURLConnection
                        connection.requestMethod   = "POST"
                        connection.doOutput        = true
                        connection.connectTimeout  = 30_000
                        connection.readTimeout     = 30_000
                        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                        val body = "key=$imgBBApiKey&image=${java.net.URLEncoder.encode(base64, "UTF-8")}"
                        connection.outputStream.write(body.toByteArray())
                        connection.outputStream.flush()

                        val response = connection.inputStream.bufferedReader().readText()
                        org.json.JSONObject(response)
                            .getJSONObject("data")
                            .getString("url")
                    }

                    db.collection("users")
                        .document(resolvedDocId)
                        .set(mapOf("photoUrl" to url), com.google.firebase.firestore.SetOptions.merge())
                        .await()

                    photoUrl         = url
                    isUploadingPhoto = false
                    Toast.makeText(context, "Profile photo updated! 🎉", Toast.LENGTH_SHORT).show()

                } catch (e: Exception) {
                    isUploadingPhoto = false
                    Toast.makeText(context,
                        "Upload failed: ${e.message?.take(80)}",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ── Data loading ──────────────────────────────────────────────────────────
    LaunchedEffect(userName) {
        db.collection("users").whereEqualTo("username", userName)
            .limit(1).get().addOnSuccessListener { snap ->
                snap.documents.firstOrNull()?.let { doc ->
                    userDocId     = doc.id
                    photoUrl      = doc.getString("photoUrl")
                    userEmail      = doc.getString("email")
                    userCreatedAt  = doc.getTimestamp("createdAt")?.toDate()?.let {
                        java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.getDefault()).format(it)
                    }
                    userBio        = doc.getString("bio")
                    userBikeType   = doc.getString("bikeType")
                    userSkillLevel = doc.getString("skillLevel")
                }
            }
    }

    LaunchedEffect(userName) {
        db.collection("posts").whereEqualTo("userName", userName)
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

    LaunchedEffect(userName) {
        db.collection("rideEvents")
            .whereArrayContains("participants", userName)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { isLoadingEvents = false; return@addSnapshotListener }
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                joinedEvents.clear()
                for (doc in snap.documents) {
                    if (doc.getString("status") == "rejected") continue
                    val eventDate = doc.getLong("date") ?: 0L
                    if (eventDate < today) continue
                    joinedEvents.add(JoinedEvent(
                        id          = doc.id,
                        title       = doc.getString("title")       ?: "Unnamed Ride",
                        route       = doc.getString("route")       ?: "",
                        date        = doc.getLong("date")          ?: 0L,
                        time        = doc.getString("time")        ?: "",
                        difficulty  = doc.getString("difficulty")  ?: "Easy",
                        distanceKm  = doc.getDouble("distanceKm") ?: 0.0,
                        isOrganizer = doc.getString("organizer")   == userName
                    ))
                }
                isLoadingEvents = false
            }
    }

    LaunchedEffect(userName) {
        db.collection("savedRoutes").whereEqualTo("userName", userName)
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
    // Total events joined including past ones — for stat display only
    var totalEventsJoinedCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(userName) {
        db.collection("rideEvents")
            .whereArrayContains("participants", userName)
            .get()
            .addOnSuccessListener { snap ->
                totalEventsJoinedCount = snap.documents.count {
                    it.getString("status") != "rejected"
                }
            }
    }

    // ── Formatters ────────────────────────────────────────────────────────────
    fun formatTs(ts: Long): String =
        if (ts == 0L) "" else SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ts))

    fun formatEventDate(ts: Long): String =
        if (ts == 0L) "Date TBA"
        else SimpleDateFormat("EEE, MMM d · yyyy", Locale.getDefault()).format(Date(ts))

    fun formatDuration(min: Long): String {
        val h = min / 60; val m = min % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Person, null,
                            tint = Color.White, modifier = Modifier.size(20.dp))
                        Text(
                            "My Profile",
                            fontWeight    = FontWeight.ExtraBold,
                            fontSize      = 20.sp,
                            color         = Color.White,
                            letterSpacing = 0.3.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, "Settings", tint = Color.White)
                    }
                },
                colors  = TopAppBarDefaults.topAppBarColors(containerColor = PGreen900),
                modifier = Modifier.shadow(elevation = 2.dp)
            )
        },
        containerColor = PBgCanvas
    ) { innerPadding ->

        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = paddingValues.calculateBottomPadding() + 32.dp)
        ) {

            // ── Profile hero ──────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(
                            colors = listOf(PGreen900, PGreen700),
                            startY = 0f,
                            endY   = 600f
                        ))
                        .padding(top = 24.dp, bottom = 52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Avatar with camera badge
                        Box(
                            modifier = Modifier.size(88.dp),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Box(
                                modifier = Modifier.size(88.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .border(2.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication        = null
                                    ) { if (!isUploadingPhoto) photoPicker.launch("image/*") },
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
                                    Text(
                                        userName.take(1).uppercase(),
                                        fontSize   = 34.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color      = Color.White
                                    )
                                }
                                // Upload overlay
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = isUploadingPhoto,
                                    enter   = fadeIn(),
                                    exit    = fadeOut()
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color       = Color.White,
                                            modifier    = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                            // Camera badge — 32dp visible, 44dp touch target
                            Box(
                                modifier = Modifier.size(44.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier.size(28.dp).clip(CircleShape)
                                        .background(PBgSurface)
                                        .border(1.5.dp, PGreen900, CircleShape)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication        = null
                                        ) { if (!isUploadingPhoto) photoPicker.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.CameraAlt, "Change photo",
                                        tint     = PGreen900,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                userName,
                                fontSize      = 20.sp,
                                fontWeight    = FontWeight.Bold,
                                color         = Color.White,
                                letterSpacing = (-0.3).sp
                            )
                            if (!userEmail.isNullOrBlank()) {
                                Text(
                                    userEmail!!,
                                    fontSize = 12.sp,
                                    color    = Color.White.copy(alpha = 0.75f)
                                )
                            }
                            if (!userCreatedAt.isNullOrBlank()) {
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CalendarMonth, null,
                                        tint     = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        "Member since $userCreatedAt",
                                        fontSize = 11.sp,
                                        color    = Color.White.copy(alpha = 0.55f)
                                    )
                                }
                            }

                            // Bio
                            Text(
                                text     = if (userBio.isNullOrBlank()) "No bio yet" else userBio!!,
                                fontSize = 12.sp,
                                color    = if (userBio.isNullOrBlank())
                                    Color.White.copy(alpha = 0.35f)
                                else
                                    Color.White.copy(alpha = 0.80f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier  = Modifier.padding(horizontal = 24.dp)
                            )

                            // Bike type + Skill level pills
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                // Bike type pill
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DirectionsBike, null,
                                            tint     = Color.White.copy(alpha = 0.75f),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text     = if (userBikeType.isNullOrBlank()) "No bike set" else userBikeType!!,
                                            fontSize = 11.sp,
                                            color    = if (userBikeType.isNullOrBlank())
                                                Color.White.copy(alpha = 0.35f)
                                            else
                                                Color.White.copy(alpha = 0.85f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                // Skill level pill
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Star, null,
                                            tint     = Color.White.copy(alpha = 0.75f),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text     = if (userSkillLevel.isNullOrBlank()) "No level set" else userSkillLevel!!,
                                            fontSize = 11.sp,
                                            color    = if (userSkillLevel.isNullOrBlank())
                                                Color.White.copy(alpha = 0.35f)
                                            else
                                                Color.White.copy(alpha = 0.85f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            if (isUploadingPhoto) {
                                Text(
                                    "Uploading photo…",
                                    fontSize = 11.sp,
                                    color    = Color.White.copy(alpha = 0.65f)
                                )
                            }
                        }
                    }
                }
            }

            // ── Stats card — overlaps hero bottom edge ────────────────────────
            item {
                Card(
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .offset(y = (-32).dp),
                    shape     = RoundedCornerShape(18.dp),
                    colors    = CardDefaults.cardColors(containerColor = PBgSurface),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        PStatCell(
                            icon  = Icons.Default.Article,
                            value = "$totalPostCount",
                            label = "Posts"
                        )
                        Box(Modifier.width(1.dp).height(36.dp).background(PDivider))
                        PStatCell(
                            icon  = Icons.Default.Groups,
                            value = "$totalEventsJoinedCount",
                            label = "Events"
                        )
                        Box(Modifier.width(1.dp).height(36.dp).background(PDivider))
                        PStatCell(
                            icon  = Icons.Default.Favorite,
                            value = "$totalLikesCount",
                            label = "Likes"
                        )
                        Box(Modifier.width(1.dp).height(36.dp).background(PDivider))
                        PStatCell(
                            icon  = Icons.AutoMirrored.Filled.DirectionsBike,
                            value = "${myRides.size}",
                            label = "Rides"
                        )
                    }
                }
                Spacer(Modifier.height(-16.dp))
            }

            // ── Tabs ──────────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor   = Color.White,
                    contentColor     = PGreen900,
                    indicator = { tabPositions ->
                        SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color    = PGreen900
                        )
                    },
                    divider = { HorizontalDivider(color = PDivider) }
                ) {
                    Tab(
                        selected               = selectedTab == 0,
                        onClick                = { selectedTab = 0 },
                        selectedContentColor   = PGreen900,
                        unselectedContentColor = PTextMuted,
                        text = {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier              = Modifier.padding(vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Feed, null, modifier = Modifier.size(15.dp))
                                Text(
                                    "Posts & Events",
                                    fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize   = 13.sp
                                )
                            }
                        }
                    )
                    Tab(
                        selected               = selectedTab == 1,
                        onClick                = { selectedTab = 1 },
                        selectedContentColor   = PGreen900,
                        unselectedContentColor = PTextMuted,
                        text = {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier              = Modifier.padding(vertical = 4.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.DirectionsBike, null, modifier = Modifier.size(15.dp))
                                Text(
                                    "Saved Rides",
                                    fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize   = 13.sp
                                )
                            }
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // ═════════════════════════════════════════════════════════════════
            // TAB 0 — Posts + Events
            // ═════════════════════════════════════════════════════════════════
            if (selectedTab == 0) {
                val loading = isLoadingPosts || isLoadingEvents
                if (loading) {
                    item {
                        Box(
                            modifier         = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color       = PGreen900,
                                strokeWidth = 2.5.dp,
                                modifier    = Modifier.size(32.dp)
                            )
                        }
                    }
                } else if (myPosts.isEmpty() && joinedEvents.isEmpty()) {
                    item {
                        PEmptyState(
                            icon    = Icons.AutoMirrored.Filled.DirectionsBike,
                            title   = "No activity yet",
                            message = "Share a ride or join a group event to get started!"
                        )
                    }
                } else {
                    if (myPosts.isNotEmpty()) {
                        item {
                            PSectionHeader(
                                icon  = Icons.Default.Feed,
                                title = "Community Posts",
                                count = "${myPosts.size}"
                            )
                        }
                        items(myPosts, key = { "post_${it.id}" }) { post ->
                            PostCard(post = post, formatTs = ::formatTs)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    if (joinedEvents.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            PSectionHeader(
                                icon  = Icons.AutoMirrored.Filled.DirectionsBike,
                                title = "Upcoming Ride Events",
                                count = "${joinedEvents.size}"
                            )
                        }
                        items(joinedEvents, key = { "event_${it.id}" }) { event ->
                            EventCard(event = event, formatEventDate = ::formatEventDate)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            // ═════════════════════════════════════════════════════════════════
            // TAB 1 — Saved Rides
            // ═════════════════════════════════════════════════════════════════
            if (selectedTab == 1) {
                if (isLoadingRides) {
                    item {
                        Box(
                            modifier         = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color       = PGreen900,
                                strokeWidth = 2.5.dp,
                                modifier    = Modifier.size(32.dp)
                            )
                        }
                    }
                } else if (myRides.isEmpty()) {
                    item {
                        PEmptyState(
                            icon    = Icons.AutoMirrored.Filled.DirectionsBike,
                            title   = "No saved rides yet",
                            message = "Complete a ride and save the route to see it here."
                        )
                    }
                } else {
                    items(myRides, key = { it.id }) { ride ->
                        RideCard(ride = ride, formatDuration = ::formatDuration)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

// ── Section header ────────────────────────────────────────────────────────────
@Composable
private fun PSectionHeader(icon: ImageVector, title: String, count: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = PGreen900, modifier = Modifier.size(16.dp))
        Text(
            title,
            fontWeight = FontWeight.Bold,
            fontSize   = 14.sp,
            color      = PTextPrimary,
            modifier   = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(PGreen100)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(count, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PGreen900)
        }
    }
}

// ── Stat cell ─────────────────────────────────────────────────────────────────
@Composable
private fun PStatCell(icon: ImageVector, value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(PGreen50),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = PGreen900, modifier = Modifier.size(18.dp))
        }
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = PTextPrimary)
        Text(label, fontSize = 10.sp, color = PTextMuted, fontWeight = FontWeight.Medium)
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────
@Composable
private fun PEmptyState(icon: ImageVector, title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(PGreen50),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = PGreen700, modifier = Modifier.size(32.dp))
        }
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            color = PTextPrimary, textAlign = TextAlign.Center)
        Text(message, fontSize = 13.sp, color = PTextMuted,
            textAlign = TextAlign.Center, lineHeight = 20.sp)
    }
}

// ── Post card ─────────────────────────────────────────────────────────────────
@Composable
private fun PostCard(post: ProfilePost, formatTs: (Long) -> String) {
    val isPending   = post.status == "pending"
    val statusColor = if (isPending) PAmber500 else Color(0xFF16A34A)
    val statusBg    = if (isPending) Color(0xFFFFFBEB) else Color(0xFFF0FAF5)
    val statusLabel = if (isPending) "Pending" else "Published"

    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = PBgSurface),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (post.imageUrl.isNotBlank()) {
                AsyncImage(
                    model              = post.imageUrl,
                    contentDescription = "Ride photo",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxWidth().height(180.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                                .background(PGreen50),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.DirectionsBike, null,
                                tint = PGreen900, modifier = Modifier.size(17.dp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(post.activity, fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp, color = PTextPrimary)
                            Text(formatTs(post.timestamp), fontSize = 11.sp, color = PTextMuted)
                        }
                    }
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(statusBg)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(statusLabel, fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold, color = statusColor)
                    }
                }

                if (post.distance.isNotBlank() && post.distance != "0") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PChip(Icons.AutoMirrored.Filled.DirectionsBike, "${post.distance} km")
                        post.rideStats?.let { stats ->
                            val dur = (stats["durationSec"] as? Long)?.let { it / 60 } ?: 0L
                            if (dur > 0) PChip(Icons.Default.Timer, "${dur}m")
                        }
                    }
                }

                if (post.description.isNotBlank()) {
                    Text(
                        post.description,
                        fontSize  = 13.sp,
                        color     = PTextSecondary,
                        lineHeight = 19.sp,
                        maxLines  = 3,
                        overflow  = TextOverflow.Ellipsis
                    )
                }

                HorizontalDivider(color = PDivider, thickness = 0.5.dp)

                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Favorite, null,
                        tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                    Text("${post.likes} like${if (post.likes != 1) "s" else ""}",
                        fontSize = 12.sp, color = PTextMuted)
                }
            }
        }
    }
}

// ── Event card ────────────────────────────────────────────────────────────────
@Composable
private fun EventCard(event: JoinedEvent, formatEventDate: (Long) -> String) {
    val diffFg = when (event.difficulty) {
        "Easy"     -> Color(0xFF166534)
        "Moderate" -> Color(0xFF9A3412)
        "Hard"     -> Color(0xFF991B1B)
        else       -> PTextSecondary
    }
    val diffBg = when (event.difficulty) {
        "Easy"     -> Color(0xFFDCFCE7)
        "Moderate" -> Color(0xFFFFEDD5)
        "Hard"     -> Color(0xFFFFE4E6)
        else       -> Color(0xFFF3F4F6)
    }

    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = PBgSurface),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(3.dp)
                    .background(diffFg, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        event.title,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp,
                        color      = PTextPrimary,
                        modifier   = Modifier.weight(1f),
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    val roleColor = if (event.isOrganizer) PGreen900 else Color(0xFF059669)
                    val roleBg    = if (event.isOrganizer) PGreen100 else Color(0xFFECFDF5)
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(roleBg).padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            if (event.isOrganizer) "Organizer" else "Joined",
                            fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = roleColor
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                            .background(diffBg).padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(event.difficulty, fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold, color = diffFg)
                    }
                    if (event.distanceKm > 0) {
                        PChip(Icons.Default.Route, String.format("%.0f km", event.distanceKm))
                    }
                }

                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, null,
                        tint = PGreen900, modifier = Modifier.size(13.dp))
                    Text(
                        buildString {
                            append(formatEventDate(event.date))
                            if (event.time.isNotBlank()) append(" · ${event.time}")
                        },
                        fontSize = 12.sp, color = PTextSecondary
                    )
                }

                if (event.route.isNotBlank()) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, null,
                            tint = PTextMuted, modifier = Modifier.size(13.dp))
                        Text(
                            event.route,
                            fontSize = 12.sp, color = PTextMuted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// ── Ride card ─────────────────────────────────────────────────────────────────
@Composable
private fun RideCard(ride: SavedRide, formatDuration: (Long) -> String) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = PBgSurface),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier              = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                            .background(PGreen50),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.DirectionsBike, null,
                            tint = PGreen900, modifier = Modifier.size(19.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ride.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            color = PTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (ride.lastRidden.isNotBlank()) {
                            Text(ride.lastRidden, fontSize = 11.sp, color = PTextMuted)
                        }
                    }
                }
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(PGreen50).padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("×${ride.timesRidden}", fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, color = PGreen900)
                }
            }

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PMiniStat("Distance",  String.format("%.1f km", ride.distanceKm))
                Box(Modifier.width(1.dp).height(28.dp).background(PDivider))
                PMiniStat("Duration",  formatDuration(ride.durationMin))
                Box(Modifier.width(1.dp).height(28.dp).background(PDivider))
                PMiniStat("Avg speed", String.format("%.1f km/h", ride.avgSpeedKmh))
            }

            HorizontalDivider(color = PDivider, thickness = 0.5.dp)

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PMiniStat("Max speed", String.format("%.1f km/h", ride.maxSpeedKmh))
                Box(Modifier.width(1.dp).height(28.dp).background(PDivider))
                PMiniStat("Elevation", String.format("%.0f m ↑", ride.elevationM))
            }
        }
    }
}

// ── Atoms ─────────────────────────────────────────────────────────────────────
@Composable
private fun PChip(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(PGreen50)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = PGreen900, modifier = Modifier.size(11.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = PTextSecondary)
    }
}

@Composable
private fun PMiniStat(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PGreen900)
        Text(label, fontSize = 10.sp, color = PTextMuted)
    }
}

// ── Backward compat constants (referenced by other files) ─────────────────────
val VALID_SKILL_LEVELS = listOf("Beginner", "Intermediate", "Advanced", "Expert")
val VALID_BIKE_TYPES   = listOf(
    "Road Bike", "Mountain Bike (MTB)", "Folding Bike", "Fixed Gear / Fixie",
    "Gravel Bike", "Hybrid Bike", "BMX Bike", "City / Commuter Bike",
    "E-Bike (Electric)", "Other"
)

suspend fun fetchUserData(
    userName: String, db: FirebaseFirestore,
    onStart: () -> Unit, onSuccess: (String, String, String) -> Unit,
    onError: (String) -> Unit, onComplete: () -> Unit
) {
    onStart()
    try {
        val snap = db.collection("users").whereEqualTo("username", userName).get().await()
        if (snap.isEmpty) { onSuccess("User not found", "Not specified", "Not specified"); return }
        val doc = snap.documents.firstOrNull() ?: run { onError("User document is null"); return }
        onSuccess(
            doc.getString("email")      ?: "No email",
            doc.getString("bikeType")   ?: "Not specified",
            doc.getString("skillLevel") ?: "Not specified"
        )
    } catch (e: Exception) { onError("Error: ${e.message}") }
    finally { onComplete() }
}