package com.darkhorses.PedalConnect.ui.theme

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ─────────────────────────────────────────────────────────────────────────────
// Design Tokens — shared system
// ─────────────────────────────────────────────────────────────────────────────
private val Green900    = Color(0xFF06402B)
private val Green800    = Color(0xFF0A5C3D)
private val Green700    = Color(0xFF0D7050)
private val Green50     = Color(0xFFF0FAF5)
private val Green100    = Color(0xFFDDF1E8)

private val Red600      = Color(0xFFDC2626)
private val Red50       = Color(0xFFFEF2F2)
private val Amber500    = Color(0xFFF59E0B)
private val Amber50     = Color(0xFFFFFBEB)
private val Orange600   = Color(0xFFEA580C)
private val Orange50    = Color(0xFFFFF7ED)

private val BgCanvas    = Color(0xFFF5F7F6)
private val BgSurface   = Color(0xFFFFFFFF)
private val TextPrimary   = Color(0xFF111827)
private val TextSecondary = Color(0xFF374151)
private val TextMuted     = Color(0xFF6B7280)
private val DividerColor  = Color(0xFFE5E7EB)
private val BorderDefault = Color(0xFFD1D5DB)
private val BorderFocus   = Green700

// ─────────────────────────────────────────────────────────────────────────────
// Activity options
// ─────────────────────────────────────────────────────────────────────────────
private data class ActivityOption(val label: String, val icon: ImageVector)

private val activityOptions = listOf(
    ActivityOption("Morning Ride",  Icons.AutoMirrored.Filled.DirectionsBike),
    ActivityOption("Evening Ride",  Icons.AutoMirrored.Filled.DirectionsBike),
    ActivityOption("Group Ride",    Icons.Filled.Groups),
    ActivityOption("Mountain Ride", Icons.Filled.Landscape),
    ActivityOption("Commute",       Icons.Filled.Route),
    ActivityOption("Leisure Ride",  Icons.Filled.SelfImprovement)
)

