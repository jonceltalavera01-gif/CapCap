package com.darkhorses.PedalConnect.tracking

import android.location.Location
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import kotlin.math.*

/**
 * Production-grade GPS trail pipeline for a cycling ride.
 *
 * Pipeline order (each stage can reject/modify a point):
 *
 *   Raw Location
 *       │
 *       ▼
 *   [1] Accuracy gate       → reject if accuracy > threshold
 *       │
 *       ▼
 *   [2] Spike detector      → reject if implied speed or jump is impossible
 *       │
 *       ▼
 *   [3] Kalman filter       → smooth lat/lon
 *       │
 *       ▼
 *   [4] Stationary gate     → reject if moved < MIN_TRAIL_DISTANCE_M
 *                             (kills stationary drift / phantom distance)
 *       │
 *       ▼
 *   [5] Segmentation check  → if gap > SEGMENT_GAP_MS, start new segment
 *                             (prevents "teleport" lines after GPS loss)
 *       │
 *       ▼
 *   [6] Accept point        → append to current segment, update anchors
 *       │
 *       ▼
 *   [7] Throttled redraw    → only rebuild polyline every REDRAW_INTERVAL_MS
 *
 * ─────────────────────────────────────────────────────────────────────────
 * Key parameters — all documented at their declaration below.
 * ─────────────────────────────────────────────────────────────────────────
 */
class GpsTrailPipeline {

    // ── Tuneable constants ────────────────────────────────────────────────────

    companion object {
        /**
         * [Stage 1] Maximum horizontal accuracy to accept a fix.
         * Android reports this as the radius of a 68% confidence circle (metres).
         * 40 m is permissive enough for urban canyons while blocking truly bad fixes.
         * Lower (e.g. 20 m) = more dropouts in cities; higher (e.g. 65 m) = more noise.
         */
        const val MAX_ACCURACY_M = 40f
        const val MAX_SPEED_MS = 24f
        const val MAX_HOP_M = 80f

        // Raised from 3.0 → 6.0 to absorb urban canyon GPS jitter while stationary.
        // A cyclist moving slowly still covers 6m in ~2s at walking pace, so this
        // does not lose real movement. Stationary jitter in Manila is typically 4–8m.
        // 3.0m — tight enough to trace corners accurately on narrow streets
        // without accumulating stationary drift (Kalman handles the smoothing)
        const val MIN_TRAIL_DISTANCE_M = 3.0

        // 0.15 m/s = 0.5 km/h — only rejects true stationary noise.
        // Slow climbs, tight corners, and traffic weaving all survive this.
        const val MIN_IMPLIED_SPEED_MS = 0.15

        // 45s — covers EDSA underpasses, BGC corridors, and mall entries
        // without splitting a continuous ride into fragments
        const val SEGMENT_GAP_MS = 45_000L

        // 1.5s redraw — smooth enough to feel live without hammering the GPU
        const val REDRAW_INTERVAL_MS = 1_500L
    }

    // ── Internal state ────────────────────────────────────────────────────────

    private val kalman = GpsKalmanFilter()

    /** Each segment is a list of GeoPoints with no GPS gap in between. */
    private val segments = mutableListOf<MutableList<GeoPoint>>()

    /** Last raw Location that passed stages 1–2 (used for spike detection anchor). */
    private var lastRawAccepted: Location? = null

    /** Last GeoPoint that was committed to a segment (used for distance gate). */
    private var lastCommittedPoint: GeoPoint? = null

    /** Timestamp of the last committed point (used for segmentation). */
    private var lastCommittedMs = 0L

    /** Timestamp of the last polyline redraw (used for throttle). */
    private var lastRedrawMs = 0L

    /**
     * Rolling bearing history — last 3 accepted movement bearings in degrees.
     * Used to detect directional consistency: real cycling has consistent
     * bearing, stationary jitter is random in all directions.
     */
    private val recentBearings = ArrayDeque<Float>(3)

    /**
     * Consecutive consistent-direction accepts — resets on direction reversal.
     * Trail only draws after 2 consistent points to avoid single-spike commits.
     */
    private var consistentMoveCount = 0

    // ── Accumulated ride metrics ──────────────────────────────────────────────

