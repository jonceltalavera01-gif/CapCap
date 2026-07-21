package com.darkhorses.PedalConnect.ui.theme

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.darkhorses.PedalConnect.services.FallDetectionService
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.osmdroid.util.GeoPoint
import java.util.Locale

// ── Colour tokens (local to this file) ───────────────────────────────────────
private val SosRed      = Color(0xFFD32F2F)
private val SosRedLight = Color(0xFFFFEBEE)
private val SosGreen    = Color(0xFF388E3C)

// ── SOS type model ────────────────────────────────────────────────────────────
data class SosType(val label: String, val icon: ImageVector, val severity: String)

val sosRow1 = listOf(
    SosType("Accident",         Icons.Default.CarCrash,                 "HIGH"),
    SosType("Medical Help",     Icons.Default.LocalHospital,             "HIGH"),
    SosType("Mechanical Issue", Icons.Default.Build,                     "MEDIUM"),
    SosType("Road Hazard",      Icons.Default.Construction,              "MEDIUM")
)
val sosRow2 = listOf(
    SosType("Stranded",    Icons.Default.LocationOff,                    "MEDIUM"),
    SosType("Flat Tire",   Icons.AutoMirrored.Filled.DirectionsBike,     "LOW"),
    SosType("Unsafe Area", Icons.Default.GppBad,                         "HIGH"),
    SosType("Other",       Icons.Default.HelpOutline,                    "LOW")
)

fun sosSeverityColor(s: String) = when (s) {
    "HIGH"   -> Color(0xFFD32F2F)
    "MEDIUM" -> Color(0xFFF57C00)
    else     -> Color(0xFF388E3C)
}

// ── SOS grid tile ─────────────────────────────────────────────────────────────
@Composable
fun SosGridTile(sosType: SosType, isSelected: Boolean, onClick: () -> Unit) {
    val accent = sosSeverityColor(sosType.severity)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) accent else Color(0xFFF5F5F5))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) accent.copy(alpha = 0.8f) else Color(0xFFE0E0E0),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier            = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(sosType.icon, null,
                tint     = if (isSelected) Color.White else accent,
                modifier = Modifier.size(26.dp))
            Text(
                sosType.label,
                fontSize   = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color      = if (isSelected) Color.White else Color(0xFF333333),
                textAlign  = TextAlign.Center,
                lineHeight = 13.sp,
                maxLines   = 2
            )
        }
    }
}

