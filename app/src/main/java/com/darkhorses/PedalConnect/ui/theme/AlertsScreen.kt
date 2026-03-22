package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
    val createdAt: Long = 0L
)

enum class AlertSeverity { HIGH, MEDIUM, LOW }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    paddingValues    : PaddingValues,
    helperName       : String,
    onNavigateToHelp : (GeoPoint) -> Unit,
    onImOnMyWay      : (AlertItem) -> Unit,
    onDismiss        : (AlertItem) -> Unit
) {
    var isRefreshing    by remember { mutableStateOf(false) }
    var selectedFilter  by remember { mutableStateOf("All") }
    var errorMessage    by remember { mutableStateOf<String?>(null) }
    var successMessage  by remember { mutableStateOf<String?>(null) }
    var expandedAlertId by remember { mutableStateOf<String?>(null) }
    var alertToConfirm  by remember { mutableStateOf<AlertItem?>(null) }
    val scope  = rememberCoroutineScope()
    val alerts = remember { mutableStateListOf<AlertItem>() }

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
                                id                     = doc.id,
                                riderName              = riderName,
                                riderNameLower         = doc.getString("riderNameLower")
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
                                createdAt = doc.getLong("timestamp") ?: 0L
                            ))
                        } catch (_: Exception) {}
                    }
                    alerts.clear()
                    alerts.addAll(unsorted.sortedByDescending { doc -> doc.createdAt })
                }
            }
    }

    val currentUserLower = helperName.trim().lowercase()
    val ownAlert         = alerts.firstOrNull { it.riderNameLower == currentUserLower }
    val othersAlerts     = alerts.filter    { it.riderNameLower != currentUserLower }

    val filteredAlerts = when (selectedFilter) {
        "High"   -> othersAlerts.filter { it.severity == AlertSeverity.HIGH }
        "Medium" -> othersAlerts.filter { it.severity == AlertSeverity.MEDIUM }
        "Low"    -> othersAlerts.filter { it.severity == AlertSeverity.LOW }
        else     -> othersAlerts
    }

    LaunchedEffect(successMessage, errorMessage) {
        if (successMessage != null || errorMessage != null) {
            delay(3000); successMessage = null; errorMessage = null
        }
    }

    // ── Auto-expire stale alerts ──────────────────────────────────────────────
    // Runs on open and every 30 minutes. Closes alerts older than 2 hours
    // that still have no responder assigned, and notifies the rider.
    LaunchedEffect(Unit) {
        while (true) {
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

    fun handleNavigateToHelp(alert: AlertItem) {
        scope.launch {
            try {
                sendHelpNotification(alert.riderName, "$helperName is navigating to your location!")
                onNavigateToHelp(alert.coordinates)
                successMessage = "Navigating to ${alert.riderName}"
            } catch (e: Exception) { errorMessage = "Failed: ${e.message}" }
        }
    }

    fun handleImOnMyWay(alert: AlertItem) {
        // Guard — only claim if still unclaimed
        if (alert.status == "responding" && alert.responderName != helperName) {
            errorMessage = "${alert.responderName ?: "Someone"} is already on the way to help."
            return
        }
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
                        "status"        to "responding",
                        "responderName" to helperName
                    ))
                }.addOnSuccessListener {
                    sendHelpNotification(
                        alert.riderName,
                        "$helperName is on the way to help you! Stay calm and stay visible."
                    )
                    onImOnMyWay(alert)
                    successMessage = "Notified ${alert.riderName} you're on your way"
                }.addOnFailureListener { e ->
                    errorMessage = e.message ?: "Someone else already claimed this alert."
                }
            } catch (e: Exception) { errorMessage = "Failed: ${e.message}" }
        }
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
                        sendHelpNotification(
                            alert.riderName,
                            "$helperName has marked your ${alert.emergencyType} as resolved. Stay safe!"
                        )
                        successMessage = "Alert resolved. Thank you for helping ${alert.riderName}!"
                    }
                    .addOnFailureListener { e ->
                        errorMessage   = "Failed: ${e.message}"
                        alertToConfirm = null
                    }
            } catch (e: Exception) { errorMessage = "Failed: ${e.message}" }
        }
    }

    fun confirmSelfResolved(alert: AlertItem) {
        scope.launch {
            try {
                FirebaseFirestore.getInstance()
                    .collection("alerts").document(alert.id)
                    .update("status", "resolved")
                    .addOnSuccessListener {
                        alerts.remove(alert)
                        successMessage = "Alert closed. Stay safe out there!"
                        // Notify responder if one was assigned
                        if (!alert.responderName.isNullOrEmpty()) {
                            sendHelpNotification(
                                alert.responderName,
                                "${alert.riderName} has resolved their ${alert.emergencyType} alert. Thank you for helping!"
                            )
                        }
                        // Also delete any pending resolve_requested notifications for this alert
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


    val highCount = othersAlerts.count { it.severity == AlertSeverity.HIGH }
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
                    item { AlertSummaryBanner(othersAlerts.size, highCount, medCount, lowCount) }

                    if (ownAlert != null) {
                        item {
                            OwnAlertCard(
                                alert             = ownAlert,
                                onConfirmResolved = { confirmSelfResolved(ownAlert) },
                                modifier          = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }

                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val chips = listOf(
                                Triple("All",    null as Int?,  Green900),
                                Triple("High",   highCount,     HighColor),
                                Triple("Medium", medCount,      MedColor),
                                Triple("Low",    lowCount,      LowColor)
                            )
                            items(chips) { (label, count, color) ->
                                val selected = selectedFilter == label
                                FilterChip(
                                    selected = selected,
                                    onClick  = { selectedFilter = label },
                                    label = {
                                        Text(
                                            if (count != null) "$label ($count)" else label,
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
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(
                                if (selectedFilter == "All") "All Alerts" else "$selectedFilter Priority",
                                fontWeight = FontWeight.Bold, fontSize = 17.sp, color = OnSurface
                            )
                            Box(modifier = Modifier.clip(CircleShape).background(Green100)
                                .padding(horizontal = 10.dp, vertical = 4.dp)) {
                                Text("${filteredAlerts.size} active", fontSize = 12.sp,
                                    color = Green900, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    if (filteredAlerts.isEmpty()) {
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
                            onShowDetails    = { expandedAlertId = if (expandedAlertId == alert.id) null else alert.id },
                            onNavigateToHelp = { handleNavigateToHelp(alert) },
                            onImOnMyWay      = { handleImOnMyWay(alert) },
                            onDismiss        = { alertToConfirm = alert },
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
                    "This will close ${alert.riderName}'s ${alert.emergencyType} alert and notify them it has been resolved.",
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
                    Text("Your Active Alert", fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp, color = Color(0xFF1565C0))
                    Text(alert.emergencyType, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, color = OnSurface)
                    Spacer(Modifier.height(2.dp))
                    val waitingTooLong = alert.status == "active" &&
                            alert.createdAt > 0L &&
                            (System.currentTimeMillis() - alert.createdAt) > 5 * 60 * 1000L

                    Text(
                        when (alert.status) {
                            "responding" -> "${alert.responderName ?: "Someone"} is on the way to help you"
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
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Column {
            Text("$total Active Alert${if (total != 1) "s" else ""}",
                fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("Pull down to refresh  -  Tap a card for details",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeverityPill("$high High",  HighColor, Color.White)
                SeverityPill("$medium Med", MedColor,  Color.White)
                SeverityPill("$low Low",    LowColor,  Color.White)
            }
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
    onShowDetails    : () -> Unit,
    onNavigateToHelp : () -> Unit,
    onImOnMyWay      : () -> Unit,
    onDismiss        : () -> Unit,
    modifier         : Modifier = Modifier
){
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
                        Text(alert.riderName.take(1).uppercase(),
                            fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = severityColor)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(alert.riderName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = OnSurface)
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

                // Photo always visible — blur overlay handles sensitivity
                if (!alert.photoUrl.isNullOrEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    var imageRevealed by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF7F7F7))
                    ) {
                        AsyncImage(
                            model              = alert.photoUrl,
                            contentDescription = "SOS Attachment",
                            modifier           = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .then(if (!imageRevealed) Modifier.blur(20.dp) else Modifier),
                            contentScale = ContentScale.Crop
                        )
                        if (!imageRevealed) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication        = null
                                    ) { imageRevealed = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.VisibilityOff, null,
                                        tint = Color.White, modifier = Modifier.size(28.dp))
                                    Text("Photo may be sensitive",
                                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                        color = Color.White)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.2f))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("Tap to reveal", fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                        if (imageRevealed) {
                            var reported by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (reported) Color(0xFF2E7D32).copy(alpha = 0.8f)
                                        else Color.Black.copy(alpha = 0.5f)
                                    )
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication        = null
                                    ) {
                                        if (!reported) {
                                            val db  = FirebaseFirestore.getInstance()
                                            val reportData = hashMapOf(
                                                "alertId"    to alert.id,
                                                "photoUrl"   to (alert.photoUrl ?: ""),
                                                "reportedBy" to helperName,
                                                "timestamp"  to System.currentTimeMillis()
                                            )
                                            db.collection("reportedImages")
                                                .add(reportData)
                                                .addOnSuccessListener {
                                                    reported = true

                                                    // ── Check total reports for this alert ──
                                                    db.collection("reportedImages")
                                                        .whereEqualTo("alertId", alert.id)
                                                        .get()
                                                        .addOnSuccessListener { snap ->
                                                            val reportCount = snap.size()

                                                            // ── Auto-hide after 3 unique reports ──
                                                            if (reportCount >= 3) {
                                                                db.collection("alerts")
                                                                    .document(alert.id)
                                                                    .update("photoUrl", "")
                                                                    .addOnSuccessListener {
                                                                        // Notify admin
                                                                        db.collection("notifications")
                                                                            .add(hashMapOf(
                                                                                "userName"  to "Admin",
                                                                                "message"   to "⚠️ Photo on ${alert.riderName}'s ${alert.emergencyType} alert was auto-hidden after 3 reports. Review alert ID: ${alert.id}",
                                                                                "type"      to "alert",
                                                                                "timestamp" to System.currentTimeMillis(),
                                                                                "read"      to false
                                                                            ))
                                                                    }
                                                            } else {
                                                                // ── Notify admin of each report ──
                                                                db.collection("notifications")
                                                                    .add(hashMapOf(
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
                                        null, tint = Color.White, modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        if (reported) "Reported" else "Report",
                                        fontSize = 10.sp, color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                if (isExpanded) {
                    if (!alert.additionalDetails.isNullOrEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF7F7F7)).padding(12.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Info, null, tint = Color.Gray,
                                    modifier = Modifier.size(15.dp).padding(top = 1.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(alert.additionalDetails, fontSize = 13.sp, color = Color.DarkGray, lineHeight = 18.sp)
                            }
                        }
                    }

                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFF0F0F0), thickness = 0.5.dp)
                Spacer(Modifier.height(10.dp))

                // ── Navigate button ───────────────────────────────────────────
                Button(
                    onClick  = onNavigateToHelp,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Green900, contentColor = Color.White),
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Directions, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Navigate to Help", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(Modifier.height(8.dp))

                // ── Side-by-side action buttons — equal weight, short labels ──
                val isClaimedByOther = alert.status == "responding" &&
                        !alert.responderName.isNullOrBlank() &&
                        alert.responderName != helperName

                if (isClaimedByOther) {
                    // Alert is locked — show who claimed it, only navigate is allowed
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFE3F2FD))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                                .background(Color(0xFF1565C0).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.DirectionsBike, null,
                                tint = Color(0xFF1565C0), modifier = Modifier.size(16.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Already being helped",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = Color(0xFF1565C0))
                            Text("${alert.responderName} is on the way",
                                fontSize = 11.sp, color = Color(0xFF455A64))
                        }
                    }
                } else {
                    val isAssignedResponder = alert.status == "responding" &&
                            alert.responderName == helperName

                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // On My Way — hidden if you're already the assigned responder
                        if (!isAssignedResponder) {
                            OutlinedButton(
                                onClick  = onImOnMyWay,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape    = RoundedCornerShape(12.dp),
                                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1565C0)),
                                border   = BorderStroke(1.5.dp, Color(0xFF1565C0)),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.ThumbUp, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("On My Way", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                        // Resolved — only visible to the assigned responder
                        if (isAssignedResponder) {
                            OutlinedButton(
                                onClick  = onDismiss,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape    = RoundedCornerShape(12.dp),
                                colors   = ButtonDefaults.outlinedButtonColors(contentColor = HighColor),
                                border   = BorderStroke(1.5.dp, HighColor),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircleOutline, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Resolved", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                }

                if (!alert.additionalDetails.isNullOrEmpty()) {
                    TextButton(onClick = onShowDetails, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isExpanded) "Show less" else "Show details", color = Green900, fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null, tint = Green900, modifier = Modifier.size(16.dp))
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
