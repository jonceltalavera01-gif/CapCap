package com.darkhorses.capcap.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    val type: ShopType
)

enum class ShopType {
    HOSPITAL, BIKE_SHOP
}

@Composable
fun ShopsScreen(paddingValues: PaddingValues, onDirectionClick: (GeoPoint) -> Unit) {
    val shops = listOf(
        ShopItem("Manila Doctors Hospital", "667 United Nations Ave, Ermita, Manila", "1.2 km", GeoPoint(14.5818, 120.9822), ShopType.HOSPITAL),
        ShopItem("Philippine General Hospital", "Taft Ave, Ermita, Manila", "2.5 km", GeoPoint(14.5794, 120.9853), ShopType.HOSPITAL),
        ShopItem("Biker's Hub", "Quezon Ave, Quezon City", "5.0 km", GeoPoint(14.6200, 121.0200), ShopType.BIKE_SHOP),
        ShopItem("Cycle Art", "Quiapo, Manila", "0.8 km", GeoPoint(14.5985, 120.9825), ShopType.BIKE_SHOP),
        ShopItem("Cardinal Santos Medical Center", "Wilson St, San Juan", "7.2 km", GeoPoint(14.5997, 121.0444), ShopType.HOSPITAL),
        ShopItem("Ross Bike Shop", "Cartimar, Pasay City", "4.5 km", GeoPoint(14.5547, 120.9972), ShopType.BIKE_SHOP)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFAAB7AE))
            .padding(paddingValues)
            .padding(16.dp)
    ) {
        Text(
            text = "Nearby Services",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(shops) { shop ->
                ShopCard(shop, onDirectionClick)
            }
        }
    }
}

@Composable
fun ShopCard(shop: ShopItem, onDirectionClick: (GeoPoint) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (shop.type == ShopType.HOSPITAL) Icons.Default.LocalHospital else Icons.Default.Store,
                contentDescription = null,
                tint = if (shop.type == ShopType.HOSPITAL) Color.Red else Color(0xFF506D45),
                modifier = Modifier.size(40.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(text = shop.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(text = shop.address, fontSize = 14.sp, color = Color.Gray)
                Text(text = "Distance: ${shop.distance}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
            }
            
            IconButton(
                onClick = { onDirectionClick(shop.location) },
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF506D45), contentColor = Color.White)
            ) {
                Icon(Icons.Default.Directions, contentDescription = "Directions")
            }
        }
    }
}
