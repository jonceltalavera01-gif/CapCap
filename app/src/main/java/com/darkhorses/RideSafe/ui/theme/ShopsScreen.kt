package com.darkhorses.RideSafe.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.osmdroid.util.GeoPoint

data class ShopItem(
    val name: String,
    val address: String,
    val distance: String,
    val location: GeoPoint,
    val type: ShopType,
    val rating: Double = 4.5,
    val openHours: String = "9:00 AM - 7:00 PM"
)

enum class ShopType {
    HOSPITAL, BIKE_SHOP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopsScreen(
    paddingValues: PaddingValues,
    onDirectionClick: (GeoPoint) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("All") }
    var showFilterMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val shops = listOf(
        ShopItem("Manila Doctors Hospital", "667 United Nations Ave, Ermita, Manila", "1.2 km", GeoPoint(14.5818, 120.9822), ShopType.HOSPITAL, 4.7, "24 hours"),
        ShopItem("Philippine General Hospital", "Taft Ave, Ermita, Manila", "2.5 km", GeoPoint(14.5794, 120.9853), ShopType.HOSPITAL, 4.5, "24 hours"),
        ShopItem("Biker's Hub", "Quezon Ave, Quezon City", "5.0 km", GeoPoint(14.6200, 121.0200), ShopType.BIKE_SHOP, 4.3, "10:00 AM - 8:00 PM"),
        ShopItem("Cycle Art", "Quiapo, Manila", "0.8 km", GeoPoint(14.5985, 120.9825), ShopType.BIKE_SHOP, 4.8, "9:00 AM - 7:00 PM"),
        ShopItem("Cardinal Santos Medical Center", "Wilson St, San Juan", "7.2 km", GeoPoint(14.5997, 121.0444), ShopType.HOSPITAL, 4.6, "24 hours"),
        ShopItem("Ross Bike Shop", "Cartimar, Pasay City", "4.5 km", GeoPoint(14.5547, 120.9972), ShopType.BIKE_SHOP, 4.4, "9:30 AM - 6:30 PM"),
        ShopItem("St. Luke's Medical Center", "E. Rodriguez Sr. Ave, Quezon City", "6.8 km", GeoPoint(14.6239, 121.0346), ShopType.HOSPITAL, 4.8, "24 hours"),
        ShopItem("The Bike Shop", "BGC, Taguig", "8.2 km", GeoPoint(14.5511, 121.0446), ShopType.BIKE_SHOP, 4.6, "10:00 AM - 9:00 PM")
    )

    // Filter shops based on selected filter and search query
    val filteredShops = shops.filter { shop ->
        val matchesFilter = when (selectedFilter) {
            "Hospitals" -> shop.type == ShopType.HOSPITAL
            "Bike Shops" -> shop.type == ShopType.BIKE_SHOP
            else -> true
        }
        val matchesSearch = if (searchQuery.isNotEmpty()) {
            shop.name.contains(searchQuery, ignoreCase = true) ||
                    shop.address.contains(searchQuery, ignoreCase = true)
        } else true
        matchesFilter && matchesSearch
    }

    // Clear messages after a delay
    LaunchedEffect(successMessage, errorMessage) {
        if (successMessage != null || errorMessage != null) {
            kotlinx.coroutines.delay(3000)
            successMessage = null
            errorMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Services",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF333333)
                ),
                actions = {
                    // Filter button
                    IconButton(
                        onClick = {
                            try {
                                showFilterMenu = true
                            } catch (e: Exception) {
                                errorMessage = "Error opening filter: ${e.message}"
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                windowInsets = WindowInsets(0.dp)
            )
        },
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFAAB7AE))
        ) {
            // Filter Dropdown Menu
            DropdownMenu(
                expanded = showFilterMenu,
                onDismissRequest = { showFilterMenu = false },
                modifier = Modifier
                    .background(Color.White)
                    .width(200.dp)
            ) {
                listOf("All", "Hospitals", "Bike Shops").forEach { filter ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (filter) {
                                        "Hospitals" -> Icons.Default.LocalHospital
                                        "Bike Shops" -> Icons.Default.Store
                                        else -> Icons.Default.List
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = when (filter) {
                                        "Hospitals" -> Color.Red
                                        "Bike Shops" -> Color(0xFF506D45)
                                        else -> Color.Gray
                                    }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = filter,
                                    fontWeight = if (selectedFilter == filter) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        },
                        onClick = {
                            selectedFilter = filter
                            showFilterMenu = false
                            successMessage = "Filtered by: $filter"
                        }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Search Bar
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "Search shops or hospitals...",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.Gray
                                )
                            }
                        }
                    }
                }

                // Messages
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    // Success message
                    if (successMessage != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFE8F5E9)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = successMessage!!,
                                    color = Color(0xFF2E7D32),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // Error message
                    if (errorMessage != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFEBEE)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = errorMessage!!,
                                    color = Color(0xFFD32F2F),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Filter info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (selectedFilter) {
                            "Hospitals" -> "Hospitals (${filteredShops.size})"
                            "Bike Shops" -> "Bike Shops (${filteredShops.size})"
                            else -> "All Services (${filteredShops.size})"
                        },
                        fontSize = 14.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )

                    if (selectedFilter != "All") {
                        AssistChip(
                            onClick = {
                                selectedFilter = "All"
                                successMessage = "Filter cleared"
                            },
                            label = {
                                Text(
                                    text = "Clear",
                                    fontSize = 12.sp,
                                    color = Color(0xFF506D45)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color.White
                            )
                        )
                    }
                }

                // Shops list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    if (filteredShops.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White.copy(alpha = 0.95f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = Color(0xFF506D45),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "No services found",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF333333)
                                        )
                                        Text(
                                            text = if (searchQuery.isNotEmpty())
                                                "Try a different search term"
                                            else
                                                "Try changing your filter",
                                            fontSize = 14.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        items(filteredShops, key = { it.name }) { shop ->
                            ShopCard(
                                shop = shop,
                                onDirectionClick = onDirectionClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShopCard(
    shop: ShopItem,
    onDirectionClick: (GeoPoint) -> Unit
) {
    val isHospital = shop.type == ShopType.HOSPITAL
    val primaryColor = if (isHospital) Color.Red else Color(0xFF506D45)
    val backgroundColor = if (isHospital) Color(0xFFFFF0F0) else Color(0xFFF0F7F0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with icon and type
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon with background
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = backgroundColor,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isHospital)
                            Icons.Default.LocalHospital
                        else
                            Icons.Default.Store,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Name and type
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = shop.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        lineHeight = 22.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rating
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFFFFB800)
                            )
                            Text(
                                text = String.format("%.1f", shop.rating),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // Type badge
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = backgroundColor
                            ),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (isHospital) "Hospital" else "Bike Shop",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = primaryColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Address with icon
            Row(
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.Gray
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = shop.address,
                    fontSize = 14.sp,
                    color = Color.DarkGray,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Distance and hours row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Distance
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DirectionsBike,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = primaryColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = shop.distance,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryColor
                    )
                }

                // Hours
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = shop.openHours,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action button
            Button(
                onClick = { onDirectionClick(shop.location) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF506D45)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Directions,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Get Directions",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}