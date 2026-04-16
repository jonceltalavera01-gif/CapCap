package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

private data class ReportedComment(
    val commentId: String,
    val postId: String,
    val userName: String,
    val text: String,
    val reportCount: Int,
    val timestamp: Long,
    val reportedBy: String = "",
    val reasons: List<String> = emptyList()
)

private data class ModerationLog(
    val id: String,
    val userName: String,
    val originalText: String,
    val censoredText: String,
    val context: String,   // "comment", "post_edit", "comment_edit"
    val timestamp: Long,
    val reviewed: Boolean = false
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
private data class UserReport(
    val id: String,
    val reporterName: String,
    val reportedName: String,
    val reportedRole: String,   // "rider" or "helper"
    val alertId: String,
    val emergencyType: String,
    val reason: String,
    val comment: String,
    val timestamp: Long,
    val reviewed: Boolean
)

private data class AuditLogEntry(
    val id: String,
    val adminUserName: String,
    val adminDisplayName: String,
    val action: String,
    val targetType: String,   // "post", "ride", "comment", "alert", "photo"
    val targetUser: String,
    val detail: String,
    val timestamp: Long
)


// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(paddingValues: PaddingValues, adminUserName: String = "") {
    val db    = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()

    var selectedSection by remember { mutableIntStateOf(0) } // 0 = Dashboard
    var drawerOpen      by remember { mutableStateOf(false) }

    val pendingPosts    = remember { mutableStateListOf<AdminPost>() }
    val pendingRides    = remember { mutableStateListOf<RideEvent>() }
    val reportedImages  = remember { mutableStateListOf<ReportedImage>() }
    val reportedPosts   = remember { mutableStateListOf<AdminPost>() }
    val activeAlerts        = remember { mutableStateListOf<AdminAlert>() }
    val reportedComments    = remember { mutableStateListOf<ReportedComment>() }

    // Dashboard state
    var totalUsers          by remember { mutableIntStateOf(0) }
    var totalPosts          by remember { mutableIntStateOf(0) }
    var resolvedAlerts      by remember { mutableIntStateOf(0) }
    var dashUsersReady      by remember { mutableStateOf(false) }
    var dashPostsReady      by remember { mutableStateOf(false) }
    var dashAlertsReady     by remember { mutableStateOf(false) }
    val isLoadingDashboard  by remember { derivedStateOf { !dashUsersReady || !dashPostsReady || !dashAlertsReady } }

    data class ActivityItem(
        val id: String,
        val type: String,       // "post", "alert", "report"
        val title: String,
        val subtitle: String,
        val timestamp: Long
    )
    val recentActivity = remember { mutableStateListOf<ActivityItem>() }

    var isLoadingPosts         by remember { mutableStateOf(true) }
    var isLoadingRides         by remember { mutableStateOf(true) }
    var isLoadingReports       by remember { mutableStateOf(true) }
    var isLoadingReportedPosts by remember { mutableStateOf(true) }
    var isLoadingAlerts           by remember { mutableStateOf(true) }
    var isLoadingReportedComments by remember { mutableStateOf(true) }
    var isLoadingModerationLogs   by remember { mutableStateOf(true) }
    val moderationLogs = remember { mutableStateListOf<ModerationLog>() }
    val auditLogs = remember { mutableStateListOf<AuditLogEntry>() }
    var isLoadingAuditLogs by remember { mutableStateOf(true) }
    val userReports = remember { mutableStateListOf<UserReport>() }
    var isLoadingUserReports by remember { mutableStateOf(true) }
    var adminDisplayName by remember { mutableStateOf(adminUserName) }

    var successMessage     by remember { mutableStateOf<String?>(null) }
    var errorMessage       by remember { mutableStateOf<String?>(null) }
    var selectedReportChip by remember { mutableStateOf("Posts") }

    // ── Eager badge count listeners (run once on launch) ─────────────────────
    LaunchedEffect(Unit) {
        db.collection("posts").whereEqualTo("status", "pending")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
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
        db.collection("rideEvents").whereEqualTo("status", "pending")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
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
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val grouped = snap.documents
                    .mapNotNull { doc -> doc.getString("postId") }
                    .distinct()
                if (reportedPosts.size != grouped.size) {
                    // Just enough to keep the badge count accurate
                    // Full data loads when section 3 is selected
                    if (selectedSection != 3) {
                        reportedPosts.clear()
                        grouped.forEach { postId ->
                            reportedPosts.add(AdminPost(
                                id = postId, userName = "", description = "",
                                activity = "", distance = "", timestamp = 0L,
                                status = "reported", imageUrl = ""
                            ))
                        }
                    }
                }
                isLoadingReportedPosts = false
            }
    }

    LaunchedEffect(Unit) {
        db.collection("reportedComments")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val grouped = snap.documents
                    .mapNotNull { doc -> doc.getString("commentId") }
                    .distinct()
                if (selectedSection != 3) {
                    reportedComments.clear()
                    grouped.forEach { commentId ->
                        reportedComments.add(ReportedComment(
                            commentId = commentId, postId = "", userName = "",
                            text = "", reportCount = 1, timestamp = 0L
                        ))
                    }
                }
                isLoadingReportedComments = false
            }
    }

    LaunchedEffect(Unit) {
        db.collection("alerts")
            .whereNotEqualTo("status", "resolved")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                if (selectedSection != 5) {
                    activeAlerts.clear()
                    for (doc in snap.documents) {
                        activeAlerts.add(AdminAlert(
                            id            = doc.id,
                            riderName     = doc.getString("riderName")     ?: "",
                            emergencyType = doc.getString("emergencyType") ?: "",
                            locationName  = doc.getString("locationName")  ?: "",
                            status        = doc.getString("status")        ?: "active",
                            severity      = doc.getString("severity")      ?: "HIGH",
                            timestamp     = doc.getLong("timestamp")       ?: 0L,
                            photoUrl      = doc.getString("photoUrl"),
                            responderName = doc.getString("responderName")
                        ))
                    }
                }
                isLoadingAlerts = false
            }
    }

    LaunchedEffect(Unit) {
        db.collection("moderationLogs")
            .whereEqualTo("reviewed", false)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                if (selectedSection != 6) {
                    moderationLogs.clear()
                    for (doc in snap.documents) {
                        moderationLogs.add(ModerationLog(
                            id           = doc.id,
                            userName     = doc.getString("userName")     ?: "",
                            originalText = doc.getString("originalText") ?: "",
                            censoredText = doc.getString("censoredText") ?: "",
                            context      = doc.getString("context")      ?: "comment",
                            timestamp    = doc.getLong("timestamp")      ?: 0L,
                            reviewed     = false
                        ))
                    }
                }
                isLoadingModerationLogs = false
            }
    }

    LaunchedEffect(Unit) {
        db.collection("userReports")
            .whereEqualTo("reviewed", false)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                if (selectedSection != 8) {
                    userReports.clear()
                    for (doc in snap.documents) {
                        userReports.add(UserReport(
                            id           = doc.id,
                            reporterName = doc.getString("reporterName")  ?: "",
                            reportedName = doc.getString("reportedName")  ?: "",
                            reportedRole = doc.getString("reportedRole")  ?: "",
                            alertId      = doc.getString("alertId")       ?: "",
                            emergencyType= doc.getString("emergencyType") ?: "",
                            reason       = doc.getString("reason")        ?: "",
                            comment      = doc.getString("comment")       ?: "",
                            timestamp    = doc.getLong("timestamp")       ?: 0L,
                            reviewed     = doc.getBoolean("reviewed")     ?: false
                        ))
                    }
                }
                isLoadingUserReports = false
            }
    }

    // ── Section-specific full data listeners ──────────────────────────────────
    LaunchedEffect(selectedSection) {
        if (selectedSection != 1) return@LaunchedEffect
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

    LaunchedEffect(selectedSection) {
        if (selectedSection != 2) return@LaunchedEffect
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

    LaunchedEffect(selectedSection) {
        if (selectedSection != 3) return@LaunchedEffect // shared with comment reports
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



    LaunchedEffect(selectedSection) {
        if (selectedSection != 4) return@LaunchedEffect
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

    LaunchedEffect(selectedSection) {
        if (selectedSection != 3) return@LaunchedEffect
        db.collection("reportedComments")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { isLoadingReportedComments = false; return@addSnapshotListener }
                val grouped = snap.documents
                    .mapNotNull { doc ->
                        val commentId  = doc.getString("commentId")  ?: return@mapNotNull null
                        val postId     = doc.getString("postId")     ?: return@mapNotNull null
                        val userName   = doc.getString("userName")   ?: return@mapNotNull null
                        val text       = doc.getString("text")       ?: ""
                        val timestamp  = doc.getLong("timestamp")    ?: 0L
                        val reportedBy = doc.getString("reportedBy") ?: ""
                        val reason     = doc.getString("reason")     ?: ""
                        commentId to ReportedComment(
                            commentId   = commentId,
                            postId      = postId,
                            userName    = userName,
                            text        = text,
                            reportCount = 1,
                            timestamp   = timestamp,
                            reportedBy  = reportedBy,
                            reasons     = if (reason.isNotBlank()) listOf(reason) else emptyList()
                        )
                    }
                    .groupBy { it.first }
                    .map { (_, reports) ->
                        val latest = reports.maxByOrNull { it.second.timestamp }!!.second
                        val allReasons = reports
                            .flatMap { it.second.reasons }
                            .filter { it.isNotBlank() }
                            .distinct()
                        latest.copy(reportCount = reports.size, reasons = allReasons)
                    }
                    .sortedByDescending { it.reportCount }
                reportedComments.clear()
                reportedComments.addAll(grouped)
                isLoadingReportedComments = false
            }
    }
    LaunchedEffect(adminUserName) {
        if (adminUserName.isBlank()) return@LaunchedEffect
        db.collection("users").whereEqualTo("username", adminUserName)
            .limit(1).get()
            .addOnSuccessListener { snap ->
                val d = snap.documents.firstOrNull()?.getString("displayName")
                    ?.takeIf { it.isNotBlank() } ?: adminUserName
                adminDisplayName = d
            }
    }

    // Dashboard — total users
    LaunchedEffect(Unit) {
        db.collection("users").addSnapshotListener { snap, _ ->
            totalUsers = snap?.size() ?: 0
            dashUsersReady = true
        }
    }

    // Dashboard — total accepted posts
    LaunchedEffect(Unit) {
        db.collection("posts").whereEqualTo("status", "accepted")
            .addSnapshotListener { snap, _ ->
                totalPosts = snap?.size() ?: 0
                dashPostsReady = true
            }
    }

    // Dashboard — resolved alerts count
    LaunchedEffect(Unit) {
        db.collection("alerts").whereEqualTo("status", "resolved")
            .addSnapshotListener { snap, _ ->
                resolvedAlerts = snap?.size() ?: 0
                dashAlertsReady = true
            }
    }

    // Dashboard — recent activity feed (last 10 across posts, alerts, reports)
    LaunchedEffect(Unit) {
        db.collection("posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snap, _ ->
                val items = snap?.documents?.mapNotNull { doc ->
                    val status = doc.getString("status") ?: return@mapNotNull null
                    ActivityItem(
                        id        = doc.id,
                        type      = "post",
                        title     = when (status) {
                            "accepted" -> "Post approved"
                            "pending"  -> "New post pending"
                            "rejected" -> "Post rejected"
                            else       -> "Post updated"
                        },
                        subtitle  = "by ${doc.getString("userName") ?: "Unknown"}",
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )
                } ?: emptyList()
                recentActivity.removeAll { it.type == "post" }
                recentActivity.addAll(items)
                recentActivity.sortByDescending { it.timestamp }
            }
    }

    LaunchedEffect(Unit) {
        db.collection("alerts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snap, _ ->
                val items = snap?.documents?.mapNotNull { doc ->
                    ActivityItem(
                        id        = doc.id,
                        type      = "alert",
                        title     = "${doc.getString("emergencyType") ?: "Emergency"} alert",
                        subtitle  = "by ${doc.getString("riderName") ?: "Unknown"} · ${doc.getString("status") ?: "active"}",
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )
                } ?: emptyList()
                recentActivity.removeAll { it.type == "alert" }
                recentActivity.addAll(items)
                recentActivity.sortByDescending { it.timestamp }
            }
    }

    LaunchedEffect(Unit) {
        db.collection("reportedPosts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snap, _ ->
                val items = snap?.documents?.mapNotNull { doc ->
                    ActivityItem(
                        id        = doc.id,
                        type      = "report",
                        title     = "Post reported",
                        subtitle  = "by ${doc.getString("reportedBy") ?: "Unknown"}",
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )
                } ?: emptyList()
                recentActivity.removeAll { it.type == "report" }
                recentActivity.addAll(items)
                recentActivity.sortByDescending { it.timestamp }
            }
    }

    LaunchedEffect(selectedSection) {
        if (selectedSection != 6) return@LaunchedEffect
        db.collection("moderationLogs")
            .whereEqualTo("reviewed", false)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { isLoadingModerationLogs = false; return@addSnapshotListener }
                moderationLogs.clear()
                for (doc in snap.documents) {
                    moderationLogs.add(ModerationLog(
                        id           = doc.id,
                        userName     = doc.getString("userName")     ?: "",
                        originalText = doc.getString("originalText") ?: "",
                        censoredText = doc.getString("censoredText") ?: "",
                        context      = doc.getString("context")      ?: "comment",
                        timestamp    = doc.getLong("timestamp")      ?: 0L,
                        reviewed     = false
                    ))
                }
                isLoadingModerationLogs = false
            }
    }
    LaunchedEffect(selectedSection) {
        if (selectedSection != 8) return@LaunchedEffect
        db.collection("userReports")
            .whereEqualTo("reviewed", false)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { isLoadingUserReports = false; return@addSnapshotListener }
                userReports.clear()
                for (doc in snap.documents) {
                    userReports.add(UserReport(
                        id            = doc.id,
                        reporterName  = doc.getString("reporterName")  ?: "",
                        reportedName  = doc.getString("reportedName")  ?: "",
                        reportedRole  = doc.getString("reportedRole")  ?: "",
                        alertId       = doc.getString("alertId")       ?: "",
                        emergencyType = doc.getString("emergencyType") ?: "",
                        reason        = doc.getString("reason")        ?: "",
                        comment       = doc.getString("comment")       ?: "",
                        timestamp     = doc.getLong("timestamp")       ?: 0L,
                        reviewed      = doc.getBoolean("reviewed")     ?: false
                    ))
                }
                isLoadingUserReports = false
            }
    }

    LaunchedEffect(selectedSection) {
        if (selectedSection != 7) return@LaunchedEffect
        db.collection("moderationAuditLog")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { isLoadingAuditLogs = false; return@addSnapshotListener }
                auditLogs.clear()
                for (doc in snap.documents) {
                    auditLogs.add(AuditLogEntry(
                        id               = doc.id,
                        adminUserName    = doc.getString("adminUserName")    ?: "",
                        adminDisplayName = doc.getString("adminDisplayName") ?: "",
                        action           = doc.getString("action")           ?: "",
                        targetType       = doc.getString("targetType")       ?: "",
                        targetUser       = doc.getString("targetUser")       ?: "",
                        detail           = doc.getString("detail")           ?: "",
                        timestamp        = doc.getLong("timestamp")          ?: 0L
                    ))
                }
                isLoadingAuditLogs = false
            }
    }

    LaunchedEffect(selectedSection) {
        if (selectedSection != 5) return@LaunchedEffect
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
    fun logAudit(action: String, targetType: String, targetUser: String, detail: String) {
        if (adminUserName.isBlank()) return
        db.collection("moderationAuditLog").add(hashMapOf(
            "adminUserName"    to adminUserName,
            "adminDisplayName" to adminDisplayName,
            "action"           to action,
            "targetType"       to targetType,
            "targetUser"       to targetUser,
            "detail"           to detail,
            "timestamp"        to System.currentTimeMillis()
        ))
    }
    // ── Actions ───────────────────────────────────────────────────────────────
    fun toast(msg: String, isSuccess: Boolean = true) {
        if (isSuccess) successMessage = msg else errorMessage = msg
    }

    fun approvePost(post: AdminPost) {
        pendingPosts.remove(post)  // optimistic removal
        db.collection("posts").document(post.id).update("status", "accepted")
            .addOnSuccessListener {
                db.collection("users")
                    .whereEqualTo("username", post.userName)
                    .limit(1).get()
                    .addOnSuccessListener { snap ->
                        val displayName = snap.documents.firstOrNull()
                            ?.getString("displayName")
                            ?.takeIf { it.isNotBlank() } ?: post.userName
                        db.collection("notifications").add(hashMapOf(
                            "userName"  to post.userName,
                            "message"   to "✅ Hey $displayName, your post has been approved and is now live!",
                            "type"      to "accepted",
                            "timestamp" to System.currentTimeMillis(), "read" to false))
                    }
                    .addOnFailureListener {
                        // Fallback — send notification without display name
                        db.collection("notifications").add(hashMapOf(
                            "userName"  to post.userName,
                            "message"   to "✅ Your post has been approved and is now live!",
                            "type"      to "accepted",
                            "timestamp" to System.currentTimeMillis(), "read" to false))
                    }
                toast("Post approved!")
                logAudit("Approved post", "post", post.userName, post.description.take(80))
            }.addOnFailureListener {
                pendingPosts.add(post)  // restore on failure
                toast("Failed to approve post.", false)
            }
    }

    fun rejectPost(post: AdminPost) {
        pendingPosts.remove(post)  // optimistic removal
        db.collection("posts").document(post.id).update("status", "rejected")
            .addOnSuccessListener {
                db.collection("users")
                    .whereEqualTo("username", post.userName)
                    .limit(1).get()
                    .addOnSuccessListener { snap ->
                        val displayName = snap.documents.firstOrNull()
                            ?.getString("displayName")
                            ?.takeIf { it.isNotBlank() } ?: post.userName
                        db.collection("notifications").add(hashMapOf(
                            "userName"  to post.userName,
                            "message"   to "❌ Sorry $displayName, your post was not approved. Please follow community guidelines.",
                            "type"      to "rejected",
                            "timestamp" to System.currentTimeMillis(), "read" to false))
                    }
                    .addOnFailureListener {
                        db.collection("notifications").add(hashMapOf(
                            "userName"  to post.userName,
                            "message"   to "❌ Your post was not approved. Please follow community guidelines.",
                            "type"      to "rejected",
                            "timestamp" to System.currentTimeMillis(), "read" to false))
                    }
                toast("Post rejected.")
                logAudit("Rejected post", "post", post.userName, post.description.take(80))
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
                    logAudit("Deleted post", "post", post.userName, post.description.take(80))
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
                db.collection("users")
                    .whereEqualTo("username", ride.organizer)
                    .limit(1).get()
                    .addOnSuccessListener { snap ->
                        val displayName = snap.documents.firstOrNull()
                            ?.getString("displayName")
                            ?.takeIf { it.isNotBlank() } ?: ride.organizer
                        db.collection("notifications").add(hashMapOf(
                            "userName"  to ride.organizer,
                            "message"   to "✅ Hey $displayName, your ride \"${ride.title}\" is approved and now visible!",
                            "type"      to "ride", "eventId" to ride.id,
                            "timestamp" to System.currentTimeMillis(), "read" to false))
                    }
                    .addOnFailureListener {
                        db.collection("notifications").add(hashMapOf(
                            "userName"  to ride.organizer,
                            "message"   to "✅ Your ride \"${ride.title}\" is approved and now visible!",
                            "type"      to "ride", "eventId" to ride.id,
                            "timestamp" to System.currentTimeMillis(), "read" to false))
                    }
                toast("Ride approved!")
                logAudit("Approved ride", "ride", ride.organizer, ride.title)
            }.addOnFailureListener {
                pendingRides.add(ride)  // restore on failure
                toast("Failed to approve ride.", false)
            }
    }

    fun rejectRide(ride: RideEvent) {
        pendingRides.remove(ride)  // optimistic removal
        db.collection("rideEvents").document(ride.id).update("status", "rejected")
            .addOnSuccessListener {
                db.collection("users")
                    .whereEqualTo("username", ride.organizer)
                    .limit(1).get()
                    .addOnSuccessListener { snap ->
                        val displayName = snap.documents.firstOrNull()
                            ?.getString("displayName")
                            ?.takeIf { it.isNotBlank() } ?: ride.organizer
                        db.collection("notifications").add(hashMapOf(
                            "userName"  to ride.organizer,
                            "message"   to "❌ Sorry $displayName, your ride \"${ride.title}\" was not approved. Please review and resubmit.",
                            "type"      to "ride", "eventId" to ride.id,
                            "timestamp" to System.currentTimeMillis(), "read" to false))
                    }
                    .addOnFailureListener {
                        db.collection("notifications").add(hashMapOf(
                            "userName"  to ride.organizer,
                            "message"   to "❌ Your ride \"${ride.title}\" was not approved. Please review and resubmit.",
                            "type"      to "ride", "eventId" to ride.id,
                            "timestamp" to System.currentTimeMillis(), "read" to false))
                    }
                toast("Ride rejected.")
                logAudit("Rejected ride", "ride", ride.organizer, ride.title)
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
                logAudit("Removed alert photo", "photo", report.reportedBy, report.alertId)
            }.addOnFailureListener { toast("Failed to remove photo.", false) }
    }

    fun dismissReports(report: ReportedImage) {
        db.collection("reportedImages").whereEqualTo("alertId", report.alertId)
            .get().addOnSuccessListener { snap ->
                snap.documents.forEach { it.reference.delete() }
                toast("Reports dismissed.")
                logAudit("Dismissed photo reports", "photo", report.reportedBy, report.alertId)
            }.addOnFailureListener { toast("Failed to dismiss.", false) }
    }

    fun forceResolveAlert(alert: AdminAlert) {
        db.collection("alerts").document(alert.id).update("status", "resolved")
            .addOnSuccessListener {
                db.collection("users")
                    .whereEqualTo("username", alert.riderName)
                    .limit(1).get()
                    .addOnSuccessListener { snap ->
                        val displayName = snap.documents.firstOrNull()
                            ?.getString("displayName")
                            ?.takeIf { it.isNotBlank() } ?: alert.riderName
                        db.collection("notifications").add(hashMapOf(
                            "userName"  to alert.riderName,
                            "message"   to "ℹ️ Hey $displayName, your ${alert.emergencyType} alert was closed by an admin.",
                            "type"      to "alert",
                            "timestamp" to System.currentTimeMillis(), "read" to false))
                    }
                    .addOnFailureListener {
                        db.collection("notifications").add(hashMapOf(
                            "userName"  to alert.riderName,
                            "message"   to "ℹ️ Your ${alert.emergencyType} alert was closed by an admin.",
                            "type"      to "alert",
                            "timestamp" to System.currentTimeMillis(), "read" to false))
                    }
                toast("Alert resolved.")
                logAudit("Resolved alert", "alert", alert.riderName, alert.emergencyType)
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
            NavSection("Dashboard",      Icons.Default.Dashboard,      0,                                          AGreen700),
    NavSection("Posts",          Icons.Default.Article,        pendingPosts.size,                          AAmber500),
    NavSection("Rides",          Icons.Default.DirectionsBike, pendingRides.size,                          Color(0xFF1976D2)),
    NavSection("Reports",        Icons.Default.Flag,           reportedPosts.size + reportedComments.size, ARedColor),
    NavSection("Photo Reports",  Icons.Default.Image,          reportedImages.size,                        Color(0xFFEA580C)),
    NavSection("Alerts",         Icons.Default.Warning,        activeAlerts.size,                          Color(0xFFEF4444)),
    NavSection("Profanity Logs", Icons.Default.Shield,         moderationLogs.size,                        Color(0xFF7C3AED)),
    NavSection("Audit Log",      Icons.Default.ManageAccounts, auditLogs.size,                             Color(0xFF0891B2)),
    NavSection("User Reports",   Icons.Default.PersonOff,      userReports.size,                           Color(0xFF7C3AED))
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
                            Box(Modifier
                                .size(40.dp)
                                .clip(CircleShape)
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
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        bottom = paddingValues.calculateBottomPadding() + 24.dp
                    )
                ) {
                    itemsIndexed(sections) { index: Int, sec: NavSection ->
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
                                        Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(
                                                if (isSelected) sec.badgeColor else sec.badgeColor.copy(
                                                    alpha = 0.25f
                                                )
                                            )
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
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Section icon
                            Box(Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
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
                                    0 -> "Overview"
                                    1 -> "${pendingPosts.size} pending"
                                    2 -> "${pendingRides.size} pending"
                                    3 -> "${reportedPosts.size + reportedComments.size} reported"
                                    4 -> "${reportedImages.size} reported"
                                    5 -> "${activeAlerts.size} active"
                                    6 -> "${moderationLogs.size} entries"
                                    7 -> "${auditLogs.size} entries"
                                    8 -> "${userReports.size} pending"
                                    else -> ""
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
                    modifier       = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start  = 16.dp, end = 16.dp, top = 12.dp,
                        bottom = paddingValues.calculateBottomPadding() + 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (selectedSection) {
                        0 -> {
                            if (isLoadingDashboard) {
                                item { AdminLoadingState() }
                            } else {
                                // ── Dashboard ─────────────────────────────────────
                                item {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(
                                                        AGreen900,
                                                        Color(0xFF0A5C3D)
                                                    )
                                                )
                                            )
                                            .padding(20.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Admin Dashboard", fontSize = 20.sp,
                                                fontWeight = FontWeight.ExtraBold, color = Color.White)
                                            Spacer(Modifier.height(12.dp))
                                            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                                            Spacer(Modifier.height(12.dp))
                                            // Quick stats row
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceAround
                                            ) {
                                                DashboardStat("$totalUsers",    "Users",    Icons.Default.People,       Color(0xFF90CAF9))
                                                DashboardStatDivider()
                                                DashboardStat("$totalPosts",   "Posts",    Icons.Default.Article,      AAmber500)
                                                DashboardStatDivider()
                                                DashboardStat("$resolvedAlerts", "Resolved", Icons.Default.CheckCircle, Color(0xFF81C784))
                                                DashboardStatDivider()
                                                DashboardStat("${activeAlerts.size}", "Active SOS", Icons.Default.Warning, Color(0xFFEF9A9A))
                                            }
                                        }
                                    }
                                }

                                // Needs attention cards
                                item {
                                    Spacer(Modifier.height(4.dp))
                                    Text("Needs Attention", fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold, color = AOnSurface,
                                        modifier = Modifier.padding(start = 2.dp))
                                    Spacer(Modifier.height(8.dp))
                                    val attentionItems = listOf(
                                        Triple("Posts",         pendingPosts.size,                          1),
                                        Triple("Rides",         pendingRides.size,                          2),
                                        Triple("Reports",       reportedPosts.size + reportedComments.size, 3),
                                        Triple("Photo Reports", reportedImages.size,                        4),
                                        Triple("Active Alerts", activeAlerts.size,                          5)
                                    ).filter { it.second > 0 }

                                    if (attentionItems.isEmpty()) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(AGreen50)
                                                .padding(20.dp),
                                            Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Icon(Icons.Default.CheckCircle, null,
                                                    tint = AGreen900, modifier = Modifier.size(20.dp))
                                                Text("All clear — nothing needs attention!",
                                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                                    color = AGreen900)
                                            }
                                        }
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            attentionItems.forEach { (label, count, sectionIdx) ->
                                                val isUrgent = label.contains("Alert") || label.contains("Report")
                                                Row(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(if (isUrgent) ARedLight else AAmber50)
                                                        .clickable { selectedSection = sectionIdx }
                                                        .padding(
                                                            horizontal = 14.dp,
                                                            vertical = 12.dp
                                                        ),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        Box(
                                                            Modifier
                                                                .size(36.dp)
                                                                .clip(RoundedCornerShape(10.dp))
                                                                .background(
                                                                    if (isUrgent) ARedColor.copy(
                                                                        alpha = 0.12f
                                                                    ) else AAmber500.copy(alpha = 0.12f)
                                                                ),
                                                            Alignment.Center
                                                        ) {
                                                            Icon(
                                                                if (isUrgent) Icons.Default.Warning else Icons.Default.HourglassTop,
                                                                null,
                                                                tint = if (isUrgent) ARedColor else AAmber500,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                        Column {
                                                            Text(label, fontSize = 13.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = if (isUrgent) ARedColor else Color(0xFF92400E))
                                                            Text("Tap to review",
                                                                fontSize = 11.sp,
                                                                color = if (isUrgent) ARedColor.copy(alpha = 0.65f) else Color(0xFFB45309))
                                                        }
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Box(
                                                            Modifier
                                                                .clip(RoundedCornerShape(20.dp))
                                                                .background(if (isUrgent) ARedColor else AAmber500)
                                                                .padding(
                                                                    horizontal = 10.dp,
                                                                    vertical = 4.dp
                                                                )
                                                        ) {
                                                            Text("$count", fontSize = 12.sp,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                color = Color.White)
                                                        }
                                                        Icon(Icons.Default.ChevronRight, null,
                                                            tint = if (isUrgent) ARedColor else AAmber500,
                                                            modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Recent activity feed — self-contained scrollable card
                                item {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Recent Activity", fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold, color = AOnSurface,
                                        modifier = Modifier.padding(start = 2.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))

                                    Card(
                                        modifier  = Modifier.fillMaxWidth(),
                                        shape     = RoundedCornerShape(16.dp),
                                        colors    = CardDefaults.cardColors(containerColor = AWhite),
                                        elevation = CardDefaults.cardElevation(2.dp)
                                    ) {
                                        if (recentActivity.isEmpty()) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 32.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.Inbox, null,
                                                    tint = AMuted, modifier = Modifier.size(28.dp))
                                                Text("No activity yet", fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold, color = AMuted)
                                            }
                                        } else {
                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(max = 340.dp)
                                                        .verticalScroll(rememberScrollState())
                                                ) {
                                                    recentActivity.take(10).forEachIndexed { index, activity ->
                                                        val (icon, iconBg, iconTint) = when (activity.type) {
                                                            "alert"  -> Triple(Icons.Default.Warning, Color(0xFFFFEBEE), ARedColor)
                                                            "report" -> Triple(Icons.Default.Flag,    AAmber50,          AAmber500)
                                                            else     -> Triple(Icons.Default.Article, AGreen50,          AGreen900)
                                                        }
                                                        Row(
                                                            Modifier
                                                                .fillMaxWidth()
                                                                .padding(
                                                                    horizontal = 12.dp,
                                                                    vertical = 10.dp
                                                                ),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                        ) {
                                                            Box(
                                                                Modifier
                                                                    .size(36.dp)
                                                                    .clip(RoundedCornerShape(10.dp))
                                                                    .background(iconBg),
                                                                Alignment.Center
                                                            ) {
                                                                Icon(icon, null, tint = iconTint,
                                                                    modifier = Modifier.size(16.dp))
                                                            }
                                                            Column(
                                                                Modifier.weight(1f),
                                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                                            ) {
                                                                Text(activity.title, fontSize = 13.sp,
                                                                    fontWeight = FontWeight.SemiBold, color = AOnSurface)
                                                                Text(activity.subtitle, fontSize = 11.sp, color = AMuted)
                                                            }
                                                            Text(formatAdminTime(activity.timestamp),
                                                                fontSize = 10.sp, color = AMuted)
                                                        }
                                                        if (index < recentActivity.take(10).lastIndex) {
                                                            HorizontalDivider(
                                                                modifier = Modifier.padding(horizontal = 12.dp),
                                                                color = ADivider, thickness = 0.5.dp
                                                            )
                                                        }
                                                    }
                                                }
                                                // Fade hint — only shows when there are enough items to scroll
                                                if (recentActivity.size > 4) {
                                                    Box(
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .height(32.dp)
                                                            .align(Alignment.BottomCenter)
                                                            .background(
                                                                Brush.verticalGradient(
                                                                    listOf(
                                                                        Color.Transparent,
                                                                        AWhite
                                                                    )
                                                                )
                                                            )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } // end else isLoadingDashboard
                        1 -> {
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
                                                        Box(Modifier
                                                            .size(52.dp)
                                                            .clip(CircleShape)
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
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .height(46.dp),
                                                                shape  = RoundedCornerShape(12.dp),
                                                                colors = ButtonDefaults.buttonColors(
                                                                    containerColor = AGreen900,
                                                                    contentColor   = Color.White)
                                                            ) { Text("Approve all", fontWeight = FontWeight.Bold) }
                                                            OutlinedButton(
                                                                onClick  = { showApproveAll = false },
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .height(44.dp),
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
                        2 -> {
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
                                                        Box(Modifier
                                                            .size(52.dp)
                                                            .clip(CircleShape)
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
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .height(46.dp),
                                                                shape  = RoundedCornerShape(12.dp),
                                                                colors = ButtonDefaults.buttonColors(
                                                                    containerColor = AGreen900,
                                                                    contentColor   = Color.White)
                                                            ) { Text("Approve all", fontWeight = FontWeight.Bold) }
                                                            OutlinedButton(
                                                                onClick  = { showApproveAll = false },
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .height(44.dp),
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
                        3 -> {
                            val isLoadingReports = isLoadingReportedPosts || isLoadingReportedComments
                            item {
                                // ── Chip toggle ───────────────────────────────
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        "Posts"    to reportedPosts.size,
                                        "Comments" to reportedComments.size
                                    ).forEach { (label, count) ->
                                        val selected = selectedReportChip == label
                                        FilterChip(
                                            selected = selected,
                                            onClick  = { selectedReportChip = label },
                                            label = {
                                                Text(
                                                    "$label ($count)",
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                                    fontSize   = 13.sp
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = ARedColor,
                                                selectedLabelColor     = Color.White,
                                                containerColor         = ARedColor.copy(alpha = 0.08f),
                                                labelColor             = ARedColor
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled             = true,
                                                selected            = selected,
                                                borderColor         = ARedColor.copy(alpha = 0.4f),
                                                selectedBorderColor = Color.Transparent,
                                                borderWidth         = 1.dp,
                                                selectedBorderWidth = 0.dp
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                }
                            }

                            if (isLoadingReports) {
                                item { AdminLoadingState() }
                            } else if (selectedReportChip == "Posts") {
                                if (reportedPosts.isEmpty()) {
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
                                            onDelete  = {
                                                db.collection("posts").document(post.id).delete()
                                                db.collection("reportedPosts")
                                                    .whereEqualTo("postId", post.id)
                                                    .get()
                                                    .addOnSuccessListener { snap ->
                                                        snap.documents.forEach { it.reference.delete() }
                                                    }
                                                db.collection("notifications").add(hashMapOf(
                                                    "userName"  to post.userName,
                                                    "message"   to "Your post was permanently removed by an admin for violating community guidelines.",
                                                    "type"      to "moderation",
                                                    "timestamp" to System.currentTimeMillis(),
                                                    "read"      to false
                                                ))
                                                reportedPosts.remove(post)
                                            },
                                            onApprove = {
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
                                                db.collection("posts").document(post.id).delete()
                                                db.collection("reportedPosts")
                                                    .whereEqualTo("postId", post.id)
                                                    .get()
                                                    .addOnSuccessListener { snap ->
                                                        snap.documents.forEach { it.reference.delete() }
                                                    }
                                                db.collection("users")
                                                    .whereEqualTo("username", post.userName)
                                                    .limit(1).get()
                                                    .addOnSuccessListener { snap ->
                                                        val displayName = snap.documents.firstOrNull()
                                                            ?.getString("displayName")
                                                            ?.takeIf { it.isNotBlank() } ?: post.userName
                                                        db.collection("notifications").add(hashMapOf(
                                                            "userName"  to post.userName,
                                                            "message"   to "❌ Sorry $displayName, your post was removed after being reported for violating community guidelines.",
                                                            "type"      to "rejected",
                                                            "timestamp" to System.currentTimeMillis(),
                                                            "read"      to false
                                                        ))
                                                    }
                                                    .addOnFailureListener {
                                                        db.collection("notifications").add(hashMapOf(
                                                            "userName"  to post.userName,
                                                            "message"   to "❌ Your post was removed after being reported for violating community guidelines.",
                                                            "type"      to "rejected",
                                                            "timestamp" to System.currentTimeMillis(),
                                                            "read"      to false
                                                        ))
                                                    }
                                                reportedPosts.remove(post)
                                            }
                                        )
                                    }
                                }
                            } else {
                                // Comments chip
                                if (reportedComments.isEmpty()) {
                                    item { AdminEmptyState(Icons.Default.CheckCircle, "No reported comments", "All comments have been reviewed.") }
                                } else {
                                    item {
                                        Text(
                                            "${reportedComments.size} reported comment${if (reportedComments.size != 1) "s" else ""}",
                                            fontSize = 12.sp, color = AMuted,
                                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                                        )
                                    }
                                    items(reportedComments, key = { it.commentId }) { reported ->
                                        AdminCommentReportCard(
                                            reported  = reported,
                                            onRestore = {
                                                reportedComments.remove(reported)
                                                db.collection("posts").document(reported.postId)
                                                    .collection("comments").document(reported.commentId)
                                                    .update("status", "visible")
                                                    .addOnSuccessListener {
                                                        db.collection("reportedComments")
                                                            .whereEqualTo("commentId", reported.commentId)
                                                            .get()
                                                            .addOnSuccessListener { snap ->
                                                                snap.documents.forEach { it.reference.delete() }
                                                            }
                                                        db.collection("posts").document(reported.postId)
                                                            .update("comments", com.google.firebase.firestore.FieldValue.increment(1))
                                                        db.collection("notifications").add(hashMapOf(
                                                            "userName"  to reported.userName,
                                                            "message"   to "✅ A report on your comment was reviewed and dismissed. Your comment is visible again.",
                                                            "type"      to "moderation_restored",
                                                            "timestamp" to System.currentTimeMillis(),
                                                            "read"      to false
                                                        ))
                                                        toast("Comment restored.")
                                                        logAudit("Restored comment", "comment", reported.userName, reported.text.take(80))
                                                    }
                                                    .addOnFailureListener {
                                                        reportedComments.add(reported)
                                                        toast("Failed to restore.", false)
                                                    }
                                            },
                                            onDelete  = {
                                                reportedComments.remove(reported)
                                                db.collection("posts").document(reported.postId)
                                                    .collection("comments").document(reported.commentId)
                                                    .delete()
                                                    .addOnSuccessListener {
                                                        db.collection("reportedComments")
                                                            .whereEqualTo("commentId", reported.commentId)
                                                            .get()
                                                            .addOnSuccessListener { snap ->
                                                                snap.documents.forEach { it.reference.delete() }
                                                            }
                                                        db.collection("notifications").add(hashMapOf(
                                                            "userName"  to reported.userName,
                                                            "message"   to "❌ Your comment was removed by an admin for violating community guidelines.",
                                                            "type"      to "rejected",
                                                            "timestamp" to System.currentTimeMillis(),
                                                            "read"      to false
                                                        ))
                                                        toast("Comment deleted.")
                                                        logAudit("Deleted comment", "comment", reported.userName, reported.text.take(80))
                                                    }
                                                    .addOnFailureListener {
                                                        reportedComments.add(reported)
                                                        toast("Failed to delete.", false)
                                                    }
                                            },
                                            onDismiss = {
                                                reportedComments.remove(reported)
                                                db.collection("reportedComments")
                                                    .whereEqualTo("commentId", reported.commentId)
                                                    .get()
                                                    .addOnSuccessListener { snap ->
                                                        snap.documents.forEach { it.reference.delete() }
                                                        toast("Reports dismissed.")
                                                        logAudit("Dismissed comment reports", "comment", reported.userName, reported.text.take(80))
                                                    }
                                                    .addOnFailureListener {
                                                        reportedComments.add(reported)
                                                        toast("Failed to dismiss.", false)
                                                    }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        4 -> {
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
                        6 -> {
                            if (isLoadingModerationLogs) {
                                item { AdminLoadingState() }
                            } else if (moderationLogs.isEmpty()) {
                                item { AdminEmptyState(Icons.Default.Shield, "No censored content", "The profanity filter has not triggered yet.") }
                            } else {
                                item {
                                    var showMarkAllDialog by remember { mutableStateOf(false) }

                                    if (showMarkAllDialog) {
                                        AlertDialog(
                                            onDismissRequest = { showMarkAllDialog = false },
                                            shape = RoundedCornerShape(20.dp),
                                            containerColor = AWhite,
                                            icon = {
                                                Box(Modifier
                                                    .size(52.dp)
                                                    .clip(CircleShape)
                                                    .background(AGreen50),
                                                    contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.DoneAll, null,
                                                        tint = AGreen900, modifier = Modifier.size(26.dp))
                                                }
                                            },
                                            title = {
                                                Text("Mark all as reviewed?",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 17.sp, color = AOnSurface,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.fillMaxWidth())
                                            },
                                            text = {
                                                Text("All ${moderationLogs.size} entries will be marked as reviewed and removed from this queue. The records are preserved in Firestore.",
                                                    fontSize = 13.sp, color = AMuted,
                                                    textAlign = TextAlign.Center)
                                            },
                                            confirmButton = {
                                                Column(Modifier.fillMaxWidth(),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(
                                                        onClick = {
                                                            showMarkAllDialog = false
                                                            moderationLogs.toList().forEach { log ->
                                                                db.collection("moderationLogs")
                                                                    .document(log.id)
                                                                    .update("reviewed", true)
                                                            }
                                                            moderationLogs.clear()
                                                            toast("All entries marked as reviewed.")
                                                            logAudit("Marked all profanity logs reviewed", "moderation", adminUserName, "${moderationLogs.size} entries")
                                                        },
                                                        modifier = Modifier.fillMaxWidth().height(46.dp),
                                                        shape = RoundedCornerShape(12.dp),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = AGreen900,
                                                            contentColor = Color.White)
                                                    ) { Text("Mark all reviewed", fontWeight = FontWeight.Bold) }
                                                    OutlinedButton(
                                                        onClick = { showMarkAllDialog = false },
                                                        modifier = Modifier.fillMaxWidth().height(44.dp),
                                                        shape = RoundedCornerShape(12.dp)
                                                    ) { Text("Cancel", color = AMuted) }
                                                }
                                            }
                                        )
                                    }

                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${moderationLogs.size} unreviewed entr${if (moderationLogs.size != 1) "ies" else "y"}",
                                            fontSize = 12.sp, color = AMuted,
                                            modifier = Modifier.padding(start = 4.dp))
                                        TextButton(onClick = { showMarkAllDialog = true }) {
                                            Icon(Icons.Default.DoneAll, null,
                                                modifier = Modifier.size(14.dp),
                                                tint = AGreen900)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Mark all reviewed", fontSize = 12.sp,
                                                color = AGreen900, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                items(moderationLogs, key = { it.id }) { log ->
                                    AdminModerationLogCard(
                                        log = log,
                                        onDismiss = {
                                            moderationLogs.remove(log)
                                            db.collection("moderationLogs")
                                                .document(log.id)
                                                .update("reviewed", true)
                                                .addOnSuccessListener {
                                                    toast("Entry marked as reviewed.")
                                                    logAudit("Reviewed profanity log", "moderation", log.userName, log.originalText.take(80))
                                                }
                                                .addOnFailureListener {
                                                    moderationLogs.add(log)
                                                    toast("Failed to dismiss.", false)
                                                }
                                        }
                                    )
                                }
                            }
                        }
                        7 -> {
                            if (isLoadingAuditLogs) {
                                item { AdminLoadingState() }
                            } else if (auditLogs.isEmpty()) {
                                item { AdminEmptyState(Icons.Default.ManageAccounts, "No audit entries yet", "Actions taken by admins will appear here.") }
                            } else {
                                item {
                                    Text(
                                        "${auditLogs.size} action${if (auditLogs.size != 1) "s" else ""} logged",
                                        fontSize = 12.sp, color = AMuted,
                                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                                    )
                                }
                                items(auditLogs, key = { it.id }) { entry ->
                                    AdminAuditCard(entry = entry)
                                }
                            }
                        }

                        5 -> {
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

                        8 -> {
                            if (isLoadingUserReports) {
                                item { AdminLoadingState() }
                            } else if (userReports.isEmpty()) {
                                item { AdminEmptyState(Icons.Default.PersonOff, "No user reports", "No riders or helpers have been reported.") }
                            } else {
                                item {
                                    Text(
                                        "${userReports.size} pending report${if (userReports.size != 1) "s" else ""}",
                                        fontSize = 12.sp, color = AMuted,
                                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                                    )
                                }
                                items(userReports, key = { it.id }) { report ->
                                    AdminUserReportCard(
                                        report    = report,
                                        onDismiss = {
                                            db.collection("userReports").document(report.id)
                                                .update("reviewed", true)
                                                .addOnSuccessListener {
                                                    userReports.remove(report)
                                                    toast("Report dismissed.")
                                                    logAudit("Dismissed user report", "user_report", report.reportedName, report.reason)
                                                }
                                                .addOnFailureListener { toast("Failed to dismiss.", false) }
                                        },
                                        onWarn = {
                                            db.collection("userReports").document(report.id)
                                                .update("reviewed", true)
                                                .addOnSuccessListener {
                                                    userReports.remove(report)
                                                    db.collection("notifications").add(hashMapOf(
                                                        "userName"  to report.reportedName,
                                                        "message"   to "⚠️ You have received a warning from an admin regarding your behavior on the platform. Please review community guidelines.",
                                                        "type"      to "moderation",
                                                        "timestamp" to System.currentTimeMillis(),
                                                        "read"      to false
                                                    ))
                                                    toast("Warning sent to ${report.reportedName}.")
                                                    logAudit("Warned user", "user_report", report.reportedName, report.reason)
                                                }
                                                .addOnFailureListener { toast("Failed to warn.", false) }
                                        }
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
    var postDisplayName by remember(post.userName) { mutableStateOf(post.userName) }
    LaunchedEffect(post.userName) {
        FirebaseFirestore.getInstance().collection("users")
            .whereEqualTo("username", post.userName).limit(1).get()
            .addOnSuccessListener { snap ->
                val d = snap.documents.firstOrNull()?.getString("displayName")
                    ?.takeIf { it.isNotBlank() } ?: post.userName
                postDisplayName = d
            }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(20.dp), containerColor = AWhite,
            icon = { Box(Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(ARedLight), Alignment.Center) {
                Icon(Icons.Default.DeleteForever, null, tint = ARedColor, modifier = Modifier.size(26.dp)) } },
            title = { Text("Delete Post?", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                color = AOnSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text  = { Text("This will permanently remove the post. This cannot be undone.",
                fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center) },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showDeleteDialog = false; onDelete() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ARedColor, contentColor = Color.White)) {
                        Text("Delete permanently", fontWeight = FontWeight.Bold, color = Color.White) }
                    OutlinedButton(onClick = { showDeleteDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("Cancel", color = AMuted) }
                }
            }
        )
    }

    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            shape = RoundedCornerShape(20.dp), containerColor = AWhite,
            icon = { Box(Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(AAmber50), Alignment.Center) {
                Icon(Icons.Default.Cancel, null, tint = AAmber500, modifier = Modifier.size(26.dp)) } },
            title = { Text("Reject Post?", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                color = AOnSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF9FAFB))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("by $postDisplayName", fontSize = 13.sp,
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AAmber500, contentColor = Color.White)) {
                        Text("Reject post", fontWeight = FontWeight.Bold, color = Color.White) }
                    OutlinedButton(onClick = { showRejectDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("Cancel", color = AMuted) }
                }
            }
        )
    }

    Card(modifier = Modifier
        .fillMaxWidth()
        .animateContentSize(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AWhite), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.fillMaxWidth()) {
            if (post.imageUrl.isNotBlank()) {
                AsyncImage(model = post.imageUrl, contentDescription = "Post image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)))
            }
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(AGreen900, AGreen700))), Alignment.Center) {
                            Text(postDisplayName.take(1).uppercase(), fontSize = 14.sp,
                                fontWeight = FontWeight.Bold, color = Color.White) }
                        Column {
                            Text(postDisplayName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AOnSurface)
                            Text(formatAdminTime(post.timestamp), fontSize = 11.sp, color = AMuted)
                        }
                    }
                    Box(Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AGreen50)
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
                    Button(onClick = onApprove, modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AGreen900, contentColor = Color.White)) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Approve", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                    OutlinedButton(onClick = { showRejectDialog = true }, modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
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

// ── Admin Comment Report Card ─────────────────────────────────────────────────
@Composable
private fun AdminCommentReportCard(
    reported: ReportedComment,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showDeleteDialog  by remember { mutableStateOf(false) }
    var showDismissDialog by remember { mutableStateOf(false) }
    var authorDisplayName   by remember(reported.userName)   { mutableStateOf(reported.userName) }
    var reporterDisplayName by remember(reported.reportedBy) { mutableStateOf(reported.reportedBy) }

    LaunchedEffect(reported.userName) {
        FirebaseFirestore.getInstance().collection("users")
            .whereEqualTo("username", reported.userName).limit(1).get()
            .addOnSuccessListener { snap ->
                val d = snap.documents.firstOrNull()?.getString("displayName")
                    ?.takeIf { it.isNotBlank() } ?: reported.userName
                authorDisplayName = d
            }
    }

    LaunchedEffect(reported.reportedBy) {
        if (reported.reportedBy.isBlank()) return@LaunchedEffect
        FirebaseFirestore.getInstance().collection("users")
            .whereEqualTo("username", reported.reportedBy).limit(1).get()
            .addOnSuccessListener { snap ->
                val d = snap.documents.firstOrNull()?.getString("displayName")
                    ?.takeIf { it.isNotBlank() } ?: reported.reportedBy
                reporterDisplayName = d
            }
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            shape = RoundedCornerShape(20.dp), containerColor = AWhite,
            icon = {
                Box(Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(AGreen50), Alignment.Center) {
                    Icon(Icons.Default.CheckCircle, null, tint = AGreen900, modifier = Modifier.size(26.dp))
                }
            },
            title = {
                Text("Restore Comment?", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                    color = AOnSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF9FAFB))
                        .padding(12.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(authorDisplayName, fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold, color = AOnSurface)
                            Text(reported.text, fontSize = 12.sp, color = AMuted,
                                maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text("The reports will be cleared and the comment made visible again. The author will be notified.",
                        fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center)
                }
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showRestoreDialog = false; onRestore() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AGreen900, contentColor = Color.White)
                    ) { Text("Restore comment", fontWeight = FontWeight.Bold, color = Color.White) }
                    OutlinedButton(
                        onClick = { showRestoreDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Cancel", color = AMuted) }
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(20.dp), containerColor = AWhite,
            icon = {
                Box(Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(ARedLight), Alignment.Center) {
                    Icon(Icons.Default.DeleteForever, null, tint = ARedColor, modifier = Modifier.size(26.dp))
                }
            },
            title = {
                Text("Delete Comment?", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                    color = AOnSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF9FAFB))
                        .padding(12.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(authorDisplayName, fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold, color = AOnSurface)
                            Text(reported.text, fontSize = 12.sp, color = AMuted,
                                maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text("This permanently removes the comment and notifies the author it violated community guidelines.",
                        fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center)
                }
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showDeleteDialog = false; onDelete() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ARedColor, contentColor = Color.White)
                    ) { Text("Delete permanently", fontWeight = FontWeight.Bold, color = Color.White) }
                    OutlinedButton(
                        onClick = { showDeleteDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Cancel", color = AMuted) }
                }
            }
        )
    }

    if (showDismissDialog) {
        AlertDialog(
            onDismissRequest = { showDismissDialog = false },
            shape = RoundedCornerShape(20.dp), containerColor = AWhite,
            icon = {
                Box(Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(AAmber50), Alignment.Center) {
                    Icon(Icons.Default.Close, null, tint = AAmber500, modifier = Modifier.size(26.dp))
                }
            },
            title = {
                Text("Dismiss Reports?", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                    color = AOnSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Text("All reports for this comment will be cleared. The comment stays hidden until manually restored.",
                    fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center)
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showDismissDialog = false; onDismiss() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AAmber500, contentColor = Color.White)
                    ) { Text("Dismiss reports", fontWeight = FontWeight.Bold, color = Color.White) }
                    OutlinedButton(
                        onClick = { showDismissDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Cancel", color = AMuted) }
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Coloured top bar
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFFFFF7ED),
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (reported.reportCount >= 3) ARedLight else AAmber50)
                            .padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Flag, null,
                                    tint = if (reported.reportCount >= 3) ARedColor else AAmber500,
                                    modifier = Modifier.size(11.dp))
                                Text("${reported.reportCount} report${if (reported.reportCount != 1) "s" else ""}",
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    color = if (reported.reportCount >= 3) ARedColor else AAmber500)
                            }
                        }
                        if (reported.reportCount >= 3) {
                            Box(Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ARedColor)
                                .padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text("AUTO-HIDDEN", fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold, color = Color.White,
                                    letterSpacing = 0.5.sp)
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(formatAdminTime(reported.timestamp), fontSize = 11.sp, color = AMuted)
                        if (reporterDisplayName.isNotBlank()) {
                            Text(
                                "by $reporterDisplayName",
                                fontSize = 10.sp,
                                color = AMuted.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }

            Column(Modifier
                .fillMaxWidth()
                .padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Author row
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(AGreen900, AGreen700))),
                        Alignment.Center) {
                        Text(authorDisplayName.take(1).uppercase(), fontSize = 14.sp,
                            fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column {
                        Text(authorDisplayName, fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp, color = AOnSurface)
                        Text("Reported comment", fontSize = 11.sp, color = AMuted)
                    }
                }

                // Reason chips
                if (reported.reasons.isNotEmpty()) {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        reported.reasons.forEach { reason ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (reason.contains("Swearing") || reason.contains("Harassment") || reason.contains(
                                                "Attack"
                                            )
                                        )
                                            ARedLight else AAmber50
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    reason, fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (reason.contains("Swearing") || reason.contains("Harassment") || reason.contains("Attack"))
                                        ARedColor else AAmber500
                                )
                            }
                        }
                    }
                }

                // Comment text preview
                Box(Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF3F4F6))
                    .padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (reported.reportCount >= 3) ARedColor else AAmber500))
                        Text(reported.text, fontSize = 13.sp, color = Color(0xFF374151),
                            lineHeight = 19.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }

                HorizontalDivider(color = ADivider, thickness = 0.5.dp)

                // Action buttons
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Restore — false report
                    Button(
                        onClick = { showRestoreDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AGreen900, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Restore, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Restore", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    // Delete — confirmed violation
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ARedColor),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, ARedColor)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    // Dismiss — clear reports, leave hidden
                    OutlinedButton(
                        onClick = { showDismissDialog = true },
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AAmber500),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, AAmber500),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    }
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
    var reportedByDisplay by remember(report.reportedBy) { mutableStateOf(report.reportedBy) }
    LaunchedEffect(report.reportedBy) {
        FirebaseFirestore.getInstance().collection("users")
            .whereEqualTo("username", report.reportedBy).limit(1).get()
            .addOnSuccessListener { snap ->
                val d = snap.documents.firstOrNull()?.getString("displayName")
                    ?.takeIf { it.isNotBlank() } ?: report.reportedBy
                reportedByDisplay = d
            }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(20.dp), containerColor = AWhite,
            icon = { Box(Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(ARedLight), Alignment.Center) {
                Icon(Icons.Default.DeleteForever, null, tint = ARedColor, modifier = Modifier.size(26.dp)) } },
            title = { Text("Remove Photo?", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                color = AOnSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text  = { Text("This will permanently remove the photo from the alert.",
                fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center) },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showDeleteDialog = false; onDeletePhoto() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ARedColor, contentColor = Color.White)) {
                        Text("Remove photo", fontWeight = FontWeight.Bold, color = Color.White) }
                    OutlinedButton(onClick = { showDeleteDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("Cancel", color = AMuted) }
                }
            }
        )
    }

    if (showDismissDialog) {
        AlertDialog(
            onDismissRequest = { showDismissDialog = false },
            shape = RoundedCornerShape(20.dp), containerColor = AWhite,
            icon = { Box(Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(AGreen50), Alignment.Center) {
                Icon(Icons.Default.CheckCircle, null, tint = AGreen900, modifier = Modifier.size(26.dp)) } },
            title = { Text("Dismiss Reports?", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                color = AOnSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text  = { Text("The photo will remain on the alert. All reports will be cleared.",
                fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center) },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showDismissDialog = false; onDismissReport() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AGreen900, contentColor = Color.White)) {
                        Text("Dismiss reports", fontWeight = FontWeight.Bold, color = Color.White) }
                    OutlinedButton(onClick = { showDismissDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("Cancel", color = AMuted) }
                }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AWhite), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (report.reportCount >= 3) ARedLight else AAmber50)
                        .padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Flag, null,
                                tint = if (report.reportCount >= 3) ARedColor else AAmber500, modifier = Modifier.size(12.dp))
                            Text("${report.reportCount} report${if (report.reportCount != 1) "s" else ""}",
                                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = if (report.reportCount >= 3) ARedColor else AAmber500) } }
                    if (report.reportCount >= 3) {
                        Box(Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ARedColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("AUTO-HIDDEN", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                                color = Color.White, letterSpacing = 0.5.sp) } } }
                Text(formatAdminTime(report.timestamp), fontSize = 11.sp, color = AMuted)
            }
            Text("Reported by: $reportedByDisplay", fontSize = 12.sp, color = AMuted)
            if (report.photoUrl.isNotBlank()) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF3F4F6))) {
                    AsyncImage(model = report.photoUrl, contentDescription = "Reported photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (!imageRevealed) Modifier.blur(20.dp) else Modifier))
                    if (!imageRevealed) {
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable { imageRevealed = true }, contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.VisibilityOff, null, tint = Color.White, modifier = Modifier.size(24.dp))
                                Text("Tap to review", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold) } } }
                }
            }
            HorizontalDivider(color = ADivider, thickness = 0.5.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showDeleteDialog = true }, modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ARedColor, contentColor = Color.White)) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remove", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                OutlinedButton(onClick = { showDismissDialog = true }, modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
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
    var alertRiderDisplayName by remember(alert.riderName) { mutableStateOf(alert.riderName) }
    LaunchedEffect(alert.riderName) {
        FirebaseFirestore.getInstance().collection("users")
            .whereEqualTo("username", alert.riderName).limit(1).get()
            .addOnSuccessListener { snap ->
                val d = snap.documents.firstOrNull()?.getString("displayName")
                    ?.takeIf { it.isNotBlank() } ?: alert.riderName
                alertRiderDisplayName = d
            }
    }

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
            icon = { Box(Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(AGreen50), Alignment.Center) {
                Icon(Icons.Default.CheckCircle, null, tint = AGreen900, modifier = Modifier.size(26.dp)) } },
            title = { Text("Force Resolve?", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                color = AOnSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text  = { Text("This will close $alertRiderDisplayName's alert and notify them it was resolved by admin.",
                fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center) },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showResolveDialog = false; onForceResolve() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AGreen900, contentColor = Color.White)) {
                        Text("Force resolve", fontWeight = FontWeight.Bold, color = Color.White) }
                    OutlinedButton(onClick = { showResolveDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("Cancel", color = AMuted) }
                }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AWhite), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .background(severityBg, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(severityColor)
                            .padding(horizontal = 7.dp, vertical = 2.dp)) {
                            Text(alert.severity.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                                color = Color.White, letterSpacing = 0.8.sp) }
                        Text(alert.emergencyType, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = severityColor) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (alert.status == "responding") {
                            Box(Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1565C0))
                                .padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text("Responding", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White) } }
                        Text(formatAdminTime(alert.timestamp), fontSize = 11.sp, color = AMuted) } }
            }
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(severityBg), Alignment.Center) {
                        Text(alertRiderDisplayName.take(1).uppercase(), fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp, color = severityColor) }
                    Column {
                        Text(alertRiderDisplayName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AOnSurface)
                        if (!alert.responderName.isNullOrBlank()) {
                            Text("Responder: ${alert.responderName}", fontSize = 11.sp,
                                color = Color(0xFF1565C0), fontWeight = FontWeight.Medium) } } }
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.LocationOn, null, tint = AMuted,
                        modifier = Modifier
                            .size(13.dp)
                            .padding(top = 1.dp))
                    Text(alert.locationName, fontSize = 12.sp, color = AMuted,
                        lineHeight = 17.sp, modifier = Modifier.weight(1f)) }
                HorizontalDivider(color = ADivider, thickness = 0.5.dp)
                Button(onClick = { showResolveDialog = true }, modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
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
    var organizerDisplayName by remember(ride.organizer) { mutableStateOf(ride.organizer) }
    LaunchedEffect(ride.organizer) {
        FirebaseFirestore.getInstance().collection("users")
            .whereEqualTo("username", ride.organizer).limit(1).get()
            .addOnSuccessListener { snap ->
                val d = snap.documents.firstOrNull()?.getString("displayName")
                    ?.takeIf { it.isNotBlank() } ?: ride.organizer
                organizerDisplayName = d
            }
    }

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
                Box(Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(AAmber50), Alignment.Center) {
                    Icon(Icons.Default.Cancel, null, tint = AAmber500, modifier = Modifier.size(26.dp))
                }
            },
            title = { Text("Reject Ride?", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                color = AOnSurface, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF9FAFB))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(ride.title, fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold, color = AOnSurface,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("by $organizerDisplayName", fontSize = 12.sp, color = AMuted)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AAmber500, contentColor = Color.White)
                    ) { Text("Reject ride", fontWeight = FontWeight.Bold, color = Color.White) }
                    OutlinedButton(
                        onClick = { showRejectDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFFEEF2FF),
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
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
                    Box(Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(diffBg)
                        .padding(horizontal = 7.dp, vertical = 2.dp)) {
                        Text(ride.difficulty, fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold, color = diffColor)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title + organizer
                Text(ride.title, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, color = AOnSurface, lineHeight = 21.sp)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(AGreen900, AGreen700))),
                        contentAlignment = Alignment.Center) {
                        Text(organizerDisplayName.take(1).uppercase(),
                            fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text(organizerDisplayName, fontSize = 12.sp,
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
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
                        modifier  = Modifier
                            .weight(1f)
                            .height(40.dp),
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
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
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
private fun DashboardStat(value: String, label: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.65f),
            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DashboardStatDivider() {
    Box(Modifier
        .width(1.dp)
        .height(36.dp)
        .background(Color.White.copy(alpha = 0.15f)))
}

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
    Box(Modifier
        .width(1.dp)
        .height(36.dp)
        .background(Color.White.copy(alpha = 0.15f)))
}

@Composable
private fun AdminLoadingState() {
    Box(Modifier
        .fillMaxWidth()
        .padding(vertical = 48.dp), Alignment.Center) {
        CircularProgressIndicator(color = AGreen900, strokeWidth = 2.5.dp, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun AdminEmptyState(icon: ImageVector, title: String, message: String) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 48.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(AGreen50), Alignment.Center) {
            Icon(icon, null, tint = AGreen900, modifier = Modifier.size(28.dp)) }
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            color = AOnSurface, textAlign = TextAlign.Center)
        Text(message, fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center, lineHeight = 19.sp)
    }
}

@Composable
private fun AdminModerationLogCard(log: ModerationLog, onDismiss: () -> Unit = {}) {
    var userDisplayName by remember(log.userName) { mutableStateOf(log.userName) }

    LaunchedEffect(log.userName) {
        FirebaseFirestore.getInstance().collection("users")
            .whereEqualTo("username", log.userName).limit(1).get()
            .addOnSuccessListener { snap ->
                val d = snap.documents.firstOrNull()?.getString("displayName")
                    ?.takeIf { it.isNotBlank() } ?: log.userName
                userDisplayName = d
            }
    }

    val contextColor = when (log.context) {
        "comment"      -> Color(0xFF1976D2)
        "comment_edit" -> Color(0xFF7B1FA2)
        "post_edit"    -> Color(0xFF388E3C)
        else           -> AMuted
    }
    val contextLabel = when (log.context) {
        "comment"      -> "New Comment"
        "comment_edit" -> "Edited Comment"
        "post_edit"    -> "Post Edit"
        else           -> log.context
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = AWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Top bar
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFFF5F3FF),
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Shield, null,
                            tint = Color(0xFF7C3AED), modifier = Modifier.size(13.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(contextColor.copy(alpha = 0.12f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(contextLabel, fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold, color = contextColor)
                        }
                    }
                    Text(formatAdminTime(log.timestamp), fontSize = 11.sp, color = AMuted)
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // User row
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(AGreen900, AGreen700))),
                        Alignment.Center
                    ) {
                        Text(userDisplayName.take(1).uppercase(),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column {
                        Text(userDisplayName, fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp, color = AOnSurface)
                        Text("Profanity auto-censored", fontSize = 11.sp, color = Color(0xFF7C3AED))
                    }
                }

                // Before → After comparison
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Original
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ARedLight)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("ORIGINAL", fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ARedColor, letterSpacing = 0.8.sp)
                        Text(log.originalText, fontSize = 13.sp,
                            color = Color(0xFF7F0000), lineHeight = 18.sp,
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    // Arrow
                    Box(Modifier.align(Alignment.CenterHorizontally)) {
                        Icon(Icons.Default.ArrowDownward, null,
                            tint = Color(0xFF7C3AED), modifier = Modifier.size(16.dp))
                    }
                    // Censored
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AGreen50)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("CENSORED", fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AGreen900, letterSpacing = 0.8.sp)
                        Text(log.censoredText, fontSize = 13.sp,
                            color = AGreen900, lineHeight = 18.sp,
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }

                HorizontalDivider(color = ADivider, thickness = 0.5.dp)

                OutlinedButton(
                    onClick  = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = AGreen900),
                    border   = androidx.compose.foundation.BorderStroke(1.5.dp, AGreen900)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Mark as Reviewed", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AdminUserReportCard(
    report    : UserReport,
    onDismiss : () -> Unit,
    onWarn    : () -> Unit
) {
    var showWarnDialog    by remember { mutableStateOf(false) }
    var showDismissDialog by remember { mutableStateOf(false) }
    var reporterDisplay   by remember(report.reporterName) { mutableStateOf(report.reporterName) }
    var reportedDisplay   by remember(report.reportedName) { mutableStateOf(report.reportedName) }

    LaunchedEffect(report.reporterName) {
        FirebaseFirestore.getInstance().collection("users")
            .whereEqualTo("username", report.reporterName).limit(1).get()
            .addOnSuccessListener { snap ->
                val d = snap.documents.firstOrNull()?.getString("displayName")
                    ?.takeIf { it.isNotBlank() } ?: report.reporterName
                reporterDisplay = d
            }
    }
    LaunchedEffect(report.reportedName) {
        FirebaseFirestore.getInstance().collection("users")
            .whereEqualTo("username", report.reportedName).limit(1).get()
            .addOnSuccessListener { snap ->
                val d = snap.documents.firstOrNull()?.getString("displayName")
                    ?.takeIf { it.isNotBlank() } ?: report.reportedName
                reportedDisplay = d
            }
    }

    val roleColor = if (report.reportedRole == "rider") Color(0xFF1565C0) else Color(0xFF6A1B9A)
    val roleBg    = if (report.reportedRole == "rider") Color(0xFFE3F2FD) else Color(0xFFF3E5F5)

    if (showWarnDialog) {
        AlertDialog(
            onDismissRequest = { showWarnDialog = false },
            shape = RoundedCornerShape(20.dp), containerColor = AWhite,
            icon = {
                Box(Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(AAmber50), Alignment.Center) {
                    Icon(Icons.Default.Warning, null, tint = AAmber500, modifier = Modifier.size(26.dp))
                }
            },
            title = {
                Text("Send Warning?", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                    color = AOnSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Text(
                    "$reportedDisplay will receive a warning notification about their behavior and the report will be marked as reviewed.",
                    fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showWarnDialog = false; onWarn() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AAmber500, contentColor = Color.White)
                    ) { Text("Send warning", fontWeight = FontWeight.Bold, color = Color.White) }
                    OutlinedButton(
                        onClick = { showWarnDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Cancel", color = AMuted) }
                }
            }
        )
    }

    if (showDismissDialog) {
        AlertDialog(
            onDismissRequest = { showDismissDialog = false },
            shape = RoundedCornerShape(20.dp), containerColor = AWhite,
            icon = {
                Box(Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(AGreen50), Alignment.Center) {
                    Icon(Icons.Default.CheckCircle, null, tint = AGreen900, modifier = Modifier.size(26.dp))
                }
            },
            title = {
                Text("Dismiss Report?", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                    color = AOnSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Text(
                    "This report will be marked as reviewed and removed from the queue. No action will be taken against $reportedDisplay.",
                    fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showDismissDialog = false; onDismiss() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AGreen900, contentColor = Color.White)
                    ) { Text("Dismiss report", fontWeight = FontWeight.Bold, color = Color.White) }
                    OutlinedButton(
                        onClick = { showDismissDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
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
            // Top bar
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(roleBg, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Flag, null,
                            tint = roleColor, modifier = Modifier.size(13.dp))
                        Box(Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(roleColor.copy(alpha = 0.12f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)) {
                            Text(
                                report.reportedRole.replaceFirstChar { it.uppercase() } + " Reported",
                                fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = roleColor
                            )
                        }
                        Text("· ${report.emergencyType}", fontSize = 11.sp, color = roleColor.copy(alpha = 0.7f))
                    }
                    Text(formatAdminTime(report.timestamp), fontSize = 11.sp, color = AMuted)
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Reported user row
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(roleBg), Alignment.Center) {
                        Text(reportedDisplay.take(1).uppercase(),
                            fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = roleColor)
                    }
                    Column {
                        Text(reportedDisplay, fontWeight = FontWeight.Bold,
                            fontSize = 14.sp, color = AOnSurface)
                        Text("Reported by $reporterDisplay",
                            fontSize = 11.sp, color = AMuted)
                    }
                }

                // Reason chip
                Box(Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(ARedLight)
                    .padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Text(report.reason, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold, color = ARedColor)
                }

                // Optional comment
                if (report.comment.isNotBlank()) {
                    Box(Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF3F4F6))
                        .padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(ARedColor.copy(alpha = 0.4f)))
                            Text(report.comment, fontSize = 13.sp, color = Color(0xFF374151),
                                lineHeight = 19.sp, maxLines = 4,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                HorizontalDivider(color = ADivider, thickness = 0.5.dp)

                // Action buttons
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick  = { showWarnDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = AAmber500, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Warning, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Warn User", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    OutlinedButton(
                        onClick  = { showDismissDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = AGreen900),
                        border   = androidx.compose.foundation.BorderStroke(1.5.dp, AGreen900)
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Dismiss", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
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
@Composable
private fun AdminAuditCard(entry: AuditLogEntry) {
    val (actionColor, actionBg, actionIcon) = when {
        entry.action.startsWith("Approved")  -> Triple(Color(0xFF166534), Color(0xFFDCFCE7), Icons.Default.CheckCircle)
        entry.action.startsWith("Rejected")  -> Triple(Color(0xFF9A3412), Color(0xFFFFEDD5), Icons.Default.Cancel)
        entry.action.startsWith("Deleted")   -> Triple(ARedColor,          ARedLight,          Icons.Default.Delete)
        entry.action.startsWith("Resolved")  -> Triple(Color(0xFF1565C0), Color(0xFFE3F2FD), Icons.Default.CheckCircle)
        entry.action.startsWith("Restored")  -> Triple(AGreen900,          AGreen50,           Icons.Default.Restore)
        entry.action.startsWith("Dismissed") -> Triple(AAmber500,          AAmber50,           Icons.Default.Close)
        entry.action.startsWith("Removed")   -> Triple(ARedColor,          ARedLight,          Icons.Default.Delete)
        else                                 -> Triple(AMuted,              Color(0xFFF3F4F6),  Icons.Default.Info)
    }

    val targetTypeLabel = when (entry.targetType) {
        "post"    -> "Post"
        "ride"    -> "Ride"
        "comment" -> "Comment"
        "alert"   -> "Alert"
        "photo"   -> "Photo"
        else      -> entry.targetType
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = AWhite),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Action icon
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(actionBg),
                Alignment.Center
            ) {
                Icon(actionIcon, null, tint = actionColor, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                // Action + type
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        entry.action, fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp, color = actionColor
                    )
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFF3F4F6))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(targetTypeLabel, fontSize = 9.sp,
                            fontWeight = FontWeight.Bold, color = AMuted,
                            letterSpacing = 0.5.sp)
                    }
                }
                // Admin who did it
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.AdminPanelSettings, null,
                        tint = Color(0xFF0891B2), modifier = Modifier.size(11.dp))
                    Text(
                        entry.adminDisplayName.ifBlank { entry.adminUserName },
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0891B2)
                    )
                }
                // Target user
                if (entry.targetUser.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Person, null,
                            tint = AMuted, modifier = Modifier.size(11.dp))
                        Text("re: ${entry.targetUser}", fontSize = 11.sp, color = AMuted)
                    }
                }
                // Detail snippet
                if (entry.detail.isNotBlank()) {
                    Text(
                        entry.detail, fontSize = 11.sp, color = AMuted,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )
                }
            }
            Text(
                formatAdminTime(entry.timestamp),
                fontSize = 10.sp, color = AMuted
            )
        }
    }
}