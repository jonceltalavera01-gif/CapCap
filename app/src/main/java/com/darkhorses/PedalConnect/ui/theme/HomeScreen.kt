package com.darkhorses.PedalConnect.ui.theme

import android.Manifest
import android.R.attr.fontWeight
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.HorizontalDivider
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.net.URL
import java.util.Locale
import kotlin.math.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.graphics.graphicsLayer

// ── Colour tokens ─────────────────────────────────────────────────────────────
private val Green900 = Color(0xFF06402B)
private val Green700 = Color(0xFF0A5C3D)
private val Green100 = Color(0xFFE8F5E9)

// ── Radius constants ─────────────────────────────────────────────────────────
private const val CYCLIST_RADIUS_KM    = 1.0  // realistic help/interaction radius
private const val SERVICES_RADIUS_KM   = 3.0  // services are worth travelling further for
private const val STALE_MS             = 30_000L           // 30s — written every 5s so 30s = definitely offline
private const val OVERPASS_INTERVAL_MS = 90_000L
private const val OVERPASS_MOVE_KM     = 0.25

private fun fuzzDistance(km: Double): Double = (Math.round(km * 2).toDouble()) / 2.0

fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R    = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a    = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return R * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private fun buildCirclePoints(center: GeoPoint, radiusKm: Double): List<GeoPoint> {
    val points      = mutableListOf<GeoPoint>()
    val earthRadius = 6371.0
    val d           = radiusKm / earthRadius
    val latRad      = Math.toRadians(center.latitude)
    val lonRad      = Math.toRadians(center.longitude)
    for (i in 0..360) {
        val bearing = Math.toRadians(i.toDouble())
        val lat2    = asin(sin(latRad) * cos(d) + cos(latRad) * sin(d) * cos(bearing))
        val lon2    = lonRad + atan2(
            sin(bearing) * sin(d) * cos(latRad),
            cos(d) - sin(latRad) * sin(lat2)
        )
        points.add(GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2)))
    }
    return points
}

// Inner circle — 1km cyclist/safety radius (solid green, filled)
fun buildCyclistRadiusPolygon(center: GeoPoint): Polygon {
    return Polygon().apply {
        points                   = buildCirclePoints(center, CYCLIST_RADIUS_KM)
        fillPaint.color          = android.graphics.Color.argb(20, 6, 64, 43)   // subtle green fill
        outlinePaint.color       = android.graphics.Color.argb(200, 10, 92, 61) // solid green border
        outlinePaint.strokeWidth = 3f
        outlinePaint.style       = Paint.Style.STROKE
    }
}

// Outer circle — 3km services radius (dashed, very subtle)
fun buildServicesRadiusPolygon(center: GeoPoint): Polygon {
    return Polygon().apply {
        points                   = buildCirclePoints(center, SERVICES_RADIUS_KM)
        fillPaint.color          = android.graphics.Color.argb(0, 0, 0, 0)      // no fill
        outlinePaint.color       = android.graphics.Color.argb(80, 10, 92, 61)  // faint green dashed
        outlinePaint.strokeWidth = 2f
        outlinePaint.style       = Paint.Style.STROKE
        outlinePaint.pathEffect  = android.graphics.DashPathEffect(floatArrayOf(16f, 14f), 0f)
    }
}

data class NearbyUser(
    val userName:   String,
    val location:   GeoPoint,
    val distanceKm: Double,
    val lastSeen:   Long = 0L
)

