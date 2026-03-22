package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.osmdroid.util.GeoPoint

// ── ShopDetailSheet ───────────────────────────────────────────────────────────
// Shown when a user taps a hospital or bike shop marker on the map.
//
// Parameters:
//   shop        — the tapped ShopItem
//   onDismiss   — called when sheet should close
//   onNavigate  — called with the shop's GeoPoint when "Navigate Here" is tapped
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopDetailSheet(
    shop       : ShopItem,
    onDismiss  : () -> Unit,
    onNavigate : (GeoPoint) -> Unit
) {
    val sheetState   = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isHospital   = shop.type == ShopType.HOSPITAL
    val primaryColor = if (isHospital) Color(0xFFD32F2F) else Color(0xFF0A5C3D)
    val bgColor      = if (isHospital) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
    val typeLabel    = if (isHospital) "Hospital" else "Bike Shop"
    val isOpen24     = shop.openHours.contains("24", ignoreCase = true)
    val hasAddress   = shop.address != "See map" && shop.address.isNotBlank()
    val hasHours     = shop.openHours != "See location" &&
            shop.openHours != "24 hours" &&
            shop.openHours.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = Color.White,
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(bgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isHospital) Icons.Default.LocalHospital else Icons.Default.Build,
                        null, tint = primaryColor, modifier = Modifier.size(26.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        shop.name,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 16.sp,
                        color      = Color(0xFF1A1A1A),
                        lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        // Type badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(bgColor)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(typeLabel, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold, color = primaryColor)
                        }
                        // 24h badge
                        if (isOpen24) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFE8F5E9))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("24h", fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            }
                        }
                        // Distance badge — always real data
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF2F5F3))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(shop.distance, fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold, color = Color(0xFF555555))
                        }
                    }
                }
            }

            // ── OSM data rows — only shown when real data exists ──────────────
            if (hasAddress || hasHours) {
                HorizontalDivider(color = Color(0xFFF0F0F0))

                if (hasAddress) {
                    Row(
                        verticalAlignment     = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, null,
                            tint     = Color(0xFF9E9E9E),
                            modifier = Modifier.size(16.dp).padding(top = 2.dp))
                        Text(shop.address, fontSize = 13.sp,
                            color = Color(0xFF555555), lineHeight = 19.sp)
                    }
                }

                if (hasHours) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.AccessTime, null,
                            tint     = Color(0xFF9E9E9E),
                            modifier = Modifier.size(16.dp))
                        Text(shop.openHours, fontSize = 13.sp,
                            color = Color(0xFF555555))
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFF0F0F0))

            // ── Navigate Here — unique to the map context ─────────────────────
            Button(
                onClick   = { onNavigate(shop.location) },
                modifier  = Modifier.fillMaxWidth().height(50.dp),
                shape     = RoundedCornerShape(14.dp),
                colors    = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor   = Color.White
                )
            ) {
                Icon(Icons.Default.Navigation, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Navigate Here", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }
    }
}