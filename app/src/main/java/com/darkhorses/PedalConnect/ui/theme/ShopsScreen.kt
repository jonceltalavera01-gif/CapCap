package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.osmdroid.util.GeoPoint

// ── Design tokens ─────────────────────────────────────────────────────────────
private val S_Green900   = Color(0xFF06402B)
private val S_Green800   = Color(0xFF0A5C3D)
private val S_Green700   = Color(0xFF0D7050)
private val S_Green100   = Color(0xFFDDF1E8)
private val S_Green50    = Color(0xFFF0FAF5)
private val S_Red600     = Color(0xFFDC2626)
private val S_Red50      = Color(0xFFFEF2F2)
private val S_Red100     = Color(0xFFFFE4E4)
private val S_Canvas     = Color(0xFFF5F7F6)
private val S_Surface    = Color(0xFFFFFFFF)
private val S_TextPrimary   = Color(0xFF111827)
private val S_TextSecondary = Color(0xFF374151)
private val S_TextMuted     = Color(0xFF6B7280)
private val S_Divider       = Color(0xFFE5E7EB)
private val S_Border        = Color(0xFFD1D5DB)

// ── Data models ───────────────────────────────────────────────────────────────
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

// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopsScreen(
    paddingValues   : PaddingValues,
    shops           : List<ShopItem>     = emptyList(),
    isLoadingShops  : Boolean            = false,
    fetchFailed     : Boolean            = false,
    onRetry         : () -> Unit         = {},
    onDirectionClick: (GeoPoint) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery    by remember { mutableStateOf("") }

    val filteredShops = remember(selectedFilter, searchQuery, shops) {
        shops.filter { shop ->
            val matchesFilter = when (selectedFilter) {
                "Hospitals"    -> shop.type == ShopType.HOSPITAL
                "Repair Shops" -> shop.type == ShopType.BIKE_SHOP
                else           -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    shop.name.contains(searchQuery, ignoreCase = true) ||
                    shop.address.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }.sortedBy { it.distanceKm }
    }

    val hospitalCount = shops.count { it.type == ShopType.HOSPITAL }
    val bikeShopCount = shops.count { it.type == ShopType.BIKE_SHOP }
    val allCount      = shops.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalPharmacy, null,
                            tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Nearby Services",
                            fontWeight    = FontWeight.ExtraBold,
                            fontSize      = 20.sp,
                            color         = Color.White,
                            letterSpacing = 0.3.sp
                        )
                    }
                },
                colors   = TopAppBarDefaults.topAppBarColors(containerColor = S_Green900),
                modifier = Modifier
            )
        },
        containerColor = S_Canvas
    ) { innerPadding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = paddingValues.calculateBottomPadding() + 32.dp)
        ) {

            // ── Hero search bar ───────────────────────────────────────────────
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(S_Green900, S_Green800))
                        )
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp)
                ) {
                    OutlinedTextField(
                        value         = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder   = {
                            Text(
                                "Search services here…",
                                fontSize = 14.sp,
                                color    = Color.White.copy(alpha = 0.5f)
                            )
                        },
                        leadingIcon  = {
                            Icon(Icons.Default.Search, null,
                                tint     = Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, "Clear",
                                        tint     = Color.White.copy(alpha = 0.65f),
                                        modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape      = RoundedCornerShape(14.dp),
                        colors     = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = Color.White.copy(alpha = 0.6f),
                            unfocusedBorderColor    = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor   = Color.White.copy(alpha = 0.12f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                            cursorColor             = Color.White,
                            focusedTextColor        = Color.White,
                            unfocusedTextColor      = Color.White
                        ),
                        modifier  = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                    )
                }
            }

            // ── Filter chips with counts ──────────────────────────────────────
            item {
                LazyRow(
                    modifier       = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    data class ChipDef(
                        val label: String,
                        val count: Int,
                        val icon: ImageVector,
                        val activeColor: Color
                    )
                    val chips = listOf(
                        ChipDef("All",          allCount,      Icons.Default.Apps,           S_Green900),
                        ChipDef("Hospitals",    hospitalCount, Icons.Default.LocalHospital,  S_Red600),
                        ChipDef("Repair Shops", bikeShopCount, Icons.Default.Build,          S_Green700)
                    )
                    items(chips) { chip ->
                        val isSelected = selectedFilter == chip.label
                        FilterChip(
                            selected = isSelected,
                            onClick  = { selectedFilter = chip.label },
                            label    = {
                                Text(
                                    "${chip.label} (${chip.count})",
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize   = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(chip.icon, null, modifier = Modifier.size(15.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor   = chip.activeColor,
                                selectedLabelColor       = Color.White,
                                selectedLeadingIconColor = Color.White,
                                containerColor           = S_Surface,
                                labelColor               = S_TextSecondary,
                                iconColor                = chip.activeColor
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled             = true,
                                selected            = isSelected,
                                borderColor         = S_Border,
                                selectedBorderColor = Color.Transparent,
                                borderWidth         = 1.dp,
                                selectedBorderWidth = 0.dp
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }

            // ── Loading state — shimmer cards ─────────────────────────────────
            if (isLoadingShops && shops.isEmpty()) {
                items(4) {
                    ShopShimmerCard(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                    )
                }
            }

            // ── Error state ───────────────────────────────────────────────────
            if (!isLoadingShops && fetchFailed && shops.isEmpty()) {
                item {
                    ServiceEmptyState(
                        icon    = Icons.Default.WifiOff,
                        iconBg  = S_Red50,
                        iconTint = S_Red600,
                        title   = "Could not load services",
                        message = "Check your connection and try again.",
                        action  = {
                            Button(
                                onClick = onRetry,
                                shape   = RoundedCornerShape(12.dp),
                                colors  = ButtonDefaults.buttonColors(
                                    containerColor = S_Green900,
                                    contentColor   = Color.White),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Retry", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    )
                }
            }

            // ── GPS not ready ─────────────────────────────────────────────────
            if (!isLoadingShops && !fetchFailed && shops.isEmpty()) {
                item {
                    ServiceEmptyState(
                        icon     = Icons.Default.LocationSearching,
                        iconBg   = Color(0xFFF3F4F6),
                        iconTint = S_TextMuted,
                        title    = "Waiting for GPS fix",
                        message  = "Go to the Map tab to enable location, then return here."
                    )
                }
            }

            // ── No search results ─────────────────────────────────────────────
            if (!isLoadingShops && shops.isNotEmpty() && filteredShops.isEmpty()) {
                item {
                    ServiceEmptyState(
                        icon     = Icons.Default.SearchOff,
                        iconBg   = Color(0xFFF3F4F6),
                        iconTint = S_TextMuted,
                        title    = "Nothing found",
                        message  = if (searchQuery.isNotEmpty())
                            "Try a different keyword."
                        else
                            "Try changing your filter."
                    )
                }
            }

            // ── Result count label ────────────────────────────────────────────
            if (!isLoadingShops && filteredShops.isNotEmpty()) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            when (selectedFilter) {
                                "Hospitals"    -> "Hospitals nearby"
                                "Repair Shops" -> "Repair shops nearby"
                                else           -> "All services nearby"
                            },
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = S_TextPrimary
                        )
                        Text(
                            "${filteredShops.size} found",
                            fontSize = 12.sp,
                            color    = S_TextMuted
                        )
                    }
                    Spacer(Modifier.height(4.dp))
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

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ── Shop card — clean, scannable, action-forward ──────────────────────────────
@Composable
fun ShopCard(
    shop            : ShopItem,
    onDirectionClick: (GeoPoint) -> Unit,
    modifier        : Modifier = Modifier
) {
    val isHospital   = shop.type == ShopType.HOSPITAL
    val accentColor  = if (isHospital) S_Red600  else S_Green700
    val accentBg     = if (isHospital) S_Red50   else S_Green50
    val accentBg2    = if (isHospital) S_Red100  else S_Green100
    val typeLabel    = if (isHospital) "Hospital" else "Repair Shop"
    val typeIcon     = if (isHospital) Icons.Default.LocalHospital else Icons.Default.Build
    val isOpen24     = shop.openHours.contains("24", ignoreCase = true)
    val hasAddress   = shop.address != "See map" && shop.address.isNotBlank()
    val hasHours     = shop.openHours != "See location" && shop.openHours.isNotBlank()

    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = S_Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {

            // ── Left accent bar + header ──────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                // Left color bar
                Box(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(
                            accentColor,
                            RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                        )
                )

                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    // Name + distance row
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment     = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier              = Modifier.weight(1f)
                        ) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accentBg),
                                Alignment.Center
                            ) {
                                Icon(typeIcon, null, tint = accentColor, modifier = Modifier.size(20.dp))
                            }
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    shop.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize   = 15.sp,
                                    color      = S_TextPrimary,
                                    maxLines   = 2,
                                    overflow   = TextOverflow.Ellipsis,
                                    lineHeight = 20.sp
                                )
                                // Type + open badges
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(accentBg2)
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            typeLabel,
                                            fontSize   = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color      = accentColor
                                        )
                                    }
                                    if (isOpen24) {
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFDCFCE7))
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "24h",
                                                fontSize   = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color      = Color(0xFF166534)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Distance badge
                        Spacer(Modifier.width(8.dp))
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier            = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(accentBg)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.NearMe, null,
                                tint     = accentColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                shop.distance,
                                fontSize   = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color      = accentColor
                            )
                        }
                    }

                    // ── Address ───────────────────────────────────────────────
                    if (hasAddress) {
                        Row(
                            verticalAlignment     = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.LocationOn, null,
                                tint     = S_TextMuted,
                                modifier = Modifier.size(14.dp).padding(top = 1.dp)
                            )
                            Text(
                                shop.address,
                                fontSize   = 13.sp,
                                color      = S_TextSecondary,
                                lineHeight = 18.sp,
                                maxLines   = 2,
                                overflow   = TextOverflow.Ellipsis,
                                modifier   = Modifier.weight(1f)
                            )
                        }
                    }

                    // ── Hours ─────────────────────────────────────────────────
                    if (hasHours) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.AccessTime, null,
                                tint     = S_TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                shop.openHours,
                                fontSize = 13.sp,
                                color    = S_TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // ── Get Directions button ─────────────────────────────────
                    Button(
                        onClick  = { onDirectionClick(shop.location) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor   = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Icon(Icons.Default.Directions, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Get Directions",
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 14.sp
                        )
                    }
                }
            }
        }
    }
}

