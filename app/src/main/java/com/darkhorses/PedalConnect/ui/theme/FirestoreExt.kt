package com.darkhorses.PedalConnect.ui.theme

import com.google.firebase.firestore.DocumentSnapshot

fun DocumentSnapshot.safePolyline(): List<Map<String, Double>> {
    val raw = get("polyline") as? List<*> ?: return emptyList()
    return raw.mapNotNull { entry ->
        val map = entry as? Map<*, *> ?: return@mapNotNull null
        val lat = map["lat"] as? Double ?: return@mapNotNull null
        val lon = map["lon"] as? Double ?: return@mapNotNull null
        mapOf("lat" to lat, "lon" to lon)
    }
}