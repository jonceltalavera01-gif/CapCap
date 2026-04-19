package com.darkhorses.PedalConnect.ui.theme

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.osmdroid.views.MapView

// ── Speed threshold above which GPS bearing takes over from compass ───────────
private const val GPS_BEARING_MIN_SPEED_KMH = 5f

// ── Low-pass filter alpha — higher = smoother but slower to react ─────────────
private const val COMPASS_ALPHA = 0.15f

/**
 * Remembers a fused heading value that blends:
 *  - Compass (accelerometer + magnetometer) when speed < GPS_BEARING_MIN_SPEED_KMH
 *  - GPS bearing when speed >= GPS_BEARING_MIN_SPEED_KMH
 *
 * Returns the current heading in degrees (0–360, 0 = north).
 */
@Composable
fun rememberFusedHeading(
    gpsBearing: Float,       // degrees, from Location.bearing
    gpsSpeed: Float,         // km/h
    isActive: Boolean        // only register sensors when heading mode is on
): State<Float> {
    val context = LocalContext.current
    val heading = remember { mutableFloatStateOf(0f) }

    DisposableEffect(isActive) {
        if (!isActive) return@DisposableEffect onDispose {}

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelSensor   = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetSensor  = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val gravity   = FloatArray(3)
        val geomag    = FloatArray(3)
        val rotation  = FloatArray(9)
        val orientation = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // If moving fast enough, GPS bearing is more accurate — skip compass
                if (gpsSpeed >= GPS_BEARING_MIN_SPEED_KMH) return

                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        // Low-pass filter to remove vibration noise from cycling
                        gravity[0] = COMPASS_ALPHA * event.values[0] + (1 - COMPASS_ALPHA) * gravity[0]
                        gravity[1] = COMPASS_ALPHA * event.values[1] + (1 - COMPASS_ALPHA) * gravity[1]
                        gravity[2] = COMPASS_ALPHA * event.values[2] + (1 - COMPASS_ALPHA) * gravity[2]
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        geomag[0] = COMPASS_ALPHA * event.values[0] + (1 - COMPASS_ALPHA) * geomag[0]
                        geomag[1] = COMPASS_ALPHA * event.values[1] + (1 - COMPASS_ALPHA) * geomag[1]
                        geomag[2] = COMPASS_ALPHA * event.values[2] + (1 - COMPASS_ALPHA) * geomag[2]
                    }
                }

                if (SensorManager.getRotationMatrix(rotation, null, gravity, geomag)) {
                    SensorManager.getOrientation(rotation, orientation)
                    val azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    val normalised = (azimuthDeg + 360f) % 360f
                    heading.floatValue = normalised
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(listener, magnetSensor, SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // When GPS bearing is reliable, override compass reading
    LaunchedEffect(gpsBearing, gpsSpeed) {
        if (gpsSpeed >= GPS_BEARING_MIN_SPEED_KMH) {
            heading.floatValue = gpsBearing
        }
    }

    return heading
}

/**
 * Applies the fused heading to the OSMdroid MapView rotation.
 * Call this inside a LaunchedEffect or SideEffect that watches headingState + isHeadingMode.
 *
 * OSMdroid rotates the map so that the user's heading points "up".
 * mapOrientation is the *counter-rotation* of the map — so we negate the heading.
 */
fun applyHeadingToMap(mapView: MapView?, heading: Float, isHeadingMode: Boolean) {
    mapView ?: return
    mapView.mapOrientation = if (isHeadingMode) -heading else 0f
}

/**
 * The re-center FAB that also shows heading-lock state.
 *
 * States:
 *  1. Hidden               — when already following + not in heading mode (default centered)
 *  2. Re-center (blue dot) — user has panned away; tap re-centers + enters heading mode
 *  3. Heading lock (arrow) — heading mode active; arrow rotates to show current bearing;
 *                            tap exits heading mode → north-up, stay centered
 */
@Composable
fun RecenterHeadingButton(
    isFollowingLocation: Boolean,
    isHeadingMode: Boolean,
    currentHeading: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Animate the compass arrow rotation smoothly
    val animatedRotation by animateFloatAsState(
        targetValue   = currentHeading,
        animationSpec = tween(durationMillis = 300),
        label         = "headingRotation"
    )

    // Icon tint: orange-ish when heading-locked (matches Google Maps), green when just re-centering
    val containerColor = when {
        isHeadingMode       -> Color(0xFF1565C0)   // heading locked — blue
        !isFollowingLocation -> Color.White          // panned away — white
        else                -> Color.Transparent    // centered + north-up — hidden
    }

    val iconTint = when {
        isHeadingMode        -> Color.White
        !isFollowingLocation -> Color(0xFF06402B)
        else                 -> Color.Transparent
    }

    // Only render when there's something to show
    if (isFollowingLocation && !isHeadingMode) return

    Box(
        modifier = modifier
            .size(44.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MyLocation,
            contentDescription = "Re-center",
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
    }
}