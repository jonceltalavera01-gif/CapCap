package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.draw.blur
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.border

private val Green900  = Color(0xFF06402B)
private val Green700  = Color(0xFF0A5C3D)
private val Green100  = Color(0xFFE8F5E9)
private val SurfaceBg = Color(0xFFF4F6F5)
private val OnSurface = Color(0xFF1A1A1A)
private val HighColor = Color(0xFFD32F2F)
private val HighBg    = Color(0xFFFFEBEE)
private val MedColor  = Color(0xFFF57C00)
private val MedBg     = Color(0xFFFFF3E0)
private val LowColor  = Color(0xFF388E3C)
private val LowBg     = Color(0xFFE8F5E9)


data class AlertItem(
    val id: String = "",
    val riderName: String,
    val riderDisplayName: String = "",
    val riderNameLower: String = "",
    val emergencyType: String,
    val locationName: String,
    val coordinates: GeoPoint,
    val time: String,
    val severity: AlertSeverity = AlertSeverity.HIGH,
    val additionalDetails: String? = null,
    val photoUrl: String? = null,
    val contactNumber: String? = null,
    val status: String = "active",
    val responderName: String? = null,
    val responderDisplayName: String = "",
    val createdAt: Long = 0L,
    val ratingGiven: Boolean = false,
    val ratingValue: Int? = null
)

