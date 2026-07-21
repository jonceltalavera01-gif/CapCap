package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

// ── Difficulty tokens (index: 0 = Easy, 1 = Moderate, 2 = Hard) ────────────────
private enum class ExploreFilter { EASY, MODERATE, HARD }
private val DifficultyLabels = listOf("Easy", "Moderate", "Hard")
private val DifficultyColors = listOf(Color(0xFF2E7D32), Color(0xFFB8860B), Color(0xFFC62828))
private val DifficultyBg     = listOf(Color(0xFFE6F4EA), Color(0xFFFCF3DC), Color(0xFFFBE7E7))

// ── Data models ───────────────────────────────────────────────────────────────
data class SavedRoute(
    val id: String          = "",
    val name: String        = "",
    val distanceKm: Double  = 0.0,
    val avgSpeedKmh: Double = 0.0,
    val durationMin: Int    = 0,
    val timesRidden: Int    = 0,
    val lastRidden: String  = "",
    val startLabel: String  = "",
    val endLabel: String    = "",
    val isShared: Boolean   = false,
    val startLat: Double    = 0.0,
    val startLon: Double    = 0.0,
    val endLat: Double      = 0.0,
    val endLon: Double      = 0.0,
    val difficulty: Int     = 1,
    val savedFrom: String   = ""
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
    val savedBy: List<String> = emptyList(),
    val timestamp: Long     = 0L,
    val endLat: Double      = 0.0,
    val endLon: Double      = 0.0,
    val difficulty: Int     = 1,
    val sourceRouteId: String = ""
)

data class RouteComment(
    val id: String            = "",
    val sharedRouteId: String = "",
    val userName: String      = "",
    val text: String          = "",
    val timestamp: Long       = 0L
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
// ── Small stat pill used inside route cards ────────────────────────────────────
@Composable
private fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color,
    bg: Color
) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bg)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(12.dp))
        Text(text, fontSize = 11.sp, color = tint, fontWeight = FontWeight.SemiBold,
            maxLines = 1, softWrap = false)
    }
}

