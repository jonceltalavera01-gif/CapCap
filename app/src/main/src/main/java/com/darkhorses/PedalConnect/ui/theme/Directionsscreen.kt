package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

// ── Colour tokens ─────────────────────────────────────────────────────────────
private val DGreen900   = Color(0xFF06402B)
private val DGreen700   = Color(0xFF0A5C3D)
private val DGreen100   = Color(0xFFE8F5E9)
private val DGreen50    = Color(0xFFF1F8F4)
private val DSurfaceBg  = Color(0xFFF2F5F3)
private val DOnSurface  = Color(0xFF1A1A1A)
private val DTextSub    = Color(0xFF6B7B6B)
private val DCommunity  = Color(0xFF3949AB)

// ── Data models ───────────────────────────────────────────────────────────────
data class SavedRoute(
    val id: String          = "",
    val name: String        = "",
    val distanceKm: Double  = 0.0,
    val durationMin: Int    = 0,
    val timesRidden: Int    = 0,
    val lastRidden: String  = "",
    val startLabel: String  = "",
    val endLabel: String    = "",
    val isShared: Boolean   = false,
    val startLat: Double    = 0.0,
    val startLon: Double    = 0.0,
    val endLat: Double      = 0.0,
    val endLon: Double      = 0.0
)

data class CommunityRoute(
    val id: String          = "",
    val userName: String    = "",
    val name: String        = "",
    val distanceKm: Double  = 0.0,
    val durationMin: Int    = 0,
    val avgSpeedKmh: Double = 0.0,
    val startLabel: String  = "",
    val savedCount: Int     = 0,
    val timestamp: Long     = 0L
)

// ── Bike canvas icon ──────────────────────────────────────────────────────────
@Composable
private fun RoutesBikeIcon(
    modifier: Modifier = Modifier,
    color: Color = DGreen700,
    strokeWidth: Float = 6f
) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val wheelR = w * 0.27f
        val lx = w * 0.27f; val rx = w * 0.73f; val wy = h * 0.65f
        val bbX = w * 0.50f; val bbY = wy
        val headTopX = rx - wheelR * 0.15f; val headTopY = h * 0.28f
        val seatTopX = lx + wheelR * 0.1f;  val seatTopY = h * 0.30f
        val sw = strokeWidth
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1, y1), Offset(x2, y2), sw, StrokeCap.Round)
        drawCircle(color, wheelR, Offset(lx, wy), style = Stroke(sw))
        drawCircle(color, wheelR, Offset(rx, wy), style = Stroke(sw))
        line(bbX, bbY, seatTopX, seatTopY); line(bbX, bbY, headTopX, headTopY)
        line(seatTopX, seatTopY, headTopX, headTopY); line(bbX, bbY, lx, wy)
        line(headTopX, headTopY, rx, wy)
        line(headTopX - w * 0.06f, headTopY, headTopX + w * 0.06f, headTopY)
        line(headTopX, headTopY, headTopX + w * 0.04f, headTopY + h * 0.10f)
        line(seatTopX - w * 0.08f, seatTopY, seatTopX + w * 0.08f, seatTopY)
        drawCircle(color, sw * 1.1f, Offset(bbX, bbY))
    }
}

