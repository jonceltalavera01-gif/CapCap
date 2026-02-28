package com.darkhorses.RideSafe.ui.theme

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.*

data class AlertItem(
    val id: String = "", // Added unique ID
    val riderName: String,
    val emergencyType: String,
    val locationName: String,
    val coordinates: GeoPoint,
    val time: String,
    val severity: AlertSeverity = AlertSeverity.HIGH,
    val additionalDetails: String? = null,
    val contactNumber: String? = null
)

enum class AlertSeverity {
    HIGH, MEDIUM, LOW
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    paddingValues: PaddingValues,
    helperName: String, // Name of the person using this screen
    onViewOnMap: (GeoPoint) -> Unit,
    onNavigateToHelp: (GeoPoint) -> Unit,
    onImOnMyWay: (AlertItem) -> Unit,
    onDismiss: (AlertItem) -> Unit
) {
    var isLoading by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }
    var showFilterMenu by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var expandedAlertId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val alerts = remember { mutableStateListOf<AlertItem>() }

    // Real-time listener for Firestore alerts
    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("alerts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    errorMessage = "Error: ${e.message}"
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    alerts.clear()
                    for (doc in snapshot.documents) {
                        try {
                            val lat = doc.getDouble("latitude") ?: 0.0
                            val lon = doc.getDouble("longitude") ?: 0.0
                            val name = doc.getString("riderName") ?: "Unknown Rider"
                            val type = doc.getString("emergencyType") ?: "Emergency"
                            val locName = doc.getString("locationName") ?: "Unknown Location"
                            val timestamp = doc.getLong("timestamp") ?: 0L
                            val severityStr = doc.getString("severity") ?: "HIGH"
                            val details = doc.getString("additionalDetails")
                            val contact = doc.getString("contactNumber")

                            val severity = try {
                                AlertSeverity.valueOf(severityStr.uppercase())
                            } catch (ex: Exception) {
                                AlertSeverity.HIGH
                            }

                            alerts.add(AlertItem(
                                id = doc.id, // Use document ID
                                riderName = name,
                                emergencyType = type,
                                locationName = locName,
                                coordinates = GeoPoint(lat, lon),
                                time = formatAlertTime(timestamp),
                                severity = severity,
                                additionalDetails = details,
                                contactNumber = contact
                            ))
                        } catch (ex: Exception) {
                            // Skip malformed documents
                        }
                    }
                }
            }
    }

    // Filter alerts based on selected filter
    val filteredAlerts = when (selectedFilter) {
        "High" -> alerts.filter { it.severity == AlertSeverity.HIGH }
        "Medium" -> alerts.filter { it.severity == AlertSeverity.MEDIUM }
        "Low" -> alerts.filter { it.severity == AlertSeverity.LOW }
        else -> alerts.toList()
    }

    // Clear messages after a delay
    LaunchedEffect(successMessage, errorMessage) {
        if (successMessage != null || errorMessage != null) {
            delay(3000)
            successMessage = null
            errorMessage = null
        }
    }

    // Function to send notification to the rider who needs help
    fun sendHelpNotification(distressedRider: String, message: String) {
        val db = FirebaseFirestore.getInstance()
        val notification = hashMapOf(
            "userName" to distressedRider, // Recipient
            "message" to message,
            "type" to "accepted",
            "timestamp" to System.currentTimeMillis(),
            "read" to false
        )
        db.collection("notifications").add(notification)
    }

    // Function to fetch alerts from backend (simulated)
    fun fetchAlerts() {
        isLoading = true
        errorMessage = null

        scope.launch {
            try {
                // Simulate network call
                delay(1500)
                successMessage = "Alerts refreshed successfully"
            } catch (e: Exception) {
                errorMessage = "Failed to fetch alerts: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // Function to clear filter
    fun clearFilter() {
        try {
            selectedFilter = "All"
            successMessage = "Filter cleared"
        } catch (e: Exception) {
            errorMessage = "Error clearing filter: ${e.message}"
        }
    }

    // Function to show alert details
    fun showAlertDetails(alertId: String) {
        try {
            expandedAlertId = if (expandedAlertId == alertId) null else alertId
        } catch (e: Exception) {
            errorMessage = "Error showing alert details: ${e.message}"
        }
    }

    // Function to handle "Navigate to Help" action
    fun handleNavigateToHelp(alert: AlertItem) {
        scope.launch {
            try {
                isLoading = true
                
                // Notify the rider
                sendHelpNotification(alert.riderName, "$helperName is navigating to your location to help!")

                delay(500) // Simulate preparation
                onNavigateToHelp(alert.coordinates)
                successMessage = "Navigating to help ${alert.riderName}"
            } catch (e: Exception) {
                errorMessage = "Failed to navigate: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // Function to handle "I'm On My Way" action
    fun handleImOnMyWay(alert: AlertItem) {
        scope.launch {
            try {
                isLoading = true
                
                // Notify the rider
                sendHelpNotification(alert.riderName, "$helperName said they are on their way to help you!")

                delay(500) // Simulate notification
                onImOnMyWay(alert)
                successMessage = "You're on your way to help ${alert.riderName}"
            } catch (e: Exception) {
                errorMessage = "Failed to send notification: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // Function to handle "Dismiss" action
    fun handleDismiss(alert: AlertItem) {
        scope.launch {
            try {
                isLoading = true
                delay(500) // Simulate API call

                onDismiss(alert)
                alerts.remove(alert)
                successMessage = "Alert from ${alert.riderName} dismissed"

                // Clear expanded state if this alert was expanded
                if (expandedAlertId == alert.id) {
                    expandedAlertId = null
                }
            } catch (e: Exception) {
                errorMessage = "Failed to dismiss alert: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Alerts",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF333333)
                ),
                actions = {
                    // Filter button
                    IconButton(
                        onClick = {
                            try {
                                showFilterMenu = true
                            } catch (e: Exception) {
                                errorMessage = "Error opening filter: ${e.message}"
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Refresh button
                    IconButton(
                        onClick = { fetchAlerts() },
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                windowInsets = WindowInsets(0.dp)
            )
        },
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFAAB7AE))
        ) {
            // Filter Dropdown Menu
            DropdownMenu(
                expanded = showFilterMenu,
                onDismissRequest = {
                    try {
                        showFilterMenu = false
                    } catch (e: Exception) {
                        errorMessage = "Error closing filter: ${e.message}"
                    }
                },
                modifier = Modifier
                    .background(Color.White)
                    .padding(vertical = 4.dp)
            ) {
                listOf("All", "High", "Medium", "Low").forEach { filter ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (filter != "All") {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (filter) {
                                                    "High" -> Color.Red
                                                    "Medium" -> Color(0xFFFFA500)
                                                    else -> Color(0xFF4CAF50)
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = filter,
                                    fontWeight = if (selectedFilter == filter) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        },
                        onClick = {
                            try {
                                selectedFilter = filter
                                showFilterMenu = false
                                successMessage = "Filtered by: $filter"
                            } catch (e: Exception) {
                                errorMessage = "Error applying filter: ${e.message}"
                            }
                        }
                    )
                }
            }

            // Main content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp)
            ) {
                // Filter info card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.95f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedFilter == "All")
                                    "All Alerts (${filteredAlerts.size})"
                                else
                                    "$selectedFilter Priority Alerts (${filteredAlerts.size})",
                                fontSize = 14.sp,
                                color = Color(0xFF333333),
                                fontWeight = FontWeight.Medium
                            )

                            if (selectedFilter != "All") {
                                AssistChip(
                                    onClick = { clearFilter() },
                                    label = {
                                        Text(
                                            text = "Clear Filter",
                                            fontSize = 12.sp,
                                            color = Color.White
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color(0xFF506D45)
                                    )
                                )
                            }
                        }
                    }
                }

                // Empty state
                if (filteredAlerts.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.95f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFF506D45),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "No alerts found",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF333333)
                                    )
                                    Text(
                                        text = "Try changing your filter",
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(filteredAlerts, key = { it.id }) { alert -> // Fixed key
                        AlertCard(
                            alert = alert,
                            isExpanded = expandedAlertId == alert.id,
                            onViewOnMap = onViewOnMap,
                            onShowDetails = { showAlertDetails(alert.id) },
                            onNavigateToHelp = { handleNavigateToHelp(alert) },
                            onImOnMyWay = { handleImOnMyWay(alert) },
                            onDismiss = { handleDismiss(alert) },
                            isLoading = isLoading
                        )
                    }
                }
            }

            // Messages overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp)
                    .padding(top = 8.dp)
            ) {
                // Success message
                if (successMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE8F5E9)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = successMessage!!,
                                color = Color(0xFF2E7D32),
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Error message
                if (errorMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFEBEE)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFD32F2F),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage!!,
                                color = Color(0xFFD32F2F),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlertCard(
    alert: AlertItem,
    isExpanded: Boolean,
    onViewOnMap: (GeoPoint) -> Unit,
    onShowDetails: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onImOnMyWay: () -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean
) {
    var showActionMenu by remember { mutableStateOf(false) }
    val severityColor = when (alert.severity) {
        AlertSeverity.HIGH -> Color.Red
        AlertSeverity.MEDIUM -> Color(0xFFFFA500)
        AlertSeverity.LOW -> Color(0xFF4CAF50)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with severity indicator and time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Severity indicator
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(severityColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // Emergency type with icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                color = severityColor.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = severityColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = alert.emergencyType,
                            fontWeight = FontWeight.Bold,
                            color = severityColor,
                            fontSize = 14.sp
                        )
                    }
                }

                // Time with icon
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = alert.time,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Rider name with icon
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFF506D45)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = alert.riderName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Location with icon
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color.Gray
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = alert.locationName,
                    fontSize = 14.sp,
                    color = Color.DarkGray,
                    modifier = Modifier.weight(1f)
                )
            }

            // Expanded details
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))

                // Additional details
                if (!alert.additionalDetails.isNullOrEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF5F5F5)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = alert.additionalDetails,
                            fontSize = 14.sp,
                            color = Color.DarkGray,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Contact info
                if (!alert.contactNumber.isNullOrEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF506D45)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = alert.contactNumber,
                            fontSize = 14.sp,
                            color = Color.DarkGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons row with Navigate to Help and dropdown menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Navigate to Help button
                Button(
                    onClick = onNavigateToHelp,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF506D45)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.Directions,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Navigate to Help",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Dropdown menu button - adjusted to match size
                Box {
                    Button(
                        onClick = { showActionMenu = true },
                        modifier = Modifier
                            .width(60.dp)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF506D45)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More actions",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showActionMenu,
                        onDismissRequest = { showActionMenu = false },
                        modifier = Modifier
                            .background(Color.White)
                            .width(200.dp)
                    ) {
                        // I'm On My Way option
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.ThumbUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = Color(0xFF2196F3)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = "I'm On My Way",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF2196F3)
                                    )
                                }
                            },
                            onClick = {
                                showActionMenu = false
                                onImOnMyWay()
                            }
                        )

                        // Dismiss option
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = Color(0xFFD32F2F)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = "Dismiss",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFD32F2F)
                                    )
                                }
                            },
                            onClick = {
                                showActionMenu = false
                                onDismiss()
                            }
                        )
                    }
                }
            }

            // More info button (collapsed/expanded)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onShowDetails,
                    enabled = !isLoading
                ) {
                    Text(
                        text = if (isExpanded) "Show less" else "Show more details",
                        color = Color(0xFF506D45),
                        fontSize = 12.sp
                    )
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF506D45)
                    )
                }
            }
        }
    }
}

fun formatAlertTime(timestamp: Long): String {
    if (timestamp == 0L) return "Unknown time"
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        else -> SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
