package com.darkhorses.PedalConnect.utils

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.math.*

object NotificationHelper {

    private const val SOS_RADIUS_KM = 3.0
    private const val STALE_THRESHOLD_MS = 300_000L // 5 minutes

    /**
     * Broadcasts an SOS notification to all cyclists within 3km who have been active
     * in the last 5 minutes.
     */
    suspend fun notifyNearbyCyclists(
        db: FirebaseFirestore,
        senderUserName: String,
        senderDisplayName: String,
        latitude: Double,
        longitude: Double,
        alertId: String
    ) {
        try {
            val now = System.currentTimeMillis()
            val locationSnap = db.collection("userLocations").get().await()
            
            val nearbyUsers = locationSnap.documents.mapNotNull { doc ->
                val userName = doc.getString("userName") ?: return@mapNotNull null
                val userId = doc.getString("userId") ?: return@mapNotNull null
                
                // Don't notify self
                if (userName.equals(senderUserName, ignoreCase = true)) return@mapNotNull null
                
                val ts = doc.getLong("timestamp") ?: 0L
                if (now - ts > STALE_THRESHOLD_MS) return@mapNotNull null
                
                val lat = doc.getDouble("latitude") ?: return@mapNotNull null
                val lon = doc.getDouble("longitude") ?: return@mapNotNull null
                
                val distance = haversineKm(latitude, longitude, lat, lon)
                if (distance <= SOS_RADIUS_KM) {
                    userId
                } else {
                    null
                }
            }.distinct()

            if (nearbyUsers.isEmpty()) {
                Log.d("NotificationHelper", "No nearby cyclists found to notify.")
                return
            }

            Log.d("NotificationHelper", "Notifying ${nearbyUsers.size} nearby cyclists.")

            // Create notification entries for each nearby user
            val batch = db.batch()
            nearbyUsers.forEach { targetUid ->
                val notifRef = db.collection("notifications").document()
                val data = hashMapOf(
                    "toId" to targetUid,
                    "userName" to senderDisplayName,
                    "message" to "🚨 $senderDisplayName needs help! SOS alert nearby.",
                    "type" to "alert",
                    "timestamp" to now,
                    "read" to false,
                    "alertId" to alertId
                )
                batch.set(notifRef, data)
            }
            batch.commit().await()

        } catch (e: Exception) {
            Log.e("NotificationHelper", "Failed to broadcast SOS notification", e)
        }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