// ── Saved route card ──────────────────────────────────────────────────────────
@Composable
private fun SavedRouteCard(
    route: SavedRoute,
    userName: String,
    db: FirebaseFirestore,
    navController: NavController,
    onNavigate: () -> Unit
) {
    var isSharing by remember { mutableStateOf(false) }
    var shared    by remember { mutableStateOf(route.isShared) }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        // Main row
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(DGreen100),
                contentAlignment = Alignment.Center
            ) {
                RoutesBikeIcon(modifier = Modifier.size(30.dp), color = DGreen700, strokeWidth = 5f)
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(route.name, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                    color = DOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.LocationOn, null, tint = DTextSub, modifier = Modifier.size(11.dp))
                    Text(route.startLabel.ifBlank { "No location" }, fontSize = 11.sp,
                        color = DTextSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = DGreen700, modifier = Modifier.size(12.dp))
                        Text(String.format("%.1f km", route.distanceKm), fontSize = 11.sp,
                            color = DGreen700, fontWeight = FontWeight.SemiBold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Default.Timer, null, tint = DTextSub, modifier = Modifier.size(12.dp))
                        Text("${route.durationMin} min", fontSize = 11.sp, color = DTextSub)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Default.Repeat, null, tint = DTextSub, modifier = Modifier.size(12.dp))
                        Text("${route.timesRidden}×", fontSize = 11.sp, color = DTextSub)
                    }
                }
            }

            // Navigate button — grey if no coords stored, green if navigable
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(if (route.endLat != 0.0 || route.endLon != 0.0) DGreen900 else Color(0xFFBBBBBB))
                    .clickable {
                        if (route.endLat != 0.0 || route.endLon != 0.0) {
                            navController.navigate("home_navigate/${route.endLat}/${route.endLon}")
                        }
                        onNavigate()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Navigation, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        HorizontalDivider(color = Color(0xFFF0F0F0))

        // Footer — last ridden + share toggle
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Default.History, null, tint = DTextSub, modifier = Modifier.size(12.dp))
                Text(
                    if (route.lastRidden.isNotEmpty()) "Last ridden ${route.lastRidden}" else "Not ridden yet",
                    fontSize = 11.sp, color = DTextSub
                )
            }

            // Share/Unshare pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (shared) DGreen100 else Color(0xFFF0F0F0))
                    .clickable {
                        if (!isSharing && route.id.isNotEmpty()) {
                            isSharing = true
                            if (!shared) {
                                val doc = hashMapOf(
                                    "userName"    to userName,
                                    "routeId"     to route.id,
                                    "name"        to route.name,
                                    "distanceKm"  to route.distanceKm,
                                    "durationMin" to route.durationMin,
                                    "startLabel"  to route.startLabel,
                                    "endLabel"    to route.endLabel,
                                    "savedCount"  to 0,
                                    "savedBy"     to emptyList<String>(),
                                    "timestamp"   to System.currentTimeMillis()
                                )
                                db.collection("sharedRoutes").add(doc)
                                    .addOnSuccessListener {
                                        db.collection("savedRoutes").document(route.id)
                                            .update("isShared", true)
                                        shared = true; isSharing = false
                                    }
                                    .addOnFailureListener { isSharing = false }
                            } else {
                                db.collection("sharedRoutes")
                                    .whereEqualTo("routeId", route.id).get()
                                    .addOnSuccessListener { snap ->
                                        snap.documents.forEach { it.reference.delete() }
                                        db.collection("savedRoutes").document(route.id)
                                            .update("isShared", false)
                                        shared = false; isSharing = false
                                    }
                                    .addOnFailureListener { isSharing = false }
                            }
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isSharing) {
                    CircularProgressIndicator(modifier = Modifier.size(11.dp),
                        strokeWidth = 1.5.dp, color = DGreen700)
                } else {
                    Icon(
                        if (shared) Icons.Default.Share else Icons.Default.ShareLocation,
                        null, tint = if (shared) DGreen700 else DTextSub,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Text(
                    if (shared) "Shared" else "Share",
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = if (shared) DGreen700 else DTextSub
                )
            }
        }
    }
}

