package com.darkhorses.PedalConnect.ui.theme

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding

// ─────────────────────────────────────────────────────────────────────────────
// Design Tokens
// ─────────────────────────────────────────────────────────────────────────────
private val Green950  = Color(0xFF052818)
private val Green900  = Color(0xFF06402B)
private val Green800  = Color(0xFF0A5C3D)
private val Green700  = Color(0xFF0D7050)
private val Green500  = Color(0xFF1A9E6E)
private val Green100  = Color(0xFFDDF1E8)
private val Green50   = Color(0xFFF0FAF5)

private val Amber500  = Color(0xFFF59E0B)
private val Amber50   = Color(0xFFFFFBEB)
private val Amber100  = Color(0xFFFEF3C7)
private val Red600    = Color(0xFFDC2626)
private val Red50     = Color(0xFFFEF2F2)
private val Red100    = Color(0xFFFFE4E4)
private val Orange600 = Color(0xFFEA580C)
private val Orange50  = Color(0xFFFFF7ED)

private fun diffFg(d: String) = when (d) {
    "Easy"     -> Color(0xFF166534)
    "Moderate" -> Color(0xFF9A3412)
    "Hard"     -> Color(0xFF991B1B)
    else       -> Color(0xFF374151)
}
private fun diffBg(d: String) = when (d) {
    "Easy"     -> Color(0xFFDCFCE7)
    "Moderate" -> Color(0xFFFFEDD5)
    "Hard"     -> Color(0xFFFFE4E6)
    else       -> Color(0xFFF3F4F6)
}

private val BgCanvas      = Color(0xFFF5F7F6)
private val BgSurface     = Color(0xFFFFFFFF)
private val TextPrimary   = Color(0xFF111827)
private val TextSecondary = Color(0xFF374151)
private val TextMuted     = Color(0xFF6B7280)
private val DividerColor  = Color(0xFFE5E7EB)
private val BorderDefault = Color(0xFFD1D5DB)

// ─────────────────────────────────────────────────────────────────────────────
// Data model
// ─────────────────────────────────────────────────────────────────────────────
data class RideEvent(
    val id: String                 = "",
    val title: String              = "",
    val description: String        = "",
    val route: String              = "",
    val date: Long                 = 0L,
    val time: String               = "",
    val organizer: String          = "",
    val participants: List<String> = emptyList(),
    val maxParticipants: Int       = 0,
    val difficulty: String         = "Easy",
    val distanceKm: Double         = 0.0,
    val timestamp: Long            = 0L,
    val isEdited: Boolean          = false,
    val editedAt: Long             = 0L,
    val attendees: List<String>    = emptyList(),
    val checkInOpen: Boolean       = false,
    val status: String             = "pending",
    val durationHours: Int         = 0
)

// ─────────────────────────────────────────────────────────────────────────────
// Date / time helpers
// ─────────────────────────────────────────────────────────────────────────────
private fun formatEventDate(ts: Long): String {
    if (ts == 0L) return "Date TBA"
    return SimpleDateFormat("EEE, MMM d · yyyy", Locale.getDefault()).format(Date(ts))
}
private fun formatRelativeTime(ts: Long): String {
    if (ts == 0L) return ""
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000L    -> "just now"
        diff < 3_600_000L -> "${diff / 60_000}m ago"
        diff < 86_400_000L-> "${diff / 3_600_000}h ago"
        else              -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
    }
}
private enum class EventTiming { TODAY, UPCOMING, PAST }

