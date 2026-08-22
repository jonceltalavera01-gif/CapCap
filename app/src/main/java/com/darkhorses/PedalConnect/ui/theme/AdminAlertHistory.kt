package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.*

data class AdminAlertRecord(
    val id: String = "",
    val riderName: String = "",
    val riderDisplayName: String = "",
    val emergencyType: String = "",
    val locationName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = 0L,
    val status: String = "active",
    val responderName: String = "",
    val responderDisplayName: String = "",
    val additionalDetails: String = "",
    val contactNumber: String = "",
    val photoUrl: String = "",
    val ratingGiven: Boolean = false,
    val ratingValue: Int? = null,
    val ratingReview: String = ""
)

// ── Design tokens — mirrors AdminScreen.kt's palette ──────────────────────────
private val AHGreen900 = Color(0xFF06402B)
private val AHGreen50  = Color(0xFFF0FAF5)
private val AHRed      = Color(0xFFD32F2F)
private val AHRedLight = Color(0xFFFFEBEE)
private val AHAmber    = Color(0xFFF59E0B)
private val AHBlue     = Color(0xFF1565C0)
private val AHBlueBg   = Color(0xFFE3F2FD)
private val AHWhite    = Color(0xFFFFFFFF)
private val AHOnSurf   = Color(0xFF111827)
private val AHMuted    = Color(0xFF6B7280)
private val AHDivider  = Color(0xFFE5E7EB)

private fun statusColor(status: String): Color = when (status) {
    "resolved"   -> AHGreen900
    "responding" -> AHBlue
    else         -> AHRed
}
private fun statusBg(status: String): Color = when (status) {
    "resolved"   -> AHGreen50
    "responding" -> AHBlueBg
    else         -> AHRedLight
}
private fun statusLabel(status: String): String = when (status) {
    "resolved"   -> "Resolved"
    "responding" -> "Responding"
    else         -> "Active"
}

private fun formatAlertHistoryTime(timestamp: Long): String {
    if (timestamp == 0L) return "Unknown time"
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000     -> "Just now"
        diff < 3_600_000  -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}

@Composable
fun AdminAlertHistorySection(
    alerts     : List<AdminAlertRecord>,
    isLoading  : Boolean,
    expandedId : String?,
    onToggle   : (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Text("Alert History", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = AHOnSurf)
            Text(
                if (isLoading) "Loading…" else "${alerts.size} alert${if (alerts.size != 1) "s" else ""} sent, all statuses",
                fontSize = 12.sp, color = AHMuted
            )
        }

        if (isLoading) {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), Alignment.Center) {
                CircularProgressIndicator(color = AHGreen900, strokeWidth = 2.5.dp, modifier = Modifier.size(32.dp))
            }
        } else if (alerts.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(AHGreen50), Alignment.Center) {
                    Icon(Icons.Default.History, null, tint = AHGreen900, modifier = Modifier.size(28.dp))
                }
                Text("No alerts sent yet", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AHOnSurf)
                Text("Every SOS alert will appear here.", fontSize = 13.sp, color = AHMuted, textAlign = TextAlign.Center)
            }
        } else {
            alerts.forEach { alert ->
                AdminAlertHistoryCard(
                    alert     = alert,
                    expanded  = expandedId == alert.id,
                    onToggle  = { onToggle(alert.id) }
                )
            }
        }
    }
}