// ── Saved route card ──────────────────────────────────────────────────────────
@Composable
private fun SavedRouteCard(
    route: SavedRoute,
    userName: String,
    db: FirebaseFirestore,
    navController: NavController,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onRenameClick: () -> Unit,
    onNavigate: () -> Unit,
    onShared: (sourceRouteId: String, CommunityRoute) -> Unit,
    onUnshared: (sourceRouteId: String, sharedDocIds: List<String>) -> Unit
) {
    var isSharing        by remember { mutableStateOf(false) }
    var menuExpanded     by remember { mutableStateOf(false) }
    var showShareDialog  by remember { mutableStateOf(false) }
    var pickedDifficulty by remember { mutableStateOf(route.difficulty) }

    fun shareWithDifficulty(difficulty: Int) {
        if (isSharing || route.id.isEmpty()) return
        isSharing = true
        val doc = hashMapOf(
            "userName"    to userName,
            "routeId"     to route.id,
            "name"        to route.name,
            "distanceKm"  to route.distanceKm,
            "avgSpeedKmh" to route.avgSpeedKmh,
            "durationMin" to route.durationMin,
            "startLabel"  to route.startLabel,
            "endLabel"    to route.endLabel,
            "endLat"      to route.endLat,
            "endLon"      to route.endLon,
            "difficulty"  to difficulty,
            "savedCount"  to 0,
            "savedBy"     to emptyList<String>(),
            "timestamp"   to System.currentTimeMillis()
        )
        db.collection("sharedRoutes").add(doc)
            .addOnSuccessListener { sharedDocRef ->
                db.collection("savedRoutes").document(route.id)
                    .update("isShared", true, "difficulty", difficulty)
                isSharing = false
                onShared(
                    route.id,
                    CommunityRoute(
                        id = sharedDocRef.id, userName = userName, name = route.name,
                        distanceKm = route.distanceKm, durationMin = route.durationMin,
                        avgSpeedKmh = route.avgSpeedKmh, startLabel = route.startLabel,
                        savedCount = 0, savedBy = emptyList(),
                        timestamp = System.currentTimeMillis(),
                        endLat = route.endLat, endLon = route.endLon, difficulty = difficulty,
                        sourceRouteId = route.id
                    )
                )
            }
            .addOnFailureListener { isSharing = false }
    }

    fun unshare() {
        if (isSharing || route.id.isEmpty()) return
        isSharing = true
        db.collection("sharedRoutes").whereEqualTo("routeId", route.id).get()
            .addOnSuccessListener { snap ->
                val removedIds = snap.documents.map { it.id }
                isSharing = false
                onUnshared(route.id, removedIds)
            }
            .addOnFailureListener { isSharing = false }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isSelected) Modifier.border(2.dp, DGreen700, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(enabled = isSelectionMode) { onToggleSelect() },
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {

            // ── Header: icon/checkbox · name + location · overflow menu ──────
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(DGreen100),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelectionMode) {
                        Icon(
                            if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            null,
                            tint = if (isSelected) DGreen700 else DTextSub,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        RoutesBikeIcon(modifier = Modifier.size(26.dp), color = DGreen700, strokeWidth = 5f)
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            route.name, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                            color = DOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (route.isShared) {
                            Icon(Icons.Default.Share, "Shared", tint = DGreen700, modifier = Modifier.size(13.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.LocationOn, null, tint = DTextSub, modifier = Modifier.size(11.dp))
                        Text(route.startLabel.ifBlank { "No location" }, fontSize = 11.sp,
                            color = DTextSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (route.savedFrom.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Group, null, tint = DTextSub, modifier = Modifier.size(11.dp))
                            Text("Originally by ${route.savedFrom}", fontSize = 11.sp,
                                color = DTextSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                if (!isSelectionMode) {
                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.MoreVert, "More options", tint = DTextSub, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp)) },
                                onClick = { menuExpanded = false; onRenameClick() }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when {
                                            isSharing      -> "Please wait…"
                                            route.isShared -> "Unshare"
                                            else           -> "Share to community"
                                        }
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (route.isShared) Icons.Default.LinkOff else Icons.Default.ShareLocation,
                                        null, modifier = Modifier.size(18.dp)
                                    )
                                },
                                enabled = !isSharing,
                                onClick = {
                                    menuExpanded = false
                                    if (route.isShared) unshare()
                                    else { pickedDifficulty = route.difficulty; showShareDialog = true }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Stats row — scrolls instead of wrapping if space runs tight ──
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip(Icons.AutoMirrored.Filled.TrendingUp, String.format("%.1f km", route.distanceKm), DGreen700, DGreen100)
                StatChip(Icons.Default.Timer, "${route.durationMin} min", DTextSub, Color(0xFFF5F5F5))
                StatChip(Icons.Default.Repeat, "${route.timesRidden}× ridden", DTextSub, Color(0xFFF5F5F5))
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFFF0F0F0))
            Spacer(Modifier.height(10.dp))

            // ── Footer: last ridden · Navigate ────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.History, null, tint = DTextSub, modifier = Modifier.size(12.dp))
                    Text(
                        if (route.lastRidden.isNotEmpty()) "Last ridden ${route.lastRidden}" else "Not ridden yet",
                        fontSize = 11.sp, color = DTextSub
                    )
                }

                if (!isSelectionMode) {
                    val hasCoords = route.endLat != 0.0 || route.endLon != 0.0
                    Button(
                        onClick = {
                            if (hasCoords) {
                                navController.navigate("home_navigate/${route.endLat}/${route.endLon}")
                            }
                            onNavigate()
                        },
                        enabled = hasCoords,
                        modifier = Modifier.height(34.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor          = DGreen900,
                            contentColor             = Color.White,
                            disabledContainerColor  = Color(0xFFDDDDDD),
                            disabledContentColor    = Color(0xFF999999)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.Navigation, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Navigate", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // ── Share dialog: pick a difficulty rating before publishing ─────────────
    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSharing) showShareDialog = false },
            title = { Text("Rate this route", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Help other riders know what to expect. Your call — there's no wrong answer.",
                        fontSize = 12.sp, color = DTextSub
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DifficultyLabels.forEachIndexed { idx, label ->
                            val selected = pickedDifficulty == idx
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) DifficultyColors[idx] else DifficultyBg[idx])
                                    .clickable { pickedDifficulty = idx }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    color = if (selected) Color.White else DifficultyColors[idx]
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isSharing,
                    onClick = { shareWithDifficulty(pickedDifficulty); showShareDialog = false }
                ) { Text(if (isSharing) "Sharing…" else "Share", color = DGreen700, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(enabled = !isSharing, onClick = { showShareDialog = false }) {
                    Text("Cancel", color = DTextSub)
                }
            }
        )
    }
}

// ── Community route card ──────────────────────────────────────────────────────
@Composable
private fun CommunityRouteCard(
    route: CommunityRoute,
    currentUser: String,
    db: FirebaseFirestore,
    onNavigate: () -> Unit,
    onSaved: (SavedRoute) -> Unit,
    onRemove: (sharedRouteId: String, sourceRouteId: String) -> Unit,
    onOpenDetails: () -> Unit
) {
    var isSaving by remember { mutableStateOf(false) }
    var isSaved  by remember {
        mutableStateOf(route.savedBy.any { it.trim().equals(currentUser.trim(), ignoreCase = true) })
    }
    val context = LocalContext.current
    val isOwnRoute = route.userName.trim().equals(currentUser.trim(), ignoreCase = true)

    Card(
        modifier  = Modifier.fillMaxWidth().clickable { onOpenDetails() },
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
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Difficulty badge
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .background(DifficultyColors[route.difficulty.coerceIn(0, 2)])
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                DifficultyLabels[route.difficulty.coerceIn(0, 2)],
                                fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold
                            )
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

                    // Save to My Routes — or Remove, if this is the current user's own route
                    OutlinedButton(
                        onClick = {
                            if (isOwnRoute) {
                                onRemove(route.id, route.sourceRouteId)
                            } else if (isSaved) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Already in My Routes — delete it from there if you'd like to remove it.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            } else if (!isSaving && route.id.isNotEmpty()) {
                                isSaving = true
                                val sharedDocRef = db.collection("sharedRoutes").document(route.id)
                                // Transaction is the source of truth for "has this user saved
                                // this route already" — savedCount only increments once per user,
                                // regardless of how many times Save gets tapped across sessions.
                                db.runTransaction { transaction ->
                                    val snap = transaction.get(sharedDocRef)
                                    val existing = (snap.get("savedBy") as? List<*>)
                                        ?.mapNotNull { it as? String } ?: emptyList()
                                    val alreadySaved = existing.any {
                                        it.trim().equals(currentUser.trim(), ignoreCase = true)
                                    }
                                    if (!alreadySaved) {
                                        transaction.update(sharedDocRef, mapOf(
                                            "savedBy"    to FieldValue.arrayUnion(currentUser),
                                            "savedCount" to FieldValue.increment(1)
                                        ))
                                    }
                                    alreadySaved
                                }.addOnSuccessListener { alreadySaved ->
                                    if (alreadySaved) {
                                        isSaved = true; isSaving = false
                                        return@addOnSuccessListener
                                    }
                                    val today = java.text.SimpleDateFormat(
                                        "MMM d, yyyy", java.util.Locale.getDefault()
                                    ).format(java.util.Date())
                                    val saveDoc = hashMapOf(
                                        "userName"    to currentUser,
                                        "name"        to route.name,
                                        "distanceKm"  to route.distanceKm,
                                        "avgSpeedKmh" to route.avgSpeedKmh,
                                        "durationMin" to route.durationMin,
                                        "startLabel"  to route.startLabel,
                                        "endLabel"    to "",
                                        "endLat"      to route.endLat,
                                        "endLon"      to route.endLon,
                                        "timesRidden" to 0,
                                        "lastRidden"  to "",
                                        "isShared"    to false,
                                        "savedCount"  to 0,
                                        "savedFrom"   to route.userName,
                                        "difficulty"  to route.difficulty,
                                        "timestamp"   to System.currentTimeMillis()
                                    )
                                    db.collection("savedRoutes").add(saveDoc)
                                        .addOnSuccessListener { savedDocRef ->
                                            isSaved = true; isSaving = false
                                            onSaved(
                                                SavedRoute(
                                                    id = savedDocRef.id, name = route.name,
                                                    distanceKm = route.distanceKm, avgSpeedKmh = route.avgSpeedKmh,
                                                    durationMin = route.durationMin, timesRidden = 0,
                                                    lastRidden = "", startLabel = route.startLabel,
                                                    endLabel = "", isShared = false,
                                                    startLat = 0.0, startLon = 0.0,
                                                    endLat = route.endLat, endLon = route.endLon,
                                                    difficulty = route.difficulty,
                                                    savedFrom = route.userName
                                                )
                                            )
                                        }
                                        .addOnFailureListener { isSaving = false }
                                }.addOnFailureListener { isSaving = false }
                            }
                        },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(
                            contentColor            = if (isOwnRoute) Color(0xFFC62828) else if (isSaved) DGreen700 else DTextSub,
                            disabledContentColor    = if (isSaved) DGreen700 else DTextSub,
                            disabledContainerColor  = if (isSaved) DGreen100 else Color.White
                        ),
                        border   = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (isOwnRoute) Color(0xFFC62828) else if (isSaved) DGreen700 else Color(0xFFCDD8CD)
                        ),
                        enabled  = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp, color = DGreen700)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(
                                    when {
                                        isOwnRoute -> Icons.Default.LinkOff
                                        isSaved    -> Icons.Default.BookmarkAdded
                                        else       -> Icons.Default.BookmarkAdd
                                    },
                                    null, modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    when {
                                        isOwnRoute -> "Remove"
                                        isSaved    -> "Saved"
                                        else       -> "Save"
                                    },
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Navigate to start — or Details, for the route's own owner
                    Button(
                        onClick  = if (isOwnRoute) onOpenDetails else onNavigate,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = DGreen900,
                            contentColor   = Color.White
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(
                                if (isOwnRoute) Icons.Default.ChatBubbleOutline else Icons.Default.Navigation,
                                null, tint = Color.White, modifier = Modifier.size(15.dp)
                            )
                            Text(if (isOwnRoute) "Details" else "Navigate",
                                fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}


// ── Route details dialog: stats + comments, no map (avoids crash risk from ─────
// unconfirmed path data / unconfigured map SDK) ─────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteDetailsSheet(
    route: CommunityRoute,
    currentUser: String,
    db: FirebaseFirestore,
    onDismiss: () -> Unit
) {
    var comments          by remember { mutableStateOf<List<RouteComment>>(emptyList()) }
    var isLoadingComments by remember { mutableStateOf(true) }
    var commentText       by remember { mutableStateOf("") }
    var isPosting         by remember { mutableStateOf(false) }

    LaunchedEffect(route.id) {
        if (route.id.isEmpty()) { isLoadingComments = false; return@LaunchedEffect }
        db.collection("routeComments")
            .whereEqualTo("sharedRouteId", route.id)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                comments = snap.documents.mapNotNull { doc ->
                    try {
                        RouteComment(
                            id            = doc.id,
                            sharedRouteId = doc.getString("sharedRouteId") ?: "",
                            userName      = doc.getString("userName")      ?: "",
                            text          = doc.getString("text")          ?: "",
                            timestamp     = doc.getLong("timestamp")       ?: 0L
                        )
                    } catch (e: Exception) { null }
                }
                isLoadingComments = false
            }
            .addOnFailureListener { isLoadingComments = false }
    }

    fun postComment() {
        val trimmed = commentText.trim()
        if (trimmed.isEmpty() || trimmed.length > 300 || isPosting || route.id.isEmpty()) return
        isPosting = true
        val doc = hashMapOf(
            "sharedRouteId" to route.id,
            "userName"      to currentUser,
            "text"          to trimmed,
            "timestamp"     to System.currentTimeMillis()
        )
        db.collection("routeComments").add(doc)
            .addOnSuccessListener { ref ->
                comments = listOf(
                    RouteComment(id = ref.id, sharedRouteId = route.id,
                        userName = currentUser, text = trimmed, timestamp = System.currentTimeMillis())
                ) + comments
                commentText = ""
                isPosting = false
            }
            .addOnFailureListener { isPosting = false }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(modifier = Modifier.fillMaxWidth().imePadding()) {

                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(route.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
                            color = DOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, "Close", tint = DTextSub, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatChip(Icons.AutoMirrored.Filled.TrendingUp, String.format("%.1f km", route.distanceKm), DGreen700, DGreen100)
                        StatChip(Icons.Default.Timer, "~${route.durationMin} min", DTextSub, Color(0xFFF5F5F5))
                        if (route.avgSpeedKmh > 0) {
                            StatChip(Icons.Default.Speed, String.format("%.1f km/h", route.avgSpeedKmh), DTextSub, Color(0xFFF5F5F5))
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFFF0F0F0))

                Column(modifier = Modifier.weight(1f, fill = false).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Comments", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DOnSurface)
                    Spacer(Modifier.height(6.dp))
                    if (isLoadingComments) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = DGreen900, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                        }
                    } else if (comments.isEmpty()) {
                        Text("No comments yet — be the first to leave one.", fontSize = 12.sp, color = DTextSub)
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(comments, key = { it.id }) { c ->
                                Column {
                                    Text(c.userName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DGreen900)
                                    Text(c.text, fontSize = 13.sp, color = DOnSurface, lineHeight = 18.sp)
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFFF0F0F0))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { if (it.length <= 300) commentText = it },
                        placeholder = { Text("Add a comment…", fontSize = 13.sp, color = DTextSub) },
                        modifier = Modifier.weight(1f),
                        singleLine = false,
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor       = DOnSurface,
                            unfocusedTextColor     = DOnSurface,
                            focusedBorderColor     = DGreen700,
                            unfocusedBorderColor   = Color(0xFFCDD8CD),
                            cursorColor            = DGreen700,
                            focusedContainerColor  = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )
                    IconButton(
                        onClick = { postComment() },
                        enabled = !isPosting && commentText.trim().isNotEmpty()
                    ) {
                        if (isPosting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = DGreen700)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, "Post", tint = DGreen700)
                        }
                    }
                }
            Spacer(Modifier.height(4.dp))
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

    var exploreFilter        by remember { mutableStateOf<ExploreFilter?>(null) }
    var isSelectionMode      by remember { mutableStateOf(false) }
    var selectedRouteIds     by remember { mutableStateOf<Set<String>>(emptySet()) }
    var routeToRename        by remember { mutableStateOf<SavedRoute?>(null) }
    var renameText           by remember { mutableStateOf("") }
    var showDeleteConfirm    by remember { mutableStateOf(false) }
    var isDeleting           by remember { mutableStateOf(false) }
    var detailsRoute         by remember { mutableStateOf<CommunityRoute?>(null) }

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
                            avgSpeedKmh = doc.getDouble("avgSpeedKmh") ?: 0.0,
                            durationMin = (doc.getLong("durationMin")  ?: 0L).toInt(),
                            timesRidden = (doc.getLong("timesRidden")  ?: 0L).toInt(),
                            lastRidden  = doc.getString("lastRidden")  ?: "",
                            startLabel  = doc.getString("startLabel")  ?: "",
                            endLabel    = doc.getString("endLabel")    ?: "",
                            isShared    = doc.getBoolean("isShared")   ?: false,
                            startLat    = doc.getDouble("startLat")    ?: 0.0,
                            startLon    = doc.getDouble("startLon")    ?: 0.0,
                            endLat      = doc.getDouble("endLat")      ?: 0.0,
                            endLon      = doc.getDouble("endLon")      ?: 0.0,
                            difficulty  = (doc.getLong("difficulty")   ?: 1L).toInt(),
                            savedFrom   = doc.getString("savedFrom")   ?: ""
                        )
                    } catch (e: Exception) { null }
                }
                isLoadingRoutes = false
            }
            .addOnFailureListener { e ->
                isLoadingRoutes = false
                android.widget.Toast.makeText(
                    navController.context,
                    "Couldn't load your routes: ${e.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
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
                        CommunityRoute(
                            id          = doc.id,
                            userName    = owner,
                            name        = doc.getString("name")        ?: "Unnamed",
                            distanceKm  = doc.getDouble("distanceKm")  ?: 0.0,
                            durationMin = (doc.getLong("durationMin")  ?: 0L).toInt(),
                            avgSpeedKmh = doc.getDouble("avgSpeedKmh") ?: 0.0,
                            startLabel  = doc.getString("startLabel")  ?: "",
                            savedCount  = (doc.getLong("savedCount")   ?: 0L).toInt(),
                            savedBy     = (doc.get("savedBy") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                            timestamp   = doc.getLong("timestamp")     ?: 0L,
                            endLat      = doc.getDouble("endLat")      ?: 0.0,
                            endLon      = doc.getDouble("endLon")      ?: 0.0,
                            difficulty  = (doc.getLong("difficulty")   ?: 1L).toInt(),
                            sourceRouteId = doc.getString("routeId")   ?: ""
                        )
                    } catch (e: Exception) { null }
                }
                isLoadingCommunity = false
            }
            .addOnFailureListener { e ->
                isLoadingCommunity = false
                android.widget.Toast.makeText(
                    navController.context,
                    "Couldn't load community routes: ${e.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
    }

    fun performUnshare(sharedRouteId: String, sourceRouteId: String) {
        if (sharedRouteId.isEmpty()) return
        db.collection("sharedRoutes").document(sharedRouteId).delete()
            .addOnSuccessListener {
                if (sourceRouteId.isNotEmpty()) {
                    db.collection("savedRoutes").document(sourceRouteId).update("isShared", false)
                }
                communityRoutes = communityRoutes.filterNot { it.id == sharedRouteId }
                savedRoutes = savedRoutes.map {
                    if (it.id == sourceRouteId) it.copy(isShared = false) else it
                }
            }
    }

    val filteredSaved = remember(searchQuery, savedRoutes) {
        if (searchQuery.isBlank()) savedRoutes
        else savedRoutes.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.startLabel.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredCommunity = remember(searchQuery, communityRoutes, exploreFilter) {
        val base = if (searchQuery.isBlank()) communityRoutes
        else communityRoutes.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.userName.contains(searchQuery, ignoreCase = true) ||
                    it.startLabel.contains(searchQuery, ignoreCase = true)
        }
        val targetDifficulty = when (exploreFilter) {
            ExploreFilter.EASY     -> 0
            ExploreFilter.MODERATE -> 1
            ExploreFilter.HARD     -> 2
            else                   -> null
        }
        targetDifficulty?.let { d -> base.filter { it.difficulty == d } } ?: base
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
                        Text("Saved Routes", fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp, color = DOnSurface, letterSpacing = 0.1.sp)
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(DGreen100)
                                .padding(horizontal = 12.dp, vertical = 5.dp)) {
                                Text("${filteredSaved.size} routes", fontSize = 12.sp,
                                    color = DGreen900, fontWeight = FontWeight.SemiBold)
                            }
                            if (savedRoutes.isNotEmpty()) {
                                TextButton(onClick = {
                                    isSelectionMode = !isSelectionMode
                                    selectedRouteIds = emptySet()
                                }) {
                                    Text(
                                        if (isSelectionMode) "Cancel" else "Select",
                                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DGreen700
                                    )
                                }
                            }
                        }
                    }
                }

                if (isSelectionMode) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp)).background(DGreen100)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${selectedRouteIds.size} selected",
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DGreen900)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = {
                                    selectedRouteIds =
                                        if (selectedRouteIds.size == filteredSaved.size) emptySet()
                                        else filteredSaved.map { it.id }.toSet()
                                }) {
                                    Text(
                                        if (selectedRouteIds.size == filteredSaved.size) "Deselect all" else "Select all",
                                        fontSize = 12.sp, color = DGreen700
                                    )
                                }
                                Button(
                                    onClick = { showDeleteConfirm = true },
                                    enabled = selectedRouteIds.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor          = Color(0xFFC62828),
                                        contentColor             = Color.White,
                                        disabledContainerColor  = Color(0xFFEFC6C6),
                                        disabledContentColor    = Color(0xFFC62828)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Delete", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
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
                    items(filteredSaved, key = { it.id }) { route ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                            SavedRouteCard(
                                route = route,
                                userName = userName,
                                db = db,
                                navController = navController,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedRouteIds.contains(route.id),
                                onToggleSelect = {
                                    selectedRouteIds =
                                        if (selectedRouteIds.contains(route.id)) selectedRouteIds - route.id
                                        else selectedRouteIds + route.id
                                },
                                onRenameClick = {
                                    routeToRename = route
                                    renameText = route.name
                                },
                                onNavigate = {},
                                onShared = { sourceRouteId, newCommunityRoute ->
                                    communityRoutes = listOf(newCommunityRoute) + communityRoutes
                                    savedRoutes = savedRoutes.map {
                                        if (it.id == sourceRouteId)
                                            it.copy(isShared = true, difficulty = newCommunityRoute.difficulty)
                                        else it
                                    }
                                },
                                onUnshared = { sourceRouteId, removedIds ->
                                    removedIds.forEach { performUnshare(it, sourceRouteId) }
                                }
                            )
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
                        Text("Community Routes", fontWeight = FontWeight.ExtraBold,
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

                // Difficulty filter chips
                item {
                    val difficultyEnums = listOf(ExploreFilter.EASY, ExploreFilter.MODERATE, ExploreFilter.HARD)
                    val segmentLabels = listOf("All") + DifficultyLabels
                    val segmentColors = listOf(DTextSub) + DifficultyColors

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Segmented control: All / Easy / Moderate / Hard — one container, no gaps
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(10.dp))
                                .padding(3.dp)
                        ) {
                            segmentLabels.forEachIndexed { idx, label ->
                                val selected = if (idx == 0) exploreFilter == null
                                else exploreFilter == difficultyEnums[idx - 1]
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) segmentColors[idx] else Color.Transparent)
                                        .clickable {
                                            exploreFilter = if (idx == 0) null else difficultyEnums[idx - 1]
                                        }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                        color = if (selected) Color.White else segmentColors[idx],
                                        maxLines = 1
                                    )
                                }
                            }
                        }

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
                    items(filteredCommunity, key = { it.id }) { route ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                            CommunityRouteCard(
                                route = route, currentUser = userName, db = db,
                                onNavigate = {
                                    if (route.endLat != 0.0 || route.endLon != 0.0) {
                                        navController.navigate("home_navigate/${route.endLat}/${route.endLon}")
                                    }
                                },
                                onSaved = { newSavedRoute ->
                                    communityRoutes = communityRoutes.map {
                                        if (it.id == route.id)
                                            it.copy(
                                                savedBy    = it.savedBy + userName,
                                                savedCount = it.savedCount + 1
                                            )
                                        else it
                                    }
                                    savedRoutes = listOf(newSavedRoute) + savedRoutes
                                },
                                onRemove = { sharedRouteId, sourceRouteId ->
                                    performUnshare(sharedRouteId, sourceRouteId)
                                },
                                onOpenDetails = { detailsRoute = route }
                            )
                        }
                    }
                }
            }
        }

        // ── Rename dialog ──────────────────────────────────────────────────
        routeToRename?.let { route ->
            AlertDialog(
                onDismissRequest = { routeToRename = null },
                title = { Text("Rename route", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        placeholder = { Text("Route name") }
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = renameText.trim().isNotEmpty(),
                        onClick = {
                            val trimmed = renameText.trim()
                            if (trimmed.isNotEmpty()) {
                                db.collection("savedRoutes").document(route.id)
                                    .update("name", trimmed)
                                    .addOnSuccessListener {
                                        savedRoutes = savedRoutes.map {
                                            if (it.id == route.id) it.copy(name = trimmed) else it
                                        }
                                    }
                            }
                            routeToRename = null
                        }
                    ) { Text("Save", color = DGreen700, fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = {
                    TextButton(onClick = { routeToRename = null }) { Text("Cancel", color = DTextSub) }
                }
            )
        }

        // ── Route details bottom sheet (stats + comments) ─────────────────────
        detailsRoute?.let { route ->
            RouteDetailsSheet(
                route = route, currentUser = userName, db = db,
                onDismiss = { detailsRoute = null }
            )
        }

        // ── Delete confirmation dialog ───────────────────────────────────────
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { if (!isDeleting) showDeleteConfirm = false },
                title = { Text("Delete ${selectedRouteIds.size} route(s)?", fontWeight = FontWeight.Bold) },
                text = { Text("This can't be undone. Shared copies of these routes will also be removed from Explore.") },
                confirmButton = {
                    TextButton(
                        enabled = !isDeleting,
                        onClick = {
                            isDeleting = true
                            val idsToDelete = selectedRouteIds.toList()
                            val batch = db.batch()
                            idsToDelete.forEach { id -> batch.delete(db.collection("savedRoutes").document(id)) }
                            batch.commit()
                                .addOnSuccessListener {
                                    idsToDelete.forEach { id ->
                                        db.collection("sharedRoutes").whereEqualTo("routeId", id).get()
                                            .addOnSuccessListener { snap ->
                                                val removedIds = snap.documents.map { it.id }
                                                snap.documents.forEach { it.reference.delete() }
                                                if (removedIds.isNotEmpty()) {
                                                    communityRoutes = communityRoutes.filterNot { removedIds.contains(it.id) }
                                                }
                                            }
                                    }
                                    savedRoutes = savedRoutes.filterNot { idsToDelete.contains(it.id) }
                                    selectedRouteIds = emptySet()
                                    isSelectionMode = false
                                    isDeleting = false
                                    showDeleteConfirm = false
                                }
                                .addOnFailureListener {
                                    isDeleting = false
                                    showDeleteConfirm = false
                                }
                        }
                    ) { Text(if (isDeleting) "Deleting…" else "Delete", color = Color(0xFFC62828), fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = {
                    TextButton(enabled = !isDeleting, onClick = { showDeleteConfirm = false }) { Text("Cancel", color = DTextSub) }
                }
            )
        }
    }
}