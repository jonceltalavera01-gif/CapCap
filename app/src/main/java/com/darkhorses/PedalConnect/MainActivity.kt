package com.darkhorses.PedalConnect

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.darkhorses.PedalConnect.ui.theme.AppNavigator
import com.darkhorses.PedalConnect.ui.theme.CapCapTheme
import com.darkhorses.PedalConnect.utils.CloudinaryHelper
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.Intent
import com.darkhorses.PedalConnect.services.FirestoreNotificationService
import com.darkhorses.PedalConnect.services.FallDetectionService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : FragmentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
        } else {
            // Permission denied
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CloudinaryHelper.init(this)
        askNotificationPermission()
        startNotificationService()
        enableEdgeToEdge()
        setContent {
            CapCapTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavigator(paddingValues = innerPadding)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        resumeFallDetectionIfEnabled()
    }

    override fun onStop() {
        super.onStop()
        stopFallDetection()
    }

    private fun resumeFallDetectionIfEnabled() {
        val prefs = getSharedPreferences("FallDetectionPrefs", MODE_PRIVATE)
        val enabled = prefs.getBoolean("enabled", false)
        if (enabled) {
            val userName = prefs.getString("user_name", "User") ?: "User"
            val intent = Intent(this, FallDetectionService::class.java).apply {
                action = FallDetectionService.ACTION_START
                putExtra(FallDetectionService.EXTRA_USER_NAME, userName)
            }
            startService(intent)
        }
    }

    private fun stopFallDetection() {
        stopService(Intent(this, FallDetectionService::class.java))
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startNotificationService() {
        val email = FirebaseAuth.getInstance().currentUser?.email
        if (email.isNullOrBlank()) {
            // Not logged in yet — nothing to gate against, default to starting.
            // (LoginScreen/session flow will re-trigger this after auth if needed.)
            launchNotificationServiceStart()
            return
        }

        lifecycleScope.launch {
            val notificationsEnabled = try {
                val snap = FirebaseFirestore.getInstance().collection("users")
                    .whereEqualTo("email", email)
                    .limit(1).get().await()
                val prefs = snap.documents.firstOrNull()?.get("settings") as? Map<*, *>
                prefs?.get("notificationsEnabled") as? Boolean ?: true
            } catch (e: Exception) {
                // Fail open — don't silently break notifications on a transient read error
                true
            }

            if (notificationsEnabled) {
                launchNotificationServiceStart()
            }
        }
    }

    private fun launchNotificationServiceStart() {
        val intent = Intent(this, FirestoreNotificationService::class.java).apply {
            action = FirestoreNotificationService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}


