package com.darkhorses.PedalConnect.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import androidx.core.app.NotificationCompat
import com.darkhorses.PedalConnect.MainActivity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.*
import kotlin.math.sqrt

class FallDetectionService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    private var accelerometer: Sensor? = null
    private var lastTriggerTime = 0L
    private var userName: String = ""
    
    private var freeFallDetected = false
    private var lastFreeFallTimestamp = 0L
    
    private var isAlertActive = false
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var countdownJob: Job? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val db = FirebaseFirestore.getInstance()

    companion object {
        const val SERVICE_CHANNEL_ID = "FallProtectionServiceChannel"
        const val SOS_CHANNEL_ID = "FallSosAlertChannel"
        const val NOTIFICATION_ID = 1001
        const val SOS_NOTIFICATION_ID = 1002
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_CANCEL_SOS = "ACTION_CANCEL_SOS"
        const val EXTRA_USER_NAME = "EXTRA_USER_NAME"
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ALWAYS call startForeground on API 26+ immediately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, 0)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }

        when (intent?.action) {
            ACTION_START -> {
                userName = intent.getStringExtra(EXTRA_USER_NAME) ?: "User"
                registerListeners()
            }
            ACTION_STOP -> {
                cancelCurrentSos()
                stopForeground(true)
                stopSelf()
            }
            ACTION_CANCEL_SOS -> {
                cancelCurrentSos()
            }
        }
        return START_STICKY
    }

    private fun cancelCurrentSos() {
        serviceScope.launch(Dispatchers.Main) {
            android.util.Log.d("FallDetectionService", "Cancelling current SOS and stopping alarm")
            isAlertActive = false
            countdownJob?.cancel()
            countdownJob = null
            stopVibration()
            stopAlarmSound()
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(SOS_NOTIFICATION_ID)
        }
    }

    private fun registerListeners() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || isAlertActive) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val acceleration = sqrt(x * x + y * y + z * z)
        val now = System.currentTimeMillis()

        // 1. Detect Potential Free-fall (acceleration < 3.0 m/s²)
        if (acceleration < 3.0f) {
            freeFallDetected = true
            lastFreeFallTimestamp = now
        }

        // 2. Detect High-G Impact (> 30.0 m/s²) shortly after free-fall
        // This confirms the phone actually hit something hard, not just moved down stairs.
        if (freeFallDetected && (now - lastFreeFallTimestamp < 1000L)) {
            if (acceleration > 30.0f) {
                if (now - lastTriggerTime > 30_000L) { // Debounce
                    lastTriggerTime = now
                    freeFallDetected = false // Reset state
                    startSosCountdown()
                }
            }
        }

        // 3. Reset free-fall state if no impact occurs within 1 second
        if (freeFallDetected && (now - lastFreeFallTimestamp > 1000L)) {
            freeFallDetected = false
        }
    }

    private fun startSosCountdown() {
        isAlertActive = true
        startAlarmSound()
        startRepeatingVibration()
        showPendingSosNotification()

        countdownJob = serviceScope.launch {
            delay(10_000L) // 10 second window to cancel
            if (isAlertActive) {
                triggerSos()
            }
        }
    }

    private fun triggerSos() {
        serviceScope.launch {
            try {
                if (!isAlertActive) return@launch

                // Get last known location
                var lastLoc: Location? = null
                try {
                    lastLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                } catch (e: SecurityException) { }

                val lat = lastLoc?.latitude ?: 0.0
                val lon = lastLoc?.longitude ?: 0.0

                if (!isAlertActive) return@launch

                val address: String = withContext(Dispatchers.IO) {
                    try {
                        val addresses = Geocoder(applicationContext, Locale.getDefault())
                            .getFromLocation(lat, lon, 1)
                        addresses?.firstOrNull()?.getAddressLine(0) ?: "Unknown Location"
                    } catch (e: Exception) { "Location Error" }
                }

                if (!isAlertActive) return@launch

                // Try to get display name from Firestore
                var displayName = userName
                try {
                    val snap = db.collection("users").whereEqualTo("username", userName).limit(1).get().await()
                    displayName = snap.documents.firstOrNull()?.getString("displayName") ?: userName
                } catch (e: Exception) { }

                if (!isAlertActive) return@launch

                val alertData = hashMapOf<String, Any>(
                    "riderName"         to userName,
                    "riderNameLower"    to userName.trim().lowercase(),
                    "riderDisplayName"  to displayName,
                    "emergencyType"     to "Urgent Help",
                    "latitude"          to lat,
                    "longitude"         to lon,
                    "locationName"      to address,
                    "timestamp"         to System.currentTimeMillis(),
                    "severity"          to "HIGH",
                    "status"            to "active",
                    "responderName"     to "",
                    "additionalDetails" to "🚨 Automatic Detection — The device detected a fall/drop."
                )

                db.collection("alerts").add(alertData).await()
                
                if (!isAlertActive) return@launch

                // Keep vibrating/sounding but change notification to "Sent"
                showSosSentNotification()
            } catch (e: Exception) {
                android.util.Log.e("FallDetectionService", "Error triggering SOS", e)
                if (isAlertActive) cancelCurrentSos()
            }
        }
    }

    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun getCancelIntent(): PendingIntent {
        val intent = Intent(this, FallDetectionService::class.java).apply {
            action = ACTION_CANCEL_SOS
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getService(this, 1, intent, flags)
    }

    private fun showPendingSosNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, SOS_CHANNEL_ID)
            .setContentTitle("🚨 FALL DETECTED!")
            .setContentText("SOS will be sent in 10 seconds. Tap to cancel.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(getPendingIntent(), true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "CANCEL SOS", getCancelIntent())
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
        nm.notify(SOS_NOTIFICATION_ID, notification)
    }

    private fun showSosSentNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, SOS_CHANNEL_ID)
            .setContentTitle("🚨 SOS SIGNAL SENT")
            .setContentText("Emergency signal has been broadcasted. Tap to stop alarm.")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP ALARM", getCancelIntent())
            .setContentIntent(getPendingIntent())
            .setOngoing(true)
            .build()
        nm.notify(SOS_NOTIFICATION_ID, notification)
    }

    private fun startAlarmSound() {
        serviceScope.launch(Dispatchers.Main) {
            try {
                if (mediaPlayer == null) {
                    val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(applicationContext, alarmUri)
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        isLooping = true
                        prepare()
                        start()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FallDetectionService", "Error playing alarm", e)
            }
        }
    }

    private fun stopAlarmSound() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (e: Exception) {}
            it.release()
        }
        mediaPlayer = null
    }

    private fun startRepeatingVibration() {
        serviceScope.launch(Dispatchers.Main) {
            if (vibrator == null) {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vm.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
            }

            val pattern = longArrayOf(0, 800, 200, 800, 200)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    private fun stopVibration() {
        vibrator?.cancel()
        vibrator = null
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle("PedalConnect Fall Protection")
            .setContentText("Monitoring for falls in the background...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(getPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            
            // 1. Service Monitoring Channel (Quiet)
            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Fall Protection Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows that the app is monitoring for falls."
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            manager?.createNotificationChannel(serviceChannel)

            // 2. SOS Alert Channel (High Priority)
            val alertChannel = NotificationChannel(
                SOS_CHANNEL_ID,
                "Fall SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts when a fall is detected."
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 200, 800, 200)
                enableLights(true)
                lightColor = android.graphics.Color.RED
                setSound(null, null) // Sound is handled by MediaPlayer for loop
            }
            manager?.createNotificationChannel(alertChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        serviceScope.cancel()
        super.onDestroy()
    }
}