private fun eventTiming(ts: Long): EventTiming {
    val now      = Calendar.getInstance()
    val eventDay = Calendar.getInstance().apply { timeInMillis = ts }
    val sameYear = now.get(Calendar.YEAR)        == eventDay.get(Calendar.YEAR)
    val sameDay  = now.get(Calendar.DAY_OF_YEAR) == eventDay.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && sameDay                      -> EventTiming.TODAY
        eventDay.timeInMillis > now.timeInMillis -> EventTiming.UPCOMING
        else                                     -> EventTiming.PAST
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Time-based event status (used by EventCard badges + color indicator)
// ─────────────────────────────────────────────────────────────────────────────
enum class EventStatus { UPCOMING, HAPPENING_NOW, ENDED }

fun getEventTimeStatus(event: RideEvent): EventStatus {
    // Pending/rejected events are never "ended" — they haven't happened yet officially
    if (event.status == "pending" || event.status == "rejected") return EventStatus.UPCOMING
    val now = System.currentTimeMillis()
    val startMs = if (event.time.isNotBlank()) {
        try {
            val parsed  = SimpleDateFormat("h:mm a", Locale.getDefault()).parse(event.time)
            val timeCal = Calendar.getInstance().apply { timeInMillis = parsed?.time ?: 0L }
            Calendar.getInstance().apply {
                timeInMillis = event.date
                set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE,      timeCal.get(Calendar.MINUTE))
                set(Calendar.SECOND,      0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } catch (e: Exception) { event.date }
    } else {
        Calendar.getInstance().apply {
            timeInMillis = event.date
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val estimatedHours = when {
        event.durationHours > 0  -> event.durationHours          // organizer set it explicitly
        event.distanceKm >= 80.0 -> 6                            // long ride
        event.distanceKm >= 30.0 -> 4                            // medium ride
        event.distanceKm >= 1.0  -> 2                            // short ride
        else                     -> 3                            // no distance — default
    }
    val endMs = startMs + estimatedHours * 60 * 60 * 1000L
    return when {
        now < startMs - 60 * 60 * 1000L -> EventStatus.UPCOMING
        now > endMs                      -> EventStatus.ENDED
        else                             -> EventStatus.HAPPENING_NOW
    }
}

private fun isCheckInWindow(event: RideEvent): Boolean {
    if (eventTiming(event.date) != EventTiming.TODAY) return false
    val now = System.currentTimeMillis()
    val cal = Calendar.getInstance().apply { timeInMillis = event.date }
    val startMs = if (event.time.isNotBlank()) {
        try {
            val parsed = SimpleDateFormat("h:mm a", Locale.getDefault()).parse(event.time)
            cal.set(Calendar.HOUR_OF_DAY,
                Calendar.getInstance().apply { timeInMillis = parsed?.time ?: 0L }.get(Calendar.HOUR_OF_DAY))
            cal.set(Calendar.MINUTE,
                Calendar.getInstance().apply { timeInMillis = parsed?.time ?: 0L }.get(Calendar.MINUTE))
            cal.timeInMillis
        } catch (e: Exception) { event.date }
    } else event.date
    return now in (startMs - 3_600_000L)..(startMs + 14_400_000L)
}

// ─────────────────────────────────────────────────────────────────────────────
// OSM Route Map helpers
// ─────────────────────────────────────────────────────────────────────────────
internal data class RouteResult(
    val points: List<GeoPoint> = emptyList(),
    val startPoint: GeoPoint?  = null,
    val endPoint: GeoPoint?    = null,
    val distanceKm: Double     = 0.0,
    val error: String?         = null
)

internal suspend fun geocodePlace(placeName: String): GeoPoint? =
    withContext(Dispatchers.IO) {
        val key = placeName.trim().lowercase()
        if (geocodeCache.containsKey(key)) return@withContext geocodeCache[key]
        nominatimThrottle()
        try {
            val encoded  = URLEncoder.encode(placeName.trim(), "UTF-8")
            val url      = "https://nominatim.openstreetmap.org/search" +
                    "?q=$encoded&format=json&limit=1&countrycodes=ph"
            val conn = URL(url).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("User-Agent", "PedalConnect/1.0 (Android; pedalconnect@gmail.com)")
            conn.connectTimeout = 8000
            conn.readTimeout    = 8000
            if (conn.responseCode != 200) { geocodeCache[key] = null; return@withContext null }
            val response = conn.inputStream.bufferedReader().readText()
            val array = JSONArray(response)
            if (array.length() == 0) { geocodeCache[key] = null; return@withContext null }
            val obj = array.getJSONObject(0)
            val result = GeoPoint(obj.getDouble("lat"), obj.getDouble("lon"))
            geocodeCache[key] = result
            result
        } catch (e: Exception) { null }
    }

internal suspend fun fetchCyclingRoute(start: GeoPoint, end: GeoPoint): RouteResult =
    withContext(Dispatchers.IO) {
        try {
            val url = "https://router.project-osrm.org/route/v1/bike/" +
                    "${start.longitude},${start.latitude};" +
                    "${end.longitude},${end.latitude}" +
                    "?overview=full&geometries=geojson"
            val conn = URL(url).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("User-Agent", "PedalConnect/1.0")
            conn.connectTimeout = 10000
            conn.readTimeout    = 10000
            if (conn.responseCode != 200) {
                return@withContext RouteResult(error = "Server error ${conn.responseCode}")
            }
            val response = conn.inputStream.bufferedReader().readText()
            val json   = JSONObject(response)
            val routes = json.getJSONArray("routes")
            if (routes.length() == 0) return@withContext RouteResult(error = "No route found")
            val route     = routes.getJSONObject(0)
            val distanceM = route.getDouble("distance")
            val coords    = route.getJSONObject("geometry").getJSONArray("coordinates")
            val points    = (0 until coords.length()).map { i ->
                val c = coords.getJSONArray(i)
                GeoPoint(c.getDouble(1), c.getDouble(0))
            }
            RouteResult(points = points, startPoint = start, endPoint = end, distanceKm = distanceM / 1000.0)
        } catch (e: Exception) {
            RouteResult(error = "Could not load route: ${e.message?.take(40)}")
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
// EventRouteMap composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun EventRouteMap(routeText: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var routeResult   by remember(routeText) { mutableStateOf<RouteResult?>(null) }
    var isLoading     by remember(routeText) { mutableStateOf(false) }
    var loadAttempted by remember(routeText) { mutableStateOf(false) }

    val parts = remember(routeText) {
        routeText.split(" to ", " - ", "–", " To ")
            .map { it.trim() }.filter { it.isNotBlank() }
    }
    val canShowMap = parts.size >= 2

    LaunchedEffect(routeText) {
        if (!canShowMap || loadAttempted) return@LaunchedEffect
        loadAttempted = true
        isLoading = true
        val start = geocodePlace(parts.first())
        val end   = geocodePlace(parts.last())
        routeResult = when {
            start == null || end == null ->
                RouteResult(error = "Could not find locations on the map")
            else -> fetchCyclingRoute(start, end)
        }
        isLoading = false
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        when {
            !canShowMap -> MapPlaceholder(
                icon = Icons.Default.Map,
                message = "Enter route as \"Start to End\" to see map"
            )
            isLoading -> Box(
                Modifier.fillMaxSize().background(Color(0xFFF1F5F9)),
                Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(color = Green900, strokeWidth = 2.5.dp,
                        modifier = Modifier.size(28.dp))
                    Text("Loading route map…", fontSize = 13.sp, color = TextSecondary)
                }
            }
            routeResult?.error != null -> MapPlaceholder(
                icon = Icons.Default.Warning,
                message = "Map unavailable for this route",
                tint = Amber500
            )
            routeResult != null -> {
                val result = routeResult!!
                Configuration.getInstance().userAgentValue = "PedalConnect/1.0"

                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(false) // disable — embedded in scroll sheet
                            isClickable = false
                            controller.setZoom(13.0)
                        }
                    },
                    update = { mapView ->
                        mapView.overlays.clear()
                        if (result.points.isNotEmpty()) {
                            val polyline = Polyline(mapView).apply {
                                setPoints(result.points)
                                outlinePaint.color = android.graphics.Color.parseColor("#06402B")
                                outlinePaint.strokeWidth = 8f
                                outlinePaint.alpha = 220
                            }
                            mapView.overlays.add(polyline)
                        }
                        result.startPoint?.let { pt ->
                            mapView.overlays.add(Marker(mapView).apply {
                                position = pt
                                title    = "Start: ${parts.first()}"
                                icon     = androidx.core.content.ContextCompat.getDrawable(
                                    context, android.R.drawable.ic_menu_mylocation)
                            })
                        }
                        result.endPoint?.let { pt ->
                            mapView.overlays.add(Marker(mapView).apply {
                                position = pt
                                title    = "End: ${parts.last()}"
                                icon     = androidx.core.content.ContextCompat.getDrawable(
                                    context, android.R.drawable.ic_menu_myplaces)
                            })
                        }
                        // Post zoom so it runs after the map view has been laid out
                        if (result.points.size > 1) {
                            val box = BoundingBox.fromGeoPoints(result.points).increaseByScale(1.4f)
                            mapView.post {
                                try {
                                    mapView.zoomToBoundingBox(box, false, 40)
                                } catch (e: Exception) {
                                    // fallback — center on midpoint
                                    val mid = result.points[result.points.size / 2]
                                    mapView.controller.setZoom(13.0)
                                    mapView.controller.setCenter(mid)
                                }
                            }
                        }
                        mapView.invalidate()
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Distance badge
                if (result.distanceKm > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Green900.copy(alpha = 0.9f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            "🚴 ${String.format("%.1f", result.distanceKm)} km by bike",
                            fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // OSM attribution (required by license)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.8f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("© OpenStreetMap contributors", fontSize = 9.sp, color = Color(0xFF374151))
                }

                // Top hint
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("Route preview · use View button for full map",
                        fontSize = 10.sp, color = Color.White)
                }
            }
        }
    }
}


@Composable
internal fun MapPlaceholder(
    icon: ImageVector,
    message: String,
    tint: Color = Color(0xFFCBD5E1)
) {
    Box(Modifier.fillMaxSize().background(Color(0xFFF1F5F9)), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(32.dp))
            Text(message, fontSize = 13.sp, color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main Screen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RidingEventsScreen(
    navController: NavController,
    userName: String,
    openEventId: String? = null
) {
    val db      = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val events          = remember { mutableStateListOf<RideEvent>() }
    var isLoading       by remember { mutableStateOf(true) }
    var isRefreshing    by remember { mutableStateOf(false) }
    var showCreateSheet  by remember { mutableStateOf(false) }
    var draftTitle       by remember { mutableStateOf("") }
    var draftDescription by remember { mutableStateOf("") }
    var draftOrigin      by remember { mutableStateOf("") }
    var draftDest        by remember { mutableStateOf("") }
    var draftTime        by remember { mutableStateOf("") }
    var draftDistance    by remember { mutableStateOf("") }
    var draftMaxPax      by remember { mutableStateOf("") }
    var draftDifficulty        by remember { mutableStateOf("Easy") }
    var draftDate              by remember { mutableStateOf(0L) }
    var draftOriginConfirmed   by remember { mutableStateOf(false) }
    var draftDestConfirmed     by remember { mutableStateOf(false) }
    var draftDuration          by remember { mutableStateOf(0) }
    var showEditSheet   by remember { mutableStateOf(false) }
    var editingEvent    by remember { mutableStateOf<RideEvent?>(null) }
    var selectedEvent   by remember { mutableStateOf<RideEvent?>(null) }
    var autoOpenHandled by remember { mutableStateOf(false) }
    var filterTab          by remember { mutableStateOf(0) }
    var difficultyFilter   by remember { mutableStateOf("All") }


    // Firestore listener
    LaunchedEffect(Unit) {
        val reg = db.collection("rideEvents")
            .orderBy("date", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(context, "Failed to load events.", Toast.LENGTH_SHORT).show()
                    isLoading = false; return@addSnapshotListener
                }
                val loaded = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        RideEvent(
                            id              = doc.id,
                            title           = doc.getString("title")           ?: "",
                            description     = doc.getString("description")     ?: "",
                            route           = doc.getString("route")           ?: "",
                            date            = doc.getLong("date")              ?: 0L,
                            time            = doc.getString("time")            ?: "",
                            organizer       = doc.getString("organizer")       ?: "",
                            participants    = (doc.get("participants") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                            maxParticipants = (doc.getLong("maxParticipants")  ?: 0L).toInt(),
                            difficulty      = doc.getString("difficulty")      ?: "Easy",
                            distanceKm      = doc.getDouble("distanceKm")      ?: 0.0,
                            timestamp       = doc.getLong("timestamp")         ?: 0L,
                            isEdited        = doc.getBoolean("isEdited")       ?: false,
                            editedAt        = doc.getLong("editedAt")          ?: 0L,
                            attendees       = (doc.get("attendees") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                            checkInOpen     = doc.getBoolean("checkInOpen")    ?: false,
                            status          = doc.getString("status")          ?: "approved",
                            durationHours   = (doc.getLong("durationHours")    ?: 0L).toInt()
                        )
                    } catch (ex: Exception) { null }
                } ?: emptyList()
                events.clear(); events.addAll(loaded)
                isLoading = false

                // Send "ride today" reminder for events happening today
                // that the user is participating in — only once per event per day
                val todayEvents = loaded.filter { event ->
                    eventTiming(event.date) == EventTiming.TODAY &&
                            event.participants.contains(userName) &&
                            event.status == "approved"
                }
                todayEvents.forEach { event ->
                    val reminderKey = "reminder_${event.id}_${
                        SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                    }"
                    // Check if reminder already sent today for this event
                    db.collection("notifications")
                        .whereEqualTo("userName", userName)
                        .whereEqualTo("reminderKey", reminderKey)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { existing ->
                            if (existing.isEmpty) {
                                db.collection("notifications").add(hashMapOf(
                                    "userName"    to userName,
                                    "message"     to "Reminder: \"${event.title}\" is happening today${if (event.time.isNotBlank()) " at ${event.time}" else ""}. Get ready! 🚴",
                                    "type"        to "ride",
                                    "eventId"     to event.id,
                                    "reminderKey" to reminderKey,
                                    "timestamp"   to System.currentTimeMillis(),
                                    "read"        to false
                                ))
                            }
                        }
                }
            }
        try { awaitCancellation() } finally { reg.remove() }
    }

    // Auto-open from homepage
    LaunchedEffect(openEventId, events.size) {
        if (!autoOpenHandled && openEventId != null && events.isNotEmpty()) {
            val target = events.firstOrNull { it.id == openEventId }
            if (target != null) { selectedEvent = target; autoOpenHandled = true }
        }
    }

    // Keep selectedEvent in sync with live Firestore updates
    // so the sheet always shows fresh data and closing it doesn't
    // cause stale state that breaks the list
    LaunchedEffect(events.toList()) {
        selectedEvent?.let { current ->
            val fresh = events.firstOrNull { it.id == current.id }
            if (fresh != null) selectedEvent = fresh
        }
    }

    val displayedEvents by remember(events.toList(), filterTab, userName, difficultyFilter) {
        derivedStateOf {
            val safeEvents = events.toList()
            val base = when (filterTab) {
                1 -> safeEvents.filter {
                    // My Rides — everything the user is involved in, past and future
                    (it.organizer == userName || it.participants.contains(userName)) &&
                            (it.status == "approved" || it.organizer == userName)
                }.sortedByDescending { it.date }
                else -> safeEvents.filter {
                    // Upcoming — approved events that haven't ended
                    eventTiming(it.date) != EventTiming.PAST &&
                            getEventTimeStatus(it) != EventStatus.ENDED &&
                            (it.status == "approved" || it.organizer == userName || it.participants.contains(userName))
                }
            }
            if (difficultyFilter == "All") base
            else base.filter { it.difficulty == difficultyFilter }
        }
    }

    // Actions
    fun deleteEvent(event: RideEvent, onSuccess: () -> Unit) {
        db.collection("rideEvents").document(event.id).delete()
            .addOnSuccessListener { Toast.makeText(context, "Ride deleted.", Toast.LENGTH_SHORT).show(); onSuccess() }
            .addOnFailureListener { Toast.makeText(context, "Failed to delete.", Toast.LENGTH_SHORT).show() }
    }
    fun checkIn(event: RideEvent) {
        if (event.attendees.contains(userName)) {
            Toast.makeText(context, "Already checked in.", Toast.LENGTH_SHORT).show(); return
        }
        db.collection("rideEvents").document(event.id)
            .update("attendees", FieldValue.arrayUnion(userName))
            .addOnSuccessListener { Toast.makeText(context, "Checked in! See you on the road 🚴", Toast.LENGTH_SHORT).show() }
            .addOnFailureListener { Toast.makeText(context, "Check-in failed. Try again.", Toast.LENGTH_SHORT).show() }
    }
    fun toggleAttendance(event: RideEvent, participant: String) {
        val ref = db.collection("rideEvents").document(event.id)
        if (event.attendees.contains(participant)) ref.update("attendees", FieldValue.arrayRemove(participant))
        else ref.update("attendees", FieldValue.arrayUnion(participant))
    }
    fun toggleCheckInOpen(event: RideEvent) {
        db.collection("rideEvents").document(event.id)
            .update("checkInOpen", !event.checkInOpen)
            .addOnSuccessListener {
                Toast.makeText(context,
                    if (!event.checkInOpen) "Check-in opened for riders" else "Check-in closed",
                    Toast.LENGTH_SHORT).show()
            }
    }
    fun toggleJoin(event: RideEvent) {
        val isJoined = event.participants.contains(userName)
        val isFull   = event.maxParticipants > 0 && event.participants.size >= event.maxParticipants && !isJoined
        if (isFull) { Toast.makeText(context, "This event is full.", Toast.LENGTH_SHORT).show(); return }
        val ref = db.collection("rideEvents").document(event.id)
        if (isJoined) {
            ref.update("participants", FieldValue.arrayRemove(userName))
                .addOnSuccessListener {
                    Toast.makeText(context, "Left the ride.", Toast.LENGTH_SHORT).show()
                    // Remove the join notification when user leaves
                    db.collection("notifications")
                        .whereEqualTo("userName", userName)
                        .whereEqualTo("eventId", event.id)
                        .whereEqualTo("type", "ride")
                        .get()
                        .addOnSuccessListener { snap ->
                            snap.documents.forEach { it.reference.delete() }
                        }
                }
        } else {
            ref.update("participants", FieldValue.arrayUnion(userName))
                .addOnSuccessListener {
                    Toast.makeText(context, "Joined! 🚴", Toast.LENGTH_SHORT).show()
                    // Send join confirmation to the joining user
                    db.collection("notifications").add(hashMapOf(
                        "userName"  to userName,
                        "message"   to "You joined \"${event.title}\" on ${formatEventDate(event.date)}${if (event.time.isNotBlank()) " at ${event.time}" else ""}. See you on the road! 🚴",
                        "type"      to "ride",
                        "eventId"   to event.id,
                        "timestamp" to System.currentTimeMillis(),
                        "read"      to false
                    ))
                    // Notify the organizer that someone joined their ride
                    if (event.organizer != userName) {
                        db.collection("notifications").add(hashMapOf(
                            "userName"  to event.organizer,
                            "message"   to "$userName joined your ride \"${event.title}\" — ${event.participants.size + 1} rider${if (event.participants.size + 1 != 1) "s" else ""} so far.",
                            "type"      to "ride",
                            "eventId"   to event.id,
                            "timestamp" to System.currentTimeMillis(),
                            "read"      to false
                        ))
                    }
                }
        }
    }

    // Delete dialog
    var showDeleteDialog by remember { mutableStateOf(false) }
    var eventToDelete    by remember { mutableStateOf<RideEvent?>(null) }

    if (showDeleteDialog && eventToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; eventToDelete = null },
            shape = RoundedCornerShape(24.dp), containerColor = BgSurface,
            icon = {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Red50), Alignment.Center) {
                    Icon(Icons.Default.DeleteForever, null, tint = Red600, modifier = Modifier.size(28.dp))
                }
            },
            title = { Text("Delete this ride?", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                color = TextPrimary, textAlign = TextAlign.Center) },
            text = { Text("Removing the event is permanent. All participants will lose their spot.",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 22.sp, textAlign = TextAlign.Center) },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        eventToDelete?.let { ev ->
                            deleteEvent(ev) { showDeleteDialog = false; eventToDelete = null; selectedEvent = null }
                        }
                    }, modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Red600, contentColor = Color.White)
                    ) { Text("Delete ride", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
                    OutlinedButton(onClick = { showDeleteDialog = false; eventToDelete = null },
                        modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDefault),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) { Text("Cancel", fontWeight = FontWeight.Medium, fontSize = 15.sp) }
                }
            }
        )
    }

    // Sheets
    selectedEvent?.let { event ->
        EventDetailSheet(
            event = event, userName = userName,
            onJoin              = { toggleJoin(event) },
            onDelete            = { eventToDelete = event; showDeleteDialog = true },
            onEdit              = { editingEvent = event; showEditSheet = true },
            onCheckIn           = { checkIn(event) },
            onToggleAttendance  = { p -> toggleAttendance(event, p) },
            onToggleCheckInOpen = { toggleCheckInOpen(event) },
            onDismiss           = { selectedEvent = null },
            onNavigate          = { destination ->
                // Navigate to home map tab with destination pre-filled
                navController.navigate("home/$userName") {
                    popUpTo("home/$userName") { inclusive = false }
                }
                // Pass destination via shared prefs so HomeScreen can pick it up
                val prefs = context.getSharedPreferences("PedalConnectPrefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putString("pending_destination", destination).apply()
            }
        )
    }

    if (showEditSheet && editingEvent != null) {
        EditEventSheet(
            event = editingEvent!!,
            onDismiss = { showEditSheet = false; editingEvent = null },
            onSave = { updated ->
                db.collection("rideEvents").document(updated.id).update(
                    mapOf(
                        "title" to updated.title, "description" to updated.description,
                        "route" to updated.route, "time" to updated.time,
                        "distanceKm" to updated.distanceKm,
                        "maxParticipants" to updated.maxParticipants,
                        "difficulty" to updated.difficulty,
                        "durationHours" to updated.durationHours,
                        "isEdited" to true, "editedAt" to System.currentTimeMillis()
                    )
                ).addOnSuccessListener {
                    showEditSheet = false; editingEvent = null
                    Toast.makeText(context, "Ride updated!", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener {
                    Toast.makeText(context, "Failed to update.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showCreateSheet) {
        CreateEventSheet(
            userName               = userName,
            initialTitle           = draftTitle,
            initialDesc            = draftDescription,
            initialOrigin          = draftOrigin,
            initialDest            = draftDest,
            initialTime            = draftTime,
            initialDistance        = draftDistance,
            initialMaxPax          = draftMaxPax,
            initialDiff            = draftDifficulty,
            initialDate            = draftDate,
            initialOriginConfirmed = draftOriginConfirmed,
            initialDestConfirmed   = draftDestConfirmed,
            initialDuration        = draftDuration,
            onDraftChange   = { t, desc, orig, dest, time, dist, pax, diff, date, origConf, destConf, dur ->
                draftTitle = t; draftDescription = desc; draftOrigin = orig
                draftDest = dest; draftTime = time; draftDistance = dist
                draftMaxPax = pax; draftDifficulty = diff; draftDate = date
                draftOriginConfirmed = origConf; draftDestConfirmed = destConf
                draftDuration = dur
            },
            onDismiss = { showCreateSheet = false },
            onCreate = { newEvent ->
                draftTitle = ""; draftDescription = ""; draftOrigin = ""
                draftDest = ""; draftTime = ""; draftDistance = ""
                draftMaxPax = ""; draftDifficulty = "Easy"; draftDate = 0L
                draftOriginConfirmed = false; draftDestConfirmed = false
                db.collection("rideEvents").add(hashMapOf(
                    "title" to newEvent.title, "description" to newEvent.description,
                    "route" to newEvent.route, "date" to newEvent.date,
                    "time" to newEvent.time, "organizer" to newEvent.organizer,
                    "participants" to listOf(newEvent.organizer),
                    "maxParticipants" to newEvent.maxParticipants,
                    "difficulty" to newEvent.difficulty, "distanceKm" to newEvent.distanceKm,
                    "timestamp" to System.currentTimeMillis(),
                    "attendees" to emptyList<String>(), "checkInOpen" to false,
                    "status" to "pending",
                    "durationHours" to newEvent.durationHours
                )).addOnSuccessListener {
                    showCreateSheet = false
                    Toast.makeText(context, "Ride submitted for review! 🎉", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener {
                    Toast.makeText(context, "Failed to create event.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Scaffold
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ride Events", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Green900),
                modifier = Modifier.shadow(elevation = 2.dp)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateSheet = true },
                containerColor = Green900, contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, "Create", modifier = Modifier.size(20.dp)) },
                text = { Text("New Ride", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            )
        },
        containerColor = BgCanvas
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh    = { scope.launch { isRefreshing = true; delay(800); isRefreshing = false } },
            modifier     = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                item { HeroBanner(upcomingCount = events.count {
                    eventTiming(it.date) != EventTiming.PAST &&
                            getEventTimeStatus(it) != EventStatus.ENDED &&
                            it.status == "approved"
                }) }
                item {
                    FilterTabs(
                        selected = filterTab,
                        onSelect = { filterTab = it; difficultyFilter = "All" },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        tabs     = listOf("Upcoming", "My Rides")
                    )
                    // Difficulty filter chips — only relevant for Upcoming tab
                    if (filterTab == 0) androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        items(listOf("All", "Easy", "Moderate", "Hard")) { level ->
                            val isSelected = difficultyFilter == level
                            val chipColor = when {
                                !isSelected -> BgSurface
                                level == "Easy" -> Color(0xFF166534)
                                level == "Moderate" -> Color(0xFF9A3412)
                                level == "Hard" -> Color(0xFF991B1B)
                                else -> Green900
                            }
                            val textColor = when {
                                !isSelected -> TextMuted
                                else -> Color.White
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(chipColor)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color.Transparent else BorderDefault,
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { difficultyFilter = level }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    level, fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = textColor
                                )
                            }
                        }
                    }
                }

                when {
                    isLoading -> item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), Alignment.Center) {
                            CircularProgressIndicator(color = Green900, strokeWidth = 2.5.dp, modifier = Modifier.size(32.dp))
                        }
                    }
                    displayedEvents.isEmpty() -> item { EmptyState(filterTab) }
                    else -> items(displayedEvents, key = { it.id }) { event ->
                        EventCard(event = event, userName = userName,
                            onJoin = { toggleJoin(event) }, onTap = { selectedEvent = event },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero Banner
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HeroBanner(upcomingCount: Int) {
    Box(Modifier.fillMaxWidth()
        .background(Brush.verticalGradient(listOf(Green950, Green800)))
        .padding(horizontal = 20.dp, vertical = 24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Group Rides", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                color = Color.White, letterSpacing = (-0.5).sp)
            Spacer(Modifier.height(2.dp))
            Text(when {
                upcomingCount == 0 -> "No upcoming rides yet — be the first!"
                upcomingCount == 1 -> "1 upcoming ride near you"
                else               -> "$upcomingCount upcoming rides near you"
            }, fontSize = 14.sp, color = Color.White.copy(alpha = 0.72f), lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroPill(Icons.Default.Groups, "Community")
                HeroPill(Icons.Default.Route, "All distances")
                HeroPill(Icons.Default.VerifiedUser, "Safe rides")
            }
        }
    }
}

@Composable
private fun HeroPill(icon: ImageVector, label: String) {
    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.12f))
        .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(12.dp))
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Filter Tabs
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun FilterTabs(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<String> = listOf("Upcoming", "My Rides")
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        color = BgSurface, shadowElevation = 1.dp) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEachIndexed { idx, label ->
                val isSelected = selected == idx
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Green900 else Color.Transparent)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(idx) }
                    .padding(vertical = 10.dp), Alignment.Center) {
                    Text(label, fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) Color.White else TextMuted)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty State
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EmptyState(filterTab: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Green50), Alignment.Center) {
            Icon(Icons.Default.DirectionsBike,
                null, tint = Green700, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(when (filterTab) { 1 -> "No rides yet"; else -> "No upcoming rides" },
            fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPrimary)
        Text(when (filterTab) {
            1    -> "Rides you create or join — past and upcoming — appear here"
            else -> "Tap New Ride to organise a group ride"
        }, fontSize = 14.sp, color = TextSecondary, textAlign = TextAlign.Center, lineHeight = 21.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Event Card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EventCard(
    event: RideEvent, userName: String,
    onJoin: () -> Unit, onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isJoined    = event.participants.contains(userName)
    val isOrganizer = event.organizer == userName
    val isFull      = event.maxParticipants > 0 && event.participants.size >= event.maxParticipants && !isJoined
    val spotsLeft   = if (event.maxParticipants > 0) event.maxParticipants - event.participants.size else -1

    val cardStatus = getEventTimeStatus(event)
    val statusBorderColor = when {
        event.status == "pending"               -> Amber500
        event.status == "rejected"              -> Red600
        cardStatus == EventStatus.ENDED         -> Color(0xFF9CA3AF)
        cardStatus == EventStatus.HAPPENING_NOW -> Color(0xFFD97706)
        else                                    -> Green500
    }

    Card(modifier = modifier.fillMaxWidth().clickable { onTap() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (cardStatus == EventStatus.ENDED) Color(0xFFF3F4F6) else BgSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 3.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // ── Status left border ────────────────────────────────────────
            Box(
                Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(
                        statusBorderColor,
                        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
            )
            // ── Card content ──────────────────────────────────────────────
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(4.dp).background(
                    Brush.horizontalGradient(listOf(statusBorderColor, statusBorderColor.copy(alpha = 0.3f))),
                    RoundedCornerShape(topEnd = 16.dp)))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(event.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                            color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp)
                        // Badges
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusChip(event.difficulty, diffFg(event.difficulty), diffBg(event.difficulty))
                            when (getEventTimeStatus(event)) {
                                EventStatus.HAPPENING_NOW -> StatusChip("Happening Now", Color(0xFF92400E), Color(0xFFFEF3C7))
                                EventStatus.ENDED         -> StatusChip("Ended", Color(0xFF6B7280), Color(0xFFF3F4F6))
                                EventStatus.UPCOMING      -> if (eventTiming(event.date) == EventTiming.TODAY) StatusChip("Today", Green800, Green50)
                            }
                            if (isOrganizer)              StatusChip("You", Green900, Green100)
                            if (isJoined && !isOrganizer) StatusChip("Joined", Green700, Green100)
                            // Verified badge
                            if (event.status == "approved") {
                                Row(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFECFDF5))
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Icon(Icons.Default.VerifiedUser, null, tint = Color(0xFF059669),
                                        modifier = Modifier.size(10.dp))
                                    Text("Verified", fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF059669))
                                }
                            }
                            if (event.status == "pending") StatusChip("Pending review", Amber500, Amber50)
                            if (event.status == "rejected") StatusChip("Rejected", Red600, Red50)
                        }
                    }
                }

                if (event.isEdited) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Edit, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                        Text("Edited ${formatRelativeTime(event.editedAt)}", fontSize = 11.sp, color = TextMuted)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.CalendarMonth, null, tint = Green700, modifier = Modifier.size(14.dp))
                        Text(formatEventDate(event.date), fontSize = 12.sp, color = TextSecondary)
                    }
                    if (event.time.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Schedule, null, tint = Green700, modifier = Modifier.size(14.dp))
                            Text(event.time, fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    if (event.distanceKm > 0) {
                        Spacer(Modifier.weight(1f))
                        Text(String.format("%.0f km", event.distanceKm), fontSize = 12.sp,
                            color = Green700, fontWeight = FontWeight.SemiBold)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.LocationOn, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    Text(event.route.ifBlank { "Route TBA" }, fontSize = 12.sp, color = TextSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }

                HorizontalDivider(color = DividerColor, thickness = 0.5.dp)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    AvatarStack(participants = event.participants, maxParticipants = event.maxParticipants)
                    if (!isOrganizer) {
                        val isEnded = cardStatus == EventStatus.ENDED
                        Button(onClick = onJoin, enabled = !isFull && !isEnded, shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(36.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor         = if (isJoined) Color(0xFFF3F4F6) else Green900,
                                contentColor           = if (isJoined) TextSecondary else Color.White,
                                disabledContainerColor = Color(0xFFE5E7EB),
                                disabledContentColor   = TextMuted),
                            contentPadding = PaddingValues(horizontal = 14.dp)) {
                            Icon(if (isJoined) Icons.Default.CheckCircle else Icons.Default.DirectionsBike,
                                null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(when { isEnded -> "Ended"; isFull -> "Full"; isJoined -> "Joined"; else -> "Join" },
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        if (event.maxParticipants > 0) {
                            Text(if (isFull) "Full" else "$spotsLeft spots left",
                                fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                color = if (isFull) Red600 else Green700)
                        }
                    }
                }
            }
            } // end Column (card content)
        } // end Row
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Atoms
// ─────────────────────────────────────────────────────────────────────────────
// Atoms
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StatusChip(label: String, textColor: Color, bgColor: Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(bgColor).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}

@Composable
private fun AvatarStack(participants: List<String>, maxParticipants: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            participants.take(4).forEachIndexed { i, name ->
                Box(Modifier.padding(start = (i * 18).dp).size(28.dp).clip(CircleShape)
                    .background(Color.White).padding(1.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Green800, Green500))), Alignment.Center) {
                    Text(name.take(1).uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        Spacer(Modifier.width((minOf(participants.size, 4) * 18).dp))
        Text(buildString {
            append("${participants.size}")
            if (maxParticipants > 0) append("/$maxParticipants")
            append(" rider${if (participants.size != 1) "s" else ""}")
        }, fontSize = 12.sp, color = TextSecondary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Event Detail Sheet
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDetailSheet(
    event: RideEvent, userName: String,
    onJoin: () -> Unit, onDelete: () -> Unit = {}, onEdit: () -> Unit = {},
    onCheckIn: () -> Unit = {}, onToggleAttendance: (String) -> Unit = {},
    onToggleCheckInOpen: () -> Unit = {}, onDismiss: () -> Unit,
    onNavigate: (String) -> Unit = {}
) {
    val sheetState  = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isJoined    = event.participants.contains(userName)
    val isOrganizer = event.organizer == userName
    val isFull      = event.maxParticipants > 0 && event.participants.size >= event.maxParticipants && !isJoined
    val db          = FirebaseFirestore.getInstance()

    // Load active alerts matching this route
    var routeAlerts by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(event.route) {
        if (event.route.isBlank()) return@LaunchedEffect
        db.collection("alerts").whereEqualTo("status", "active").get()
            .addOnSuccessListener { snap ->
                val lower = event.route.lowercase()
                routeAlerts = snap.documents.mapNotNull { doc ->
                    val loc = doc.getString("location") ?: return@mapNotNull null
                    val msg = doc.getString("message") ?: doc.getString("description") ?: ""
                    val matches = loc.split(" ", ",", "-").any { w ->
                        w.length > 3 && lower.contains(w.lowercase())
                    }
                    if (matches) if (msg.isNotBlank()) "⚠️ $loc — $msg" else "⚠️ $loc" else null
                }
            }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = BgSurface, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 4.dp).width(36.dp).height(4.dp)
                .clip(RoundedCornerShape(2.dp)).background(Color(0xFFD1D5DB)))
        }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 32.dp)
            .statusBarsPadding().navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(0.dp)) {

            // Title + badges
            Text(event.title, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                color = TextPrimary, lineHeight = 26.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusChip(event.difficulty, diffFg(event.difficulty), diffBg(event.difficulty))
                if (isOrganizer)              StatusChip("Your ride", Green900, Green100)
                if (isJoined && !isOrganizer) StatusChip("Joined", Green700, Green100)
                when (getEventTimeStatus(event)) {
                    EventStatus.HAPPENING_NOW -> StatusChip("Happening Now", Color(0xFF92400E), Color(0xFFFEF3C7))
                    EventStatus.ENDED         -> StatusChip("Ended", Color(0xFF6B7280), Color(0xFFF3F4F6))
                    EventStatus.UPCOMING      -> if (eventTiming(event.date) == EventTiming.TODAY) StatusChip("Today", Green800, Green50)
                }
                // Verified by admin badge
                if (event.status == "approved") {
                    Row(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFECFDF5))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Default.VerifiedUser, null, tint = Color(0xFF059669),
                            modifier = Modifier.size(12.dp))
                        Text("Verified by admin", fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold, color = Color(0xFF059669))
                    }
                }
                if (event.isEdited) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Default.Edit, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                        Text("Edited ${formatRelativeTime(event.editedAt)}", fontSize = 11.sp, color = TextMuted)
                    }
                }
            }

            // Pending / rejected notice for organizer
            if (isOrganizer && event.status == "pending") {
                Spacer(Modifier.height(12.dp))
                InlineAlert(Icons.Default.HourglassTop, Amber500, Amber50, Amber100,
                    "This ride is awaiting admin approval before it becomes visible to other riders.")
            }
            if (isOrganizer && event.status == "rejected") {
                Spacer(Modifier.height(12.dp))
                InlineAlert(Icons.Default.Cancel, Red600, Red50, Red100,
                    "This ride was not approved by an admin. Please review and edit before resubmitting.")
            }

            // Edited warning for joined members
            if (isJoined && !isOrganizer && event.isEdited) {
                Spacer(Modifier.height(12.dp))
                InlineAlert(Icons.Default.Info, Amber500, Amber50, Amber100,
                    "This ride was updated after you joined — please review the details.")
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = DividerColor)
            Spacer(Modifier.height(16.dp))

            // Key details
            Text("Details", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = TextMuted, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailTile(Icons.Default.CalendarMonth, "Date", formatEventDate(event.date))
                if (event.time.isNotBlank())
                    DetailTile(Icons.Default.Schedule, "Start time", event.time)

                // Route tile with View on Maps button
                if (event.route.isNotBlank()) {
                    val routeContext = LocalContext.current
                    val mapsUrl = remember(event.route) {
                        val parts = event.route.split(" to ", " - ", "–").map { it.trim() }
                        if (parts.size >= 2) {
                            val origin = android.net.Uri.encode(parts.first())
                            val dest   = android.net.Uri.encode(parts.last())
                            "https://www.google.com/maps/dir/?api=1&origin=$origin&destination=$dest&travelmode=bicycling"
                        } else {
                            "https://www.google.com/maps/search/?api=1&query=${android.net.Uri.encode(event.route)}"
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Green50), Alignment.Center) {
                            Icon(Icons.Default.LocationOn, null, tint = Green900, modifier = Modifier.size(18.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("Route", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                            Text(event.route, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Google Maps button
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFEEF2FF))
                                .clickable {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(mapsUrl))
                                    routeContext.startActivity(intent)
                                }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Map, null, tint = Color(0xFF4285F4),
                                        modifier = Modifier.size(13.dp))
                                    Text("View", fontSize = 11.sp, color = Color(0xFF4285F4),
                                        fontWeight = FontWeight.SemiBold)
                                }
                            }
                            // Navigate with PedalConnect button
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Green50)
                                .clickable {
                                    // Extract destination from route string
                                    val parts = event.route.split(" to ", " - ", "–")
                                        .map { it.trim() }
                                    val destination = if (parts.size >= 2) parts.last() else event.route
                                    onDismiss()
                                    onNavigate(destination)
                                }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Navigation, null, tint = Green900,
                                        modifier = Modifier.size(13.dp))
                                    Text("Navigate", fontSize = 11.sp, color = Green900,
                                        fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                if (event.distanceKm > 0)
                    DetailTile(Icons.Default.Route, "Distance", String.format("%.1f km", event.distanceKm))
                DetailTile(Icons.Default.Person, "Organizer", event.organizer)
                DetailTile(Icons.Default.Groups, "Riders", buildString {
                    append("${event.participants.size}")
                    if (event.maxParticipants > 0) append(" / ${event.maxParticipants}")
                    append(" joined")
                })
            }

            // OSM Route map preview
            if (event.route.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text("Route preview", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = TextMuted, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(8.dp))
                EventRouteMap(routeText = event.route)
            }

            // Route safety alerts
            if (routeAlerts.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = DividerColor)
                Spacer(Modifier.height(16.dp))
                Text("Route Alerts", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = TextMuted, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    routeAlerts.forEach { alert ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Red50).padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, null, tint = Red600,
                                modifier = Modifier.size(15.dp).padding(top = 1.dp))
                            Text(alert, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
                        }
                    }
                }
            }

            // Description
            if (event.description.isNotBlank()) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = DividerColor)
                Spacer(Modifier.height(16.dp))
                Text("About this ride", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = TextMuted, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(8.dp))
                Text(event.description, fontSize = 14.sp, color = TextSecondary, lineHeight = 22.sp)
            }

            // Participants list
            if (event.participants.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = DividerColor)
                Spacer(Modifier.height(16.dp))
                Text("Riders (${event.participants.size})", fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold, color = TextMuted, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(12.dp))

                var showAll by remember { mutableStateOf(false) }
                val visible = if (showAll || event.participants.size <= 4) event.participants
                else event.participants.take(4)

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    visible.forEach { p ->
                        val attended = event.attendees.contains(p)
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(
                                if (attended) Brush.linearGradient(listOf(Color(0xFF166534), Color(0xFF15803D)))
                                else Brush.linearGradient(listOf(Green900, Green700))), Alignment.Center) {
                                Text(p.take(1).uppercase(), fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(p, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                                if (attended) Text("Checked in ✓", fontSize = 12.sp,
                                    color = Green700, fontWeight = FontWeight.Medium)
                            }
                            when {
                                p == event.organizer -> StatusChip("Organizer", Green900, Green100)
                                isOrganizer -> IconButton(onClick = { onToggleAttendance(p) },
                                    modifier = Modifier.size(36.dp)) {
                                    Icon(if (attended) Icons.Default.CheckCircle
                                    else Icons.Default.RadioButtonUnchecked, null,
                                        tint = if (attended) Green700 else TextMuted,
                                        modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                    if (event.participants.size > 4) {
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Green50)
                            .clickable { showAll = !showAll }.padding(vertical = 10.dp), Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(if (showAll) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    null, tint = Green700, modifier = Modifier.size(16.dp))
                                Text(if (showAll) "Show less" else "Show ${event.participants.size - 4} more",
                                    fontSize = 13.sp, color = Green700, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = DividerColor)
            Spacer(Modifier.height(16.dp))

            // Actions
            val eventIsPast = eventTiming(event.date) == EventTiming.PAST
            when {
                eventIsPast -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF3F4F6)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.History, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                    Text("This ride has already taken place", fontSize = 14.sp, color = TextMuted)
                }

                !isOrganizer -> {
                    val hasCheckedIn = event.attendees.contains(userName)
                    val showCheckIn  = isJoined && (isCheckInWindow(event) || event.checkInOpen)
                    val isEnded      = getEventTimeStatus(event) == EventStatus.ENDED

                    if (isEnded) {
                        // Event ended — show read-only state, no join/leave
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF3F4F6)).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.History, null, tint = TextMuted,
                                modifier = Modifier.size(18.dp))
                            Column {
                                Text("This ride has ended", fontSize = 14.sp,
                                    color = TextMuted, fontWeight = FontWeight.Medium)
                                if (hasCheckedIn) {
                                    Text("You checked in ✓", fontSize = 12.sp, color = Green700,
                                        fontWeight = FontWeight.Medium)
                                } else if (isJoined) {
                                    Text("You were registered for this ride",
                                        fontSize = 12.sp, color = TextMuted)
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (showCheckIn) {
                                Button(onClick = onCheckIn, enabled = !hasCheckedIn,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (hasCheckedIn) Green700 else Green900,
                                        contentColor = Color.White,
                                        disabledContainerColor = Green700,
                                        disabledContentColor = Color.White)) {
                                    Icon(if (hasCheckedIn) Icons.Default.CheckCircle else Icons.Default.HowToReg,
                                        null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (hasCheckedIn) "Checked in!" else "I'm here — check in",
                                        fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                }
                            }
                            Button(onClick = { onJoin(); onDismiss() }, enabled = !isFull,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isJoined) Color(0xFFF3F4F6) else Green900,
                                    contentColor = if (isJoined) TextSecondary else Color.White,
                                    disabledContainerColor = Color(0xFFE5E7EB),
                                    disabledContentColor = TextMuted)) {
                                Icon(if (isJoined) Icons.Default.ExitToApp else Icons.Default.DirectionsBike,
                                    null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(when { isFull -> "This ride is full"; isJoined -> "Leave this ride"; else -> "Join this ride" },
                                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            }
                        }
                    }
                }

                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Green50).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Groups, null, tint = Green900, modifier = Modifier.size(20.dp))
                            Text("${event.participants.size} rider${if (event.participants.size != 1) "s" else ""} joined your ride",
                                fontSize = 14.sp, color = Green900, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (eventTiming(event.date) == EventTiming.TODAY) {
                        OutlinedButton(onClick = onToggleCheckInOpen,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp,
                                if (event.checkInOpen) Green700 else BorderDefault),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (event.checkInOpen) Green700 else TextSecondary)) {
                            Icon(if (event.checkInOpen) Icons.Default.LockOpen else Icons.Default.Lock,
                                null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (event.checkInOpen) "Close check-in" else "Open check-in for riders",
                                fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                    }
                    Button(onClick = { onEdit(); onDismiss() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Green900, contentColor = Color.White)) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Edit this ride", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    OutlinedButton(onClick = { onDelete(); onDismiss() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Red600.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Red600)) {
                        Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete this ride", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailTile(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Green50), Alignment.Center) {
            Icon(icon, null, tint = Green900, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InlineAlert(icon: ImageVector, iconColor: Color, bgColor: Color, borderColor: Color, text: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bgColor)
        .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(16.dp).padding(top = 1.dp))
        Text(text, fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Create Event Sheet
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateEventSheet(
    userName: String,
    initialTitle: String           = "",
    initialDesc: String            = "",
    initialOrigin: String          = "",
    initialDest: String            = "",
    initialTime: String            = "",
    initialDistance: String        = "",
    initialMaxPax: String          = "",
    initialDiff: String            = "Easy",
    initialDate: Long              = 0L,
    initialOriginConfirmed: Boolean = false,
    initialDestConfirmed: Boolean   = false,
    initialDuration: Int            = 0,
    onDraftChange: (String, String, String, String, String, String, String, String, Long, Boolean, Boolean, Int) -> Unit = { _,_,_,_,_,_,_,_,_,_,_,_ -> },
    onDismiss: () -> Unit,
    onCreate: (RideEvent) -> Unit
) {
    val db         = FirebaseFirestore.getInstance()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title        by remember { mutableStateOf(initialTitle) }
    var description  by remember { mutableStateOf(initialDesc) }
    var route        by remember { mutableStateOf(if (initialDest.isNotBlank()) "$initialOrigin to $initialDest" else initialOrigin) }
    var time         by remember { mutableStateOf(initialTime) }
    var distanceText by remember { mutableStateOf(initialDistance) }
    var maxPaxText   by remember { mutableStateOf(initialMaxPax) }
    var difficulty          by remember { mutableStateOf(initialDiff) }
    var selectedDate        by remember { mutableStateOf(initialDate) }
    val samePlace = run {
        val firstOrigin = initialOrigin.trim().lowercase().split(",").firstOrNull()?.trim() ?: ""
        val firstDest   = initialDest.trim().lowercase().split(",").firstOrNull()?.trim() ?: ""
        firstOrigin.isNotBlank() && firstDest.isNotBlank() && firstOrigin == firstDest
    }
    var originConfirmed by remember { mutableStateOf(if (samePlace) false else initialOriginConfirmed) }
    var destConfirmed   by remember { mutableStateOf(if (samePlace) false else initialDestConfirmed) }
    var routeError      by remember { mutableStateOf(if (samePlace) "Destination cannot be the same as the starting point" else "") }
    var duration            by remember { mutableStateOf(initialDuration) }
    var originIslandGroup   by remember { mutableStateOf(IslandGroup.UNKNOWN) }
    var crossIslandError    by remember { mutableStateOf("") }
    var routeOrigin      by remember { mutableStateOf(initialOrigin.ifBlank {
        if (route.contains(" to ", ignoreCase = true)) route.substringBefore(" to ").trim() else route
    }) }
    var routeDestination by remember { mutableStateOf(
        if (samePlace) ""
        else initialDest.ifBlank {
            if (route.contains(" to ", ignoreCase = true)) route.substringAfter(" to ").trim() else ""
        }
    ) }

    var titleError     by remember { mutableStateOf("") }
    var dateError      by remember { mutableStateOf("") }
    var distanceError  by remember { mutableStateOf("") }
    var maxPaxError    by remember { mutableStateOf("") }
    var difficultyWarn by remember { mutableStateOf("") }
    var routeDistanceKm  by remember { mutableStateOf<Double?>(null) }
    var isLoadingRouteDist by remember { mutableStateOf(false) }

    data class ActiveAlert(val location: String = "", val message: String = "", val severity: String = "")

    var activeAlerts     by remember { mutableStateOf<List<ActiveAlert>>(emptyList()) }
    var showDangerDialog by remember { mutableStateOf(false) }
    var pendingEvent     by remember { mutableStateOf<RideEvent?>(null) }



    // Persist draft state back to parent on every change
    LaunchedEffect(title, description, route, time, distanceText, maxPaxText, difficulty, selectedDate, originConfirmed, destConfirmed, duration) {
        val parts = route.split(" to ", ignoreCase = true).map { it.trim() }
        onDraftChange(
            title, description,
            parts.getOrElse(0) { route },
            parts.getOrElse(1) { "" },
            time, distanceText, maxPaxText, difficulty, selectedDate,
            originConfirmed, destConfirmed, duration
        )
    }
    LaunchedEffect(Unit) {
        db.collection("alerts").whereEqualTo("status", "active").get()
            .addOnSuccessListener { snap ->
                activeAlerts = snap.documents.mapNotNull { doc ->
                    val loc = doc.getString("location") ?: return@mapNotNull null
                    ActiveAlert(loc,
                        doc.getString("message") ?: doc.getString("description") ?: "",
                        doc.getString("severity") ?: doc.getString("type") ?: "Warning")
                }
            }
    }

    LaunchedEffect(difficulty, distanceText) {
        val km = distanceText.toDoubleOrNull()
        difficultyWarn = when {
            km != null && difficulty == "Easy" && km > 60 -> "Easy + ${km.toInt()} km — consider Moderate or Hard"
            km != null && difficulty == "Hard" && km < 5  -> "Hard difficulty for ${km.toInt()} km seems unusual"
            else -> ""
        }
    }

    fun routeHasDanger(r: String): Boolean {
        val lower = r.lowercase()
        return activeAlerts.any { alert ->
            alert.location.split(" ", ",", "-").any { w -> w.length > 3 && lower.contains(w.lowercase()) }
        }
    }
    fun matchingAlerts(r: String): List<ActiveAlert> {
        val lower = r.lowercase()
        return activeAlerts.filter { alert ->
            alert.location.split(" ", ",", "-").any { w -> w.length > 3 && lower.contains(w.lowercase()) }
        }
    }

    fun attemptCreate() {
        var ok = true
        titleError = ""; routeError = ""; dateError = ""; distanceError = ""; maxPaxError = ""
        when {
            title.isBlank()              -> { titleError = "Title is required"; ok = false }
            title.trim().length < 5      -> { titleError = "At least 5 characters"; ok = false }
            title.trim().length > 80     -> { titleError = "Under 80 characters"; ok = false }
            !title.any { it.isLetter() } -> { titleError = "Must contain actual words"; ok = false }
        }
        // Rebuild route synchronously from the two fields at submission time
        // LaunchedEffect may not have updated `route` yet when button is tapped
        val submittedRoute = if (routeDestination.isNotBlank())
            "${routeOrigin.trim()} to ${routeDestination.trim()}"
        else routeOrigin.trim()

        when {
            routeOrigin.isBlank()        -> { routeError = "Starting point is required"; ok = false }
            routeDestination.isBlank()   -> { routeError = "Destination is required"; ok = false }
            !originConfirmed             -> { routeError = "Please select a starting point from the suggestions"; ok = false }
            !destConfirmed               -> { routeError = "Please select a destination from the suggestions"; ok = false }
            submittedRoute.length > 100  -> { routeError = "Route is too long"; ok = false }
        }
        val now = System.currentTimeMillis()
        when {
            selectedDate == 0L                   -> { dateError = "Please pick a date"; ok = false }
            selectedDate < now - 86_400_000L     -> { dateError = "Date cannot be in the past"; ok = false }
            selectedDate > now + 365L*86400*1000 -> { dateError = "Cannot be more than 1 year ahead"; ok = false }
        }
        val distKm = distanceText.toDoubleOrNull()
        when {
            distanceText.isNotBlank() && distKm == null -> { distanceError = "Enter a valid number"; ok = false }
            distKm != null && distKm < 1.0              -> { distanceError = "Min 1 km"; ok = false }
            distKm != null && distKm > 200.0            -> { distanceError = "Max 200 km"; ok = false }
        }
        val maxPax = maxPaxText.toIntOrNull()
        when {
            maxPaxText.isNotBlank() && maxPax == null -> { maxPaxError = "Enter a valid number"; ok = false }
            maxPax != null && maxPax == 1             -> { maxPaxError = "Needs at least 2 riders"; ok = false }
            maxPax != null && maxPax > 500            -> { maxPaxError = "Max 500 riders"; ok = false }
        }
        if (!ok) return
        val event = RideEvent(title = title.trim(), description = description.trim(), route = submittedRoute.trim(),
            date = selectedDate, time = time.trim(), organizer = userName,
            difficulty = difficulty, distanceKm = distKm ?: 0.0, maxParticipants = maxPax ?: 0,
            durationHours = duration)
        if (routeHasDanger(submittedRoute)) { pendingEvent = event; showDangerDialog = true }
        else onCreate(event)
    }

    if (showDangerDialog && pendingEvent != null) {
        AlertDialog(onDismissRequest = { showDangerDialog = false; pendingEvent = null },
            shape = RoundedCornerShape(24.dp), containerColor = BgSurface,
            icon = { Box(Modifier.size(56.dp).clip(CircleShape).background(Orange50), Alignment.Center) {
                Icon(Icons.Default.Warning, null, tint = Orange600, modifier = Modifier.size(28.dp)) }
            },
            title = { Text("Active alert on route", fontWeight = FontWeight.Bold,
                fontSize = 18.sp, textAlign = TextAlign.Center, color = TextPrimary) },
            text = { Text("There's a safety alert near your chosen route. Riders will be notified when they join. Proceed?",
                fontSize = 14.sp, color = TextSecondary, lineHeight = 22.sp, textAlign = TextAlign.Center) },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showDangerDialog = false; pendingEvent?.let { onCreate(it) }; pendingEvent = null },
                        modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Orange600, contentColor = Color.White)
                    ) { Text("Proceed anyway", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
                    OutlinedButton(onClick = { showDangerDialog = false; pendingEvent = null },
                        modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDefault),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) { Text("Change route", fontWeight = FontWeight.Medium, fontSize = 15.sp) }
                }
            }
        )
    }

    var showDatePicker  by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = {
                selectedDate = datePickerState.selectedDateMillis ?: 0L
                showDatePicker = false; dateError = ""
            }) { Text("Confirm", color = Green900, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) {
                Text("Cancel", color = TextSecondary) } }
        ) { DatePicker(state = datePickerState) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = BgSurface, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 4.dp).width(36.dp).height(4.dp)
            .clip(RoundedCornerShape(2.dp)).background(Color(0xFFD1D5DB))) }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 48.dp)
            .statusBarsPadding().navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(0.dp)) {

            Row(Modifier.padding(bottom = if (initialTitle.isNotBlank() || initialOrigin.isNotBlank() || initialDate > 0L) 12.dp else 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Green50), Alignment.Center) {
                    Icon(Icons.Default.DirectionsBike, null, tint = Green900, modifier = Modifier.size(22.dp)) }
                Column {
                    Text("Plan a ride", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary)
                    Text("Organise a group ride for the community", fontSize = 13.sp, color = TextSecondary)
                }
            }

            if (initialTitle.isNotBlank() || initialOrigin.isNotBlank() || initialDate > 0L) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 20.dp)
                        .clip(RoundedCornerShape(10.dp)).background(Green50)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Restore, null, tint = Green700, modifier = Modifier.size(16.dp))
                    Text("Draft restored — your progress was saved",
                        fontSize = 13.sp, color = Green700, modifier = Modifier.weight(1f))
                    Text("Clear", fontSize = 12.sp, color = Red600, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            title = ""; description = ""; route = ""; time = ""
                            distanceText = ""; maxPaxText = ""; difficulty = "Easy"; selectedDate = 0L
                        })
                }
            }

            FormSectionLabel("Ride info")
            Spacer(Modifier.height(12.dp))
            RideTextField(title, { title = it; titleError = "" }, "Ride title *",
                "e.g. Sunday Morning Gravel Ride", isError = titleError.isNotEmpty(), errorText = titleError)
            Spacer(Modifier.height(14.dp))// Split origin/destination for better map accuracy


            // Keep combined route in sync
            LaunchedEffect(routeOrigin, routeDestination) {
                route = if (routeDestination.isNotBlank())
                    "${routeOrigin.trim()} to ${routeDestination.trim()}"
                else routeOrigin.trim()
                val firstOrigin = routeOrigin.trim().lowercase().split(",").firstOrNull()?.trim() ?: ""
                val firstDest   = routeDestination.trim().lowercase().split(",").firstOrNull()?.trim() ?: ""
                if (firstOrigin.isNotBlank() && firstDest.isNotBlank() && firstOrigin == firstDest) {
                    routeError      = "Invalid! The input cannot be the same location"
                    originConfirmed = false
                    destConfirmed   = false
                } else {
                    routeError = ""
                }
            }

            RouteAutocompleteField(
                value             = routeOrigin,
                onValueChange     = { routeOrigin = it },
                onPlaceSelected   = { selected -> routeOrigin = selected },
                label             = "Starting point *",
                placeholder       = "e.g. Mall of Asia, Pasay",
                isError           = routeError.isNotEmpty() && routeOrigin.isBlank(),
                errorText         = if (routeOrigin.isBlank()) routeError else "",
                confirmed         = originConfirmed,
                onConfirmedChange = { confirmed ->
                    if (confirmed) {
                        val firstOrigin = routeOrigin.trim().lowercase().split(",").firstOrNull()?.trim() ?: ""
                        val firstDest   = routeDestination.trim().lowercase().split(",").firstOrNull()?.trim() ?: ""
                        if (firstOrigin.isNotBlank() && destConfirmed && firstOrigin == firstDest) {
                            routeError      = "Starting point cannot be the same as the destination"
                            originConfirmed = false
                        } else {
                            originConfirmed = true
                            routeError      = ""
                        }
                    } else {
                        originConfirmed = false
                    }
                }
            )
            Spacer(Modifier.height(12.dp))
            RouteAutocompleteField(
                value             = routeDestination,
                onValueChange     = { routeDestination = it },
                onPlaceSelected   = { selected -> routeDestination = selected },
                label             = "Destination *",
                placeholder       = "e.g. Tagaytay City, Cavite",
                isError           = routeError.isNotEmpty(),
                errorText         = routeError,
                confirmed         = destConfirmed,
                onConfirmedChange = { confirmed ->
                    if (confirmed) {
                        val firstOrigin = routeOrigin.trim().lowercase().split(",").firstOrNull()?.trim() ?: ""
                        val firstDest   = routeDestination.trim().lowercase().split(",").firstOrNull()?.trim() ?: ""
                        if (firstDest.isNotBlank() && firstDest == firstOrigin) {
                            routeError    = "Destination cannot be the same as the starting point"
                            destConfirmed = false
                        } else {
                            destConfirmed = true
                            routeError    = ""
                        }
                    } else {
                        destConfirmed = false
                    }
                }
            )
            // Auto-fetch distance from OSRM — with cross-island detection
            LaunchedEffect(routeOrigin, routeDestination) {
                crossIslandError = ""
                if (routeOrigin.length < 5 || routeDestination.length < 5) {
                    routeDistanceKm = null; return@LaunchedEffect
                }
                isLoadingRouteDist = true
                try {
                    val startResult = geocodePlaceWithIsland(routeOrigin)
                    val endResult   = geocodePlaceWithIsland(routeDestination)
                    if (startResult != null && endResult != null) {
                        val (startPoint, startIsland) = startResult
                        val (endPoint,   endIsland)   = endResult
                        originIslandGroup = startIsland
                        // Block cross-island routes
                        if (startIsland != IslandGroup.UNKNOWN &&
                            endIsland   != IslandGroup.UNKNOWN &&
                            startIsland != endIsland) {
                            crossIslandError   = "These locations are on different islands — a cycling route isn't possible between them. Please choose two places on the same island."
                            routeDistanceKm    = null
                            isLoadingRouteDist = false
                            return@LaunchedEffect
                        }
                        val result = fetchCyclingRoute(startPoint, endPoint)
                        if (result.error == null && result.distanceKm > 0) {
                            // Also block unreasonably long routes even on the same island
                            if (result.distanceKm > 400.0) {
                                crossIslandError   = "This route is ${result.distanceKm.toInt()} km — that's too far for a group ride. Please choose closer locations."
                                routeDistanceKm    = null
                                isLoadingRouteDist = false
                                return@LaunchedEffect
                            }
                            routeDistanceKm = result.distanceKm
                            distanceText    = String.format("%.1f", result.distanceKm)
                            distanceError   = ""
                        }
                    }
                } catch (e: Exception) { /* ignore */ }
                isLoadingRouteDist = false
            }

            // Cross-island error banner
            if (crossIslandError.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Red50)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.WrongLocation, null, tint = Red600,
                        modifier = Modifier.size(16.dp).padding(top = 1.dp))
                    Text(crossIslandError, fontSize = 13.sp, color = Red600, lineHeight = 19.sp)
                }
            }

            // Route confirmation hint + map preview — grouped together
            if (crossIslandError.isBlank() && routeOrigin.isNotBlank() && routeDestination.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (isLoadingRouteDist) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp, color = Green700)
                        Text("Calculating route distance…",
                            fontSize = 12.sp, color = TextMuted)
                    } else {
                        Icon(Icons.Default.CheckCircle, null, tint = Green700,
                            modifier = Modifier.size(13.dp))
                        Text(
                            if (routeDistanceKm != null)
                                "Route: ${routeOrigin.trim()} → ${routeDestination.trim()} · ${String.format("%.1f", routeDistanceKm)} km"
                            else
                                "Route: ${routeOrigin.trim()} → ${routeDestination.trim()}",
                            fontSize = 12.sp, color = Green700, fontWeight = FontWeight.Medium
                        )
                    }
                }
                // Route map preview — appears right below the confirmation hint
                if (originConfirmed && destConfirmed) {
                    Spacer(Modifier.height(10.dp))
                    Text("Route preview", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = TextMuted, letterSpacing = 0.8.sp)
                    Spacer(Modifier.height(8.dp))
                    EventRouteMap(routeText = route)
                }
            }


            Spacer(Modifier.height(24.dp)); HorizontalDivider(color = DividerColor); Spacer(Modifier.height(20.dp))

            FormSectionLabel("When"); Spacer(Modifier.height(12.dp))
            FormFieldLabel("Date *", isError = dateError.isNotEmpty()); Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp,
                    when { dateError.isNotEmpty() -> Red600; selectedDate > 0 -> Green700; else -> BorderDefault }),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (selectedDate > 0) TextPrimary else TextMuted)) {
                Icon(Icons.Default.CalendarMonth, null,
                    tint = if (selectedDate > 0) Green900 else TextMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (selectedDate > 0) formatEventDate(selectedDate) else "Select a date",
                    fontSize = 14.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                if (selectedDate > 0) Icon(Icons.Default.CheckCircle, null, tint = Green700, modifier = Modifier.size(16.dp))
            }
            if (dateError.isNotEmpty()) ErrorText(dateError)
            Spacer(Modifier.height(14.dp))
            var showTimePicker by remember { mutableStateOf(false) }
            val timePickerState = rememberTimePickerState(
                initialHour   = 6,
                initialMinute = 0,
                is24Hour      = false
            )
            if (showTimePicker) {
                DatePickerDialog(
                    onDismissRequest = { showTimePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            val hour   = timePickerState.hour
                            val minute = timePickerState.minute
                            val amPm   = if (hour < 12) "AM" else "PM"
                            val hour12 = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
                            time = String.format("%d:%02d %s", hour12, minute, amPm)
                            showTimePicker = false
                        }) { Text("Confirm", color = Green900, fontWeight = FontWeight.SemiBold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text("Cancel", color = TextSecondary)
                        }
                    }
                ) { TimePicker(state = timePickerState) }
            }
            FormFieldLabel("Start time (optional)"); Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick  = { showTimePicker = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                border   = androidx.compose.foundation.BorderStroke(1.5.dp,
                    if (time.isNotBlank()) Green700 else BorderDefault),
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (time.isNotBlank()) TextPrimary else TextMuted)
            ) {
                Icon(Icons.Default.Schedule, null,
                    tint     = if (time.isNotBlank()) Green900 else TextMuted,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(time.ifBlank { "Select start time" },
                    fontSize = 14.sp, modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start)
                if (time.isNotBlank()) {
                    Icon(Icons.Default.CheckCircle, null, tint = Green700, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear", fontSize = 12.sp, color = TextMuted,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { time = "" })
                }
            }
            Spacer(Modifier.height(14.dp))
            FormFieldLabel("Estimated duration (optional)")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Auto", 2 to "2 hrs", 3 to "3 hrs", 4 to "4 hrs", 6 to "6 hrs", 8 to "8 hrs").forEach { (hrs, label) ->
                    val isSelected = duration == hrs
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Green900 else Color(0xFFF3F4F6))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { duration = hrs }
                            .padding(vertical = 10.dp),
                        Alignment.Center
                    ) {
                        Text(label, fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Color.White else TextMuted)
                    }
                }
            }
            Row(Modifier.padding(top = 4.dp, start = 2.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Info, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                Text("\"Auto\" estimates from distance. Set manually for accuracy.",
                    fontSize = 11.sp, color = TextMuted)
            }

            Spacer(Modifier.height(24.dp)); HorizontalDivider(color = DividerColor); Spacer(Modifier.height(20.dp))

            FormSectionLabel("Details"); Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    FormFieldLabel("Distance (km)", isError = distanceError.isNotEmpty())
                    Spacer(Modifier.height(6.dp))
                    CompactNumberField(
                        distanceText,
                        { distanceText = it.filter { c -> c.isDigit() || c == '.' }; distanceError = "" },
                        if (isLoadingRouteDist) "Calculating…" else "e.g. 40",
                        isError = distanceError.isNotEmpty()
                    )
                    if (distanceError.isNotEmpty()) ErrorText(distanceError)
                    if (routeDistanceKm != null && !isLoadingRouteDist) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(top = 3.dp)) {
                            Icon(Icons.Default.Route, null, tint = Green700,
                                modifier = Modifier.size(11.dp))
                            Text("From route", fontSize = 10.sp, color = Green700)
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    FormFieldLabel("Participants", isError = maxPaxError.isNotEmpty()); Spacer(Modifier.height(6.dp))
                    CompactNumberField(maxPaxText, { maxPaxText = it.filter { c -> c.isDigit() }; maxPaxError = "" }, "500 max", isError = maxPaxError.isNotEmpty())
                    if (maxPaxError.isNotEmpty()) ErrorText(maxPaxError)
                }
            }
            Spacer(Modifier.height(16.dp))
            FormFieldLabel("Difficulty"); Spacer(Modifier.height(8.dp))
            DifficultySelector(difficulty) { difficulty = it }
            if (difficultyWarn.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                InlineAlert(Icons.Default.Warning, Amber500, Amber50, Amber100, difficultyWarn)
            }

            Spacer(Modifier.height(24.dp)); HorizontalDivider(color = DividerColor); Spacer(Modifier.height(20.dp))

            FormSectionLabel("Description"); Spacer(Modifier.height(4.dp))
            Text("Optional — tell riders what to expect", fontSize = 13.sp, color = TextMuted)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = description, onValueChange = { if (it.length <= 300) description = it },
                placeholder = { Text("Pace, gear needed, what to expect…", color = Color(0xFFD1D5DB), fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth().height(108.dp), shape = RoundedCornerShape(12.dp), maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Green700,
                    unfocusedBorderColor = BorderDefault,
                    cursorColor          = Green700,
                    focusedTextColor     = TextPrimary,
                    unfocusedTextColor   = TextPrimary
                ))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                Text("${description.length}/300", fontSize = 11.sp,
                    color = if (description.length > 280) Red600 else TextMuted)
            }

            Spacer(Modifier.height(16.dp))

            // Active alerts banner
            if (activeAlerts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Warning, null, tint = Orange600, modifier = Modifier.size(14.dp))
                        Text("${activeAlerts.size} active alert${if (activeAlerts.size != 1) "s" else ""} in your area",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Orange600)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        activeAlerts.forEach { alert ->
                            val isMatch = route.isNotBlank() && matchingAlerts(route).contains(alert)
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(if (isMatch) Red50 else Orange50)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(if (isMatch) Icons.Default.LocationOn else Icons.Default.Info, null,
                                    tint = if (isMatch) Red600 else Orange600,
                                    modifier = Modifier.size(16.dp).padding(top = 1.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(alert.location, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                            color = if (isMatch) Red600 else Orange600)
                                        if (isMatch) {
                                            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Red600)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                Text("On your route", fontSize = 10.sp,
                                                    color = Color.White, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                    if (alert.message.isNotBlank())
                                        Text(alert.message, fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                InlineAlert(Icons.Default.Info, Orange600, Orange50, Color.Transparent,
                    "As organizer, you're responsible for route safety. Always check active alerts before scheduling.")
            }

            Spacer(Modifier.height(24.dp))
            Button(onClick = { attemptCreate() }, modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green900, contentColor = Color.White)) {
                Icon(Icons.Default.DirectionsBike, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Create ride event", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Edit Event Sheet
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditEventSheet(event: RideEvent, onDismiss: () -> Unit, onSave: (RideEvent) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title        by remember { mutableStateOf(event.title) }
    var description  by remember { mutableStateOf(event.description) }
    var route        by remember { mutableStateOf(event.route) }
    var time         by remember { mutableStateOf(event.time) }
    var distanceText by remember { mutableStateOf(if (event.distanceKm > 0) event.distanceKm.toInt().toString() else "") }
    var maxPaxText   by remember { mutableStateOf(if (event.maxParticipants > 0) event.maxParticipants.toString() else "") }
    var difficulty   by remember { mutableStateOf(event.difficulty) }

    var titleError     by remember { mutableStateOf("") }
    var routeError     by remember { mutableStateOf("") }
    var distanceError  by remember { mutableStateOf("") }
    var maxPaxError    by remember { mutableStateOf("") }
    var difficultyWarn by remember { mutableStateOf("") }

    LaunchedEffect(difficulty, distanceText) {
        val km = distanceText.toDoubleOrNull()
        difficultyWarn = when {
            km != null && difficulty == "Easy" && km > 60 -> "Easy + ${km.toInt()} km — consider Moderate or Hard"
            km != null && difficulty == "Hard" && km < 5  -> "Hard difficulty for ${km.toInt()} km seems unusual"
            else -> ""
        }
    }

    fun validate(): Boolean {
        var ok = true
        titleError = ""; routeError = ""; distanceError = ""; maxPaxError = ""
        when {
            title.isBlank()              -> { titleError = "Title is required"; ok = false }
            title.trim().length < 5      -> { titleError = "At least 5 characters"; ok = false }
            title.trim().length > 80     -> { titleError = "Under 80 characters"; ok = false }
            !title.any { it.isLetter() } -> { titleError = "Must contain actual words"; ok = false }
        }
        when {
            route.isBlank()              -> { routeError = "Route is required"; ok = false }
            route.trim().length < 5      -> { routeError = "At least 5 characters"; ok = false }
            route.trim().length > 100    -> { routeError = "Under 100 characters"; ok = false }
            !route.any { it.isLetter() } -> { routeError = "Must contain actual text"; ok = false }
            !route.contains(" to ", ignoreCase = true) -> { routeError = "Select both a starting point and destination from the suggestions"; ok = false }
            route.contains(" to ", ignoreCase = true) && route.substringBefore(" to ").trim().equals(route.substringAfter(" to ").trim(), ignoreCase = true) -> { routeError = "Destination cannot be the same as the starting point"; ok = false }
        }
        val distKm = distanceText.toDoubleOrNull()
        when {
            distanceText.isNotBlank() && distKm == null -> { distanceError = "Enter a valid number"; ok = false }
            distKm != null && distKm < 1.0             -> { distanceError = "Min 1 km"; ok = false }
            distKm != null && distKm > 200.0           -> { distanceError = "Max 200 km"; ok = false }
        }
        val maxPax = maxPaxText.toIntOrNull()
        when {
            maxPaxText.isNotBlank() && maxPax == null -> { maxPaxError = "Enter a valid number"; ok = false }
            maxPax != null && maxPax == 1             -> { maxPaxError = "Needs at least 2 riders"; ok = false }
            maxPax != null && maxPax > 500            -> { maxPaxError = "Max 500 riders"; ok = false }
        }
        return ok
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = BgSurface, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 4.dp).width(36.dp).height(4.dp)
            .clip(RoundedCornerShape(2.dp)).background(Color(0xFFD1D5DB))) }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 48.dp)
            .statusBarsPadding().navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(0.dp)) {

            Row(Modifier.padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Green50), Alignment.Center) {
                    Icon(Icons.Default.Edit, null, tint = Green900, modifier = Modifier.size(20.dp)) }
                Column {
                    Text("Edit ride", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary)
                    Text("Date cannot be changed after creation", fontSize = 13.sp, color = TextMuted)
                }
            }

            FormSectionLabel("Ride info"); Spacer(Modifier.height(12.dp))
            RideTextField(title, { title = it; titleError = "" }, "Ride title *",
                "e.g. Sunday Morning Gravel Ride", isError = titleError.isNotEmpty(), errorText = titleError)
            Spacer(Modifier.height(14.dp))
            var routeOrigin      by remember { mutableStateOf(
                if (route.contains(" to ", ignoreCase = true))
                    route.substringBefore(" to ").trim() else route
            )}
            var routeDestination by remember { mutableStateOf(
                if (route.contains(" to ", ignoreCase = true))
                    route.substringAfter(" to ").trim() else ""
            )}

            LaunchedEffect(routeOrigin, routeDestination) {
                route = if (routeDestination.isNotBlank())
                    "${routeOrigin.trim()} to ${routeDestination.trim()}"
                else routeOrigin.trim()
                routeError = ""
            }

            RouteAutocompleteField(
                value             = routeOrigin,
                onValueChange     = { routeOrigin = it },
                onPlaceSelected   = { routeOrigin = it },
                label             = "Starting point *",
                placeholder       = "e.g. Mall of Asia, Pasay",
                isError           = routeError.isNotEmpty() && routeOrigin.isBlank(),
                errorText         = if (routeOrigin.isBlank()) routeError else "",
                confirmed         = false,
                onConfirmedChange = {}
            )
            Spacer(Modifier.height(12.dp))
            RouteAutocompleteField(
                value             = routeDestination,
                onValueChange     = { routeDestination = it },
                onPlaceSelected   = { selected ->
                    if (selected.trim().equals(routeOrigin.trim(), ignoreCase = true)) {
                        routeError = "Destination cannot be the same as the starting point"
                    } else {
                        routeDestination = selected
                        routeError = ""
                    }
                },
                label             = "Destination *",
                placeholder       = "e.g. Tagaytay City, Cavite",
                isError           = routeError.isNotEmpty() && routeDestination.isBlank(),
                errorText         = if (routeDestination.isBlank()) routeError else "",
                confirmed         = false,
                onConfirmedChange = {}
            )
            if (routeOrigin.isNotBlank() && routeDestination.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = Green700,
                        modifier = Modifier.size(13.dp))
                    Text("Route: ${routeOrigin.trim()} → ${routeDestination.trim()}",
                        fontSize = 12.sp, color = Green700, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(24.dp)); HorizontalDivider(color = DividerColor); Spacer(Modifier.height(20.dp))

            FormSectionLabel("When"); Spacer(Modifier.height(12.dp))
            FormFieldLabel("Date (locked)"); Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF9FAFB))
                .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Lock, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                Text(formatEventDate(event.date), fontSize = 14.sp, color = TextMuted)
            }
            Spacer(Modifier.height(14.dp))
            var showTimePickerEdit by remember { mutableStateOf(false) }
            val timePickerStateEdit = rememberTimePickerState(
                initialHour   = 6,
                initialMinute = 0,
                is24Hour      = false
            )
            if (showTimePickerEdit) {
                DatePickerDialog(
                    onDismissRequest = { showTimePickerEdit = false },
                    confirmButton = {
                        TextButton(onClick = {
                            val hour   = timePickerStateEdit.hour
                            val minute = timePickerStateEdit.minute
                            val amPm   = if (hour < 12) "AM" else "PM"
                            val hour12 = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
                            time = String.format("%d:%02d %s", hour12, minute, amPm)
                            showTimePickerEdit = false
                        }) { Text("Confirm", color = Green900, fontWeight = FontWeight.SemiBold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTimePickerEdit = false }) {
                            Text("Cancel", color = TextSecondary)
                        }
                    }
                ) { TimePicker(state = timePickerStateEdit) }
            }
            FormFieldLabel("Start time (optional)"); Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick  = { showTimePickerEdit = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                border   = androidx.compose.foundation.BorderStroke(1.5.dp,
                    if (time.isNotBlank()) Green700 else BorderDefault),
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (time.isNotBlank()) TextPrimary else TextMuted)
            ) {
                Icon(Icons.Default.Schedule, null,
                    tint     = if (time.isNotBlank()) Green900 else TextMuted,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(time.ifBlank { "Select start time" },
                    fontSize = 14.sp, modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start)
                if (time.isNotBlank()) {
                    Icon(Icons.Default.CheckCircle, null, tint = Green700, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear", fontSize = 12.sp, color = TextMuted,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { time = "" })
                }
            }

            Spacer(Modifier.height(24.dp)); HorizontalDivider(color = DividerColor); Spacer(Modifier.height(20.dp))

            FormSectionLabel("Details"); Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    FormFieldLabel("Distance (km)", isError = distanceError.isNotEmpty()); Spacer(Modifier.height(6.dp))
                    CompactNumberField(distanceText, { distanceText = it.filter { c -> c.isDigit() || c == '.' }; distanceError = "" }, "e.g. 40", isError = distanceError.isNotEmpty())
                    if (distanceError.isNotEmpty()) ErrorText(distanceError)
                }
                Column(Modifier.weight(1f)) {
                    FormFieldLabel("Max riders", isError = maxPaxError.isNotEmpty()); Spacer(Modifier.height(6.dp))
                    CompactNumberField(maxPaxText, { maxPaxText = it.filter { c -> c.isDigit() }; maxPaxError = "" }, "∞ unlimited", isError = maxPaxError.isNotEmpty())
                    if (maxPaxError.isNotEmpty()) ErrorText(maxPaxError)
                }
            }
            Spacer(Modifier.height(16.dp))
            FormFieldLabel("Difficulty"); Spacer(Modifier.height(8.dp))
            DifficultySelector(difficulty) { difficulty = it }
            if (difficultyWarn.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                InlineAlert(Icons.Default.Warning, Amber500, Amber50, Amber100, difficultyWarn)
            }

            Spacer(Modifier.height(24.dp)); HorizontalDivider(color = DividerColor); Spacer(Modifier.height(20.dp))

            FormSectionLabel("Description"); Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = description, onValueChange = { if (it.length <= 300) description = it },
                placeholder = { Text("Pace, gear needed, what to expect…", color = Color(0xFFD1D5DB), fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth().height(108.dp), shape = RoundedCornerShape(12.dp), maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Green700,
                    unfocusedBorderColor = BorderDefault,
                    cursorColor          = Green700,
                    focusedTextColor     = TextPrimary,
                    unfocusedTextColor   = TextPrimary
                ))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                Text("${description.length}/300", fontSize = 11.sp,
                    color = if (description.length > 280) Red600 else TextMuted)
            }
            Spacer(Modifier.height(24.dp))

            Button(onClick = { if (validate()) onSave(event.copy(
                title = title.trim(), description = description.trim(), route = route.trim(),
                time = time.trim(), distanceKm = distanceText.toDoubleOrNull() ?: 0.0,
                maxParticipants = maxPaxText.toIntOrNull() ?: 0, difficulty = difficulty)) },
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green900, contentColor = Color.White)) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save changes", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
}
// ─────────────────────────────────────────────────────────────────────────────
// Island group helper — prevents cross-island impossible routes
// ─────────────────────────────────────────────────────────────────────────────
private enum class IslandGroup { LUZON, VISAYAS, MINDANAO, UNKNOWN }