@Composable
private fun AdminAlertHistoryCard(
    alert    : AdminAlertRecord,
    expanded : Boolean,
    onToggle : () -> Unit
) {
    val riderLabel    = alert.riderDisplayName.ifBlank { alert.riderName }.ifBlank { "Unknown rider" }
    val responderLabel = alert.responderDisplayName.ifBlank { alert.responderName }

    Card(
        modifier  = Modifier.fillMaxWidth().animateContentSize(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = AHWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // ── Status header strip ────────────────────────────────────────
            Box(
                Modifier.fillMaxWidth()
                    .background(statusBg(alert.status), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Warning, null, tint = statusColor(alert.status), modifier = Modifier.size(13.dp))
                        Text(alert.emergencyType.ifBlank { "Alert" }, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, color = statusColor(alert.status),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(statusColor(alert.status))
                            .padding(horizontal = 7.dp, vertical = 2.dp)) {
                            Text(statusLabel(alert.status), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                        Text(formatAlertHistoryTime(alert.timestamp), fontSize = 11.sp, color = AHMuted)
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // ── Rider row ───────────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(statusBg(alert.status)), Alignment.Center) {
                        Text(riderLabel.take(1).uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = statusColor(alert.status))
                    }
                    Column {
                        Text(riderLabel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AHOnSurf)
                        if (alert.contactNumber.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Default.Phone, null, tint = AHMuted, modifier = Modifier.size(11.dp))
                                Text(alert.contactNumber, fontSize = 11.sp, color = AHMuted)
                            }
                        }
                    }
                }

                // ── Location ────────────────────────────────────────────────
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.LocationOn, null, tint = AHMuted, modifier = Modifier.size(14.dp).padding(top = 1.dp))
                    Text(alert.locationName.ifBlank { "Unknown Location" }, fontSize = 12.sp, color = Color(0xFF374151),
                        lineHeight = 17.sp, modifier = Modifier.weight(1f))
                }

                // ── Responder row — only if claimed ───────────────────────────
                if (responderLabel.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.DirectionsBike, null, tint = AHBlue, modifier = Modifier.size(14.dp))
                        Text(
                            if (alert.status == "resolved") "Helped by $responderLabel" else "Responding: $responderLabel",
                            fontSize = 12.sp, color = AHBlue, fontWeight = FontWeight.Medium
                        )
                    }
                }

                // ── Rating — only if given ─────────────────────────────────────
                if (alert.ratingGiven && alert.ratingValue != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(5) { i ->
                            Icon(
                                if (i < alert.ratingValue) Icons.Default.Star else Icons.Default.StarBorder,
                                null,
                                tint = if (i < alert.ratingValue) Color(0xFFF57C00) else Color(0xFFDDDDDD),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text("(${alert.ratingValue}/5)", fontSize = 11.sp, color = AHMuted,
                            modifier = Modifier.padding(start = 4.dp))
                    }
                }

                // ── Expand toggle ───────────────────────────────────────────────
                val hasExtra = alert.additionalDetails.isNotBlank() || alert.photoUrl.isNotBlank() ||
                        alert.ratingReview.isNotBlank()
                if (hasExtra) {
                    HorizontalDivider(color = AHDivider, thickness = 0.5.dp)

                    if (expanded) {
                        if (alert.additionalDetails.isNotBlank()) {
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF7F7F7)).padding(12.dp)) {
                                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Info, null, tint = AHMuted, modifier = Modifier.size(14.dp).padding(top = 1.dp))
                                    Text(alert.additionalDetails, fontSize = 12.sp, color = Color(0xFF374151), lineHeight = 17.sp)
                                }
                            }
                        }
                        if (alert.ratingReview.isNotBlank()) {
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFFF7ED)).padding(12.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("RIDER REVIEW", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFF57C00), letterSpacing = 0.6.sp)
                                    Text(alert.ratingReview, fontSize = 12.sp, color = Color(0xFF374151), lineHeight = 17.sp)
                                }
                            }
                        }
                        if (alert.photoUrl.isNotBlank()) {
                            var revealed by remember(alert.id) { mutableStateOf(false) }
                            Box(
                                Modifier.fillMaxWidth().height(180.dp)
                                    .clip(RoundedCornerShape(10.dp)).background(Color(0xFFF3F4F6))
                            ) {
                                AsyncImage(
                                    model = alert.photoUrl, contentDescription = "Alert photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().then(if (!revealed) Modifier.blur(20.dp) else Modifier)
                                )
                                if (!revealed) {
                                    Box(
                                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                                            .clickable { revealed = true },
                                        Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Default.VisibilityOff, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                            Text("Tap to reveal", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    androidx.compose.material3.TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (expanded) "Show less" else "Show details", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}