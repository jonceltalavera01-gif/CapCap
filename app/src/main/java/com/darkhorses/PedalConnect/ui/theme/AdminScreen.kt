package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ── Design tokens ─────────────────────────────────────────────────────────────
private val AGreen900  = Color(0xFF06402B)
private val AGreen700  = Color(0xFF0A5C3D)
private val AGreen100  = Color(0xFFDDF1E8)
private val AGreen50   = Color(0xFFF0FAF5)
private val ARedColor  = Color(0xFFD32F2F)
private val ARedLight  = Color(0xFFFFEBEE)
private val AAmber500  = Color(0xFFF59E0B)
private val AAmber50   = Color(0xFFFFFBEB)
private val ASurface   = Color(0xFFF5F7F6)
private val AWhite     = Color(0xFFFFFFFF)
private val AOnSurface = Color(0xFF111827)
private val AMuted     = Color(0xFF6B7280)
private val ADivider   = Color(0xFFE5E7EB)

// ── Data models ───────────────────────────────────────────────────────────────
private data class AdminPost(
    val id: String,
    val userName: String,
    val description: String,
    val activity: String,
    val distance: String,
    val timestamp: Long,
    val status: String,
    val imageUrl: String
)

private data class ReportedImage(
    val reportId: String,
    val alertId: String,
    val photoUrl: String,
    val reportedBy: String,
    val timestamp: Long,
    val reportCount: Int = 1
)

private data class AdminAlert(
    val id: String,
    val riderName: String,
    val emergencyType: String,
    val locationName: String,
    val status: String,
    val severity: String,
    val timestamp: Long,
    val photoUrl: String?,
    val responderName: String?
)

// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(paddingValues: PaddingValues) {
    val db    = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()

    var selectedSection by remember { mutableIntStateOf(0) }
    var drawerOpen      by remember { mutableStateOf(false) }

    val pendingPosts    = remember { mutableStateListOf<AdminPost>() }
    val pendingRides    = remember { mutableStateListOf<RideEvent>() }
    val reportedImages  = remember { mutableStateListOf<ReportedImage>() }
    val reportedPosts   = remember { mutableStateListOf<AdminPost>() }
    val activeAlerts    = remember { mutableStateListOf<AdminAlert>() }

    var isLoadingPosts         by remember { mutableStateOf(true) }
    var isLoadingRides         by remember { mutableStateOf(true) }
    var isLoadingReports       by remember { mutableStateOf(true) }
    var isLoadingReportedPosts by remember { mutableStateOf(true) }
    var isLoadingAlerts        by remember { mutableStateOf(true) }

    var successMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage   by remember { mutableStateOf<String?>(null) }

    // ── Firestore listeners ───────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        db.collection("posts")
            .whereEqualTo("status", "pending")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { isLoadingPosts = false; return@addSnapshotListener }
                pendingPosts.clear()
                for (doc in snap.documents) {
                    pendingPosts.add(AdminPost(
                        id          = doc.id,
                        userName    = doc.getString("userName")    ?: "",
                        description = doc.getString("description") ?: "",
                        activity    = doc.getString("activity")    ?: "Cycling Ride",
                        distance    = doc.getString("distance")    ?: "",
                        timestamp   = doc.getLong("timestamp")     ?: 0L,
                        status      = doc.getString("status")      ?: "pending",
                        imageUrl    = doc.getString("imageUrl")    ?: ""
                    ))
                }
                isLoadingPosts = false
            }
    }

    LaunchedEffect(Unit) {
        db.collection("rideEvents")
            .whereEqualTo("status", "pending")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { isLoadingRides = false; return@addSnapshotListener }
                pendingRides.clear()
                for (doc in snap.documents) {
                    try {
                        pendingRides.add(RideEvent(
                            id              = doc.id,
                            title           = doc.getString("title")       ?: "",
                            description     = doc.getString("description") ?: "",
                            route           = doc.getString("route")       ?: "",
                            date            = doc.getLong("date")          ?: 0L,
                            time            = doc.getString("time")        ?: "",
                            organizer       = doc.getString("organizer")   ?: "",
                            participants    = (doc.get("participants") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                            maxParticipants = (doc.getLong("maxParticipants") ?: 0L).toInt(),
                            difficulty      = doc.getString("difficulty")  ?: "Easy",
                            distanceKm      = doc.getDouble("distanceKm")  ?: 0.0,
                            timestamp       = doc.getLong("timestamp")     ?: 0L,
                            status          = "pending"
                        ))
                    } catch (e: Exception) { }
                }
                isLoadingRides = false
            }
    }

    LaunchedEffect(Unit) {
        db.collection("reportedPosts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { isLoadingReportedPosts = false; return@addSnapshotListener }
                val grouped = snap.documents
                    .mapNotNull { doc ->
                        val postId   = doc.getString("postId")   ?: return@mapNotNull null
                        val userName = doc.getString("userName") ?: return@mapNotNull null
                        postId to (userName to (doc.getLong("timestamp") ?: 0L))
                    }
                    .groupBy { it.first }
                val postIds = grouped.keys.toList()
                if (postIds.isEmpty()) { isLoadingReportedPosts = false; return@addSnapshotListener }
                // Fetch the actual post documents
                reportedPosts.clear()
                var fetched = 0
                postIds.forEach { postId ->
                    db.collection("posts").document(postId).get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                reportedPosts.add(AdminPost(
                                    id          = doc.id,
                                    userName    = doc.getString("userName")    ?: "",
                                    description = doc.getString("description") ?: "",
                                    activity    = doc.getString("activity")    ?: "",
                                    distance    = doc.getString("distance")    ?: "",
                                    timestamp   = doc.getLong("timestamp")     ?: 0L,
                                    status      = doc.getString("status")      ?: "",
                                    imageUrl    = doc.getString("imageUrl")    ?: ""
                                ))
                            }
                            fetched++
                            if (fetched == postIds.size) isLoadingReportedPosts = false
                        }
                        .addOnFailureListener {
                            fetched++
                            if (fetched == postIds.size) isLoadingReportedPosts = false
                        }
                }
            }
    }

    LaunchedEffect(Unit) {
        db.collection("reportedPosts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { isLoadingReportedPosts = false; return@addSnapshotListener }
                val grouped = snap.documents
                    .mapNotNull { doc ->
                        val postId   = doc.getString("postId")   ?: return@mapNotNull null
                        val userName = doc.getString("userName") ?: return@mapNotNull null
                        postId to (userName to (doc.getLong("timestamp") ?: 0L))
                    }
                    .groupBy { it.first }
                val postIds = grouped.keys.toList()
                if (postIds.isEmpty()) { isLoadingReportedPosts = false; return@addSnapshotListener }
                // Fetch the actual post documents
                reportedPosts.clear()
                var fetched = 0
                postIds.forEach { postId ->
                    db.collection("posts").document(postId).get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                reportedPosts.add(AdminPost(
                                    id          = doc.id,
                                    userName    = doc.getString("userName")    ?: "",
                                    description = doc.getString("description") ?: "",
                                    activity    = doc.getString("activity")    ?: "",
                                    distance    = doc.getString("distance")    ?: "",
                                    timestamp   = doc.getLong("timestamp")     ?: 0L,
                                    status      = doc.getString("status")      ?: "",
                                    imageUrl    = doc.getString("imageUrl")    ?: ""
                                ))
                            }
                            fetched++
                            if (fetched == postIds.size) isLoadingReportedPosts = false
                        }
                        .addOnFailureListener {
                            fetched++
                            if (fetched == postIds.size) isLoadingReportedPosts = false
                        }
                }
            }
    }

    LaunchedEffect(Unit) {
        db.collection("reportedImages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { isLoadingReports = false; return@addSnapshotListener }
                val grouped = snap.documents
                    .mapNotNull { doc ->
                        val alertId  = doc.getString("alertId")  ?: return@mapNotNull null
                        val photoUrl = doc.getString("photoUrl") ?: return@mapNotNull null
                        alertId to ReportedImage(
                            reportId   = doc.id,
                            alertId    = alertId,
                            photoUrl   = photoUrl,
                            reportedBy = doc.getString("reportedBy") ?: "",
                            timestamp  = doc.getLong("timestamp")    ?: 0L
                        )
                    }
                    .groupBy { it.first }
                    .map { (_, reports) ->
                        val latest = reports.maxByOrNull { it.second.timestamp }!!.second
                        latest.copy(reportCount = reports.size)
                    }
                    .sortedByDescending { it.reportCount }
                reportedImages.clear()
                reportedImages.addAll(grouped)
                isLoadingReports = false
            }
    }

    LaunchedEffect(Unit) {
        db.collection("alerts")
            .addSnapshotListener { snap, _ ->
                if (snap == null) { isLoadingAlerts = false; return@addSnapshotListener }
                activeAlerts.clear()
                for (doc in snap.documents) {
                    if ((doc.getString("status") ?: "active") == "resolved") continue
                    activeAlerts.add(AdminAlert(
                        id            = doc.id,
                        riderName     = doc.getString("riderName")     ?: "Unknown",
                        emergencyType = doc.getString("emergencyType") ?: "Emergency",
                        locationName  = doc.getString("locationName")  ?: "Unknown",
                        status        = doc.getString("status")        ?: "active",
                        severity      = doc.getString("severity")      ?: "HIGH",
                        timestamp     = doc.getLong("timestamp")       ?: 0L,
                        photoUrl      = doc.getString("photoUrl"),
                        responderName = doc.getString("responderName")
                    ))
                }
                activeAlerts.sortByDescending { it.timestamp }
                isLoadingAlerts = false
            }
    }

    // ── Actions ───────────────────────────────────────────────────────────────
    fun toast(msg: String, isSuccess: Boolean = true) {
        if (isSuccess) successMessage = msg else errorMessage = msg
    }

    fun approvePost(post: AdminPost) {
        pendingPosts.remove(post)  // optimistic removal
        db.collection("posts").document(post.id).update("status", "accepted")
            .addOnSuccessListener {
                db.collection("notifications").add(hashMapOf(
                    "userName"  to post.userName,
                    "message"   to "✅ Your post has been approved and is now live!",
                    "type"      to "accepted",
                    "timestamp" to System.currentTimeMillis(), "read" to false))
                toast("Post approved!")
            }.addOnFailureListener {
                pendingPosts.add(post)  // restore on failure
                toast("Failed to approve post.", false)
            }
    }

    fun rejectPost(post: AdminPost) {
        pendingPosts.remove(post)  // optimistic removal
        db.collection("posts").document(post.id).update("status", "rejected")
            .addOnSuccessListener {
                db.collection("notifications").add(hashMapOf(
                    "userName"  to post.userName,
                    "message"   to "❌ Your post was not approved. Please follow community guidelines.",
                    "type"      to "rejected",
                    "timestamp" to System.currentTimeMillis(), "read" to false))
                toast("Post rejected.")
            }.addOnFailureListener {
                pendingPosts.add(post)  // restore on failure
                toast("Failed to reject post.", false)
            }
    }

    fun deletePost(post: AdminPost) {
        db.collection("posts").document(post.id).get().addOnSuccessListener { doc ->
            val deleteUrl = doc.getString("imageDeleteUrl") ?: ""
            db.collection("posts").document(post.id).delete()
                .addOnSuccessListener {
                    toast("Post deleted.")
                    if (deleteUrl.isNotBlank()) {
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val conn = java.net.URL(deleteUrl).openConnection() as java.net.HttpURLConnection
                                conn.requestMethod = "GET"; conn.connectTimeout = 10_000
                                conn.responseCode; conn.disconnect()
                            } catch (e: Exception) { }
                        }
                    }
                }.addOnFailureListener { toast("Failed to delete.", false) }
        }
    }

    fun approveRide(ride: RideEvent) {
        pendingRides.remove(ride)  // optimistic removal
        db.collection("rideEvents").document(ride.id).update("status", "approved")
            .addOnSuccessListener {
                db.collection("notifications").add(hashMapOf(
                    "userName"  to ride.organizer,
                    "message"   to "✅ Your ride \"${ride.title}\" is approved and now visible!",
                    "type"      to "ride", "eventId" to ride.id,
                    "timestamp" to System.currentTimeMillis(), "read" to false))
                toast("Ride approved!")
            }.addOnFailureListener {
                pendingRides.add(ride)  // restore on failure
                toast("Failed to approve ride.", false)
            }
    }

    fun rejectRide(ride: RideEvent) {
        pendingRides.remove(ride)  // optimistic removal
        db.collection("rideEvents").document(ride.id).update("status", "rejected")
            .addOnSuccessListener {
                db.collection("notifications").add(hashMapOf(
                    "userName"  to ride.organizer,
                    "message"   to "❌ Your ride \"${ride.title}\" was not approved. Please review and resubmit.",
                    "type"      to "ride", "eventId" to ride.id,
                    "timestamp" to System.currentTimeMillis(), "read" to false))
                toast("Ride rejected.")
            }.addOnFailureListener {
                pendingRides.add(ride)  // restore on failure
                toast("Failed to reject ride.", false)
            }
    }

    fun deleteAlertPhoto(report: ReportedImage) {
        db.collection("alerts").document(report.alertId).update("photoUrl", "")
            .addOnSuccessListener {
                db.collection("reportedImages").whereEqualTo("alertId", report.alertId)
                    .get().addOnSuccessListener { it.documents.forEach { d -> d.reference.delete() } }
                toast("Photo removed.")
            }.addOnFailureListener { toast("Failed to remove photo.", false) }
    }

    fun dismissReports(report: ReportedImage) {
        db.collection("reportedImages").whereEqualTo("alertId", report.alertId)
            .get().addOnSuccessListener { snap ->
                snap.documents.forEach { it.reference.delete() }
                toast("Reports dismissed.")
            }.addOnFailureListener { toast("Failed to dismiss.", false) }
    }

    fun forceResolveAlert(alert: AdminAlert) {
        db.collection("alerts").document(alert.id).update("status", "resolved")
            .addOnSuccessListener {
                db.collection("notifications").add(hashMapOf(
                    "userName" to alert.riderName,
                    "message"  to "ℹ️ Your ${alert.emergencyType} alert was closed by an admin.",
                    "type"     to "alert",
                    "timestamp" to System.currentTimeMillis(), "read" to false))
                toast("Alert resolved.")
            }.addOnFailureListener { toast("Failed to resolve.", false) }
    }

    LaunchedEffect(successMessage, errorMessage) {
        if (successMessage != null || errorMessage != null) {
            kotlinx.coroutines.delay(3000)
            successMessage = null; errorMessage = null
        }
    }

    // ── Drawer sections ───────────────────────────────────────────────────────
    data class NavSection(
        val label: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val count: Int,
        val badgeColor: Color
    )
    val sections = listOf(
        NavSection("Posts",         Icons.Default.Article,        pendingPosts.size,   AAmber500),
        NavSection("Rides",         Icons.Default.DirectionsBike, pendingRides.size,   Color(0xFF1976D2)),
        NavSection("Post Reports",  Icons.Default.Flag,           reportedPosts.size,  ARedColor),
        NavSection("Photo Reports", Icons.Default.Image,          reportedImages.size, Color(0xFFEA580C)),
        NavSection("Alerts",        Icons.Default.Warning,        activeAlerts.size,   Color(0xFFEF4444))
    )

    // ── Drawer state ──────────────────────────────────────────────────────────
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    LaunchedEffect(drawerOpen) {
        if (drawerOpen) drawerState.open() else drawerState.close()
    }
    LaunchedEffect(drawerState.currentValue) {
        drawerOpen = drawerState.isOpen
    }

    ModalNavigationDrawer(
        drawerState   = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = AGreen900,
                modifier = Modifier.width(280.dp)
            ) {
                // Drawer header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(40.dp).clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AdminPanelSettings, null,
                                    tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            Column {
                                Text("Admin Panel", fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp, color = Color.White)
                                Text("Moderation", fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.6f))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        // Urgency hint — oldest pending item
                        val oldestPost = pendingPosts.minByOrNull { it.timestamp }
                        val oldestRide = pendingRides.minByOrNull { it.timestamp }
                        val oldest     = listOfNotNull(oldestPost?.timestamp, oldestRide?.timestamp).minOrNull()
                        if (oldest != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AAmber500.copy(alpha = 0.18f))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.HourglassTop, null,
                                    tint = AAmber500, modifier = Modifier.size(13.dp))
                                Text(
                                    "Oldest pending: ${formatAdminTime(oldest)}",
                                    fontSize = 11.sp, color = AAmber500,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(13.dp))
                                Text("Nothing pending review",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.5f))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Nav items
                sections.forEachIndexed { index, sec ->
                    val isSelected = selectedSection == index
                    NavigationDrawerItem(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        label = {
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(sec.label, fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) AGreen900 else Color.White)
                                if (sec.count > 0) {
                                    Box(
                                        Modifier.clip(RoundedCornerShape(20.dp))
                                            .background(if (isSelected) sec.badgeColor else sec.badgeColor.copy(alpha = 0.25f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text("${sec.count}", fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else sec.badgeColor)
                                    }
                                }
                            }
                        },
                        icon = {
                            Icon(sec.icon, null,
                                tint     = if (isSelected) AGreen900 else Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(20.dp))
                        },
                        selected = isSelected,
                        onClick  = {
                            selectedSection = index
                            scope.launch { drawerState.close() }
                            drawerOpen = false
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor   = Color.White,
                            unselectedContainerColor = Color.Transparent,
                            selectedIconColor        = AGreen900,
                            unselectedIconColor      = Color.White.copy(alpha = 0.75f),
                            selectedTextColor        = AGreen900,
                            unselectedTextColor      = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Section icon
                            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center) {
                                Icon(sections[selectedSection].icon, null,
                                    tint = Color.White, modifier = Modifier.size(17.dp))
                            }
                            Column {
                                Text(sections[selectedSection].label,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize   = 17.sp, color = Color.White)
                                Text(when (selectedSection) {
                                    0 -> "${pendingPosts.size} pending"
                                    1 -> "${pendingRides.size} pending"
                                    2 -> "${reportedImages.size} reported"
                                    else -> "${activeAlerts.size} active"
                                }, fontSize = 11.sp, color = Color.White.copy(alpha = 0.65f))
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            drawerOpen = true
                            scope.launch { drawerState.open() }
                        }) {
                            Box {
                                Icon(Icons.Default.Menu, "Menu", tint = Color.White)
                                val totalPending = pendingPosts.size + pendingRides.size
                                if (totalPending > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 2.dp, y = (-1).dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFD32F2F))
                                            .border(1.5.dp, AGreen900, CircleShape)
                                    )
                                }
                            }
                        }
                    },
                    colors   = TopAppBarDefaults.topAppBarColors(containerColor = AGreen900),
                    modifier = Modifier.shadow(2.dp)
                )
            },
            containerColor = ASurface
        ) { innerPadding ->
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier       = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(
                        start  = 16.dp, end = 16.dp, top = 12.dp,
                        bottom = paddingValues.calculateBottomPadding() + 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (selectedSection) {
                        0 -> {
                            if (isLoadingPosts) {
                                item { AdminLoadingState() }
                            } else if (pendingPosts.isEmpty()) {
                                item { AdminEmptyState(Icons.Default.CheckCircle, "All caught up!", "No posts pending approval.") }
                            } else {
                                item {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment     = Alignment.CenterVertically
                                    ) {
                                        Text("${pendingPosts.size} post${if (pendingPosts.size != 1) "s" else ""} awaiting review",
                                            fontSize = 12.sp, color = AMuted,
                                            modifier = Modifier.padding(start = 4.dp))
                                        if (pendingPosts.size >= 3) {
                                            var showApproveAll by remember { mutableStateOf(false) }
                                            if (showApproveAll) {
                                                AlertDialog(
                                                    onDismissRequest = { showApproveAll = false },
                                                    shape = RoundedCornerShape(20.dp),
                                                    containerColor = AWhite,
                                                    icon = {
                                                        Box(Modifier.size(52.dp).clip(CircleShape)
                                                            .background(AGreen50),
                                                            contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.CheckCircle, null,
                                                                tint = AGreen900, modifier = Modifier.size(26.dp))
                                                        }
                                                    },
                                                    title = {
                                                        Text("Approve all ${pendingPosts.size} posts?",
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 17.sp, color = AOnSurface,
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.fillMaxWidth())
                                                    },
                                                    text = {
                                                        Text("All pending posts will be published and their authors notified.",
                                                            fontSize = 13.sp, color = AMuted,
                                                            textAlign = TextAlign.Center)
                                                    },
                                                    confirmButton = {
                                                        Column(Modifier.fillMaxWidth(),
                                                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            Button(
                                                                onClick = {
                                                                    showApproveAll = false
                                                                    pendingPosts.toList().forEach { approvePost(it) }
                                                                },
                                                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                                                shape  = RoundedCornerShape(12.dp),
                                                                colors = ButtonDefaults.buttonColors(
                                                                    containerColor = AGreen900,
                                                                    contentColor   = Color.White)
                                                            ) { Text("Approve all", fontWeight = FontWeight.Bold) }
                                                            OutlinedButton(
                                                                onClick  = { showApproveAll = false },
                                                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                                                shape    = RoundedCornerShape(12.dp)
                                                            ) { Text("Cancel", color = AMuted) }
                                                        }
                                                    }
                                                )
                                            }
                                            TextButton(onClick = { showApproveAll = true }) {
                                                Icon(Icons.Default.DoneAll, null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = AGreen900)
                                                Spacer(Modifier.width(4.dp))
                                                Text("Approve all", fontSize = 12.sp,
                                                    color = AGreen900, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                                items(pendingPosts, key = { it.id }) { post ->
                                    AdminPostCard(
                                        post      = post,
                                        onApprove = { approvePost(post) },
                                        onReject  = { rejectPost(post) },
                                        onDelete  = { deletePost(post) }
                                    )
                                }
                            }
                        }
                        1 -> {
                            if (isLoadingRides) {
                                item { AdminLoadingState() }
                            } else if (pendingRides.isEmpty()) {
                                item { AdminEmptyState(Icons.Default.DirectionsBike, "No pending rides", "All ride events have been reviewed.") }
                            } else {
                                item {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment     = Alignment.CenterVertically
                                    ) {
                                        Text("${pendingRides.size} ride${if (pendingRides.size != 1) "s" else ""} awaiting approval",
                                            fontSize = 12.sp, color = AMuted,
                                            modifier = Modifier.padding(start = 4.dp))
                                        if (pendingRides.size >= 3) {
                                            var showApproveAll by remember { mutableStateOf(false) }
                                            if (showApproveAll) {
                                                AlertDialog(
                                                    onDismissRequest = { showApproveAll = false },
                                                    shape = RoundedCornerShape(20.dp),
                                                    containerColor = AWhite,
                                                    icon = {
                                                        Box(Modifier.size(52.dp).clip(CircleShape)
                                                            .background(AGreen50),
                                                            contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Default.CheckCircle, null,
                                                                tint = AGreen900, modifier = Modifier.size(26.dp))
                                                        }
                                                    },
                                                    title = {
                                                        Text("Approve all ${pendingRides.size} rides?",
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 17.sp, color = AOnSurface,
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.fillMaxWidth())
                                                    },
                                                    text = {
                                                        Text("All pending rides will be published and organizers notified.",
                                                            fontSize = 13.sp, color = AMuted,
                                                            textAlign = TextAlign.Center)
                                                    },
                                                    confirmButton = {
                                                        Column(Modifier.fillMaxWidth(),
                                                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            Button(
                                                                onClick = {
                                                                    showApproveAll = false
                                                                    pendingRides.toList().forEach { approveRide(it) }
                                                                },
                                                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                                                shape  = RoundedCornerShape(12.dp),
                                                                colors = ButtonDefaults.buttonColors(
                                                                    containerColor = AGreen900,
                                                                    contentColor   = Color.White)
                                                            ) { Text("Approve all", fontWeight = FontWeight.Bold) }
                                                            OutlinedButton(
                                                                onClick  = { showApproveAll = false },
                                                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                                                shape    = RoundedCornerShape(12.dp)
                                                            ) { Text("Cancel", color = AMuted) }
                                                        }
                                                    }
                                                )
                                            }
                                            TextButton(onClick = { showApproveAll = true }) {
                                                Icon(Icons.Default.DoneAll, null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = AGreen900)
                                                Spacer(Modifier.width(4.dp))
                                                Text("Approve all", fontSize = 12.sp,
                                                    color = AGreen900, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                                items(pendingRides, key = { it.id }) { ride ->
                                    AdminRideCard(
                                        ride      = ride,
                                        onApprove = { approveRide(ride) },
                                        onReject  = { rejectRide(ride) }
                                    )
                                }
                            }
                        }
                        2 -> {
                            if (isLoadingReportedPosts) {
                                item { AdminLoadingState() }
                            } else if (reportedPosts.isEmpty()) {
                                item { AdminEmptyState(Icons.Default.CheckCircle, "No reported posts", "All posts have been reviewed.") }
                            } else {
                                item {
                                    Text("${reportedPosts.size} reported post${if (reportedPosts.size != 1) "s" else ""}",
                                        fontSize = 12.sp, color = AMuted,
                                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                                }
                                items(reportedPosts, key = { it.id }) { post ->
                                    AdminPostCard(
                                        post      = post,
                                        onApprove = {
                                            // "Approve" here means dismiss reports — restore post to accepted
                                            db.collection("posts").document(post.id)
                                                .update("status", "accepted")
                                            db.collection("reportedPosts")
                                                .whereEqualTo("postId", post.id)
                                                .get()
                                                .addOnSuccessListener { snap ->
                                                    snap.documents.forEach { it.reference.delete() }
                                                }
                                            reportedPosts.remove(post)
                                        },
                                        onReject  = {
                                            // "Reject" means remove the post entirely
                                            db.collection("posts").document(post.id).delete()
                                            db.collection("reportedPosts")
                                                .whereEqualTo("postId", post.id)
                                                .get()
                                                .addOnSuccessListener { snap ->
                                                    snap.documents.forEach { it.reference.delete() }
                                                }
                                            db.collection("notifications").add(hashMapOf(
                                                "userName"  to post.userName,
                                                "message"   to "❌ Your post was removed after being reported for violating community guidelines.",
                                                "type"      to "rejected",
                                                "timestamp" to System.currentTimeMillis(),
                                                "read"      to false
                                            ))
                                            reportedPosts.remove(post)
                                        },
                                        onDelete  = {
                                            db.collection("posts").document(post.id).delete()
                                            db.collection("reportedPosts")
                                                .whereEqualTo("postId", post.id)
                                                .get()
                                                .addOnSuccessListener { snap ->
                                                    snap.documents.forEach { it.reference.delete() }
                                                }
                                            reportedPosts.remove(post)
                                        }
                                    )
                                }
                            }
                        }
                        3 -> {
                            if (isLoadingReports) {
                                item { AdminLoadingState() }
                            } else if (reportedImages.isEmpty()) {
                                item { AdminEmptyState(Icons.Default.CheckCircle, "No reported images", "All photos have been reviewed.") }
                            } else {
                                item {
                                    Text("${reportedImages.size} alert${if (reportedImages.size != 1) "s" else ""} with reported photos",
                                        fontSize = 12.sp, color = AMuted,
                                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                                }
                                items(reportedImages, key = { it.alertId }) { report ->
                                    AdminReportCard(
                                        report          = report,
                                        onDeletePhoto   = { deleteAlertPhoto(report) },
                                        onDismissReport = { dismissReports(report) }
                                    )
                                }
                            }
                        }
                        4 -> {
                            if (isLoadingAlerts) {
                                item { AdminLoadingState() }
                            } else if (activeAlerts.isEmpty()) {
                                item { AdminEmptyState(Icons.Default.CheckCircle, "No active alerts", "All emergencies resolved.") }
                            } else {
                                item {
                                    Text("${activeAlerts.size} active alert${if (activeAlerts.size != 1) "s" else ""}",
                                        fontSize = 12.sp, color = AMuted,
                                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                                }
                                items(activeAlerts, key = { it.id }) { alert ->
                                    AdminAlertCard(
                                        alert          = alert,
                                        onForceResolve = { forceResolveAlert(alert) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Toast
                val toastMsg  = successMessage ?: errorMessage
                val isSuccess = successMessage != null
                if (toastMsg != null) {
                    Card(
                        modifier  = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = paddingValues.calculateBottomPadding() + 24.dp)
                            .padding(horizontal = 24.dp),
                        shape     = RoundedCornerShape(14.dp),
                        colors    = CardDefaults.cardColors(
                            containerColor = if (isSuccess) Color(0xFF1B5E20) else Color(0xFF7F0000)),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                                null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(toastMsg, color = Color.White,
                                fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
// ── Admin Post Card ───────────────────────────────────────────────────────────
@Composable
private fun AdminPostCard(
    post: AdminPost,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(20.dp), containerColor = AWhite,
            icon = { Box(Modifier.size(52.dp).clip(CircleShape).background(ARedLight), Alignment.Center) {
                Icon(Icons.Default.DeleteForever, null, tint = ARedColor, modifier = Modifier.size(26.dp)) } },
            title = { Text("Delete Post?", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                color = AOnSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text  = { Text("This will permanently remove the post. This cannot be undone.",
                fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center) },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showDeleteDialog = false; onDelete() },
                        modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ARedColor, contentColor = Color.White)) {
                        Text("Delete permanently", fontWeight = FontWeight.Bold, color = Color.White) }
                    OutlinedButton(onClick = { showDeleteDialog = false },
                        modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("Cancel", color = AMuted) }
                }
            }
        )
    }

    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            shape = RoundedCornerShape(20.dp), containerColor = AWhite,
            icon = { Box(Modifier.size(52.dp).clip(CircleShape).background(AAmber50), Alignment.Center) {
                Icon(Icons.Default.Cancel, null, tint = AAmber500, modifier = Modifier.size(26.dp)) } },
            title = { Text("Reject Post?", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                color = AOnSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF9FAFB))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("by ${post.userName}", fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold, color = AOnSurface)
                            if (post.description.isNotBlank()) {
                                Text(post.description, fontSize = 12.sp, color = AMuted,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            Text(formatAdminTime(post.timestamp), fontSize = 11.sp, color = AMuted)
                        }
                    }
                    Text("The rider will be notified that their post was not approved.",
                        fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center)
                }
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showRejectDialog = false; onReject() },
                        modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AAmber500, contentColor = Color.White)) {
                        Text("Reject post", fontWeight = FontWeight.Bold, color = Color.White) }
                    OutlinedButton(onClick = { showRejectDialog = false },
                        modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("Cancel", color = AMuted) }
                }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AWhite), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.fillMaxWidth()) {
            if (post.imageUrl.isNotBlank()) {
                AsyncImage(model = post.imageUrl, contentDescription = "Post image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)))
            }
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(36.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(AGreen900, AGreen700))), Alignment.Center) {
                            Text(post.userName.take(1).uppercase(), fontSize = 14.sp,
                                fontWeight = FontWeight.Bold, color = Color.White) }
                        Column {
                            Text(post.userName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AOnSurface)
                            Text(formatAdminTime(post.timestamp), fontSize = 11.sp, color = AMuted)
                        }
                    }
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(AGreen50)
                        .padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text(post.activity, fontSize = 10.sp, color = AGreen900, fontWeight = FontWeight.Medium) }
                }
                if (post.description.isNotBlank()) {
                    Text(post.description, fontSize = 13.sp, color = Color(0xFF374151),
                        lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                if (post.distance.isNotBlank() && post.distance != "0") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Route, null, tint = AMuted, modifier = Modifier.size(12.dp))
                        Text("${post.distance} km", fontSize = 11.sp, color = AMuted) } }
                HorizontalDivider(color = ADivider, thickness = 0.5.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onApprove, modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AGreen900, contentColor = Color.White)) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Approve", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                    OutlinedButton(onClick = { showRejectDialog = true }, modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AAmber500),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, AAmber500)) {
                        Icon(Icons.Default.Cancel, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reject", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ARedColor),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, ARedColor),
                        contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp)) }
                }
            }
        }
    }
}