enum class AlertSeverity { HIGH, MEDIUM, LOW }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    paddingValues    : PaddingValues,
    helperName       : String,
    userLocation     : GeoPoint?,
    onNavigateToHelp : (GeoPoint, String) -> Unit,
    onImOnMyWay      : (AlertItem) -> Unit,
    isAdmin          : Boolean = false,
){
    var isRefreshing    by remember { mutableStateOf(false) }
    var selectedFilter  by remember { mutableStateOf("All") }
    var errorMessage    by remember { mutableStateOf<String?>(null) }
    var successMessage  by remember { mutableStateOf<String?>(null) }
    var expandedAlertId by remember { mutableStateOf<String?>(null) }
    var alertToConfirm          by remember { mutableStateOf<AlertItem?>(null) }
    var alertToCancelResponse   by remember { mutableStateOf<AlertItem?>(null) }
    var sortBySeverity  by remember { mutableStateOf(false) }
    val scope  = rememberCoroutineScope()
    val alerts      = remember { mutableStateListOf<AlertItem>() }
    var isFirstLoad by remember { mutableStateOf(true) }
    var isClaimingAlert by remember { mutableStateOf(false) }
    var alertToRate     by remember { mutableStateOf<AlertItem?>(null) }
    var pendingRating        by remember { mutableIntStateOf(0) }
    var showReportDialog     by remember { mutableStateOf(false) }
    var reportTargetName     by remember { mutableStateOf("") }
    var reportTargetRole     by remember { mutableStateOf("") } // "rider" or "helper"
    var reportAlertRef       by remember { mutableStateOf<AlertItem?>(null) }
    var selectedReportReason by remember { mutableStateOf("") }
    var reportOtherText      by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        FirebaseFirestore.getInstance()
            .collection("alerts")
            .addSnapshotListener { snapshot, e ->
                if (e != null) { errorMessage = "Error: ${e.message}"; return@addSnapshotListener }
                snapshot?.let {
                    val unsorted = mutableListOf<AlertItem>()
                    for (doc in it.documents) {
                        try {
                            val docStatus = doc.getString("status") ?: "active"
                            if (docStatus == "resolved") continue
                            val lat = doc.getDouble("latitude") ?: 0.0
                            val lon = doc.getDouble("longitude") ?: 0.0
                            val severity = try {
                                AlertSeverity.valueOf((doc.getString("severity") ?: "HIGH").uppercase())
                            } catch (_: Exception) { AlertSeverity.HIGH }
                            val riderName = doc.getString("riderName") ?: "Unknown Rider"
                            unsorted.add(AlertItem(
                                id                  = doc.id,
                                riderName           = riderName,
                                riderDisplayName    = doc.getString("riderDisplayName")
                                    ?.takeIf { s -> s.isNotBlank() } ?: riderName,
                                riderNameLower      = doc.getString("riderNameLower")
                                    ?: riderName.trim().lowercase(),
                                emergencyType          = doc.getString("emergencyType") ?: "Emergency",
                                locationName           = doc.getString("locationName") ?: "Unknown Location",
                                coordinates            = GeoPoint(lat, lon),
                                time                   = formatAlertTime(doc.getLong("timestamp") ?: 0L),
                                severity               = severity,
                                additionalDetails      = doc.getString("additionalDetails"),
                                photoUrl               = doc.getString("photoUrl"),
                                contactNumber          = doc.getString("contactNumber"),
                                status                 = docStatus,
                                responderName          = doc.getString("responderName"),
                                responderDisplayName   = doc.getString("responderDisplayName") ?: "",
                                createdAt   = doc.getLong("timestamp") ?: 0L,
                                ratingGiven = doc.getBoolean("ratingGiven") ?: false,
                                ratingValue = doc.getLong("ratingValue")?.toInt()
                            ))
                        } catch (_: Exception) {}
                    }
                    alerts.clear()
                    alerts.addAll(unsorted.sortedByDescending { doc -> doc.createdAt })
                    isFirstLoad = false
                }
            }
    }

    val currentUserLower = helperName.trim().lowercase()
    val ownAlert         = alerts.firstOrNull { it.riderNameLower == currentUserLower }
    val othersAlerts     = alerts.filter { alert ->
        if (alert.riderNameLower == currentUserLower) return@filter false
        if (userLocation == null) return@filter true // show all if GPS not ready yet
        val dist = haversineKm(
            userLocation.latitude, userLocation.longitude,
            alert.coordinates.latitude, alert.coordinates.longitude
        )
        dist <= 3.0
    }

    val severityOrder = mapOf(AlertSeverity.HIGH to 0, AlertSeverity.MEDIUM to 1, AlertSeverity.LOW to 2)

    val filteredAlerts = run {
        val base = when (selectedFilter) {
            "High"   -> othersAlerts.filter { it.severity == AlertSeverity.HIGH }
            "Medium" -> othersAlerts.filter { it.severity == AlertSeverity.MEDIUM }
            "Low"    -> othersAlerts.filter { it.severity == AlertSeverity.LOW }
            else     -> othersAlerts
        }
        if (sortBySeverity)
            base.sortedWith(compareBy({ severityOrder[it.severity] }, { -it.createdAt }))
        else
            base // already sorted by newest from Firestore listener
    }

    LaunchedEffect(successMessage, errorMessage) {
        if (successMessage != null || errorMessage != null) {
            delay(3000); successMessage = null; errorMessage = null
        }
    }

    // ── Auto-expire stale alerts ──────────────────────────────────────────────
    // Only runs on the admin client to prevent duplicate resolves and
    // duplicate notifications when multiple users have the screen open.
    LaunchedEffect(isAdmin) {
        if (!isAdmin) return@LaunchedEffect
        while (true) {
            // Auto-revert responding alerts with no resolution after 15 minutes
            val revertCutoff = System.currentTimeMillis() - 15 * 60 * 1000L
            FirebaseFirestore.getInstance()
                .collection("alerts")
                .whereEqualTo("status", "responding")
                .get()
                .addOnSuccessListener { snap ->
                    for (doc in snap.documents) {
                        val createdAt   = doc.getLong("timestamp") ?: 0L
                        val responder   = doc.getString("responderName") ?: ""
                        if (createdAt > 0L && createdAt < revertCutoff && responder.isNotBlank()) {
                            doc.reference.update(mapOf(
                                "status"               to "active",
                                "responderName"        to "",
                                "responderDisplayName" to ""
                            )).addOnSuccessListener {
                                val riderName     = doc.getString("riderName")     ?: return@addOnSuccessListener
                                val emergencyType = doc.getString("emergencyType") ?: "alert"
                                FirebaseFirestore.getInstance()
                                    .collection("notifications").add(hashMapOf(
                                        "userName"  to riderName,
                                        "message"   to "The cyclist responding to your $emergencyType alert is no longer available. Your alert is active again — another cyclist can now help you.",
                                        "type"      to "alert",
                                        "timestamp" to System.currentTimeMillis(),
                                        "read"      to false
                                    ))
                            }
                        }
                    }
                }
            val cutoff = System.currentTimeMillis() - 2 * 60 * 60 * 1000L
            FirebaseFirestore.getInstance()
                .collection("alerts")
                .whereEqualTo("status", "active")
                .get()
                .addOnSuccessListener { snap ->
                    for (doc in snap.documents) {
                        val createdAt    = doc.getLong("timestamp") ?: 0L
                        val responder    = doc.getString("responderName") ?: ""
                        if (createdAt > 0L && createdAt < cutoff && responder.isBlank()) {
                            doc.reference.update("status", "resolved")
                                .addOnSuccessListener {
                                    val riderName     = doc.getString("riderName")     ?: return@addOnSuccessListener
                                    val emergencyType = doc.getString("emergencyType") ?: "alert"
                                    FirebaseFirestore.getInstance()
                                        .collection("notifications").add(hashMapOf(
                                            "userName"  to riderName,
                                            "message"   to "Your $emergencyType alert was automatically closed after 2 hours with no response. If you still need help, please send a new alert.",
                                            "type"      to "alert",
                                            "timestamp" to System.currentTimeMillis(),
                                            "read"      to false
                                        ))
                                }
                        }
                    }
                }
            delay(30 * 60 * 1000L) // check every 30 minutes
        }
    }

    fun sendHelpNotification(rider: String, msg: String) {
        FirebaseFirestore.getInstance().collection("notifications").add(hashMapOf(
            "userName"  to rider,
            "message"   to msg,
            "type"      to "accepted",
            "timestamp" to System.currentTimeMillis(),
            "read"      to false
        ))
    }

    fun fetchDisplayName(username: String, onResult: (String) -> Unit) {
        FirebaseFirestore.getInstance().collection("users")
            .whereEqualTo("username", username).limit(1).get()
            .addOnSuccessListener { snap ->
                val display = snap.documents.firstOrNull()?.getString("displayName")
                    ?.takeIf { it.isNotBlank() } ?: username
                onResult(display)
            }
            .addOnFailureListener { onResult(username) }
    }

    fun handleNavigateToHelp(alert: AlertItem) {
        scope.launch {
            try {
                fetchDisplayName(helperName) { helperDisplay ->
                    sendHelpNotification(alert.riderName, "$helperDisplay is navigating to your location!")
                }
                onNavigateToHelp(alert.coordinates, alert.emergencyType)
                successMessage = "Navigating to ${alert.riderDisplayName.ifBlank { alert.riderName }}"
            } catch (e: Exception) { errorMessage = "Failed: ${e.message}" }
        }
    }

    fun handleImOnMyWay(alert: AlertItem) {
        if (isClaimingAlert) return
        if (alert.status == "responding" && alert.responderName != helperName) {
            errorMessage = "${alert.responderName ?: "Someone"} is already on the way to help."
            return
        }
        isClaimingAlert = true
        scope.launch {
            try {
                val ref = FirebaseFirestore.getInstance()
                    .collection("alerts").document(alert.id)
                // Use a transaction so two responders can't claim simultaneously
                FirebaseFirestore.getInstance().runTransaction { transaction ->
                    val snapshot = transaction.get(ref)
                    val currentStatus       = snapshot.getString("status") ?: "active"
                    val currentResponder    = snapshot.getString("responderName") ?: ""
                    if (currentStatus == "responding" && currentResponder.isNotBlank() && currentResponder != helperName) {
                        throw Exception("${currentResponder} is already responding.")
                    }
                    transaction.update(ref, mapOf(
                        "status"               to "responding",
                        "responderName"        to helperName,
                        "responderDisplayName" to helperName  // overwritten below after transaction
                    ))
                }.addOnSuccessListener {
                    // Seed the live_locations document so the rider's listener fires immediately
                    FirebaseFirestore.getInstance()
                        .collection("live_locations")
                        .document(alert.id)
                        .set(
                            mapOf("helper" to mapOf(
                                "lat"       to 0.0,
                                "lng"       to 0.0,
                                "updatedAt" to System.currentTimeMillis()
                            )),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                    fetchDisplayName(helperName) { helperDisplay ->
                        sendHelpNotification(
                            alert.riderName,
                            "$helperDisplay is on the way to help you! Stay calm and stay visible."
                        )
                        // Write the resolved display name back so all listeners get it instantly
                        FirebaseFirestore.getInstance()
                            .collection("alerts").document(alert.id)
                            .update("responderDisplayName", helperDisplay)
                    }
                    onImOnMyWay(alert)
                    successMessage = "Notified ${alert.riderDisplayName.ifBlank { alert.riderName }} you're on your way"
                }.addOnFailureListener { e ->
                    errorMessage = e.message ?: "Someone else already claimed this alert."
                    isClaimingAlert = false
                }
            } catch (e: Exception) {
                errorMessage = "Failed: ${e.message}"
                isClaimingAlert = false
            }
        }
        isClaimingAlert = false
    }

    fun requestResolve(alert: AlertItem) {
        scope.launch {
            try {
                FirebaseFirestore.getInstance()
                    .collection("alerts").document(alert.id)
                    .update(mapOf(
                        "status"        to "resolved",
                        "responderName" to helperName
                    ))
                    .addOnSuccessListener {
                        alertToConfirm = null
                        fetchDisplayName(helperName) { helperDisplay ->
                            sendHelpNotification(
                                alert.riderName,
                                "$helperDisplay has marked your ${alert.emergencyType} as resolved. Stay safe!"
                            )
                        }
                        successMessage = "Alert resolved. Thank you for helping ${alert.riderDisplayName.ifBlank { alert.riderName }}!"
                    }
                    .addOnFailureListener { e ->
                        errorMessage   = "Failed: ${e.message}"
                        alertToConfirm = null
                    }
            } catch (e: Exception) { errorMessage = "Failed: ${e.message}" }
        }
    }

    val resolveAlert: (AlertItem, Int?, String?) -> Unit = { alert, ratingValue, reviewText ->
        scope.launch {
            try {
                val updates = mutableMapOf<String, Any>(
                    "status"      to "resolved",
                    "ratingGiven" to true
                )
                if (reviewText != null) updates["ratingReview"] = reviewText
                // Delete live location doc — revokes access for both sides
                FirebaseFirestore.getInstance()
                    .collection("live_locations")
                    .document(alert.id)
                    .delete()
                if (ratingValue != null) updates["ratingValue"] = ratingValue

                FirebaseFirestore.getInstance()
                    .collection("alerts").document(alert.id)
                    .update(updates)
                    .addOnSuccessListener {
                        // Write rating to responder's user document
                        if (ratingValue != null && !alert.responderName.isNullOrBlank()) {
                            val db = FirebaseFirestore.getInstance()
                            db.collection("users")
                                .whereEqualTo("username", alert.responderName!!)
                                .limit(1)
                                .get()
                                .addOnSuccessListener { snap ->
                                    val doc = snap.documents.firstOrNull() ?: return@addOnSuccessListener
                                    val currentRating = doc.getDouble("helperRating") ?: 0.0
                                    val currentCount  = doc.getLong("helperRatingCount")?.toInt() ?: 0
                                    val newCount      = currentCount + 1
                                    val newRating     = ((currentRating * currentCount) + ratingValue) / newCount
                                    val ratingUpdate  = mutableMapOf<String, Any>(
                                        "helperRating"      to newRating,
                                        "helperRatingCount" to newCount
                                    )
                                    doc.reference.update(ratingUpdate)
                                    // Save review as a separate document for history
                                    if (reviewText != null) {
                                        fetchDisplayName(alert.riderName) { riderDisplay ->
                                            db.collection("helperReviews").add(hashMapOf(
                                                "responderName"     to alert.responderName!!,
                                                "riderName"         to alert.riderName,
                                                "riderDisplayName"  to riderDisplay,
                                                "rating"            to ratingValue,
                                                "review"            to reviewText,
                                                "alertId"           to alert.id,
                                                "timestamp"         to System.currentTimeMillis()
                                            ))
                                        }
                                    }
                                }
                        }
                        alerts.remove(alert)
                        successMessage = "Alert closed. Stay safe out there!"
                        if (!alert.responderName.isNullOrEmpty()) {
                            fetchDisplayName(alert.riderName) { riderDisplay ->
                                val stars = if (ratingValue != null)
                                    "★".repeat(ratingValue).padEnd(5, '☆')
                                else null
                                val msg = if (stars != null)
                                    "$riderDisplay resolved their ${alert.emergencyType} alert and rated your response $stars ($ratingValue/5). Thank you for helping!"
                                else
                                    "$riderDisplay has resolved their ${alert.emergencyType} alert. Thank you for helping!"
                                FirebaseFirestore.getInstance().collection("notifications").add(hashMapOf(
                                    "userName"  to alert.responderName!!,
                                    "message"   to msg,
                                    "type"      to if (stars != null) "rating" else "accepted",
                                    "timestamp" to System.currentTimeMillis(),
                                    "read"      to false
                                ))
                            }
                        }
                        FirebaseFirestore.getInstance()
                            .collection("notifications")
                            .whereEqualTo("alertId", alert.id)
                            .get()
                            .addOnSuccessListener { snap ->
                                snap.documents.forEach { it.reference.delete() }
                            }
                    }
                    .addOnFailureListener { e -> errorMessage = "Failed: ${e.message}" }
            } catch (e: Exception) { errorMessage = "Failed: ${e.message}" }
        }
    }

    val submitResponderRating: (String, Int) -> Unit = { responderUsername, rating ->
        val db = FirebaseFirestore.getInstance()
        db.collection("users")
            .whereEqualTo("username", responderUsername)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                val doc = snap.documents.firstOrNull() ?: return@addOnSuccessListener
                val currentRating = doc.getDouble("helperRating") ?: 0.0
                val currentCount  = doc.getLong("helperRatingCount")?.toInt() ?: 0
                val newCount      = currentCount + 1
                val newRating     = ((currentRating * currentCount) + rating) / newCount
                doc.reference.update(mapOf(
                    "helperRating"      to newRating,
                    "helperRatingCount" to newCount
                ))
            }
    }

    fun confirmSelfResolved(alert: AlertItem) {
        if (!alert.responderName.isNullOrBlank() && !alert.ratingGiven) {
            alertToRate   = alert
            pendingRating = 0
            return
        }
        resolveAlert(alert, null, null)
    }


    val highCount= othersAlerts.count { it.severity == AlertSeverity.HIGH }
    val medCount  = othersAlerts.count { it.severity == AlertSeverity.MEDIUM }
    val lowCount  = othersAlerts.count { it.severity == AlertSeverity.LOW }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationImportant, null,
                            tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Emergency Alerts", fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp, color = Color.White, letterSpacing = 0.3.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Green900)
            )
        },
        containerColor = SurfaceBg
    ) { innerPadding ->

        Box(Modifier.fillMaxSize()) {

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    scope.launch {
                        isRefreshing = true; delay(1000)
                        isRefreshing = false; successMessage = "Alerts refreshed"
                    }
                },
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = paddingValues.calculateBottomPadding() + 24.dp)
                ) {
                    item { AlertSummaryBanner(othersAlerts.size, highCount, medCount, lowCount)
                        // ── Own alert pinned above filters ────────────────────
                    }

                    if (ownAlert != null) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(4.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1565C0))
                                    )
                                    Text(
                                        "YOUR ACTIVE ALERT",
                                        fontSize     = 11.sp,
                                        fontWeight   = FontWeight.ExtraBold,
                                        color        = Color(0xFF1565C0),
                                        letterSpacing = 1.2.sp
                                    )
                                }
                                OwnAlertCard(
                                    alert             = ownAlert,
                                    onConfirmResolved = { confirmSelfResolved(ownAlert) },
                                    modifier          = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 4.dp)
                                )
                                HorizontalDivider(
                                    modifier  = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    color     = Color(0xFFE0E0E0),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }

                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val totalOthers = othersAlerts.size
                            val chips = listOf(
                                Triple("All",    totalOthers, Green900),
                                Triple("High",   highCount,   HighColor),
                                Triple("Medium", medCount,    MedColor),
                                Triple("Low",    lowCount,    LowColor)
                            )
                            items(chips) { (label, count, color) ->
                                val selected = selectedFilter == label
                                FilterChip(
                                    selected = selected,
                                    onClick  = { selectedFilter = label },
                                    label = {
                                        Text(
                                            "$label ($count)",
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize   = 13.sp
                                        )
                                    },
                                    leadingIcon = {
                                        if (label != "All") {
                                            Box(
                                                modifier = Modifier.size(9.dp).clip(CircleShape)
                                                    .background(if (selected) Color.White else color)
                                            )
                                        }
                                    },
                                    trailingIcon = {
                                        // × clear icon only on selected non-All chips
                                        if (selected && label != "All") {
                                            Icon(
                                                Icons.Default.Close, "Clear filter",
                                                tint     = Color.White,
                                                modifier = Modifier.size(14.dp).clickable(
                                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                    indication        = null
                                                ) { selectedFilter = "All" }
                                            )
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor   = color,
                                        selectedLabelColor       = Color.White,
                                        selectedLeadingIconColor = Color.White,
                                        containerColor = if (label != "All") color.copy(alpha = 0.10f) else Color.White,
                                        labelColor     = if (label != "All") color else OnSurface
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled             = true,
                                        selected            = selected,
                                        borderColor         = if (label != "All") color.copy(alpha = 0.55f) else Color(0xFFDDDDDD),
                                        selectedBorderColor = Color.Transparent,
                                        borderWidth         = if (label != "All" && !selected) 1.5.dp else 1.dp,
                                        selectedBorderWidth = 0.dp
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    if (selectedFilter == "All") "All Alerts" else "$selectedFilter Priority",
                                    fontWeight = FontWeight.Bold, fontSize = 17.sp, color = OnSurface
                                )
                                Text(
                                    "Sorted by ${if (sortBySeverity) "severity" else "newest"}",
                                    fontSize = 11.sp, color = Color(0xFF9E9E9E)
                                )
                            }
                            // Sort toggle
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (sortBySeverity) Green900 else Color.White)
                                    .border(1.dp, if (sortBySeverity) Green900 else Color(0xFFDDDDDD), RoundedCornerShape(10.dp))
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication        = null
                                    ) { sortBySeverity = !sortBySeverity }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    Icons.Default.Sort, null,
                                    tint     = if (sortBySeverity) Color.White else Color(0xFF9E9E9E),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    if (sortBySeverity) "By severity" else "By newest",
                                    fontSize   = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = if (sortBySeverity) Color.White else Color(0xFF9E9E9E)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    if (isFirstLoad) {
                        items(3) {
                            ShimmerAlertCard(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                            )
                        }
                    } else if (filteredAlerts.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                colors   = CardDefaults.cardColors(containerColor = Color.White),
                                shape    = RoundedCornerShape(18.dp)
                            ) {
                                Column(
                                    modifier            = Modifier.fillMaxWidth().padding(48.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.CheckCircle, null,
                                        tint = LowColor, modifier = Modifier.size(56.dp))
                                    Spacer(Modifier.height(12.dp))
                                    Text("All clear!", fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp, color = OnSurface)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        if (selectedFilter == "All") "No active alerts right now"
                                        else "No $selectedFilter priority alerts",
                                        fontSize = 13.sp, color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                    items(filteredAlerts, key = { it.id }) { alert ->
                        AlertCard(
                            alert            = alert,
                            isExpanded       = expandedAlertId == alert.id,
                            helperName       = helperName,
                            isAdmin          = isAdmin,
                            onShowDetails    = { expandedAlertId = if (expandedAlertId == alert.id) null else alert.id },
                            onNavigateToHelp = { handleNavigateToHelp(alert) },
                            onImOnMyWay      = { handleImOnMyWay(alert) },
                            onDismiss        = { alertToConfirm = alert },
                            onCancelResponse = { alertToCancelResponse = alert },
                            onReportRider    = { reportedAlert ->
                                reportTargetName     = reportedAlert.riderDisplayName
                                    .takeIf { it.isNotBlank() } ?: reportedAlert.riderName
                                reportTargetRole     = "rider"
                                reportAlertRef       = reportedAlert
                                selectedReportReason = ""
                                reportOtherText      = ""
                                showReportDialog     = true
                            },
                            modifier         = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            val toastMsg  = successMessage ?: errorMessage
            val isSuccess = successMessage != null
            if (toastMsg != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = paddingValues.calculateBottomPadding() + 24.dp)
                        .padding(horizontal = 24.dp),
                    shape     = RoundedCornerShape(14.dp),
                    colors    = CardDefaults.cardColors(
                        containerColor = if (isSuccess) Color(0xFF1B5E20) else Color(0xFF7F0000)
                    ),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                            null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(toastMsg, color = Color.White, fontSize = 13.sp,
                            fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    alertToConfirm?.let { alert ->
        AlertDialog(
            onDismissRequest = { alertToConfirm = null },
            shape            = RoundedCornerShape(20.dp),
            containerColor   = Color.White,
            icon = {
                Box(
                    modifier         = Modifier.size(56.dp).clip(CircleShape).background(LowBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CheckCircle, null,
                        tint = LowColor, modifier = Modifier.size(28.dp))
                }
            },
            title = {
                Text("Mark as Resolved?",
                    fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                    textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier   = Modifier.fillMaxWidth())
            },
            text = {
                Text(
                    "This will close ${alert.riderDisplayName.ifBlank { alert.riderName }}'s ${alert.emergencyType} alert and notify them it has been resolved.",
                    fontSize  = 14.sp, color = Color.Gray, lineHeight = 20.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick  = { requestResolve(alert) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = LowColor, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Yes, Mark Resolved", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    }
                    OutlinedButton(
                        onClick  = { alertToConfirm = null },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape    = RoundedCornerShape(12.dp),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDDDDDD))
                    ) {
                        Text("Cancel", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }
        )
    }
    var pendingReview by remember { mutableStateOf("") }
    alertToRate?.let { alert ->
        val responderDisplay = alert.responderDisplayName
            .takeIf { it.isNotBlank() } ?: alert.responderName ?: "the responder"
        AlertDialog(
            onDismissRequest = { /* blocked — must interact with buttons */ },
            shape          = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            icon = {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape)
                        .background(Color(0xFFE8F5E9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Star, null,
                        tint     = Color(0xFFF57C00),
                        modifier = Modifier.size(28.dp))
                }
            },
            title = {
                Text(
                    "Rate Your Helper",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 18.sp,
                    color      = Color(0xFF1A1A1A),
                    textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier   = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "How was $responderDisplay's response?",
                        fontSize  = 14.sp,
                        color     = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    // Star row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        (1..5).forEach { star ->
                            Icon(
                                imageVector = if (star <= pendingRating)
                                    Icons.Default.Star
                                else
                                    Icons.Default.StarBorder,
                                contentDescription = "$star stars",
                                tint     = if (star <= pendingRating) Color(0xFFF57C00)
                                else Color(0xFFDDDDDD),
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable(
                                        interactionSource = remember {
                                            androidx.compose.foundation.interaction.MutableInteractionSource()
                                        },
                                        indication = null
                                    ) { pendingRating = star }
                            )
                        }
                    }
                    if (pendingRating > 0) {
                        Text(
                            when (pendingRating) {
                                1 -> "Poor — they barely helped"
                                2 -> "Fair — could have been better"
                                3 -> "Good — they helped adequately"
                                4 -> "Great — they really came through"
                                5 -> "Excellent — lifesaver! 🙌"
                                else -> ""
                            },
                            fontSize   = 12.sp,
                            color      = Color(0xFF455A64),
                            fontWeight = FontWeight.Medium,
                            textAlign  = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    // Optional written review
                    OutlinedTextField(
                        value         = pendingReview,
                        onValueChange = { if (it.length <= 150) pendingReview = it },
                        placeholder   = { Text("Leave a comment (optional)", fontSize = 12.sp, color = Color.LightGray) },
                        modifier      = Modifier.fillMaxWidth().height(80.dp),
                        shape         = RoundedCornerShape(12.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = Color(0xFFF57C00),
                            unfocusedBorderColor    = Color(0xFFE0E0E0),
                            focusedContainerColor   = Color.White,
                            unfocusedContainerColor = Color(0xFFFAFAFA),
                            cursorColor             = Color(0xFFF57C00),
                            focusedTextColor        = Color(0xFF1A1A1A),
                            unfocusedTextColor      = Color(0xFF1A1A1A)
                        ),
                        maxLines = 3
                    )
                    if (pendingRating == 0) {
                        Text(
                            "Please select a star rating to close this alert.",
                            fontSize   = 11.sp,
                            color      = Color(0xFFD32F2F),
                            fontWeight = FontWeight.Medium,
                            textAlign  = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    // ── Report helper option ──────────────────────────────────
                    HorizontalDivider(
                        color     = Color(0xFFEEEEEE),
                        thickness = 0.5.dp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication        = null
                            ) {
                                val helper = alert.responderName ?: return@clickable
                                reportTargetName     = alert.responderDisplayName
                                    .takeIf { it.isNotBlank() } ?: helper
                                reportTargetRole     = "helper"
                                reportAlertRef       = alert
                                selectedReportReason = ""
                                reportOtherText      = ""
                                showReportDialog     = true
                            }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Flag, null,
                            tint     = Color(0xFFD32F2F).copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "Report this helper",
                            fontSize   = 12.sp,
                            color      = Color(0xFFD32F2F).copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier            = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (pendingRating > 0) {
                                val review = pendingReview.trim().ifBlank { null }
                                alertToRate   = null
                                pendingReview = ""
                                resolveAlert(alert, pendingRating, review)
                            }
                        },
                        enabled  = pendingRating > 0,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor         = Color(0xFF2E7D32),
                            contentColor           = Color.White,
                            disabledContainerColor = Color(0xFFBDBDBD),
                            disabledContentColor   = Color.White
                        )
                    ) {
                        Text(
                            "Submit Rating & Close Alert",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 14.sp
                        )
                    }
                    OutlinedButton(
                        onClick  = { /* keep dialog open */ },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape    = RoundedCornerShape(12.dp),
                        border   = androidx.compose.foundation.BorderStroke(
                            1.dp, Color(0xFFDDDDDD))
                    ) {
                        Text("Go Back", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }
        )
    }
    // ── Report User Dialog ────────────────────────────────────────────────────
    if (showReportDialog) {
        val reportReasons = listOf(
            "False/Fake Alert",
            "Never showed up",
            "Inappropriate behavior",
            "Other"
        )
        val isOther          = selectedReportReason == "Other"
        val canSubmit        = selectedReportReason.isNotBlank() &&
                (!isOther || reportOtherText.trim().length >= 5)

        AlertDialog(
            onDismissRequest = {
                showReportDialog     = false
                selectedReportReason = ""
                reportOtherText      = ""
            },
            shape          = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            icon = {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape)
                        .background(Color(0xFFFFEBEE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Flag, null,
                        tint     = Color(0xFFD32F2F),
                        modifier = Modifier.size(28.dp))
                }
            },
            title = {
                Text(
                    "Report ${reportTargetRole.replaceFirstChar { it.uppercase() }}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 18.sp,
                    color      = Color(0xFF1A1A1A),
                    textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier   = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Why are you reporting $reportTargetName?",
                        fontSize  = 13.sp,
                        color     = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier  = Modifier.fillMaxWidth()
                    )
                    reportReasons.forEach { reason ->
                        val isSelected = selectedReportReason == reason
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) Color(0xFFFFEBEE)
                                    else Color(0xFFF7F7F7)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) Color(0xFFD32F2F)
                                    else Color(0xFFE0E0E0),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication        = null
                                ) { selectedReportReason = reason }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(18.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) Color(0xFFD32F2F)
                                        else Color.White
                                    )
                                    .border(
                                        1.5.dp,
                                        if (isSelected) Color(0xFFD32F2F)
                                        else Color(0xFFBBBBBB),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier.size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    )
                                }
                            }
                            Text(
                                reason,
                                fontSize   = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold
                                else FontWeight.Normal,
                                color      = if (isSelected) Color(0xFFD32F2F)
                                else Color(0xFF1A1A1A)
                            )
                        }
                    }
                    // Optional/required text field
                    androidx.compose.animation.AnimatedVisibility(
                        visible = selectedReportReason.isNotBlank()
                    ) {
                        OutlinedTextField(
                            value         = reportOtherText,
                            onValueChange = { if (it.length <= 100) reportOtherText = it },
                            placeholder   = {
                                Text(
                                    if (isOther) "Please describe the issue (required)"
                                    else         "Add a comment (optional)",
                                    fontSize = 12.sp,
                                    color    = Color.LightGray
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor      = Color(0xFFD32F2F),
                                unfocusedBorderColor    = Color(0xFFE0E0E0),
                                focusedContainerColor   = Color.White,
                                unfocusedContainerColor = Color(0xFFFAFAFA),
                                cursorColor             = Color(0xFFD32F2F),
                                focusedTextColor        = Color(0xFF1A1A1A),
                                unfocusedTextColor      = Color(0xFF1A1A1A)
                            ),
                            maxLines = 3
                        )
                    }
                    if (isOther && reportOtherText.trim().length < 5 && reportOtherText.isNotBlank()) {
                        Text(
                            "Please provide more detail.",
                            fontSize   = 11.sp,
                            color      = Color(0xFFD32F2F),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier            = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (!canSubmit) return@Button
                            val alert     = reportAlertRef ?: return@Button
                            val reason    = if (isOther) reportOtherText.trim()
                            else selectedReportReason
                            val comment   = if (!isOther) reportOtherText.trim()
                            else null
                            val db        = FirebaseFirestore.getInstance()
                            val reportData = hashMapOf<String, Any>(
                                "reporterName"   to helperName,
                                "reportedName"   to reportTargetName,
                                "reportedRole"   to reportTargetRole,
                                "alertId"        to alert.id,
                                "emergencyType"  to alert.emergencyType,
                                "reason"         to reason,
                                "timestamp"      to System.currentTimeMillis(),
                                "reviewed"       to false
                            )
                            if (!comment.isNullOrBlank()) reportData["comment"] = comment
                            db.collection("userReports").add(reportData)
                                .addOnSuccessListener {
                                    // Notify admin
                                    val adminMsg = buildString {
                                        append("🚩 $helperName reported $reportTargetName ")
                                        append("(${reportTargetRole}) for \"$reason\"")
                                        if (!comment.isNullOrBlank()) append(" — \"$comment\"")
                                        append(" | Alert: ${alert.emergencyType}")
                                    }
                                    db.collection("notifications").add(hashMapOf(
                                        "userName"  to "Admin",
                                        "message"   to adminMsg,
                                        "type"      to "report",
                                        "timestamp" to System.currentTimeMillis(),
                                        "read"      to false
                                    ))
                                    successMessage = "Report submitted. Thank you for keeping the community safe."
                                }
                                .addOnFailureListener {
                                    errorMessage = "Failed to submit report. Please try again."
                                }
                            showReportDialog     = false
                            selectedReportReason = ""
                            reportOtherText      = ""
                        },
                        enabled  = canSubmit,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor         = Color(0xFFD32F2F),
                            contentColor           = Color.White,
                            disabledContainerColor = Color(0xFFBDBDBD),
                            disabledContentColor   = Color.White
                        )
                    ) {
                        Text("Submit Report",
                            fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    OutlinedButton(
                        onClick  = {
                            showReportDialog     = false
                            selectedReportReason = ""
                            reportOtherText      = ""
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape    = RoundedCornerShape(12.dp),
                        border   = androidx.compose.foundation.BorderStroke(
                            1.dp, Color(0xFFDDDDDD))
                    ) {
                        Text("Cancel", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }
        )
    }

    alertToCancelResponse?.let { alert ->
        AlertDialog(
            onDismissRequest = { alertToCancelResponse = null },
            shape            = RoundedCornerShape(20.dp),
            containerColor   = Color.White,
            icon = {
                Box(
                    modifier         = Modifier.size(56.dp).clip(CircleShape)
                        .background(Color(0xFFFFF3E0)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Cancel, null,
                        tint     = Color(0xFFF57C00),
                        modifier = Modifier.size(28.dp))
                }
            },
            title = {
                Text("Cancel Response?",
                    fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                    color      = Color(0xFF1A1A1A),
                    textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier   = Modifier.fillMaxWidth())
            },
            text = {
                Text(
                    "This will notify ${alert.riderDisplayName.ifBlank { alert.riderName }} that you can no longer help. Their alert will become active again.",
                    fontSize  = 14.sp, color = Color.Gray, lineHeight = 20.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                FirebaseFirestore.getInstance()
                                    .collection("alerts").document(alert.id)
                                    .update(mapOf(
                                        "status"               to "active",
                                        "responderName"        to "",
                                        "responderDisplayName" to ""
                                    ))
                                    .addOnSuccessListener {
                                        alertToCancelResponse = null
                                        // Revoke live location access
                                        FirebaseFirestore.getInstance()
                                            .collection("live_locations")
                                            .document(alert.id)
                                            .delete()
                                        fetchDisplayName(helperName) { helperDisplay ->
                                            sendHelpNotification(
                                                alert.riderName,
                                                "$helperDisplay can no longer respond to your ${alert.emergencyType} alert. Your alert is active again."
                                            )
                                        }
                                        successMessage = "Response cancelled. The rider has been notified."
                                    }
                                    .addOnFailureListener { e ->
                                        errorMessage          = "Failed: ${e.message}"
                                        alertToCancelResponse = null
                                    }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF57C00),
                            contentColor   = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Cancel, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Yes, Cancel Response", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    }
                    OutlinedButton(
                        onClick  = { alertToCancelResponse = null },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape    = RoundedCornerShape(12.dp),
                        border   = BorderStroke(1.dp, Color(0xFFDDDDDD)),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
                    ) {
                        Text("Keep Responding", fontSize = 14.sp)
                    }
                }
            }
        )
    }
}




// ── Pinned "Your Active Alert" card ──────────────────────────────────────────
@Composable
private fun OwnAlertCard(
    alert             : AlertItem,
    onConfirmResolved : () -> Unit,
    modifier          : Modifier = Modifier
) {
    Card(
        modifier  = modifier.animateContentSize(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)),
        colors    = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(Color(0xFF1565C0).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.NotificationImportant, null,
                        tint = Color(0xFF1565C0), modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(alert.emergencyType, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, color = OnSurface)
                    Spacer(Modifier.height(2.dp))
                    val waitingTooLong = alert.status == "active" &&
                            alert.createdAt > 0L &&
                            (System.currentTimeMillis() - alert.createdAt) > 5 * 60 * 1000L

                    var responderDisplayName by remember(alert.responderName) {
                        mutableStateOf(
                            alert.responderDisplayName.takeIf { it.isNotBlank() }
                                ?: alert.responderName
                                ?: "Someone"
                        )
                    }
                    LaunchedEffect(alert.responderName) {
                        if (alert.responderDisplayName.isBlank() && !alert.responderName.isNullOrBlank()) {
                            FirebaseFirestore.getInstance().collection("users")
                                .whereEqualTo("username", alert.responderName).limit(1).get()
                                .addOnSuccessListener { snap ->
                                    val d = snap.documents.firstOrNull()?.getString("displayName")
                                        ?.takeIf { it.isNotBlank() } ?: alert.responderName
                                    responderDisplayName = d
                                }
                        }
                    }
                    Text(
                        when (alert.status) {
                            "responding" -> "$responderDisplayName is on the way to help you"
                            else         -> if (waitingTooLong)
                                "⚠️ No one nearby yet — consider calling emergency services"
                            else
                                "Waiting for a nearby cyclist to respond..."
                        },
                        fontSize = 12.sp,
                        color    = if (waitingTooLong) Color(0xFFD32F2F) else Color(0xFF455A64)
                    )
                }
            }

            // Self-resolve — always available to the sender
            var showSelfResolveDialog by remember { mutableStateOf(false) }

                if (showSelfResolveDialog) {
                    AlertDialog(
                        onDismissRequest = { showSelfResolveDialog = false },
                        shape            = RoundedCornerShape(20.dp),
                        containerColor   = Color.White,
                        icon = {
                            Box(
                                modifier         = Modifier.size(56.dp).clip(CircleShape)
                                    .background(Color(0xFFE8F5E9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CheckCircle, null,
                                    tint     = Color(0xFF2E7D32),
                                    modifier = Modifier.size(28.dp))
                            }
                        },
                        title = {
                            Text(
                                "Mark as Resolved?",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize   = 18.sp,
                                color      = Color(0xFF1A1A1A),
                                textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier   = Modifier.fillMaxWidth()
                            )
                        },
                        text = {
                            Text(
                                "Confirm that your ${alert.emergencyType} situation has been handled and is no longer an emergency.",
                                fontSize  = 14.sp,
                                color     = Color.Gray,
                                lineHeight = 20.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        },
                        confirmButton = {
                            Column(
                                modifier            = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick  = { showSelfResolveDialog = false; onConfirmResolved() },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape    = RoundedCornerShape(12.dp),
                                    colors   = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2E7D32),
                                        contentColor   = Color.White
                                    )
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Yes, It's Resolved", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                }
                                OutlinedButton(
                                    onClick  = { showSelfResolveDialog = false },
                                    modifier = Modifier.fillMaxWidth().height(44.dp),
                                    shape    = RoundedCornerShape(12.dp),
                                    border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDDDDDD))
                                ) {
                                    Text("Cancel", color = Color.Gray, fontSize = 14.sp)
                                }
                            }
                        }
                    )
                }

                Button(
                    onClick  = { showSelfResolveDialog = true },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32),
                        contentColor   = Color.White
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Mark as Resolved", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                }
            }

        }
    }


