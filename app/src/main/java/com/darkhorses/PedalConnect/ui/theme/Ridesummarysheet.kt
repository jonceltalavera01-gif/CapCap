package com.darkhorses.PedalConnect.ui.theme


import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
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
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import com.darkhorses.PedalConnect.BuildConfig

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

    // Renders the route polyline + markers to a bitmap and uploads to ImgBB.
    // Returns the image URL or null if it fails.

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = Color.White,
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                    Text("Ride Complete! 🎉", fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp, color = RSGreen900)
                    Text("Great effort, $displayName!", fontSize = 13.sp,
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
                    // Overlay label
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(RSGreen900.copy(alpha = 0.85f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Your Route",
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
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
                            scope.launch {
                                try {
                                    // Render route polyline to bitmap and upload to ImgBB
                                    val routeImageUrl = try {
                                        val bitmap = renderRouteToBitmap(locationPoints)
                                        if (bitmap != null) uploadBitmapToImgBB(context, bitmap)
                                        else ""
                                    } catch (e: Exception) { "" }

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
                                    }
                                )
                                suspendCancellableCoroutine<Unit> { cont ->
                                    db.collection("posts").add(post)
                                        .addOnSuccessListener { cont.resume(Unit) }
                                        .addOnFailureListener { cont.resumeWithException(it) }
                                }
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
                                } catch (e: Exception) {
                                    isPosting = false
                                    Toast.makeText(context, "Failed to share. Try again.",
                                        Toast.LENGTH_SHORT).show()
                                }
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
    val padding = 60f

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
    canvas.drawCircle(toX(points.first().longitude), toY(points.first().latitude), 14f, startPaint)

    // End marker — red circle
    val endPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#D32F2F")
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
    }
    canvas.drawCircle(toX(points.last().longitude), toY(points.last().latitude), 14f, endPaint)

    return bitmap
}

     suspend fun uploadBitmapToImgBB(context: android.content.Context, bitmap: Bitmap): String {
    return withContext(Dispatchers.IO) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
        val encoded = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
        val apiKey = BuildConfig.IMGBB_API_KEY
        val url = java.net.URL("https://api.imgbb.com/1/upload?key=$apiKey")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.outputStream.write("image=$encoded".toByteArray())
        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val json = org.json.JSONObject(response)
        json.getJSONObject("data").getString("url")
    }
}