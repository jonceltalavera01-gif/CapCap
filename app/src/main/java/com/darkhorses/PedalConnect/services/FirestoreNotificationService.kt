package com.darkhorses.PedalConnect.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.darkhorses.PedalConnect.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class FirestoreNotificationService : Service() {

    private var listenerRegistration: ListenerRegistration? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        const val CHANNEL_ID = "FirestoreNotificationChannel"
        const val ALERT_CHANNEL_ID = "PedalConnectAlertChannel"
        const val NOTIFICATION_ID = 2001
        const val SYSTEM_NOTIFICATION_ID = 3001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure notification channels exist
        createNotificationChannels()

        // Start as foreground service to keep it alive
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PedalConnect Background Sync")
            .setContentText("Listening for real-time updates...")
            .setSmallIcon(com.darkhorses.PedalConnect.R.drawable.ic_stat_notification)
            .setColor(android.graphics.Color.parseColor("#06402B"))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("NotificationService", "Failed to start foreground service", e)
        }

        // Listen for auth state changes to start/stop listening to Firestore
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                Log.d("NotificationService", "User logged in: ${user.uid}, starting listener")
                startListening(user.uid)
            } else {
                Log.d("NotificationService", "User logged out, stopping listener")
                listenerRegistration?.remove()
                listenerRegistration = null
            }
        }

        return START_STICKY
    }

    private fun startListening(uid: String) {
        // Listen for ANY new notification where toId == uid
        listenerRegistration?.remove()

        Log.d("NotificationService", "Setting up Firestore listener for UID: $uid")

        listenerRegistration = db.collection("notifications")
            .whereEqualTo("toId", uid)
            .whereEqualTo("read", false)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("NotificationService", "Firestore listen failed: ${e.message}", e)
                    return@addSnapshotListener
                }

                if (snapshots == null) {
                    Log.d("NotificationService", "Snapshots is null")
                    return@addSnapshotListener
                }

                Log.d("NotificationService", "Snapshot received with ${snapshots.documentChanges.size} changes")

                for (dc in snapshots.documentChanges) {
                    if (dc.type == DocumentChange.Type.ADDED) {
                        val doc = dc.document
                        val message = doc.getString("message") ?: "New notification"
                        val type = doc.getString("type") ?: "info"
                        val userName = doc.getString("userName") ?: "Someone"
                        val timestamp = doc.getLong("timestamp") ?: 0L

                        Log.d("NotificationService", "New notification added: $message (type: $type, ts: $timestamp, user: $userName)")

                        // Ignore notifications older than 10 minutes from now
                        if (Math.abs(System.currentTimeMillis() - timestamp) < 600_000) {
                            if (!isAppInForeground()) {
                                showSystemNotification(message, type, userName)
                            } else {
                                Log.d("NotificationService", "App is in foreground, skipping system notification.")
                            }

                            // If it's a message, mark as read immediately so it doesn't
                            // show up in any in-app notification lists/counts.
                            if (type == "message") {
                                doc.reference.update("read", true)
                            }
                        } else {
                            Log.d("NotificationService", "Skipping old notification. Diff: ${System.currentTimeMillis() - timestamp}")
                        }
                    }
                }
            }
    }

    private fun isAppInForeground(): Boolean {
        val appProcessInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(appProcessInfo)
        return appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private fun showSystemNotification(message: String, type: String, senderName: String) {
        val title = when(type) {
            "like" -> "Post Interaction"
            "comment" -> "New Comment"
            "reply" -> "New Reply"
            "alert" -> "🚨 Emergency Alert!"
            "moderation" -> "Account Notice"
            "message" -> senderName
            else -> "PedalConnect Update"
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(com.darkhorses.PedalConnect.R.drawable.ic_stat_notification)
            .setColor(android.graphics.Color.parseColor("#06402B"))
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // 1. Service Monitoring Channel (Low priority, quiet)
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Background Sync Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows that PedalConnect is listening for updates in the background."
                setShowBadge(false)
            }
            manager?.createNotificationChannel(serviceChannel)

            // 2. Real-time Alert Channel (High priority, makes noise)
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Real-time Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts you when someone interacts with your posts or when there's an emergency."
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager?.createNotificationChannel(alertChannel)
        }
    }

    override fun onDestroy() {
        listenerRegistration?.remove()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
