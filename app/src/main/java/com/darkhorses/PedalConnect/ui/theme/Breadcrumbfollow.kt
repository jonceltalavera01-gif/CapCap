package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.osmdroid.util.GeoPoint
import java.util.Locale

/**
 * Result of matching a live GPS fix against a recorded breadcrumb trail.
 *
 * [matchedIdx]     — index into [breadcrumbPoints] the fix is currently closest to
 *                     (bounded to a forward window from the previous match — see
 *                     [matchBreadcrumbProgress]).
 * [distFromTrailKm]— distance in km from the fix to that matched point. Used by the
 *                     caller to decide whether to show an off-route toast.
 * [remainingKm]    — cumulative distance from [matchedIdx] to the end of the trail.
 */
data class BreadcrumbMatch(
    val matchedIdx: Int,
    val distFromTrailKm: Double,
    val remainingKm: Double
)

/**
 * Pure function — no HomeScreen state, no side effects. Matches [currentPoint]
 * against [breadcrumbPoints], searching only a forward window starting at
 * [lastMatchedIdx]. This enforces forward progress structurally (can't match a
 * point behind where we already were) and avoids false matches to a far-ahead
 * leg of a route that loops back near itself.
 *
 * Mirrors the inline logic that used to live in HomeScreen's LocationListener.
 */
fun matchBreadcrumbProgress(
    currentPoint: GeoPoint,
    breadcrumbPoints: List<GeoPoint>,
    lastMatchedIdx: Int,
    windowSize: Int = 40
): BreadcrumbMatch {
    require(breadcrumbPoints.size >= 2) { "matchBreadcrumbProgress requires at least 2 points" }

    val windowEnd = (lastMatchedIdx + windowSize).coerceAtMost(breadcrumbPoints.size - 1)
    var bestIdx = lastMatchedIdx
    var bestDist = haversineKm(
        currentPoint.latitude, currentPoint.longitude,
        breadcrumbPoints[lastMatchedIdx].latitude, breadcrumbPoints[lastMatchedIdx].longitude
    )
    for (i in lastMatchedIdx..windowEnd) {
        val d = haversineKm(
            currentPoint.latitude, currentPoint.longitude,
            breadcrumbPoints[i].latitude, breadcrumbPoints[i].longitude
        )
        if (d < bestDist) { bestDist = d; bestIdx = i }
    }

    var remKm = 0.0
    for (i in bestIdx + 1 until breadcrumbPoints.size) {
        remKm += haversineKm(
            breadcrumbPoints[i - 1].latitude, breadcrumbPoints[i - 1].longitude,
            breadcrumbPoints[i].latitude, breadcrumbPoints[i].longitude
        )
    }

    return BreadcrumbMatch(matchedIdx = bestIdx, distFromTrailKm = bestDist, remainingKm = remKm)
}

/**
 * "Following saved route" banner — shown while retracing a saved route's
 * recorded trail. Pure UI; all state lives in the caller (HomeScreen).
 */
@Composable
fun BreadcrumbFollowBanner(
    visible: Boolean,
    remainingKm: Double,
    onStop: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn() + slideInVertically(initialOffsetY = { -it }),
        exit    = fadeOut() + slideOutVertically(targetOffsetY = { -it })
    ) {
        Card(
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = Color(0xFF0288D1)),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier         = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.DirectionsBike, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Following saved route", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    Text(
                        "${String.format(Locale.getDefault(), "%.1f", remainingKm)} km remaining",
                        fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f)
                    )
                }
                IconButton(onClick = onStop, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}