    /** Total distance in metres — only incremented for accepted, non-drift points. */
    var totalDistanceM = 0.0
        private set

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Feed a raw Location from the LocationListener.
     * Returns a [PipelineResult] describing what happened and whether the
     * polylines on the map need to be redrawn.
     */
    fun process(location: Location): PipelineResult {

        // ── Stage 1: Accuracy gate ────────────────────────────────────────────
        if (location.accuracy > MAX_ACCURACY_M) {
            return PipelineResult.Rejected(RejectionReason.POOR_ACCURACY)
        }

        // ── Stage 2: Spike detector ───────────────────────────────────────────
        lastRawAccepted?.let { prev ->
            val distM    = prev.distanceTo(location)
            val dtSec    = ((location.time - prev.time) / 1000f).coerceAtLeast(0.001f)
            val impliedMs = distM / dtSec

            if (impliedMs > MAX_SPEED_MS || distM > MAX_HOP_M) {
                // Don't update lastRawAccepted — keep the good anchor
                return PipelineResult.Rejected(RejectionReason.SPIKE)
            }
        }
        // Point survived spike check — update raw anchor
        lastRawAccepted = location

        // ── Stage 3: Kalman filter ────────────────────────────────────────────
        val (filtLat, filtLon) = kalman.process(
            lat         = location.latitude,
            lon         = location.longitude,
            accuracyM   = location.accuracy,
            timestampMs = location.time
        )

        // ── Stage 4: Stationary / minimum-distance gate ───────────────────────
        val filteredPoint = GeoPoint(filtLat, filtLon)
        val distFromLast  = lastCommittedPoint?.let { last ->
            haversineM(last.latitude, last.longitude, filtLat, filtLon)
        } ?: Double.MAX_VALUE  // first ever point always passes

        // Distance gate — reject if not moved enough
        if (distFromLast < MIN_TRAIL_DISTANCE_M) {
            consistentMoveCount = 0
            return PipelineResult.Rejected(RejectionReason.STATIONARY_DRIFT)
        }

        // Implied speed gate — reject if movement is too slow to be real cycling
        val dtSec = ((location.time - lastCommittedMs) / 1000.0).coerceAtLeast(0.001)
        val impliedSpeedMs = distFromLast / dtSec
        if (lastCommittedMs > 0 && impliedSpeedMs < MIN_IMPLIED_SPEED_MS) {
            consistentMoveCount = 0
            return PipelineResult.Rejected(RejectionReason.STATIONARY_DRIFT)
        }

        // ── Stage 4b: Bearing consistency gate ───────────────────────────────
        // Real cycling = consistent forward direction.
        // Stationary jitter = random bearings that reverse constantly.
        // We compute the bearing from last committed point to this filtered point
        // and check if it is consistent with recent movement history.
        val prevPoint = lastCommittedPoint
        if (prevPoint != null && recentBearings.size >= 2) {
            val currentBearing = bearingDeg(
                prevPoint.latitude, prevPoint.longitude,
                filtLat, filtLon
            )
            // Average of recent bearings as reference direction
            val avgBearing = recentBearings.average().toFloat()
            val angleDiff  = bearingDiffDeg(currentBearing, avgBearing)

            // If this point reverses direction by more than 90°, it is likely jitter
            // bouncing back. Require 2 consistent points before committing.
            if (angleDiff > 90f) {
                consistentMoveCount = 0
                return PipelineResult.Rejected(RejectionReason.STATIONARY_DRIFT)
            }

            // Update bearing history
            if (recentBearings.size >= 3) recentBearings.removeFirst()
            recentBearings.addLast(currentBearing)
            consistentMoveCount++
        } else {
            // Not enough history yet — compute and store bearing, accept point
            if (prevPoint != null) {
                val bearing = bearingDeg(
                    prevPoint.latitude, prevPoint.longitude,
                    filtLat, filtLon
                )
                if (recentBearings.size >= 3) recentBearings.removeFirst()
                recentBearings.addLast(bearing)
            }
            consistentMoveCount++
        }

        // ── Stage 5: Segmentation ─────────────────────────────────────────────
        val nowMs      = location.time
        val gapMs      = if (lastCommittedMs > 0) nowMs - lastCommittedMs else 0L
        val newSegment = gapMs > SEGMENT_GAP_MS && lastCommittedMs > 0

        if (newSegment) {
            // Signal loss — start fresh segment to avoid teleport line
            kalman.reset()
            segments.add(mutableListOf())
        }

        // ── Stage 6: Commit point ─────────────────────────────────────────────
        if (segments.isEmpty()) segments.add(mutableListOf())
        segments.last().add(filteredPoint)

        // Accumulate distance only for points that crossed the MIN threshold
        if (lastCommittedPoint != null) {
            totalDistanceM += distFromLast
        }

        lastCommittedPoint = filteredPoint
        lastCommittedMs    = nowMs

        // ── Stage 7: Throttled redraw decision ───────────────────────────────
        val shouldRedraw = (nowMs - lastRedrawMs) >= REDRAW_INTERVAL_MS
        if (shouldRedraw) lastRedrawMs = nowMs

        return PipelineResult.Accepted(
            point         = filteredPoint,
            newSegment    = newSegment,
            shouldRedraw  = shouldRedraw,
            distanceAddedM = if (lastCommittedPoint != null) distFromLast else 0.0
        )
    }