// ── Shimmer loading card ──────────────────────────────────────────────────────
@Composable
private fun ShopShimmerCard(modifier: Modifier = Modifier) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 0.7f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation  = androidx.compose.animation.core.tween(900),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    val shimmer = Color(0xFFE0E0E0).copy(alpha = shimmerAlpha)

    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = S_Surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(shimmer,
                RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)))
            Column(
                Modifier.weight(1f).padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(shimmer))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.width(160.dp).height(13.dp).clip(RoundedCornerShape(6.dp)).background(shimmer))
                        Box(Modifier.width(80.dp).height(10.dp).clip(RoundedCornerShape(6.dp)).background(shimmer))
                    }
                }
                Box(Modifier.fillMaxWidth(0.85f).height(10.dp).clip(RoundedCornerShape(6.dp)).background(shimmer))
                Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(shimmer))
            }
        }
    }
}

// ── Empty / error state ───────────────────────────────────────────────────────
@Composable
private fun ServiceEmptyState(
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    iconBg  : Color,
    iconTint: Color,
    title   : String,
    message : String,
    action  : (@Composable () -> Unit)? = null
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(iconBg),
            Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(34.dp))
        }
        Text(
            title,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 16.sp,
            color      = S_TextPrimary,
            textAlign  = TextAlign.Center
        )
        Text(
            message,
            fontSize   = 13.sp,
            color      = S_TextMuted,
            textAlign  = TextAlign.Center,
            lineHeight = 20.sp
        )
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}