private fun detectIslandGroup(lat: Double, lon: Double): IslandGroup {
    // Rough bounding boxes for the three major island groups
    return when {
        // Mindanao: roughly lat 5.5–10.0, lon 121.5–127.0
        lat in 5.5..10.0 && lon in 121.5..127.0 -> IslandGroup.MINDANAO
        // Visayas: roughly lat 9.5–12.5, lon 121.5–126.5
        lat in 9.5..12.5 && lon in 121.5..126.5 -> IslandGroup.VISAYAS
        // Luzon: roughly lat 12.0–20.5, lon 117.0..127.0
        lat in 12.0..20.5 && lon in 117.0..127.0 -> IslandGroup.LUZON
        else -> IslandGroup.UNKNOWN
    }
}

private suspend fun geocodePlaceWithIsland(placeName: String): Pair<GeoPoint, IslandGroup>? =
    withContext(Dispatchers.IO) {
        nominatimThrottle()
        try {
            val encoded  = URLEncoder.encode(placeName.trim(), "UTF-8")
            val url      = "https://nominatim.openstreetmap.org/search" +
                    "?q=$encoded&format=json&limit=1&countrycodes=ph"
            val conn = URL(url).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("User-Agent", "PedalConnect/1.0")
            conn.connectTimeout = 5000
            conn.readTimeout    = 5000
            if (conn.responseCode != 200) return@withContext null
            val response = conn.inputStream.bufferedReader().readText()
            val array = JSONArray(response)
            if (array.length() == 0) return@withContext null
            val obj = array.getJSONObject(0)
            val lat = obj.getDouble("lat")
            val lon = obj.getDouble("lon")
            Pair(GeoPoint(lat, lon), detectIslandGroup(lat, lon))
        } catch (e: Exception) { null }
    }

