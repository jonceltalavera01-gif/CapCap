package com.darkhorses.PedalConnect.ui.theme

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.darkhorses.PedalConnect.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await


private val LoginGreen900 = Color(0xFF06402B)
private val LoginGreen700 = Color(0xFF0A5C3D)

internal const val PREFS_NAME          = "PedalConnectPrefs"
internal const val KEY_SAVED_USER_NAME = "saved_user_name"
internal const val KEY_SAVED_EMAIL     = "saved_email"
internal const val KEY_SAVED_CREATED_AT = "saved_created_at"
private  const val KEY_FAIL_COUNT      = "fail_count"
private  const val KEY_LOCKOUT_UNTIL   = "lockout_until"

private const val MAX_ATTEMPTS    = 5
private const val LOCKOUT_SECONDS = 30L

// ── Logout logic updated to sign out from Google as well ──────────────────────
fun logoutAndNavigate(context: Context, navController: NavController) {
    // 1. Clear SharedPreferences
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .remove(KEY_SAVED_USER_NAME)
        .remove(KEY_SAVED_EMAIL)
        .remove(KEY_SAVED_CREATED_AT)
        .remove(KEY_FAIL_COUNT)
        .remove(KEY_LOCKOUT_UNTIL)
        .apply()

    // 2. Sign out from Firebase
    FirebaseAuth.getInstance().signOut()

    // 3. Sign out from Google (Crucial for account selection)
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
    val googleSignInClient = GoogleSignIn.getClient(context, gso)
    googleSignInClient.signOut().addOnCompleteListener {
        // 4. Navigate to login
        navController.navigate("login") {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }
}

@Composable
fun LoginScreen(navController: NavController, paddingValues: PaddingValues) {
    val context = LocalContext.current
    val db      = FirebaseFirestore.getInstance()
    val auth    = FirebaseAuth.getInstance()
    val prefs   = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var email           by remember { mutableStateOf(prefs.getString(KEY_SAVED_EMAIL, "") ?: "") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading       by remember { mutableStateOf(false) }

    var emailError    by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf("") }
    var generalError  by remember { mutableStateOf("") }

    var failCount      by remember { mutableStateOf(prefs.getInt(KEY_FAIL_COUNT, 0)) }
    var lockoutUntil   by remember { mutableStateOf(prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)) }
    var lockoutSeconds by remember { mutableStateOf(0L) }
    val isLockedOut    = lockoutSeconds > 0L

    val resetLockout = {
        failCount = 0
        lockoutUntil = 0L
        prefs.edit().remove(KEY_FAIL_COUNT).remove(KEY_LOCKOUT_UNTIL).apply()
    }

    LaunchedEffect(lockoutUntil) {
        while (true) {
            val remaining = (lockoutUntil - System.currentTimeMillis()) / 1000L
            lockoutSeconds = if (remaining > 0) remaining else 0L
            if (lockoutSeconds <= 0L) break
            delay(1000L)
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            isLoading = true
            auth.signInWithCredential(credential).addOnCompleteListener { authTask ->
                if (authTask.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    val userEmail = firebaseUser?.email ?: ""

                    db.collection("users").whereEqualTo("email", userEmail).get()
                        .addOnSuccessListener { docs ->
                            if (!docs.isEmpty) {
                                val userDoc = docs.documents[0]
                                val name = userDoc.getString("username") ?: "Rider"
                                val createdAt = userDoc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                                
                                resetLockout()
                                prefs.edit()
                                    .putString(KEY_SAVED_USER_NAME, name)
                                    .putString(KEY_SAVED_EMAIL, userEmail)
                                    .putLong(KEY_SAVED_CREATED_AT, createdAt)
                                    .apply()
                                
                                isLoading = false
                                navController.navigate("home/$name") { popUpTo(0) { inclusive = true } }
                            } else {
                                val newName = firebaseUser?.displayName ?: "Rider"
                                val now = com.google.firebase.Timestamp.now()
                                val newUser = hashMapOf(
                                    "username" to newName, 
                                    "email" to userEmail, 
                                    "createdAt" to now
                                )
                                db.collection("users").add(newUser).addOnSuccessListener {
                                    resetLockout()
                                    prefs.edit()
                                        .putString(KEY_SAVED_USER_NAME, newName)
                                        .putString(KEY_SAVED_EMAIL, userEmail)
                                        .putLong(KEY_SAVED_CREATED_AT, now.toDate().time)
                                        .apply()
                                    isLoading = false
                                    navController.navigate("home/$newName") { popUpTo(0) { inclusive = true } }
                                }.addOnFailureListener {
                                    isLoading = false
                                    generalError = "Profile creation failed."
                                }
                            }
                        }
                } else {
                    isLoading = false
                    generalError = "Firebase Auth failed."
                }
            }
        } catch (e: ApiException) {
            isLoading = false
            if (e.statusCode != 12501) {
                generalError = "Google Error (${e.statusCode}): Please check your Firebase settings."
            }
        }
    }

    fun handleGoogleSignIn() {
        try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId == 0) {
                generalError = "Configuration error: Missing Web Client ID. Update google-services.json."
                return
            }
            val webClientId = context.getString(resId)
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            val googleSignInClient = GoogleSignIn.getClient(context, gso)

            // To force account selection every time, we sign out first
            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        } catch (e: Exception) {
            generalError = "Sign-in failed to initialize."
        }
    }

    val focusManager       = LocalFocusManager.current
    val passwordFocus      = remember { FocusRequester() }

    var visible  by remember { mutableStateOf(false) }
    var visible2 by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(50)
        visible = true
        delay(150)
        visible2 = true
        val saved = prefs.getString(KEY_SAVED_USER_NAME, null)
        if (!saved.isNullOrBlank()) {
            navController.navigate("home/$saved") { popUpTo(0) { inclusive = true } }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color(0xFF021F14),
                    0.45f to LoginGreen900,
                    1.0f to Color(0xFF1A9E6E)
                )
            ))
            .padding(paddingValues)
    ) {
        Column(
            modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { -40 }) {
                Column(
                    modifier            = Modifier.fillMaxWidth().padding(top = 56.dp, bottom = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.foundation.Image(
                        painter            = painterResource(id = R.drawable.pedalconnect_logo),
                        contentDescription = "PedalConnect Logo",
                        modifier           = Modifier.size(110.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text("PedalConnect", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("Ride together, ride stronger", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                }
            }

            AnimatedVisibility(visible2, enter = fadeIn() + slideInVertically { 80 }) {
                Card(
                    modifier  = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
                    shape     = RoundedCornerShape(24.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                    elevation = CardDefaults.cardElevation(12.dp)
                ) {
                    Column(
                        modifier            = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Welcome Back", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = LoginGreen900)
                        Spacer(Modifier.height(24.dp))

                        if (generalError.isNotEmpty()) {
                            Text(generalError, color = Color.Red, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
                        }

                        if (isLockedOut) {
                            Text("Locked out. Try again in ${lockoutSeconds}s", color = Color.Red, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
                        }

                        LoginFieldLabel("Email", LoginGreen900)
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; emailError = ""; generalError = "" },
                            placeholder = { Text("your@email.com") },
                            modifier = Modifier.fillMaxWidth(),
                            isError = emailError.isNotEmpty(),
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                            colors = loginFieldColors()
                        )
                        if (emailError.isNotEmpty()) {
                            Text(emailError, color = Color(0xFFDC2626), fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 2.dp))
                        }
                        Spacer(Modifier.height(16.dp))

                        LoginFieldLabel("Password", LoginGreen900)
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; passwordError = ""; generalError = "" },
                            placeholder = { Text("Enter your password", color = Color(0xFFB0BEC5)) },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus),
                            isError = passwordError.isNotEmpty(),
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            colors = loginFieldColors()
                        )
                        if (passwordError.isNotEmpty()) {
                            Text(passwordError, color = Color(0xFFDC2626), fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 2.dp))
                        }

                        Spacer(Modifier.height(28.dp))

                        Button(
                            onClick = {
                                if (isLockedOut) return@Button
                                // Client-side validation
                                var valid = true
                                if (email.trim().isEmpty()) {
                                    emailError = "Email is required"; valid = false
                                } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                                    emailError = "Enter a valid email address"; valid = false
                                }
                                if (password.isEmpty()) {
                                    passwordError = "Password is required"; valid = false
                                } else if (password.length < 6) {
                                    passwordError = "Password must be at least 6 characters"; valid = false
                                }
                                if (!valid) return@Button
                                focusManager.clearFocus()
                                isLoading = true
                                db.collection("users").whereEqualTo("email", email.trim()).whereEqualTo("password", password).get()
                                    .addOnSuccessListener { docs ->
                                        isLoading = false
                                        if (!docs.isEmpty) {
                                            resetLockout()
                                            val userDoc = docs.documents[0]
                                            val name = userDoc.getString("username") ?: "Rider"
                                            val createdAt = userDoc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                                            
                                            prefs.edit()
                                                .putString(KEY_SAVED_USER_NAME, name)
                                                .putString(KEY_SAVED_EMAIL, email.trim())
                                                .putLong(KEY_SAVED_CREATED_AT, createdAt)
                                                .apply()
                                                
                                            navController.navigate("home/$name") { popUpTo(0) { inclusive = true } }
                                        } else {
                                            failCount++
                                            if (failCount >= MAX_ATTEMPTS) {
                                                lockoutUntil = System.currentTimeMillis() + LOCKOUT_SECONDS * 1000L
                                                prefs.edit().putInt(KEY_FAIL_COUNT, failCount).putLong(KEY_LOCKOUT_UNTIL, lockoutUntil).apply()
                                            }
                                            generalError = "Invalid credentials"
                                        }
                                    }.addOnFailureListener { isLoading = false; generalError = "Network error" }
                            },
                            enabled = !isLoading && !isLockedOut,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LoginGreen900)
                        ) {
                            if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            else Text("Login", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.height(24.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFCDD8CD))
                            Text("OR", modifier = Modifier.padding(horizontal = 16.dp), fontSize = 12.sp, color = Color(0xFF7A8F7A))
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFCDD8CD))
                        }
                        Spacer(Modifier.height(24.dp))

                        OutlinedButton(
                            onClick = { if (!isLoading) handleGoogleSignIn() },
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, if (isLoading) Color(0xFFE5E7EB) else Color(0xFFCDD8CD))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GoogleLogo(modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Continue with Google", color = Color(0xFF111827), fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        // Combined text into one component to prevent breaking \"Sign Up\" across lines
                        val signUpText = buildAnnotatedString {
                            append("Don't have an account? ")
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = LoginGreen900)) {
                                append("Sign Up")
                            }
                        }
                        Text(
                            text = signUpText,
                            fontSize = 14.sp,
                            color = Color(0xFF4B5563),
                            modifier = Modifier.clickable { navController.navigate("register") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginFieldLabel(text: String, color: Color) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
}

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = LoginGreen900,
    unfocusedBorderColor    = Color(0xFFCDD8CD),
    focusedContainerColor   = Color.White,
    unfocusedContainerColor = Color.White,
    focusedTextColor        = Color(0xFF111827),
    unfocusedTextColor      = Color(0xFF111827),
    errorTextColor          = Color(0xFF111827),
    cursorColor             = LoginGreen900
)

