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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.animation.AnimatedVisibility
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
    val reasons: List<String> = emptyList(),
    val source: String = "user"  // "user" = reported by users, "admin" = hidden by admin
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
private data class AdminUser(
    val id: String, val username: String, val displayName: String, val email: String,
    val role: String, val createdAt: Long, val photoUrl: String,
    val pendingDeletion: Boolean, val deletionScheduledAt: Long,
    val warningCount: Int = 0,
    val suspended: Boolean = false, val suspendedUntil: Long = 0L
)
private data class TrashItem(
    val id: String,
    val type: String,           // "post" or "comment"
    val originalId: String,
    val postId: String,         // empty for posts, parent postId for comments
    val userName: String,
    val content: String,        // description for posts, text for comments
    val imageUrl: String,       // only for posts
    val reason: String,
    val deletedBy: String,
    val deletedAt: Long,
    val expiresAt: Long
)
private object AdminSection {
    const val DASHBOARD = 0; const val POSTS = 1; const val ALERT_HISTORY = 2
    const val PRONE_AREAS = 3; const val USERS = 4; const val RIDES = 5
    const val REPORTS = 6; const val PROFANITY = 7; const val AUDIT = 8; const val TRASH = 9
    const val ANNOUNCEMENTS = 10
}

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
    val reportedComments    = remember { mutableStateListOf<ReportedComment>() }
    val allUsers = remember { mutableStateListOf<AdminUser>() }
    var isLoadingUsers by remember { mutableStateOf(true) }
    var userSearchQuery by remember { mutableStateOf("") }

    val proneAreas = remember { mutableStateListOf<ProneAreaStat>() }
    var isLoadingProneAreas by remember { mutableStateOf(true) }

    val alertHistory = remember { mutableStateListOf<AdminAlertRecord>() }
    var isLoadingAlertHistory by remember { mutableStateOf(true) }
    var expandedAlertHistoryId by remember { mutableStateOf<String?>(null) }

    // Dashboard state
    var totalUsers          by remember { mutableIntStateOf(0) }
    var totalPosts          by remember { mutableIntStateOf(0) }
    var totalAlerts         by remember { mutableIntStateOf(0) }
    var resolvedAlerts      by remember { mutableIntStateOf(0) }
    var dashUsersReady      by remember { mutableStateOf(false) }
    var dashPostsReady      by remember { mutableStateOf(false) }
    var dashTotalAlertsReady by remember { mutableStateOf(false) }
    var dashAlertsReady     by remember { mutableStateOf(false) }
    val isLoadingDashboard  by remember { derivedStateOf { !dashUsersReady || !dashPostsReady || !dashTotalAlertsReady || !dashAlertsReady } }

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
    var isLoadingReportedComments by remember { mutableStateOf(true) }
    var isLoadingModerationLogs   by remember { mutableStateOf(true) }
    val moderationLogs = remember { mutableStateListOf<ModerationLog>() }
    val auditLogs = remember { mutableStateListOf<AuditLogEntry>() }
    var isLoadingAuditLogs by remember { mutableStateOf(true) }
    val userReports = remember { mutableStateListOf<UserReport>() }
    var isLoadingUserReports by remember { mutableStateOf(true) }
    val trashItems = remember { mutableStateListOf<TrashItem>() }
    var isLoadingTrash by remember { mutableStateOf(true) }
    var selectedTrashChip by remember { mutableStateOf("Posts") }
    var adminDisplayName by remember { mutableStateOf(adminUserName) }

    val announcements = remember { mutableStateListOf<Announcement>() }
    var isLoadingAnnouncements by remember { mutableStateOf(true) }
    var showCreateAnnouncement by remember { mutableStateOf(false) }

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
        db.collection("users").addSnapshotListener { snap, _ ->
            if (snap == null) { isLoadingUsers = false; return@addSnapshotListener }
            allUsers.clear()
            for (doc in snap.documents) {
                allUsers.add(AdminUser(
                    id = doc.id,
                    username = doc.getString("username") ?: "",
                    displayName = doc.getString("displayName")?.takeIf { it.isNotBlank() }
                        ?: doc.getString("username") ?: "",
                    email = doc.getString("email") ?: "",
                    role = doc.getString("role") ?: "rider",
                    createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
                    photoUrl = doc.getString("photoUrl") ?: "",
                    pendingDeletion = doc.getBoolean("pendingDeletion") ?: false,
                    deletionScheduledAt = doc.getTimestamp("deletionScheduledAt")?.toDate()?.time ?: 0L,
                    warningCount = (doc.getLong("warningCount") ?: 0L).toInt(),
                    suspended = doc.getBoolean("suspended") ?: false,
                    suspendedUntil = doc.getTimestamp("suspendedUntil")?.toDate()?.time ?: 0L
                ))
            }
            allUsers.sortByDescending { it.createdAt }
            isLoadingUsers = false
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
                    if (selectedSection != AdminSection.REPORTS) {
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
                if (selectedSection != AdminSection.REPORTS) {
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
        db.collection("reportedImages")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val grouped = snap.documents
                    .mapNotNull { doc -> doc.getString("alertId") }
                    .distinct()
                if (selectedSection != AdminSection.REPORTS) {
                    reportedImages.clear()
                    grouped.forEach { alertId ->
                        reportedImages.add(ReportedImage(
                            reportId = "", alertId = alertId, photoUrl = "",
                            reportedBy = "", timestamp = 0L
                        ))
                    }
                }
                isLoadingReports = false
            }
    }

    LaunchedEffect(Unit) {
        db.collection("moderationLogs")
            .whereEqualTo("reviewed", false)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                if (selectedSection != AdminSection.PROFANITY) {
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
                if (selectedSection != AdminSection.REPORTS) {
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

    LaunchedEffect(Unit) {
        db.collection("adminTrash")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                if (selectedSection != AdminSection.TRASH) {
                    trashItems.clear()
                    for (doc in snap.documents) {
                        trashItems.add(TrashItem(
                            id         = doc.id,
                            type       = doc.getString("type")       ?: "",
                            originalId = doc.getString("originalId") ?: "",
                            postId     = doc.getString("postId")     ?: "",
                            userName   = doc.getString("userName")   ?: "",
                            content    = doc.getString("content")    ?: "",
                            imageUrl   = doc.getString("imageUrl")   ?: "",
                            reason     = doc.getString("reason")     ?: "",
                            deletedBy  = doc.getString("deletedBy")  ?: "",
                            deletedAt  = doc.getLong("deletedAt")    ?: 0L,
                            expiresAt  = doc.getLong("expiresAt")    ?: 0L
                        ))
                    }
                }
                isLoadingTrash = false
            }
    }

    LaunchedEffect(Unit) {
        db.collection("moderationAuditLog")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                if (selectedSection != AdminSection.AUDIT) {
                    val now = System.currentTimeMillis()
                    auditLogs.clear()
                    for (doc in snap.documents) {
                        val expiresAt = doc.getLong("expiresAt") ?: 0L
                        if (expiresAt > 0L && now > expiresAt) {
                            db.collection("moderationAuditLog").document(doc.id).delete()
                            continue
                        }
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
                }
                isLoadingAuditLogs = false
            }
    }

    LaunchedEffect(Unit) {
        db.collection("announcements")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { isLoadingAnnouncements = false; return@addSnapshotListener }
                announcements.clear()
                for (doc in snap.documents) {
                    announcements.add(Announcement(
                        id             = doc.id,
                        title          = doc.getString("title")   ?: "",
                        message        = doc.getString("message") ?: "",
                        severity       = doc.getString("severity") ?: "info",
                        active         = doc.getBoolean("active") ?: true,
                        startAt        = doc.getLong("startAt")   ?: 0L,
                        expiresAt      = doc.getLong("expiresAt"),
                        createdBy      = doc.getString("createdBy") ?: "",
                        createdByName  = doc.getString("createdByName") ?: "",
                        createdAt      = doc.getLong("createdAt") ?: 0L,
                        dismissedBy    = (doc.get("dismissedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    ))
                }
                isLoadingAnnouncements = false
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
        if (selectedSection != AdminSection.RIDES) return@LaunchedEffect
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

    // Store report metadata alongside the posts
    val reportedPostMeta = remember { mutableStateMapOf<String, Pair<List<String>, List<String>>>() }
    // Key: postId → Pair(reasons, reportedByList)

    LaunchedEffect(selectedSection) {
        if (selectedSection != AdminSection.REPORTS) return@LaunchedEffect
        db.collection("reportedPosts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { isLoadingReportedPosts = false; return@addSnapshotListener }
                val grouped = snap.documents
                    .mapNotNull { doc ->
                        val postId     = doc.getString("postId")     ?: return@mapNotNull null
                        val reportedBy = doc.getString("reportedBy") ?: ""
                        val reason     = doc.getString("reason")     ?: ""
                        postId to Pair(reason, reportedBy)
                    }
                    .groupBy { it.first }

                // Build meta map: postId → (reasons, reporters)
                reportedPostMeta.clear()
                grouped.forEach { (postId, entries) ->
                    val reasons   = entries.map { it.second.first }.filter { it.isNotBlank() }.distinct()
                    val reporters = entries.map { it.second.second }.filter { it.isNotBlank() }.distinct()
                    reportedPostMeta[postId] = Pair(reasons, reporters)
                }

                val postIds = grouped.keys.toList()
                if (postIds.isEmpty()) { isLoadingReportedPosts = false; return@addSnapshotListener }

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
        if (selectedSection != AdminSection.REPORTS) return@LaunchedEffect
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
        if (selectedSection != AdminSection.REPORTS) return@LaunchedEffect
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
                        val source     = doc.getString("source")     ?: "user"
                        commentId to ReportedComment(
                            commentId   = commentId,
                            postId      = postId,
                            userName    = userName,
                            text        = text,
                            reportCount = 1,
                            timestamp   = timestamp,
                            reportedBy  = reportedBy,
                            reasons     = if (reason.isNotBlank()) listOf(reason) else emptyList(),
                            source      = source
                        )
                    }
                    .groupBy { it.first }
                    .map { (_, reports) ->
                        val latest = reports.maxByOrNull { it.second.timestamp }!!.second
                        val allReasons = reports
                            .flatMap { it.second.reasons }
                            .filter { it.isNotBlank() }
                            .distinct()
                        // For admin-hidden comments, source is preserved from the single record
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

    // Dashboard — total alerts (all-time, any status)
    LaunchedEffect(Unit) {
        db.collection("alerts")
            .addSnapshotListener { snap, _ ->
                totalAlerts = snap?.size() ?: 0
                dashTotalAlertsReady = true
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
        db.collection("moderationAuditLog")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                recentActivity.clear()
                snap.documents.forEach { doc ->
                    val action     = doc.getString("action")           ?: return@forEach
                    val targetType = doc.getString("targetType")       ?: ""
                    val targetUser = doc.getString("targetUser")       ?: "Unknown"
                    val adminName  = doc.getString("adminDisplayName") ?: doc.getString("adminUserName") ?: "Admin"
                    val timestamp  = doc.getLong("timestamp")          ?: 0L

                    val type = when {
                        action.contains("Approved", ignoreCase = true)  -> "post"
                        action.contains("Rejected", ignoreCase = true)  -> "report"
                        action.contains("Deleted", ignoreCase = true) ||
                                action.contains("Trashed", ignoreCase = true) ||
                                action.contains("Removed", ignoreCase = true)   -> "report"
                        action.contains("Resolved", ignoreCase = true)  -> "alert"
                        action.contains("Restored", ignoreCase = true)  -> "post"
                        action.contains("Warned", ignoreCase = true)    -> "report"
                        else                                             -> "post"
                    }

                    recentActivity.add(ActivityItem(
                        id       = doc.id,
                        type     = type,
                        title    = action,
                        subtitle = "by $adminName · re: $targetUser",
                        timestamp = timestamp
                    ))
                }
            }
    }

    LaunchedEffect(selectedSection) {
        if (selectedSection != AdminSection.PROFANITY) return@LaunchedEffect
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
        if (selectedSection != AdminSection.REPORTS) return@LaunchedEffect
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
        if (selectedSection != AdminSection.AUDIT) return@LaunchedEffect
        db.collection("moderationAuditLog")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { isLoadingAuditLogs = false; return@addSnapshotListener }
                val now = System.currentTimeMillis()
                auditLogs.clear()
                for (doc in snap.documents) {
                    val expiresAt = doc.getLong("expiresAt") ?: 0L
                    if (expiresAt > 0L && now > expiresAt) {
                        // Auto-purge expired audit entries (90-day retention)
                        db.collection("moderationAuditLog").document(doc.id).delete()
                        continue
                    }
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
        if (selectedSection != AdminSection.TRASH) return@LaunchedEffect
        db.collection("adminTrash")
            .orderBy("deletedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { isLoadingTrash = false; return@addSnapshotListener }
                val now = System.currentTimeMillis()
                trashItems.clear()
                for (doc in snap.documents) {
                    val expiresAt = doc.getLong("expiresAt") ?: 0L
                    if (expiresAt > 0L && now > expiresAt) {
                        // Auto-purge expired items
                        db.collection("adminTrash").document(doc.id).delete()
                        continue
                    }
                    trashItems.add(TrashItem(
                        id         = doc.id,
                        type       = doc.getString("type")       ?: "",
                        originalId = doc.getString("originalId") ?: "",
                        postId     = doc.getString("postId")     ?: "",
                        userName   = doc.getString("userName")   ?: "",
                        content    = doc.getString("content")    ?: "",
                        imageUrl   = doc.getString("imageUrl")   ?: "",
                        reason     = doc.getString("reason")     ?: "",
                        deletedBy  = doc.getString("deletedBy")  ?: "",
                        deletedAt  = doc.getLong("deletedAt")    ?: 0L,
                        expiresAt  = doc.getLong("expiresAt")    ?: 0L
                    ))
                }
                isLoadingTrash = false
            }
    }

    LaunchedEffect(selectedSection) {
        if (selectedSection != AdminSection.ALERT_HISTORY) return@LaunchedEffect
        isLoadingAlertHistory = true
        db.collection("alerts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                val records = snap.documents.map { doc ->
                    val riderName = doc.getString("riderName") ?: ""
                    AdminAlertRecord(
                        id                    = doc.id,
                        riderName             = riderName,
                        riderDisplayName      = doc.getString("riderDisplayName") ?: "",
                        emergencyType         = doc.getString("emergencyType") ?: "Alert",
                        locationName          = doc.getString("locationName") ?: "",
                        latitude              = doc.getDouble("latitude") ?: 0.0,
                        longitude             = doc.getDouble("longitude") ?: 0.0,
                        timestamp             = doc.getLong("timestamp") ?: 0L,
                        status                = doc.getString("status") ?: "active",
                        responderName         = doc.getString("responderName") ?: "",
                        responderDisplayName  = doc.getString("responderDisplayName") ?: "",
                        additionalDetails     = doc.getString("additionalDetails") ?: "",
                        contactNumber         = doc.getString("contactNumber") ?: "",
                        photoUrl              = doc.getString("photoUrl") ?: "",
                        ratingGiven           = doc.getBoolean("ratingGiven") ?: false,
                        ratingValue           = doc.getLong("ratingValue")?.toInt(),
                        ratingReview          = doc.getString("ratingReview") ?: ""
                    )
                }
                alertHistory.clear()
                alertHistory.addAll(records)
                isLoadingAlertHistory = false
            }
            .addOnFailureListener { isLoadingAlertHistory = false }
    }

    LaunchedEffect(selectedSection) {
        if (selectedSection != AdminSection.PRONE_AREAS) return@LaunchedEffect
        isLoadingProneAreas = true
        db.collection("alerts").get()
            .addOnSuccessListener { snap ->
                val counts = snap.documents
                    .mapNotNull { it.getString("locationName")?.trim()?.takeIf { loc -> loc.isNotBlank() } }
                    .groupingBy { it }
                    .eachCount()
                val total = counts.values.sum()
                val ranked = counts.entries
                    .sortedByDescending { it.value }
                    .map { (loc, count) ->
                        ProneAreaStat(
                            location   = loc,
                            count      = count,
                            percentage = if (total > 0) ((count * 100) / total) else 0
                        )
                    }
                proneAreas.clear()
                proneAreas.addAll(ranked)
                isLoadingProneAreas = false
            }
            .addOnFailureListener { isLoadingProneAreas = false }
    }

    fun logAudit(action: String, targetType: String, targetUser: String, detail: String) {
        logModerationAudit(db, adminUserName, adminDisplayName, action, targetType, targetUser, detail)
    }
    fun moveToTrash(
        type: String,
        originalId: String,
        postId: String,
        userName: String,
        content: String,
        imageUrl: String,
        reason: String
    ) {
        val now = System.currentTimeMillis()
        db.collection("adminTrash").add(hashMapOf(
            "type"       to type,
            "originalId" to originalId,
            "postId"     to postId,
            "userName"   to userName,
            "content"    to content,
            "imageUrl"   to imageUrl,
            "reason"     to reason,
            "deletedBy"  to adminUserName,
            "deletedAt"  to now,
            "expiresAt"  to (now + 30L * 24 * 60 * 60 * 1000)
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
                        sendUserNotification(
                            db, post.userName,
                            "✅ Hey $displayName, your post has been approved and is now live!",
                            "accepted",
                            displayUserName = "Admin"
                        )
                    }
                    .addOnFailureListener {
                        sendUserNotification(
                            db, post.userName,
                            "✅ Your post has been approved and is now live!",
                            "accepted",
                            displayUserName = "Admin"
                        )
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
                        sendUserNotification(
                            db, post.userName,
                            "❌ Sorry $displayName, your post was not approved. Please follow community guidelines.",
                            "rejected",
                            displayUserName = "Admin"
                        )
                    }
                    .addOnFailureListener {
                        sendUserNotification(
                            db, post.userName,
                            "❌ Your post was not approved. Please follow community guidelines.",
                            "rejected",
                            displayUserName = "Admin"
                        )
                    }
                toast("Post rejected.")
                logAudit("Rejected post", "post", post.userName, post.description.take(80))
            }.addOnFailureListener {
                pendingPosts.add(post)  // restore on failure
                toast("Failed to reject post.", false)
            }
    }

    fun deletePost(post: AdminPost) {
        db.collection("posts").document(post.id).delete()
            .addOnSuccessListener {
                toast("Post deleted.")
                logAudit("Deleted post", "post", post.userName, post.description.take(80))
            }.addOnFailureListener { toast("Failed to delete.", false) }
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
                        sendUserNotification(
                            db, ride.organizer,
                            "✅ Hey $displayName, your ride \"${ride.title}\" is approved and now visible!",
                            "ride",
                            extraFields = mapOf("eventId" to ride.id),
                            displayUserName = "Admin"
                        )
                    }
                    .addOnFailureListener {
                        sendUserNotification(
                            db, ride.organizer,
                            "✅ Your ride \"${ride.title}\" is approved and now visible!",
                            "ride",
                            extraFields = mapOf("eventId" to ride.id),
                            displayUserName = "Admin"
                        )
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
                        sendUserNotification(
                            db, ride.organizer,
                            "❌ Sorry $displayName, your ride \"${ride.title}\" was not approved. Please review and resubmit.",
                            "ride",
                            extraFields = mapOf("eventId" to ride.id),
                            displayUserName = "Admin"
                        )
                    }
                    .addOnFailureListener {
                        sendUserNotification(
                            db, ride.organizer,
                            "❌ Your ride \"${ride.title}\" was not approved. Please review and resubmit.",
                            "ride",
                            extraFields = mapOf("eventId" to ride.id),
                            displayUserName = "Admin"
                        )
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
        NavSection("Dashboard",      Icons.Default.Dashboard,      0,                                                                                    AGreen700),
        NavSection("Posts",          Icons.Default.Article,        pendingPosts.size,                                                                    AAmber500),
        NavSection("Alerts",  Icons.Default.History,        0,                                                                                    Color(0xFF1565C0)),
        NavSection("Most Prone Areas",    Icons.Default.LocationOn,     0,                                                                                    ARedColor),
        NavSection("Users Management",          Icons.Default.People,         allUsers.count { it.pendingDeletion },                                                Color(0xFF0288D1)),
        NavSection("Ride Events",          Icons.Default.DirectionsBike, pendingRides.size,                                                                    Color(0xFF1976D2)),
        NavSection("Reports",        Icons.Default.Flag,           reportedPosts.size + reportedComments.size + reportedImages.size + userReports.size,  ARedColor),
        NavSection("Profanity Logs", Icons.Default.Shield,         moderationLogs.size,                                                                   Color(0xFF7C3AED)),
        NavSection("Audit Log",      Icons.Default.ManageAccounts, auditLogs.size,                                                                        Color(0xFF0891B2)),
        NavSection("Trash Bin",      Icons.Default.DeleteSweep,    trashItems.size,                                                                       Color(0xFF6B7280)),
        NavSection("Announcements",  Icons.Default.Campaign,       announcements.count { it.active && (it.expiresAt == null || it.expiresAt > System.currentTimeMillis()) }, Color(0xFF0D7050))
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
            floatingActionButton = {
                if (selectedSection == AdminSection.ANNOUNCEMENTS) {
                    FloatingActionButton(
                        onClick = { showCreateAnnouncement = true },
                        containerColor = AGreen900,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier
                            .padding(bottom = paddingValues.calculateBottomPadding())
                            .size(56.dp)
                    ) {
                        Icon(Icons.Default.Campaign, "New Announcement", modifier = Modifier.size(24.dp))
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.End,
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
                                    AdminSection.DASHBOARD     -> "Overview"
                                    AdminSection.POSTS         -> "${pendingPosts.size} pending"
                                    AdminSection.ALERT_HISTORY -> "${alertHistory.size} alert${if (alertHistory.size != 1) "s" else ""}"
                                    AdminSection.PRONE_AREAS   -> "${proneAreas.size} location${if (proneAreas.size != 1) "s" else ""}"
                                    AdminSection.USERS       -> "${allUsers.size} user${if (allUsers.size != 1) "s" else ""}"
                                    AdminSection.RIDES     -> "${pendingRides.size} pending"
                                    AdminSection.REPORTS   -> "${reportedPosts.size + reportedComments.size + reportedImages.size + userReports.size} reported"
                                    AdminSection.PROFANITY -> "${moderationLogs.size} entries"
                                    AdminSection.AUDIT     -> "${auditLogs.size} entries"
                                    AdminSection.TRASH     -> "${trashItems.size} item${if (trashItems.size != 1) "s" else ""}"
                                    AdminSection.ANNOUNCEMENTS -> "${announcements.size} total"
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
                        AdminSection.DASHBOARD -> {
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
                                                DashboardStat("$totalAlerts",  "Alerts",   Icons.Default.Warning,      Color(0xFFEF9A9A))
                                                DashboardStatDivider()
                                                DashboardStat("$resolvedAlerts", "Resolved", Icons.Default.CheckCircle, Color(0xFF81C784))
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
                                        Triple("Posts",    pendingPosts.size, 1),
                                        Triple("Rides", pendingRides.size, AdminSection.RIDES),
                                        Triple("Reports",  reportedPosts.size + reportedComments.size + reportedImages.size + userReports.size, AdminSection.REPORTS)
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
                        AdminSection.POSTS -> {
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
                        AdminSection.ALERT_HISTORY -> {
                            item {
                                AdminAlertHistorySection(
                                    alerts     = alertHistory,
                                    isLoading  = isLoadingAlertHistory,
                                    expandedId = expandedAlertHistoryId,
                                    onToggle   = { id -> expandedAlertHistoryId = if (expandedAlertHistoryId == id) null else id }
                                )
                            }
                        }
                        AdminSection.PRONE_AREAS -> {
                            item {
                                AdminProneAreasSection(areas = proneAreas, isLoading = isLoadingProneAreas)
                            }
                        }
                        AdminSection.USERS -> {
                            item {
                                OutlinedTextField(
                                    value = userSearchQuery, onValueChange = { userSearchQuery = it },
                                    placeholder = { Text("Search by name or email", fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.Search, null, tint = AMuted, modifier = Modifier.size(18.dp)) },
                                    trailingIcon = {
                                        if (userSearchQuery.isNotBlank()) {
                                            IconButton(onClick = { userSearchQuery = "" }) {
                                                Icon(Icons.Default.Close, null, tint = AMuted, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    singleLine = true, shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AGreen900, unfocusedBorderColor = ADivider,
                                        focusedContainerColor = AWhite, unfocusedContainerColor = AWhite
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            val filteredUsers = allUsers.filter {
                                userSearchQuery.isBlank() ||
                                        it.displayName.contains(userSearchQuery, ignoreCase = true) ||
                                        it.username.contains(userSearchQuery, ignoreCase = true) ||
                                        it.email.contains(userSearchQuery, ignoreCase = true)
                            }
                            if (isLoadingUsers) {
                                item { AdminLoadingState() }
                            } else if (filteredUsers.isEmpty()) {
                                item {
                                    AdminEmptyState(Icons.Default.PersonSearch,
                                        if (userSearchQuery.isBlank()) "No users yet" else "No matches",
                                        if (userSearchQuery.isBlank()) "Registered users will appear here." else "Try a different name or email.")
                                }
                            } else {
                                item {
                                    Text("${filteredUsers.size} user${if (filteredUsers.size != 1) "s" else ""}",
                                        fontSize = 12.sp, color = AMuted,
                                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                                }
                                items(filteredUsers, key = { it.id }) { user ->
                                    AdminUserCard(
                                        user = user,
                                        onSave = { newDisplayName ->
                                            db.collection("users").document(user.id)
                                                .update("displayName", newDisplayName)
                                                .addOnSuccessListener {
                                                    val idx = allUsers.indexOfFirst { it.id == user.id }
                                                    if (idx != -1) allUsers[idx] = allUsers[idx].copy(displayName = newDisplayName)
                                                    toast("User updated.")
                                                    logAudit("Edited user", "user", user.username, "displayName updated")
                                                }.addOnFailureListener { toast("Failed to update user.", false) }
                                        },
                                        onRoleChange = { newRole ->
                                            db.collection("users").document(user.id)
                                                .update("role", newRole)
                                                .addOnSuccessListener {
                                                    val idx = allUsers.indexOfFirst { it.id == user.id }
                                                    if (idx != -1) allUsers[idx] = allUsers[idx].copy(role = newRole)
                                                    toast("Role changed: ${user.role} → $newRole")
                                                    logAudit("Changed role: ${user.role} → $newRole", "user", user.username, "by $adminUserName")
                                                }.addOnFailureListener { toast("Failed to change role.", false) }
                                        },
                                        onScheduleDelete = {
                                            val scheduledFor = com.google.firebase.Timestamp(
                                                java.util.Date(System.currentTimeMillis() + 14L * 24 * 60 * 60 * 1000))
                                            db.collection("users").document(user.id)
                                                .set(mapOf("pendingDeletion" to true, "deletionScheduledAt" to scheduledFor),
                                                    com.google.firebase.firestore.SetOptions.merge())
                                                .addOnSuccessListener {
                                                    val idx = allUsers.indexOfFirst { it.id == user.id }
                                                    if (idx != -1) allUsers[idx] = allUsers[idx].copy(
                                                        pendingDeletion = true, deletionScheduledAt = scheduledFor.toDate().time)
                                                    db.collection("notifications").add(hashMapOf(
                                                        "userName" to user.username,
                                                        "message" to "⚠️ Your account is scheduled for deletion in 14 days by an admin. Log in before then to cancel.",
                                                        "type" to "moderation",
                                                        "timestamp" to System.currentTimeMillis(), "read" to false))
                                                    toast("Account scheduled for deletion in 14 days.")
                                                    logAudit("Scheduled user deletion", "user", user.username, "14-day grace period")
                                                }.addOnFailureListener { toast("Failed to schedule deletion.", false) }
                                        },
                                        onCancelDelete = {
                                            db.collection("users").document(user.id)
                                                .set(mapOf("pendingDeletion" to false, "deletionScheduledAt" to null, "warningCount" to 0),
                                                    com.google.firebase.firestore.SetOptions.merge())
                                                .addOnSuccessListener {
                                                    val idx = allUsers.indexOfFirst { it.id == user.id }
                                                    if (idx != -1) allUsers[idx] = allUsers[idx].copy(pendingDeletion = false, deletionScheduledAt = 0L, warningCount = 0)
                                                    toast("Deletion canceled. Warning count reset.")
                                                    logAudit("Canceled user deletion", "user", user.username, "restored by admin, warnings reset")
                                                }.addOnFailureListener { toast("Failed to cancel deletion.", false) }
                                        },
                                        onLiftSuspension = {
                                            db.collection("users").document(user.id)
                                                .set(mapOf("suspended" to false, "suspendedUntil" to null, "warningCount" to 0),
                                                    com.google.firebase.firestore.SetOptions.merge())
                                                .addOnSuccessListener {
                                                    val idx = allUsers.indexOfFirst { it.id == user.id }
                                                    if (idx != -1) allUsers[idx] = allUsers[idx].copy(suspended = false, suspendedUntil = 0L, warningCount = 0)
                                                    toast("Suspension lifted. Warning count reset.")
                                                    logAudit("Lifted suspension", "user", user.username, "restored by admin, warnings reset")
                                                }.addOnFailureListener { toast("Failed to lift suspension.", false) }
                                        }
                                    )
                                }
                            }
                        }
                        AdminSection.RIDES -> {
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
                        AdminSection.REPORTS -> {
                            val isLoadingReportsTab = isLoadingReportedPosts || isLoadingReportedComments || isLoadingReports || isLoadingUserReports
                            item {
                                // ── Chip toggle ───────────────────────────────
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(bottom = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        "Posts"    to reportedPosts.size,
                                        "Comments" to reportedComments.size,
                                        "Photos"   to reportedImages.size,
                                        "Users"    to userReports.size
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

                            if (isLoadingReportsTab) {
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
                                        val meta     = reportedPostMeta[post.id]
                                        val reasons  = meta?.first  ?: emptyList()
                                        val reporters = meta?.second ?: emptyList()
                                        AdminReportedPostCard(
                                            post          = post,
                                            reportReasons = reasons,
                                            reportedBy    = reporters,
                                            reportCount   = reporters.size.coerceAtLeast(1),
                                            onRemove = {
                                                reportedPosts.remove(post)
                                                val reasonSummary = reasons.joinToString(", ")
                                                    .ifBlank { "violating community guidelines" }
                                                // Move to trash instead of hard delete
                                                moveToTrash(
                                                    type       = "post",
                                                    originalId = post.id,
                                                    postId     = "",
                                                    userName   = post.userName,
                                                    content    = post.description,
                                                    imageUrl   = post.imageUrl,
                                                    reason     = reasonSummary
                                                )
                                                db.collection("posts").document(post.id)
                                                    .update("status", "trashed")
                                                    .addOnSuccessListener {
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
                                                                    "message"   to "❌ Your post was removed by an admin. Reason: $reasonSummary",
                                                                    "type"      to "moderation",
                                                                    "timestamp" to System.currentTimeMillis(),
                                                                    "read"      to false
                                                                ))
                                                            }
                                                            .addOnFailureListener {
                                                                db.collection("notifications").add(hashMapOf(
                                                                    "userName"  to post.userName,
                                                                    "message"   to "❌ Your post was removed by an admin for violating community guidelines.",
                                                                    "type"      to "moderation",
                                                                    "timestamp" to System.currentTimeMillis(),
                                                                    "read"      to false
                                                                ))
                                                            }
                                                        toast("Post moved to trash bin.")
                                                        logAudit("Trashed reported post", "post", post.userName, post.description.take(80))
                                                    }
                                                    .addOnFailureListener {
                                                        reportedPosts.add(post)
                                                        toast("Failed to remove post.", false)
                                                    }
                                            },
                                            onDismiss = {
                                                reportedPosts.remove(post)
                                                db.collection("reportedPosts")
                                                    .whereEqualTo("postId", post.id)
                                                    .get()
                                                    .addOnSuccessListener { snap ->
                                                        snap.documents.forEach { it.reference.delete() }
                                                        db.collection("users")
                                                            .whereEqualTo("username", post.userName)
                                                            .limit(1).get()
                                                            .addOnSuccessListener { userSnap ->
                                                                val displayName = userSnap.documents.firstOrNull()
                                                                    ?.getString("displayName")
                                                                    ?.takeIf { it.isNotBlank() } ?: post.userName
                                                                db.collection("notifications").add(hashMapOf(
                                                                    "userName"  to post.userName,
                                                                    "message"   to "✅ Hey $displayName, a report on your post was reviewed and dismissed. Your post remains visible.",
                                                                    "type"      to "moderation_dismissed",
                                                                    "timestamp" to System.currentTimeMillis(),
                                                                    "read"      to false
                                                                ))
                                                            }
                                                        toast("Report dismissed.")
                                                        logAudit("Dismissed post report", "post", post.userName, post.description.take(80))
                                                    }
                                                    .addOnFailureListener {
                                                        reportedPosts.add(post)
                                                        toast("Failed to dismiss.", false)
                                                    }
                                            }
                                        )
                                    }
                                }
                            } else if (selectedReportChip == "Comments") {
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
                                        if (reported.source == "admin") {
                                            AdminHiddenCommentCard(
                                                reported  = reported,
                                                onRestore = {
                                                    reportedComments.remove(reported)
                                                    db.collection("posts").document(reported.postId)
                                                        .collection("comments").document(reported.commentId)
                                                        .update("status", "visible")
                                                        .addOnSuccessListener {
                                                            db.collection("posts").document(reported.postId)
                                                                .update("comments", com.google.firebase.firestore.FieldValue.increment(1))
                                                            db.collection("reportedComments")
                                                                .whereEqualTo("commentId", reported.commentId)
                                                                .get()
                                                                .addOnSuccessListener { snap ->
                                                                    snap.documents.forEach { it.reference.delete() }
                                                                }
                                                            db.collection("notifications").add(hashMapOf(
                                                                "userName"  to reported.userName,
                                                                "message"   to "✅ Your comment has been restored by an admin.",
                                                                "type"      to "moderation_restored",
                                                                "timestamp" to System.currentTimeMillis(),
                                                                "read"      to false
                                                            ))
                                                            toast("Comment restored.")
                                                            logAudit("Restored admin-hidden comment", "comment", reported.userName, reported.text.take(80))
                                                        }
                                                        .addOnFailureListener {
                                                            reportedComments.add(reported)
                                                            toast("Failed to restore.", false)
                                                        }
                                                },
                                                onDelete  = {
                                                    reportedComments.remove(reported)
                                                    val reasonSummary = reported.reasons.joinToString(", ")
                                                        .ifBlank { "violating community guidelines" }
                                                    moveToTrash(
                                                        type       = "comment",
                                                        originalId = reported.commentId,
                                                        postId     = reported.postId,
                                                        userName   = reported.userName,
                                                        content    = reported.text,
                                                        imageUrl   = "",
                                                        reason     = reasonSummary
                                                    )
                                                    db.collection("posts").document(reported.postId)
                                                        .collection("comments").document(reported.commentId)
                                                        .update("status", "trashed")
                                                        .addOnSuccessListener {
                                                            db.collection("reportedComments")
                                                                .whereEqualTo("commentId", reported.commentId)
                                                                .get()
                                                                .addOnSuccessListener { snap ->
                                                                    snap.documents.forEach { it.reference.delete() }
                                                                }
                                                            db.collection("notifications").add(hashMapOf(
                                                                "userName"  to reported.userName,
                                                                "message"   to "❌ Your comment was removed by an admin. Reason: $reasonSummary",
                                                                "type"      to "rejected",
                                                                "timestamp" to System.currentTimeMillis(),
                                                                "read"      to false
                                                            ))
                                                            toast("Comment moved to trash.")
                                                            logAudit("Trashed admin-hidden comment", "comment", reported.userName, reported.text.take(80))
                                                        }
                                                        .addOnFailureListener {
                                                            reportedComments.add(reported)
                                                            toast("Failed to delete.", false)
                                                        }
                                                }
                                            )
                                        } else {
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
                                                    val reasonSummary = reported.reasons.joinToString(", ")
                                                        .ifBlank { "violating community guidelines" }
                                                    moveToTrash(
                                                        type       = "comment",
                                                        originalId = reported.commentId,
                                                        postId     = reported.postId,
                                                        userName   = reported.userName,
                                                        content    = reported.text,
                                                        imageUrl   = "",
                                                        reason     = reasonSummary
                                                    )
                                                    db.collection("posts").document(reported.postId)
                                                        .collection("comments").document(reported.commentId)
                                                        .update("status", "trashed")
                                                        .addOnSuccessListener {
                                                            db.collection("reportedComments")
                                                                .whereEqualTo("commentId", reported.commentId)
                                                                .get()
                                                                .addOnSuccessListener { snap ->
                                                                    snap.documents.forEach { it.reference.delete() }
                                                                }
                                                            db.collection("notifications").add(hashMapOf(
                                                                "userName"  to reported.userName,
                                                                "message"   to "❌ Your comment was removed by an admin. Reason: $reasonSummary",
                                                                "type"      to "rejected",
                                                                "timestamp" to System.currentTimeMillis(),
                                                                "read"      to false
                                                            ))
                                                            toast("Comment moved to trash.")
                                                            logAudit("Trashed comment", "comment", reported.userName, reported.text.take(80))
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
                                        } // end else user-reported
                                    }
                                }
                            } else if (selectedReportChip == "Photos") {
                                if (reportedImages.isEmpty()) {
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
                            } else {
                                // Users chip
                                if (userReports.isEmpty()) {
                                    item { AdminEmptyState(Icons.Default.PersonOff, "No user reports", "No users have been reported.") }
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
                                            currentWarningCount = allUsers.find { it.username == report.reportedName }?.warningCount ?: 0,
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
                                                        val targetUser = allUsers.find { it.username == report.reportedName }
                                                        val newWarningCount = (targetUser?.warningCount ?: 0) + 1
                                                        if (targetUser != null) {
                                                            db.collection("users").document(targetUser.id)
                                                                .update("warningCount", newWarningCount)
                                                        }
                                                        if (newWarningCount >= 3) {
                                                            val suspendedUntil = com.google.firebase.Timestamp(
                                                                java.util.Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000))
                                                            targetUser?.let {
                                                                db.collection("users").document(it.id)
                                                                    .set(mapOf("suspended" to true, "suspendedUntil" to suspendedUntil),
                                                                        com.google.firebase.firestore.SetOptions.merge())
                                                            }
                                                            sendUserNotification(
                                                                db, report.reportedName,
                                                                "🚫 You've received your 3rd warning and your account is suspended for 7 days. You won't be able to log in until the suspension lifts.",
                                                                "moderation",
                                                                displayUserName = "Admin"
                                                            )
                                                            toast("3rd warning — account suspended for 7 days.")
                                                            logAudit("Suspended user (3rd warning)", "user_report", report.reportedName, report.reason)
                                                        } else {
                                                            sendUserNotification(
                                                                db, report.reportedName,
                                                                "⚠️ You have received a warning from an admin regarding your behavior on the platform. Please review community guidelines. ($newWarningCount/3)",
                                                                "moderation",
                                                                displayUserName = "Admin"
                                                            )
                                                            toast("Warning sent to ${report.reportedName}. ($newWarningCount/3)")
                                                            logAudit("Warned user ($newWarningCount/3)", "user_report", report.reportedName, report.reason)
                                                        }
                                                    }
                                                    .addOnFailureListener { toast("Failed to warn.", false) }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        AdminSection.PROFANITY -> {
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
                                        if (moderationLogs.size >= 3) {
                                            TextButton(onClick = { showMarkAllDialog = true }) {
                                                Icon(Icons.Default.DoneAll, null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = AGreen900)
                                                Spacer(Modifier.width(4.dp))
                                                Text("Mark all reviewed", fontSize = 12.sp,
                                                    color = AGreen900, fontWeight = FontWeight.SemiBold)
                                            }
                                        } // end if >= 3
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
                        AdminSection.AUDIT -> {
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

                        AdminSection.ANNOUNCEMENTS -> {
                            if (isLoadingAnnouncements) {
                                item { AdminLoadingState() }
                            } else if (announcements.isEmpty()) {
                                item { AdminEmptyState(Icons.Default.Campaign, "No announcements yet", "Tap the button below to post one to the community.") }
                            } else {
                                item {
                                    Text("${announcements.size} announcement${if (announcements.size != 1) "s" else ""}",
                                        fontSize = 12.sp, color = AMuted,
                                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                                }
                                items(announcements, key = { it.id }) { ann ->
                                    AdminAnnouncementCard(
                                        announcement = ann,
                                        onToggleActive = {
                                            val isReactivating = !ann.active
                                            val updates = hashMapOf<String, Any>("active" to isReactivating)
                                            // Deliberately does NOT touch dismissedBy — a user's dismissal
                                            // is a permanent "I've seen this" signal, independent of the
                                            // admin's active/inactive toggle. To re-notify everyone
                                            // including past dismissers, post a new announcement instead.
                                            if (isReactivating) {
                                                // If it had already expired, clear the expiry — otherwise
                                                // Homepage's expiresAt filter keeps it hidden even though
                                                // "active" is now true, silently undoing the reactivation.
                                                val expiresAt = ann.expiresAt
                                                if (expiresAt != null && expiresAt <= System.currentTimeMillis()) {
                                                    updates["expiresAt"] = com.google.firebase.firestore.FieldValue.delete()
                                                }
                                            }
                                            db.collection("announcements").document(ann.id)
                                                .update(updates)
                                                .addOnSuccessListener {
                                                    logAudit(if (isReactivating) "Reactivated announcement" else "Deactivated announcement",
                                                        "announcement", ann.createdByName, ann.title)
                                                    val reactivateMsg = if (ann.dismissedBy.isNotEmpty())
                                                        "Reactivated"
                                                    else "Announcement reactivated."
                                                    toast(if (isReactivating) reactivateMsg else "Announcement deactivated.")
                                                }
                                                .addOnFailureListener { toast("Failed to update.", false) }
                                        },
                                        onDelete = {
                                            db.collection("announcements").document(ann.id).delete()
                                                .addOnSuccessListener {
                                                    logAudit("Deleted announcement", "announcement", ann.createdByName, ann.title)
                                                    toast("Announcement deleted.")
                                                }
                                                .addOnFailureListener { toast("Failed to delete.", false) }
                                        }
                                    )
                                }
                            }
                        }

                        AdminSection.TRASH -> {
                            // ── Trash ──────────────────────────────────────────
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        "Posts"    to trashItems.count { it.type == "post" },
                                        "Comments" to trashItems.count { it.type == "comment" }
                                    ).forEach { (label, count) ->
                                        val selected = selectedTrashChip == label
                                        FilterChip(
                                            selected = selected,
                                            onClick  = { selectedTrashChip = label },
                                            label = {
                                                Text(
                                                    "$label ($count)",
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                                    fontSize   = 13.sp
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = AMuted,
                                                selectedLabelColor     = Color.White,
                                                containerColor         = AMuted.copy(alpha = 0.08f),
                                                labelColor             = AMuted
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled             = true,
                                                selected            = selected,
                                                borderColor         = AMuted.copy(alpha = 0.4f),
                                                selectedBorderColor = Color.Transparent,
                                                borderWidth         = 1.dp,
                                                selectedBorderWidth = 0.dp
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                }
                            }

                            if (isLoadingTrash) {
                                item { AdminLoadingState() }
                            } else {
                                val filteredTrash = trashItems.filter {
                                    it.type == if (selectedTrashChip == "Posts") "post" else "comment"
                                }
                                if (filteredTrash.isEmpty()) {
                                    item {
                                        AdminEmptyState(
                                            Icons.Default.DeleteSweep,
                                            "Trash is empty",
                                            "Deleted ${selectedTrashChip.lowercase()} will appear here for 30 days."
                                        )
                                    }
                                } else {
                                    item {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "${filteredTrash.size} item${if (filteredTrash.size != 1) "s" else ""} · auto-purge after 30 days",
                                                fontSize = 12.sp, color = AMuted,
                                                modifier = Modifier.padding(start = 4.dp)
                                            )
                                            if (filteredTrash.size >= 3) {
                                                var showEmptyDialog by remember { mutableStateOf(false) }
                                                if (showEmptyDialog) {
                                                    AlertDialog(
                                                        onDismissRequest = { showEmptyDialog = false },
                                                        shape = RoundedCornerShape(20.dp),
                                                        containerColor = AWhite,
                                                        icon = {
                                                            Box(Modifier.size(52.dp).clip(CircleShape)
                                                                .background(ARedLight),
                                                                contentAlignment = Alignment.Center) {
                                                                Icon(Icons.Default.DeleteForever, null,
                                                                    tint = ARedColor, modifier = Modifier.size(26.dp))
                                                            }
                                                        },
                                                        title = {
                                                            Text("Empty ${selectedTrashChip} Trash?",
                                                                fontWeight = FontWeight.ExtraBold,
                                                                fontSize = 17.sp, color = AOnSurface,
                                                                textAlign = TextAlign.Center,
                                                                modifier = Modifier.fillMaxWidth())
                                                        },
                                                        text = {
                                                            Text("All ${filteredTrash.size} ${selectedTrashChip.lowercase()} in trash will be permanently deleted. This cannot be undone.",
                                                                fontSize = 13.sp, color = AMuted,
                                                                textAlign = TextAlign.Center)
                                                        },
                                                        confirmButton = {
                                                            Column(Modifier.fillMaxWidth(),
                                                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                Button(
                                                                    onClick = {
                                                                        showEmptyDialog = false
                                                                        filteredTrash.toList().forEach { item ->
                                                                            db.collection("adminTrash").document(item.id).delete()
                                                                            if (item.type == "post") {
                                                                                db.collection("posts").document(item.originalId).delete()
                                                                            } else {
                                                                                db.collection("posts").document(item.postId)
                                                                                    .collection("comments").document(item.originalId).delete()
                                                                            }
                                                                        }
                                                                        trashItems.removeAll { it.type == if (selectedTrashChip == "Posts") "post" else "comment" }
                                                                        toast("Trash emptied.")
                                                                        logAudit("Emptied ${selectedTrashChip.lowercase()} trash", "trash", adminUserName, "${filteredTrash.size} items")
                                                                    },
                                                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                                                    shape = RoundedCornerShape(12.dp),
                                                                    colors = ButtonDefaults.buttonColors(
                                                                        containerColor = ARedColor,
                                                                        contentColor = Color.White)
                                                                ) { Text("Empty trash", fontWeight = FontWeight.Bold) }
                                                                OutlinedButton(
                                                                    onClick = { showEmptyDialog = false },
                                                                    modifier = Modifier.fillMaxWidth().height(44.dp),
                                                                    shape = RoundedCornerShape(12.dp)
                                                                ) { Text("Cancel", color = AMuted) }
                                                            }
                                                        }
                                                    )
                                                }
                                                TextButton(onClick = { showEmptyDialog = true }) {
                                                    Icon(Icons.Default.DeleteForever, null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = ARedColor)
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Empty trash", fontSize = 12.sp,
                                                        color = ARedColor, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
                                    }
                                    items(filteredTrash, key = { it.id }) { item ->
                                        AdminTrashCard(
                                            item = item,
                                            onRestore = {
                                                trashItems.remove(item)
                                                if (item.type == "post") {
                                                    db.collection("posts").document(item.originalId)
                                                        .update("status", "accepted")
                                                        .addOnSuccessListener {
                                                            db.collection("adminTrash").document(item.id).delete()
                                                            db.collection("notifications").add(hashMapOf(
                                                                "userName"  to item.userName,
                                                                "message"   to "✅ Your post has been restored by an admin.",
                                                                "type"      to "moderation_restored",
                                                                "timestamp" to System.currentTimeMillis(),
                                                                "read"      to false
                                                            ))
                                                            toast("Post restored.")
                                                            logAudit("Restored post from trash", "post", item.userName, item.content.take(80))
                                                        }
                                                        .addOnFailureListener {
                                                            trashItems.add(item)
                                                            toast("Failed to restore.", false)
                                                        }
                                                } else {
                                                    db.collection("posts").document(item.postId)
                                                        .collection("comments").document(item.originalId)
                                                        .update("status", "visible")
                                                        .addOnSuccessListener {
                                                            db.collection("posts").document(item.postId)
                                                                .update("comments", com.google.firebase.firestore.FieldValue.increment(1))
                                                            db.collection("adminTrash").document(item.id).delete()
                                                            db.collection("notifications").add(hashMapOf(
                                                                "userName"  to item.userName,
                                                                "message"   to "✅ Your comment has been restored by an admin.",
                                                                "type"      to "moderation_restored",
                                                                "timestamp" to System.currentTimeMillis(),
                                                                "read"      to false
                                                            ))
                                                            toast("Comment restored.")
                                                            logAudit("Restored comment from trash", "comment", item.userName, item.content.take(80))
                                                        }
                                                        .addOnFailureListener {
                                                            trashItems.add(item)
                                                            toast("Failed to restore.", false)
                                                        }
                                                }
                                            },
                                            onDeletePermanently = {
                                                trashItems.remove(item)
                                                if (item.type == "post") {
                                                    db.collection("posts").document(item.originalId).delete()
                                                } else {
                                                    db.collection("posts").document(item.postId)
                                                        .collection("comments").document(item.originalId).delete()
                                                }
                                                db.collection("adminTrash").document(item.id).delete()
                                                    .addOnSuccessListener {
                                                        toast("Permanently deleted.")
                                                        logAudit("Permanently deleted from trash", item.type, item.userName, item.content.take(80))
                                                    }
                                                    .addOnFailureListener {
                                                        trashItems.add(item)
                                                        toast("Failed to delete.", false)
                                                    }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Create announcement dialog ────────────────────────────────
                if (showCreateAnnouncement) {
                    var title by remember { mutableStateOf("") }
                    var message by remember { mutableStateOf("") }
                    var severity by remember { mutableStateOf("info") }
                    var hasExpiry by remember { mutableStateOf(false) }
                    var expiryDays by remember { mutableStateOf(7) }
                    var formError by remember { mutableStateOf<String?>(null) }

                    AlertDialog(
                        onDismissRequest = { showCreateAnnouncement = false },
                        shape = RoundedCornerShape(20.dp),
                        containerColor = AWhite,
                        icon = {
                            Box(Modifier.size(52.dp).clip(CircleShape).background(AGreen50), Alignment.Center) {
                                Icon(Icons.Default.Campaign, null, tint = AGreen900, modifier = Modifier.size(24.dp))
                            }
                        },
                        title = {
                            Text("New Announcement", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                                color = AOnSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = title, onValueChange = { if (it.length <= 60) { title = it; formError = null } },
                                    label = { Text("Title", fontSize = 12.sp) },
                                    singleLine = true, shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = AOnSurface, unfocusedTextColor = AOnSurface,
                                        focusedBorderColor = AGreen900, unfocusedBorderColor = ADivider,
                                        focusedContainerColor = AWhite, unfocusedContainerColor = AWhite
                                    )
                                )
                                OutlinedTextField(
                                    value = message, onValueChange = { if (it.length <= 300) { message = it; formError = null } },
                                    label = { Text("Message", fontSize = 12.sp) },
                                    shape = RoundedCornerShape(10.dp), maxLines = 4,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = AOnSurface, unfocusedTextColor = AOnSurface,
                                        focusedBorderColor = AGreen900, unfocusedBorderColor = ADivider,
                                        focusedContainerColor = AWhite, unfocusedContainerColor = AWhite
                                    )
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Priority", fontSize = 12.sp, color = AMuted, fontWeight = FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("info" to "Info", "warning" to "Warning", "urgent" to "Urgent").forEach { (value, label) ->
                                            val selected = severity == value
                                            val color = when (value) { "urgent" -> ARedColor; "warning" -> AAmber500; else -> AGreen900 }
                                            Box(
                                                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                                    .background(if (selected) color else Color(0xFFF3F4F6))
                                                    .clickable { severity = value }
                                                    .padding(vertical = 8.dp), Alignment.Center
                                            ) {
                                                Text(label, fontSize = 12.sp,
                                                    color = if (selected) Color.White else AMuted,
                                                    fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFF9FAFB)).clickable { hasExpiry = !hasExpiry }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Auto-expire", fontSize = 13.sp, color = AOnSurface, fontWeight = FontWeight.Medium)
                                    Switch(
                                        checked = hasExpiry,
                                        onCheckedChange = { hasExpiry = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = AGreen900,
                                            checkedBorderColor = AGreen900,
                                            uncheckedThumbColor = Color.White,
                                            uncheckedTrackColor = Color(0xFFD1D5DB),
                                            uncheckedBorderColor = Color(0xFFD1D5DB)
                                        )
                                    )
                                }
                                AnimatedVisibility(visible = hasExpiry) {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Expires in", fontSize = 12.sp, color = AMuted)
                                        listOf(3, 7, 14, 30).forEach { d ->
                                            val selected = expiryDays == d
                                            Box(
                                                Modifier.clip(RoundedCornerShape(8.dp))
                                                    .background(if (selected) AGreen900 else Color(0xFFF3F4F6))
                                                    .clickable { expiryDays = d }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text("${d}d", fontSize = 11.sp,
                                                    color = if (selected) Color.White else AMuted,
                                                    fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                                if (formError != null) {
                                    Text(formError ?: "", fontSize = 12.sp, color = ARedColor)
                                }
                            }
                        },
                        confirmButton = {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (title.isBlank() || message.isBlank()) {
                                            formError = "Title and message are required."
                                            return@Button
                                        }
                                        val expiresAt = if (hasExpiry)
                                            System.currentTimeMillis() + expiryDays * 24L * 60 * 60 * 1000
                                        else null
                                        postAnnouncement(
                                            db = db, title = title.trim(), message = message.trim(),
                                            severity = severity, startAt = System.currentTimeMillis(),
                                            expiresAt = expiresAt, adminUserName = adminUserName,
                                            adminDisplayName = adminDisplayName
                                        )
                                        logAudit("Posted announcement", "announcement", adminDisplayName, title.trim())
                                        toast("Announcement posted.")
                                        showCreateAnnouncement = false
                                    },
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AGreen900, contentColor = Color.White)
                                ) { Text("Post announcement", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { showCreateAnnouncement = false },
                                    modifier = Modifier.fillMaxWidth().height(44.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Cancel", color = AMuted) }
                            }
                        }
                    )
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

// ── Admin Reported Post Card ──────────────────────────────────────────────────
@Composable
private fun AdminReportedPostCard(
    post: AdminPost,
    reportReasons: List<String>,
    reportedBy: List<String>,
    reportCount: Int,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var showRemoveDialog  by remember { mutableStateOf(false) }
    var showDismissDialog by remember { mutableStateOf(false) }
    var postDisplayName   by remember(post.userName) { mutableStateOf(post.userName) }

    LaunchedEffect(post.userName) {
        FirebaseFirestore.getInstance().collection("users")
            .whereEqualTo("username", post.userName).limit(1).get()
            .addOnSuccessListener { snap ->
                val d = snap.documents.firstOrNull()?.getString("displayName")
                    ?.takeIf { it.isNotBlank() } ?: post.userName
                postDisplayName = d
            }
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            shape = RoundedCornerShape(20.dp), containerColor = AWhite,
            icon = {
                Box(Modifier.size(52.dp).clip(CircleShape).background(ARedLight),
                    Alignment.Center) {
                    Icon(Icons.Default.DeleteForever, null,
                        tint = ARedColor, modifier = Modifier.size(26.dp))
                }
            },
            title = {
                Text("Remove Post?", fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp, color = AOnSurface,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Text("This moves the post to Trash and notifies $postDisplayName it violated community guidelines.",
                    fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center)
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showRemoveDialog = false; onRemove() },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ARedColor, contentColor = Color.White)
                    ) { Text("Move to Trash", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { showRemoveDialog = false },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
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
                Box(Modifier.size(52.dp).clip(CircleShape).background(AGreen50),
                    Alignment.Center) {
                    Icon(Icons.Default.CheckCircle, null,
                        tint = AGreen900, modifier = Modifier.size(26.dp))
                }
            },
            title = {
                Text("Dismiss Report?", fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp, color = AOnSurface,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Text("The post will stay visible and all reports against it will be cleared.",
                    fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center)
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showDismissDialog = false; onDismiss() },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AGreen900, contentColor = Color.White)
                    ) { Text("Dismiss report", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { showDismissDialog = false },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Cancel", color = AMuted) }
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Top bar — report context
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(ARedLight, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(Modifier.fillMaxWidth(),
                    Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Flag, null,
                            tint = ARedColor, modifier = Modifier.size(13.dp))
                        Box(Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ARedColor)
                            .padding(horizontal = 7.dp, vertical = 2.dp)) {
                            Text(
                                "$reportCount report${if (reportCount != 1) "s" else ""}",
                                fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                        Text("Reported post", fontSize = 11.sp,
                            color = ARedColor, fontWeight = FontWeight.SemiBold)
                    }
                    Text(formatAdminTime(post.timestamp),
                        fontSize = 11.sp, color = AMuted)
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Author row
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(AGreen900, AGreen700))),
                        Alignment.Center
                    ) {
                        Text(postDisplayName.take(1).uppercase(),
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column {
                        Text(postDisplayName, fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp, color = AOnSurface)
                        Text(formatAdminTime(post.timestamp),
                            fontSize = 11.sp, color = AMuted)
                    }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AGreen50)
                        .padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text(post.activity, fontSize = 10.sp,
                            color = AGreen900, fontWeight = FontWeight.Medium)
                    }
                }

                // Report reasons
                if (reportReasons.isNotEmpty()) {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        reportReasons.distinct().forEach { reason ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(ARedLight)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(reason, fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold, color = ARedColor)
                            }
                        }
                    }
                }

                // Reported by
                if (reportedBy.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Person, null,
                            tint = AMuted, modifier = Modifier.size(12.dp))
                        Text(
                            "Reported by: ${reportedBy.distinct().joinToString(", ")}",
                            fontSize = 11.sp, color = AMuted
                        )
                    }
                }

                // Post image
                if (post.imageUrl.isNotBlank()) {
                    coil.compose.AsyncImage(
                        model = post.imageUrl,
                        contentDescription = "Post image",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }

                // Post description
                if (post.description.isNotBlank()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF3F4F6))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(ARedColor.copy(alpha = 0.4f))
                            )
                            Text(post.description, fontSize = 13.sp,
                                color = Color(0xFF374151), lineHeight = 19.sp,
                                maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                HorizontalDivider(color = ADivider, thickness = 0.5.dp)

                // Action buttons
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Dismiss — keep post, clear reports
                    Button(
                        onClick = { showDismissDialog = true },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AGreen900, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Dismiss", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = Color.White)
                    }
                    // Remove — delete post
                    OutlinedButton(
                        onClick = { showRemoveDialog = true },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ARedColor),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, ARedColor)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Remove", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
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
                    Text("This moves the comment to Trash bin and notifies the author it violated community guidelines.",
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
                    ) { Text("Move to Trash", fontWeight = FontWeight.Bold, color = Color.White) }
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

// ── Admin Hidden Comment Card ─────────────────────────────────────────────────
@Composable
private fun AdminHiddenCommentCard(
    reported: ReportedComment,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showDeleteDialog  by remember { mutableStateOf(false) }
    var authorDisplayName by remember(reported.userName) { mutableStateOf(reported.userName) }

    LaunchedEffect(reported.userName) {
        FirebaseFirestore.getInstance().collection("users")
            .whereEqualTo("username", reported.userName).limit(1).get()
            .addOnSuccessListener { snap ->
                val d = snap.documents.firstOrNull()?.getString("displayName")
                    ?.takeIf { it.isNotBlank() } ?: reported.userName
                authorDisplayName = d
            }
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            shape = RoundedCornerShape(20.dp), containerColor = AWhite,
            icon = {
                Box(Modifier.size(52.dp).clip(CircleShape).background(AGreen50),
                    Alignment.Center) {
                    Icon(Icons.Default.Visibility, null,
                        tint = AGreen900, modifier = Modifier.size(26.dp))
                }
            },
            title = {
                Text("Restore Comment?", fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp, color = AOnSurface,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Text("The comment will be made visible again and $authorDisplayName will be notified.",
                    fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center)
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showRestoreDialog = false; onRestore() },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AGreen900, contentColor = Color.White)
                    ) { Text("Restore comment", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { showRestoreDialog = false },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
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
                Box(Modifier.size(52.dp).clip(CircleShape).background(ARedLight),
                    Alignment.Center) {
                    Icon(Icons.Default.DeleteForever, null,
                        tint = ARedColor, modifier = Modifier.size(26.dp))
                }
            },
            title = {
                Text("Move to Trash?", fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp, color = AOnSurface,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Text("This moves the comment to Trash and notifies $authorDisplayName.",
                    fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center)
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showDeleteDialog = false; onDelete() },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ARedColor, contentColor = Color.White)
                    ) { Text("Move to Trash", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { showDeleteDialog = false },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Cancel", color = AMuted) }
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Amber top bar — visually distinct from red user-reported cards
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(AAmber50, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(Modifier.fillMaxWidth(),
                    Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.VisibilityOff, null,
                            tint = AAmber500, modifier = Modifier.size(13.dp))
                        Box(Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AAmber500)
                            .padding(horizontal = 7.dp, vertical = 2.dp)) {
                            Text("Admin Hidden", fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                        Text("Hidden by admin", fontSize = 11.sp,
                            color = AAmber500, fontWeight = FontWeight.SemiBold)
                    }
                    Text(formatAdminTime(reported.timestamp),
                        fontSize = 11.sp, color = AMuted)
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Author row
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(AGreen900, AGreen700))),
                        Alignment.Center
                    ) {
                        Text(authorDisplayName.take(1).uppercase(),
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column {
                        Text(authorDisplayName, fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp, color = AOnSurface)
                        Text("Hidden comment", fontSize = 11.sp, color = AMuted)
                    }
                }

                // Reason chips
                if (reported.reasons.isNotEmpty()) {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        reported.reasons.distinct().forEach { reason ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(AAmber50)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(reason, fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold, color = AAmber500)
                            }
                        }
                    }
                }

                // Comment text preview
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF3F4F6))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(AAmber500.copy(alpha = 0.5f))
                        )
                        Text(reported.text, fontSize = 13.sp,
                            color = Color(0xFF374151), lineHeight = 19.sp,
                            maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }

                HorizontalDivider(color = ADivider, thickness = 0.5.dp)

                // Action buttons
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showRestoreDialog = true },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AGreen900, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Visibility, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Restore", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = Color.White)
                    }
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ARedColor),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, ARedColor)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    currentWarningCount: Int = 0,
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

    val roleColor = when (report.reportedRole) {
        "rider"   -> Color(0xFF1565C0)
        "profile" -> Color(0xFF374151)
        else      -> Color(0xFF6A1B9A)
    }
    val roleBg = when (report.reportedRole) {
        "rider"   -> Color(0xFFE3F2FD)
        "profile" -> Color(0xFFF3F4F6)
        else      -> Color(0xFFF3E5F5)
    }

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
                                if (report.reportedRole == "profile") "User Reported"
                                else report.reportedRole.replaceFirstChar { it.uppercase() } + " Reported",
                                fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = roleColor
                            )
                        }
                        if (report.emergencyType.isNotBlank()) {
                            Text("· ${report.emergencyType}", fontSize = 11.sp, color = roleColor.copy(alpha = 0.7f))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (currentWarningCount > 0) {
                            Box(Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (currentWarningCount >= 2) ARedColor.copy(alpha = 0.15f) else AAmber500.copy(alpha = 0.15f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)) {
                                Text("$currentWarningCount/3 warnings", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = if (currentWarningCount >= 2) ARedColor else AAmber500)
                            }
                        }
                        Text(formatAdminTime(report.timestamp), fontSize = 11.sp, color = AMuted)
                    }
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

// formatAdminTime() diffs (now - timestamp); for a *future* expiresAt that's
// negative and always lands in the "<60s" bucket → always prints "Just now".
private fun formatExpiryCountdown(expiresAt: Long): String {
    if (expiresAt == 0L) return ""
    val diff = expiresAt - System.currentTimeMillis()
    return when {
        diff <= 0         -> "expired"
        diff < 3_600_000  -> "in ${(diff / 60_000).coerceAtLeast(1)}m"
        diff < 86_400_000 -> "in ${diff / 3_600_000}h"
        else              -> "in ${diff / 86_400_000}d"
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
                        fontSize = 13.sp, color = actionColor,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 2, overflow = TextOverflow.Ellipsis
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

// ── Admin Trash Card ──────────────────────────────────────────────────────────
@Composable
private fun AdminTrashCard(
    item: TrashItem,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    var showDeleteDialog  by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var ownerDisplayName  by remember(item.userName) { mutableStateOf(item.userName) }
    var deleterDisplayName by remember(item.deletedBy) { mutableStateOf(item.deletedBy) }

    LaunchedEffect(item.userName) {
        FirebaseFirestore.getInstance().collection("users")
            .whereEqualTo("username", item.userName).limit(1).get()
            .addOnSuccessListener { snap ->
                val d = snap.documents.firstOrNull()?.getString("displayName")
                    ?.takeIf { it.isNotBlank() } ?: item.userName
                ownerDisplayName = d
            }
    }

    LaunchedEffect(item.deletedBy) {
        if (item.deletedBy.isBlank()) return@LaunchedEffect
        FirebaseFirestore.getInstance().collection("users")
            .whereEqualTo("username", item.deletedBy).limit(1).get()
            .addOnSuccessListener { snap ->
                val d = snap.documents.firstOrNull()?.getString("displayName")
                    ?.takeIf { it.isNotBlank() } ?: item.deletedBy
                deleterDisplayName = d
            }
    }

    // Days remaining
    val daysRemaining = ((item.expiresAt - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    val expiryColor = when {
        daysRemaining <= 3  -> ARedColor
        daysRemaining <= 7  -> AAmber500
        else                -> AMuted
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            shape = RoundedCornerShape(20.dp), containerColor = AWhite,
            icon = {
                Box(Modifier.size(52.dp).clip(CircleShape).background(AGreen50),
                    Alignment.Center) {
                    Icon(Icons.Default.Restore, null,
                        tint = AGreen900, modifier = Modifier.size(26.dp))
                }
            },
            title = {
                Text("Restore ${if (item.type == "post") "Post" else "Comment"}?",
                    fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = AOnSurface,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Text("This will make the ${item.type} visible again and notify $ownerDisplayName.",
                    fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center)
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showRestoreDialog = false; onRestore() },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AGreen900, contentColor = Color.White)
                    ) { Text("Restore", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { showRestoreDialog = false },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
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
                Box(Modifier.size(52.dp).clip(CircleShape).background(ARedLight),
                    Alignment.Center) {
                    Icon(Icons.Default.DeleteForever, null,
                        tint = ARedColor, modifier = Modifier.size(26.dp))
                }
            },
            title = {
                Text("Delete Permanently?", fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp, color = AOnSurface,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Text("This ${item.type} will be gone forever. This cannot be undone.",
                    fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center)
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showDeleteDialog = false; onDeletePermanently() },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ARedColor, contentColor = Color.White)
                    ) { Text("Delete permanently", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { showDeleteDialog = false },
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
            // Grey top bar
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF3F4F6), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Delete, null,
                            tint = AMuted, modifier = Modifier.size(13.dp))
                        Box(Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AMuted.copy(alpha = 0.15f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)) {
                            Text(
                                if (item.type == "post") "Trashed Post" else "Trashed Comment",
                                fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = AMuted
                            )
                        }
                    }
                    // Expiry countdown
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Timer, null,
                            tint = expiryColor, modifier = Modifier.size(11.dp))
                        Text(
                            if (daysRemaining == 0) "Expires today" else "$daysRemaining day${if (daysRemaining != 1) "s" else ""} left",
                            fontSize = 11.sp, color = expiryColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Owner row
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(AMuted, Color(0xFF9CA3AF)))),
                        Alignment.Center
                    ) {
                        Text(ownerDisplayName.take(1).uppercase(),
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column {
                        Text(ownerDisplayName, fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp, color = AOnSurface)
                        Text("Deleted ${formatAdminTime(item.deletedAt)} by $deleterDisplayName",
                            fontSize = 11.sp, color = AMuted)
                    }
                }

                // Reason chip
                if (item.reason.isNotBlank()) {
                    Box(Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFF3F4F6))
                        .padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(item.reason, fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold, color = AMuted)
                    }
                }

                // Content preview
                if (item.content.isNotBlank()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF9FAFB))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(AMuted.copy(alpha = 0.4f))
                            )
                            Text(item.content, fontSize = 13.sp,
                                color = Color(0xFF374151), lineHeight = 19.sp,
                                maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                HorizontalDivider(color = ADivider, thickness = 0.5.dp)

                // Action buttons
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showRestoreDialog = true },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AGreen900, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Restore, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Restore", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = Color.White)
                    }
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ARedColor),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, ARedColor)
                    ) {
                        Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
// ── Admin Announcement Card ───────────────────────────────────────────────────
@Composable
private fun AdminAnnouncementCard(
    announcement: Announcement,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val isExpired = announcement.expiresAt != null && announcement.expiresAt < now
    val (accent, bg) = when (announcement.severity) {
        "urgent"  -> ARedColor to ARedLight
        "warning" -> AAmber500 to AAmber50
        else      -> AGreen900 to AGreen50
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(20.dp), containerColor = AWhite,
            icon = { Box(Modifier.size(52.dp).clip(CircleShape).background(ARedLight), Alignment.Center) {
                Icon(Icons.Default.DeleteForever, null, tint = ARedColor, modifier = Modifier.size(26.dp)) } },
            title = { Text("Delete Announcement?", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                color = AOnSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text  = { Text("This removes it for everyone immediately. This cannot be undone.",
                fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center) },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showDeleteDialog = false; onDelete() },
                        modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ARedColor, contentColor = Color.White)
                    ) { Text("Delete", fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = { showDeleteDialog = false },
                        modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(12.dp)
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
            Box(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Campaign, null, tint = accent, modifier = Modifier.size(13.dp))
                        Text(announcement.severity.replaceFirstChar { it.uppercase() },
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accent)
                        if (!announcement.active) {
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(AMuted.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text("INACTIVE", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = AMuted)
                            }
                        } else if (isExpired) {
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(AMuted.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text("EXPIRED", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = AMuted)
                            }
                        }
                    }
                    Text(formatAdminTime(announcement.createdAt), fontSize = 11.sp, color = AMuted)
                }
            }
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(announcement.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AOnSurface)
                Text(announcement.message, fontSize = 13.sp, color = Color(0xFF374151), lineHeight = 19.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Visibility, null, tint = AMuted, modifier = Modifier.size(12.dp))
                        Text("${announcement.dismissedBy.size} dismissed", fontSize = 11.sp, color = AMuted)
                    }
                    if (announcement.expiresAt != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Timer, null, tint = AMuted, modifier = Modifier.size(12.dp))
                            Text("Expires ${formatExpiryCountdown(announcement.expiresAt)}", fontSize = 11.sp, color = AMuted)
                        }
                    }
                }
                HorizontalDivider(color = ADivider, thickness = 0.5.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onToggleActive,
                        modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AGreen900),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, AGreen900)
                    ) {
                        Icon(if (announcement.active) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (announcement.active) "Deactivate" else "Reactivate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ARedColor),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, ARedColor)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminUserCard(
    user: AdminUser,
    onSave: (String) -> Unit,
    onScheduleDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onRoleChange: (String) -> Unit,
    onLiftSuspension: () -> Unit = {}
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editName by remember(user.displayName) { mutableStateOf(user.displayName) }
    var editRole by remember(user.role) { mutableStateOf(user.role) }

    val roleColor = when (user.role) { "admin" -> Color(0xFF6A1B9A); else -> AGreen900 }
    val roleBg    = when (user.role) { "admin" -> Color(0xFFF3E5F5); else -> AGreen50 }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            shape = RoundedCornerShape(20.dp), containerColor = AWhite,
            icon = { Box(Modifier.size(52.dp).clip(CircleShape).background(AGreen50), Alignment.Center) {
                Icon(Icons.Default.Edit, null, tint = AGreen900, modifier = Modifier.size(24.dp)) } },
            title = { Text("Edit User", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                color = AOnSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName, onValueChange = { editName = it },
                        label = { Text("Display name", fontSize = 12.sp) },
                        singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor    = AOnSurface,
                            unfocusedTextColor  = AOnSurface,
                            focusedBorderColor  = AGreen900,
                            unfocusedBorderColor = ADivider,
                            focusedContainerColor   = AWhite,
                            unfocusedContainerColor = AWhite
                        )
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Role", fontSize = 12.sp, color = AMuted, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFF3F4F6))
                                .padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text(user.role.replaceFirstChar { it.uppercase() }, fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold, color = AMuted)
                            }
                            Text("Change role from the user list, not here",
                                fontSize = 10.sp, color = AMuted)
                        }
                    }
                }
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showEditDialog = false; onSave(editName.trim()) },
                        enabled = editName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AGreen900, contentColor = Color.White)
                    ) { Text("Save changes", fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = { showEditDialog = false },
                        modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(12.dp)
                    ) { Text("Cancel", color = AMuted) }
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(20.dp), containerColor = AWhite,
            icon = { Box(Modifier.size(52.dp).clip(CircleShape).background(ARedLight), Alignment.Center) {
                Icon(Icons.Default.PersonRemove, null, tint = ARedColor, modifier = Modifier.size(24.dp)) } },
            title = { Text("Delete ${user.displayName}?", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                color = AOnSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = {
                Text("This schedules permanent deletion in 14 days — the same grace period users get when deleting their own account. ${user.displayName} will be signed out on next login attempt and can cancel by logging back in before the deadline.",
                    fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center)
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showDeleteDialog = false; onScheduleDelete() },
                        modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ARedColor, contentColor = Color.White)
                    ) { Text("Schedule deletion", fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = { showDeleteDialog = false },
                        modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(12.dp)
                    ) { Text("Cancel", color = AMuted) }
                }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AWhite), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.fillMaxWidth()) {
            if (user.pendingDeletion) {
                Box(Modifier.fillMaxWidth()
                    .background(ARedLight, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Timer, null, tint = ARedColor, modifier = Modifier.size(13.dp))
                        Text("Pending deletion · ${formatAdminTime(user.deletionScheduledAt)}",
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ARedColor)
                    }
                }
            }
            if (user.suspended) {
                Box(Modifier.fillMaxWidth()
                    .background(AAmber50, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Block, null, tint = AAmber500, modifier = Modifier.size(13.dp))
                        Text(
                            if (user.suspendedUntil > System.currentTimeMillis())
                                "Suspended until ${formatAdminTime(user.suspendedUntil)}"
                            else "Suspension expired — will lift on next login",
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AAmber500)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(roleBg), Alignment.Center) {
                        if (user.photoUrl.isNotBlank()) {
                            AsyncImage(model = user.photoUrl, contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                        } else {
                            Text(user.displayName.take(1).uppercase(), fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp, color = roleColor)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(user.displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AOnSurface)
                        Text(user.email.ifBlank { "@${user.username}" }, fontSize = 12.sp, color = AMuted)
                    }
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(roleBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text(user.role.replaceFirstChar { it.uppercase() }, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, color = roleColor)
                    }
                }
                if (user.createdAt > 0L) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.CalendarMonth, null, tint = AMuted, modifier = Modifier.size(11.dp))
                        Text("Joined ${formatAdminTime(user.createdAt)}", fontSize = 11.sp, color = AMuted)
                    }
                }
                HorizontalDivider(color = ADivider, thickness = 0.5.dp)
                var showRoleDialog by remember { mutableStateOf(false) }
                var pendingRole by remember { mutableStateOf(user.role) }
                if (showRoleDialog) {
                    AlertDialog(
                        onDismissRequest = { showRoleDialog = false; pendingRole = user.role },
                        shape = RoundedCornerShape(20.dp), containerColor = AWhite,
                        icon = { Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFFF3E5F5)), Alignment.Center) {
                            Icon(Icons.Default.AdminPanelSettings, null, tint = Color(0xFF6A1B9A), modifier = Modifier.size(24.dp)) } },
                        title = { Text("Change Role", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                            color = AOnSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("${user.displayName} is currently ${user.role}.", fontSize = 13.sp, color = AMuted, textAlign = TextAlign.Center)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    listOf("rider", "admin").forEach { r ->
                                        val selected = pendingRole == r
                                        Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                            .background(if (selected) AGreen900 else Color(0xFFF3F4F6))
                                            .clickable { pendingRole = r }
                                            .padding(vertical = 8.dp), Alignment.Center) {
                                            Text(r.replaceFirstChar { it.uppercase() }, fontSize = 12.sp,
                                                color = if (selected) Color.White else AMuted, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                if (pendingRole == "admin" && user.role != "admin") {
                                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                        .background(ARedLight).padding(10.dp)) {
                                        Text("This grants full admin access, including editing other users' roles.",
                                            fontSize = 12.sp, color = ARedColor, textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { showRoleDialog = false; onRoleChange(pendingRole) },
                                    enabled = pendingRole != user.role,
                                    modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (pendingRole == "admin") ARedColor else AGreen900,
                                        contentColor = Color.White)
                                ) { Text("Confirm: ${user.role} → $pendingRole", fontWeight = FontWeight.Bold) }
                                OutlinedButton(onClick = { showRoleDialog = false; pendingRole = user.role },
                                    modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(12.dp)
                                ) { Text("Cancel", color = AMuted) }
                            }
                        }
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pendingRole = user.role; showRoleDialog = true },
                        modifier = Modifier.width(40.dp).height(40.dp), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6A1B9A)),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF6A1B9A)),
                        contentPadding = PaddingValues(0.dp)
                    ) { Icon(Icons.Default.AdminPanelSettings, null, modifier = Modifier.size(16.dp)) }
                    OutlinedButton(
                        onClick = { editName = user.displayName; showEditDialog = true },
                        modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AGreen900),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, AGreen900)
                    ) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                        Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    if (user.pendingDeletion) {
                        Button(onClick = onCancelDelete,
                            modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AGreen900, contentColor = Color.White)
                        ) { Icon(Icons.Default.Restore, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                            Text("Cancel deletion", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                    } else if (user.suspended) {
                        Button(onClick = onLiftSuspension,
                            modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AAmber500, contentColor = Color.White)
                        ) { Icon(Icons.Default.LockOpen, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                            Text("Lift suspension", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                    } else {
                        OutlinedButton(onClick = { showDeleteDialog = true },
                            modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ARedColor),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, ARedColor)
                        ) { Icon(Icons.Default.PersonRemove, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                            Text("Delete", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}