// ─────────────────────────────────────────────────────────────────────────────
// Route autocomplete field — Nominatim suggestions as user types
// ─────────────────────────────────────────────────────────────────────────────
private data class PlaceSuggestion(
    val displayName: String,
    val shortName: String
)

private sealed class SearchState {
    object Idle      : SearchState()
    object Searching : SearchState()
    object Confirmed : SearchState()
    object NoResults : SearchState()
    data class Results(val items: List<PlaceSuggestion>) : SearchState()
}
private val nominatimCache  = mutableMapOf<String, List<PlaceSuggestion>>()
private val geocodeCache    = mutableMapOf<String, GeoPoint?>()
private val nominatimMutex  = kotlinx.coroutines.sync.Mutex()
private var lastNominatimRequestMs = 0L
private const val NOMINATIM_MIN_INTERVAL_MS = 1200L

private suspend fun nominatimThrottle() {
    nominatimMutex.lock()
    try {
        val now  = System.currentTimeMillis()
        val wait = NOMINATIM_MIN_INTERVAL_MS - (now - lastNominatimRequestMs)
        if (wait > 0) delay(wait)
        lastNominatimRequestMs = System.currentTimeMillis()
    } finally {
        nominatimMutex.unlock()
    }
}

private suspend fun nominatimSearch(query: String): List<PlaceSuggestion> =
    withContext(Dispatchers.IO) {
        val key = query.trim().lowercase()
        if (key.length < 5) return@withContext emptyList()
        nominatimCache[key]?.let { return@withContext it }
        nominatimThrottle()
        try {
            val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search" +
                    "?q=$encoded&format=json&limit=8&countrycodes=ph"
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty(
                "User-Agent",
                "PedalConnect/1.0 (Android cycling safety app; pedalconnect@gmail.com)"
            )
            conn.setRequestProperty("Accept-Language", "en")
            conn.connectTimeout = 10_000
            conn.readTimeout    = 10_000
            conn.requestMethod  = "GET"
            if (conn.responseCode != 200) return@withContext emptyList()
            val body  = conn.inputStream.bufferedReader().readText()
            val array = org.json.JSONArray(body)
            val results = (0 until array.length()).mapNotNull { i ->
                val obj  = array.getJSONObject(i)
                val full = obj.optString("display_name", "").trim()
                if (full.isBlank()) return@mapNotNull null
                val parts = full.split(",").map { it.trim() }.filter { it.isNotBlank() }
                PlaceSuggestion(
                    displayName = parts.take(4).joinToString(", "),
                    shortName   = parts.take(3).joinToString(", ")
                )
            }.distinctBy { it.shortName }
            if (results.isNotEmpty()) nominatimCache[key] = results
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

@Composable
private fun RouteAutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    onPlaceSelected: (String) -> Unit,
    label: String,
    placeholder: String,
    isError: Boolean                  = false,
    errorText: String                 = "",
    confirmed: Boolean                = false,
    onConfirmedChange: (Boolean) -> Unit = {},
    modifier: Modifier                = Modifier
) {
    val scope = rememberCoroutineScope()
    var searchState    by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    var searchJob      by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var suppressSearch by remember { mutableStateOf(false) }

    // Track whether this is the very first composition
    var isFirstComposition by remember { mutableStateOf(true) }

    LaunchedEffect(value) {
        // On first composition, if already confirmed just restore state — don't trigger search
        if (isFirstComposition) {
            isFirstComposition = false
            if (confirmed) {
                searchState = SearchState.Confirmed
                return@LaunchedEffect
            }
        }
        if (suppressSearch) { suppressSearch = false; return@LaunchedEffect }
        searchJob?.cancel()
        val trimmed = value.trim()
        if (trimmed.length < 5) { searchState = SearchState.Idle; return@LaunchedEffect }
        val cached = nominatimCache[trimmed.lowercase()]
        if (cached != null) {
            searchState = if (cached.isEmpty()) SearchState.NoResults
            else SearchState.Results(cached)
            return@LaunchedEffect
        }
        searchState = SearchState.Idle
        searchJob = scope.launch {
            delay(800)
            searchState = SearchState.Searching
            val results = nominatimSearch(trimmed)
            searchState = if (results.isEmpty()) SearchState.NoResults
            else SearchState.Results(results)
        }
    }

    val showError    = isError || (searchState is SearchState.NoResults && !confirmed)
    val displayError = when {
        isError && errorText.isNotEmpty()    -> errorText
        searchState is SearchState.NoResults -> "No places found — try a more specific name"
        else                                 -> errorText
    }
    val showDropdown = searchState is SearchState.Results && !confirmed

    Column(modifier = modifier) {
        FormFieldLabel(label, isError = showError)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value         = value,
            onValueChange = { raw ->
                val clean = raw
                    .filter { c -> c.isLetter() || c == ' ' || c == ',' || c == '-' || c == '.' || c == '(' || c == ')' }
                    .replace(Regex(" {2,}"), " ")
                if (clean.length <= 80) onValueChange(clean)
            },
            placeholder  = { Text(placeholder, color = Color(0xFFD1D5DB), fontSize = 13.sp) },
            singleLine   = true,
            isError      = showError,
            shape        = RoundedCornerShape(12.dp),
            modifier     = Modifier.fillMaxWidth(),
            leadingIcon  = {
                Icon(
                    if (confirmed) Icons.Default.CheckCircle else Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = when {
                        confirmed                            -> Green700
                        searchState is SearchState.NoResults -> Red600
                        else                                 -> Green900
                    },
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                when {
                    searchState is SearchState.Searching ->
                        CircularProgressIndicator(
                            modifier    = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color       = Green700
                        )
                    value.isNotBlank() ->
                        IconButton(onClick = {
                            searchJob?.cancel()
                            searchJob = null
                            suppressSearch = true
                            searchState = SearchState.Idle
                            onValueChange("")
                            onConfirmedChange(false)
                        }) {
                            Icon(Icons.Default.Close, "Clear",
                                tint     = if (confirmed) Green700 else TextMuted,
                                modifier = Modifier.size(16.dp))
                        }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = if (confirmed) Green700 else Green700,
                unfocusedBorderColor = if (confirmed) Green700 else BorderDefault,
                errorBorderColor     = Red600,
                cursorColor          = Green700,
                focusedTextColor     = TextPrimary,
                unfocusedTextColor   = TextPrimary,
                errorTextColor       = TextPrimary
            )
        )

        if (showDropdown) {
            val items = (searchState as SearchState.Results).items
            Spacer(Modifier.height(4.dp))
            Surface(
                modifier        = Modifier.fillMaxWidth(),
                shape           = RoundedCornerShape(12.dp),
                color           = BgSurface,
                shadowElevation = 4.dp
            ) {
                Column {
                    items.forEachIndexed { index, suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    suppressSearch = true
                                    searchJob?.cancel()
                                    searchState = SearchState.Confirmed
                                    onConfirmedChange(true)
                                    onValueChange(suggestion.displayName)
                                    onPlaceSelected(suggestion.shortName)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Place, null,
                                tint = Green700, modifier = Modifier.size(16.dp))
                            Column {
                                val namePart = suggestion.displayName.split(",").first().trim()
                                val restPart = suggestion.displayName
                                    .removePrefix(suggestion.displayName.split(",").first())
                                    .trimStart(',', ' ')
                                Text(namePart, fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                if (restPart.isNotBlank()) {
                                    Text(restPart, fontSize = 11.sp, color = TextMuted,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (index < items.lastIndex) {
                            HorizontalDivider(
                                modifier  = Modifier.padding(horizontal = 14.dp),
                                color     = DividerColor, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        when {
            showError && displayError.isNotEmpty() ->
                ErrorText(displayError)
            value.isNotBlank() && value.trim().length < 5 ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp, start = 2.dp)) {
                    Icon(Icons.Default.Info, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                    Text("Keep typing to search…", fontSize = 12.sp, color = TextMuted)
                }
            confirmed ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp, start = 2.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = Green700, modifier = Modifier.size(12.dp))
                    Text("Location confirmed", fontSize = 12.sp, color = Green700)
                }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared form atoms
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun FormSectionLabel(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold,
        color = TextMuted, letterSpacing = 1.sp)
}

@Composable
private fun FormFieldLabel(text: String, isError: Boolean = false) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        color = if (isError) Red600 else TextSecondary)
}

@Composable
private fun ErrorText(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 4.dp, start = 2.dp)) {
        Icon(Icons.Default.Error, null, tint = Red600, modifier = Modifier.size(12.dp))
        Text(text, fontSize = 12.sp, color = Red600)
    }
}

@Composable
private fun RideTextField(
    value: String, onValueChange: (String) -> Unit,
    label: String, placeholder: String, modifier: Modifier = Modifier,
    isError: Boolean = false, errorText: String = "",
    leadingIcon: @Composable (() -> Unit)? = null
) {
    Column(modifier = modifier) {
        FormFieldLabel(label, isError = isError); Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = value, onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = Color(0xFFD1D5DB), fontSize = 13.sp) },
            singleLine = true, isError = isError, shape = RoundedCornerShape(12.dp),
            leadingIcon = leadingIcon, modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Green700,
                unfocusedBorderColor = BorderDefault,
                errorBorderColor     = Red600,
                cursorColor          = Green700,
                focusedTextColor     = TextPrimary,
                unfocusedTextColor   = TextPrimary,
                errorTextColor       = TextPrimary
            ))
        if (isError && errorText.isNotEmpty()) ErrorText(errorText)
    }
}
@Composable
private fun CompactNumberField(value: String, onValueChange: (String) -> Unit, placeholder: String, isError: Boolean) {
    OutlinedTextField(value = value, onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Color(0xFFD1D5DB), fontSize = 13.sp) },
        singleLine = true, isError = isError, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = Green700,
            unfocusedBorderColor = BorderDefault,
            errorBorderColor     = Red600,
            cursorColor          = Green700,
            focusedTextColor     = TextPrimary,
            unfocusedTextColor   = TextPrimary,
            errorTextColor       = TextPrimary
        ))
}

@Composable
private fun DifficultySelector(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Easy", "Moderate", "Hard").forEach { level ->
            val isSelected = selected == level
            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                .background(if (isSelected) diffFg(level) else diffBg(level))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(level) }
                .padding(vertical = 12.dp), Alignment.Center) {
                Text(level, fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) Color.White else diffFg(level))
            }
        }
    }
}