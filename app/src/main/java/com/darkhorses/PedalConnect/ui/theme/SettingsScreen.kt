package com.darkhorses.PedalConnect.ui.theme

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

// ── Design tokens ─────────────────────────────────────────────────────────────
private val SettingsGreen900 = Color(0xFF06402B)
private val SettingsGreen700 = Color(0xFF0A5C3D)
private val SettingsGreen500 = Color(0xFF1A7A52)
private val SettingsGreen100 = Color(0xFFE8F5EE)
private val DangerRed        = Color(0xFFD32F2F)
private val DangerRedBg      = Color(0xFFFFEBEE)
private val AmberWarning     = Color(0xFFF59E0B)
private val SurfaceWhite     = Color(0xFFFFFFFF)
private val SurfaceGrey      = Color(0xFFF0F4F0)
private val TextPrimary      = Color(0xFF111827)
private val TextSecondary    = Color(0xFF6B7280)
private val DividerColor     = Color(0xFFE5EAE5)
private val ErrorRed         = Color(0xFFB91C1C)
private val ErrorRedBg       = Color(0xFFFEF2F2)

// ── Constants ─────────────────────────────────────────────────────────────────
private const val BIO_MAX_CHARS = 160
private val BIKE_TYPE_OPTIONS = listOf(
    "Road Bike", "Mountain Bike (MTB)", "Folding Bike", "Fixed Gear / Fixie",
    "Gravel Bike", "Hybrid Bike", "BMX Bike", "City / Commuter Bike",
    "E-Bike (Electric)", "Other"
)
private val SKILL_LEVEL_OPTIONS = listOf("Beginner", "Intermediate", "Advanced", "Expert")

// ── Settings data class (loaded from Firestore) ───────────────────────────────
private data class UserSettings(
    val displayName: String                 = "",
    val bio: String                         = "",
    val bikeTypes: List<String>             = emptyList(),
    val skillLevel: String                  = "",
    val photoUrl: String                    = "",
    val notificationsEnabled: Boolean       = true,
    val sosAlertsEnabled: Boolean           = true,
    val nearbyCyclistAlertsEnabled: Boolean = true,
    val locationSharingEnabled: Boolean     = true,
    val darkModeEnabled: Boolean            = false
)

// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val scope       = rememberCoroutineScope()
    val context     = LocalContext.current
    val auth        = FirebaseAuth.getInstance()
    val db          = FirebaseFirestore.getInstance()
    val currentUser = auth.currentUser

    // ── Remote state ──────────────────────────────────────────────────────────
    var userDocId        by remember { mutableStateOf<String?>(null) }
    var isLoadingSettings by remember { mutableStateOf(true) }
    var settings          by remember { mutableStateOf(UserSettings()) }
    var userName         by remember { mutableStateOf("") }
    var isAdmin          by remember { mutableStateOf(false) }

    // ── Edit Profile draft state ──────────────────────────────────────────────
    var editProfileExpanded by remember { mutableStateOf(false) }
    var draftName           by remember { mutableStateOf("") }
    var draftBio            by remember { mutableStateOf("") }
    var draftBikeTypes      by remember { mutableStateOf<List<String>>(emptyList()) }
    var draftSkillLevel     by remember { mutableStateOf("") }
    var draftPhotoUrl       by remember { mutableStateOf("") }
    var isSavingProfile     by remember { mutableStateOf(false) }
    var isUploadingPhoto    by remember { mutableStateOf(false) }
    var profileSaveError    by remember { mutableStateOf<String?>(null) }
    var nameError           by remember { mutableStateOf<String?>(null) }
    // bikeTypeExpanded removed — bike type now uses a checklist
    var skillLevelExpanded  by remember { mutableStateOf(false) }

    // ── Toggle save state ─────────────────────────────────────────────────────
    var isSavingToggle by remember { mutableStateOf(false) }

    // ── Dialog state ──────────────────────────────────────────────────────────
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteStep1  by remember { mutableStateOf(false) }
    var showDeleteStep2  by remember { mutableStateOf(false) }
    var isDeleting       by remember { mutableStateOf(false) }
    var deleteError      by remember { mutableStateOf<String?>(null) }

    // ── Load settings from Firestore ──────────────────────────────────────────
    LaunchedEffect(currentUser?.email) {
        if (currentUser?.email == null) { isLoadingSettings = false; return@LaunchedEffect }
        try {
            val snap = db.collection("users")
                .whereEqualTo("email", currentUser.email)
                .limit(1).get().await()
            val doc = snap.documents.firstOrNull()
            if (doc != null) {
                userDocId = doc.id
                userName  = doc.getString("username") ?: ""
                isAdmin   = doc.getString("role") == "admin"
                val prefs = doc.get("settings") as? Map<*, *>
                // Graceful migration: bikeTypes may be an array (new) or a string (old)
                val rawBikeTypes: List<String> = when (val raw = doc.get("bikeTypes")) {
                    is List<*> -> raw.filterIsInstance<String>()
                    else -> {
                        // Legacy string field — wrap in list, will be overwritten on next save
                        val legacy = doc.getString("bikeType") ?: ""
                        if (legacy.isNotBlank()) listOf(legacy) else emptyList()
                    }
                }
                settings = UserSettings(
                    displayName                = doc.getString("displayName")?.takeIf { it.isNotBlank() }
                        ?: currentUser.displayName?.takeIf { it.isNotBlank() }
                        ?: userName,
                    bio                        = doc.getString("bio") ?: "",
                    bikeTypes                  = rawBikeTypes,
                    skillLevel                 = doc.getString("skillLevel") ?: "",
                    photoUrl                   = doc.getString("photoUrl") ?: "",
                    notificationsEnabled       = prefs?.get("notificationsEnabled") as? Boolean ?: true,
                    sosAlertsEnabled           = prefs?.get("sosAlertsEnabled") as? Boolean ?: true,
                    nearbyCyclistAlertsEnabled = prefs?.get("nearbyCyclistAlertsEnabled") as? Boolean ?: true,
                    locationSharingEnabled     = prefs?.get("locationSharingEnabled") as? Boolean ?: true,
                    darkModeEnabled            = prefs?.get("darkModeEnabled") as? Boolean ?: false
                )
                // Seed draft with current values — fall back to username so field is never blank
                draftName       = settings.displayName.takeIf { it.isNotBlank() } ?: userName
                draftBio        = settings.bio
                draftBikeTypes  = settings.bikeTypes
                draftSkillLevel = settings.skillLevel
                draftPhotoUrl   = settings.photoUrl
            }
        } catch (e: Exception) {
            Toast.makeText(context,
                "Failed to load settings: ${e.message?.take(60)}", Toast.LENGTH_SHORT).show()
        }
        isLoadingSettings = false
    }

    // ── Helper: save a single boolean toggle to Firestore ─────────────────────
    fun saveToggle(key: String, value: Boolean) {
        val docId = userDocId ?: return
        isSavingToggle = true
        scope.launch {
            try {
                db.collection("users").document(docId)
                    .set(mapOf("settings" to mapOf(key to value)), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Toast.makeText(context,
                    "Couldn't save setting: ${e.message?.take(60)}", Toast.LENGTH_SHORT).show()
            } finally { isSavingToggle = false }
        }
    }

    // ── Photo picker ──────────────────────────────────────────────────────────
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isUploadingPhoto = true
        scope.launch {
            try {
                val imgBBApiKey = com.darkhorses.PedalConnect.BuildConfig.IMGBB_API_KEY
                val uploadedUrl = withContext(Dispatchers.IO) {
                    val bytes  = context.contentResolver.openInputStream(uri)!!.readBytes()
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                    val conn   = java.net.URL("https://api.imgbb.com/1/upload")
                        .openConnection() as java.net.HttpURLConnection
                    conn.requestMethod  = "POST"; conn.doOutput = true
                    conn.connectTimeout = 30_000; conn.readTimeout = 30_000
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    val body = "key=$imgBBApiKey&image=${java.net.URLEncoder.encode(base64, "UTF-8")}"
                    conn.outputStream.write(body.toByteArray()); conn.outputStream.flush()
                    val resp = conn.inputStream.bufferedReader().readText()
                    org.json.JSONObject(resp).getJSONObject("data").getString("url")
                }
                draftPhotoUrl = uploadedUrl
            } catch (e: Exception) {
                Toast.makeText(context,
                    "Photo upload failed: ${e.message?.take(60)}", Toast.LENGTH_LONG).show()
            } finally { isUploadingPhoto = false }
        }
    }

    // ── Save Profile (validation + Firestore + Auth update) ───────────────────
    fun saveProfile() {
        nameError = null; profileSaveError = null
        val trimmedName = draftName.trim()

        // ── Validation ────────────────────────────────────────────────────────
        when {
            trimmedName.isBlank() ->
            { nameError = "Display name cannot be empty"; return }
            trimmedName.length < 2 ->
            { nameError = "Name must be at least 2 characters"; return }
            trimmedName.length > 40 ->
            { nameError = "Name must be 40 characters or fewer"; return }
            draftBio.length > BIO_MAX_CHARS ->
            { profileSaveError = "Bio must be $BIO_MAX_CHARS characters or fewer"; return }
            userDocId == null ->
            { profileSaveError = "Account not found. Please sign out and back in."; return }
        }

        isSavingProfile = true
        scope.launch {
            try {
                // 1. Update Firebase Auth displayName
                currentUser?.updateProfile(
                    userProfileChangeRequest { displayName = trimmedName }
                )?.await()

                // 2. Update Firestore
                val updates = buildMap<String, Any> {
                    put("displayName", trimmedName)
                    put("bio",         draftBio.trim())
                    put("bikeTypes",   draftBikeTypes)
                    put("skillLevel",  draftSkillLevel)
                    if (draftPhotoUrl.isNotBlank()) put("photoUrl", draftPhotoUrl)
                }
                db.collection("users").document(userDocId!!)
                    .set(updates, SetOptions.merge()).await()

                // 3. Commit to local state
                settings = settings.copy(
                    displayName = trimmedName,
                    bio         = draftBio.trim(),
                    bikeTypes   = draftBikeTypes,
                    skillLevel  = draftSkillLevel,
                    photoUrl    = if (draftPhotoUrl.isNotBlank()) draftPhotoUrl else settings.photoUrl
                )
                // Backfill displayName on all existing posts by this user
                db.collection("posts")
                    .whereEqualTo("userName", userName)
                    .get()
                    .await()
                    .documents.forEach { doc ->
                        doc.reference.update("displayName", trimmedName).await()
                    }

                editProfileExpanded = false
                invalidateUserProfileCache(userName)
                Toast.makeText(context, "Profile updated ✅", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                profileSaveError = "Save failed: ${e.message?.take(80)}"
            } finally { isSavingProfile = false }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dialogs
    // ─────────────────────────────────────────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                Box(Modifier.size(48.dp).background(DangerRedBg, CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Logout, null,
                        tint = DangerRed, modifier = Modifier.size(26.dp))
                }
            },
            title = { Text("Log Out?", fontWeight = FontWeight.Bold, color = SettingsGreen900) },
            text  = {
                Text("You will be returned to the login screen. Log back in anytime to continue riding.",
                    color = Color(0xFF5A6B5A), fontSize = 14.sp)
            },
            confirmButton = {
                Button(
                    onClick = { showLogoutDialog = false; logoutAndNavigate(context, navController) },
                    colors  = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    shape   = RoundedCornerShape(10.dp)
                ) { Text("Log Out", color = Color.White, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLogoutDialog = false },
                    shape = RoundedCornerShape(10.dp)) {
                    Text("Cancel", color = SettingsGreen900)
                }
            },
            containerColor = Color(0xFFF5F7F5), shape = RoundedCornerShape(20.dp)
        )
    }

    if (showDeleteStep1) {
        AlertDialog(
            onDismissRequest = { showDeleteStep1 = false },
            shape = RoundedCornerShape(20.dp), containerColor = Color.White,
            icon = {
                Box(Modifier.size(56.dp).background(DangerRedBg, CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Warning, null, tint = DangerRed, modifier = Modifier.size(30.dp))
                }
            },
            title = {
                Text("Delete Account?", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                    color = DangerRed, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Text("This action is permanent and cannot be undone. Before you proceed, please understand what will be deleted:",
                        fontSize = 14.sp, color = Color(0xFF444444),
                        lineHeight = 20.sp, textAlign = TextAlign.Center)
                    Card(shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DangerRedBg),
                        elevation = CardDefaults.cardElevation(0.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DeleteWarningItem("Your profile and account credentials")
                            DeleteWarningItem("All your posts and community activity")
                            DeleteWarningItem("Your ride history and saved routes")
                            DeleteWarningItem("Any active emergency alerts you've posted")
                            DeleteWarningItem("You will not be able to recover this data")
                        }
                    }
                }
            },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showDeleteStep1 = false; showDeleteStep2 = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                    ) {
                        Icon(Icons.Rounded.DeleteForever, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("I Understand, Continue", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    OutlinedButton(onClick = { showDeleteStep1 = false },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp)) {
                        Text("Keep My Account", color = SettingsGreen900, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        )
    }

    if (showDeleteStep2) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteStep2 = false },
            shape = RoundedCornerShape(20.dp), containerColor = Color.White,
            icon = {
                Box(Modifier.size(56.dp).background(DangerRedBg, CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.DeleteForever, null, tint = DangerRed, modifier = Modifier.size(30.dp))
                }
            },
            title = {
                Text("Are You Absolutely Sure?", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                    color = DangerRed, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Tapping \"Delete My Account\" below will immediately and permanently erase everything. There is no undo.",
                        fontSize = 14.sp, color = Color(0xFF444444),
                        lineHeight = 20.sp, textAlign = TextAlign.Center)
                    if (deleteError != null) {
                        Card(shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = DangerRedBg),
                            elevation = CardDefaults.cardElevation(0.dp),
                            modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.ErrorOutline, null, tint = DangerRed,
                                    modifier = Modifier.size(16.dp))
                                Text(deleteError!!, fontSize = 12.sp,
                                    color = DangerRed, lineHeight = 16.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            deleteError = null; isDeleting = true
                            scope.launch {
                                val result = deleteAccountFromFirebase()
                                if (result == null) {
                                    isDeleting = false; showDeleteStep2 = false
                                    logoutAndNavigate(context, navController)
                                } else { isDeleting = false; deleteError = result }
                            }
                        },
                        enabled = !isDeleting,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DangerRed,
                            disabledContainerColor = DangerRed.copy(alpha = 0.5f)
                        )
                    ) {
                        if (isDeleting) {
                            CircularProgressIndicator(color = Color.White,
                                modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Deleting…", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        } else {
                            Icon(Icons.Rounded.DeleteForever, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Delete My Account", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    OutlinedButton(
                        onClick = { if (!isDeleting) { showDeleteStep2 = false; deleteError = null } },
                        enabled = !isDeleting,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Cancel", color = SettingsGreen900, fontWeight = FontWeight.SemiBold) }
                }
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Main Scaffold
    // ─────────────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Settings", fontWeight = FontWeight.Bold,
                        color = Color.White, fontSize = 20.sp)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Rounded.ArrowBackIosNew, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SettingsGreen900)
            )
        },
        containerColor = SurfaceGrey
    ) { inner ->

        // Loading skeleton while Firestore loads
        if (isLoadingSettings) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SettingsGreen700,
                    modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
            }
            return@Scaffold
        }

        val focusManager = LocalFocusManager.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {

            // ── Profile Card (collapsible edit form) ──────────────────────────
            Spacer(Modifier.height(4.dp))
            Card(
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier  = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            ) {
                Column {
                    // Always-visible header row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (!isAdmin) Modifier.clickable { editProfileExpanded = !editProfileExpanded } else Modifier)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(listOf(SettingsGreen500, SettingsGreen900))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val shownPhoto = if (editProfileExpanded) draftPhotoUrl else settings.photoUrl
                            when {
                                isUploadingPhoto -> CircularProgressIndicator(
                                    color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                shownPhoto.isNotBlank() -> {
                                    AsyncImage(
                                        model = shownPhoto, contentDescription = "Avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                }
                                else -> Icon(Icons.Rounded.Person, null,
                                    tint = Color.White, modifier = Modifier.size(30.dp))
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (editProfileExpanded) draftName.ifBlank { "Your Name" }
                                else settings.displayName.ifBlank { userName.ifBlank { "Cyclist" } },
                                fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary
                            )
                            Text(currentUser?.email ?: "", fontSize = 13.sp, color = TextSecondary)
                        }
                        // Edit / collapse icon — hidden for admin
                        if (!isAdmin) {
                            Box(
                                modifier = Modifier.size(32.dp).background(SettingsGreen100, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (editProfileExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.Edit,
                                    contentDescription = if (editProfileExpanded) "Collapse" else "Edit Profile",
                                    tint = SettingsGreen700, modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // ── Inline edit form — hidden for admin ───────────────────
                    AnimatedVisibility(
                        visible = editProfileExpanded && !isAdmin,
                        enter   = expandVertically() + fadeIn(),
                        exit    = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAF8))
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            HorizontalDivider(color = DividerColor)

                            // Photo section
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp).clip(CircleShape)
                                        .background(Brush.radialGradient(
                                            listOf(SettingsGreen500, SettingsGreen900)))
                                        .clickable { if (!isUploadingPhoto) photoPicker.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        isUploadingPhoto -> CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                        draftPhotoUrl.isNotBlank() -> AsyncImage(
                                            model = draftPhotoUrl, contentDescription = "Draft photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape))
                                        else -> Icon(Icons.Rounded.Person, null,
                                            tint = Color.White, modifier = Modifier.size(32.dp))
                                    }
                                }
                                Column(modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Profile Photo", fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Text("Tap the circle or the button to change",
                                        fontSize = 11.sp, color = TextSecondary, lineHeight = 15.sp)
                                    OutlinedButton(
                                        onClick = { if (!isUploadingPhoto) photoPicker.launch("image/*") },
                                        enabled = !isUploadingPhoto,
                                        shape   = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        border  = BorderStroke(1.dp, SettingsGreen700)
                                    ) {
                                        Icon(Icons.Rounded.CameraAlt, null,
                                            tint = SettingsGreen700, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (isUploadingPhoto) "Uploading…" else "Change Photo",
                                            fontSize = 12.sp, color = SettingsGreen700,
                                            fontWeight = FontWeight.Medium)
                                    }
                                }
                            }

                            // Display Name
                            ProfileTextField(
                                value         = draftName,
                                onValueChange = { v -> draftName = v.take(40); nameError = null },
                                label         = "Display Name *",
                                placeholder   = "Your name",
                                icon          = Icons.Rounded.Badge,
                                error         = nameError,
                                imeAction     = ImeAction.Next,
                                onNext        = { focusManager.moveFocus(FocusDirection.Down) }
                            )

                            // Bio
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                ProfileTextField(
                                    value         = draftBio,
                                    onValueChange = { v -> if (v.length <= BIO_MAX_CHARS) draftBio = v },
                                    label         = "Bio",
                                    placeholder   = "Tell other cyclists about yourself…",
                                    icon          = Icons.Rounded.Edit,
                                    singleLine    = false,
                                    maxLines      = 4,
                                    imeAction     = ImeAction.Default
                                )
                                Text(
                                    "${draftBio.length}/$BIO_MAX_CHARS",
                                    fontSize = 11.sp,
                                    color    = if (draftBio.length >= BIO_MAX_CHARS - 10) AmberWarning else TextSecondary,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }

                            // Bike Types (multi-select, max 3)
                            BikeTypePills(
                                selected = draftBikeTypes,
                                onChange = { draftBikeTypes = it }
                            )

                            // Skill Level
                            DropdownField(
                                label       = "Skill Level",
                                icon        = Icons.Rounded.Star,
                                selected    = draftSkillLevel,
                                placeholder = "Select your skill level",
                                options     = SKILL_LEVEL_OPTIONS,
                                expanded    = skillLevelExpanded,
                                onExpand    = { skillLevelExpanded = true },
                                onDismiss   = { skillLevelExpanded = false },
                                onSelect    = { draftSkillLevel = it; skillLevelExpanded = false }
                            )

                            // Global form error banner
                            AnimatedVisibility(visible = profileSaveError != null) {
                                Card(
                                    shape     = RoundedCornerShape(10.dp),
                                    colors    = CardDefaults.cardColors(containerColor = ErrorRedBg),
                                    elevation = CardDefaults.cardElevation(0.dp),
                                    modifier  = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Rounded.ErrorOutline, null,
                                            tint = ErrorRed, modifier = Modifier.size(16.dp))
                                        Text(profileSaveError ?: "", fontSize = 12.sp,
                                            color = ErrorRed, lineHeight = 16.sp,
                                            modifier = Modifier.weight(1f))
                                    }
                                }
                            }

                            // Cancel / Save row
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        // Reset draft back to last saved values
                                        draftName       = settings.displayName
                                        draftBio        = settings.bio
                                        draftBikeTypes  = settings.bikeTypes
                                        draftSkillLevel = settings.skillLevel
                                        draftPhotoUrl   = settings.photoUrl
                                        nameError = null; profileSaveError = null
                                        editProfileExpanded = false
                                    },
                                    enabled  = !isSavingProfile,
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape    = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Cancel", color = TextSecondary,
                                        fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                }
                                Button(
                                    onClick  = { saveProfile() },
                                    enabled  = !isSavingProfile && !isUploadingPhoto,
                                    modifier = Modifier.weight(2f).height(44.dp),
                                    shape    = RoundedCornerShape(12.dp),
                                    colors   = ButtonDefaults.buttonColors(
                                        containerColor         = SettingsGreen900,
                                        disabledContainerColor = SettingsGreen900.copy(alpha = 0.5f)
                                    )
                                ) {
                                    if (isSavingProfile) {
                                        CircularProgressIndicator(color = Color.White,
                                            modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Saving…", color = Color.White, fontSize = 14.sp)
                                    } else {
                                        Icon(Icons.Rounded.Check, null,
                                            tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Save Profile", color = Color.White,
                                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Notifications ─────────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader("Notifications")
            SettingsGroup {
                SettingsToggleRow(
                    icon    = Icons.Rounded.Notifications,
                    label   = "Push Notifications",
                    tint    = SettingsGreen700,
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { v ->
                        settings = settings.copy(notificationsEnabled = v)
                        saveToggle("notificationsEnabled", v)
                        if (!v) {
                            // Cascade off
                            settings = settings.copy(
                                sosAlertsEnabled           = false,
                                nearbyCyclistAlertsEnabled = false
                            )
                            saveToggle("sosAlertsEnabled", false)
                            saveToggle("nearbyCyclistAlertsEnabled", false)
                        }
                    }
                )
                AnimatedVisibility(
                    visible = settings.notificationsEnabled,
                    enter   = expandVertically(), exit = shrinkVertically()
                ) {
                    Column {
                        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 56.dp))
                        SettingsToggleRow(
                            icon     = Icons.Rounded.Campaign,
                            label    = "SOS & Emergency Alerts",
                            sublabel = "Get notified when riders nearby need help",
                            tint     = AmberWarning,
                            checked  = settings.sosAlertsEnabled,
                            onCheckedChange = { v ->
                                settings = settings.copy(sosAlertsEnabled = v)
                                saveToggle("sosAlertsEnabled", v)
                            }
                        )
                        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 56.dp))
                        SettingsToggleRow(
                            icon     = Icons.Rounded.MyLocation,
                            label    = "Nearby Cyclist Alerts",
                            sublabel = "Cyclists within 1 km of your route",
                            tint     = SettingsGreen700,
                            checked  = settings.nearbyCyclistAlertsEnabled,
                            onCheckedChange = { v ->
                                settings = settings.copy(nearbyCyclistAlertsEnabled = v)
                                saveToggle("nearbyCyclistAlertsEnabled", v)
                            }
                        )
                    }
                }
            }

            // ── Privacy & Safety ──────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader("Privacy & Safety")
            SettingsGroup {
                SettingsToggleRow(
                    icon     = Icons.Rounded.ShareLocation,
                    label    = "Share My Location",
                    sublabel = "Off stops broadcasting and hides you from nearby lists",
                    tint     = SettingsGreen700,
                    checked  = settings.locationSharingEnabled,
                    onCheckedChange = { v ->
                        settings = settings.copy(locationSharingEnabled = v)
                        saveToggle("locationSharingEnabled", v)
                        if (!v) {
                            // Remove from userLocations immediately so map hides user right away
                            scope.launch {
                                try {
                                    val docId = userDocId ?: return@launch
                                    val username = db.collection("users").document(docId)
                                        .get().await().getString("username") ?: return@launch
                                    db.collection("userLocations").document(username).delete().await()
                                } catch (_: Exception) { }
                            }
                        }
                    }
                )
                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 56.dp))
                SettingsRow(
                    icon     = Icons.Rounded.Shield,
                    label    = "Blocked Users",
                    sublabel = "Manage who can see your activity",
                    tint     = SettingsGreen700
                ) { /* TODO */ }
                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 56.dp))
                SettingsRow(
                    icon     = Icons.Rounded.VisibilityOff,
                    label    = "Data & Privacy",
                    sublabel = "Control what data is collected",
                    tint     = SettingsGreen700
                ) { /* TODO */ }
            }

            // ── App Preferences ───────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader("App Preferences")
            SettingsGroup {
                SettingsToggleRow(
                    icon     = Icons.Rounded.DarkMode,
                    label    = "Dark Mode",
                    sublabel = "Takes effect on next app launch",
                    tint     = SettingsGreen700,
                    checked  = settings.darkModeEnabled,
                    onCheckedChange = { v ->
                        settings = settings.copy(darkModeEnabled = v)
                        saveToggle("darkModeEnabled", v)
                        Toast.makeText(context,
                            "Dark mode will apply on next launch", Toast.LENGTH_SHORT).show()
                    }
                )
                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 56.dp))
                SettingsRow(icon = Icons.Rounded.Map, label = "Map Style",
                    sublabel = "Standard", tint = SettingsGreen700) { }
                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 56.dp))
                SettingsRow(icon = Icons.Rounded.Language, label = "Language",
                    sublabel = "English (PH)", tint = SettingsGreen700) { }
            }

            // ── Support & About ───────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            SettingsSectionHeader("Support & About")
            SettingsGroup {
                SettingsRow(icon = Icons.Rounded.HelpOutline, label = "Help & FAQ",
                    sublabel = "Get answers and troubleshoot", tint = SettingsGreen700) {
                    context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://pedalconnect.app/help")))
                }
                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 56.dp))
                SettingsRow(icon = Icons.Rounded.BugReport, label = "Report a Bug",
                    sublabel = "Help us improve PedalConnect", tint = SettingsGreen700) {
                    context.startActivity(Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:support@pedalconnect.app")
                        putExtra(Intent.EXTRA_SUBJECT, "Bug Report - PedalConnect")
                    })
                }
                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 56.dp))
                SettingsRow(icon = Icons.Rounded.Star, label = "Rate the App",
                    sublabel = "Enjoying PedalConnect? Leave a review!", tint = AmberWarning) {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=com.darkhorses.PedalConnect")))
                    } catch (_: Exception) { }
                }
                HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 56.dp))
                SettingsRow(icon = Icons.Rounded.Info, label = "About",
                    sublabel = "Version 1.0.0 · PedalConnect", tint = SettingsGreen700) { }
            }

            // ── Danger zone ───────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            Card(
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = DangerRedBg),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier  = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                    SettingsRow(icon = Icons.Rounded.Logout, label = "Log Out",
                        tint = DangerRed, showChevron = false) { showLogoutDialog = true }
                    HorizontalDivider(color = Color(0xFFFFCDD2))
                    SettingsRow(icon = Icons.Rounded.DeleteForever, label = "Delete Account",
                        sublabel = "Permanently erase all your data",
                        tint = DangerRed, showChevron = false) { showDeleteStep1 = true }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Edit profile sub-components
