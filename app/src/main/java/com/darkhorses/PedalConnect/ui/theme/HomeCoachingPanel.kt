package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
internal fun CoachingWorkoutPanel(
    workout: TrainingWorkout,
    elapsedSeconds: Long,
    currentSpeedKmh: Float,
    totalDistanceM: Double,
    avgSpeedKmh: Float,
    maxSpeedKmh: Float,
    coachingMessage: String,
    intervalPhase: String,
    currentInterval: Int,
    phaseSecondsRemaining: Int,
    showHydration: Boolean,
    showNutrition: Boolean
) {
    // Collapsed by default — the full type-specific layout (progress bars,
    // interval timers, etc.) was covering the map and FABs during a ride.
    // Tap the header to expand for the detailed view when actually wanted.
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.98f))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { expanded = !expanded },
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        workout.title,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = Color(0xFF06402B),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    AnimatedContent(
                        targetState = coachingMessage,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "coaching"
                    ) { msg ->
                        Text(
                            msg.ifBlank { "Keep pedaling!" },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF0A5C3D),
                            fontStyle = FontStyle.Italic,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                Surface(
                    color = when (workout.type) {
                        "Recovery" -> Color(0xFFE8F5E9)
                        "Intervals" -> Color(0xFFFFEBEE)
                        "Long Ride" -> Color(0xFFE3F2FD)
                        "Race" -> Color(0xFFF3E5F5)
                        else -> Color(0xFFE8F5E9)
                    }, shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        workout.type,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (workout.type) {
                            "Recovery" -> Color(0xFF2E7D32)
                            "Intervals" -> Color(0xFFC62828)
                            "Long Ride" -> Color(0xFF1565C0)
                            "Race" -> Color(0xFF7B1FA2)
                            else -> Color(0xFF06402B)
                        }
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse workout panel" else "Expand workout panel",
                    tint = Color(0xFF9E9E9E),
                    modifier = Modifier.padding(start = 6.dp).size(20.dp)
                )
            }

            if (!expanded) {
                // Compact always-visible stats — same three numbers regardless
                // of workout type, so the collapsed state stays useful without
                // the full type-specific layout's height.
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    WorkoutStatItem("Time", formatTimeTop(elapsedSeconds), Icons.Default.Timer, Color(0xFF06402B))
                    WorkoutStatItem("Speed", String.format("%.1f", currentSpeedKmh), Icons.Default.Speed, Color(0xFF06402B), "km/h")
                    WorkoutStatItem("Distance", String.format("%.2f", totalDistanceM / 1000.0), Icons.AutoMirrored.Filled.DirectionsBike, Color(0xFF06402B), "km")
                }
            } else {
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                when (workout.type) {
                    "Recovery" -> RecoveryLayout(elapsedSeconds, currentSpeedKmh, totalDistanceM, workout)
                    "Endurance" -> EnduranceLayout(totalDistanceM, workout, elapsedSeconds, avgSpeedKmh)
                    "Intervals" -> IntervalsLayout(
                        intervalPhase, currentInterval, workout.numIntervals ?: 1,
                        phaseSecondsRemaining, currentSpeedKmh
                    )
                    "Long Ride" -> LongRideLayout(
                        totalDistanceM, workout, elapsedSeconds, avgSpeedKmh, showHydration, showNutrition
                    )
                    "Race" -> RaceLayout(currentSpeedKmh, avgSpeedKmh, maxSpeedKmh, totalDistanceM, workout)
                    else -> DefaultWorkoutLayout(elapsedSeconds, currentSpeedKmh, totalDistanceM)
                }
            }
        }
    }
}

@Composable
private fun RecoveryLayout(elapsed: Long, speed: Float, distance: Double, workout: TrainingWorkout) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        WorkoutStatItem("Time", formatTimeTop(elapsed), Icons.Default.Timer, Color(0xFF06402B))
        WorkoutStatItem("Speed", String.format("%.1f", speed), Icons.Default.Speed, Color(0xFF06402B), "km/h")
        WorkoutStatItem("HR Goal", workout.hrZone ?: "Zone 1", Icons.Default.MonitorHeart, Color(0xFFD32F2F))
    }
    Text(
        "Effort Goal: EASY",
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = Color(0xFF2E7D32)
    )
}

