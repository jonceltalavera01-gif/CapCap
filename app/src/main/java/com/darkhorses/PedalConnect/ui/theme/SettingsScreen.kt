package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val SettingsGreen900 = Color(0xFF06402B)
private val SettingsGreen700 = Color(0xFF0A5C3D)
private val SettingsSage     = Color(0xFFAAB7AE)
private val DangerRed        = Color(0xFFD32F2F)
private val DangerRedBg      = Color(0xFFFFEBEE)

@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var showLogoutDialog       by remember { mutableStateOf(false) }
    // ── Delete account state ──────────────────────────────────────────────────
    var showDeleteStep1        by remember { mutableStateOf(false) } // warning dialog
    var showDeleteStep2        by remember { mutableStateOf(false) } // final confirm dialog
    var isDeleting             by remember { mutableStateOf(false) }
    var deleteError            by remember { mutableStateOf<String?>(null) }

    // ── Logout confirmation dialog ─────────────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon             = {
                Box(
                    modifier         = Modifier.size(48.dp).background(DangerRedBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Logout, null, tint = DangerRed, modifier = Modifier.size(26.dp))
                }
            },
            title = { Text("Log Out?", fontWeight = FontWeight.Bold, color = SettingsGreen900) },
            text  = {
                Text(
                    "You will be returned to the login screen. Log back in anytime to continue riding.",
                    color = Color(0xFF5A6B5A), fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        logoutAndNavigate(context, navController)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    shape  = RoundedCornerShape(10.dp)
                ) {
                    Text("Log Out", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showLogoutDialog = false },
                    shape   = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel", color = SettingsGreen900)
                }
            },
            containerColor = Color(0xFFF5F7F5),
            shape          = RoundedCornerShape(20.dp)
        )
    }

    // ── STEP 1: Delete account — warning dialog ───────────────────────────────
    if (showDeleteStep1) {
        AlertDialog(
            onDismissRequest = { showDeleteStep1 = false },
            shape            = RoundedCornerShape(20.dp),
            containerColor   = Color.White,
            icon = {
                Box(
                    modifier         = Modifier.size(56.dp).background(DangerRedBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Warning, null,
                        tint = DangerRed, modifier = Modifier.size(30.dp))
                }
            },
            title = {
                Text(
                    "Delete Account?",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 18.sp,
                    color      = DangerRed,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier            = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "This action is permanent and cannot be undone. Before you proceed, please understand what will be deleted:",
                        fontSize   = 14.sp,
                        color      = Color(0xFF444444),
                        lineHeight = 20.sp,
                        textAlign  = TextAlign.Center
                    )

                    // Warning checklist
                    Card(
                        shape     = RoundedCornerShape(12.dp),
                        colors    = CardDefaults.cardColors(containerColor = DangerRedBg),
                        elevation = CardDefaults.cardElevation(0.dp),
                        modifier  = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier            = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
                Column(
                    modifier            = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Destructive CTA advances to step 2
                    Button(
                        onClick  = { showDeleteStep1 = false; showDeleteStep2 = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = DangerRed)
                    ) {
                        Icon(Icons.Rounded.DeleteForever, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("I Understand, Continue",
                            fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    OutlinedButton(
                        onClick  = { showDeleteStep1 = false },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape    = RoundedCornerShape(12.dp)
                    ) {
                        Text("Keep My Account", color = SettingsGreen900, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        )
    }

    // ── STEP 2: Final irreversible confirmation ────────────────────────────────
    if (showDeleteStep2) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteStep2 = false },
            shape            = RoundedCornerShape(20.dp),
            containerColor   = Color.White,
            icon = {
                Box(
                    modifier         = Modifier.size(56.dp).background(DangerRedBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.DeleteForever, null,
                        tint = DangerRed, modifier = Modifier.size(30.dp))
                }
            },
            title = {
                Text(
                    "Are You Absolutely Sure?",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 18.sp,
                    color      = DangerRed,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    verticalArrangement     = Arrangement.spacedBy(12.dp),
                    horizontalAlignment     = Alignment.CenterHorizontally,
                    modifier                = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Tapping \"Delete My Account\" below will immediately and permanently erase everything. There is no undo.",
                        fontSize   = 14.sp,
                        color      = Color(0xFF444444),
                        lineHeight = 20.sp,
                        textAlign  = TextAlign.Center
                    )

                    // Error feedback
                    if (deleteError != null) {
                        Card(
                            shape     = RoundedCornerShape(10.dp),
                            colors    = CardDefaults.cardColors(containerColor = DangerRedBg),
                            elevation = CardDefaults.cardElevation(0.dp),
                            modifier  = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier              = Modifier.padding(12.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.ErrorOutline, null,
                                    tint = DangerRed, modifier = Modifier.size(16.dp))
                                Text(deleteError!!, fontSize = 12.sp,
                                    color = DangerRed, lineHeight = 16.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier            = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick  = {
                            deleteError = null
                            isDeleting  = true
                            scope.launch {
                                val result = deleteAccountFromFirebase()
                                if (result == null) {
                                    // Success — navigate to login and clear entire back stack
                                    isDeleting     = false
                                    showDeleteStep2 = false
                                    logoutAndNavigate(context, navController)
                                } else {
                                    isDeleting  = false
                                    deleteError = result
                                }
                            }
                        },
                        enabled  = !isDeleting,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor         = DangerRed,
                            disabledContainerColor = DangerRed.copy(alpha = 0.5f)
                        )
                    ) {
                        if (isDeleting) {
                            CircularProgressIndicator(
                                color    = Color.White,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Deleting…", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        } else {
                            Icon(Icons.Rounded.DeleteForever, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Delete My Account",
                                fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    OutlinedButton(
                        onClick  = { if (!isDeleting) { showDeleteStep2 = false; deleteError = null } },
                        enabled  = !isDeleting,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape    = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", color = SettingsGreen900, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Rounded.ArrowBackIosNew, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SettingsGreen900)
            )
        },
        containerColor = Color(0xFFF0F4F0)
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Account section ───────────────────────────────────────────────
            SettingsSectionHeader("Account")
            SettingsGroup {
                SettingsRow(Icons.Rounded.Person,        "Edit Profile",       SettingsGreen700) { navController.navigateUp() }
                HorizontalDivider(color = Color(0xFFE0E8E0))
                SettingsRow(Icons.Rounded.Notifications, "Notifications",      SettingsGreen700) { }
                HorizontalDivider(color = Color(0xFFE0E8E0))
                SettingsRow(Icons.Rounded.Lock,          "Privacy & Security", SettingsGreen700) { }
            }

            // ── App section ───────────────────────────────────────────────────
            SettingsSectionHeader("App")
            SettingsGroup {
                SettingsRow(Icons.Rounded.Palette,  "Appearance", SettingsGreen700) { }
                HorizontalDivider(color = Color(0xFFE0E8E0))
                SettingsRow(Icons.Rounded.Language, "Language",   SettingsGreen700) { }
                HorizontalDivider(color = Color(0xFFE0E8E0))
                SettingsRow(Icons.Rounded.Info,     "About",      SettingsGreen700) { }
            }

            // ── Danger zone ───────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            SettingsSectionHeader("Danger Zone")
            SettingsGroup {
                SettingsRow(
                    icon        = Icons.Rounded.Logout,
                    label       = "Log Out",
                    tint        = DangerRed,
                    showChevron = false
                ) {
                    showLogoutDialog = true
                }
                HorizontalDivider(color = Color(0xFFFFE0E0))
                SettingsRow(
                    icon        = Icons.Rounded.DeleteForever,
                    label       = "Delete Account",
                    tint        = DangerRed,
                    showChevron = false
                ) {
                    showDeleteStep1 = true
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Deletes all user data from Firestore then removes the Auth account ────────
// Returns null on success, or an error message string on failure.
private suspend fun deleteAccountFromFirebase(): String? {
    return try {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
            ?: return "No authenticated user found. Please log in again."

        val db    = FirebaseFirestore.getInstance()
        val uid   = user.uid

        // ── Resolve username from Firestore (all docs use username, not uid) ──
        val userDocSnap = db.collection("users")
            .whereEqualTo("email", user.email ?: "")
            .limit(1)
            .get().await()
        val userDoc  = userDocSnap.documents.firstOrNull()
        val userName = userDoc?.getString("username") ?: ""

        suspend fun deleteCollection(collection: String, field: String, value: String) {
            if (value.isBlank()) return
            val snap = db.collection(collection).whereEqualTo(field, value).get().await()
            for (doc in snap.documents) doc.reference.delete().await()
        }

        // ── 1. Delete user profile document ──────────────────────────────────
        userDoc?.reference?.delete()?.await()

        // ── 2. Delete posts made by this user ─────────────────────────────────
        deleteCollection("posts", "userName", userName)

        // ── 3. Remove likes this user gave on other posts ─────────────────────
        //    Posts store likes as a count + a likedBy array — remove from array
        val likedPostsSnap = db.collection("posts")
            .whereArrayContains("likedBy", userName)
            .get().await()
        for (doc in likedPostsSnap.documents) {
            val currentLikes  = (doc.getLong("likes") ?: 1L) - 1L
            val likedBy       = (doc.get("likedBy") as? List<*>)
                ?.filterIsInstance<String>()
                ?.filter { it != userName }
                ?: emptyList()
            doc.reference.update(
                mapOf(
                    "likes"   to maxOf(currentLikes, 0L),
                    "likedBy" to likedBy
                )
            ).await()
        }

        // ── 4. Delete comments made by this user ──────────────────────────────
        deleteCollection("comments", "userName", userName)

        // ── 5. Delete alerts sent by this user ────────────────────────────────
        deleteCollection("alerts", "riderName", userName)

        // ── 6. Delete notifications for this user ─────────────────────────────
        deleteCollection("notifications", "userName", userName)

        // ── 7. Remove user from rideEvents participant lists ──────────────────
        val eventsSnap = db.collection("rideEvents")
            .whereArrayContains("participants", userName)
            .get().await()
        for (doc in eventsSnap.documents) {
            val updated = (doc.get("participants") as? List<*>)
                ?.filterIsInstance<String>()
                ?.filter { it != userName }
                ?: emptyList()
            doc.reference.update("participants", updated).await()
        }

        // ── 8. Delete ride events organized by this user ──────────────────────
        deleteCollection("rideEvents", "organizer", userName)

        // ── 9. Delete saved routes ────────────────────────────────────────────
        deleteCollection("savedRoutes", "userName", userName)

        // ── 10. Delete user location document ────────────────────────────────
        if (userName.isNotBlank()) {
            db.collection("userLocations").document(userName).delete().await()
        }

        // ── 11. Delete the Firebase Auth account itself ───────────────────────
        user.delete().await()

        null as String?
    } catch (e: FirebaseAuthRecentLoginRequiredException) {
        "For security, please log out and log back in before deleting your account."
    } catch (e: Exception) {
        "Failed to delete account: ${e.message}"
    }
}

// ── Small row item inside the warning checklist ───────────────────────────────
@Composable
private fun DeleteWarningItem(text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Rounded.RemoveCircleOutline,
            contentDescription = null,
            tint     = DangerRed,
            modifier = Modifier.size(15.dp).padding(top = 2.dp)
        )
        Text(
            text,
            fontSize   = 13.sp,
            color      = Color(0xFF5D1A1A),
            lineHeight = 18.sp
        )
    }
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title,
        fontSize      = 12.sp,
        fontWeight    = FontWeight.SemiBold,
        color         = Color(0xFF7A8F7A),
        letterSpacing = 0.8.sp,
        modifier      = Modifier.padding(start = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(
    icon        : ImageVector,
    label       : String,
    tint        : Color,
    showChevron : Boolean = true,
    onClick     : () -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier         = Modifier
                .size(36.dp)
                .background(tint.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 15.sp, color = Color(0xFF1A1A1A), modifier = Modifier.weight(1f))
        if (showChevron) {
            Icon(Icons.Rounded.ChevronRight, null,
                tint = Color(0xFFBBBBBB), modifier = Modifier.size(20.dp))
        }
    }
}