    /**
     * Apply all current segments to a list of OSMdroid Polylines.
     * Call this when [PipelineResult.Accepted.shouldRedraw] is true, OR
     * immediately after Stop Ride to get the final trail.
     *
     * The caller is responsible for adding/removing these polylines from the
     * MapView overlay list. This method only mutates the point data.
     *
     * @param polylines Mutable list managed by the caller. This method will
     *                  grow it if new segments were added since last call.
     * @param stylePolyline Lambda to apply stroke style to a new Polyline.
     */
    fun applyToPolylines(
        polylines: MutableList<Polyline>,
        stylePolyline: (Polyline) -> Unit
    ) {
        segments.forEachIndexed { idx, seg ->
            if (seg.isEmpty()) return@forEachIndexed

            val poly = if (idx < polylines.size) {
                polylines[idx]
            } else {
                Polyline().also {
                    stylePolyline(it)
                    polylines.add(it)
                }
            }
            poly.setPoints(seg)
        }
    }

    /** All committed GeoPoints across all segments, in order. */
    val allPoints: List<GeoPoint>
        get() = segments.flatten()

    /** Number of accepted segments (>1 means there was a GPS gap during the ride). */
    val segmentCount: Int get() = segments.size

    /** Reset everything — call at Start Ride or after dismissing the summary. */
    fun reset() {
        segments.clear()
        lastRawAccepted    = null
        lastCommittedPoint = null
        lastCommittedMs    = 0L
        lastRedrawMs       = 0L
        totalDistanceM     = 0.0
        recentBearings.clear()
        consistentMoveCount = 0
        kalman.reset()
    }

    // ── Internal geometry ─────────────────────────────────────────────────────

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R    = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a    = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Compass bearing in degrees (0–360) from point 1 → point 2. */
    private fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1R = Math.toRadians(lat1)
        val lat2R = Math.toRadians(lat2)
        val x = sin(dLon) * cos(lat2R)
        val y = cos(lat1R) * sin(lat2R) - sin(lat1R) * cos(lat2R) * cos(dLon)
        return ((Math.toDegrees(atan2(x, y)).toFloat() + 360f) % 360f)
    }

    /**
     * Shortest angular difference between two bearings (0–180°).
     * e.g. bearingDiffDeg(350f, 10f) = 20f, not 340f.
     */
    private fun bearingDiffDeg(a: Float, b: Float): Float {
        val diff = ((a - b + 180f + 360f) % 360f) - 180f
        return abs(diff)
    }
}

// ── Result types ──────────────────────────────────────────────────────────────

sealed class PipelineResult {
    data class Accepted(
        val point: GeoPoint,
        val newSegment: Boolean,
        val shouldRedraw: Boolean,
        val distanceAddedM: Double
    ) : PipelineResult()

    data class Rejected(val reason: RejectionReason) : PipelineResult()
}

enum class RejectionReason {
    POOR_ACCURACY,    // location.accuracy > MAX_ACCURACY_M
    SPIKE,            // implied speed or hop too large
    STATIONARY_DRIFT  // moved < MIN_TRAIL_DISTANCE_M from last accepted point
}