// ── Admin Report Card ─────────────────────────────────────────────────────────
@Composable
private fun AdminReportCard(
    report: ReportedImage,
    onDeletePhoto: () -> Unit,
    onDismissReport: () -> Unit
) {
    var imageRevealed     by remember { mutableStateOf(false) }
    var showDeleteDialog  by remember { mutableStateOf(false) }
    var showDismissDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(20.dp), containerColor = AWhite,
            icon = { Box(Modifier.size(52.dp).clip(CircleShape).background(ARedLight), Alignment.Center) {
                Icon(Icons.Default.DeleteForever, null, tint = ARedColor, modifier = Modifier.size(26.dp)) } },
            title = { Text("Remove Photo?", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                color = AOnSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text  = { Text("This will permanently remove the photo from the alert.",
                fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center) },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showDeleteDialog = false; onDeletePhoto() },
                        modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ARedColor, contentColor = Color.White)) {
                        Text("Remove photo", fontWeight = FontWeight.Bold, color = Color.White) }
                    OutlinedButton(onClick = { showDeleteDialog = false },
                        modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("Cancel", color = AMuted) }
                }
            }
        )
    }

    if (showDismissDialog) {
        AlertDialog(
            onDismissRequest = { showDismissDialog = false },
            shape = RoundedCornerShape(20.dp), containerColor = AWhite,
            icon = { Box(Modifier.size(52.dp).clip(CircleShape).background(AGreen50), Alignment.Center) {
                Icon(Icons.Default.CheckCircle, null, tint = AGreen900, modifier = Modifier.size(26.dp)) } },
            title = { Text("Dismiss Reports?", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                color = AOnSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text  = { Text("The photo will remain on the alert. All reports will be cleared.",
                fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center) },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showDismissDialog = false; onDismissReport() },
                        modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AGreen900, contentColor = Color.White)) {
                        Text("Dismiss reports", fontWeight = FontWeight.Bold, color = Color.White) }
                    OutlinedButton(onClick = { showDismissDialog = false },
                        modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("Cancel", color = AMuted) }
                }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AWhite), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (report.reportCount >= 3) ARedLight else AAmber50)
                        .padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Flag, null,
                                tint = if (report.reportCount >= 3) ARedColor else AAmber500, modifier = Modifier.size(12.dp))
                            Text("${report.reportCount} report${if (report.reportCount != 1) "s" else ""}",
                                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = if (report.reportCount >= 3) ARedColor else AAmber500) } }
                    if (report.reportCount >= 3) {
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(ARedColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("AUTO-HIDDEN", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                                color = Color.White, letterSpacing = 0.5.sp) } } }
                Text(formatAdminTime(report.timestamp), fontSize = 11.sp, color = AMuted)
            }
            Text("Reported by: ${report.reportedBy}", fontSize = 12.sp, color = AMuted)
            if (report.photoUrl.isNotBlank()) {
                Box(modifier = Modifier.fillMaxWidth().height(180.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Color(0xFFF3F4F6))) {
                    AsyncImage(model = report.photoUrl, contentDescription = "Reported photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                            .then(if (!imageRevealed) Modifier.blur(20.dp) else Modifier))
                    if (!imageRevealed) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                            .clickable { imageRevealed = true }, contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.VisibilityOff, null, tint = Color.White, modifier = Modifier.size(24.dp))
                                Text("Tap to review", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold) } } }
                }
            }
            HorizontalDivider(color = ADivider, thickness = 0.5.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showDeleteDialog = true }, modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ARedColor, contentColor = Color.White)) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remove Photo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                OutlinedButton(onClick = { showDismissDialog = true }, modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AGreen900),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, AGreen900)) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Keep Photo", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ── Admin Alert Card ──────────────────────────────────────────────────────────
