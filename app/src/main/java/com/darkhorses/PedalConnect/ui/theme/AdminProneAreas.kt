package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ProneAreaStat(
    val location: String,
    val count: Int,
    val percentage: Int
)

// ── Design tokens — mirrors AdminScreen.kt's palette ──────────────────────────
private val PAGreen900 = Color(0xFF06402B)
private val PAGreen50  = Color(0xFFF0FAF5)
private val PARed      = Color(0xFFD32F2F)
private val PAAmber    = Color(0xFFF59E0B)
private val PAWhite    = Color(0xFFFFFFFF)
private val PAOnSurf   = Color(0xFF111827)
private val PAMuted    = Color(0xFF6B7280)
private val PADivider  = Color(0xFFE5E7EB)

private fun rankColor(index: Int): Color = when (index) {
    0 -> PARed
    1 -> Color(0xFFF57C00)
    2 -> PAAmber
    else -> PAMuted
}

@Composable
fun AdminProneAreasSection(
    areas     : List<ProneAreaStat>,
    isLoading : Boolean
) {
    if (isLoading) {
        Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), Alignment.Center) {
            CircularProgressIndicator(color = PAGreen900, strokeWidth = 2.5.dp, modifier = Modifier.size(32.dp))
        }
        return
    }
    if (areas.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(PAGreen50), Alignment.Center) {
                Icon(Icons.Default.LocationOn, null, tint = PAGreen900, modifier = Modifier.size(28.dp))
            }
            Text("No alerts reported yet", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = PAOnSurf)
            Text("Reported alert locations will be ranked here.",
                fontSize = 13.sp, color = PAMuted, textAlign = TextAlign.Center)
        }
        return
    }

    val topThree = areas.take(3)
    val maxCount = areas.first().count

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            Text("Most Alert Prone Areas", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = PAOnSurf)
            Text("Locations ranked by number of reported alerts", fontSize = 12.sp, color = PAMuted)
        }

        // ── Most Dangerous Spots — top 3 highlight card ───────────────────────
        Card(
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = PAWhite),
            elevation = CardDefaults.cardElevation(2.dp),
            border    = BorderStroke(1.dp, PARed.copy(alpha = 0.25f))
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(PARed), Alignment.Center) {
                        Icon(Icons.Default.PriorityHigh, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                    Text("Most Dangerous Spots", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PAOnSurf)
                }
                topThree.forEachIndexed { index, area ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(rankColor(index)), Alignment.Center) {
                                Text("${index + 1}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(area.location, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    color = PAOnSurf, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${area.count} alert${if (area.count != 1) "s" else ""}", fontSize = 11.sp, color = PAMuted)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .widthIn(min = 44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(rankColor(index))
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${area.percentage}%",
                                fontSize   = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color      = Color.White,
                                maxLines   = 1,
                                softWrap   = false
                            )
                        }
                    }
                    if (index < topThree.lastIndex) HorizontalDivider(color = PADivider, thickness = 0.5.dp)
                }
            }
        }

        // ── Full ranked list ───────────────────────────────────────────────────
        Card(
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = PAWhite),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                areas.forEachIndexed { index, area ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${index + 1}", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = if (index < 3) rankColor(index) else PAMuted,
                            modifier = Modifier.width(22.dp)
                        )
                        Icon(
                            Icons.Default.LocationOn, null,
                            tint = if (index < 3) rankColor(index) else PAMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(area.location, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = PAOnSurf, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${area.count} alert${if (area.count != 1) "s" else ""} · ${area.percentage}% of alerts",
                                fontSize = 11.sp, color = PAMuted)
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(PADivider)) {
                                val fraction = if (maxCount > 0) area.count.toFloat() / maxCount else 0f
                                Box(
                                    Modifier.fillMaxWidth(fraction).height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (index < 3) rankColor(index) else rankColor(index).copy(alpha = 0.4f))
                                )
                            }
                        }
                    }
                    if (index < areas.lastIndex) {
                        HorizontalDivider(color = PADivider, thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp))
                    }
                }
            }
        }
    }
}