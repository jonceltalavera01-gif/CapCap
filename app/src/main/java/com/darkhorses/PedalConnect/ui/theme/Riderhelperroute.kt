package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.darkhorses.PedalConnect.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

/**
 * Draws (and keeps refreshed) a live route line from the rider to their
 * responding helper, distinct from the helper's own turn-by-turn polyline.
 *
 * Recalculated whenever the helper's live location moves more than
 * [minRecalcDistanceKm], or every [minRecalcIntervalMs], whichever comes
 * first — mirrors the throttle shape already used for Firestore location
 * writes elsewhere in the app.
 *
 * Call this from HomeScreen only while the CURRENT user is the rider on an
 * alert with status == "responding" (i.e. NOT from the helper's own screen —
 * the helper already sees their own turn-by-turn route via fetchRoutes/
 * onNavigateToHelp).
 *
 * Purely a side-effect composable: it owns and cleans up its own Polyline
 * overlay on [mapView], and does not return or mutate any HomeScreen state.
 */
@Composable
fun RiderHelperLiveRoute(
    enabled: Boolean,
    riderLocation: GeoPoint?,
    helperLocation: GeoPoint?,
    mapView: MapView?,
    minRecalcIntervalMs: Long = 20_000L,
    minRecalcDistanceKm: Double = 0.05
) {
    val scope = rememberCoroutineScope()
    var polyline by remember { mutableStateOf<Polyline?>(null) }
    var lastFetchMs by remember { mutableStateOf(0L) }
    var lastHelperFetchPoint by remember { mutableStateOf<GeoPoint?>(null) }

    // Tear down the line the moment this is no longer relevant (helper cancels,
    // alert resolves, or the rider navigates away) rather than leaving a stale
    // route line on the map.
    DisposableEffect(enabled) {
        if (!enabled) {
            polyline?.let { mapView?.overlays?.remove(it) }
            polyline = null
            mapView?.invalidate()
        }
        onDispose {
            polyline?.let { mapView?.overlays?.remove(it) }
        }
    }

    LaunchedEffect(enabled, riderLocation, helperLocation) {
        if (!enabled) return@LaunchedEffect
        val rider = riderLocation ?: return@LaunchedEffect
        val helper = helperLocation ?: return@LaunchedEffect
        val map = mapView ?: return@LaunchedEffect

        val now = System.currentTimeMillis()
        val movedEnough = lastHelperFetchPoint?.let { last ->
            haversineKm(last.latitude, last.longitude, helper.latitude, helper.longitude) >= minRecalcDistanceKm
        } ?: true
        val dueForRefresh = (now - lastFetchMs) >= minRecalcIntervalMs

        if (!movedEnough && !dueForRefresh && polyline != null) return@LaunchedEffect

        lastFetchMs = now
        lastHelperFetchPoint = helper

        scope.launch(Dispatchers.IO) {
            try {
                val url = "https://api.openrouteservice.org/v2/directions/cycling-road" +
                        "?api_key=${BuildConfig.ORS_API_KEY}" +
                        "&start=${rider.longitude},${rider.latitude}" +
                        "&end=${helper.longitude},${helper.latitude}"
                val json = JSONObject(java.net.URL(url).readText())
                val features = json.getJSONArray("features")
                if (features.length() == 0) return@launch
                val coords = features.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
                val points = (0 until coords.length()).map {
                    val c = coords.getJSONArray(it)
                    GeoPoint(c.getDouble(1), c.getDouble(0))
                }

                withContext(Dispatchers.Main) {
                    // Re-check enabled — the alert may have resolved while the
                    // network call was in flight.
                    if (!enabled) return@withContext
                    val existing = polyline
                    if (existing != null) {
                        existing.setPoints(points)
                    } else {
                        val newLine = Polyline().apply {
                            setPoints(points)
                            outlinePaint.color       = android.graphics.Color.argb(190, 0, 150, 220)
                            outlinePaint.strokeWidth = 9f
                            outlinePaint.strokeCap   = android.graphics.Paint.Cap.ROUND
                            outlinePaint.strokeJoin  = android.graphics.Paint.Join.ROUND
                            // Dashed so it reads as "helper's path to you" rather than
                            // being confused with the helper's own solid turn-by-turn line.
                            outlinePaint.pathEffect  = android.graphics.DashPathEffect(floatArrayOf(20f, 14f), 0f)
                        }
                        map.overlays.add(newLine)
                        polyline = newLine
                    }
                    map.invalidate()
                }
            } catch (e: Exception) {
                android.util.Log.e("RiderHelperLiveRoute", "Failed to fetch rider-helper route", e)
            }
        }
    }
}