@Composable
fun AlertSummaryBanner(total: Int, high: Int, medium: Int, low: Int) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(Green900, Green700)))
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "$total Active Alert${if (total != 1) "s" else ""}",
                fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White
            )
            Text(
                "Pull down to refresh · Tap a card to expand details",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.65f)
            )
        }
    }
}

@Composable
fun SeverityPill(label: String, bg: Color, textColor: Color) {
    Box(modifier = Modifier.clip(RoundedCornerShape(20.dp))
        .background(bg.copy(alpha = 0.25f)).padding(horizontal = 12.dp, vertical = 5.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}

@Composable
fun AlertCard(
    alert            : AlertItem,
    isExpanded       : Boolean,
    helperName       : String,
    isAdmin          : Boolean = false,
    onShowDetails    : () -> Unit,
    onNavigateToHelp : () -> Unit,
    onImOnMyWay      : () -> Unit,
    onDismiss        : () -> Unit,
    onCancelResponse : () -> Unit,
    onReportRider    : (AlertItem) -> Unit = {},
    modifier         : Modifier = Modifier
){
    var showReviewSheet by remember { mutableStateOf(false) }

    if (showReviewSheet && !alert.responderName.isNullOrBlank()) {
        HelperReviewSheet(
            responderUsername    = alert.responderName!!,
            responderDisplayName = alert.responderDisplayName
                .takeIf { it.isNotBlank() } ?: alert.responderName!!,
            onDismiss = { showReviewSheet = false }
        )
    }
    val severityColor = when (alert.severity) {
        AlertSeverity.HIGH   -> HighColor
        AlertSeverity.MEDIUM -> MedColor
        AlertSeverity.LOW    -> LowColor
    }
    val severityBg = when (alert.severity) {
        AlertSeverity.HIGH   -> HighBg
        AlertSeverity.MEDIUM -> MedBg
        AlertSeverity.LOW    -> LowBg
    }
    val severityLabel = when (alert.severity) {
        AlertSeverity.HIGH   -> "HIGH"
        AlertSeverity.MEDIUM -> "MEDIUM"
        AlertSeverity.LOW    -> "LOW"
    }

    Card(
        modifier  = modifier.fillMaxWidth()
            .animateContentSize(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        shape     = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {

            // ── Severity header strip ─────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(severityBg, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(6.dp))
                            .background(severityColor).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text(severityLabel, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                                color = Color.White, letterSpacing = 1.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Warning, null, tint = severityColor, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            alert.emergencyType,
                            fontWeight = FontWeight.Bold,
                            color      = severityColor,
                            fontSize   = 14.sp,
                            maxLines   = 1,
                            modifier   = Modifier.weight(1f, fill = false)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, null, tint = Color.Gray, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(alert.time, fontSize = 12.sp, color = Color.Gray)
                        }
                        if (alert.status == "responding") {
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1565C0))
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Default.DirectionsBike,
                                    null, tint = Color.White, modifier = Modifier.size(10.dp))
                                Text("Responding",
                                    fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }

            // ── Card body ─────────────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(severityBg),
                        contentAlignment = Alignment.Center) {
                        Text(alert.riderDisplayName.ifBlank { alert.riderName }.take(1).uppercase(),
                            fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = severityColor)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(alert.riderDisplayName.ifBlank { alert.riderName }, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = OnSurface)
                        if (!alert.contactNumber.isNullOrEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Phone, null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(alert.contactNumber, fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.LocationOn, null, tint = Color(0xFFAAAAAA),
                        modifier = Modifier.size(15.dp).padding(top = 1.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(alert.locationName, fontSize = 13.sp, color = Color.DarkGray,
                        lineHeight = 18.sp, modifier = Modifier.weight(1f))
                }

                // ── Photo indicator pill — shown when collapsed, photo exists ─
                if (!alert.photoUrl.isNullOrEmpty() && !isExpanded) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF3F3F3))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.Image, null,
                            tint     = Color(0xFF9E9E9E),
                            modifier = Modifier.size(13.dp))
                        Text("Photo attached — tap Show details to view",
                            fontSize = 11.sp, color = Color(0xFF9E9E9E),
                            fontWeight = FontWeight.Medium)
                    }
                }

                if (isExpanded) {
                    // ── Expanded photo — only shown when details are expanded ─────────────
                    if (!alert.photoUrl.isNullOrEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        var expandedImageRevealed by remember { mutableStateOf(false) }
                        var isImageLoading        by remember { mutableStateOf(true) }
                        var reported              by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 260.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF0F0F0))
                        ) {
                            // ── Image (always in tree, blur controlled by reveal state) ──
                            AsyncImage(
                                model              = alert.photoUrl,
                                contentDescription = "SOS photo",
                                modifier           = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 180.dp, max = 260.dp)
                                    .then(if (!expandedImageRevealed) Modifier.blur(20.dp) else Modifier),
                                contentScale = ContentScale.Crop,
                                onLoading = { isImageLoading = true },
                                onSuccess = { isImageLoading = false },
                                onError   = { isImageLoading = false }
                            )

                            // ── Loading spinner — shown while image is fetching ───────────
                            if (isImageLoading) {
                                Box(
                                    modifier         = Modifier
                                        .matchParentSize()
                                        .background(Color(0xFFF0F0F0)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color       = Green900,
                                            modifier    = Modifier.size(28.dp),
                                            strokeWidth = 2.5.dp
                                        )
                                        Text("Loading photo…",
                                            fontSize   = 11.sp,
                                            color      = Color(0xFF9E9E9E),
                                            fontWeight = FontWeight.Medium)
                                    }
                                }
                            }

                            // ── Blur/reveal overlay — shown after image loads ─────────────
                            if (!isImageLoading && !expandedImageRevealed) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(Color.Black.copy(alpha = 0.45f))
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication        = null
                                        ) { expandedImageRevealed = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.VisibilityOff, null,
                                            tint     = Color.White,
                                            modifier = Modifier.size(26.dp))
                                        Text("Photo may be sensitive",
                                            fontSize   = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color      = Color.White)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White.copy(alpha = 0.2f))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("Tap to reveal",
                                                fontSize = 11.sp, color = Color.White)
                                        }
                                    }
                                }
                            }

                            // ── Report button — only visible after image is revealed ──────
                            if (!isImageLoading && expandedImageRevealed) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (reported) Color(0xFF2E7D32).copy(alpha = 0.85f)
                                            else Color.Black.copy(alpha = 0.5f)
                                        )
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication        = null
                                        ) {
                                            if (!reported) {
                                                val db = FirebaseFirestore.getInstance()
                                                db.collection("reportedImages")
                                                    .add(hashMapOf(
                                                        "alertId"    to alert.id,
                                                        "photoUrl"   to (alert.photoUrl ?: ""),
                                                        "reportedBy" to helperName,
                                                        "timestamp"  to System.currentTimeMillis()
                                                    ))
                                                    .addOnSuccessListener {
                                                        reported = true
                                                        db.collection("reportedImages")
                                                            .whereEqualTo("alertId", alert.id)
                                                            .get()
                                                            .addOnSuccessListener { snap ->
                                                                val reportCount = snap.size()
                                                                if (reportCount >= 3) {
                                                                    db.collection("alerts")
                                                                        .document(alert.id)
                                                                        .update("photoUrl", "")
                                                                        .addOnSuccessListener {
                                                                            db.collection("notifications").add(hashMapOf(
                                                                                "userName"  to "Admin",
                                                                                "message"   to "⚠️ Photo on ${alert.riderName}'s ${alert.emergencyType} alert was auto-hidden after 3 reports. Review alert ID: ${alert.id}",
                                                                                "type"      to "alert",
                                                                                "timestamp" to System.currentTimeMillis(),
                                                                                "read"      to false
                                                                            ))
                                                                        }
                                                                } else {
                                                                    db.collection("notifications").add(hashMapOf(
                                                                        "userName"  to "Admin",
                                                                        "message"   to "🚩 $helperName reported a photo on ${alert.riderName}'s ${alert.emergencyType} alert. ($reportCount/3 reports)",
                                                                        "type"      to "alert",
                                                                        "timestamp" to System.currentTimeMillis(),
                                                                        "read"      to false
                                                                    ))
                                                                }
                                                            }
                                                    }
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            if (reported) Icons.Default.Check else Icons.Default.Flag,
                                            null,
                                            tint     = Color.White,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            if (reported) "Reported" else "Report",
                                            fontSize   = 10.sp,
                                            color      = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Additional details text ───────────────────────────────────────────
                    if (!alert.additionalDetails.isNullOrEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF7F7F7))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Info, null, tint = Color.Gray,
                                    modifier = Modifier.size(15.dp).padding(top = 1.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(alert.additionalDetails, fontSize = 13.sp,
                                    color = Color.DarkGray, lineHeight = 18.sp)
                            }
                        }
                    }

                    // ── Show nothing extra message if neither exists ──────────────────────
                    if (alert.photoUrl.isNullOrEmpty() && alert.additionalDetails.isNullOrEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF7F7F7))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No additional details provided.",
                                fontSize = 12.sp, color = Color.Gray,
                                fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFF0F0F0), thickness = 0.5.dp)
                Spacer(Modifier.height(10.dp))

                // ── Three-state action area ───────────────────────────────────
                if (!isAdmin) {
                    val isClaimedByMe    = alert.status == "responding" && alert.responderName == helperName
                    val isClaimedByOther = alert.status == "responding" &&
                            !alert.responderName.isNullOrBlank() &&
                            alert.responderName != helperName

                    when {
                        // ── State 1: You are the responder ───────────────────
                        isClaimedByMe -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFF57C00).copy(alpha = 0.10f))
                                        .border(1.dp, Color(0xFFF57C00).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(36.dp).clip(CircleShape)
                                            .background(Color(0xFFF57C00).copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.DirectionsBike, null,
                                            tint = Color(0xFFF57C00), modifier = Modifier.size(18.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("You are responding",
                                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                            color = Color(0xFFF57C00))
                                        Text("Navigate to ${alert.riderDisplayName.ifBlank { alert.riderName }}",
                                            fontSize = 11.sp, color = Color(0xFF455A64))
                                    }
                                }
                                OutlinedButton(
                                    onClick  = onCancelResponse,
                                    modifier = Modifier.fillMaxWidth().height(44.dp),
                                    shape    = RoundedCornerShape(12.dp),
                                    border   = BorderStroke(1.dp, Color(0xFFDDDDDD)),
                                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
                                ) {
                                    Icon(Icons.Default.Cancel, null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Cancel Response", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                // ── Report rider option ───────────────────────
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication        = null
                                        ) {
                                            onReportRider(alert)
                                        }
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.Flag, null,
                                        tint     = Color(0xFFD32F2F).copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        "Report this rider",
                                        fontSize   = 12.sp,
                                        color      = Color(0xFFD32F2F).copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // ── State 2: Someone else is responding ───────────────
                        isClaimedByOther -> {
                            val responderDisplay = alert.responderDisplayName
                                .takeIf { it.isNotBlank() } ?: alert.responderName ?: "Someone"
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1565C0).copy(alpha = 0.08f))
                                    .border(1.dp, Color(0xFF1565C0).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(36.dp).clip(CircleShape)
                                        .background(Color(0xFF1565C0).copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.DirectionsBike, null,
                                        tint = Color(0xFF1565C0), modifier = Modifier.size(18.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("$responderDisplay is responding",
                                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1565C0), maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(3.dp))
                                    Row(
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("On the way to ${alert.riderDisplayName.ifBlank { alert.riderName }}",
                                            fontSize = 11.sp, color = Color(0xFF455A64))
                                        if (!alert.responderName.isNullOrBlank()) {
                                            HelperRatingBadge(
                                                responderUsername = alert.responderName!!,
                                                onClick           = { showReviewSheet = true }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ── State 3: Unclaimed — offer to respond ─────────────
                        else -> {
                            Button(
                                onClick  = onImOnMyWay,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape    = RoundedCornerShape(12.dp),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1565C0),
                                    contentColor   = Color.White
                                )
                            ) {
                                Icon(Icons.Default.DirectionsBike, null,
                                    modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("I'm On My Way",
                                    fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (!alert.additionalDetails.isNullOrEmpty() || !alert.photoUrl.isNullOrEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick  = onShowDetails,
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape    = RoundedCornerShape(10.dp),
                        border   = BorderStroke(1.dp, Green900.copy(alpha = 0.4f)),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Green900),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null, modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isExpanded) "Show less" else "Show details",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                        if (!isExpanded && !alert.photoUrl.isNullOrEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.Image, null,
                                tint     = Green900.copy(alpha = 0.6f),
                                modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }
        }
    }
}

fun formatAlertTime(timestamp: Long): String {
    if (timestamp == 0L) return "Unknown time"
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000     -> "Just now"
        diff < 3_600_000  -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
@Composable
private fun ShimmerAlertCard(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 0.7f,
        animationSpec = infiniteRepeatable(
            animation  = androidx.compose.animation.core.tween(900),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    val shimmer = Color(0xFFE0E0E0).copy(alpha = shimmerAlpha)

    Card(
        modifier  = modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        shape     = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Header strip
            Box(
                modifier = Modifier.fillMaxWidth().height(42.dp)
                    .background(shimmer, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Avatar + name row
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(shimmer))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.width(120.dp).height(12.dp)
                            .clip(RoundedCornerShape(6.dp)).background(shimmer))
                        Box(modifier = Modifier.width(80.dp).height(10.dp)
                            .clip(RoundedCornerShape(6.dp)).background(shimmer))
                    }
                }
                // Location row
                Box(modifier = Modifier.fillMaxWidth().height(10.dp)
                    .clip(RoundedCornerShape(6.dp)).background(shimmer))
                Box(modifier = Modifier.width(200.dp).height(10.dp)
                    .clip(RoundedCornerShape(6.dp)).background(shimmer))
                Spacer(Modifier.height(4.dp))
                // Button placeholders
                Box(modifier = Modifier.fillMaxWidth().height(50.dp)
                    .clip(RoundedCornerShape(12.dp)).background(shimmer))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f).height(48.dp)
                        .clip(RoundedCornerShape(12.dp)).background(shimmer))
                    Box(modifier = Modifier.weight(1f).height(48.dp)
                        .clip(RoundedCornerShape(12.dp)).background(shimmer))
                }
            }
        }
    }
}