// ── SOS Sheet ─────────────────────────────────────────────────────────────────
// Parameters:
//   userName       — current user's display name
//   userGeoPoint   — current GPS position (nullable if GPS not ready)
//   onDismiss      — called when sheet should close
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosSheet(
    userName     : String,
    userGeoPoint : GeoPoint?,
    onDismiss    : () -> Unit,
    onSosSent    : () -> Unit = {}
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val db      = FirebaseFirestore.getInstance()

    var isSending  by remember { mutableStateOf(false) }
    var hasLaunched by remember { mutableStateOf(false) }

    suspend fun sendEmergencySignal(photoBytes: ByteArray?) {
        withContext(Dispatchers.IO) {
            try {
                val hasLocation = userGeoPoint != null
                val address = if (hasLocation) {
                    try {
                        val addresses = Geocoder(context, Locale.getDefault())
                            .getFromLocation(userGeoPoint!!.latitude, userGeoPoint.longitude, 1)
                        if (!addresses.isNullOrEmpty()) addresses[0].getAddressLine(0) ?: "Unknown Location"
                        else "Unknown Location"
                    } catch (e: Exception) { "Location Error" }
                } else "Location unavailable — check on rider"

                var photoUrl: String? = null
                if (photoBytes != null) {
                    try {
                        val base64Image = android.util.Base64.encodeToString(
                            photoBytes, android.util.Base64.NO_WRAP
                        )
                        val apiKey = com.darkhorses.PedalConnect.BuildConfig.IMGBB_API_KEY
                        val boundary = "----FormBoundary${System.currentTimeMillis()}"
                        val conn = java.net.URL(
                            "https://api.imgbb.com/1/upload?key=$apiKey&expiration=2592000"
                        ).openConnection() as java.net.HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.doOutput      = true
                        conn.connectTimeout = 30_000
                        conn.readTimeout    = 30_000
                        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                        val body = buildString {
                            append("--$boundary\r\n")
                            append("Content-Disposition: form-data; name=\"image\"\r\n\r\n")
                            append(base64Image)
                            append("\r\n--$boundary--\r\n")
                        }.toByteArray(Charsets.UTF_8)
                        conn.setRequestProperty("Content-Length", body.size.toString())
                        conn.outputStream.use { it.write(body) }
                        val responseCode = conn.responseCode
                        val response = if (responseCode == 200) {
                            conn.inputStream.bufferedReader().readText()
                        } else {
                            conn.errorStream?.bufferedReader()?.readText()
                                ?: "Upload failed with code $responseCode"
                        }
                        conn.disconnect()
                        val json = org.json.JSONObject(response)
                        if (json.optBoolean("success", false)) {
                            photoUrl = json.getJSONObject("data").getString("url")
                        } else {
                            android.util.Log.e("SOS_PHOTO", "imgBB rejected upload: $response")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SOS_PHOTO", "Upload exception: ${e.message}")
                    }
                }

                val displayName = try {
                    val snap = db.collection("users")
                        .whereEqualTo("username", userName)
                        .limit(1).get().await()
                    snap.documents.firstOrNull()
                        ?.getString("displayName")
                        ?.takeIf { it.isNotBlank() } ?: userName
                } catch (e: Exception) { userName }

                val alert = hashMapOf<String, Any>(
                    "riderName"        to userName,
                    "riderNameLower"   to userName.trim().lowercase(),
                    "riderDisplayName" to displayName,
                    "emergencyType"  to "SOS",
                    "latitude"       to (userGeoPoint?.latitude  ?: 0.0),
                    "longitude"      to (userGeoPoint?.longitude ?: 0.0),
                    "locationName"   to address,
                    "timestamp"      to System.currentTimeMillis(),
                    "severity"       to "HIGH",
                    "status"         to "active",
                    "responderName"  to ""
                )
                if (!hasLocation)     alert["locationError"] = true
                if (photoUrl != null) alert["photoUrl"]      = photoUrl

                try {
                    withContext(Dispatchers.IO) { db.collection("alerts").add(alert).await() }
                    withContext(Dispatchers.Main) {
                        val msg = if (hasLocation) "SOS Alert Sent!" else "⚠️ Alert sent — location unknown."
                        Toast.makeText(context, msg,
                            if (hasLocation) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                    }
                    if (hasLocation) {
                        try {
                            val locationSnap = db.collection("userLocations").get().await()
                            val staleThresholdMs = 30_000L
                            val now = System.currentTimeMillis()
                            val nearbyCount = locationSnap.documents.count { doc ->
                                val name = doc.getString("userName") ?: return@count false
                                if (name.trim().equals(userName, ignoreCase = true)) return@count false
                                if (name.trim().equals("Admin", ignoreCase = true)) return@count false
                                val ts  = doc.getLong("timestamp") ?: 0L
                                if (now - ts > staleThresholdMs) return@count false
                                val lat = doc.getDouble("latitude")  ?: return@count false
                                val lon = doc.getDouble("longitude") ?: return@count false
                                haversineKm(userGeoPoint!!.latitude, userGeoPoint.longitude, lat, lon) <= 3.0
                            }
                            withContext(Dispatchers.Main) {
                                if (nearbyCount == 0) {
                                    Toast.makeText(
                                        context,
                                        "⚠️ No cyclists are nearby right now. Consider calling emergency services.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } catch (e: Exception) { /* non-critical */ }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to send alert: ${e.message?.take(60)}",
                            Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to send alert. Try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun bitmapToJpegBytes(bmp: android.graphics.Bitmap): ByteArray {
        val maxWidth = 1200
        val ratio    = if (bmp.width > maxWidth) maxWidth.toFloat() / bmp.width else 1f
        val scaled   = if (ratio < 1f)
            android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true)
        else bmp
        val output = java.io.ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, output)
        return output.toByteArray()
    }

    fun sendNow(bitmap: android.graphics.Bitmap?) {
        if (isSending) return
        isSending = true
        try {
            val cancelIntent = Intent(context, FallDetectionService::class.java).apply {
                action = FallDetectionService.ACTION_CANCEL_SOS
            }
            context.startService(cancelIntent)
        } catch (e: Exception) {}
        val photoBytes = bitmap?.let { bitmapToJpegBytes(it) }
        scope.launch {
            try {
                sendEmergencySignal(photoBytes)
                onSosSent()
            } finally {
                isSending = false
                onDismiss()
            }
        }
    }

    var photoUri by remember { mutableStateOf<android.net.Uri?>(null) }

    fun createImageUri(): android.net.Uri {
        val file = java.io.File.createTempFile(
            "SOS_${System.currentTimeMillis()}_", ".jpg", context.cacheDir
        )
        return androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
    }

    fun loadBitmapFromUri(uri: android.net.Uri): android.graphics.Bitmap? {
        return try {
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            } ?: return null

            val rotationDegrees = context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = androidx.exifinterface.media.ExifInterface(input)
                when (exif.getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                )) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            if (rotationDegrees != 0f) {
                val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees) }
                android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else bitmap
        } catch (e: Exception) {
            android.util.Log.e("SOS_PHOTO", "Failed to load captured photo: ${e.message}")
            null
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = photoUri
        if (success && uri != null) {
            val bitmap = loadBitmapFromUri(uri)
            if (bitmap != null) {
                sendNow(bitmap)
            } else {
                Toast.makeText(context, "Failed to read photo. Try again.", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        } else {
            // User backed out of the camera — don't send an alert
            onDismiss()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createImageUri()
            photoUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission is required to send an SOS photo.", Toast.LENGTH_LONG).show()
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        if (hasLaunched) return@LaunchedEffect
        hasLaunched = true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val uri = createImageUri()
            photoUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (isSending) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Card(
                shape  = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = SosRed)
                    Text("Sending SOS…", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1A1A1A))
                }
            }
        }
    }
}