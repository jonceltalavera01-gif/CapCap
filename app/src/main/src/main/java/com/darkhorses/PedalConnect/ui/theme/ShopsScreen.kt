package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.osmdroid.util.GeoPoint

private val Green900   = Color(0xFF06402B)
private val Green700   = Color(0xFF0A5C3D)
private val Green100   = Color(0xFFE8F5E9)
private val RedPrimary = Color(0xFFD32F2F)
private val RedLight   = Color(0xFFFFEBEE)
private val SurfaceBg  = Color(0xFFF2F5F3)
private val OnSurface  = Color(0xFF1A1A1A)
private val TextSub    = Color(0xFF6B7B6B)

data class ShopItem(
    val name: String,
    val address: String,
    val distance: String,
    val distanceKm: Double = 0.0,
    val location: GeoPoint,
    val type: ShopType,
    val rating: Double = 0.0,
    val openHours: String = "See location"
)

enum class ShopType { HOSPITAL, BIKE_SHOP }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopsScreen(
    paddingValues  : PaddingValues,
    shops          : List<ShopItem>  = emptyList(),
    isLoadingShops : Boolean         = false,
    fetchFailed    : Boolean         = false,
    onRetry        : () -> Unit      = {},
    onDirectionClick: (GeoPoint) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery    by remember { mutableStateOf("") }

    val filteredShops = remember(selectedFilter, searchQuery, shops) {
        shops.filter { shop ->
            val matchesFilter = when (selectedFilter) {
                "Hospitals"  -> shop.type == ShopType.HOSPITAL
                "Bike Shops" -> shop.type == ShopType.BIKE_SHOP
                else         -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    shop.name.contains(searchQuery, ignoreCase = true) ||
                    shop.address.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }.sortedBy { it.distanceKm }
    }

    val hospitalCount = shops.count { it.type == ShopType.HOSPITAL }
    val bikeShopCount = shops.count { it.type == ShopType.BIKE_SHOP }
    val closestHosp   = shops.filter { it.type == ShopType.HOSPITAL }.minByOrNull { it.distanceKm }
    val closestShop   = shops.filter { it.type == ShopType.BIKE_SHOP }.minByOrNull { it.distanceKm }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Nearby Services", fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp, color = Color.White, letterSpacing = 0.2.sp)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Green900)
            )
        },
        containerColor = SurfaceBg
    ) { innerPadding ->
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding      = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            // ── Hero search bar ───────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Green900, Green700), 0f, 220f))
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)
                ) {
                    OutlinedTextField(
                        value         = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder   = { Text("Search hospitals or bike shops…", fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f)) },
                        leadingIcon   = { Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(20.dp)) },
                        trailingIcon  = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, "Clear", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape      = RoundedCornerShape(16.dp),
                        colors     = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = Color.White.copy(alpha = 0.7f),
                            unfocusedBorderColor    = Color.White.copy(alpha = 0.25f),
                            focusedContainerColor   = Color.White.copy(alpha = 0.14f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.09f),
                            cursorColor             = Color.White,
                            focusedTextColor        = Color.White,
                            unfocusedTextColor      = Color.White
                        ),
                        modifier  = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                    )
                }
            }

            // ── Quick stat cards ──────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-18).dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Hospital card
                    Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(6.dp)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(RedLight), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.LocalHospital, null, tint = RedPrimary, modifier = Modifier.size(22.dp))
                                }
                                Column {
                                    if (isLoadingShops) CircularProgressIndicator(color = RedPrimary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    else Text("$hospitalCount", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = OnSurface)
                                    Text("Hospitals", fontSize = 11.sp, color = TextSub, fontWeight = FontWeight.Medium)
                                }
                            }
                            // Closest hospital hint
                            if (closestHosp != null && !isLoadingShops) {
                                HorizontalDivider(color = Color(0xFFF5F5F5))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.NearMe, null, tint = RedPrimary, modifier = Modifier.size(11.dp))
                                    Text("Nearest: ${closestHosp.distance}", fontSize = 11.sp,
                                        color = RedPrimary, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    // Bike shop card
                    Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(6.dp)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Green100), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Build, null, tint = Green700, modifier = Modifier.size(22.dp))
                                }
                                Column {
                                    if (isLoadingShops) CircularProgressIndicator(color = Green700, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    else Text("$bikeShopCount", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = OnSurface)
                                    Text("Bike Shops", fontSize = 11.sp, color = TextSub, fontWeight = FontWeight.Medium)
                                }
                            }
                            // Closest bike shop hint
                            if (closestShop != null && !isLoadingShops) {
                                HorizontalDivider(color = Color(0xFFF5F5F5))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.NearMe, null, tint = Green700, modifier = Modifier.size(11.dp))
                                    Text("Nearest: ${closestShop.distance}", fontSize = 11.sp,
                                        color = Green700, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            // ── Filter chips ──────────────────────────────────────────────────
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf(
                        Triple("All",        Icons.Default.Apps,                           Color(0xFF555555)),
                        Triple("Hospitals",  Icons.Default.LocalHospital,                  RedPrimary),
                        Triple("Bike Shops", Icons.Default.Build, Green700)
                    )
                    items(filters) { (label, icon, color) ->
                        val selected = selectedFilter == label
                        FilterChip(
                            selected    = selected,
                            onClick     = { selectedFilter = label },
                            label       = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp) },
                            leadingIcon = { Icon(icon, null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor   = if (label == "Hospitals") RedPrimary else Green900,
                                selectedLabelColor       = Color.White,
                                selectedLeadingIconColor = Color.White,
                                containerColor           = Color.White,
                                labelColor               = OnSurface,
                                iconColor                = color
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true, selected = selected,
                                borderColor = Color(0xFFE0E0E0), selectedBorderColor = Color.Transparent
                            ),
                            shape     = RoundedCornerShape(12.dp),
                            elevation = FilterChipDefaults.filterChipElevation(elevation = 2.dp, pressedElevation = 0.dp)
                        )
                    }
                }
            }

            // ── Section header ────────────────────────────────────────────────
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically) {
                    Text(
                        when (selectedFilter) { "Hospitals" -> "Hospitals"; "Bike Shops" -> "Bike Shops"; else -> "All Services" },
                        fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = OnSurface
                    )
                    if (isLoadingShops) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CircularProgressIndicator(color = Green900, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text("Scanning area…", fontSize = 12.sp, color = TextSub, fontWeight = FontWeight.Medium)
                        }
                    } else {
                        Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Green100).padding(horizontal = 12.dp, vertical = 5.dp)) {
                            Text("${filteredShops.size} found", fontSize = 12.sp, color = Green900, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // ── Loading state ─────────────────────────────────────────────────
            if (isLoadingShops && shops.isEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CircularProgressIndicator(color = Green900, modifier = Modifier.size(36.dp))
                        Text("Scanning your area…", fontSize = 14.sp, color = TextSub, fontWeight = FontWeight.Medium)
                        Text("Finding hospitals & bike shops within 3 km", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            // ── Empty / GPS not ready ─────────────────────────────────────────
            if (!isLoadingShops && shops.isEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.size(72.dp).clip(CircleShape)
                            .background(if (fetchFailed) Color(0xFFFFEBEE) else Color(0xFFF0F0F0)),
                            contentAlignment = Alignment.Center) {
                            Icon(if (fetchFailed) Icons.Default.WifiOff else Icons.Default.LocationSearching, null,
                                tint = if (fetchFailed) Color(0xFFD32F2F) else Color(0xFFBBBBBB), modifier = Modifier.size(38.dp))
                        }
                        Text(if (fetchFailed) "Could not load services" else "Waiting for GPS fix",
                            fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF555555))
                        Text(if (fetchFailed) "Check your connection and try again"
                        else "Move to the Map tab to enable location,\nthen return here",
                            fontSize = 13.sp, color = TextSub, textAlign = TextAlign.Center)
                        if (fetchFailed) {
                            Spacer(Modifier.height(4.dp))
                            Button(onClick = onRetry, shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Green900, contentColor = Color.White),
                                modifier = Modifier.height(46.dp)) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Retry", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // ── No search results ─────────────────────────────────────────────
            if (!isLoadingShops && shops.isNotEmpty() && filteredShops.isEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(Color(0xFFF0F0F0)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SearchOff, null, tint = Color(0xFFBBBBBB), modifier = Modifier.size(38.dp))
                        }
                        Text("Nothing found", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF555555))
                        Text(if (searchQuery.isNotEmpty()) "Try a different keyword" else "Try changing your filter",
                            fontSize = 13.sp, color = TextSub)
                    }
                }
            }

            // ── Shop cards ────────────────────────────────────────────────────
            items(filteredShops, key = { it.name + it.distanceKm }) { shop ->
                ShopCard(
                    shop             = shop,
                    onDirectionClick = onDirectionClick,
                    modifier         = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                )
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
fun ShopCard(
    shop             : ShopItem,
    onDirectionClick : (GeoPoint) -> Unit,
    modifier         : Modifier = Modifier
) {
    val isHospital      = shop.type == ShopType.HOSPITAL
    val primaryColor    = if (isHospital) RedPrimary else Green700
    val bgColor         = if (isHospital) RedLight   else Green100
    val typeLabel       = if (isHospital) "Hospital"  else "Bike Shop"
    val isOpen24        = shop.openHours.contains("24", ignoreCase = true)
    val hasRealAddress  = shop.address != "See map" && shop.address.isNotBlank()
    val hasRealHours    = shop.openHours != "See location" && shop.openHours.isNotBlank()

    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Coloured top accent bar ───────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: icon + name
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                            if (isHospital) {
                                Icon(Icons.Default.LocalHospital, null, tint = primaryColor, modifier = Modifier.size(26.dp))
                            } else {
                                Icon(Icons.Default.Build, null, tint = primaryColor, modifier = Modifier.size(26.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(shop.name, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                                lineHeight = 20.sp, color = OnSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(3.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(primaryColor).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                    Text(typeLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                if (isOpen24) {
                                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF2E7D32)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                        Text("24h", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                                if (shop.rating > 0) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFB800), modifier = Modifier.size(12.dp))
                                        Text(String.format("%.1f", shop.rating), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF666666))
                                    }
                                }
                            }
                        }
                    }
                    // Right: distance badge
                    Box(modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        .background(primaryColor).padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.NearMe, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.height(2.dp))
                            Text(shop.distance, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                }
            }

            // ── Info rows ─────────────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {

                if (hasRealAddress) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(SurfaceBg),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.LocationOn, null, tint = primaryColor.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                        }
                        Text(shop.address, fontSize = 13.sp, color = TextSub, lineHeight = 19.sp, modifier = Modifier.weight(1f))
                    }
                }

                if (hasRealHours) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(SurfaceBg),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AccessTime, null, tint = primaryColor.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                        }
                        Text(shop.openHours, fontSize = 13.sp, color = TextSub,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }

                // ── What to expect row (always shown) ────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(SurfaceBg),
                        contentAlignment = Alignment.Center) {
                        Icon(
                            if (isHospital) Icons.Default.HealthAndSafety else Icons.Default.Build,
                            null, tint = primaryColor.copy(alpha = 0.8f), modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        if (isHospital) "Emergency care • First aid • Medical services"
                        else "Bike repair • Parts • Accessories",
                        fontSize = 13.sp, color = TextSub, modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick   = { onDirectionClick(shop.location) },
                    modifier  = Modifier.fillMaxWidth().height(48.dp),
                    colors    = ButtonDefaults.buttonColors(
                        containerColor = primaryColor,
                        contentColor   = Color.White
                    ),
                    shape     = RoundedCornerShape(14.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Box(modifier = Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Directions, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Get Directions", color = Color.White,
                        fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, letterSpacing = 0.3.sp)
                }
            }
        }
    }
}