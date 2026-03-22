package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil

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

private const val RESOLVE_REQUEST_COOLDOWN_MS = 5 * 60 * 1000L

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
    val lastResolveRequestedAt: Long? = null
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
    var alertToDelete   by remember { mutableStateOf<AlertItem?>(null) }
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
                                lastResolveRequestedAt = doc.getLong("lastResolveRequestedAt")
                            ))
                        } catch (_: Exception) {}
                    }
                    alerts.clear()
                    alerts.addAll(unsorted.sortedByDescending { doc -> doc.time })
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
        scope.launch {
            try {
                FirebaseFirestore.getInstance()
                    .collection("alerts").document(alert.id)
                    .update(mapOf("status" to "responding", "responderName" to helperName))
                sendHelpNotification(
                    alert.riderName,
                    "$helperName is on the way to help you! Stay calm and stay visible."
                )
                onImOnMyWay(alert)
                successMessage = "Notified ${alert.riderName} you're on your way"
            } catch (e: Exception) { errorMessage = "Failed: ${e.message}" }
        }
    }

    fun requestResolve(alert: AlertItem) {
        val lastSent = alert.lastResolveRequestedAt ?: 0L
        val elapsed  = System.currentTimeMillis() - lastSent
        if (elapsed < RESOLVE_REQUEST_COOLDOWN_MS) {
            val minutesLeft = ceil((RESOLVE_REQUEST_COOLDOWN_MS - elapsed) / 60_000.0).toInt()
            errorMessage = "Please wait $minutesLeft more minute${if (minutesLeft != 1) "s" else ""} before re-sending."
            alertToConfirm = null
            return
        }
        val now = System.currentTimeMillis()
        scope.launch {
            try {
                FirebaseFirestore.getInstance()
                    .collection("alerts").document(alert.id)
                    .update(mapOf(
                        "status"                 to "resolve_requested",
                        "responderName"          to helperName,
                        "lastResolveRequestedAt" to now
                    ))
                    .addOnSuccessListener {
                        alertToConfirm = null
                        // Write alertId so NotificationsScreen can show inline confirm buttons
                        FirebaseFirestore.getInstance().collection("notifications").add(hashMapOf(
                            "userName"  to alert.riderName,
                            "message"   to "$helperName believes your ${alert.emergencyType} has been resolved. Confirm below.",
                            "type"      to "resolve_requested",
                            "alertId"   to alert.id,
                            "timestamp" to System.currentTimeMillis(),
                            "read"      to false
                        ))
                        successMessage = "Confirmation request sent to ${alert.riderName}"
                    }
                    .addOnFailureListener { e ->
                        errorMessage   = "Failed: ${e.message}"
                        alertToConfirm = null
                    }
                onDismiss(alert)
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
                        successMessage = "Alert closed. Glad the situation has been handled!"
                        if (!alert.responderName.isNullOrEmpty()) {
                            sendHelpNotification(
                                alert.responderName,
                                "${alert.riderName} confirmed the ${alert.emergencyType} has been resolved. Thank you for helping!"
                            )
                        }
                    }
                    .addOnFailureListener { e -> errorMessage = "Failed: ${e.message}" }
            } catch (e: Exception) { errorMessage = "Failed: ${e.message}" }
        }
    }

    fun rejectResolve(alert: AlertItem) {
        scope.launch {
            try {
                FirebaseFirestore.getInstance()
                    .collection("alerts").document(alert.id)
                    .update("status", "active")
                    .addOnSuccessListener {
                        successMessage = "Alert re-activated. Help is still being arranged."
                        if (!alert.responderName.isNullOrEmpty()) {
                            sendHelpNotification(
                                alert.responderName,
                                "${alert.riderName} indicated they still need help with ${alert.emergencyType}."
                            )
                        }
                    }
                    .addOnFailureListener { e -> errorMessage = "Failed: ${e.message}" }
            } catch (e: Exception) { errorMessage = "Failed: ${e.message}" }
        }
    }

    fun deleteOwnAlert(alert: AlertItem) {
        scope.launch {
            try {
                FirebaseFirestore.getInstance()
                    .collection("alerts").document(alert.id)
                    .delete()
                    .addOnSuccessListener {
                        alerts.remove(alert)
                        alertToDelete  = null
                        successMessage = "Alert removed."
                    }
                    .addOnFailureListener { e ->
                        errorMessage  = "Failed to remove: ${e.message}"
                        alertToDelete = null
                    }
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
                                onStillNeedHelp   = { rejectResolve(ownAlert) },
                                onDeleteAlert     = { alertToDelete = ownAlert },
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
        val lastSent   = alert.lastResolveRequestedAt ?: 0L
        val elapsed    = System.currentTimeMillis() - lastSent
        val onCooldown = elapsed < RESOLVE_REQUEST_COOLDOWN_MS
        val minsLeft   = if (onCooldown)
            ceil((RESOLVE_REQUEST_COOLDOWN_MS - elapsed) / 60_000.0).toInt() else 0

        RequestResolveDialog(
            alert      = alert,
            helperName = helperName,
            onCooldown = onCooldown,
            minsLeft   = minsLeft,
            onConfirm  = { requestResolve(alert) },
            onDismiss  = { alertToConfirm = null }
        )
    }

    alertToDelete?.let { alert ->
        AlertDialog(
            onDismissRequest = { alertToDelete = null },
            shape            = RoundedCornerShape(20.dp),
            containerColor   = Color.White,
            icon = {
                Box(
                    modifier         = Modifier.size(56.dp).clip(CircleShape)
                        .background(Color(0xFFFFEBEE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.DeleteForever, null,
                        tint = HighColor, modifier = Modifier.size(28.dp))
                }
            },
            title = {
                Text("Remove Alert?",
                    fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                    textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier   = Modifier.fillMaxWidth())
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier            = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "This will remove your \"${alert.emergencyType}\" alert. Only do this if it was sent by mistake.",
                        fontSize = 14.sp, color = Color.Gray, lineHeight = 20.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        elevation = CardDefaults.cardElevation(0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Info, null, tint = MedColor, modifier = Modifier.size(16.dp))
                            Text("Keep the alert active if the issue hasn't been addressed.",
                                fontSize = 12.sp, color = Color(0xFF5D4037), lineHeight = 17.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick  = { deleteOwnAlert(alert) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = HighColor)
                    ) {
                        Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Yes, Remove Alert", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    OutlinedButton(
                        onClick  = { alertToDelete = null },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape    = RoundedCornerShape(12.dp),
                        border   = BorderStroke(1.dp, Color(0xFFDDDDDD))
                    ) {
                        Text("Keep Alert", color = Color.Gray, fontSize = 14.sp)
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
    onStillNeedHelp   : () -> Unit,
    onDeleteAlert     : () -> Unit,
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
                    Text(
                        when (alert.status) {
                            "responding"        -> "${alert.responderName ?: "Someone"} is on the way to help you"
                            "resolve_requested" -> "${alert.responderName ?: "A helper"} marked this resolved — confirm below"
                            else                -> "Waiting for a nearby cyclist to respond..."
                        },
                        fontSize = 12.sp, color = Color(0xFF455A64)
                    )
                }
                IconButton(onClick = onDeleteAlert, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove accidental alert",
                        tint = Color(0xFF90A4AE), modifier = Modifier.size(20.dp))
                }
            }

            if (alert.status == "resolve_requested") {
                HorizontalDivider(color = Color(0xFFBBDEFB))
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFF3E0)).padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HelpOutline, null,
                            tint = MedColor, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Has this been resolved?", fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp, color = MedColor)
                            Text(
                                "${alert.responderName ?: "A helper"} marked your ${alert.emergencyType} as resolved. " +
                                        "Please confirm if the situation has been taken care of.",
                                fontSize = 11.sp, color = Color(0xFF5D4037), lineHeight = 15.sp
                            )
                        }
                    }
                }
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick  = onConfirmResolved,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White),
                        shape    = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Resolved", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick  = onStillNeedHelp,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                        border   = BorderStroke(1.5.dp, HighColor),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = HighColor),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Need Help", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
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
    onShowDetails    : () -> Unit,
    onNavigateToHelp : () -> Unit,
    onImOnMyWay      : () -> Unit,
    onDismiss        : () -> Unit,
    modifier         : Modifier = Modifier
) {
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
                        if (alert.status == "responding" || alert.status == "resolve_requested") {
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (alert.status == "resolve_requested") Color(0xFFF57C00)
                                        else Color(0xFF1565C0)
                                    )
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    if (alert.status == "resolve_requested") Icons.Default.HelpOutline
                                    else Icons.Default.DirectionsBike,
                                    null, tint = Color.White, modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    if (alert.status == "resolve_requested") "Awaiting Confirm" else "Responding",
                                    fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White
                                )
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

                    if (!alert.photoUrl.isNullOrEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        AsyncImage(
                            model      = alert.photoUrl,
                            contentDescription = "SOS Attachment",
                            modifier   = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF7F7F7)),
                            contentScale = ContentScale.Crop
                        )
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
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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

                if (!alert.additionalDetails.isNullOrEmpty() || !alert.photoUrl.isNullOrEmpty()) {
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

@Composable
private fun RequestResolveDialog(
    alert      : AlertItem,
    helperName : String,
    onCooldown : Boolean,
    minsLeft   : Int,
    onConfirm  : () -> Unit,
    onDismiss  : () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape)
                    .background(if (onCooldown) Color(0xFFFFF3E0) else LowBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (onCooldown) Icons.Default.HourglassTop else Icons.Default.CheckCircle,
                    null,
                    tint     = if (onCooldown) MedColor else LowColor,
                    modifier = Modifier.size(30.dp)
                )
            }
        },
        title = {
            Text(
                if (onCooldown) "Request Already Sent" else "Request Confirmation?",
                fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
                modifier   = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier            = Modifier.fillMaxWidth()
            ) {
                if (onCooldown) {
                    Text("A confirmation request was already sent to",
                        fontSize = 14.sp, color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(alert.riderName, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                        color = OnSurface, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        elevation = CardDefaults.cardElevation(0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Timer, null, tint = MedColor, modifier = Modifier.size(16.dp))
                            Text(
                                "You can resend in $minsLeft minute${if (minsLeft != 1) "s" else ""}. Give them a moment to respond.",
                                fontSize = 12.sp, color = Color(0xFF5D4037), lineHeight = 17.sp
                            )
                        }
                    }
                } else {
                    Text("You're about to request that",
                        fontSize = 14.sp, color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(alert.riderName, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                        color = OnSurface, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("confirms their ${alert.emergencyType} has been resolved.",
                        fontSize = 14.sp, color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        elevation = CardDefaults.cardElevation(0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Notifications, null, tint = MedColor, modifier = Modifier.size(16.dp))
                            Text(
                                "${alert.riderName} will be notified and must confirm before the alert is closed.",
                                fontSize = 12.sp, color = Color(0xFF5D4037), lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick  = { if (!onCooldown) onConfirm() },
                    enabled  = !onCooldown,
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = if (onCooldown) Color(0xFFBDBDBD) else LowColor,
                        contentColor           = Color.White,
                        disabledContainerColor = Color(0xFFBDBDBD),
                        disabledContentColor   = Color.White
                    ),
                    shape    = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(
                        if (onCooldown) Icons.Default.HourglassTop else Icons.Default.Send,
                        null, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (onCooldown) "Wait ${minsLeft}m before resending" else "Send Confirmation",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )
                }
                OutlinedButton(
                    onClick  = onDismiss,
                    shape    = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    border   = BorderStroke(1.dp, Color(0xFFDDDDDD))
                ) {
                    Text("Cancel", color = Color.Gray, fontSize = 14.sp)
                }
            }
        },
        shape          = RoundedCornerShape(20.dp),
        containerColor = Color.White
    )
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