@Composable
private fun GoogleLogo(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w  = size.width
        val cx = w / 2f
        val cy = size.height / 2f
        val r  = w / 2f
        val ring = androidx.compose.ui.geometry.Size(r * 1.44f, r * 1.44f)
        val tl   = androidx.compose.ui.geometry.Offset(cx - r * 0.72f, cy - r * 0.72f)
        val sw   = r * 0.28f
        drawArc(Color(0xFF4285F4), -30f,  60f,  false, style = androidx.compose.ui.graphics.drawscope.Stroke(sw), topLeft = tl, size = ring)
        drawArc(Color(0xFF34A853),  90f,  90f,  false, style = androidx.compose.ui.graphics.drawscope.Stroke(sw), topLeft = tl, size = ring)
        drawArc(Color(0xFFFBBC05), 180f,  60f,  false, style = androidx.compose.ui.graphics.drawscope.Stroke(sw), topLeft = tl, size = ring)
        drawArc(Color(0xFFEA4335), 240f,  90f,  false, style = androidx.compose.ui.graphics.drawscope.Stroke(sw), topLeft = tl, size = ring)
        drawRect(Color(0xFF4285F4), topLeft = androidx.compose.ui.geometry.Offset(cx, cy - r * 0.18f), size = androidx.compose.ui.geometry.Size(r * 0.76f, r * 0.36f))
    }
}