// ── Community route card ──────────────────────────────────────────────────────
@Composable
private fun CommunityRouteCard(
    route: CommunityRoute,
    currentUser: String,
    db: FirebaseFirestore,
    onNavigate: () -> Unit
) {
    var isSaving by remember { mutableStateOf(false) }
    var isSaved  by remember { mutableStateOf(false) }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // Header band
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(DGreen900, DGreen700)))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(route.name, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Person, null,
                                tint = Color.White.copy(alpha = 0.70f), modifier = Modifier.size(11.dp))
                            Text("by ${route.userName}", fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.70f))
                        }
                    }
                    // Save count badge
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Default.Bookmark, null, tint = Color.White, modifier = Modifier.size(11.dp))
                            Text("${route.savedCount}", fontSize = 11.sp,
                                color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

                // Start label
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.LocationOn, null, tint = DTextSub, modifier = Modifier.size(13.dp))
                    Text(route.startLabel.ifBlank { "No location" }, fontSize = 12.sp,
                        color = DTextSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                // Stats pills
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(DGreen100)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = DGreen700, modifier = Modifier.size(13.dp))
                        Text(String.format("%.1f km", route.distanceKm), fontSize = 12.sp,
                            color = DGreen700, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFF5F5F5))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Timer, null, tint = DTextSub, modifier = Modifier.size(13.dp))
                        Text("~${route.durationMin} min", fontSize = 12.sp,
                            color = DTextSub, fontWeight = FontWeight.Medium)
                    }
                    if (route.avgSpeedKmh > 0) {
                        Row(
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFF5F5F5))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Speed, null, tint = DTextSub, modifier = Modifier.size(13.dp))
                            Text(String.format("%.1f km/h", route.avgSpeedKmh), fontSize = 12.sp,
                                color = DTextSub, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFF0F0F0))

                // Action buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                    // Save to My Routes
                    OutlinedButton(
                        onClick = {
                            if (!isSaving && !isSaved && route.id.isNotEmpty()) {
                                isSaving = true
                                val today = java.text.SimpleDateFormat(
                                    "MMM d, yyyy", java.util.Locale.getDefault()
                                ).format(java.util.Date())
                                val saveDoc = hashMapOf(
                                    "userName"    to currentUser,
                                    "name"        to route.name,
                                    "distanceKm"  to route.distanceKm,
                                    "durationMin" to route.durationMin,
                                    "startLabel"  to route.startLabel,
                                    "endLabel"    to "",
                                    "timesRidden" to 0,
                                    "lastRidden"  to "",
                                    "isShared"    to false,
                                    "savedCount"  to 0,
                                    "savedFrom"   to route.userName,
                                    "timestamp"   to System.currentTimeMillis()
                                )
                                db.collection("savedRoutes").add(saveDoc)
                                    .addOnSuccessListener {
                                        db.collection("sharedRoutes").document(route.id)
                                            .update("savedCount", FieldValue.increment(1))
                                        isSaved = true; isSaving = false
                                    }
                                    .addOnFailureListener { isSaving = false }
                            }
                        },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape    = RoundedCornerShape(12.dp),
                        border   = androidx.compose.foundation.BorderStroke(
                            1.5.dp, if (isSaved) DGreen700 else Color(0xFFCDD8CD)
                        ),
                        colors   = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isSaved) DGreen700 else DTextSub
                        ),
                        enabled  = !isSaving && !isSaved
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp, color = DGreen700)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(
                                    if (isSaved) Icons.Default.BookmarkAdded else Icons.Default.BookmarkAdd,
                                    null, modifier = Modifier.size(15.dp)
                                )
                                Text(if (isSaved) "Saved" else "Save",
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Navigate to start
                    Button(
                        onClick  = onNavigate,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = DGreen900)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.Default.Navigation, null, modifier = Modifier.size(15.dp))
                            Text("Navigate", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}