@Composable
private fun AdminAlertCard(alert: AdminAlert, onForceResolve: () -> Unit) {
    var showResolveDialog by remember { mutableStateOf(false) }

    val severityColor = when (alert.severity.uppercase()) {
        "HIGH"   -> Color(0xFFD32F2F)
        "MEDIUM" -> Color(0xFFF57C00)
        else     -> Color(0xFF388E3C)
    }
    val severityBg = when (alert.severity.uppercase()) {
        "HIGH"   -> Color(0xFFFFEBEE)
        "MEDIUM" -> Color(0xFFFFF3E0)
        else     -> Color(0xFFE8F5E9)
    }

    if (showResolveDialog) {
        AlertDialog(
            onDismissRequest = { showResolveDialog = false },
            shape = RoundedCornerShape(20.dp), containerColor = AWhite,
            icon = { Box(Modifier.size(52.dp).clip(CircleShape).background(AGreen50), Alignment.Center) {
                Icon(Icons.Default.CheckCircle, null, tint = AGreen900, modifier = Modifier.size(26.dp)) } },
            title = { Text("Force Resolve?", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                color = AOnSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text  = { Text("This will close ${alert.riderName}'s alert and notify them it was resolved by admin.",
                fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center) },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showResolveDialog = false; onForceResolve() },
                        modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AGreen900, contentColor = Color.White)) {
                        Text("Force resolve", fontWeight = FontWeight.Bold, color = Color.White) }
                    OutlinedButton(onClick = { showResolveDialog = false },
                        modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("Cancel", color = AMuted) }
                }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AWhite), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth()
                .background(severityBg, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(severityColor)
                            .padding(horizontal = 7.dp, vertical = 2.dp)) {
                            Text(alert.severity.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                                color = Color.White, letterSpacing = 0.8.sp) }
                        Text(alert.emergencyType, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = severityColor) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (alert.status == "responding") {
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF1565C0))
                                .padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text("Responding", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White) } }
                        Text(formatAdminTime(alert.timestamp), fontSize = 11.sp, color = AMuted) } }
            }
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(severityBg), Alignment.Center) {
                        Text(alert.riderName.take(1).uppercase(), fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp, color = severityColor) }
                    Column {
                        Text(alert.riderName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AOnSurface)
                        if (!alert.responderName.isNullOrBlank()) {
                            Text("Responder: ${alert.responderName}", fontSize = 11.sp,
                                color = Color(0xFF1565C0), fontWeight = FontWeight.Medium) } } }
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.LocationOn, null, tint = AMuted,
                        modifier = Modifier.size(13.dp).padding(top = 1.dp))
                    Text(alert.locationName, fontSize = 12.sp, color = AMuted,
                        lineHeight = 17.sp, modifier = Modifier.weight(1f)) }
                HorizontalDivider(color = ADivider, thickness = 0.5.dp)
                Button(onClick = { showResolveDialog = true }, modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AGreen900, contentColor = Color.White)) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Force Resolve", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            }
        }
    }
}