// ─────────────────────────────────────────────────────────────────────────────
// AddPostScreen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPostScreen(navController: NavController, userName: String) {
    val context = LocalContext.current
    val db      = FirebaseFirestore.getInstance()

    var description       by remember { mutableStateOf("") }
    var selectedActivity  by remember { mutableStateOf("Morning Ride") }
    var distance          by remember { mutableStateOf("") }
    var isLoading         by remember { mutableStateOf(false) }
    var descriptionError  by remember { mutableStateOf("") }
    var distanceError     by remember { mutableStateOf("") }
    var visible           by remember { mutableStateOf(false) }
    var selectedImageUri  by remember { mutableStateOf<Uri?>(null) }
    var imageError        by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> selectedImageUri = uri; imageError = "" }

    LaunchedEffect(Unit) { visible = true }

    // Compress image to max 1200px wide at 80% JPEG quality before upload
    suspend fun compressImage(uri: android.net.Uri): ByteArray = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)!!
        val original    = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        val maxWidth  = 1200
        val ratio     = maxWidth.toFloat() / original.width.toFloat()
        val newWidth  = if (original.width > maxWidth) maxWidth else original.width
        val newHeight = if (original.width > maxWidth) (original.height * ratio).toInt() else original.height
        val scaled    = Bitmap.createScaledBitmap(original, newWidth, newHeight, true)
        val output    = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, output)
        original.recycle(); scaled.recycle()
        output.toByteArray()
    }

    data class ImgBBResult(val url: String, val deleteUrl: String)

    // Upload to ImgBB — returns both display URL and delete URL
    suspend fun uploadImageBytes(bytes: ByteArray): ImgBBResult = withContext(Dispatchers.IO) {
        val base64Image = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val apiKey      = com.darkhorses.PedalConnect.BuildConfig.IMGBB_API_KEY
        val apiUrl = java.net.URL("https://api.imgbb.com/1/upload?key=$apiKey")
        val boundary    = "----FormBoundary${System.currentTimeMillis()}"
        val conn        = apiUrl.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput      = true
        conn.connectTimeout = 30_000
        conn.readTimeout    = 30_000
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        conn.outputStream.bufferedWriter().use { writer ->
            writer.write("--$boundary\r\n")
            writer.write("Content-Disposition: form-data; name=\"image\"\r\n\r\n")
            writer.write(base64Image)
            writer.write("\r\n--$boundary--\r\n")
        }
        val responseCode = conn.responseCode
        val response = if (responseCode == 200) {
            conn.inputStream.bufferedReader().readText()
        } else {
            conn.errorStream?.bufferedReader()?.readText()
                ?: throw Exception("ImgBB upload failed with code $responseCode")
        }
        val json = org.json.JSONObject(response)
        if (!json.getBoolean("success")) throw Exception("ImgBB upload failed: ${json.optString("error")}")
        val data = json.getJSONObject("data")
        ImgBBResult(
            url       = data.getString("url"),
            deleteUrl = data.getString("delete_url")
        )
    }

    val charLimit = 280


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text("Share Activity", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Green900),
                modifier = Modifier.shadow(elevation = 2.dp)
            )
        },
        containerColor = BgCanvas
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(innerPadding).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            AnimatedVisibility(
                visible = visible,
                enter   = fadeIn() + slideInVertically(initialOffsetY = { 40 })
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // ── Poster identity ───────────────────────────────────────
                    Card(
                        Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = BgSurface),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(46.dp).clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Green900, Green700))),
                                Alignment.Center
                            ) {
                                Text(userName.take(1).uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(userName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(
                                        Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF22C55E))
                                    )
                                    Text("Sharing with the community", fontSize = 12.sp, color = TextSecondary)
                                }
                            }
                        }
                    }

                    // ── Description ───────────────────────────────────────────
                    PostSectionCard(
                        icon  = Icons.Default.Edit,
                        title = "What's on your mind?"
                    ) {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { if (it.length <= charLimit) { description = it; if (descriptionError.isNotEmpty()) descriptionError = "" } },
                            placeholder = { Text("Share your ride experience, tips, or how it felt today…", color = Color(0xFFD1D5DB), fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth().height(130.dp),
                            shape = RoundedCornerShape(12.dp), isError = descriptionError.isNotEmpty(),
                            colors = postFieldColors()
                        )
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            if (descriptionError.isNotEmpty()) {
                                InlineFieldError(descriptionError)
                            } else {
                                Spacer(Modifier.size(1.dp))
                            }
                            Text(
                                "${description.length}/$charLimit", fontSize = 12.sp,
                                color = if (description.length > charLimit * 0.9) Red600 else TextMuted
                            )
                        }
                    }

                    // ── Photo upload ──────────────────────────────────────────
                    PostSectionCard(
                        icon  = Icons.Default.Image,
                        title = "Photo",
                        subtitle = "Optional"
                    ) {
                        if (selectedImageUri != null) {
                            // Preview
                            Box(Modifier.fillMaxWidth()) {
                                AsyncImage(
                                    model = selectedImageUri, contentDescription = "Selected photo",
                                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                // Remove button — top-right
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(8.dp)
                                        .size(32.dp).clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { selectedImageUri = null; imageError = "" },
                                    Alignment.Center
                                ) {
                                    Icon(Icons.Default.Close, "Remove photo", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            // Validation status
                            when {
                                imageError.isNotEmpty() -> {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                            .background(Red50).padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        Icon(Icons.Default.Warning, null, tint = Red600, modifier = Modifier.size(14.dp))
                                        Text(imageError, fontSize = 12.sp, color = Red600)
                                    }
                                }
                                else -> {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF16A34A), modifier = Modifier.size(14.dp))
                                        Text("Photo ready to upload", fontSize = 12.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        } else {
                            // Pick photo button
                            Box(
                                Modifier.fillMaxWidth().height(130.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BgCanvas)
                                    .border(1.5.dp, BorderDefault, RoundedCornerShape(12.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { imagePickerLauncher.launch("image/*") },
                                Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        Modifier.size(48.dp).clip(CircleShape).background(Green50),
                                        Alignment.Center
                                    ) {
                                        Icon(Icons.Default.AddPhotoAlternate, null, tint = Green700, modifier = Modifier.size(24.dp))
                                    }
                                    Text("Tap to add a photo", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                                    Text("JPEG / PNG · Max 5 MB", fontSize = 11.sp, color = TextMuted)
                                }
                            }
                        }
                    }

                    // ── Activity type ─────────────────────────────────────────
                    PostSectionCard(
                        icon  = Icons.AutoMirrored.Filled.DirectionsBike,
                        title = "Activity Type"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            activityOptions.chunked(2).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { option ->
                                        val isSelected = selectedActivity == option.label
                                        Box(
                                            Modifier.weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSelected) Green900 else Green50)
                                                .border(
                                                    1.5.dp,
                                                    if (isSelected) Green800 else BorderDefault,
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) { selectedActivity = option.label }
                                                .padding(vertical = 11.dp, horizontal = 8.dp),
                                            Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    option.icon, null,
                                                    tint = if (isSelected) Color.White else Green800,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Text(
                                                    option.label, fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (isSelected) Color.White else TextPrimary,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                    if (row.size < 2) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // ── Distance ──────────────────────────────────────────────
                    PostSectionCard(
                        icon  = Icons.Default.Route,
                        title = "Distance Covered",
                        subtitle = "Optional"
                    ) {
                        OutlinedTextField(
                            value = distance,
                            onValueChange = {
                                val filtered = it.filter { c -> c.isDigit() || c == '.' }
                                if (filtered.count { c -> c == '.' } <= 1 && filtered.length <= 7) {
                                    distance = filtered; if (distanceError.isNotEmpty()) distanceError = ""
                                }
                            },
                            placeholder  = { Text("e.g. 25.5", color = Color(0xFFD1D5DB), fontSize = 13.sp) },
                            trailingIcon = {
                                Text("km", color = Green700, fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp, modifier = Modifier.padding(end = 14.dp))
                            },
                            singleLine = true, isError = distanceError.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            colors = postFieldColors()
                        )
                        if (distanceError.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            InlineFieldError(distanceError)
                        }
                    }

                    // ── Community note ────────────────────────────────────────
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Amber50).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, null, tint = Amber500, modifier = Modifier.size(15.dp).padding(top = 1.dp))
                        Text(
                            "Posts are reviewed before appearing in the community feed.",
                            fontSize = 12.sp, color = Color(0xFF92400E), lineHeight = 18.sp
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    // ── Submit ────────────────────────────────────────────────
                    Button(
                        onClick = {
                            var valid = true
                            descriptionError = ""; distanceError = ""; imageError = ""
                            if (description.isBlank()) { descriptionError = "Write something before posting"; valid = false }
                            else if (description.trim().length < 5) { descriptionError = "Description is too short"; valid = false }
                            if (distance.isNotBlank()) {
                                val d = distance.toDoubleOrNull()
                                when { d == null || d <= 0 -> { distanceError = "Enter a valid distance (e.g. 10.5)"; valid = false }
                                    d > 9999            -> { distanceError = "Distance seems too large"; valid = false } }
                            }
                            if (valid) {
                                isLoading = true
                                scope.launch {
                                    try {
                                        val uri = selectedImageUri

                                        // File size check
                                        if (uri != null) {
                                            val fileSize = context.contentResolver
                                                .openFileDescriptor(uri, "r")?.statSize ?: 0L
                                            if (fileSize > 5 * 1024 * 1024) {
                                                imageError = "Image must be under 5 MB"
                                                isLoading  = false
                                                return@launch
                                            }
                                        }


                                        // Compress + upload to ImgBB
                                        var imageUrl    = ""
                                        var deleteUrl   = ""
                                        if (uri != null) {
                                            val compressed = compressImage(uri)
                                            val result     = uploadImageBytes(compressed)
                                            imageUrl  = result.url
                                            deleteUrl = result.deleteUrl
                                        }

                                        // Save to Firestore — store deleteUrl so images
                                        // can be cleaned up when posts are deleted
                                        suspendCancellableCoroutine<Unit> { cont ->
                                            db.collection("posts").add(hashMapOf(
                                                "userName"       to userName,
                                                "description"    to description.trim(),
                                                "activity"       to selectedActivity,
                                                "distance"       to distance.trim(),
                                                "timestamp"      to System.currentTimeMillis(),
                                                "likes"          to 0,
                                                "comments"       to 0,
                                                "likedBy"        to emptyList<String>(),
                                                "status"         to "pending",
                                                "imageUrl"       to imageUrl,
                                                "imageDeleteUrl" to deleteUrl
                                            ))
                                                .addOnSuccessListener { cont.resume(Unit) }
                                                .addOnFailureListener { cont.resumeWithException(it) }
                                        }

                                        Toast.makeText(context, "Activity posted! 🚴", Toast.LENGTH_SHORT).show()
                                        navController.navigateUp()

                                    } catch (e: Exception) {
                                        isLoading = false
                                        Toast.makeText(context,
                                            "Failed to share. Check your connection and try again.",
                                            Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled  = !isLoading,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor         = Green900,
                            disabledContainerColor = Green900.copy(alpha = 0.4f)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Text("Post Activity", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
            }
            // Full-screen loading overlay while uploading
            if (isLoading) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = BgSurface),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(
                            Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Green900,
                                modifier = Modifier.size(40.dp),
                                strokeWidth = 3.dp
                            )
                            Text("Uploading your activity…",
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                color = TextPrimary)
                            Text("This may take a moment",
                                fontSize = 12.sp, color = TextMuted)
                        }
                    }
                }
            }
        } // end Box
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable post section card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PostSectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgSurface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Card header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Green50), Alignment.Center) {
                    Icon(icon, null, tint = Green900, modifier = Modifier.size(18.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                    if (subtitle != null) {
                        Text(subtitle, fontSize = 11.sp, color = TextMuted)
                    }
                }
            }
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Inline field error
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun InlineFieldError(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Default.Error, null, tint = Red600, modifier = Modifier.size(12.dp))
        Text(text, color = Red600, fontSize = 12.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared field colours
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun postFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = Green700,
    unfocusedBorderColor    = BorderDefault,
    errorBorderColor        = Red600,
    focusedContainerColor   = BgCanvas,
    unfocusedContainerColor = BgCanvas,
    errorContainerColor     = Red50,
    cursorColor             = Green700,
    focusedTextColor        = Color(0xFF111827),
    unfocusedTextColor      = Color(0xFF111827),
    errorTextColor          = Color(0xFF111827)
)