// ── Main Screen ───────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectionsScreen(navController: NavController, userName: String) {
    val db = FirebaseFirestore.getInstance()

    var selectedTab          by remember { mutableIntStateOf(0) }
    var searchQuery          by remember { mutableStateOf("") }
    var savedRoutes          by remember { mutableStateOf<List<SavedRoute>>(emptyList()) }
    var communityRoutes      by remember { mutableStateOf<List<CommunityRoute>>(emptyList()) }
    var isLoadingRoutes      by remember { mutableStateOf(true) }
    var isLoadingCommunity   by remember { mutableStateOf(true) }

    // ── Load user's saved routes ──────────────────────────────────────────────
    LaunchedEffect(userName) {
        db.collection("savedRoutes")
            .whereEqualTo("userName", userName)
            .orderBy("timesRidden", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                savedRoutes = snap.documents.mapNotNull { doc ->
                    try {
                        SavedRoute(
                            id          = doc.id,
                            name        = doc.getString("name")        ?: "Unnamed Route",
                            distanceKm  = doc.getDouble("distanceKm")  ?: 0.0,
                            durationMin = (doc.getLong("durationMin")  ?: 0L).toInt(),
                            timesRidden = (doc.getLong("timesRidden")  ?: 0L).toInt(),
                            lastRidden  = doc.getString("lastRidden")  ?: "",
                            startLabel  = doc.getString("startLabel")  ?: "",
                            endLabel    = doc.getString("endLabel")    ?: "",
                            isShared    = doc.getBoolean("isShared")   ?: false,
                            startLat    = doc.getDouble("startLat")    ?: 0.0,
                            startLon    = doc.getDouble("startLon")    ?: 0.0,
                            endLat      = doc.getDouble("endLat")      ?: 0.0,
                            endLon      = doc.getDouble("endLon")      ?: 0.0
                        )
                    } catch (e: Exception) { null }
                }
                isLoadingRoutes = false
            }
            .addOnFailureListener { isLoadingRoutes = false }
    }

    // ── Load community shared routes (exclude own) ────────────────────────────
    LaunchedEffect(userName) {
        db.collection("sharedRoutes")
            .orderBy("savedCount", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                communityRoutes = snap.documents.mapNotNull { doc ->
                    try {
                        val owner = doc.getString("userName") ?: return@mapNotNull null
                        if (owner == userName) return@mapNotNull null
                        CommunityRoute(
                            id          = doc.id,
                            userName    = owner,
                            name        = doc.getString("name")        ?: "Unnamed",
                            distanceKm  = doc.getDouble("distanceKm")  ?: 0.0,
                            durationMin = (doc.getLong("durationMin")  ?: 0L).toInt(),
                            avgSpeedKmh = doc.getDouble("avgSpeedKmh") ?: 0.0,
                            startLabel  = doc.getString("startLabel")  ?: "",
                            savedCount  = (doc.getLong("savedCount")   ?: 0L).toInt(),
                            timestamp   = doc.getLong("timestamp")     ?: 0L
                        )
                    } catch (e: Exception) { null }
                }
                isLoadingCommunity = false
            }
            .addOnFailureListener { isLoadingCommunity = false }
    }

    val filteredSaved = remember(searchQuery, savedRoutes) {
        if (searchQuery.isBlank()) savedRoutes
        else savedRoutes.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.startLabel.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredCommunity = remember(searchQuery, communityRoutes) {
        if (searchQuery.isBlank()) communityRoutes
        else communityRoutes.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.userName.contains(searchQuery, ignoreCase = true) ||
                    it.startLabel.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Directions", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp,
                            color = Color.White, letterSpacing = 0.2.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                },

                colors = TopAppBarDefaults.topAppBarColors(containerColor = DGreen900)
            )
        },
        containerColor = DSurfaceBg
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            // ── Hero search bar ───────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(
                            colors = listOf(DGreen900, DGreen700), startY = 0f, endY = 200f))
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery, onValueChange = { searchQuery = it },
                        placeholder = { Text("Search routes…", fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.55f)) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, null,
                                tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, "Clear",
                                        tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true, shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = Color.White.copy(alpha = 0.7f),
                            unfocusedBorderColor    = Color.White.copy(alpha = 0.25f),
                            focusedContainerColor   = Color.White.copy(alpha = 0.14f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.09f),
                            cursorColor             = Color.White,
                            focusedTextColor        = Color.White,
                            unfocusedTextColor      = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                    )
                }
            }

            // ── Quick stats strip ─────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).offset(y = (-14).dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(
                        Triple(DGreen100,         DGreen700,  Pair("${savedRoutes.size}",     "My Routes")),
                        Triple(Color(0xFFE8EAF6), DCommunity, Pair("${communityRoutes.size}", "Explore"))
                    ).forEachIndexed { idx, (bg, iconColor, data) ->
                        Card(
                            modifier  = Modifier.weight(1f),
                            shape     = RoundedCornerShape(14.dp),
                            colors    = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier         = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(bg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (idx == 0) RoutesBikeIcon(modifier = Modifier.size(20.dp), color = iconColor, strokeWidth = 5f)
                                    else Icon(Icons.Default.Group, null, tint = iconColor, modifier = Modifier.size(18.dp))
                                }
                                Column {
                                    Text(data.first, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = DOnSurface)
                                    Text(data.second, fontSize = 11.sp, color = DTextSub, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            // ── Tab selector — 3 tabs ─────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        Pair("My Routes", Icons.AutoMirrored.Filled.DirectionsBike),
                        Pair("Explore",   Icons.Default.Group)
                    ).forEachIndexed { idx, (label, icon) ->
                        val selected = selectedTab == idx
                        Row(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (selected) DGreen900 else Color.Transparent)
                                .clickable { selectedTab = idx }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, null,
                                tint = if (selected) Color.White else DTextSub,
                                modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(label, fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (selected) Color.White else DTextSub)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── MY ROUTES TAB ─────────────────────────────────────────────────
            if (selectedTab == 0) {
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Frequently Used", fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp, color = DOnSurface, letterSpacing = 0.1.sp)
                        Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(DGreen100)
                            .padding(horizontal = 12.dp, vertical = 5.dp)) {
                            Text("${filteredSaved.size} routes", fontSize = 12.sp,
                                color = DGreen900, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (isLoadingRoutes) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = DGreen900, strokeWidth = 3.dp)
                        }
                    }
                } else if (filteredSaved.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(DGreen100),
                                contentAlignment = Alignment.Center) {
                                RoutesBikeIcon(modifier = Modifier.size(38.dp), color = DGreen700, strokeWidth = 5f)
                            }
                            Text("No saved routes yet", fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp, color = DOnSurface)
                            Text(
                                "Complete a ride and tap 'Save Route' in the ride summary — your routes will appear here for quick re-navigation.",
                                fontSize = 13.sp, color = DTextSub, lineHeight = 19.sp
                            )
                        }
                    }
                } else {
                    items(filteredSaved) { route ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                            SavedRouteCard(route = route, userName = userName, db = db, navController = navController, onNavigate = {})
                        }
                    }
                }
            }
            // ── COMMUNITY ROUTES TAB ──────────────────────────────────────────
            if (selectedTab == 1) {
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Shared by Riders", fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp, color = DOnSurface, letterSpacing = 0.1.sp)
                        Box(modifier = Modifier.clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFE8EAF6)).padding(horizontal = 12.dp, vertical = 5.dp)) {
                            Text("${filteredCommunity.size} routes", fontSize = 12.sp,
                                color = DCommunity, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Info banner
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp)).background(Color(0xFFE8EAF6))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, null, tint = DCommunity, modifier = Modifier.size(16.dp))
                        Text(
                            "Routes shared by fellow cyclists. Save any route to your collection and navigate to its start.",
                            fontSize = 12.sp, color = DCommunity, lineHeight = 17.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }

                if (isLoadingCommunity) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = DGreen900, strokeWidth = 3.dp)
                        }
                    }
                } else if (filteredCommunity.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(modifier = Modifier.size(72.dp).clip(CircleShape)
                                .background(Color(0xFFE8EAF6)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Group, null, tint = DCommunity, modifier = Modifier.size(36.dp))
                            }
                            Text("No community routes yet", fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp, color = DOnSurface)
                            Text(
                                "Be the first — complete a ride, save it, then tap the Share toggle on any saved route.",
                                fontSize = 13.sp, color = DTextSub, lineHeight = 19.sp
                            )
                        }
                    }
                } else {
                    items(filteredCommunity) { route ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                            CommunityRouteCard(route = route, currentUser = userName, db = db, onNavigate = {})
                        }
                    }
                }
            }
        }
    }
}