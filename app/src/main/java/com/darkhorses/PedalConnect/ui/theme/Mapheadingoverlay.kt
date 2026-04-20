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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.unit.sp
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
// Tracks the last smoothed orientation for interpolation
private var lastSmoothedOrientation = 0f

fun applyHeadingToMap(mapView: MapView?, heading: Float, isHeadingMode: Boolean, onRotationChanged: ((Float) -> Unit)? = null) {
    mapView ?: return
    if (!isHeadingMode) {
        lastSmoothedOrientation = 0f
        mapView.mapOrientation  = 0f
        onRotationChanged?.invoke(0f)
        return
    }

    val target = -heading

    // Shortest-path interpolation — prevents spinning the long way around 0/360
    var delta = target - lastSmoothedOrientation
    if (delta > 180f)  delta -= 360f
    if (delta < -180f) delta += 360f

    // Smooth factor — 0.15 = very smooth/slow, 0.25 = balanced, 0.4 = snappy
    val smoothed = lastSmoothedOrientation + delta * 0.3f
    lastSmoothedOrientation = smoothed

    mapView.mapOrientation = smoothed
    onRotationChanged?.invoke(smoothed)
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
    mapRotationDegrees: Float,       // current map orientation in degrees
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Animate compass needle rotation smoothly
    val animatedRotation by animateFloatAsState(
        targetValue   = -mapRotationDegrees,   // counter-rotate so needle always points north
        animationSpec = tween(durationMillis = 250),
        label         = "compassRotation"
    )

    // Only visible when user has panned away OR map is rotated off north
    val isMapRotated = mapRotationDegrees != 0f
    val isVisible    = !isFollowingLocation || isMapRotated

    if (!isVisible) return

    Box(
        modifier = modifier
            .size(44.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isMapRotated) {
            // Show compass needle pointing to north when map is rotated
            Icon(
                imageVector        = Icons.Default.Navigation,
                contentDescription = "Snap to north",
                tint               = Color(0xFFD32F2F),   // red needle — matches Google Maps
                modifier           = Modifier
                    .size(20.dp)
                    .rotate(animatedRotation)
            )
        } else {
            // Show MyLocation icon when just re-centering
            Icon(
                imageVector        = Icons.Default.MyLocation,
                contentDescription = "Re-center",
                tint               = Color(0xFF06402B),
                modifier           = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun NearbyAlertBanner(
    alert    : NearbyAlertInfo,
    onTap    : () -> Unit,
    onDismiss: () -> Unit
) {
    var progress by remember { mutableFloatStateOf(1f) }
    val animatedProgress by animateFloatAsState(
        targetValue   = progress,
        animationSpec = tween(durationMillis = 6000, easing = androidx.compose.animation.core.LinearEasing),
        label         = "bannerProgress"
    )

    LaunchedEffect(alert.alertId) {
        progress = 0f
        kotlinx.coroutines.delay(6000L)
        onDismiss()
    }

    val isUrgent      = alert.emergencyType == "Urgent Help"
    val containerColor = if (isUrgent) Color(0xFF4A147C) else Color(0xFFD32F2F)
    val iconVector    = if (isUrgent) Icons.Default.PriorityHigh else Icons.Default.Warning

    AnimatedVisibility(
        visible = true,
        enter   = slideInVertically(
            initialOffsetY = { -it },
            animationSpec  = tween(320, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(240)),
        exit    = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(260)
        ) + fadeOut(animationSpec = tween(180))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(containerColor)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication        = null,
                    onClick           = onTap
                )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Icon
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = iconVector,
                            contentDescription = null,
                            tint               = Color.White,
                            modifier           = Modifier.size(20.dp)
                        )
                    }

                    // Text
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = if (isUrgent) "🚨 Urgent Help Nearby!" else "🆘 Alert Nearby",
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = Color.White,
                            maxLines   = 1
                        )
                        Text(
                            text     = "${alert.emergencyType} · ${alert.riderDisplayName} · ${alert.distanceText}",
                            fontSize = 11.sp,
                            color    = Color.White.copy(alpha = 0.80f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Dismiss
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication        = null,
                                onClick           = onDismiss
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint               = Color.White,
                            modifier           = Modifier.size(14.dp)
                        )
                    }
                }

                // Duration bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.20f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.75f))
                    )
                }
            }
        }
    }
}

data class NearbyAlertInfo(
    val alertId        : String,
    val riderDisplayName: String,
    val emergencyType  : String,
    val distanceText   : String
)