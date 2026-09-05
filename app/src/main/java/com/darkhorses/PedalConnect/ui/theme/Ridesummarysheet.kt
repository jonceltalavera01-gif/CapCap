package com.darkhorses.PedalConnect.ui.theme


import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.graphics.Bitmap
import android.location.Geocoder
import android.graphics.Canvas
import android.util.Base64
import com.darkhorses.PedalConnect.utils.CloudinaryHelper
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import androidx.navigation.NavController
import kotlinx.coroutines.tasks.await
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import com.darkhorses.PedalConnect.BuildConfig

private val RSGreen900 = Color(0xFF06402B)
private val RSGreen100 = Color(0xFFE8F5E9)

// A ride needs at least a real GPS track and a non-trivial distance/duration
// before it's allowed onto the community feed — otherwise "Share" can produce
// an effectively empty post (0.00 km, no route, no start/end labels).
private const val MIN_SHAREABLE_DISTANCE_M = 100.0   // 0.1 km
private const val MIN_SHAREABLE_DURATION_SEC = 60L   // 1 minute


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
    navController   : NavController,
    userName        : String,
    distanceM       : Double,
    durationSeconds : Long,
    maxSpeedKmh     : Float,
    elevationM      : Double,
    rideStartPoint  : GeoPoint?,
    destinationPoint: GeoPoint?,
    locationPoints  : List<GeoPoint>,
    formatTime      : (Long) -> String,
    linkedWeekNumber: Int? = null,
    linkedWorkoutId : String? = null,
    onDismiss       : () -> Unit
) {
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()
    val db         = FirebaseFirestore.getInstance()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    var displayName by remember { mutableStateOf(userName) }
    LaunchedEffect(userName) {
        db.collection("users")
            .whereEqualTo("username", userName)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                val fetched = snap.documents.firstOrNull()?.getString("displayName")
                if (!fetched.isNullOrBlank()) displayName = fetched
            }
    }

    val avgSpeedKmh = if (durationSeconds > 0)
        (distanceM / 1000.0) / (durationSeconds / 3600.0) else 0.0

    // Gate: no recorded track, or the track is too short/quick to represent
    // a real ride worth putting on the feed.
    val hasEnoughRideData = locationPoints.size >= 2 &&
            distanceM >= MIN_SHAREABLE_DISTANCE_M &&
            durationSeconds >= MIN_SHAREABLE_DURATION_SEC

    var postCaption by remember {
        mutableStateOf(
            "Just completed a ${
                String.format(Locale.getDefault(), "%.2f", distanceM / 1000.0)
            } km ride in ${formatTime(durationSeconds)}! 🚴"
        )
    }

    var activeWorkout by remember { mutableStateOf<TrainingWorkout?>(null) }
    LaunchedEffect(linkedWorkoutId, linkedWeekNumber) {
        if (linkedWorkoutId != null && linkedWeekNumber != null) {
            try {
                val snap = db.collection("trainingPlans").document(userName).collection("plans")
                    .whereEqualTo("isActive", true)
                    .limit(1)
                    .get()
                    .await()
                val planData = snap.documents.firstOrNull()?.data
                if (planData != null) {
                    val plan = documentToPlan(planData)
                    activeWorkout = plan.weeks.find { it.weekNumber == linkedWeekNumber }
                        ?.workouts?.find { it.id == linkedWorkoutId }
                }
            } catch (e: Exception) {
                android.util.Log.e("RideSummary", "Failed to fetch workout for comparison", e)
            }
        }
    }

    var isPosting  by remember { mutableStateOf(false) }
    var isSkipping by remember { mutableStateOf(false) }
    var saveRoute  by remember { mutableStateOf(false) }
    var routeName  by remember { mutableStateOf("") }

    // Renders the route polyline + markers to a bitmap and uploads to ImgBB.
    // Returns the image URL or null if it fails.

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = Color.White,
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        val screenHeightDp = LocalConfiguration.current.screenHeightDp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = (screenHeightDp * 0.92f).dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 48.dp),
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
                    Text(
                        text = if (linkedWorkoutId != null) "Workout Summary" else "Ride Complete!",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp,
                        color = RSGreen900
                    )
                    Text(
                        text = if (linkedWorkoutId != null) "See how you performed today." else "Great effort, $displayName!",
                        fontSize = 13.sp,
                        color = Color(0xFF7A8F7A)
                    )
                    if (linkedWeekNumber != null && linkedWorkoutId != null) {
                        Text("✓ Logged to your training plan", fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold, color = RSGreen900)
                    }
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

            // ── Workout Comparison Section ────────────────────────────────────
            activeWorkout?.let { workout ->
                val actualKm = distanceM / 1000.0
                val actualMin = (durationSeconds / 60).toInt()

                val isGoalMet = evaluateWorkoutGoal(workout, actualKm, actualMin)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isGoalMet) Color(0xFFF0F9F0) else Color(0xFFFFF1F0)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.2.dp, if (isGoalMet) Color(0xFF2E7D32) else Color(0xFFD32F2F))
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isGoalMet) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (isGoalMet) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                                modifier = Modifier.size(32.dp)
                            )
                            Column {
                                Text(
                                    text = if (isGoalMet) "Congratulations!" else "Training Failed",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = if (isGoalMet) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                )
                                Text(
                                    text = if (isGoalMet) "You accomplished the workout tasks!" else "You didn't meet the workout requirements.",
                                    fontSize = 12.sp,
                                    color = if (isGoalMet) Color(0xFF2E7D32).copy(alpha = 0.8f) else Color(0xFFD32F2F).copy(alpha = 0.8f)
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = (if (isGoalMet) Color(0xFF2E7D32) else Color(0xFFD32F2F)).copy(alpha = 0.1f))
                        Spacer(Modifier.height(12.dp))
                        
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            when (workout.type) {
                                "Intervals" -> {
                                    val work = workout.workDurationMin ?: 0
                                    val rest = workout.recoveryDurationMin ?: 0
                                    val sets = workout.numIntervals ?: 0
                                    val cycleMin = work + rest
                                    val actualSets = if (cycleMin > 0) actualMin / cycleMin else 0
                                    
                                    ComparisonRow("Number of Intervals", actualSets.toDouble(), sets.toDouble(), "sets")
                                    ComparisonRow("Workout Minutes", 0.0, 0.0, "$work min", isLabelOnly = true)
                                    ComparisonRow("Recovery Minutes", 0.0, 0.0, "$rest min", isLabelOnly = true)
                                }
                                "Recovery" -> {
                                    ComparisonRow("Ride Duration", actualMin.toDouble(), workout.durationMin.toDouble(), "min")
                                    workout.hrZone?.let { ComparisonRow("Target Zone", 0.0, 0.0, it, isLabelOnly = true) }
                                }
                                "Endurance" -> {
                                    ComparisonRow("Distance", actualKm, workout.distanceKm, "km")
                                    ComparisonRow("Duration", actualMin.toDouble(), workout.durationMin.toDouble(), "min")
                                }
                                "Long Ride" -> {
                                    ComparisonRow("Distance", actualKm, workout.distanceKm, "km")
                                    ComparisonRow("Duration", actualMin.toDouble(), workout.durationMin.toDouble(), "min")
                                    if ((workout.elevationM ?: 0.0) > 0) {
                                        ComparisonRow("Elevation Gain", elevationM, workout.elevationM ?: 0.0, "m")
                                    }
                                }
                                "Race" -> {
                                    ComparisonRow("Race Distance", actualKm, workout.distanceKm, "km")
                                    ComparisonRow("Target Pace", 0.0, 0.0, workout.targetFinishTime ?: "", isLabelOnly = true)
                                }
                                else -> {
                                    ComparisonRow("Distance", actualKm, workout.distanceKm, "km")
                                    ComparisonRow("Duration", actualMin.toDouble(), workout.durationMin.toDouble(), "min")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            // ── Mini map — ridden route preview ───────────────────────────────
            if (locationPoints.size >= 2) {
                val mapContext = LocalContext.current
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE8EDE8), RoundedCornerShape(16.dp))
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory  = { ctx ->
                            Configuration.getInstance().load(
                                ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
                            )
                            Configuration.getInstance().userAgentValue = ctx.packageName
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(false)
                                isClickable  = false
                                isFocusable  = false
                                overlayManager.tilesOverlay.isEnabled = true
                                // Draw the trail polyline
                                val trail = Polyline().apply {
                                    setPoints(locationPoints)
                                    outlinePaint.color       = android.graphics.Color.argb(220, 0, 180, 100)
                                    outlinePaint.strokeWidth = 10f
                                    outlinePaint.strokeCap   = android.graphics.Paint.Cap.ROUND
                                    outlinePaint.strokeJoin  = android.graphics.Paint.Join.ROUND
                                }
                                overlays.add(trail)
                                // Start marker — yellow flag pin (matches live ride start marker)
                                locationPoints.firstOrNull()?.let { start ->
                                    org.osmdroid.views.overlay.Marker(this).apply {
                                        position = start
                                        title    = "Start"
                                        setAnchor(
                                            org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                                            org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM
                                        )
                                        icon = android.graphics.drawable.BitmapDrawable(
                                            ctx.resources,
                                            makeMarkerBitmap(
                                                context    = ctx,
                                                bgColor    = android.graphics.Color.argb(255, 255, 214, 0),
                                                isHospital = false,
                                                sizePx     = 64,
                                                isAlert    = false,
                                                isCyclist  = false,
                                                isFlag     = true
                                            )
                                        )
                                        overlays.add(this)
                                    }
                                }

                                // End marker — red hospital-style pin (solid red dot)
                                locationPoints.lastOrNull()?.let { end ->
                                    org.osmdroid.views.overlay.Marker(this).apply {
                                        position = end
                                        title    = "End"
                                        setAnchor(
                                            org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                                            org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM
                                        )
                                        icon = android.graphics.drawable.BitmapDrawable(
                                            ctx.resources,
                                            makeMarkerBitmap(
                                                context         = ctx,
                                                bgColor         = android.graphics.Color.argb(255, 211, 47, 47),
                                                isHospital      = false,
                                                sizePx          = 64,
                                                isAlert         = false,
                                                isCyclist       = false,
                                                isFlag          = false,
                                                isCheckeredFlag = true
                                            )
                                        )
                                        overlays.add(this)
                                    }
                                }
                                // Zoom to fit the whole route
                                post {
                                    try {
                                        val box = BoundingBox.fromGeoPoints(locationPoints)
                                        zoomToBoundingBox(box.increaseByScale(1.3f), false, 32)
                                    } catch (_: Exception) {
                                        controller.setCenter(locationPoints.first())
                                        controller.setZoom(15.0)
                                    }
                                }
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = Color(0xFFE8EDE8))

            // ── Save route toggle — only offered when there's a real track ─────
            if (hasEnoughRideData) Row(
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

            if (hasEnoughRideData) {
                // ── Share to feed section ─────────────────────────────────────
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
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                    minLines      = 3,
                    maxLines      = 5,
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
                Text(
                    text      = "${postCaption.length}/200",
                    fontSize  = 11.sp,
                    color     = if (postCaption.length > 180) Color(0xFFD32F2F) else Color.Gray,
                    modifier  = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            } else {
                // ── Not enough data to post — explain instead of offering Share ─
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFF7ED))
                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Info, null, tint = Color(0xFFB45309), modifier = Modifier.size(20.dp))
                    Column {
                        Text(
                            "Not enough ride data to share",
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF92400E)
                        )
                        Text(
                            "This ride is too short or has no GPS track, so it can't be posted to the community feed or saved as a route.",
                            fontSize = 12.sp, color = Color(0xFF92400E).copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // ── Action buttons ────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {

                // Skip — no feed post, but still saves the route if the toggle is on
                OutlinedButton(
                    modifier = Modifier.weight(if (hasEnoughRideData) 1f else 3f).height(52.dp),
                    onClick = {
                        if (!isPosting && !isSkipping) {
                            isSkipping = true
                            scope.launch {
                                var routeSaved = false
                                if (saveRoute) {
                                    routeSaved = try {
                                        saveRouteToFirestore(
                                            context = context,
                                            db = db, userName = userName, routeName = routeName,
                                            distanceM = distanceM, durationSeconds = durationSeconds,
                                            avgSpeedKmh = avgSpeedKmh, maxSpeedKmh = maxSpeedKmh,
                                            elevationM = elevationM, rideStartPoint = rideStartPoint,
                                            destinationPoint = destinationPoint, locationPoints = locationPoints
                                        )
                                        true
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Couldn't save route. Try again.", Toast.LENGTH_SHORT).show()
                                        false
                                    }
                                }
                                // Workout completion already written when the ride
                                // stopped in HomeScreen — not repeated here.
                                isSkipping = false
                                scope.launch { sheetState.hide() }
                                    .invokeOnCompletion {
                                        onDismiss()
                                        if (routeSaved) navController.navigate("directions/$userName")
                                    }
                            }
                        }
                    },
                    enabled  = !isPosting && !isSkipping,
                    shape    = RoundedCornerShape(14.dp),
                    border   = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFCDD8CD)),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF7A8F7A))
                ) {
                    if (isSkipping) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF7A8F7A))
                    } else {
                        Text("Skip", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }

                // Share to Feed — only offered when the ride actually has data worth posting
                if (hasEnoughRideData) Button(
                    onClick = {
                        if (!isPosting && !isSkipping) {
                            isPosting = true
                            scope.launch {
                                try {
                                    // Render route polyline to bitmap and upload to Cloudinary
                                    val routeImageUrl = try {
                                        val bitmap = renderRouteToBitmap(locationPoints)
                                        if (bitmap != null) uploadBitmapToCloudinary(bitmap)
                                        else ""
                                    } catch (e: Exception) { "" }

                                    // Best-effort reverse geocode of where the ride actually
                                    // started/ended — powers the "X → Y" line on the feed card
                                    // and detail sheet. Never blocks the share on failure.
                                    val endGeoForLabel = destinationPoint ?: locationPoints.lastOrNull() ?: rideStartPoint
                                    val startLabel = reverseGeocodeLabel(context, rideStartPoint)
                                    val endLabel   = reverseGeocodeLabel(context, endGeoForLabel)

                                    val post = hashMapOf(
                                        "userName"       to userName,
                                        "displayName"    to displayName,
                                        "description"    to postCaption.trim(),
                                        "activity"       to "Cycling Ride",
                                        "distance"       to String.format(Locale.getDefault(), "%.2f", distanceM / 1000.0),
                                        "timestamp"      to System.currentTimeMillis(),
                                        "likes"          to 0,
                                        "comments"       to 0,
                                        "likedBy"        to emptyList<String>(),
                                        "status"         to "accepted",
                                        "routeImageUrl"  to routeImageUrl,
                                        "rideStats"      to hashMapOf(
                                            "distanceKm"  to distanceM / 1000.0,
                                            "durationSec" to durationSeconds,
                                            "avgSpeedKmh" to avgSpeedKmh,
                                            "maxSpeedKmh" to maxSpeedKmh.toDouble(),
                                            "elevationM"  to elevationM
                                        ),
                                        "polyline"       to locationPoints.map {
                                            mapOf("lat" to it.latitude, "lon" to it.longitude)
                                        },
                                        "startLabel"     to startLabel,
                                        "endLabel"       to endLabel
                                    )
                                    suspendCancellableCoroutine<Unit> { cont ->
                                        db.collection("posts").add(post)
                                            .addOnSuccessListener { cont.resume(Unit) }
                                            .addOnFailureListener { cont.resumeWithException(it) }
                                    }

                                    // Feed post succeeded. Route save is tracked independently
                                    // so a route-save failure doesn't get reported as a failed share.
                                    var routeSaved = false
                                    if (saveRoute) {
                                        routeSaved = try {
                                            saveRouteToFirestore(
                                                context = context,
                                                db = db, userName = userName, routeName = routeName,
                                                distanceM = distanceM, durationSeconds = durationSeconds,
                                                avgSpeedKmh = avgSpeedKmh, maxSpeedKmh = maxSpeedKmh,
                                                elevationM = elevationM, rideStartPoint = rideStartPoint,
                                                destinationPoint = destinationPoint, locationPoints = locationPoints
                                            )
                                            true
                                        } catch (e: Exception) { false }
                                    }

                                    // Workout completion already written when the ride
                                    // stopped in HomeScreen — not repeated here.
                                    isPosting = false
                                    Toast.makeText(
                                        context,
                                        when {
                                            saveRoute && routeSaved  -> "Ride shared & route saved!"
                                            saveRoute && !routeSaved -> "Ride shared, but the route couldn't be saved."
                                            else                     -> "Ride shared to feed!"
                                        },
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    scope.launch { sheetState.hide() }
                                        .invokeOnCompletion {
                                            onDismiss()
                                            if (routeSaved) navController.navigate("directions/$userName")
                                        }
                                } catch (e: Exception) {
                                    isPosting = false
                                    Toast.makeText(context, "Failed to share. Try again.",
                                        Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled  = !isPosting && !isSkipping,
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
@Composable
private fun ComparisonRow(
    label: String,
    actual: Double,
    target: Double,
    unit: String,
    isFixed: Boolean = false,
    isLabelOnly: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = Color(0xFF4B5563), fontWeight = FontWeight.Medium)
        
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                isLabelOnly -> {
                    Text(unit, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = RSGreen900)
                }
                isFixed -> {
                    Text("${target.toInt()} $unit", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = RSGreen900)
                    Icon(Icons.Default.Check, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
                }
                else -> {
                    val isMet = actual >= target * WORKOUT_COMPLETION_THRESHOLD
                    Text(
                        text = if (unit == "km") String.format("%.2f", actual) else String.format("%.0f", actual),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isMet) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                    )
                    Text(
                        text = "/ ${if (unit == "km") String.format("%.1f", target) else String.format("%.0f", target)} $unit",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    if (isMet) {
                        Icon(Icons.Default.Check, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

 fun renderRouteToBitmap(points: List<org.osmdroid.util.GeoPoint>): Bitmap? {
    if (points.size < 2) return null
    val width = 800
    val height = 400
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Background
    canvas.drawColor(android.graphics.Color.parseColor("#E8F4EA"))

    // Project lat/lon to pixel
    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val minLon = points.minOf { it.longitude }
    val maxLon = points.maxOf { it.longitude }
    val latRange = (maxLat - minLat).takeIf { it > 0 } ?: 0.001
    val lonRange = (maxLon - minLon).takeIf { it > 0 } ?: 0.001
     val padding = 120f

    fun toX(lon: Double) = (padding + ((lon - minLon) / lonRange) * (width - 2 * padding)).toFloat()
    fun toY(lat: Double) = (padding + ((maxLat - lat) / latRange) * (height - 2 * padding)).toFloat()

    // Draw route line
    val linePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#00B464")
        strokeWidth = 8f
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        isAntiAlias = true
    }
    val path = android.graphics.Path()
    points.forEachIndexed { i, pt ->
        if (i == 0) path.moveTo(toX(pt.longitude), toY(pt.latitude))
        else path.lineTo(toX(pt.longitude), toY(pt.latitude))
    }
    canvas.drawPath(path, linePaint)

    // Start marker — green circle
    val startPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#FFD600")
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
    }
     canvas.drawCircle(toX(points.first().longitude), toY(points.first().latitude), 16f, startPaint)
     canvas.drawCircle(toX(points.first().longitude), toY(points.first().latitude), 16f, android.graphics.Paint().apply {
         color = android.graphics.Color.WHITE
         style = android.graphics.Paint.Style.STROKE
         strokeWidth = 3f
         isAntiAlias = true
     })

    // End marker — red circle
    val endPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#D32F2F")
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
    }
     canvas.drawCircle(toX(points.last().longitude), toY(points.last().latitude), 16f, endPaint)
     canvas.drawCircle(toX(points.last().longitude), toY(points.last().latitude), 16f, android.graphics.Paint().apply {
         color = android.graphics.Color.WHITE
         style = android.graphics.Paint.Style.STROKE
         strokeWidth = 3f
         isAntiAlias = true
     })

    return bitmap
}

     suspend fun uploadBitmapToCloudinary(bitmap: Bitmap): String {
    return withContext(Dispatchers.IO) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
        val result = CloudinaryHelper.uploadImage(stream.toByteArray())
        result.url
    }
     }

// Extracted so Share and Skip write the exact same document shape —
// no risk of the two flows drifting apart when a field gets added later.
// Auto-generates a name if the user left it blank, so the route always saves.
// Best-effort reverse geocode — returns "" on any failure so a save never
// blocks or fails just because geocoding is unavailable (no network, no
// geocoder backend on the device, etc.).
// Firestore documents cap out around 1MiB, and a multi-hour ride recorded
// once per second can produce thousands of points. Downsampling keeps the
// saved route well within limits without materially changing its shape.
// Always keeps the first and last point so the endpoints stay exact.
private fun downsampleForStorage(points: List<GeoPoint>, maxPoints: Int = 500): List<GeoPoint> {
    if (points.size <= maxPoints) return points
    val step = (points.size - 1).toDouble() / (maxPoints - 1)
    return (0 until maxPoints).map { i -> points[(i * step).toInt().coerceIn(0, points.size - 1)] }
}

private suspend fun reverseGeocodeLabel(context: Context, geo: GeoPoint?): String {
    if (geo == null) return ""
    return withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            val results = Geocoder(context, Locale.getDefault())
                .getFromLocation(geo.latitude, geo.longitude, 1)
            val addr = results?.firstOrNull() ?: return@withContext ""
            listOfNotNull(
                addr.thoroughfare,
                addr.subLocality ?: addr.locality
            ).joinToString(", ").ifBlank { addr.locality ?: "" }
        } catch (e: Exception) { "" }
    }
}

private suspend fun saveRouteToFirestore(
    context         : Context,
    db              : FirebaseFirestore,
    userName        : String,
    routeName       : String,
    distanceM       : Double,
    durationSeconds : Long,
    avgSpeedKmh     : Double,
    maxSpeedKmh     : Float,
    elevationM      : Double,
    rideStartPoint  : GeoPoint?,
    destinationPoint: GeoPoint?,
    locationPoints  : List<GeoPoint>
) {
    val today = java.text.SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(java.util.Date())
    // The route's real endpoint is where the ride actually finished, not where
    // it started. destinationPoint only exists if the rider was navigating
    // somewhere; otherwise fall back to the last recorded GPS point — NOT
    // rideStartPoint, which was the old (buggy) behavior.
    val endGeo = destinationPoint ?: locationPoints.lastOrNull() ?: rideStartPoint
    val finalName = routeName.trim().ifBlank {
        "Route – ${java.text.SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(java.util.Date())}"
    }
    val startLabel = reverseGeocodeLabel(context, rideStartPoint)
    val endLabel   = reverseGeocodeLabel(context, endGeo)
    db.collection("savedRoutes").add(hashMapOf(
        "userName"    to userName,
        "name"        to finalName,
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
        "startLabel"  to startLabel,
        "endLabel"    to endLabel,
        "polyline"    to downsampleForStorage(locationPoints).map { mapOf("lat" to it.latitude, "lon" to it.longitude) },
        "isShared"    to false,
        "savedCount"  to 0,
        "timestamp"   to System.currentTimeMillis()
    )).await()
}

// Writes actual ride numbers onto the specific planned TrainingWorkout this
// session was started from. Read-modify-write on the whole "weeks" array —
// same Firestore nested-array constraint TrainingScreen.kt's persist() works
// around. Kept self-contained (raw maps, not TrainingScreen's private data
// classes) since Kotlin top-level `private` is file-private, not package-private.
internal suspend fun markWorkoutInProgress(
    db         : FirebaseFirestore,
    userName   : String,
    weekNumber : Int,
    workoutId  : String
) {
    require(userName.isNotBlank()) { "userName must not be blank" }
    require(workoutId.isNotBlank()) { "workoutId must not be blank" }
    require(weekNumber > 0) { "weekNumber must be positive, got $weekNumber" }

    val snap = db.collection("trainingPlans").document(userName).collection("plans")
        .whereEqualTo("isActive", true)
        .limit(1)
        .get()
        .await()
    val planDoc = snap.documents.firstOrNull()
        ?: throw IllegalStateException("No active training plan found for $userName")
    @Suppress("UNCHECKED_CAST")
    val weeks = planDoc.get("weeks") as? List<Map<String, Any?>>
        ?: throw IllegalStateException("Active plan document for $userName has no weeks")

    var workoutFound = false
    val updatedWeeks = weeks.map { weekMap ->
        val wNum = (weekMap["weekNumber"] as? Long)?.toInt()
        if (wNum != weekNumber) return@map weekMap
        @Suppress("UNCHECKED_CAST")
        val workouts = weekMap["workouts"] as? List<Map<String, Any?>> ?: return@map weekMap
        val updatedWorkouts = workouts.map { w ->
            if (w["id"] as? String != workoutId) w else {
                workoutFound = true
                w + mapOf("inProgress" to true)
            }
        }
        weekMap + mapOf("workouts" to updatedWorkouts)
    }
    if (!workoutFound) {
        throw IllegalStateException(
            "Workout $workoutId not found in week $weekNumber of $userName's active plan"
        )
    }
    planDoc.reference
        .set(mapOf("weeks" to updatedWeeks), com.google.firebase.firestore.SetOptions.merge())
        .await()
}

// Mirror of markWorkoutInProgress that clears the flag instead of setting it.
// Used both by the normal Stop-button flow (via completeLinkedWorkout, which
// already clears inProgress inline) and by the startup sweep below for
// workouts left stuck inProgress=true by a session that never reached Stop.
internal suspend fun clearWorkoutInProgress(
    db         : FirebaseFirestore,
    userName   : String,
    weekNumber : Int,
    workoutId  : String
) {
    require(userName.isNotBlank()) { "userName must not be blank" }
    require(workoutId.isNotBlank()) { "workoutId must not be blank" }
    require(weekNumber > 0) { "weekNumber must be positive, got $weekNumber" }

    val snap = db.collection("trainingPlans").document(userName).collection("plans")
        .whereEqualTo("isActive", true)
        .limit(1)
        .get()
        .await()
    val planDoc = snap.documents.firstOrNull()
        ?: throw IllegalStateException("No active training plan found for $userName")
    @Suppress("UNCHECKED_CAST")
    val weeks = planDoc.get("weeks") as? List<Map<String, Any?>>
        ?: throw IllegalStateException("Active plan document for $userName has no weeks")

    var workoutFound = false
    val updatedWeeks = weeks.map { weekMap ->
        val wNum = (weekMap["weekNumber"] as? Long)?.toInt()
        if (wNum != weekNumber) return@map weekMap
        @Suppress("UNCHECKED_CAST")
        val workouts = weekMap["workouts"] as? List<Map<String, Any?>> ?: return@map weekMap
        val updatedWorkouts = workouts.map { w ->
            if (w["id"] as? String != workoutId) w else {
                workoutFound = true
                w + mapOf("inProgress" to false)
            }
        }
        weekMap + mapOf("workouts" to updatedWorkouts)
    }
    if (!workoutFound) {
        throw IllegalStateException(
            "Workout $workoutId not found in week $weekNumber of $userName's active plan"
        )
    }
    planDoc.reference
        .set(mapOf("weeks" to updatedWeeks), com.google.firebase.firestore.SetOptions.merge())
        .await()
}

internal suspend fun completeLinkedWorkout(
    db                : FirebaseFirestore,
    userName          : String,
    weekNumber        : Int,
    workoutId         : String,
    actualDurationMin : Int,
    actualDistanceKm  : Double,
    actualAvgSpeedKmh : Double
) {
    require(userName.isNotBlank()) { "userName must not be blank" }
    require(workoutId.isNotBlank()) { "workoutId must not be blank" }
    require(weekNumber > 0) { "weekNumber must be positive, got $weekNumber" }

    val snap = db.collection("trainingPlans").document(userName).collection("plans")
        .whereEqualTo("isActive", true)
        .limit(1)
        .get()
        .await()
    val planDoc = snap.documents.firstOrNull()
        ?: throw IllegalStateException("No active training plan found for $userName")
    val plan = documentToPlan(
        planDoc.data ?: throw IllegalStateException("Active plan document for $userName has no data")
    )

    // Tracks whether workoutId actually existed in weekNumber, so a stale or
    // mismatched id fails loudly instead of silently writing nothing back —
    // the previous version returned normally either way, which looked
    // identical to success at every call site.
    var workoutFound = false
    val updatedWeeks = plan.weeks.map { week ->
        if (week.weekNumber != weekNumber) return@map week.toMap()

        val updatedWorkouts = week.workouts.map { w ->
            if (w.id != workoutId) return@map w.toMap()
            workoutFound = true

            // Completion/failed/previouslyFailed are derived the same way as
            // the manual toggle in TrainingScreen.kt (applyWorkoutOutcome), so
            // an auto-completed ride and a manual mark can't disagree, and a
            // failed attempt stays visible even if a later retry succeeds.
            applyWorkoutOutcome(w, actualDistanceKm, actualDurationMin).copy(
                actualDurationMin = actualDurationMin.coerceAtLeast(0),
                actualDistanceKm = actualDistanceKm.coerceAtLeast(0.0),
                actualAvgSpeedKmh = actualAvgSpeedKmh.coerceAtLeast(0.0),
                inProgress = false
            ).toMap()
        }
        mapOf("weekNumber" to week.weekNumber, "workouts" to updatedWorkouts)
    }
    if (!workoutFound) {
        throw IllegalStateException(
            "Workout $workoutId not found in week $weekNumber of $userName's active plan"
        )
    }
    planDoc.reference
        .set(mapOf("weeks" to updatedWeeks), com.google.firebase.firestore.SetOptions.merge())
        .await()
}
