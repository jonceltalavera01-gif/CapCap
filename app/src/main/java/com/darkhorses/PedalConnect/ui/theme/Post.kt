package com.darkhorses.PedalConnect.ui.theme

data class Post(
    val id: String                          = "",
    val userName: String                    = "",
    val displayName: String                 = "",
    val description: String                 = "",
    val activity: String                    = "",
    val distance: String                    = "",
    val timestamp: Long                     = 0L,
    val likes: Int                          = 0,
    val comments: Int                       = 0,
    val likedBy: List<String>               = emptyList(),
    val status: String                      = "",
    val imageUrl: String                    = "",
    val imageDeleteUrl: String              = "",
    val polyline: List<Map<String, Double>> = emptyList(),
    val routeImageUrl: String               = "",
    val editedAt: Long                      = 0L,
    val rideStats: Map<String, Any>?        = null
)