internal fun makeMarkerBitmap(
    context:          android.content.Context,
    bgColor:          Int,
    isHospital:       Boolean,
    sizePx:           Int     = 72,
    isAlert:          Boolean = false,
    isCyclist:        Boolean = false,
    isFlag:           Boolean = false,
    isCheckeredFlag:  Boolean = false
): android.graphics.Bitmap {
    val w      = sizePx
    val h      = (sizePx * 1.35f).toInt()
    val bmp    = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val cx     = w / 2f
    val r      = w / 2f - 6f

    val shadowPath = android.graphics.Path().apply {
        addCircle(cx + 2f, r + 6f, r, android.graphics.Path.Direction.CW)
        moveTo(cx + 2f, r * 1.35f + 6f)
        lineTo(cx - r * 0.28f + 2f, r * 1.65f + 6f)
        lineTo(cx + r * 0.28f + 2f, r * 1.65f + 6f)
        close()
        moveTo(cx + 2f, r * 1.65f + 6f)
        lineTo(cx + 2f, h.toFloat() - 4f + 6f)
    }
    canvas.drawPath(shadowPath, Paint().apply {
        isAntiAlias = true
        color       = android.graphics.Color.argb(50, 0, 0, 0)
        style       = Paint.Style.FILL_AND_STROKE
        strokeWidth = r * 0.22f
        strokeCap   = Paint.Cap.ROUND
    })

    val bodyPath = android.graphics.Path().apply {
        addCircle(cx, r + 4f, r, android.graphics.Path.Direction.CW)
    }
    canvas.drawPath(bodyPath, Paint().apply {
        isAntiAlias = true; color = bgColor; style = Paint.Style.FILL
    })
    canvas.drawLine(cx, r * 1.6f + 4f, cx, h.toFloat() - 6f, Paint().apply {
        isAntiAlias = true; color = bgColor; strokeWidth = r * 0.36f; strokeCap = Paint.Cap.ROUND
    })
    canvas.drawCircle(cx, r + 4f, r, Paint().apply {
        isAntiAlias = true
        color       = android.graphics.Color.WHITE
        style       = Paint.Style.STROKE
        strokeWidth = 5f
    })

    val cy        = r + 4f
    val iconPaint = Paint().apply {
        isAntiAlias = true
        color       = android.graphics.Color.WHITE
        strokeCap   = Paint.Cap.ROUND
        strokeJoin  = Paint.Join.ROUND
    }

    if (isAlert) {
        val barW = r * 0.22f
        iconPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(cx - barW, cy - r * 0.44f, cx + barW, cy + r * 0.10f, barW, barW, iconPaint)
        canvas.drawCircle(cx, cy + r * 0.34f, r * 0.14f, iconPaint)
    } else if (isHospital) {
        val arm = r * 0.42f; val thick = r * 0.18f
        iconPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(cx - arm, cy - thick, cx + arm, cy + thick, thick, thick, iconPaint)
        canvas.drawRoundRect(cx - thick, cy - arm, cx + thick, cy + arm, thick, thick, iconPaint)
    } else if (isCyclist) {
        // Simple person silhouette — head circle + body line
        iconPaint.style = Paint.Style.FILL
        // Head
        canvas.drawCircle(cx, cy - r * 0.28f, r * 0.18f, iconPaint)
        // Body
        iconPaint.style     = Paint.Style.STROKE
        iconPaint.strokeWidth = r * 0.14f
        canvas.drawLine(cx, cy - r * 0.10f, cx, cy + r * 0.22f, iconPaint)
        // Arms
        canvas.drawLine(cx - r * 0.22f, cy + r * 0.02f, cx + r * 0.22f, cy + r * 0.02f, iconPaint)
        // Legs
        canvas.drawLine(cx, cy + r * 0.22f, cx - r * 0.18f, cy + r * 0.44f, iconPaint)
        canvas.drawLine(cx, cy + r * 0.22f, cx + r * 0.18f, cy + r * 0.44f, iconPaint)
    } else if (isFlag) {
        // Flag icon — ride start marker
        iconPaint.style       = Paint.Style.STROKE
        iconPaint.color       = android.graphics.Color.WHITE
        iconPaint.strokeWidth = r * 0.10f
        iconPaint.strokeCap   = Paint.Cap.ROUND
        val poleX = cx - r * 0.18f
        // Pole
        canvas.drawLine(poleX, cy - r * 0.50f, poleX, cy + r * 0.42f, iconPaint)
        // Flag triangle
        iconPaint.style = Paint.Style.FILL
        val flagPath = android.graphics.Path().apply {
            moveTo(poleX, cy - r * 0.50f)
            lineTo(poleX + r * 0.52f, cy - r * 0.22f)
            lineTo(poleX, cy + r * 0.06f)
            close()
        }
        canvas.drawPath(flagPath, iconPaint)
    } else if (isCheckeredFlag) {
        // Checkered flag icon — ride end marker
        iconPaint.style       = Paint.Style.STROKE
        iconPaint.color       = android.graphics.Color.WHITE
        iconPaint.strokeWidth = r * 0.10f
        iconPaint.strokeCap   = Paint.Cap.ROUND
        val poleX = cx - r * 0.18f
        // Pole
        canvas.drawLine(poleX, cy - r * 0.50f, poleX, cy + r * 0.42f, iconPaint)
        // Checkered flag — 2x2 grid of alternating squares
        val flagW  = r * 0.52f
        val flagH  = r * 0.56f
        val flagX  = poleX
        val flagY  = cy - r * 0.50f
        val cellW  = flagW / 2f
        val cellH  = flagH / 2f
        // Row 0: white | black
        iconPaint.style = Paint.Style.FILL
        iconPaint.color = android.graphics.Color.WHITE
        canvas.drawRect(flagX, flagY, flagX + cellW, flagY + cellH, iconPaint)
        iconPaint.color = android.graphics.Color.BLACK
        canvas.drawRect(flagX + cellW, flagY, flagX + flagW, flagY + cellH, iconPaint)
        // Row 1: black | white
        canvas.drawRect(flagX, flagY + cellH, flagX + cellW, flagY + flagH, iconPaint)
        iconPaint.color = android.graphics.Color.WHITE
        canvas.drawRect(flagX + cellW, flagY + cellH, flagX + flagW, flagY + flagH, iconPaint)
        // Flag outline
        iconPaint.style       = Paint.Style.STROKE
        iconPaint.color       = android.graphics.Color.WHITE
        iconPaint.strokeWidth = r * 0.08f
        canvas.drawRect(flagX, flagY, flagX + flagW, flagY + flagH, iconPaint)
    } else {
        // Gear/cog icon — bike shop marker
        iconPaint.style = Paint.Style.FILL
        iconPaint.color = android.graphics.Color.WHITE
        val gearR      = r * 0.36f
        val innerR     = r * 0.16f
        val toothW     = r * 0.08f
        val toothLen   = r * 0.14f
        val toothCount = 8
        for (i in 0 until toothCount) {
            canvas.save()
            canvas.rotate((i * 360f / toothCount), cx, cy)
            val toothRect = android.graphics.RectF(
                cx - toothW, cy - gearR - toothLen, cx + toothW, cy - gearR
            )
            canvas.drawRect(toothRect, iconPaint)
            canvas.restore()
        }
        canvas.drawCircle(cx, cy, gearR, iconPaint)
        iconPaint.color = bgColor
        canvas.drawCircle(cx, cy, innerR, iconPaint)
        iconPaint.color = android.graphics.Color.WHITE
        canvas.drawCircle(cx, cy, innerR * 0.4f, iconPaint)
    }
    return bmp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, userName: String, openAlertsTab: Boolean = false) {
    val context        = LocalContext.current
    val scope          = rememberCoroutineScope()
    val db             = FirebaseFirestore.getInstance()
    val lifecycleOwner = LocalLifecycleOwner.current

    var showSheet          by remember { mutableStateOf(false) }
    var showExitDialog     by remember { mutableStateOf(false) }
    var showCancelSosDialog by remember { mutableStateOf(false) }

    var selectedItem by rememberSaveable { mutableIntStateOf(if (openAlertsTab) 3 else 1) }

    var isAdmin by remember { mutableStateOf(false) }
    LaunchedEffect(userName) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .whereEqualTo("username", userName)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                isAdmin = snap.documents
                    .firstOrNull()
                    ?.getString("role") == "admin"
            }
    }

    val items = if (isAdmin)
        listOf("Home", "Moderate", "Services", "Alert", "Profile")
    else
        listOf("Home", "Map", "Services", "Alert", "Profile")

    val icons = if (isAdmin) listOf(
        Icons.Filled.Home, Icons.Filled.AdminPanelSettings, Icons.Filled.Build,
        Icons.Filled.Notifications, Icons.Filled.Person
    ) else listOf(
        Icons.Filled.Home, Icons.Filled.Map, Icons.Filled.Build,
        Icons.Filled.Notifications, Icons.Filled.Person
    )

    var searchQuery       by remember { mutableStateOf("") }
    var searchSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    // Persists across recompositions AND app restarts (saved to Bundle)
    // Recent searches — persists across app kills via SharedPreferences
    val prefs = context.getSharedPreferences("PedalConnectPrefs", Context.MODE_PRIVATE)
    var recentSearches by remember {
        mutableStateOf(
            prefs.getString("recent_searches", "")
                ?.split("||")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        )
    }
    fun saveToRecents(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val updated = (listOf(trimmed) + recentSearches.filter {
            !it.equals(trimmed, ignoreCase = true)
        }).take(5)
        recentSearches = updated
        prefs.edit().putString("recent_searches", updated.joinToString("||")).apply()
    }
    fun clearRecents() {
        recentSearches = emptyList()
        prefs.edit().remove("recent_searches").apply()
    }
    var isLocationEnabled by remember { mutableStateOf(true) }
    var currentRoute      by remember { mutableStateOf<Polyline?>(null) }
    var altRoute          by remember { mutableStateOf<Polyline?>(null) }
    var destinationPoint  by remember { mutableStateOf<GeoPoint?>(null) }

    data class RouteOption(
        val label: String, val description: String, val distanceKm: Double,
        val durationMin: Int, val points: List<GeoPoint>,
        val color: androidx.compose.ui.graphics.Color
    )
    var routeOptions     by remember { mutableStateOf<List<RouteOption>>(emptyList()) }
    var selectedRouteIdx by remember { mutableIntStateOf(0) }
    var showRoutePanel   by remember { mutableStateOf(false) }
    var routeDismissed   by remember { mutableStateOf(false) }  // true once user taps Navigate
    var isLoadingRoute   by remember { mutableStateOf(false) }
    var activePolylines  by remember { mutableStateOf<List<Polyline>>(emptyList()) }

    data class TurnStep(val instruction: String, val distanceM: Double)
    var turnSteps         by remember { mutableStateOf<List<TurnStep>>(emptyList()) }
    var currentStepIdx    by remember { mutableIntStateOf(0) }
    var showTurnPanel     by remember { mutableStateOf(false) }
    var offRouteCheckDist by remember { mutableDoubleStateOf(0.0) }

    val alerts = remember { mutableStateListOf<AlertItem>() }
    var myLocationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }

    var isTracking          by remember { mutableStateOf(false) }
    var isPaused            by remember { mutableStateOf(false) }
    var totalDistance       by remember { mutableDoubleStateOf(0.0) }
    var lastTrackedLocation by remember { mutableStateOf<Location?>(null) }

    var elapsedSeconds      by remember { mutableLongStateOf(0L) }
    var currentSpeedKmh     by remember { mutableFloatStateOf(0f) }
    var maxSpeedKmh         by remember { mutableFloatStateOf(0f) }
    var elevationGainMeters by remember { mutableDoubleStateOf(0.0) }
    var lastAltitude        by remember { mutableDoubleStateOf(Double.MIN_VALUE) }
    var locationPoints      by remember { mutableStateOf(listOf<GeoPoint>()) }
    var rideStartPoint      by remember { mutableStateOf<GeoPoint?>(null) }
    var rideTrailPolyline   by remember { mutableStateOf<Polyline?>(null) }

    var showRideSummary  by remember { mutableStateOf(false) }
    var summaryDistance  by remember { mutableDoubleStateOf(0.0) }
    var summarySeconds   by remember { mutableLongStateOf(0L) }
    var summaryMaxSpeed  by remember { mutableFloatStateOf(0f) }
    var summaryElevation by remember { mutableDoubleStateOf(0.0) }

    var mapViewRef          by remember { mutableStateOf<MapView?>(null) }
    var isFollowingLocation by remember { mutableStateOf(true) }
    var showSearchOverlay   by remember { mutableStateOf(false) }

    // ── Proximity / shops state ───────────────────────────────────────────────
    var userGeoPoint        by remember { mutableStateOf<GeoPoint?>(null) }
    val nearbyUsers         = remember { mutableStateListOf<NearbyUser>() }
    val nearbyShops         = remember { mutableStateListOf<ShopItem>() }
    var isLoadingShops      by remember { mutableStateOf(false) }
    var fetchFailed         by remember { mutableStateOf(false) }
    // Tapped shop — drives the bottom sheet
    var selectedShop        by remember { mutableStateOf<ShopItem?>(null) }
    var lastOverpassFetchMs by remember { mutableLongStateOf(0L) }
    var lastOverpassCenter  by remember { mutableStateOf<GeoPoint?>(null) }
    var mapFocusFilter      by remember { mutableStateOf<ShopType?>(null) }

    val avgSpeedKmh = if (elapsedSeconds > 0) (totalDistance / 1000.0) / (elapsedSeconds / 3600.0) else 0.0

    LaunchedEffect(isTracking, isPaused) {
        while (isTracking && !isPaused) {
            kotlinx.coroutines.delay(1000L)
            elapsedSeconds++
        }
    }

    fun formatTime(seconds: Long): String {
        val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
        return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
        else String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    fun resetRide() {
        totalDistance = 0.0; elapsedSeconds = 0L; currentSpeedKmh = 0f
        maxSpeedKmh = 0f; elevationGainMeters = 0.0; lastAltitude = Double.MIN_VALUE
        lastTrackedLocation = null; locationPoints = listOf(); rideStartPoint = null
        rideTrailPolyline?.let { mapViewRef?.overlays?.remove(it) }
        rideTrailPolyline = null
    }

    fun checkLocationStatus() {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        isLocationEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    // ── Fetch hospitals & bike shops from Overpass API ────────────────────────
    fun fetchNearbyPlaces(center: GeoPoint) {
        val radiusM = (SERVICES_RADIUS_KM * 1000).toInt()
        val lat = center.latitude; val lon = center.longitude
        val query = """
            [out:json][timeout:15];
            (
              node["amenity"="hospital"](around:$radiusM,$lat,$lon);
              way["amenity"="hospital"](around:$radiusM,$lat,$lon);
              node["shop"="bicycle"](around:$radiusM,$lat,$lon);
              way["shop"="bicycle"](around:$radiusM,$lat,$lon);
            );
            out center tags;
        """.trimIndent()

        isLoadingShops = true
        fetchFailed    = false

        scope.launch(Dispatchers.IO) {
            try {
                val encoded  = java.net.URLEncoder.encode(query, "UTF-8")
                val response = java.net.URL("https://overpass-api.de/api/interpreter?data=$encoded").readText()
                val json     = org.json.JSONObject(response)
                val elements = json.getJSONArray("elements")

                fun parseElement(el: org.json.JSONObject): ShopItem? {
                    val tags = el.optJSONObject("tags") ?: return null
                    val name = tags.optString("name").takeIf { it.isNotBlank() } ?: return null
                    val nodeLat  = el.optDouble("lat", Double.NaN)
                    val nodeLon  = el.optDouble("lon", Double.NaN)
                    val center2  = el.optJSONObject("center")
                    val placeLat = if (!nodeLat.isNaN()) nodeLat else center2?.optDouble("lat", Double.NaN) ?: Double.NaN
                    val placeLon = if (!nodeLon.isNaN()) nodeLon else center2?.optDouble("lon", Double.NaN) ?: Double.NaN
                    if (placeLat.isNaN() || placeLon.isNaN()) return null
                    val type = when {
                        tags.optString("amenity") == "hospital" -> ShopType.HOSPITAL
                        tags.optString("shop")    == "bicycle"  -> ShopType.BIKE_SHOP
                        else -> return null
                    }
                    val address = listOfNotNull(
                        tags.optString("addr:housenumber").takeIf { it.isNotBlank() },
                        tags.optString("addr:street").takeIf { it.isNotBlank() },
                        tags.optString("addr:city").takeIf { it.isNotBlank() }
                    ).joinToString(", ").takeIf { it.isNotBlank() }
                        ?: tags.optString("addr:full").takeIf { it.isNotBlank() }
                        ?: "See map"
                    val openHours = tags.optString("opening_hours").takeIf { it.isNotBlank() }
                        ?: if (type == ShopType.HOSPITAL) "24 hours" else "See location"
                    val dist = fuzzDistance(haversineKm(lat, lon, placeLat, placeLon))
                    return ShopItem(
                        name = name, address = address, distance = "$dist km",
                        distanceKm = haversineKm(lat, lon, placeLat, placeLon),
                        location = GeoPoint(placeLat, placeLon),
                        type = type, rating = 0.0, openHours = openHours
                    )
                }

                val fetched = (0 until elements.length())
                    .mapNotNull { i -> parseElement(elements.getJSONObject(i)) }
                    .sortedBy { it.distanceKm }

                withContext(Dispatchers.Main) {
                    nearbyShops.clear()
                    nearbyShops.addAll(fetched)
                    isLoadingShops = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isLoadingShops = false
                    fetchFailed    = true
                }
            }
        }
    }

    fun emergencyToShopType(emergencyType: String): ShopType? = when {
        emergencyType.contains("flat tire",       ignoreCase = true) -> ShopType.BIKE_SHOP
        emergencyType.contains("mechanical",      ignoreCase = true) -> ShopType.BIKE_SHOP
        emergencyType.contains("stranded",        ignoreCase = true) -> ShopType.BIKE_SHOP
        emergencyType.contains("medical",         ignoreCase = true) -> ShopType.HOSPITAL
        emergencyType.contains("accident",        ignoreCase = true) -> ShopType.HOSPITAL
        emergencyType.contains("road hazard",     ignoreCase = true) -> ShopType.HOSPITAL
        emergencyType.contains("unsafe area",     ignoreCase = true) -> ShopType.HOSPITAL
        else -> null  // "Other" and unknown types show all markers
    }

    fun publishLocation(loc: GeoPoint) {
        // Admin account should not appear as a cyclist on the map
        if (userName.trim().equals("Admin", ignoreCase = true)) return
        userGeoPoint = loc
        val now         = System.currentTimeMillis()
        val lastCenter  = lastOverpassCenter
        val movedEnough = lastCenter == null ||
                haversineKm(loc.latitude, loc.longitude, lastCenter.latitude, lastCenter.longitude) >= OVERPASS_MOVE_KM
        val timeElapsed = (now - lastOverpassFetchMs) >= OVERPASS_INTERVAL_MS
        if (timeElapsed || movedEnough) {
            lastOverpassFetchMs = now
            lastOverpassCenter  = loc
            fetchNearbyPlaces(loc)
        }
        db.collection("userLocations").document(userName)
            .set(mapOf(
                "userName"  to userName,
                "latitude"  to loc.latitude,
                "longitude" to loc.longitude,
                "timestamp" to System.currentTimeMillis()
            ))

    }

    BackHandler {
        when { selectedItem != 1 -> selectedItem = 1; else -> showExitDialog = true }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checkLocationStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        db.collection("alerts").addSnapshotListener { snapshot, e ->
            if (e != null) return@addSnapshotListener
            snapshot?.let {
                alerts.clear()
                for (doc in it.documents) {
                    val docStatus = doc.getString("status") ?: "active"
                    if (docStatus == "resolved") continue
                    alerts.add(AlertItem(
                        id            = doc.id,
                        riderName     = doc.getString("riderName") ?: "Unknown",
                        emergencyType = doc.getString("emergencyType") ?: "Alert",
                        locationName  = doc.getString("locationName") ?: "Resolving...",
                        coordinates   = GeoPoint(
                            doc.getDouble("latitude") ?: 0.0,
                            doc.getDouble("longitude") ?: 0.0
                        ),
                        time          = "",
                        status        = doc.getString("status") ?: "active",
                        responderName = doc.getString("responderName")
                    ))
                }
            }
        }
    }

    // ── Nearby users — reliable periodic poll ─────────────────────────────────
// Replaces the snapshot listener + isFirstFix bulk read combo.
// Polls every 8 seconds once GPS is available so all devices converge
// on the same view regardless of open order or movement.
    LaunchedEffect(Unit) {
        while (true) {
            val center = userGeoPoint ?: myLocationOverlay?.myLocation
            if (center != null) {
                db.collection("userLocations").get()
                    .addOnSuccessListener { snap ->
                        val fresh = mutableListOf<NearbyUser>()
                        for (doc in snap.documents) {
                            val name = doc.getString("userName") ?: continue
                            if (name == userName) continue
                            val lat  = doc.getDouble("latitude")  ?: continue
                            val lon  = doc.getDouble("longitude") ?: continue
                            val ts   = doc.getLong("timestamp")   ?: 0L
                            if (System.currentTimeMillis() - ts > STALE_MS) continue
                            val dist = haversineKm(
                                center.latitude, center.longitude, lat, lon
                            )
                            if (dist <= CYCLIST_RADIUS_KM) {
                                fresh.add(NearbyUser(
                                    userName   = name,
                                    location   = GeoPoint(lat, lon),
                                    distanceKm = fuzzDistance(dist),
                                    lastSeen   = ts
                                ))
                            }
                        }
                        nearbyUsers.clear()
                        nearbyUsers.addAll(fresh)
                    }
            }
            kotlinx.coroutines.delay(8_000L)
        }
    }

    DisposableEffect(Unit) {
        onDispose { db.collection("userLocations").document(userName).delete() }
    }

    // ── Stale location cleanup — runs every 60s on every active client ───────
    // Deletes any userLocation document older than STALE_MS so ghost markers
    // don't linger after a crash or force-kill
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            val cutoff = System.currentTimeMillis() - STALE_MS
            db.collection("userLocations").get()
                .addOnSuccessListener { snap ->
                    for (doc in snap.documents) {
                        val ts = doc.getLong("timestamp") ?: 0L
                        if (ts < cutoff) doc.reference.delete()
                    }
                }
        }
    }

    SideEffect {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))
        Configuration.getInstance().userAgentValue = context.packageName
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) checkLocationStatus()
    }

    LaunchedEffect(Unit) {
        checkLocationStatus()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    fun fetchRoutes(start: GeoPoint, end: GeoPoint, map: MapView) {
        isLoadingRoute = true; showRoutePanel = false; showTurnPanel = false
        routeDismissed = false
        turnSteps = emptyList()
        activePolylines.forEach { map.overlays.remove(it) }; activePolylines = emptyList()
        val profiles = listOf(
            Triple("cycling-road",    "Fastest",  android.graphics.Color.argb(220, 25, 118, 210)),
            Triple("cycling-regular", "Quietest", android.graphics.Color.argb(220, 46, 125,  50))
        )
        scope.launch(Dispatchers.IO) {
            val fetched = mutableListOf<RouteOption>()
            profiles.forEachIndexed { idx, (profile, label, _) ->
                try {
                    val url = "https://api.openrouteservice.org/v2/directions/$profile" +
                            "?api_key=${com.darkhorses.PedalConnect.BuildConfig.ORS_API_KEY}" +
                            "&start=${start.longitude},${start.latitude}" +
                            "&end=${end.longitude},${end.latitude}"
                    val json     = JSONObject(URL(url).readText())
                    val features = json.getJSONArray("features")
                    if (features.length() == 0) return@forEachIndexed
                    val feature  = features.getJSONObject(0)
                    val props    = feature.getJSONObject("properties")
                    val summary  = props.getJSONArray("segments").getJSONObject(0)
                    val distKm   = summary.getDouble("distance") / 1000.0
                    val durMin   = (summary.getDouble("duration") / 60.0).toInt()
                    val steps    = mutableListOf<TurnStep>()
                    val segs     = props.getJSONArray("segments")
                    for (s in 0 until segs.length()) {
                        val stepsArr = segs.getJSONObject(s).getJSONArray("steps")
                        for (st in 0 until stepsArr.length()) {
                            val step = stepsArr.getJSONObject(st)
                            steps.add(TurnStep(
                                step.optString("instruction", "Continue"),
                                step.optDouble("distance", 0.0)
                            ))
                        }
                    }
                    val coords = feature.getJSONObject("geometry").getJSONArray("coordinates")
                    val points = (0 until coords.length()).map {
                        val c = coords.getJSONArray(it)
                        GeoPoint(c.getDouble(1), c.getDouble(0))
                    }
                    val desc = when (profile) {
                        "cycling-road" -> "Fastest path · road optimised"
                        else           -> "Quieter roads · safer for cyclists"
                    }
                    fetched.add(RouteOption(
                        label, desc, distKm, durMin, points,
                        when (profile) {
                            "cycling-road" -> Color(0xFF1976D2)
                            else           -> Color(0xFF2E7D32)
                        }
                    ))
                    if (idx == 0) withContext(Dispatchers.Main) { turnSteps = steps }
                } catch (e: Exception) { e.printStackTrace() }
            }
            withContext(Dispatchers.Main) {
                routeOptions   = fetched
                isLoadingRoute = false
                if (fetched.isEmpty()) return@withContext
                val newPolylines = fetched.mapIndexed { idx, opt ->
                    Polyline().apply {
                        setPoints(opt.points)
                        outlinePaint.color = android.graphics.Color.argb(
                            if (idx == selectedRouteIdx) 220 else 80,
                            (opt.color.red   * 255).toInt(),
                            (opt.color.green * 255).toInt(),
                            (opt.color.blue  * 255).toInt()
                        )
                        outlinePaint.strokeWidth = if (idx == selectedRouteIdx) 12f else 8f
                        outlinePaint.strokeCap   = Paint.Cap.ROUND
                        outlinePaint.strokeJoin  = Paint.Join.ROUND
                    }
                }
                newPolylines.forEach { map.overlays.add(it) }
                activePolylines = newPolylines
                showRoutePanel  = true
                showTurnPanel   = true
                currentStepIdx  = 0
                map.invalidate()
                val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPoints(fetched[selectedRouteIdx].points)
                map.zoomToBoundingBox(boundingBox.increaseByScale(1.3f), true, 80)
            }
        }
    }

    fun searchAndRoute(query: String, map: MapView) {
        if (query.isBlank()) return
        isLoadingRoute = true
        scope.launch(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val center  = userGeoPoint ?: myLocationOverlay?.myLocation
                val url = if (center != null) {
                    val delta  = 2.7  // ~300km — same island, blocks cross-island routes
                    val minLon = center.longitude - delta
                    val maxLon = center.longitude + delta
                    val minLat = center.latitude  - delta
                    val maxLat = center.latitude  + delta
                    "https://nominatim.openstreetmap.org/search" +
                            "?q=$encoded&format=json&limit=5&countrycodes=ph" +
                            "&viewbox=$minLon,$maxLat,$maxLon,$minLat&bounded=1"
                } else {
                    "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=5&countrycodes=ph"
                }
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.setRequestProperty("User-Agent", "PedalConnect/1.0 (Android)")
                connection.setRequestProperty("Accept-Language", "en")
                connection.connectTimeout = 10_000; connection.readTimeout = 10_000
                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    withContext(Dispatchers.Main) {
                        isLoadingRoute = false
                        Toast.makeText(context, "Search unavailable (HTTP $responseCode)", Toast.LENGTH_SHORT).show()
                    }; return@launch
                }
                val json = JSONArray(connection.inputStream.bufferedReader().readText())
                if (json.length() == 0) {
                    withContext(Dispatchers.Main) {
                        isLoadingRoute = false
                        Toast.makeText(context,
                            "Location not found nearby. Destinations on other islands are not supported.",
                            Toast.LENGTH_LONG).show()
                    }; return@launch
                }
                val place = json.getJSONObject(0)
                val dest  = GeoPoint(place.getDouble("lat"), place.getDouble("lon"))
                withContext(Dispatchers.Main) {
                    destinationPoint  = dest; searchSuggestions = emptyList()
                    val start = userGeoPoint ?: myLocationOverlay?.myLocation
                    ?: lastTrackedLocation?.let { GeoPoint(it.latitude, it.longitude) }
                    if (start != null) {
                        // Pre-check actual route distance via ORS before committing
                        // Uses a lightweight summary-only request — no geometry needed
                        // This gives us road distance, not straight-line approximation
                        scope.launch(Dispatchers.IO) {
                            try {
                                val apiKey = com.darkhorses.PedalConnect.BuildConfig.ORS_API_KEY
                                val checkUrl = "https://api.openrouteservice.org/v2/directions/cycling-road" +
                                        "?api_key=$apiKey" +
                                        "&start=${start.longitude},${start.latitude}" +
                                        "&end=${dest.longitude},${dest.latitude}"

                                val conn = java.net.URL(checkUrl).openConnection()
                                        as java.net.HttpURLConnection
                                conn.connectTimeout = 10_000
                                conn.readTimeout    = 10_000

                                val responseCode = conn.responseCode
                                if (responseCode != 200) {
                                    withContext(Dispatchers.Main) {
                                        fetchRoutes(start, dest, map)
                                    }
                                    return@launch
                                }

                                val checkJson   = JSONObject(conn.inputStream.bufferedReader().readText())
                                val features    = checkJson.getJSONArray("features")
                                if (features.length() == 0) {
                                    withContext(Dispatchers.Main) {
                                        isLoadingRoute   = false
                                        destinationPoint = null
                                        Toast.makeText(context,
                                            "No cycling route found to that destination.",
                                            Toast.LENGTH_LONG).show()
                                    }
                                    return@launch
                                }

                                val summary      = features.getJSONObject(0)
                                    .getJSONObject("properties")
                                    .getJSONArray("segments")
                                    .getJSONObject(0)
                                val routeDistKm  = summary.getDouble("distance") / 1000.0
                                val routeMinutes = (summary.getDouble("duration") / 60.0).toInt()

                                withContext(Dispatchers.Main) {
                                    when {
                                        routeDistKm > 100.0 -> {
                                            // Actual road distance exceeds 100km — reject
                                            isLoadingRoute   = false
                                            destinationPoint = null
                                            Toast.makeText(
                                                context,
                                                "Route is ${String.format("%.1f", routeDistKm)}km by road " +
                                                        "(~${routeMinutes / 60}h ${routeMinutes % 60}min) — " +
                                                        "too far for a cycling trip. " +
                                                        "PedalConnect supports routes up to 100km.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        else -> {
                                            // Within limit — proceed with full route fetch
                                            fetchRoutes(start, dest, map)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // Network error on pre-check — don't block, just proceed
                                withContext(Dispatchers.Main) {
                                    fetchRoutes(start, dest, map)
                                }
                            }
                        }
                    } else {
                        isLoadingRoute = false
                        Toast.makeText(context,
                            "GPS not ready — move to an open area.",
                            Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isLoadingRoute = false
                    Toast.makeText(context, "Search failed: ${e.message?.take(60)}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    var suggestionJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun fetchSuggestions(query: String) {
        if (query.length < 2) { searchSuggestions = emptyList(); return }
        suggestionJob?.cancel()
        suggestionJob = scope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(300L)   // debounce — cancel if user keeps typing
            try {
                val encoded  = java.net.URLEncoder.encode(query, "UTF-8")
                val center   = userGeoPoint ?: myLocationOverlay?.myLocation
                // Build URL — if we have GPS, restrict to a ~300km viewbox around the user
                // This keeps results on the same island (e.g. Luzon from Manila)
                // while still allowing long-distance cycling destinations like Baguio or Batangas.
                // bounded=1 prevents cross-island results (e.g. Cebu from Manila)
                val url = if (center != null) {
                    val delta = 2.7   // ~300km in degrees — covers same island, blocks cross-island
                    val minLon = center.longitude - delta
                    val maxLon = center.longitude + delta
                    val minLat = center.latitude  - delta
                    val maxLat = center.latitude  + delta
                    "https://nominatim.openstreetmap.org/search" +
                            "?q=$encoded&format=json&limit=5&countrycodes=ph" +
                            "&viewbox=$minLon,$maxLat,$maxLon,$minLat&bounded=1"
                } else {
                    // No GPS yet — fall back to country-level search
                    "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=5&countrycodes=ph"
                }
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.setRequestProperty("User-Agent", "PedalConnect/1.0 (Android)")
                conn.connectTimeout = 5_000; conn.readTimeout = 5_000
                if (conn.responseCode != 200) return@launch
                val json        = JSONArray(conn.inputStream.bufferedReader().readText())
                val suggestions = (0 until json.length()).map { i ->
                    json.getJSONObject(i).getString("display_name").split(",").take(3).joinToString(", ")
                }
                withContext(Dispatchers.Main) { searchSuggestions = suggestions }
            } catch (e: Exception) { /* silent */ }
        }
    }

    // Pick up any pending destination from ride events navigation
    LaunchedEffect(Unit) {
        val pendingDest = prefs.getString("pending_destination", null)
        if (!pendingDest.isNullOrBlank()) {
            prefs.edit().remove("pending_destination").apply()
            searchQuery = pendingDest
            selectedItem = 1
            kotlinx.coroutines.delay(800L)
            val map = mapViewRef
            if (map != null) searchAndRoute(pendingDest, map)
        }
    }

    fun selectRoute(idx: Int, map: MapView) {
        selectedRouteIdx = idx
        activePolylines.forEachIndexed { i, poly ->
            val opt = routeOptions.getOrNull(i) ?: return@forEachIndexed
            poly.outlinePaint.color       = android.graphics.Color.argb(if (i == idx) 220 else 80,
                (opt.color.red * 255).toInt(), (opt.color.green * 255).toInt(), (opt.color.blue * 255).toInt())
            poly.outlinePaint.strokeWidth = if (i == idx) 12f else 8f
        }
        map.invalidate(); currentStepIdx = 0
    }

    fun fetchRoute(start: GeoPoint, end: GeoPoint, map: MapView) { fetchRoutes(start, end, map) }

    fun checkOffRoute(loc: GeoPoint) {
        val route = routeOptions.getOrNull(selectedRouteIdx) ?: return
        if (route.points.isEmpty()) return
        val minDist = route.points.minOf { haversineKm(loc.latitude, loc.longitude, it.latitude, it.longitude) }
        offRouteCheckDist = minDist
        if (minDist > 0.08) { val map = mapViewRef ?: return; fetchRoutes(loc, destinationPoint ?: return, map) }
    }

    DisposableEffect(Unit) {
        val locationManager  = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                // ── GPS spike filter ──────────────────────────────────────────
                // Reject points with poor accuracy or impossible speed
                val accuracyM = location.accuracy
                if (accuracyM > 35f) return  // too inaccurate — skip entirely

                val gp = GeoPoint(location.latitude, location.longitude)
                publishLocation(gp)
                if (showTurnPanel && routeOptions.isNotEmpty()) {
                    checkOffRoute(gp)
                    val steps = turnSteps; val step = steps.getOrNull(currentStepIdx)
                    if (step != null) {
                        val route  = routeOptions.getOrNull(selectedRouteIdx)
                        val target = route?.points?.getOrNull(
                            (currentStepIdx.toFloat() / steps.size * route.points.size).toInt()
                        )
                        if (target != null) {
                            val distToStep = haversineKm(gp.latitude, gp.longitude, target.latitude, target.longitude)
                            if (distToStep < 0.03 && currentStepIdx < steps.size - 1) currentStepIdx++
                        }
                    }
                }
                val instantSpeedKmh = if (location.hasSpeed() && location.speed > 0f) {
                    (location.speed * 3.6f).coerceAtMost(80f)
                } else {
                    lastTrackedLocation?.let { last ->
                        val distM    = last.distanceTo(location)
                        val timeDiff = (location.time - last.time) / 1000f
                        if (timeDiff > 0f && distM > 1f)
                            ((distM / timeDiff) * 3.6f).coerceAtMost(80f)
                        else null
                    } ?: 0f
                }
                // Smooth speed using simple rolling average with previous reading
                // This kills single-frame spikes while keeping real acceleration visible
                val rawSpeedKmh = if (currentSpeedKmh > 0f)
                    (currentSpeedKmh * 0.6f + instantSpeedKmh * 0.4f)
                else
                    instantSpeedKmh
                val targetZoom = when {
                    rawSpeedKmh >= 25f -> 14.0; rawSpeedKmh >= 10f -> 15.5
                    rawSpeedKmh >= 3f  -> 17.0; else -> 17.5
                }
                mapViewRef?.controller?.animateTo(gp, targetZoom, 800L)
                if (isTracking && !isPaused) {
                    lastTrackedLocation?.let { last ->
                        val d        = last.distanceTo(location)
                        val timeDiff = (location.time - last.time) / 1000f // seconds
                        // Implied speed from displacement — reject if physically impossible
                        // 20 m/s = 72 km/h, generous upper bound for a cyclist
                        val impliedSpeedMs = if (timeDiff > 0) d / timeDiff else Float.MAX_VALUE
                        val isSpike = impliedSpeedMs > 20f || d > 100f  // >100m jump in 1s = bad GPS
                        if (d > 2.0 && !isSpike) {
                            totalDistance  += d
                            locationPoints  = locationPoints + gp
                            // Update live trail polyline
                            val trail = rideTrailPolyline ?: Polyline().also { rideTrailPolyline = it }
                            trail.setPoints(locationPoints)
                            trail.outlinePaint.color       = android.graphics.Color.argb(220, 0, 180, 100)
                            trail.outlinePaint.strokeWidth = 10f
                            trail.outlinePaint.strokeCap   = android.graphics.Paint.Cap.ROUND
                            trail.outlinePaint.strokeJoin  = android.graphics.Paint.Join.ROUND
                            mapViewRef?.let { mv ->
                                if (!mv.overlays.contains(trail)) mv.overlays.add(trail)
                                mv.invalidate()
                            }
                        }
                    }
                    lastTrackedLocation = location
                    currentSpeedKmh = rawSpeedKmh
                    if (rawSpeedKmh > maxSpeedKmh) maxSpeedKmh = rawSpeedKmh
                    if (location.hasAltitude()) {
                        val alt = location.altitude
                        if (lastAltitude != Double.MIN_VALUE) { val diff = alt - lastAltitude; if (diff > 0.5) elevationGainMeters += diff }
                        lastAltitude = alt
                    }
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,   1000L, 1f, locationListener)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 1f, locationListener)
        }
        onDispose { if (!isTracking) currentSpeedKmh = 0f; locationManager.removeUpdates(locationListener) }
    }

    if (!isLocationEnabled && selectedItem == 1) {
        AlertDialog(
            onDismissRequest = { },
            title   = { Text("Location Services Disabled") },
            text    = { Text("Please enable GPS to use the map features.") },
            confirmButton = {
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                    colors = ButtonDefaults.buttonColors(containerColor = Green900, contentColor = Color.White)) { Text("Enable GPS") }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon     = {
                            Box {
                                Icon(icons[index], contentDescription = item,
                                    modifier = Modifier.size(22.dp))
                                // Red dot — admin Moderate tab when posts/rides pending
                                if (isAdmin && index == 1) {
                                    var pendingCount by remember { mutableIntStateOf(0) }
                                    LaunchedEffect(Unit) {
                                        val db2 = FirebaseFirestore.getInstance()
                                        db2.collection("posts")
                                            .whereEqualTo("status", "pending")
                                            .addSnapshotListener { snap, _ ->
                                                val postCount = snap?.size() ?: 0
                                                db2.collection("rideEvents")
                                                    .whereEqualTo("status", "pending")
                                                    .get()
                                                    .addOnSuccessListener { rideSnap ->
                                                        pendingCount = postCount + rideSnap.size()
                                                    }
                                            }
                                    }
                                    if (pendingCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 4.dp, y = (-2).dp)
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFD32F2F))
                                        )
                                    }
                                }
                                // Red dot — Alerts tab
                                // Admin: only when there are unattended alerts (no responder yet)
                                // Regular user: any active alert
                                if (index == 3) {
                                    val shouldDot = if (isAdmin) {
                                        alerts.any { it.status == "active" }
                                    } else {
                                        alerts.isNotEmpty()
                                    }
                                    if (shouldDot) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 4.dp, y = (-2).dp)
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFD32F2F))
                                        )
                                    }
                                }
                            }
                        },
                        label    = { Text(item, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        selected = selectedItem == index,
                        onClick  = { selectedItem = index },
                        colors   = NavigationBarItemDefaults.colors(
                            selectedIconColor   = Green900, selectedTextColor   = Green900,
                            indicatorColor      = Green100,
                            unselectedIconColor = Color(0xFF9E9E9E), unselectedTextColor = Color(0xFF9E9E9E)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedItem) {

                0 -> Homepage(
                    navController  = navController,
                    paddingValues  = innerPadding,
                    userName       = userName,
                    userLat        = userGeoPoint?.latitude  ?: 14.5995,
                    userLon        = userGeoPoint?.longitude ?: 120.9842,
                    isAdmin        = isAdmin,
                    onExploreRides = { eventId ->
                        navController.navigate(
                            if (eventId != null) "events/$userName?openEventId=$eventId"
                            else "events/$userName"
                        )
                    }
                )

                1 -> if (isAdmin) {
                    AdminScreen(paddingValues = innerPadding)
                } else Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory  = { ctx ->
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(15.0)
                                controller.setCenter(GeoPoint(14.5995, 120.9842))
                                addMapListener(object : org.osmdroid.events.MapListener {
                                    override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                                        if (isFollowingLocation) { isFollowingLocation = false; myLocationOverlay?.disableFollowLocation() }
                                        return false
                                    }
                                    override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean = false
                                })
                            }.also { mapView ->
                                val overlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), mapView).apply {
                                    enableMyLocation(); enableFollowLocation()
                                    runOnFirstFix {
                                        scope.launch {
                                            myLocation?.let { loc ->
                                                mapView.controller.animateTo(loc)
                                                mapView.controller.setZoom(16.0)
                                                publishLocation(loc)
                                                destinationPoint?.let { fetchRoute(loc, it, mapView) }
                                            }
                                        }
                                    }
                                }
                                myLocationOverlay = overlay; mapView.overlays.add(overlay)
                                currentRoute?.let { mapView.overlays.add(it) }
                                mapViewRef = mapView
                            }
                        },
                        update = { view ->
                            view.onResume()
                            view.overlays.removeAll(view.overlays.filterIsInstance<Marker>() + view.overlays.filterIsInstance<Polygon>())
                            val center = userGeoPoint ?: myLocationOverlay?.myLocation ?: GeoPoint(14.5995, 120.9842)
                            // Draw outer services circle first (stays behind everything)
                            view.overlays.add(0, buildServicesRadiusPolygon(center))
                            // Draw inner cyclist circle on top of it
                            view.overlays.add(1, buildCyclistRadiusPolygon(center))
                            nearbyShops.forEach { shop ->
                                val isHospital  = shop.type == ShopType.HOSPITAL
                                val isRelevant  = mapFocusFilter == null || shop.type == mapFocusFilter
                                val alpha       = if (isRelevant) 255 else 60

                                val markerBitmap = if (isHospital)
                                    makeMarkerBitmap(
                                        view.context,
                                        android.graphics.Color.argb(alpha, 211, 47, 47),
                                        true
                                    )
                                else
                                    makeMarkerBitmap(
                                        view.context,
                                        android.graphics.Color.argb(alpha, 10, 92, 61),
                                        false
                                    )

                                Marker(view).apply {
                                    position = shop.location
                                    title    = shop.name
                                    snippet  = null
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    icon = android.graphics.drawable.BitmapDrawable(view.context.resources, markerBitmap)
                                    // Tapping a dimmed marker clears the filter so user can see all
                                    setOnMarkerClickListener { _, _ ->
                                        if (!isRelevant) {
                                            mapFocusFilter = null
                                        } else {
                                            selectedShop = shop
                                        }
                                        true
                                    }
                                    view.overlays.add(this)
                                }
                            }
                            alerts.forEach { alert ->
                                Marker(view).apply {
                                    position = alert.coordinates
                                    title    = "🆘 ${alert.riderName}: ${alert.emergencyType}"
                                    snippet  = alert.locationName
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    val alertBitmap = makeMarkerBitmap(view.context, android.graphics.Color.argb(255, 211, 47, 47), false, 80, true)
                                    icon = android.graphics.drawable.BitmapDrawable(view.context.resources, alertBitmap)
                                    view.overlays.add(this)
                                    // Fetch display name and update marker title asynchronously
                                    // Falls back to riderName (username) if displayName is blank
                                    scope.launch {
                                        val displayName = try {
                                            fetchUserProfile(alert.riderName, db).displayName
                                                .takeIf { it.isNotBlank() } ?: alert.riderName
                                        } catch (e: Exception) { alert.riderName }
                                        title = "🆘 $displayName: ${alert.emergencyType}"
                                        view.invalidate()
                                    }
                                }
                            }
                            nearbyUsers.forEach { user ->
                                Marker(view).apply {
                                    position = user.location
                                    title    = "🚴 ${user.userName}"
                                    snippet  = "${user.distanceKm} km away"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    // Fetch display name and update marker title asynchronously
                                    scope.launch {
                                        val displayName = fetchUserProfile(user.userName, db).displayName
                                        title = "🚴 $displayName"
                                        view.invalidate()
                                    }
                                    // Default to bike icon marker while photo loads
                                    icon = android.graphics.drawable.BitmapDrawable(
                                        view.context.resources,
                                        makeMarkerBitmap(
                                            context    = view.context,
                                            bgColor    = android.graphics.Color.argb(255, 26, 158, 110),
                                            isHospital = false,
                                            sizePx     = 80,
                                            isAlert    = false,
                                            isCyclist  = true
                                        )
                                    )
                                    view.overlays.add(this)
                                    // Fetch photo URL then update marker icon
                                    db.collection("users")
                                        .whereEqualTo("username", user.userName)
                                        .limit(1)
                                        .get()
                                        .addOnSuccessListener { snap ->
                                            val photoUrl = snap.documents
                                                .firstOrNull()
                                                ?.getString("photoUrl")
                                            if (!photoUrl.isNullOrBlank()) {
                                                scope.launch(Dispatchers.IO) {
                                                    try {
                                                        val stream = java.net.URL(photoUrl)
                                                            .openStream()
                                                        val raw = android.graphics.BitmapFactory
                                                            .decodeStream(stream)
                                                        if (raw != null) {
                                                            // Crop to circle with white border + pin tail
                                                            val size   = 80
                                                            val h      = (size * 1.35f).toInt()
                                                            val bmp    = android.graphics.Bitmap.createBitmap(size, h, android.graphics.Bitmap.Config.ARGB_8888)
                                                            val canvas = android.graphics.Canvas(bmp)
                                                            val cx     = size / 2f
                                                            val r      = size / 2f - 6f
                                                            val paint  = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

                                                            // Draw pin tail
                                                            paint.color = android.graphics.Color.argb(255, 26, 158, 110)
                                                            canvas.drawLine(cx, r * 1.6f + 4f, cx, h.toFloat() - 6f, paint.apply {
                                                                strokeWidth = r * 0.36f
                                                                strokeCap   = android.graphics.Paint.Cap.ROUND
                                                                style       = android.graphics.Paint.Style.STROKE
                                                            })

                                                            // Clip photo to circle
                                                            val scaled = android.graphics.Bitmap.createScaledBitmap(raw, (r * 2).toInt(), (r * 2).toInt(), true)
                                                            paint.style = android.graphics.Paint.Style.FILL
                                                            canvas.drawCircle(cx, r + 4f, r, paint)
                                                            val shader = android.graphics.BitmapShader(scaled, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
                                                            val matrix = android.graphics.Matrix()
                                                            matrix.setTranslate(cx - r, 4f)
                                                            shader.setLocalMatrix(matrix)
                                                            paint.shader = shader
                                                            canvas.drawCircle(cx, r + 4f, r, paint)
                                                            paint.shader = null

                                                            // White border ring
                                                            paint.style       = android.graphics.Paint.Style.STROKE
                                                            paint.color       = android.graphics.Color.WHITE
                                                            paint.strokeWidth = 5f
                                                            canvas.drawCircle(cx, r + 4f, r, paint)

                                                            // Brand green outer ring
                                                            paint.color       = android.graphics.Color.argb(255, 26, 158, 110)
                                                            paint.strokeWidth = 3f
                                                            canvas.drawCircle(cx, r + 4f, r + 1f, paint)

                                                            withContext(Dispatchers.Main) {
                                                                icon = android.graphics.drawable.BitmapDrawable(
                                                                    view.context.resources, bmp
                                                                )
                                                                view.invalidate()
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        // Keep default bike icon if photo fails
                                                    }
                                                }
                                            }
                                        }
                                }
                            }
                            rideStartPoint?.let { start ->
                                val startBitmap = makeMarkerBitmap(
                                    view.context,
                                    android.graphics.Color.argb(255, 255, 214, 0),
                                    isHospital = false,
                                    sizePx     = 64,
                                    isAlert    = false,
                                    isCyclist  = false,
                                    isFlag     = true
                                )
                                Marker(view).apply {
                                    position = start; title = "🚩 Ride Start"; snippet = "Your starting point"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    icon = android.graphics.drawable.BitmapDrawable(view.context.resources, startBitmap)
                                    view.overlays.add(this)
                                }
                            }
                            myLocationOverlay?.let { loc -> if (!view.overlays.contains(loc)) view.overlays.add(loc) }
                            view.invalidate()
                        },
                        onRelease = { view -> view.onPause(); view.onDetach() }
                    )

                    Column(
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = innerPadding.calculateBottomPadding() + 180.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End
                    ) {
                        androidx.compose.animation.AnimatedVisibility(visible = !isFollowingLocation, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                            FloatingActionButton(
                                onClick = {
                                    val loc = userGeoPoint ?: myLocationOverlay?.myLocation
                                    if (loc != null) { mapViewRef?.controller?.animateTo(loc); myLocationOverlay?.enableFollowLocation(); isFollowingLocation = true }
                                },
                                modifier = Modifier.size(44.dp), shape = CircleShape,
                                containerColor = Color.White, contentColor = Green900,
                                elevation = FloatingActionButtonDefaults.elevation(6.dp)
                            ) { Icon(Icons.Default.MyLocation, "Re-center", modifier = Modifier.size(20.dp)) }
                        }
                        FloatingActionButton(
                            onClick = { navController.navigate("directions/$userName") },
                            modifier = Modifier.size(44.dp), shape = CircleShape,
                            containerColor = Color.White, contentColor = Green900,
                            elevation = FloatingActionButtonDefaults.elevation(6.dp)
                        ) { Icon(Icons.Default.Navigation, "Directions", modifier = Modifier.size(20.dp)) }
                    }

                    Column(
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(top = 10.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val focusManager = LocalFocusManager.current
                        fun clearDestination() {
                            searchQuery = ""; searchSuggestions = emptyList(); destinationPoint = null
                            activePolylines.forEach { mapViewRef?.overlays?.remove(it) }
                            activePolylines = emptyList(); routeOptions = emptyList()
                            showRoutePanel = false; showTurnPanel = false
                            routeDismissed = false
                            mapViewRef?.invalidate()
                        }

                        // ── Search pill — tapping opens full-screen overlay ───
                        if (destinationPoint != null) {
                            // Active destination chip
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .shadow(6.dp, RoundedCornerShape(32.dp))
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(Color.White)
                                    .clickable { showRoutePanel = true }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(Green100), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.LocationOn, null, tint = Green900, modifier = Modifier.size(15.dp))
                                }
                                Text(
                                    searchQuery.ifBlank { "Destination" },
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1A1A1A), maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { showSearchOverlay = true }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Edit, "Change destination", tint = Color(0xFF9E9E9E), modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { clearDestination() }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, "Clear", tint = Color(0xFF9E9E9E), modifier = Modifier.size(16.dp))
                                }
                            }
                        } else {
                            // Inactive pill — tapping opens search overlay
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .shadow(8.dp, RoundedCornerShape(32.dp))
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(Color.White)
                                    .clickable { showSearchOverlay = true }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (isLoadingRoute) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Green900, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Search, null, tint = Color(0xFF9E9E9E), modifier = Modifier.size(20.dp))
                                }
                                Text(
                                    if (isLoadingRoute) "Finding route…" else "Where do you want to go?",
                                    fontSize = 15.sp, color = Color(0xFF9E9E9E),
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier.size(32.dp).clip(CircleShape).background(Green100),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.MyLocation, null, tint = Green900, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        // ── Map focus filter banner ───────────────────────────
                        if (mapFocusFilter != null) {
                            val filterLabel = when (mapFocusFilter) {
                                ShopType.HOSPITAL  -> "🏥 Showing hospitals near the alert"
                                ShopType.BIKE_SHOP -> "🔧 Showing bike shops near the alert"
                                else               -> ""
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1565C0).copy(alpha = 0.92f))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    filterLabel,
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = Color.White,
                                    modifier   = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.2f))
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication        = null
                                        ) { mapFocusFilter = null }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "Show all",
                                        fontSize   = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color      = Color.White
                                    )
                                }
                            }
                        }

                        // ── Start Ride button — hidden for Admin and when tracking ──
                        if (!isAdmin) {
                            AnimatedVisibility(
                                visible = !isTracking,
                                enter   = fadeIn(animationSpec = tween(200)) + expandVertically(),
                                exit    = fadeOut(animationSpec = tween(150)) + shrinkVertically()
                            ) {
                                Button(
                                    onClick = {
                                        resetRide(); isTracking = true; isPaused = false
                                        rideStartPoint = userGeoPoint ?: myLocationOverlay?.myLocation
                                    },
                                    modifier  = Modifier.fillMaxWidth().height(52.dp)
                                        .shadow(8.dp, RoundedCornerShape(16.dp)),
                                    shape     = RoundedCornerShape(16.dp),
                                    colors    = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFFD600),
                                        contentColor   = Color(0xFF1A1A1A)
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(modifier = Modifier.size(30.dp).clip(CircleShape)
                                            .background(Color(0xFF1A1A1A).copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                                        }
                                        Text("Start Ride", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = 0.3.sp)
                                    }
                                }
                            }

                            // ── Compact stats strip — replaces Start Ride slot when tracking ──
                            AnimatedVisibility(
                                visible = isTracking,
                                enter   = fadeIn(animationSpec = tween(200)) + expandVertically(),
                                exit    = fadeOut(animationSpec = tween(150)) + shrinkVertically()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .shadow(6.dp, RoundedCornerShape(18.dp))
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Green900),
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    // Time
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Text(
                                            formatTime(elapsedSeconds),
                                            fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White
                                        )
                                        Text("Time", fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f))
                                    }
                                    Box(Modifier.width(1.dp).height(28.dp).background(Color.White.copy(alpha = 0.2f)))
                                    // Speed
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Row(
                                            verticalAlignment     = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                String.format(Locale.getDefault(), "%.1f", currentSpeedKmh),
                                                fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White
                                            )
                                            Text("km/h", fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f))
                                        }
                                        Text("Speed", fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f))
                                    }
                                    Box(Modifier.width(1.dp).height(28.dp).background(Color.White.copy(alpha = 0.2f)))
                                    // Distance
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Row(
                                            verticalAlignment     = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Box(modifier = Modifier.size(7.dp).clip(CircleShape)
                                                .background(if (isPaused) Color(0xFFFF9800) else Color(0xFF4CAF50)))
                                            Text(
                                                String.format(Locale.getDefault(), "%.2f", totalDistance / 1000.0),
                                                fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White
                                            )
                                            Text("km", fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f))
                                        }
                                        Text("Distance", fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f))
                                    }
                                }       // end outer stats strip Row
                            }           // end AnimatedVisibility (isTracking strip)
                            // ── Active alert banner — sits below search/stats, never overlaps ──
                            val myActiveAlertInline = alerts.firstOrNull {
                                it.riderName.trim().lowercase() == userName.trim().lowercase()
                            }
                            AnimatedVisibility(
                                visible = myActiveAlertInline != null && !showSearchOverlay,
                                enter   = fadeIn() + expandVertically(),
                                exit    = fadeOut() + shrinkVertically()
                            ) {
                                if (myActiveAlertInline != null) {
                                    val bannerColor = when (myActiveAlertInline.status) {
                                        "responding" -> Color(0xFF1565C0)
                                        else         -> Color(0xFFD32F2F)
                                    }
                                    val bannerText = when (myActiveAlertInline.status) {
                                        "responding" -> "🚴 ${myActiveAlertInline.responderName ?: "Someone"} is on the way!"
                                        else         -> "🆘 Your ${myActiveAlertInline.emergencyType} alert is active"
                                    }
                                    Card(
                                        onClick   = { selectedItem = 3 },
                                        modifier  = Modifier.fillMaxWidth(),
                                        shape     = RoundedCornerShape(16.dp),
                                        colors    = CardDefaults.cardColors(containerColor = bannerColor),
                                        elevation = CardDefaults.cardElevation(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment     = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.size(36.dp).clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.18f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    when (myActiveAlertInline.status) {
                                                        "responding" -> Icons.AutoMirrored.Filled.DirectionsBike
                                                        else         -> Icons.Default.Warning
                                                    },
                                                    null, tint = Color.White, modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(bannerText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                                Text("Tap to open Alerts tab", fontSize = 10.sp, color = Color.White.copy(alpha = 0.75f))
                                            }
                                            Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }                   // end if (!isAdmin)
                    }                   // end top Column

                    // ── Full-screen search overlay — animated drop down ────────
                    AnimatedVisibility(
                        visible = showSearchOverlay,
                        enter   = slideInVertically(
                            initialOffsetY = { -it },
                            animationSpec  = tween(320, easing = FastOutSlowInEasing)
                        ) + fadeIn(
                            animationSpec = tween(240, easing = FastOutSlowInEasing)
                        ),
                        exit    = slideOutVertically(
                            targetOffsetY = { -it },
                            animationSpec = tween(260, easing = FastOutSlowInEasing)
                        ) + fadeOut(
                            animationSpec = tween(180, easing = FastOutSlowInEasing)
                        )
                    ) {
                        SearchOverlay(
                            initialQuery   = searchQuery,
                            suggestions    = searchSuggestions,
                            recentSearches = recentSearches,
                            isLoadingRoute = isLoadingRoute,
                            onQueryChange  = { query ->
                                if (query.length >= 2) fetchSuggestions(query)
                                else searchSuggestions = emptyList()
                            },
                            onSearch       = { query ->
                                val map = mapViewRef ?: return@SearchOverlay
                                searchQuery = query
                                saveToRecents(query)
                                showSearchOverlay = false
                                searchSuggestions = emptyList()
                                searchAndRoute(query, map)
                            },
                            onDismiss      = { showSearchOverlay = false; searchSuggestions = emptyList() },
                            onClearRecents = { clearRecents() }
                        )
                    }


                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = innerPadding.calculateBottomPadding() + 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AnimatedVisibility(visible = showTurnPanel && turnSteps.isNotEmpty(),
                            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                            exit  = fadeOut() + slideOutVertically(targetOffsetY = { -it })) {
                            val step = turnSteps.getOrNull(currentStepIdx)
                            if (step != null) {
                                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f)), elevation = CardDefaults.cardElevation(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                        Box(modifier = Modifier.width(4.dp).fillMaxHeight()
                                            .background(Green900, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)))
                                        Row(modifier = Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Green100), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Navigation, null, tint = Green900, modifier = Modifier.size(22.dp))
                                            }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(step.instruction, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1A1A1A), maxLines = 2)
                                            if (step.distanceM > 0)
                                                Text("in ${if (step.distanceM >= 1000) String.format("%.1f km", step.distanceM / 1000) else "${step.distanceM.toInt()} m"}",
                                                    fontSize = 12.sp, color = Color(0xFF7A8F7A), fontWeight = FontWeight.Medium)
                                        }
                                        Text("${currentStepIdx + 1}/${turnSteps.size}", fontSize = 11.sp, color = Color(0xFF9E9E9E), fontWeight = FontWeight.Medium)
                                            IconButton(onClick = { showTurnPanel = false }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.Close, null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                            }
                                        } // end inner Row
                                    } // end outer Row with accent bar
                                }
                            }
                        }

                        AnimatedVisibility(visible = showRoutePanel && !routeDismissed && routeOptions.isNotEmpty(),
                            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                            exit  = fadeOut() + slideOutVertically(targetOffsetY = { it })) {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f)), elevation = CardDefaults.cardElevation(8.dp)) {
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // ── Drag handle — tap to dismiss route panel ──
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showRoutePanel = false
                                                routeDismissed = true
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, "Dismiss",
                                            tint = Color(0xFFBBBBBB), modifier = Modifier.size(22.dp))
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        routeOptions.forEachIndexed { idx, opt ->
                                            val isSelected = idx == selectedRouteIdx
                                            Column(
                                                modifier = Modifier.weight(1f).height(100.dp).clip(RoundedCornerShape(14.dp))
                                                    .background(if (isSelected) opt.color.copy(alpha = 0.10f) else Color(0xFFF5F5F5))
                                                    .border(if (isSelected) 2.dp else 1.dp, if (isSelected) opt.color else Color(0xFFE0E0E0), RoundedCornerShape(14.dp))
                                                    .clickable {
                                                        val map = mapViewRef ?: return@clickable
                                                        selectRoute(idx, map)
                                                        // Tapping a route card = selection + confirmation
                                                        // Turn-by-turn starts immediately, no extra button needed
                                                        showRoutePanel = false
                                                        routeDismissed = true
                                                        showTurnPanel  = true
                                                        currentStepIdx = 0
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                                    Icon(if (idx == 0) Icons.Default.Speed else Icons.AutoMirrored.Filled.DirectionsBike, null, tint = opt.color, modifier = Modifier.size(13.dp))
                                                    Text(opt.label, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = if (isSelected) opt.color else Color(0xFF1A1A1A))
                                                }
                                                Text(String.format("%.1f km", opt.distanceKm), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = if (isSelected) opt.color else Color(0xFF1A1A1A))
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                    Text("${opt.durationMin} min", fontSize = 11.sp, color = Color(0xFF9E9E9E))
                                                    if (idx == 1) Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFE8F5E9)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                        Text("✓ Best", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = !isAdmin && (isTracking || totalDistance > 0) && !showSearchOverlay,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                            exit  = fadeOut() + slideOutVertically(targetOffsetY = { it })) {
                            val nearbyHospitalsCard = nearbyShops.count { it.type == ShopType.HOSPITAL }
                            val nearbyBikeShopsCard = nearbyShops.count { it.type == ShopType.BIKE_SHOP }
                            var cardExpanded by remember { mutableStateOf(false) }
                            LaunchedEffect(isTracking) { if (!isTracking) cardExpanded = true }
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f)), elevation = CardDefaults.cardElevation(8.dp)) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // ── Always-visible controls row: pills (left) + pause/stop (right) ──
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { cardExpanded = !cardExpanded }
                                            .padding(horizontal = 20.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Pills — only shown when tracking
                                        if (isTracking) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Cyclists pill
                                                Row(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(20.dp))
                                                        .background(Green100)
                                                        .padding(horizontal = 7.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                                                    Icon(Icons.AutoMirrored.Filled.DirectionsBike, null, tint = Green900, modifier = Modifier.size(11.dp))
                                                    Text("${nearbyUsers.size}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Green900)
                                                }
                                                // Hospitals pill
                                                Row(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(20.dp))
                                                        .background(Color(0xFFD32F2F))
                                                        .padding(horizontal = 7.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(Icons.Default.LocalHospital, null, tint = Color.White, modifier = Modifier.size(11.dp))
                                                    Text("$nearbyHospitalsCard", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                                }
                                                // Shops pill
                                                Row(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(20.dp))
                                                        .background(Color(0xFFE0F2F1))
                                                        .padding(horizontal = 7.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(Icons.Default.HomeRepairService, null, tint = Color(0xFF00796B), modifier = Modifier.size(11.dp))
                                                    Text("$nearbyBikeShopsCard", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00796B))
                                                }
                                            }
                                        } else {
                                            // Not tracking — show status label instead
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF9E9E9E)))
                                                Text("Ride Complete", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF9E9E9E))
                                            }
                                        }
                                        // Pause + Stop buttons (tracking only) or chevron (post-ride)
                                        if (isTracking) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                IconButton(
                                                    onClick  = { isPaused = !isPaused; if (isPaused) currentSpeedKmh = 0f },
                                                    modifier = Modifier.size(42.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color(0xFFF5F5F5))
                                                ) {
                                                    Icon(
                                                        if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                                        null,
                                                        tint     = if (isPaused) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick  = {
                                                        summaryDistance  = totalDistance
                                                        summarySeconds   = elapsedSeconds
                                                        summaryMaxSpeed  = maxSpeedKmh
                                                        summaryElevation = elevationGainMeters
                                                        isTracking       = false
                                                        isPaused         = false
                                                        currentSpeedKmh  = 0f
                                                        showRideSummary  = true
                                                    },
                                                    modifier = Modifier.size(42.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color(0xFFD32F2F).copy(alpha = 0.85f))
                                                ) {
                                                    Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        } else {
                                            Icon(
                                                if (cardExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                                null, tint = Color(0xFFBBBBBB), modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    // ── Expandable stats — always auto-expanded post-ride, hidden during tracking ──
                                    AnimatedVisibility(visible = cardExpanded && !isTracking && totalDistance > 0, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            HorizontalDivider(color = Color(0xFFE8EDE8))
                                            TextButton(onClick = { resetRide() }, modifier = Modifier.fillMaxWidth()) {
                                                Text("Clear & Reset", color = Color(0xFF7A8F7A), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }


                        if (!isTracking && totalDistance == 0.0 || isTracking) {
                            val nearbyHospitals2 = nearbyShops.count { it.type == ShopType.HOSPITAL }
                            val nearbyBikeShops2 = nearbyShops.count { it.type == ShopType.BIKE_SHOP }
                            val myActiveSosAlert = alerts.firstOrNull {
                                it.riderName.trim().lowercase() == userName.trim().lowercase()
                            }
                            val hasActiveAlert = myActiveSosAlert != null

                            AnimatedVisibility(
                                visible = !showSearchOverlay && !isTracking,
                                enter   = fadeIn(animationSpec = tween(200)),
                                exit    = fadeOut(animationSpec = tween(150))
                            ) {
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color.White.copy(alpha = 0.92f))
                                            .padding(horizontal = 11.dp, vertical = 6.dp),
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Box(modifier = Modifier.size(6.dp).clip(CircleShape)
                                            .background(Color(0xFF4CAF50)))
                                        Icon(Icons.AutoMirrored.Filled.DirectionsBike, null,
                                            tint     = Green900,
                                            modifier = Modifier.size(13.dp))
                                        Text("${nearbyUsers.size} nearby", fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color      = Green900)
                                    }
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color(0xFFD32F2F))
                                            .padding(horizontal = 11.dp, vertical = 6.dp),
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(Icons.Default.LocalHospital, null,
                                            tint     = Color.White,
                                            modifier = Modifier.size(13.dp))
                                        Text("$nearbyHospitals2 hosp.", fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color      = Color.White)
                                    }
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color(0xFFE0F2F1))
                                            .padding(horizontal = 11.dp, vertical = 6.dp),
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(Icons.Default.HomeRepairService, null,
                                            tint     = Color(0xFF00796B),
                                            modifier = Modifier.size(13.dp))
                                        Text("$nearbyBikeShops2 shops", fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color      = Color(0xFF00796B))
                                    }
                                }
                            }

                            if (!isAdmin) {
                                AnimatedVisibility(
                                    visible = !showSearchOverlay,
                                    enter   = fadeIn(animationSpec  = tween(200)),
                                    exit    = fadeOut(animationSpec = tween(150))
                                ) {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(72.dp)
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(
                                                if (hasActiveAlert) Color(0xFF2E7D32).copy(alpha = 0.18f)
                                                else Color(0xFFD32F2F).copy(alpha = 0.18f)
                                            )
                                    )
                                    val sosPulse = rememberInfiniteTransition(label = "sos")
                                    val sosScale by sosPulse.animateFloat(
                                        initialValue = 1f, targetValue = if (!hasActiveAlert) 1.03f else 1f,
                                        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                                        label = "sosScale"
                                    )
                                    Button(
                                        onClick = {
                                            if (hasActiveAlert) showCancelSosDialog = true
                                            else showSheet = true
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 4.dp)
                                            .height(64.dp)
                                            .graphicsLayer { scaleX = sosScale; scaleY = sosScale }
                                            .shadow(16.dp, RoundedCornerShape(20.dp)),
                                        shape  = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (hasActiveAlert) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                        ),
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                    ) {
                                        Row(
                                            verticalAlignment     = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.size(36.dp).clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    if (hasActiveAlert) Icons.Default.Cancel else Icons.Default.Warning,
                                                    null, modifier = Modifier.size(20.dp), tint = Color.White
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.Start) {
                                                Text(
                                                    if (hasActiveAlert) "CANCEL SOS" else "SOS",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize   = 20.sp, letterSpacing = 4.sp,
                                                    color      = Color.White
                                                )
                                                Text(
                                                    if (hasActiveAlert) "Tap to cancel your active alert"
                                                    else "Tap to send distress signal",
                                                    fontSize   = 9.sp, fontWeight = FontWeight.Medium,
                                                    color      = Color.White.copy(alpha = 0.8f),
                                                    letterSpacing = 0.3.sp
                                                )
                                            }
                                        }
                                    }
                                }
                                }
                            } // end if (!isAdmin)
                        } // end if (!isTracking && totalDistance == 0.0)
                    } // end bottom Column

                    // ── Shop detail bottom sheet ──────────────────────────────
                    if (!isAdmin) selectedShop?.let { shop ->
                        ShopDetailSheet(
                            shop       = shop,
                            onDismiss  = { selectedShop = null },
                            onNavigate = { location ->
                                destinationPoint = location
                                selectedShop     = null
                                val start = userGeoPoint ?: myLocationOverlay?.myLocation
                                if (start != null) {
                                    mapViewRef?.let { fetchRoutes(start, location, it) }
                                }
                            }
                        )
                    }
                } // end map tab else branch

                2 -> ShopsScreen(
                    paddingValues    = innerPadding,
                    shops            = nearbyShops.toList(),
                    isLoadingShops   = isLoadingShops,
                    fetchFailed      = fetchFailed,
                    onRetry          = { userGeoPoint?.let { fetchNearbyPlaces(it) } },
                    onDirectionClick = { destination ->
                        destinationPoint = destination
                        mapFocusFilter   = null  // manual navigation shows all markers
                        selectedItem     = 1
                    }
                )

                3 -> AlertsScreen(
                    paddingValues    = innerPadding,
                    helperName       = userName,
                    isAdmin          = isAdmin,
                    onNavigateToHelp = { coordinates, emergencyType ->
                        destinationPoint = coordinates
                        mapFocusFilter   = emergencyToShopType(emergencyType)
                        selectedItem     = 1
                    },
                    onImOnMyWay      = { alert -> Toast.makeText(context, "Notifying ${alert.riderName} you're coming!", Toast.LENGTH_SHORT).show() },
                )

                4 -> ProfileScreen(
                    navController = navController,
                    userName      = userName,
                    paddingValues = innerPadding
                )

                else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("${items[selectedItem]} Screen Coming Soon")
                }
            }

            if (showRideSummary) {
                RideSummarySheet(
                    userName         = userName,
                    distanceM        = summaryDistance,
                    durationSeconds  = summarySeconds,
                    maxSpeedKmh      = summaryMaxSpeed,
                    elevationM       = summaryElevation,
                    rideStartPoint   = rideStartPoint,
                    destinationPoint = destinationPoint,
                    locationPoints   = locationPoints,
                    formatTime       = ::formatTime,
                    onDismiss        = { showRideSummary = false; resetRide() }
                )
            }

            if (showExitDialog) {
                AlertDialog(
                    onDismissRequest = { showExitDialog = false },
                    shape            = RoundedCornerShape(24.dp),
                    containerColor   = Color.White,
                    icon = {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape)
                                .background(Color(0xFFFEF2F2)),
                            Alignment.Center
                        ) {
                            Icon(Icons.Default.ExitToApp, null,
                                tint     = Color(0xFFD32F2F),
                                modifier = Modifier.size(28.dp))
                        }
                    },
                    title = {
                        Text("Exit PedalConnect?",
                            fontWeight = FontWeight.Bold, fontSize = 18.sp,
                            color      = Color(0xFF111827),
                            textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier   = Modifier.fillMaxWidth())
                    },
                    text = {
                        Text("Are you sure you want to exit the app?",
                            fontSize  = 14.sp, color = Color(0xFF6B7280),
                            lineHeight = 22.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    },
                    confirmButton = {
                        Column(Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick  = { (context as? android.app.Activity)?.finish() },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape    = RoundedCornerShape(14.dp),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD32F2F),
                                    contentColor   = Color.White)
                            ) {
                                Text("Exit", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            }
                            OutlinedButton(
                                onClick  = { showExitDialog = false },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape    = RoundedCornerShape(14.dp),
                                border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD1D5DB)),
                                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF374151))
                            ) {
                                Text("Stay", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            }
                        }
                    }
                )
            }

            if (showCancelSosDialog) {
                val alertToCancel = alerts.firstOrNull {
                    it.riderName.trim().lowercase() == userName.trim().lowercase()
                }
                AlertDialog(
                    onDismissRequest = { showCancelSosDialog = false },
                    shape            = RoundedCornerShape(20.dp),
                    containerColor   = Color.White,
                    icon = {
                        Box(
                            modifier = Modifier.size(56.dp).clip(CircleShape)
                                .background(Green100),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Cancel, null,
                                tint = Green900, modifier = Modifier.size(28.dp))
                        }
                    },
                    title = {
                        Text("Cancel SOS Alert?",
                            fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                            color = Color(0xFF1A1A1A),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth())
                    },
                    text = {
                        Text(
                            "This will close your active ${alertToCancel?.emergencyType ?: "emergency"} alert. Only cancel if you no longer need help.",
                            fontSize = 14.sp, color = Color.Gray, lineHeight = 20.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    },
                    confirmButton = {
                        Column(Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    showCancelSosDialog = false
                                    alertToCancel?.let { alert ->
                                        db.collection("alerts").document(alert.id)
                                            .update("status", "resolved")
                                            .addOnSuccessListener {
                                                Toast.makeText(context,
                                                    "Alert cancelled. Stay safe!",
                                                    Toast.LENGTH_SHORT).show()
                                                // Notify responder if one was assigned
                                                if (!alert.responderName.isNullOrBlank()) {
                                                    db.collection("notifications").add(hashMapOf(
                                                        "userName"  to alert.responderName,
                                                        "message"   to "$userName has cancelled their ${alert.emergencyType} alert.",
                                                        "type"      to "alert",
                                                        "timestamp" to System.currentTimeMillis(),
                                                        "read"      to false
                                                    ))
                                                }
                                            }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape    = RoundedCornerShape(12.dp),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = Green900, contentColor = Color.White)
                            ) {
                                Text("Yes, Cancel Alert",
                                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            OutlinedButton(
                                onClick  = { showCancelSosDialog = false },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape    = RoundedCornerShape(12.dp),
                                border   = androidx.compose.foundation.BorderStroke(
                                    1.dp, Color(0xFFDDDDDD))
                            ) {
                                Text("Keep Alert Active",
                                    color = Color.Gray, fontSize = 14.sp)
                            }
                        }
                    }
                )
            }

            if (showSheet) {
                SosSheet(
                    userName     = userName,
                    userGeoPoint = userGeoPoint,
                    onDismiss    = { showSheet = false }
                )
            }
        }
    }
}