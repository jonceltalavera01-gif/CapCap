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
    private var usernameListenerRegistration: ListenerRegistration? = null
    private var settingsListenerRegistration: ListenerRegistration? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    @Volatile private var currentUsername: String? = null

    // Cached in-memory from a realtime Firestore listener so we don't need an
    // async read on every incoming notification. Defaults to true (matches
    // the default used in SettingsScreen) until the first snapshot arrives.
    @Volatile private var notificationsEnabled = true

    companion object {
        const val CHANNEL_ID = "FirestoreNotificationChannel"
        const val ALERT_CHANNEL_ID = "PedalConnectAlertChannel"
        const val NOTIFICATION_ID = 2001
        const val SYSTEM_NOTIFICATION_ID = 3001
        const val ACTION_START = "com.darkhorses.PedalConnect.action.START_NOTIFICATIONS"
        const val ACTION_STOP = "com.darkhorses.PedalConnect.action.STOP_NOTIFICATIONS"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.d("NotificationService", "Stop requested — user disabled in-app notifications")
            listenerRegistration?.remove()
            listenerRegistration = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

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
                startListeningToNotificationSetting(user.email)
            } else {
                Log.d("NotificationService", "User logged out, stopping listener")
                listenerRegistration?.remove()
                listenerRegistration = null
                usernameListenerRegistration?.remove()
                usernameListenerRegistration = null
                settingsListenerRegistration?.remove()
                settingsListenerRegistration = null
                notificationsEnabled = true
                currentUsername = null
            }
        }

        return START_STICKY
    }

    private fun startListening(uid: String) {
        // Primary listener: query by toId (standard UID targeting)
        listenerRegistration?.remove()
        Log.d("NotificationService", "Setting up primary Firestore listener for UID: $uid")

        listenerRegistration = db.collection("notifications")
            .whereEqualTo("toId", uid)
            .whereEqualTo("read", false)
            .addSnapshotListener { snapshots, e ->
                handleNotificationChanges(snapshots, e)
            }
    }

    private fun startListeningByUsername(username: String) {
        // Fallback listener: query by userName (legacy/buggy targeting where username is used as ID)
        usernameListenerRegistration?.remove()
        Log.d("NotificationService", "Setting up fallback Firestore listener for username: $username")

        usernameListenerRegistration = db.collection("notifications")
            .whereEqualTo("userName", username)
            .whereEqualTo("read", false)
            .addSnapshotListener { snapshots, e ->
                handleNotificationChanges(snapshots, e)
            }
    }

    private fun handleNotificationChanges(snapshots: com.google.firebase.firestore.QuerySnapshot?, e: com.google.firebase.firestore.FirebaseFirestoreException?) {
        if (e != null) {
            Log.w("NotificationService", "Firestore listen failed: ${e.message}", e)
            return
        }
        if (snapshots == null) return

        for (dc in snapshots.documentChanges) {
            if (dc.type == DocumentChange.Type.ADDED) {
                val doc = dc.document

                // If this notification only has userName but no toId, it's a "buggy" 
                // notification targeted at this user. If it HAS a toId, we only 
                // process it if it matches our UID (to avoid duplicates if both 
                // listeners pick up the same document, though they shouldn't 
                // if the schema is followed).
                val toId = doc.getString("toId")
                if (toId != null && toId != auth.currentUser?.uid) continue

                val message = doc.getString("message") ?: "New notification"
                val type = doc.getString("type") ?: "info"
                val timestamp = doc.getLong("timestamp") ?: 0L

                // If toId is null, it's a fallback notification where userName was used as recipient.
                // In this case, the actor is likely "Admin".
                val actorName = if (toId == null) "Admin" else (doc.getString("userName") ?: "Someone")

                // Ignore notifications older than 10 minutes from now
                if (Math.abs(System.currentTimeMillis() - timestamp) < 600_000) {
                    if (!notificationsEnabled) {
                        Log.d("NotificationService", "Notifications disabled by user, skipping system notification.")
                    } else if (!isAppInForeground() || type == "alert") {
                        showSystemNotification(message, type, actorName)
                    }

                    // Auto-read messages/alerts to prevent repeat notifications
                    if (type == "message" || type == "alert") {
                        doc.reference.update("read", true)
                    }
                }
            }
        }
    }

    /**
     * Keeps [notificationsEnabled] in sync in real time with the user's
     * "In-App Notifications" toggle in Settings, so a change takes effect
     * immediately without restarting this service.
     */
    private fun startListeningToNotificationSetting(email: String?) {
        settingsListenerRegistration?.remove()
        if (email.isNullOrBlank()) return

        settingsListenerRegistration = db.collection("users")
            .whereEqualTo("email", email)
            .limit(1)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("NotificationService", "Settings listen failed: ${e.message}", e)
                    return@addSnapshotListener
                }
                val doc = snapshots?.documents?.firstOrNull() ?: return@addSnapshotListener
                val prefs = doc.get("settings") as? Map<*, *>
                notificationsEnabled = prefs?.get("notificationsEnabled") as? Boolean ?: true

                val username = doc.getString("username")
                if (!username.isNullOrBlank() && username != currentUsername) {
                    currentUsername = username
                    startListeningByUsername(username)
                }

                Log.d("NotificationService", "notificationsEnabled updated: $notificationsEnabled, username: $currentUsername")
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
        settingsListenerRegistration?.remove()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