// ─────────────────────────────────────────────────────────────────────────────

/** Labelled OutlinedTextField with leading icon, optional error, and ImeAction */
@Composable
private fun ProfileTextField(
    value         : String,
    onValueChange : (String) -> Unit,
    label         : String,
    placeholder   : String,
    icon          : ImageVector,
    error         : String?   = null,
    singleLine    : Boolean   = true,
    maxLines      : Int       = 1,
    imeAction     : ImeAction = ImeAction.Next,
    onNext        : (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = if (error != null) ErrorRed else TextSecondary)
        OutlinedTextField(
            value         = value,
            onValueChange = onValueChange,
            placeholder   = {
                Text(placeholder, fontSize = 14.sp,
                    color = TextSecondary.copy(alpha = 0.55f))
            },
            leadingIcon   = {
                Icon(icon, null,
                    tint = if (error != null) ErrorRed else SettingsGreen700,
                    modifier = Modifier.size(18.dp))
            },
            isError        = error != null,
            supportingText = if (error != null) {
                { Text(error, color = ErrorRed, fontSize = 11.sp) }
            } else null,
            singleLine     = singleLine,
            maxLines       = maxLines,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction      = imeAction
            ),
            keyboardActions = KeyboardActions(onNext = { onNext?.invoke() }),
            shape  = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor    = SettingsGreen700,
                unfocusedBorderColor  = DividerColor,
                errorBorderColor      = ErrorRed,
                focusedLabelColor     = SettingsGreen700,
                focusedTextColor      = TextPrimary,
                unfocusedTextColor    = TextPrimary,
                disabledTextColor     = TextSecondary
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Multi-select bike type pill toggles, capped at 3 */
@Composable
private fun BikeTypePills(
    selected : List<String>,
    onChange : (List<String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Bike Type",
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color      = TextSecondary
            )
            Text(
                "${selected.size}/3",
                fontSize = 11.sp,
                color    = if (selected.size >= 3) AmberWarning else TextSecondary
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp),
            modifier              = Modifier.fillMaxWidth()
        ) {
            BIKE_TYPE_OPTIONS.forEach { option ->
                val isChecked  = option in selected
                val isDisabled = !isChecked && selected.size >= 3
                val bgColor    = if (isChecked) SettingsGreen700 else DividerColor.copy(alpha = 0.5f)
                val textColor  = when {
                    isChecked  -> Color.White
                    isDisabled -> TextSecondary.copy(alpha = 0.35f)
                    else       -> TextSecondary
                }
                Row(
                    modifier = Modifier
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(bgColor)
                        .clickable(enabled = !isDisabled) {
                            onChange(
                                if (isChecked) selected - option
                                else (selected + option).takeLast(3)
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (isChecked) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint     = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                    Text(
                        option,
                        fontSize   = 12.sp,
                        fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                        color      = textColor,
                        softWrap   = false
                    )
                }
            }
        }
        if (selected.size >= 3) {
            Text(
                "Maximum 3 bike types selected",
                fontSize = 11.sp,
                color    = AmberWarning
            )
        }
    }
}

/** Read-only dropdown using ExposedDropdownMenuBox */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label       : String,
    icon        : ImageVector,
    selected    : String,
    placeholder : String,
    options     : List<String>,
    expanded    : Boolean,
    onExpand    : () -> Unit,
    onDismiss   : () -> Unit,
    onSelect    : (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        ExposedDropdownMenuBox(
            expanded         = expanded,
            onExpandedChange = { if (it) onExpand() else onDismiss() }
        ) {
            OutlinedTextField(
                value         = selected.ifBlank { placeholder },
                onValueChange = {},
                readOnly      = true,
                leadingIcon   = {
                    Icon(icon, null, tint = SettingsGreen700, modifier = Modifier.size(18.dp))
                },
                trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                textStyle     = LocalTextStyle.current.copy(
                    color    = if (selected.isBlank()) TextSecondary.copy(0.55f) else TextPrimary,
                    fontSize = 14.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor         = SettingsGreen700,
                    unfocusedBorderColor       = DividerColor,
                    focusedTrailingIconColor   = SettingsGreen700,
                    unfocusedTrailingIconColor = TextSecondary,
                    focusedTextColor           = TextPrimary,
                    unfocusedTextColor         = TextPrimary,
                    disabledTextColor          = TextPrimary
                ),
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded         = expanded,
                onDismissRequest = onDismiss,
                modifier         = Modifier.background(SurfaceWhite)
            ) {
                options.forEach { option ->
                    val isSelected = option == selected
                    DropdownMenuItem(
                        text = {
                            Text(option, fontSize = 14.sp,
                                color      = if (isSelected) SettingsGreen700 else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                        },
                        onClick     = { onSelect(option) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Rounded.Check, null,
                                tint = SettingsGreen700, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable row composables (unchanged API, kept for all other sections)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text          = title.uppercase(),
        fontSize      = 11.sp, fontWeight = FontWeight.Bold,
        color         = SettingsGreen500, letterSpacing = 1.2.sp,
        modifier      = Modifier.padding(start = 8.dp, bottom = 4.dp, top = 8.dp)
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier  = Modifier.fillMaxWidth()
    ) { Column(content = content) }
}

@Composable
private fun SettingsRow(
    icon        : ImageVector,
    label       : String,
    sublabel    : String?  = null,
    tint        : Color,
    showChevron : Boolean  = true,
    onClick     : () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bgColor by animateColorAsState(
        if (isPressed) tint.copy(alpha = 0.05f) else Color.Transparent, label = "rowBg")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (sublabel != null) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(38.dp)
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            if (sublabel != null)
                Text(sublabel, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
        }
        if (showChevron)
            Icon(Icons.Rounded.ChevronRight, null,
                tint = Color(0xFFCCCCCC), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsToggleRow(
    icon            : ImageVector,
    label           : String,
    sublabel        : String?  = null,
    tint            : Color,
    checked         : Boolean,
    onCheckedChange : (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (sublabel != null) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(38.dp)
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            if (sublabel != null)
                Text(sublabel, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
        }
        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            colors          = SwitchDefaults.colors(
                checkedThumbColor    = Color.White, checkedTrackColor    = tint,
                uncheckedThumbColor  = Color.White, uncheckedTrackColor  = Color(0xFFD1D5DB),
                uncheckedBorderColor = Color(0xFFD1D5DB)
            )
        )
    }
}

@Composable
private fun DeleteWarningItem(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Rounded.RemoveCircleOutline, null,
            tint = DangerRed, modifier = Modifier.size(15.dp).padding(top = 2.dp))
        Text(text, fontSize = 13.sp, color = Color(0xFF5D1A1A), lineHeight = 18.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Firebase helpers
// ─────────────────────────────────────────────────────────────────────────────

private suspend fun deleteAccountFromFirebase(): String? {
    return try {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
            ?: return "No authenticated user found. Please log in again."
        val db = FirebaseFirestore.getInstance()

        val userDocSnap = db.collection("users")
            .whereEqualTo("email", user.email ?: "")
            .limit(1).get().await()
        val userDoc  = userDocSnap.documents.firstOrNull()
        val userName = userDoc?.getString("username") ?: ""

        suspend fun deleteCollection(collection: String, field: String, value: String) {
            if (value.isBlank()) return
            db.collection(collection).whereEqualTo(field, value).get().await()
                .documents.forEach { it.reference.delete().await() }
        }

        userDoc?.reference?.delete()?.await()
        deleteCollection("posts", "userName", userName)

        db.collection("posts").whereArrayContains("likedBy", userName).get().await()
            .documents.forEach { doc ->
                val newLikes  = maxOf((doc.getLong("likes") ?: 1L) - 1L, 0L)
                val newLikedBy = (doc.get("likedBy") as? List<*>)
                    ?.filterIsInstance<String>()?.filter { it != userName } ?: emptyList()
                doc.reference.update(mapOf("likes" to newLikes, "likedBy" to newLikedBy)).await()
            }

        deleteCollection("comments",     "userName",  userName)
        deleteCollection("alerts",       "riderName", userName)
        deleteCollection("notifications","userName",  userName)

        db.collection("rideEvents").whereArrayContains("participants", userName).get().await()
            .documents.forEach { doc ->
                val updated = (doc.get("participants") as? List<*>)
                    ?.filterIsInstance<String>()?.filter { it != userName } ?: emptyList()
                doc.reference.update("participants", updated).await()
            }

        deleteCollection("rideEvents",  "organizer", userName)
        deleteCollection("savedRoutes", "userName",  userName)
        if (userName.isNotBlank())
            db.collection("userLocations").document(userName).delete().await()

        user.delete().await()
        null
    } catch (e: FirebaseAuthRecentLoginRequiredException) {
        "For security, please log out and log back in before deleting your account."
    } catch (e: Exception) {
        "Failed to delete account: ${e.message}"
    }
}