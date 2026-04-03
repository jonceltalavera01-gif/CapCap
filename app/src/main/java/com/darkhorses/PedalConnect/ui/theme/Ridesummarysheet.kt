package com.darkhorses.PedalConnect.ui.theme


import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.util.Locale

private val RSGreen900 = Color(0xFF06402B)
private val RSGreen100 = Color(0xFFE8F5E9)

// ── RideSummarySheet ──────────────────────────────────────────────────────────
// Shown after the user taps Stop on the ride tracker.
//
// Parameters:
//   userName         — current user's display name
//   distanceM        — total ride distance in metres
//   durationSeconds  — total elapsed seconds
//   maxSpeedKmh      — max speed recorded during ride
//   elevationM       — total elevation gain in metres
//   rideStartPoint   — GPS position where ride started (for saving route)
//   destinationPoint — navigated destination if set (for saving route)
//   locationPoints   — polyline points recorded during ride
//   formatTime       — shared time formatter from HomeScreen
//   onDismiss        — called when sheet closes (also triggers resetRide in HomeScreen)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideSummarySheet(
    userName        : String,
    distanceM       : Double,
    durationSeconds : Long,
    maxSpeedKmh     : Float,
    elevationM      : Double,
    rideStartPoint  : GeoPoint?,
    destinationPoint: GeoPoint?,
    locationPoints  : List<GeoPoint>,
    formatTime      : (Long) -> String,
    onDismiss       : () -> Unit
) {
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()
    val db         = FirebaseFirestore.getInstance()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val avgSpeedKmh = if (durationSeconds > 0)
        (distanceM / 1000.0) / (durationSeconds / 3600.0) else 0.0

    var postCaption by remember {
        mutableStateOf(
            "Just completed a ${
                String.format(Locale.getDefault(), "%.2f", distanceM / 1000.0)
            } km ride in ${formatTime(durationSeconds)}! 🚴"
        )
    }
    var isPosting  by remember { mutableStateOf(false) }
    var saveRoute  by remember { mutableStateOf(false) }
    var routeName  by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = Color.White,
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(RSGreen100),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.DirectionsBike, null,
                        tint = RSGreen900, modifier = Modifier.size(28.dp))
                }
                Column {
                    Text("Ride Complete! 🎉", fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp, color = RSGreen900)
                    Text("Great effort, $userName!", fontSize = 13.sp,
                        color = Color(0xFF7A8F7A))
                }
            }

            HorizontalDivider(color = Color(0xFFE8EDE8))

            // ── Primary stats — distance + duration ───────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(String.format(Locale.getDefault(), "%.2f", distanceM / 1000.0),
                        fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = RSGreen900)
                    Text("km", fontSize = 11.sp, color = Color(0xFF9E9E9E))
                    Text("Distance", fontSize = 11.sp, color = Color(0xFF7A8F7A),
                        fontWeight = FontWeight.Medium)
                }
                Box(modifier = Modifier.width(1.dp).height(52.dp).background(Color(0xFFE8EDE8)))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatTime(durationSeconds),
                        fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = RSGreen900)
                    Text("time", fontSize = 11.sp, color = Color(0xFF9E9E9E))
                    Text("Duration", fontSize = 11.sp, color = Color(0xFF7A8F7A),
                        fontWeight = FontWeight.Medium)
                }
            }

            // ── Secondary stats — speed + elevation ───────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(String.format(Locale.getDefault(), "%.1f", avgSpeedKmh),
                        fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = RSGreen900)
                    Text("km/h", fontSize = 10.sp, color = Color(0xFF9E9E9E))
                    Text("Avg Speed", fontSize = 10.sp, color = Color(0xFF7A8F7A),
                        fontWeight = FontWeight.Medium)
                }
                Box(modifier = Modifier.width(1.dp).height(44.dp).background(Color(0xFFE8EDE8)))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(String.format(Locale.getDefault(), "%.1f", maxSpeedKmh),
                        fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = RSGreen900)
                    Text("km/h", fontSize = 10.sp, color = Color(0xFF9E9E9E))
                    Text("Max Speed", fontSize = 10.sp, color = Color(0xFF7A8F7A),
                        fontWeight = FontWeight.Medium)
                }
                Box(modifier = Modifier.width(1.dp).height(44.dp).background(Color(0xFFE8EDE8)))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(String.format(Locale.getDefault(), "%.0f", elevationM),
                        fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = RSGreen900)
                    Text("m", fontSize = 10.sp, color = Color(0xFF9E9E9E))
                    Text("Elev ↑", fontSize = 10.sp, color = Color(0xFF7A8F7A),
                        fontWeight = FontWeight.Medium)
                }
            }

            HorizontalDivider(color = Color(0xFFE8EDE8))

            // ── Save route toggle ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (saveRoute) RSGreen100 else Color(0xFFF8FAF8))
                    .border(1.dp,
                        if (saveRoute) RSGreen900.copy(alpha = 0.3f) else Color(0xFFE0E8E0),
                        RoundedCornerShape(12.dp))
                    .clickable { saveRoute = !saveRoute }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (saveRoute) RSGreen900 else Color(0xFFE0E8E0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.BookmarkAdd, null,
                            tint = if (saveRoute) Color.White else Color(0xFF7A8F7A),
                            modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text("Save this route", fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = if (saveRoute) RSGreen900 else Color(0xFF1A1A1A))
                        Text("Add to your Directions",
                            fontSize = 11.sp, color = Color(0xFF7A8F7A))
                    }
                }
                Switch(checked = saveRoute, onCheckedChange = { saveRoute = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor  = Color.White,
                        checkedTrackColor  = RSGreen900
                    ))
            }

            // Route name input — shown when toggle is on
            if (saveRoute) {
                OutlinedTextField(
                    value         = routeName,
                    onValueChange = { routeName = it },
                    placeholder   = { Text("Give this route a name…",
                        color = Color.LightGray, fontSize = 13.sp) },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                    singleLine    = true,
                    leadingIcon   = { Icon(Icons.Default.Edit, null,
                        tint = RSGreen900, modifier = Modifier.size(18.dp)) },
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = RSGreen900,
                        unfocusedBorderColor    = Color(0xFFCDD8CD),
                        focusedContainerColor   = Color(0xFFF8FAF8),
                        unfocusedContainerColor = Color(0xFFF8FAF8),
                        cursorColor             = RSGreen900,
                        focusedTextColor        = Color(0xFF1A1A1A),
                        unfocusedTextColor      = Color(0xFF1A1A1A)
                    )
                )
            }

            HorizontalDivider(color = Color(0xFFE8EDE8))

            // ── Share to feed section ─────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Groups, null, tint = RSGreen900,
                    modifier = Modifier.size(18.dp))
                Text("Share to Community Feed", fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, color = RSGreen900)
            }

            OutlinedTextField(
                value         = postCaption,
                onValueChange = { if (it.length <= 200) postCaption = it },
                placeholder   = { Text("Add a caption for your ride…",
                    color = Color.LightGray, fontSize = 13.sp) },
                modifier      = Modifier.fillMaxWidth().height(90.dp),
                shape         = RoundedCornerShape(12.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = RSGreen900,
                    unfocusedBorderColor    = Color(0xFFCDD8CD),
                    focusedContainerColor   = Color(0xFFF8FAF8),
                    unfocusedContainerColor = Color(0xFFF8FAF8),
                    cursorColor             = RSGreen900,
                    focusedTextColor        = Color(0xFF1A1A1A),
                    unfocusedTextColor      = Color(0xFF1A1A1A)
                ),
                supportingText = {
                    Text("${postCaption.length}/200", fontSize = 11.sp,
                        color = if (postCaption.length > 180) Color(0xFFD32F2F) else Color.Gray,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                }
            )

            // ── Action buttons ────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {

                // Skip — dismiss without sharing
                OutlinedButton(
                    onClick = {
                        scope.launch { sheetState.hide() }
                            .invokeOnCompletion { onDismiss() }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    border   = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFCDD8CD)),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF7A8F7A))
                ) {
                    Text("Skip", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }

                // Share to Feed
                Button(
                    onClick = {
                        if (!isPosting) {
                            isPosting = true
                            val post = hashMapOf(
                                "userName"    to userName,
                                "description" to postCaption.trim(),
                                "activity"    to "Cycling Ride",
                                "distance"    to String.format(Locale.getDefault(), "%.2f", distanceM / 1000.0),
                                "timestamp"   to System.currentTimeMillis(),
                                "likes"       to 0,
                                "comments"    to 0,
                                "likedBy"     to emptyList<String>(),
                                "status"      to "pending",
                                "rideStats"   to hashMapOf(
                                    "distanceKm"  to distanceM / 1000.0,
                                    "durationSec" to durationSeconds,
                                    "avgSpeedKmh" to avgSpeedKmh,
                                    "maxSpeedKmh" to maxSpeedKmh.toDouble(),
                                    "elevationM"  to elevationM
                                )
                            )
                            db.collection("posts").add(post)
                                .addOnSuccessListener {
                                    isPosting = false
                                    // Save route if toggle is on and name is filled
                                    if (saveRoute && routeName.isNotBlank()) {
                                        val today    = java.text.SimpleDateFormat(
                                            "MMM d, yyyy", java.util.Locale.getDefault()
                                        ).format(java.util.Date())
                                        val endGeo   = destinationPoint ?: rideStartPoint
                                        db.collection("savedRoutes").add(hashMapOf(
                                            "userName"    to userName,
                                            "name"        to routeName.trim(),
                                            "distanceKm"  to distanceM / 1000.0,
                                            "durationMin" to (durationSeconds / 60),
                                            "avgSpeedKmh" to avgSpeedKmh,
                                            "maxSpeedKmh" to maxSpeedKmh.toDouble(),
                                            "elevationM"  to elevationM,
                                            "timesRidden" to 1,
                                            "lastRidden"  to today,
                                            "startLat"    to (rideStartPoint?.latitude  ?: 0.0),
                                            "startLon"    to (rideStartPoint?.longitude ?: 0.0),
                                            "endLat"      to (endGeo?.latitude  ?: 0.0),
                                            "endLon"      to (endGeo?.longitude ?: 0.0),
                                            "polyline"    to locationPoints.map {
                                                mapOf("lat" to it.latitude, "lon" to it.longitude)
                                            },
                                            "isShared"    to false,
                                            "savedCount"  to 0,
                                            "timestamp"   to System.currentTimeMillis()
                                        ))
                                    }
                                    Toast.makeText(
                                        context,
                                        if (saveRoute && routeName.isNotBlank())
                                            "Ride shared & route saved! 🚴"
                                        else "Ride shared to feed! 🚴",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    scope.launch { sheetState.hide() }
                                        .invokeOnCompletion { onDismiss() }
                                }
                                .addOnFailureListener {
                                    isPosting = false
                                    Toast.makeText(context, "Failed to share. Try again.",
                                        Toast.LENGTH_SHORT).show()
                                }
                        }
                    },
                    modifier = Modifier.weight(2f).height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = RSGreen900,
                        contentColor   = Color.White
                    )
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(color = Color.White,
                            modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                            Text("Share to Feed", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}