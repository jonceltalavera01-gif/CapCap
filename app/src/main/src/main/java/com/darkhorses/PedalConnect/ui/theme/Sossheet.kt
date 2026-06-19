package com.darkhorses.PedalConnect.ui.theme

import android.Manifest
import android.content.Context
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
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    onDismiss    : () -> Unit
) {
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()
    val db         = FirebaseFirestore.getInstance()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedSos       by remember { mutableStateOf<SosType?>(null) }
    var pickedPhotoUri    by remember { mutableStateOf<android.net.Uri?>(null) }
    var additionalDetails by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> pickedPhotoUri = uri }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) photoPickerLauncher.launch("image/*")
        else Toast.makeText(context, "Camera permission is required to attach a photo.", Toast.LENGTH_SHORT).show()
    }

    fun launchPhotoPicker() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) photoPickerLauncher.launch("image/*")
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun sendEmergencySignal(type: String, severity: String, details: String?) {
        if (userGeoPoint != null) {
            scope.launch(Dispatchers.IO) {
                val geocoder = Geocoder(context, Locale.getDefault())
                val address  = try {
                    val addresses = geocoder.getFromLocation(
                        userGeoPoint.latitude, userGeoPoint.longitude, 1
                    )
                    if (!addresses.isNullOrEmpty()) addresses[0].getAddressLine(0) ?: "Unknown Location"
                    else "Unknown Location"
                } catch (e: Exception) { "Location Error" }

                val alert = hashMapOf<String, Any>(
                    "riderName"      to userName,
                    "riderNameLower" to userName.trim().lowercase(),
                    "emergencyType"  to type,
                    "latitude"       to userGeoPoint.latitude,
                    "longitude"      to userGeoPoint.longitude,
                    "locationName"   to address,
                    "timestamp"      to System.currentTimeMillis(),
                    "severity"       to severity,
                    "status"         to "active",
                    "responderName"  to ""
                )
                if (!details.isNullOrBlank()) alert["additionalDetails"] = details
                withContext(Dispatchers.Main) {
                    db.collection("alerts").add(alert)
                        .addOnSuccessListener {
                            Toast.makeText(context, "$type Alert Sent!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "Failed to send alert", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        } else {
            scope.launch(Dispatchers.IO) {
                val alert = hashMapOf<String, Any>(
                    "riderName"      to userName,
                    "riderNameLower" to userName.trim().lowercase(),
                    "emergencyType"  to type,
                    "latitude"       to 0.0,
                    "longitude"      to 0.0,
                    "locationName"   to "Location unavailable — check on rider",
                    "timestamp"      to System.currentTimeMillis(),
                    "severity"       to "HIGH",
                    "status"         to "active",
                    "responderName"  to "",
                    "locationError"  to true
                )
                if (!details.isNullOrBlank()) alert["additionalDetails"] = details
                withContext(Dispatchers.Main) {
                    db.collection("alerts").add(alert)
                        .addOnSuccessListener {
                            Toast.makeText(context, "⚠️ Alert sent — location unknown.", Toast.LENGTH_LONG).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "Failed to send alert. Try again.", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = Color.White,
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier              = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(SosRedLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Warning, null, tint = SosRed, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text("What Happened?", fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp, color = Color(0xFF1A1A1A))
                    Text("Select any that applies to your situation",
                        fontSize = 12.sp, color = Color.Gray)
                }
            }

            HorizontalDivider(color = Color(0xFFF0F0F0))

            // ── Emergency type grid ───────────────────────────────────────────
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sosRow1.forEach { type ->
                            Box(modifier = Modifier.weight(1f)) {
                                SosGridTile(
                                    sosType    = type,
                                    isSelected = selectedSos?.label == type.label,
                                    onClick    = {
                                        selectedSos = if (selectedSos?.label == type.label) null else type
                                    }
                                )
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sosRow2.forEach { type ->
                            Box(modifier = Modifier.weight(1f)) {
                                SosGridTile(
                                    sosType    = type,
                                    isSelected = selectedSos?.label == type.label,
                                    onClick    = {
                                        selectedSos = if (selectedSos?.label == type.label) null else type
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ── Photo attachment ──────────────────────────────────────────────
            Card(
                onClick   = { launchPhotoPicker() },
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(
                    containerColor = if (pickedPhotoUri != null) Color(0xFFE8F5E9) else Color(0xFFFAFAFA)
                ),
                border    = if (pickedPhotoUri != null)
                    androidx.compose.foundation.BorderStroke(1.5.dp, SosGreen)
                else
                    androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (pickedPhotoUri != null) SosGreen else SosRedLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (pickedPhotoUri != null) Icons.Default.CheckCircle
                            else Icons.Default.CameraAlt,
                            null, tint = Color.White, modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (pickedPhotoUri != null) "Photo attached"
                            else "Can you take some pictures?",
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            color = if (pickedPhotoUri != null) SosGreen else Color(0xFF1A1A1A)
                        )
                        Text(
                            if (pickedPhotoUri != null) "Tap to change photo"
                            else "Attach a photo as proof (optional)",
                            fontSize = 11.sp, color = Color.Gray
                        )
                    }
                    if (pickedPhotoUri != null) {
                        IconButton(onClick = { pickedPhotoUri = null }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // ── Additional details ────────────────────────────────────────────
            OutlinedTextField(
                value         = additionalDetails,
                onValueChange = { if (it.length <= 200) additionalDetails = it },
                placeholder   = { Text("Explain us more (Optional)", color = Color.LightGray, fontSize = 14.sp) },
                modifier      = Modifier.fillMaxWidth().height(100.dp),
                shape         = RoundedCornerShape(14.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = SosRed,
                    unfocusedBorderColor    = Color(0xFFE0E0E0),
                    focusedContainerColor   = Color.White,
                    unfocusedContainerColor = Color.White,
                    cursorColor             = SosRed,
                    focusedTextColor        = Color(0xFF1A1A1A),
                    unfocusedTextColor      = Color(0xFF1A1A1A)
                )
            )

            // ── Send button ───────────────────────────────────────────────────
            Button(
                onClick = {
                    val type = selectedSos
                    if (type != null) {
                        sendEmergencySignal(
                            type.label, type.severity,
                            additionalDetails.trim().ifBlank { null }
                        )
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            onDismiss()
                        }
                    } else {
                        Toast.makeText(context, "Please select an emergency type", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier  = Modifier.fillMaxWidth().height(56.dp),
                shape     = RoundedCornerShape(16.dp),
                colors    = ButtonDefaults.buttonColors(
                    containerColor        = SosRed,
                    contentColor          = Color.White,
                    disabledContainerColor = SosRed.copy(alpha = 0.4f)
                ),
                elevation = ButtonDefaults.buttonElevation(4.dp)
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text(
                        if (selectedSos != null) "Send ${selectedSos!!.label} Alert"
                        else "Send SOS Report",
                        fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color.White
                    )
                }
            }

            // ── Cancel ────────────────────────────────────────────────────────
            TextButton(
                onClick  = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel", color = Color.Gray, fontWeight = FontWeight.Medium)
            }
        }
    }
}