package com.darkhorses.PedalConnect.ui.theme

import com.google.firebase.firestore.DocumentSnapshot

@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.safePolyline(): List<Map<String, Double>> {
    return try {
        val raw = get("polyline") as? List<*> ?: return emptyList()
        raw.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val lat = (map["lat"] as? Number)?.toDouble() ?: return@mapNotNull null
            val lon = (map["lon"] as? Number)?.toDouble() ?: return@mapNotNull null
            mapOf("lat" to lat, "lon" to lon)
        }
    } catch (e: Exception) {
        emptyList()
    }
}