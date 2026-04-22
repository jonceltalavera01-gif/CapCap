package com.darkhorses.PedalConnect.ui.theme

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val SP_Green900    = Color(0xFF06402B)
private val SP_Green800    = Color(0xFF0A5C3D)
private val SP_Green700    = Color(0xFF0D7050)
private val SP_Green50     = Color(0xFFF0FAF5)
private val SP_Red600      = Color(0xFFDC2626)
private val SP_Red50       = Color(0xFFFEF2F2)
private val SP_Amber500    = Color(0xFFF59E0B)
private val SP_Amber50     = Color(0xFFFFFBEB)
private val SP_BgCanvas    = Color(0xFFF5F7F6)
private val SP_BgSurface   = Color(0xFFFFFFFF)
private val SP_TextPrimary   = Color(0xFF111827)
private val SP_TextSecondary = Color(0xFF374151)
private val SP_TextMuted     = Color(0xFF6B7280)
private val SP_BorderDefault = Color(0xFFD1D5DB)
private val SP_DividerColor  = Color(0xFFE5E7EB)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPostSimpleScreen(navController: NavController, userName: String) {
    val context = LocalContext.current
    val db      = FirebaseFirestore.getInstance()
    val scope   = rememberCoroutineScope()

    var postText          by remember { mutableStateOf("") }
    var postTextError     by remember { mutableStateOf("") }
    var selectedImageUri  by remember { mutableStateOf<Uri?>(null) }
    var imageError        by remember { mutableStateOf("") }
    var isLoading         by remember { mutableStateOf(false) }
    var userPhotoUrl      by remember { mutableStateOf<String?>(null) }
    var userDisplayName   by remember { mutableStateOf("") }
    var selectedPostType  by remember { mutableStateOf("Community") }

    val charLimit = 500

    LaunchedEffect(userName) {
        db.collection("users").whereEqualTo("username", userName)
            .limit(1).get()
            .addOnSuccessListener { snap ->
                val doc = snap.documents.firstOrNull()
                userPhotoUrl    = doc?.getString("photoUrl")
                userDisplayName = doc?.getString("displayName") ?: ""
            }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> selectedImageUri = uri; imageError = "" }

    suspend fun compressImage(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
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

    suspend fun uploadImageBytes(bytes: ByteArray): ImgBBResult = withContext(Dispatchers.IO) {
        val base64Image = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val apiKey      = com.darkhorses.PedalConnect.BuildConfig.IMGBB_API_KEY
        val conn        = java.net.URL("https://api.imgbb.com/1/upload").openConnection()
                as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput      = true
        conn.connectTimeout = 30_000
        conn.readTimeout    = 30_000
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        val body = "key=$apiKey&image=${java.net.URLEncoder.encode(base64Image, "UTF-8")}"
        conn.outputStream.write(body.toByteArray())
        conn.outputStream.flush()
        val responseCode = conn.responseCode
        val response = if (responseCode == 200) {
            conn.inputStream.bufferedReader().readText()
        } else {
            conn.errorStream?.bufferedReader()?.readText()
                ?: throw Exception("ImgBB upload failed with code $responseCode")
        }
        val json = org.json.JSONObject(response)
        if (!json.getBoolean("success")) throw Exception("ImgBB upload failed")
        val data = json.getJSONObject("data")
        ImgBBResult(url = data.getString("url"), deleteUrl = data.getString("delete_url"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text("Add Post", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            postTextError = ""
                            if (postText.isBlank()) { postTextError = "Write something first"; return@TextButton }
                            if (postText.trim().length < 3) { postTextError = "Too short"; return@TextButton }
                            isLoading = true
                            scope.launch {
                                try {
                                    val uri = selectedImageUri
                                    if (uri != null) {
                                        val fileSize = context.contentResolver
                                            .openFileDescriptor(uri, "r")?.statSize ?: 0L
                                        if (fileSize > 5 * 1024 * 1024) {
                                            imageError = "Image must be under 5 MB"
                                            isLoading = false
                                            return@launch
                                        }
                                    }
                                    var imageUrl  = ""
                                    var deleteUrl = ""
                                    if (uri != null) {
                                        val compressed = compressImage(uri)
                                        val result     = uploadImageBytes(compressed)
                                        imageUrl  = result.url
                                        deleteUrl = result.deleteUrl
                                    }
                                    suspendCancellableCoroutine<Unit> { cont ->
                                        db.collection("posts").add(hashMapOf(
                                            "userName"       to userName,
                                            "displayName"    to userDisplayName.ifBlank { userName },
                                            "description"    to postText.trim(),
                                            "activity"       to selectedPostType,
                                            "distance"       to "",
                                            "timestamp"      to System.currentTimeMillis(),
                                            "likes"          to 0,
                                            "comments"       to 0,
                                            "likedBy"        to emptyList<String>(),
                                            "status"         to "pending",
                                            "imageUrl"       to imageUrl,
                                            "imageDeleteUrl" to deleteUrl,
                                            "postType"       to "simple"
                                        ))
                                            .addOnSuccessListener { cont.resume(Unit) }
                                            .addOnFailureListener { cont.resumeWithException(it) }
                                    }
                                    Toast.makeText(context, "Post submitted! ✅", Toast.LENGTH_SHORT).show()
                                    navController.navigateUp()
                                } catch (e: Exception) {
                                    isLoading = false
                                    Toast.makeText(context, "Error: ${e.message?.take(120)}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Publish", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SP_Green900),
                modifier = Modifier.shadow(elevation = 2.dp)
            )
        },
        containerColor = SP_BgCanvas
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(12.dp))

                // ── Poster identity ───────────────────────────────────────────
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SP_BgSurface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(SP_Green900, SP_Green700))),
                            Alignment.Center
                        ) {
                            if (!userPhotoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = userPhotoUrl, contentDescription = "Profile photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Text(userName.take(1).uppercase(), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(userDisplayName.ifBlank { userName }, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = SP_TextPrimary)
                            Text("Posting to community", fontSize = 12.sp, color = SP_TextSecondary)
                        }
                    }
                }

                // ── Text input ────────────────────────────────────────────────
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SP_BgSurface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = postText,
                            onValueChange = { if (it.length <= charLimit) { postText = it; postTextError = "" } },
                            placeholder = { Text("What's going on? Share with the community…", color = Color(0xFFD1D5DB), fontSize = 14.sp) },
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            shape = RoundedCornerShape(12.dp),
                            isError = postTextError.isNotEmpty(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor      = SP_Green700,
                                unfocusedBorderColor    = SP_BorderDefault,
                                errorBorderColor        = SP_Red600,
                                focusedContainerColor   = SP_BgCanvas,
                                unfocusedContainerColor = SP_BgCanvas,
                                cursorColor             = SP_Green700,
                                focusedTextColor        = SP_TextPrimary,
                                unfocusedTextColor      = SP_TextPrimary
                            )
                        )
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            if (postTextError.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Error, null, tint = SP_Red600, modifier = Modifier.size(12.dp))
                                    Text(postTextError, color = SP_Red600, fontSize = 12.sp)
                                }
                            } else Spacer(Modifier.size(1.dp))
                            Text(
                                "${postText.length}/$charLimit", fontSize = 12.sp,
                                color = if (postText.length > charLimit * 0.9) SP_Red600 else SP_TextMuted
                            )
                        }
                    }
                }

                // ── Post type selector ────────────────────────────────────────
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SP_BgSurface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                                    .background(SP_Green50),
                                Alignment.Center
                            ) {
                                Icon(Icons.Default.Label, null,
                                    tint = SP_Green900, modifier = Modifier.size(18.dp))
                            }
                            Text("Post Type",
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 14.sp,
                                color      = SP_TextPrimary)
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Community", "Discussion").forEach { type ->
                                val isSelected = selectedPostType == type
                                Box(
                                    Modifier.weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) SP_Green900 else SP_BgCanvas)
                                        .border(
                                            1.5.dp,
                                            if (isSelected) SP_Green800 else SP_BorderDefault,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication        = null
                                        ) { selectedPostType = type }
                                        .padding(vertical = 12.dp),
                                    Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            if (type == "Community") Icons.Default.Groups
                                            else Icons.Default.Forum,
                                            null,
                                            tint     = if (isSelected) Color.White else SP_Green900,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            type,
                                            fontSize   = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color      = if (isSelected) Color.White else SP_TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Optional photo ────────────────────────────────────────────
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SP_BgSurface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(SP_Green50), Alignment.Center) {
                                Icon(Icons.Default.Image, null, tint = SP_Green900, modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Text("Photo", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = SP_TextPrimary)
                                Text("Optional", fontSize = 11.sp, color = SP_TextMuted)
                            }
                        }
                        if (selectedImageUri != null) {
                            Box(Modifier.fillMaxWidth()) {
                                AsyncImage(
                                    model = selectedImageUri, contentDescription = "Selected photo",
                                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(8.dp)
                                        .size(30.dp).clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { selectedImageUri = null; imageError = "" },
                                    Alignment.Center
                                ) {
                                    Icon(Icons.Default.Close, "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                            if (imageError.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .background(SP_Red50).padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Icon(Icons.Default.Warning, null, tint = SP_Red600, modifier = Modifier.size(14.dp))
                                    Text(imageError, fontSize = 12.sp, color = SP_Red600)
                                }
                            }
                        } else {
                            Box(
                                Modifier.fillMaxWidth().height(110.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SP_BgCanvas)
                                    .border(1.5.dp, SP_BorderDefault, RoundedCornerShape(12.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { imagePickerLauncher.launch("image/*") },
                                Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.AddPhotoAlternate, null, tint = SP_Green700, modifier = Modifier.size(24.dp))
                                    Text("Tap to add a photo", fontSize = 13.sp, color = SP_TextSecondary, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                // ── Review note ───────────────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(SP_Amber50).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Info, null, tint = SP_Amber500, modifier = Modifier.size(15.dp))
                    Text(
                        "Posts are reviewed before appearing in the community feed.",
                        fontSize = 12.sp, color = Color(0xFF92400E), lineHeight = 18.sp
                    )
                }

                Spacer(Modifier.height(24.dp))
            }

            // Full-screen loading overlay
            if (isLoading) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                    Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SP_BgSurface),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(
                            Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = SP_Green900, modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
                            Text("Uploading your post…", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SP_TextPrimary)
                        }
                    }
                }
            }
        }
    }
}