@Composable
private fun AdminRideCard(
    ride: RideEvent,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    var showRejectDialog by remember { mutableStateOf(false) }

    val diffColor = when (ride.difficulty) {
        "Easy"     -> Color(0xFF166534)
        "Moderate" -> Color(0xFF9A3412)
        "Hard"     -> Color(0xFF991B1B)
        else       -> AMuted
    }
    val diffBg = when (ride.difficulty) {
        "Easy"     -> Color(0xFFDCFCE7)
        "Moderate" -> Color(0xFFFFEDD5)
        "Hard"     -> Color(0xFFFFE4E6)
        else       -> Color(0xFFF3F4F6)
    }

    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            shape = RoundedCornerShape(20.dp), containerColor = AWhite,
            icon = {
                Box(Modifier.size(52.dp).clip(CircleShape).background(AAmber50), Alignment.Center) {
                    Icon(Icons.Default.Cancel, null, tint = AAmber500, modifier = Modifier.size(26.dp))
                }
            },
            title = { Text("Reject Ride?", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                color = AOnSurface, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF9FAFB))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(ride.title, fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold, color = AOnSurface,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("by ${ride.organizer}", fontSize = 12.sp, color = AMuted)
                            if (ride.route.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.LocationOn, null,
                                        tint = AMuted, modifier = Modifier.size(11.dp))
                                    Text(ride.route, fontSize = 11.sp, color = AMuted,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                            if (ride.date > 0L) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.CalendarMonth, null,
                                        tint = AMuted, modifier = Modifier.size(11.dp))
                                    Text(
                                        java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                                            .format(java.util.Date(ride.date)),
                                        fontSize = 11.sp, color = AMuted
                                    )
                                }
                            }
                            Text("Submitted ${formatAdminTime(ride.timestamp)}",
                                fontSize = 11.sp, color = AMuted)
                        }
                    }
                    Text("The organizer will be notified their ride was not approved.",
                        fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center)
                }
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showRejectDialog = false; onReject() },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AAmber500, contentColor = Color.White)
                    ) { Text("Reject ride", fontWeight = FontWeight.Bold, color = Color.White) }
                    OutlinedButton(
                        onClick = { showRejectDialog = false },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Cancel", color = AMuted) }
                }
            }
        )
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = AWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Coloured top bar
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(Color(0xFFEEF2FF), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.DirectionsBike, null,
                            tint = Color(0xFF1976D2), modifier = Modifier.size(14.dp))
                        Text("Ride Event", fontSize = 11.sp,
                            color = Color(0xFF1976D2), fontWeight = FontWeight.SemiBold)
                    }
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(diffBg)
                        .padding(horizontal = 7.dp, vertical = 2.dp)) {
                        Text(ride.difficulty, fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold, color = diffColor)
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title + organizer
                Text(ride.title, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, color = AOnSurface, lineHeight = 21.sp)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(22.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(AGreen900, AGreen700))),
                        contentAlignment = Alignment.Center) {
                        Text(ride.organizer.take(1).uppercase(),
                            fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text(ride.organizer, fontSize = 12.sp,
                        color = AMuted, fontWeight = FontWeight.Medium)
                    Text("·", fontSize = 12.sp, color = AMuted)
                    Text(formatAdminTime(ride.timestamp), fontSize = 11.sp, color = AMuted)
                }

                // Route
                if (ride.route.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Default.LocationOn, null,
                            tint = AMuted, modifier = Modifier.size(12.dp))
                        Text(ride.route, fontSize = 12.sp, color = AMuted,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                    }
                }

                // Date + distance row
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.CalendarMonth, null,
                            tint = AMuted, modifier = Modifier.size(12.dp))
                        Text(
                            if (ride.date > 0L)
                                java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                                    .format(java.util.Date(ride.date))
                            else "Date TBA",
                            fontSize = 12.sp, color = AMuted
                        )
                    }
                    if (ride.distanceKm > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Route, null,
                                tint = AMuted, modifier = Modifier.size(12.dp))
                            Text(String.format("%.1f km", ride.distanceKm),
                                fontSize = 12.sp, color = AMuted)
                        }
                    }
                    if (ride.maxParticipants > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Groups, null,
                                tint = AMuted, modifier = Modifier.size(12.dp))
                            Text("Max ${ride.maxParticipants}",
                                fontSize = 12.sp, color = AMuted)
                        }
                    }
                }

                // Description
                if (ride.description.isNotBlank()) {
                    Text(ride.description, fontSize = 12.sp,
                        color = Color(0xFF374151), lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }

                // Route preview map
                if (ride.route.isNotBlank() &&
                    ride.route.contains(" to ", ignoreCase = true)) {
                    var showMap by remember { mutableStateOf(false) }
                    if (!showMap) {
                        OutlinedButton(
                            onClick  = { showMap = true },
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                            shape    = RoundedCornerShape(10.dp),
                            border   = androidx.compose.foundation.BorderStroke(1.dp, ADivider),
                            colors   = ButtonDefaults.outlinedButtonColors(contentColor = AMuted)
                        ) {
                            Icon(Icons.Default.Map, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Preview route map", fontSize = 12.sp,
                                fontWeight = FontWeight.Medium)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Text("Route preview", fontSize = 11.sp,
                                    color = AMuted, fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp)
                                TextButton(
                                    onClick         = { showMap = false },
                                    contentPadding  = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("Hide", fontSize = 11.sp, color = AMuted)
                                }
                            }
                            EventRouteMap(
                                routeText = ride.route,
                                modifier  = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                HorizontalDivider(color = ADivider, thickness = 0.5.dp)

                // Action buttons
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick   = onApprove,
                        modifier  = Modifier.weight(1f).height(40.dp),
                        shape     = RoundedCornerShape(10.dp),
                        colors    = ButtonDefaults.buttonColors(
                            containerColor = AGreen900, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Approve", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    OutlinedButton(
                        onClick  = { showRejectDialog = true },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = AAmber500),
                        border   = androidx.compose.foundation.BorderStroke(1.5.dp, AAmber500)
                    ) {
                        Icon(Icons.Default.Cancel, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reject", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Atoms ─────────────────────────────────────────────────────────────────────
@Composable
private fun AdminStat(value: String, label: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.65f), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AdminStatDivider() {
    Box(Modifier.width(1.dp).height(36.dp).background(Color.White.copy(alpha = 0.15f)))
}

@Composable
private fun AdminLoadingState() {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), Alignment.Center) {
        CircularProgressIndicator(color = AGreen900, strokeWidth = 2.5.dp, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun AdminEmptyState(icon: ImageVector, title: String, message: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(AGreen50), Alignment.Center) {
            Icon(icon, null, tint = AGreen900, modifier = Modifier.size(28.dp)) }
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            color = AOnSurface, textAlign = TextAlign.Center)
        Text(message, fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center, lineHeight = 19.sp)
    }
}

private fun formatAdminTime(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000     -> "Just now"
        diff < 3_600_000  -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}