@Composable
private fun EnduranceLayout(distanceM: Double, workout: TrainingWorkout, elapsed: Long, avgSpeed: Float) {
    val targetKm = workout.distanceKm; val currentKm = distanceM / 1000.0
    val progress = if (targetKm > 0) (currentKm / targetKm).toFloat().coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Distance Progress", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Text("${String.format("%.1f", currentKm)} / ${String.format("%.1f", targetKm)} km",
                fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = Color(0xFF2196F3), trackColor = Color(0xFFE3F2FD)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            WorkoutStatItem("Time", formatTimeTop(elapsed), Icons.Default.Timer, Color.Gray)
            WorkoutStatItem("Avg Speed", String.format("%.1f", avgSpeed), Icons.Default.Speed, Color.Gray, "km/h")
            WorkoutStatItem("Remaining", String.format("%.1f", (targetKm - currentKm).coerceAtLeast(0.0)),
                Icons.Default.Flag, Color.Gray, "km")
        }
    }
}

@Composable
private fun IntervalsLayout(phase: String, current: Int, total: Int, remainingSec: Int, speed: Float) {
    val phaseColor = if (phase == "WORK") Color(0xFFD32F2F) else Color(0xFF4CAF50)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Interval $current / $total", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Surface(color = phaseColor, shape = RoundedCornerShape(8.dp)) {
                Text(phase, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }
        Text(formatTimeTop(remainingSec.toLong()), fontSize = 42.sp, fontWeight = FontWeight.Black,
            color = phaseColor, fontFamily = FontFamily.Monospace)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            WorkoutStatItem("Speed", String.format("%.1f", speed), Icons.Default.Speed, Color(0xFF06402B), "km/h")
            WorkoutStatItem("Next", if (phase == "WORK") "RECOVERY" else "WORK", Icons.Default.Bolt, Color.Gray)
        }
    }
}

@Composable
private fun LongRideLayout(
    distanceM: Double, workout: TrainingWorkout, elapsed: Long, avgSpeed: Float,
    showHydrate: Boolean, showNutri: Boolean
) {
    val targetKm = workout.distanceKm; val currentKm = distanceM / 1000.0
    val progress = if (targetKm > 0) (currentKm / targetKm).toFloat().coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Long Ride Progress", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Text("${(progress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF06402B))
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            color = Color(0xFF0A5C3D), trackColor = Color(0xFFDDF1E8)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            WorkoutStatItem("Dist", String.format("%.1f", currentKm), Icons.AutoMirrored.Filled.DirectionsBike, Color.Gray, "km")
            WorkoutStatItem("Avg", String.format("%.1f", avgSpeed), Icons.Default.Speed, Color.Gray, "km/h")
            WorkoutStatItem("Left", String.format("%.1f", (targetKm - currentKm).coerceAtLeast(0.0)), Icons.Default.Flag, Color.Gray, "km")
        }
        if (showHydrate || showNutri) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                if (showHydrate) ReminderBadge("DRINK WATER \uD83D\uDCA7", Color(0xFF2196F3))
                if (showHydrate && showNutri) Spacer(Modifier.width(8.dp))
                if (showNutri) ReminderBadge("FUEL UP \uD83C\uDF4C", Color(0xFFF57C00))
            }
        }
    }
}

@Composable
private fun RaceLayout(speed: Float, avgSpeed: Float, maxSpeed: Float, distanceM: Double, workout: TrainingWorkout) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            WorkoutStatItem("Current", String.format("%.1f", speed), Icons.Default.Speed, Color(0xFFD32F2F), "km/h")
            WorkoutStatItem("Average", String.format("%.1f", avgSpeed), Icons.Default.Timeline, Color(0xFF06402B), "km/h")
            WorkoutStatItem("Max", String.format("%.1f", maxSpeed), Icons.Default.VerticalAlignTop, Color(0xFFF57C00), "km/h")
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5).copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("TARGET FINISH", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2))
                    Text(workout.targetFinishTime ?: "2h 30m", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("DISTANCE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2))
                    Text(String.format("%.1f km", distanceM / 1000.0), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun DefaultWorkoutLayout(elapsed: Long, speed: Float, distance: Double) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        WorkoutStatItem("Time", formatTimeTop(elapsed), Icons.Default.Timer, Color(0xFF06402B))
        WorkoutStatItem("Speed", String.format("%.1f", speed), Icons.Default.Speed, Color(0xFF06402B), "km/h")
        WorkoutStatItem("Dist", String.format("%.2f", distance / 1000.0), Icons.AutoMirrored.Filled.DirectionsBike, Color(0xFF06402B), "km")
    }
}

@Composable
private fun WorkoutStatItem(label: String, value: String, icon: ImageVector, color: Color, unit: String = "") {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, null, tint = color.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = color)
            if (unit.isNotEmpty()) Text(unit, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = color.copy(alpha = 0.6f), modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
        }
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
    }
}

@Composable
private fun ReminderBadge(text: String, color: Color) {
    Surface(color = color, shape = RoundedCornerShape(16.dp), shadowElevation = 4.dp) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
    }
}

private fun formatTimeTop(seconds: Long): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%02d:%02d", m, s)
}