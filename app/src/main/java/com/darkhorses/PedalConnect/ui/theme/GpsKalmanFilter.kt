package com.darkhorses.PedalConnect.tracking

/**
 * 2-axis Kalman filter for GPS lat/lon smoothing.
 *
 * Models position as a random-walk with process noise Q and measurement noise R.
 * Each axis (lat, lon) is treated as independent — good enough for cycling at
 * city-block scales where cross-correlation is negligible.
 *
 * Key parameters
 * ──────────────
 * Q_METERS_PER_SECOND  How fast we expect the rider to accelerate (m/s).
 *   Lower → filter trusts past state more → smoother but lags real movement.
 *   Higher → filter trusts new GPS hits more → responsive but noisier.
 *   2.5 m/s² works well for cycling (sprint = ~4 m/s², cruise = ~0.5 m/s²).
 *
 * The measurement covariance R is set dynamically from location.accuracy
 * so a "bad" GPS fix (accuracy = 60 m) is automatically down-weighted vs.
 * a good fix (accuracy = 4 m).
 */
class GpsKalmanFilter {

    companion object {
        // Expected acceleration noise in m/s — tune for cycling dynamics
        // Lowered from 2.5 → 0.8 m/s².
        // 2.5 was too aggressive — it made the filter chase noisy fixes while stationary.
        // 0.8 still tracks real cycling acceleration but holds position under jitter.
        // 1.5 m/s² — balanced for cycling dynamics.
        // Responsive enough for sprints and descents,
        // stable enough to resist stationary jitter.
        private const val Q_METERS_PER_SECOND = 1.5
        // Minimum allowed variance — prevents filter from becoming overconfident
        private const val MIN_ACCURACY_M = 1.0
    }

    private var estimatedLat  = 0.0
    private var estimatedLon  = 0.0
    private var variance      = -1.0   // negative = not yet initialised
    private var lastTimestampMs = 0L

    val isInitialized get() = variance > 0

    /**
     * Feed a new raw GPS reading into the filter.
     *
     * @param lat          Raw latitude from Location
     * @param lon          Raw longitude from Location
     * @param accuracyM    location.accuracy in metres (horizontal 68% CEP)
     * @param timestampMs  location.time in milliseconds
     * @return Pair(filteredLat, filteredLon)
     */
    fun process(lat: Double, lon: Double, accuracyM: Float, timestampMs: Long): Pair<Double, Double> {
        val accuracy = maxOf(accuracyM.toDouble(), MIN_ACCURACY_M)

        if (!isInitialized) {
            // Bootstrap: trust the first fix entirely
            estimatedLat    = lat
            estimatedLon    = lon
            variance        = accuracy * accuracy
            lastTimestampMs = timestampMs
            return Pair(lat, lon)
        }

        // ── Predict ──────────────────────────────────────────────────────────
        // Grow variance by elapsed time × process noise
        val dtSeconds = ((timestampMs - lastTimestampMs) / 1000.0).coerceAtLeast(0.0)
        lastTimestampMs = timestampMs

        val processNoise = Q_METERS_PER_SECOND * dtSeconds
        variance += processNoise * processNoise

        // ── Update ───────────────────────────────────────────────────────────
        // Kalman gain: how much to trust the new measurement vs our prediction
        val measurementVariance = accuracy * accuracy
        val gain = variance / (variance + measurementVariance)

        estimatedLat += gain * (lat - estimatedLat)
        estimatedLon += gain * (lon - estimatedLon)
        variance     *= (1.0 - gain)  // posterior variance — tightens after good fix

        return Pair(estimatedLat, estimatedLon)
    }

    /** Call when GPS signal is lost so the next fix bootstraps cleanly. */
    fun reset() {
        variance        = -1.0
        lastTimestampMs = 0L
    }

    /** Current estimated accuracy in metres (1-σ). Useful for diagnostics. */
    val estimatedAccuracyM: Double get() = if (isInitialized) Math.sqrt(variance) else Double.MAX_VALUE
}