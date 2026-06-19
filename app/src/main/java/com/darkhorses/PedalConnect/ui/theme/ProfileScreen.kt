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
import androidx.compose.material3.rememberModalBottomSheetState
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
private typealias ProfilePost = Post

internal data class JoinedEvent(
    val id: String, val title: String, val route: String,
    val date: Long, val time: String, val difficulty: String,
    val distanceKm: Double, val isOrganizer: Boolean,
    val status: String = "approved"
)

internal data class SavedRide(
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

    var userDisplayName by remember { mutableStateOf("") }
    var userEmail     by remember { mutableStateOf<String?>(null) }
    var userCreatedAt by remember { mutableStateOf<String?>(null) }
    var userBio       by remember { mutableStateOf<String?>(null) }
    var userBikeTypes by remember { mutableStateOf<List<String>>(emptyList()) }
    var userSkillLevel by remember { mutableStateOf<String?>(null) }
    var photoUrl         by remember { mutableStateOf<String?>(null) }
    var isUploadingPhoto by remember { mutableStateOf(false) }
    var userDocId        by remember { mutableStateOf<String?>(null) }
    var subTab           by remember { mutableIntStateOf(0) }
    var showRidesSheet   by remember { mutableStateOf(false) }
    val ridesSheetState  = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedEvent    by remember { mutableStateOf<RideEvent?>(null) }

    val myPosts      = remember { mutableStateListOf<ProfilePost>() }
    // Tracks optimistic like state independently of the snapshot listener
    // key = postId, value = true (liked) / false (unliked)
    val likeOverrides = remember { mutableStateMapOf<String, Boolean>() }
    val joinedEvents = remember { mutableStateListOf<JoinedEvent>() }
    val myRides      = remember { mutableStateListOf<SavedRide>() }

    var isLoadingPosts  by remember { mutableStateOf(true) }
    var isLoadingEvents by remember { mutableStateOf(true) }
    var isLoadingRides  by remember { mutableStateOf(true) }

    var showEditDialog   by remember { mutableStateOf(false) }
    var editingPost      by remember { mutableStateOf<ProfilePost?>(null) }
    var editDescription  by remember { mutableStateOf("") }
    var isSavingEdit     by remember { mutableStateOf(false) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletingPost     by remember { mutableStateOf<ProfilePost?>(null) }
    var isDeletingPost   by remember { mutableStateOf(false) }

    // Stats — all posts (accepted + pending) as rider's activity count
    val totalPostCount  by remember { derivedStateOf { myPosts.count { it.status == "accepted" } } }
    val totalLikesCount by remember { derivedStateOf {
        myPosts.filter { it.status == "accepted" }.sumOf { post ->
            val wasLikedInFirestore = post.likedBy.contains(userName)
            val isLiked = likeOverrides[post.id] ?: wasLikedInFirestore
            when {
                isLiked && !wasLikedInFirestore -> post.likes + 1
                !isLiked && wasLikedInFirestore -> post.likes - 1
                else                            -> post.likes
            }
        }
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

                    val imgBBApiKey = com.darkhorses.PedalConnect.BuildConfig.IMGBB_API_KEY

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
            .limit(1)
            .addSnapshotListener { snap, _ ->
                snap?.documents?.firstOrNull()?.let { doc ->
                    userDocId       = doc.id
                    photoUrl        = doc.getString("photoUrl")
                    userEmail       = doc.getString("email")
                    userDisplayName = doc.getString("displayName") ?: ""
                    userCreatedAt   = doc.getTimestamp("createdAt")?.toDate()?.let {
                        java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.getDefault()).format(it)
                    }
                    userBio        = doc.getString("bio")
                    userBikeTypes  = when (val raw = doc.get("bikeTypes")) {
                        is List<*> -> raw.filterIsInstance<String>()
                        else -> {
                            val legacy = doc.getString("bikeType") ?: ""
                            if (legacy.isNotBlank()) listOf(legacy) else emptyList()
                        }
                    }
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
                    val poly = doc.safePolyline()
                    myPosts.add(ProfilePost(
                        id            = doc.id,
                        userName      = doc.getString("userName")      ?: userName,
                        displayName   = doc.getString("displayName")   ?: "",
                        description   = doc.getString("description")   ?: "",
                        activity      = doc.getString("activity")      ?: "Cycling Ride",
                        distance      = doc.getString("distance")      ?: "0",
                        timestamp     = doc.getLong("timestamp")       ?: 0L,
                        likes         = (doc.getLong("likes") ?: 0L).toInt(),
                        status        = doc.getString("status")        ?: "pending",
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
                isLoadingPosts = false
            }
    }

    LaunchedEffect(userName) {
        db.collection("rideEvents")
            .whereArrayContains("participants", userName)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { isLoadingEvents = false; return@addSnapshotListener }
                val loaded = mutableListOf<JoinedEvent>()
                for (doc in snap.documents) {
                    if (doc.getString("status") == "rejected") continue
                    loaded.add(JoinedEvent(
                        id          = doc.id,
                        title       = doc.getString("title")       ?: "Unnamed Ride",
                        route       = doc.getString("route")       ?: "",
                        date        = doc.getLong("date")          ?: 0L,
                        time        = doc.getString("time")        ?: "",
                        difficulty  = doc.getString("difficulty")  ?: "Easy",
                        distanceKm  = doc.getDouble("distanceKm") ?: 0.0,
                        isOrganizer = doc.getString("organizer")   == userName,
                        status      = doc.getString("status")      ?: "approved"
                    ))
                }
                // Upcoming events first, then past — within each group newest date first
                val now = System.currentTimeMillis()
                joinedEvents.clear()
                joinedEvents.addAll(
                    loaded.sortedWith(compareBy<JoinedEvent> { it.date < now }.thenByDescending { it.date })
                )
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
    // One-time repair: resync likes integer from likedBy array for any post where they diverge
    LaunchedEffect(userName) {
        db.collection("posts").whereEqualTo("userName", userName).get()
            .addOnSuccessListener { snap ->
                for (doc in snap.documents) {
                    val likedBy = (doc.get("likedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    val storedLikes = (doc.getLong("likes") ?: 0L).toInt()
                    if (storedLikes != likedBy.size) {
                        doc.reference.update("likes", likedBy.size)
                    }
                }
            }
    }

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
            .addOnFailureListener {
                // Non-critical — stat simply stays 0 if this fails
            }
    }

    // ── Edit / Delete actions ─────────────────────────────────────────────────
    fun saveEdit() {
        val post = editingPost ?: return
        if (editDescription.isBlank()) return
        isSavingEdit = true
        db.collection("posts").document(post.id).update(
            "description", editDescription.trim(),
            "editedAt", System.currentTimeMillis()
        )
            .addOnSuccessListener {
                isSavingEdit = false
                val now = System.currentTimeMillis()
                val idx = myPosts.indexOfFirst { it.id == post.id }
                if (idx != -1) {
                    myPosts[idx] = myPosts[idx].copy(
                        description = editDescription.trim(),
                        editedAt    = now
                    )
                }
                showEditDialog = false; editingPost = null
                Toast.makeText(context, "Post updated!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                isSavingEdit = false
                Toast.makeText(context, "Failed to update: ${e.message?.take(60) ?: "Unknown error"}", Toast.LENGTH_LONG).show()
            }
    }

    fun deletePost() {
        val post = deletingPost ?: return
        isDeletingPost = true
        db.collection("posts").document(post.id).delete()
            .addOnSuccessListener {
                isDeletingPost = false; showDeleteDialog = false; deletingPost = null
                myPosts.removeAll { it.id == post.id }
                likeOverrides.remove(post.id)
                Toast.makeText(context, "Post deleted.", Toast.LENGTH_SHORT).show()
                if (post.imageDeleteUrl.isNotBlank()) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val conn = java.net.URL(post.imageDeleteUrl)
                                .openConnection() as java.net.HttpURLConnection
                            conn.requestMethod = "GET"
                            conn.connectTimeout = 10_000
                            conn.responseCode
                            conn.disconnect()
                        } catch (e: Exception) { /* best-effort */ }
                    }
                }
            }
            .addOnFailureListener { e ->
                isDeletingPost = false
                Toast.makeText(context, "Failed to delete: ${e.message?.take(60) ?: "Unknown error"}", Toast.LENGTH_LONG).show()
            }
    }

    // ── Formatters ────────────────────────────────────────────────────────────
    fun formatStatNum(n: Int): String = when {
        n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000f)
        n >= 1_000     -> String.format("%.1fk", n / 1_000f)
        else           -> "$n"
    }

    fun formatTs(ts: Long): String =
        if (ts == 0L) "" else SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ts))

    fun formatEventDate(ts: Long): String =
        if (ts == 0L) "Date TBA"
        else SimpleDateFormat("EEE, MMM d · yyyy", Locale.getDefault()).format(Date(ts))

    fun formatDuration(min: Long): String {
        val h = min / 60; val m = min % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    // ── Edit dialog ───────────────────────────────────────────────────────────
    if (showEditDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { if (!isSavingEdit) { showEditDialog = false; editingPost = null } }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = PBgSurface),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(PGreen50), Alignment.Center) {
                            Icon(Icons.Default.Edit, null, tint = PGreen900, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Edit Post", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PTextPrimary)
                            Text("Update your description", fontSize = 12.sp, color = PTextMuted)
                        }
                    }
                    HorizontalDivider(color = PDivider)
                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { if (it.length <= 300) editDescription = it },
                        label = { Text("Description", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        shape = RoundedCornerShape(12.dp), maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PGreen700, unfocusedBorderColor = PDivider,
                            focusedLabelColor = PGreen700, cursorColor = PGreen700,
                            focusedTextColor = PTextPrimary, unfocusedTextColor = PTextPrimary
                        ),
                        supportingText = {
                            Text("${editDescription.length}/300",
                                color = if (editDescription.length > 280) Color(0xFFDC2626) else PTextMuted,
                                fontSize = 11.sp, modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End)
                        }
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { showEditDialog = false; editingPost = null },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp), enabled = !isSavingEdit,
                            border = androidx.compose.foundation.BorderStroke(1.dp, PDivider),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PTextSecondary)
                        ) { Text("Cancel", fontWeight = FontWeight.Medium) }
                        Button(
                            onClick = { saveEdit() },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isSavingEdit && editDescription.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = PGreen900, contentColor = Color.White)
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
            shape = RoundedCornerShape(24.dp), containerColor = PBgSurface,
            icon = {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Color(0xFFFEF2F2)), Alignment.Center) {
                    Icon(Icons.Default.DeleteForever, null, tint = Color(0xFFDC2626), modifier = Modifier.size(28.dp))
                }
            },
            title = { Text("Delete this post?", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PTextPrimary, textAlign = TextAlign.Center) },
            text = {
                Text("This will permanently remove your post from the community feed.",
                    fontSize = 14.sp, color = PTextSecondary, lineHeight = 22.sp, textAlign = TextAlign.Center)
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { deletePost() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp), enabled = !isDeletingPost,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White)
                    ) {
                        if (isDeletingPost) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Delete post", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    OutlinedButton(
                        onClick = { showDeleteDialog = false; deletingPost = null },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp), enabled = !isDeletingPost,
                        border = androidx.compose.foundation.BorderStroke(1.dp, PDivider),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PTextSecondary)
                    ) { Text("Cancel", fontWeight = FontWeight.Medium, fontSize = 15.sp) }
                }
            }
        )
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

        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {

            LazyColumn(
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = paddingValues.calculateBottomPadding() + 32.dp)
            ) {

                // ── Profile hero ──────────────────────────────────────────────────
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(PGreen900, PGreen800),
                                    startY = 0f,
                                    endY   = 400f
                                )
                            )
                            .padding(horizontal = 20.dp, vertical = 20.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            // ── Avatar row ────────────────────────────────────
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .padding(top = 20.dp, bottom = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Avatar
                                Box(
                                    modifier         = Modifier.size(72.dp),
                                    contentAlignment = Alignment.BottomEnd
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.12f))
                                            .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
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
                                                fontSize   = 28.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color      = Color.White
                                            )
                                        }
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = isUploadingPhoto,
                                            enter   = fadeIn(),
                                            exit    = fadeOut()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    color       = Color.White,
                                                    modifier    = Modifier.size(22.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                        }
                                    }
                                    // Camera badge
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(PBgSurface)
                                            .border(1.dp, PGreen900, CircleShape)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication        = null
                                            ) { if (!isUploadingPhoto) photoPicker.launch("image/*") },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.CameraAlt, "Change photo",
                                            tint     = PGreen900,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }

                                // Name / email / joined date
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        userDisplayName.ifBlank { userName },
                                        fontSize      = 18.sp,
                                        fontWeight    = FontWeight.Bold,
                                        color         = Color.White,
                                        letterSpacing = (-0.3).sp,
                                        maxLines      = 1,
                                        overflow      = TextOverflow.Ellipsis
                                    )
                                    if (!userEmail.isNullOrBlank()) {
                                        Text(
                                            userEmail!!,
                                            fontSize = 12.sp,
                                            color    = Color.White.copy(alpha = 0.65f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (!userCreatedAt.isNullOrBlank()) {
                                        Row(
                                            verticalAlignment     = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.CalendarMonth, null,
                                                tint     = Color.White.copy(alpha = 0.45f),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                "Since $userCreatedAt",
                                                fontSize = 11.sp,
                                                color    = Color.White.copy(alpha = 0.45f)
                                            )
                                        }
                                    }
                                    if (isUploadingPhoto) {
                                        Text(
                                            "Uploading photo…",
                                            fontSize = 11.sp,
                                            color    = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }

                            // ── Frosted inset: bio + pills ────────────────────
                            if (!userBio.isNullOrBlank() || userBikeTypes.isNotEmpty() || !userSkillLevel.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.18f))
                                        .padding(horizontal = 20.dp, vertical = 12.dp)
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (!userBio.isNullOrBlank()) {
                                            Text(
                                                userBio!!,
                                                fontSize   = 13.sp,
                                                color      = Color.White.copy(alpha = 0.85f),
                                                lineHeight = 19.sp,
                                                modifier   = Modifier.fillMaxWidth()
                                            )
                                        }
                                        if (userBikeTypes.isNotEmpty() || !userSkillLevel.isNullOrBlank()) {
                                            androidx.compose.foundation.layout.FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement   = Arrangement.spacedBy(6.dp),
                                                modifier              = Modifier.fillMaxWidth()
                                            ) {
                                                userBikeTypes.forEach { bikeType ->
                                                    Row(
                                                        modifier = Modifier
                                                            .wrapContentWidth()
                                                            .clip(RoundedCornerShape(20.dp))
                                                            .background(Color.White.copy(alpha = 0.12f))
                                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment     = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.DirectionsBike, null,
                                                            tint     = Color.White.copy(alpha = 0.75f),
                                                            modifier = Modifier.size(10.dp)
                                                        )
                                                        Text(
                                                            bikeType,
                                                            fontSize   = 10.sp,
                                                            color      = Color.White.copy(alpha = 0.85f),
                                                            fontWeight = FontWeight.Medium,
                                                            softWrap   = false
                                                        )
                                                    }
                                                }
                                                if (!userSkillLevel.isNullOrBlank()) {
                                                    Row(
                                                        modifier = Modifier
                                                            .wrapContentWidth()
                                                            .clip(RoundedCornerShape(20.dp))
                                                            .background(Color.White.copy(alpha = 0.12f))
                                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment     = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Star, null,
                                                            tint     = Color.White.copy(alpha = 0.75f),
                                                            modifier = Modifier.size(10.dp)
                                                        )
                                                        Text(
                                                            userSkillLevel!!,
                                                            fontSize   = 10.sp,
                                                            color      = Color.White.copy(alpha = 0.85f),
                                                            fontWeight = FontWeight.Medium,
                                                            softWrap   = false
                                                        )
                                                    }
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
                        colors    = CardDefaults.cardColors(containerColor = PBgSurface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier          = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PStatCell(
                                icon     = Icons.Default.Article,
                                value    = formatStatNum(totalPostCount),
                                label    = "Posts",
                                modifier = Modifier.weight(1f)
                            )
                            Box(Modifier.width(1.dp).height(32.dp).background(PDivider))
                            PStatCell(
                                icon     = Icons.Default.Groups,
                                value    = formatStatNum(totalEventsJoinedCount),
                                label    = "Events",
                                modifier = Modifier.weight(1f)
                            )
                            Box(Modifier.width(1.dp).height(32.dp).background(PDivider))
                            PStatCell(
                                icon     = Icons.Default.Favorite,
                                value    = formatStatNum(totalLikesCount),
                                label    = "Likes",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // ── Tab row with Saved Rides icon button ──────────────────────────
                item {
                    Spacer(Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Tab row — right padding reserves space for the icon button
                        TabRow(
                            selectedTabIndex = subTab,
                            containerColor   = PBgSurface,
                            contentColor     = PGreen900,
                            modifier         = Modifier.padding(end = 52.dp),
                            indicator = { tabPositions ->
                                SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[subTab]),
                                    color    = PGreen900
                                )
                            },
                            divider = { HorizontalDivider(color = PDivider, thickness = 1.dp) }
                        ) {
                            Tab(
                                selected               = subTab == 0,
                                onClick                = { subTab = 0 },
                                selectedContentColor   = PGreen900,
                                unselectedContentColor = PTextMuted,
                                text = {
                                    Text(
                                        "Community Posts",
                                        fontWeight = if (subTab == 0) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize   = 13.sp,
                                        maxLines   = 1,
                                        modifier   = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            )
                            Tab(
                                selected               = subTab == 1,
                                onClick                = { subTab = 1 },
                                selectedContentColor   = PGreen900,
                                unselectedContentColor = PTextMuted,
                                text = {
                                    Text(
                                        "Ride Events",
                                        fontWeight = if (subTab == 1) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize   = 13.sp,
                                        maxLines   = 1,
                                        modifier   = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            )
                        }

                        // Saved Rides icon button — pinned to the right
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp)
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PGreen700)
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
                            // Count badge
                            if (myRides.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 6.dp, end = 6.dp)
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(PGreen900),
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

                // ═════════════════════════════════════════════════════════════════
                // Ride Events / Community Posts
                // ═════════════════════════════════════════════════════════════════
                // ── Community Posts (subTab 0) ────────────────────────────────
                if (subTab == 0) {
                    if (isLoadingPosts) {
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
                    } else if (myPosts.isEmpty()) {
                        item {
                            PEmptyState(
                                icon    = Icons.Default.Feed,
                                title   = "No posts yet",
                                message = "Share a ride to get started!"
                            )
                        }
                    } else {
                        items(myPosts, key = { "${it.id}_${it.editedAt}_${it.description.length}" }) { post ->
                            val wasLikedInFirestore = post.likedBy.contains(userName)
                            val isLiked = likeOverrides[post.id] ?: wasLikedInFirestore
                            // Adjust the real likes integer by ±1 only if override disagrees with Firestore
                            val displayedLikes = when {
                                isLiked && !wasLikedInFirestore -> post.likes + 1  // optimistically added
                                !isLiked && wasLikedInFirestore -> post.likes - 1  // optimistically removed
                                else                            -> post.likes       // in sync, use real value
                            }
                            val displayedPost = post.copy(
                                likedBy = if (isLiked)
                                    (post.likedBy + userName).distinct()
                                else
                                    post.likedBy - userName,
                                likes = displayedLikes
                            )
                            CommunityFeedCard(
                                post = displayedPost,
                                currentUser    = userName,
                                viewerIsAuthor = post.userName == userName,
                                onLike = {
                                    val currentlyLiked = likeOverrides[post.id] ?: post.likedBy.contains(userName)
                                    // Set override immediately — snapshot can't race this
                                    likeOverrides[post.id] = !currentlyLiked
                                    val ref = db.collection("posts").document(post.id)
                                    if (currentlyLiked) {
                                        ref.update(
                                            "likedBy", com.google.firebase.firestore.FieldValue.arrayRemove(userName),
                                            "likes",   com.google.firebase.firestore.FieldValue.increment(-1)
                                        ).addOnFailureListener {
                                            // Revert override on failure
                                            likeOverrides[post.id] = currentlyLiked
                                        }
                                    } else {
                                        ref.update(
                                            "likedBy", com.google.firebase.firestore.FieldValue.arrayUnion(userName),
                                            "likes",   com.google.firebase.firestore.FieldValue.increment(1)
                                        ).addOnFailureListener {
                                            likeOverrides[post.id] = currentlyLiked
                                        }
                                    }
                                },
                                onEdit    = { editingPost = post; editDescription = post.description; showEditDialog = true },
                                onDelete  = { deletingPost = post; showDeleteDialog = true },
                                photoUrl  = photoUrl,
                                isAdmin   = false
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                // ── Ride Events (subTab 1) ────────────────────────────────────
                if (subTab == 1) {
                    if (isLoadingEvents) {
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
                    } else if (joinedEvents.isEmpty()) {
                        item {
                            PEmptyState(
                                icon    = Icons.AutoMirrored.Filled.DirectionsBike,
                                title   = "No events yet",
                                message = "Join a group ride event to see it here."
                            )
                        }
                    } else {
                        items(joinedEvents, key = { "event_${it.id}" }) { event ->
                            val rideEvent = RideEvent(
                                id             = event.id,
                                title          = event.title,
                                route          = event.route,
                                date           = event.date,
                                time           = event.time,
                                difficulty     = event.difficulty,
                                distanceKm     = event.distanceKm,
                                status         = event.status,
                                participants   = if (event.isOrganizer)
                                    listOf(userName) else listOf(userName),
                                organizer      = if (event.isOrganizer) userName else ""
                            )
                            EventCard(
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
            } // end LazyColumn
        } // end Column

        // ── Event Detail Sheet ────────────────────────────────────────────────
        selectedEvent?.let { event ->
            EventDetailSheet(
                event               = event,
                userName            = userName,
                onJoin              = {
                    val db2 = FirebaseFirestore.getInstance()
                    val ref = db2.collection("rideEvents").document(event.id)
                    if (event.participants.contains(userName)) {
                        ref.update("participants", com.google.firebase.firestore.FieldValue.arrayRemove(userName))
                            .addOnSuccessListener {
                                Toast.makeText(context, "Left the ride.", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        ref.update("participants", com.google.firebase.firestore.FieldValue.arrayUnion(userName))
                            .addOnSuccessListener {
                                Toast.makeText(context, "Joined! 🚴", Toast.LENGTH_SHORT).show()
                            }
                    }
                },
                onDelete            = {},
                onEdit              = {},
                onCheckIn           = {
                    val db2 = FirebaseFirestore.getInstance()
                    db2.collection("rideEvents").document(event.id)
                        .update("attendees", com.google.firebase.firestore.FieldValue.arrayUnion(userName))
                        .addOnSuccessListener {
                            Toast.makeText(context, "Checked in! See you on the road 🚴", Toast.LENGTH_SHORT).show()
                            // Refresh selectedEvent so the sheet reflects the new attendees list
                            db2.collection("rideEvents").document(event.id)
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
                    val prefs = context.getSharedPreferences("PedalConnectPrefs", android.content.Context.MODE_PRIVATE)
                    prefs.edit().putString("pending_destination", destination).apply()
                    navController.navigate("home/$userName") {
                        popUpTo("home/$userName") { inclusive = false }
                    }
                },
                onViewProfile       = { targetUser ->
                    if (targetUser != userName)
                        navController.navigate("public_profile/$targetUser")
                }
            )
        }

        // ── Saved Rides bottom sheet ──────────────────────────────────────────
        if (showRidesSheet) {
            ModalBottomSheet(
                onDismissRequest  = { showRidesSheet = false },
                sheetState        = ridesSheetState,
                containerColor    = PBgCanvas,
                dragHandle        = {
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp, bottom = 8.dp)
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(PDivider)
                    )
                }
            ) {
                // Sheet header
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
                                .background(PGreen50),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Bookmarks, null,
                                tint     = PGreen900,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            "Saved Routes",
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color      = PTextPrimary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(PGreen100)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "${myRides.size}",
                                fontSize   = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color      = PGreen900
                            )
                        }
                    }
                    IconButton(onClick = { showRidesSheet = false }) {
                        Icon(
                            Icons.Default.Close, "Close",
                            tint     = PTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(color = PDivider, thickness = 1.dp)
                Spacer(Modifier.height(8.dp))

                // Sheet content
                if (isLoadingRides) {
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
                } else if (myRides.isEmpty()) {
                    PEmptyState(
                        icon    = Icons.AutoMirrored.Filled.DirectionsBike,
                        title   = "No saved rides yet",
                        message = "Complete a ride and save the route to see it here."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start  = 0.dp,
                            end    = 0.dp,
                            top    = 0.dp,
                            bottom = paddingValues.calculateBottomPadding() + 32.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(myRides, key = { it.id }) { ride ->
                            RideCard(ride = ride, formatDuration = ::formatDuration)
                        }
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
private fun PStatCell(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier,
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

// ── Event card ────────────────────────────────────────────────────────────────
@Composable
private fun EventCard(event: JoinedEvent, formatEventDate: (Long) -> String, onTap: () -> Unit = {}) {
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
    val rideEvent = RideEvent(
        id = event.id, title = event.title, route = event.route,
        date = event.date, time = event.time, difficulty = event.difficulty,
        distanceKm = event.distanceKm, status = event.status
    )
    val timeStatus = getEventTimeStatus(rideEvent)
    val isPast     = timeStatus == EventStatus.ENDED

    val headerGradient = if (isPast)
        Brush.horizontalGradient(listOf(Color(0xFF6B7280), Color(0xFF9CA3AF)))
    else if (event.isOrganizer)
        Brush.horizontalGradient(listOf(PGreen950, PGreen800))
    else
        Brush.horizontalGradient(listOf(PGreen900, PGreen700))

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onTap() },
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = PBgSurface),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPast) 0.dp else 2.dp,
            pressedElevation = 4.dp
        )
    ) {
        Column {
            // ── Coloured header band ──────────────────────────────────────
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
                    // Title
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
                    // Role pill
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
                    // Difficulty
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
                            color      = if (isPast) PTextMuted else diffFg
                        )
                    }
                    // Status
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
                                .background(PGreen50)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Route, null,
                                    tint = PGreen700, modifier = Modifier.size(10.dp))
                                Text(
                                    String.format("%.0f km", event.distanceKm),
                                    fontSize   = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = PGreen700
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = PDivider, thickness = 0.5.dp)

                // Date + time
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isPast) Color(0xFFF3F4F6) else PGreen50),
                        Alignment.Center
                    ) {
                        Icon(Icons.Default.CalendarMonth, null,
                            tint     = if (isPast) PTextMuted else PGreen900,
                            modifier = Modifier.size(14.dp))
                    }
                    Text(
                        buildString {
                            append(formatEventDate(event.date))
                            if (event.time.isNotBlank()) append("  ·  ${event.time}")
                        },
                        fontSize = 12.sp,
                        color    = if (isPast) PTextMuted else PTextSecondary,
                        fontWeight = FontWeight.Medium
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
                                tint     = PTextMuted,
                                modifier = Modifier.size(14.dp))
                        }
                        Text(
                            event.route,
                            fontSize = 12.sp,
                            color    = PTextMuted,
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

// ── Ride card ─────────────────────────────────────────────────────────────────
@Composable
internal fun RideCard(ride: SavedRide, formatDuration: (Long) -> String) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = PBgSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(listOf(PGreen900, PGreen700)),
                        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier            = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            ride.name,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 15.sp,
                            color      = Color.White,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        if (ride.lastRidden.isNotBlank()) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.History, null,
                                    tint     = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(11.dp))
                                Text(
                                    ride.lastRidden,
                                    fontSize = 11.sp,
                                    color    = Color.White.copy(alpha = 0.65f)
                                )
                            }
                        }
                    }
                    // Times ridden badge
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            "×${ride.timesRidden}",
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = Color.White
                        )
                        Text(
                            "ridden",
                            fontSize = 9.sp,
                            color    = Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ── Stats grid ────────────────────────────────────────────────
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Primary stats row
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RideStatBlock(
                        label = "Distance",
                        value = String.format("%.1f", ride.distanceKm),
                        unit  = "km",
                        icon  = Icons.Default.Route,
                        modifier = Modifier.weight(1f)
                    )
                    RideStatBlock(
                        label = "Duration",
                        value = formatDuration(ride.durationMin),
                        unit  = "",
                        icon  = Icons.Default.Timer,
                        modifier = Modifier.weight(1f)
                    )
                    RideStatBlock(
                        label = "Elevation",
                        value = String.format("%.0f", ride.elevationM),
                        unit  = "m ↑",
                        icon  = Icons.Default.Terrain,
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(color = PDivider, thickness = 0.5.dp)

                // Speed row
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RideStatBlock(
                        label = "Avg Speed",
                        value = String.format("%.1f", ride.avgSpeedKmh),
                        unit  = "km/h",
                        icon  = Icons.Default.Speed,
                        modifier = Modifier.weight(1f)
                    )
                    RideStatBlock(
                        label = "Max Speed",
                        value = String.format("%.1f", ride.maxSpeedKmh),
                        unit  = "km/h",
                        icon  = Icons.Default.FlashOn,
                        modifier = Modifier.weight(1f)
                    )
                    // Empty spacer to keep grid balanced
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RideStatBlock(
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier            = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PBgCanvas)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = PGreen700, modifier = Modifier.size(11.dp))
            Text(label, fontSize = 10.sp, color = PTextMuted, fontWeight = FontWeight.Medium)
        }
        Row(
            verticalAlignment     = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                value,
                fontSize   = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = PTextPrimary
            )
            if (unit.isNotBlank()) {
                Text(
                    unit,
                    fontSize   = 9.sp,
                    color      = PTextMuted,
                    fontWeight = FontWeight.Medium,
                    modifier   = Modifier.padding(bottom = 1.dp)
                )
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
private fun PMiniStat(value: String, label: String) {
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