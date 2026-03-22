package com.darkhorses.PedalConnect.ui.theme

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import kotlin.math.sin
import kotlin.math.cos

// ── Colour tokens ─────────────────────────────────────────────────────────────
private val LGreen950 = Color(0xFF021F14)
private val LGreen900 = Color(0xFF06402B)
private val LGreen800 = Color(0xFF0A5C3D)
private val LGreen700 = Color(0xFF0D7050)
private val LGreen500 = Color(0xFF1A9E6E)
private val LGreen100 = Color(0xFFDDF1E8)
private val LMuted    = Color(0xFF6B7280)
private val LError    = Color(0xFFDC2626)
private val LErrorBg  = Color(0xFFFFEBEE)

internal const val PREFS_NAME           = "PedalConnectPrefs"
internal const val KEY_SAVED_USER_NAME  = "saved_user_name"
internal const val KEY_SAVED_EMAIL      = "saved_email"
internal const val KEY_SAVED_CREATED_AT = "saved_created_at"
private  const val KEY_FAIL_COUNT       = "fail_count"
private  const val KEY_LOCKOUT_UNTIL    = "lockout_until"
private  const val MAX_ATTEMPTS         = 5
private  const val LOCKOUT_SECONDS      = 30L

// ── Email typo detection ──────────────────────────────────────────────────────
private val domainTypos = mapOf(
    "gmial.com"   to "gmail.com",  "gmai.com"    to "gmail.com",
    "gmal.com"    to "gmail.com",  "gmali.com"   to "gmail.com",
    "gnail.com"   to "gmail.com",  "gamil.com"   to "gmail.com",
    "gmail.con"   to "gmail.com",  "gmail.cpm"   to "gmail.com",
    "gmail.cm"    to "gmail.com",  "gmail.co"    to "gmail.com",
    "yahooo.com"  to "yahoo.com",  "yaho.com"    to "yahoo.com",
    "yahoo.con"   to "yahoo.com",  "yhaoo.com"   to "yahoo.com",
    "hotmai.com"  to "hotmail.com","hotmial.com" to "hotmail.com",
    "hotmail.con" to "hotmail.com","outloo.com"  to "outlook.com",
    "outlok.com"  to "outlook.com","outlook.con" to "outlook.com",
    "iclod.com"   to "icloud.com", "icloud.con"  to "icloud.com"
)

private fun detectEmailTypo(input: String): String {
    val trimmed = input.trim().lowercase()
    if (!trimmed.contains("@")) return ""
    val domain     = trimmed.substringAfter("@")
    val suggestion = domainTypos[domain] ?: return ""
    return trimmed.substringBefore("@") + "@" + suggestion
}

// ── Logout ────────────────────────────────────────────────────────────────────
fun logoutAndNavigate(context: Context, navController: NavController) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .remove(KEY_SAVED_USER_NAME).remove(KEY_SAVED_EMAIL)
        .remove(KEY_SAVED_CREATED_AT).remove(KEY_FAIL_COUNT)
        .remove(KEY_LOCKOUT_UNTIL).apply()
    FirebaseAuth.getInstance().signOut()
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
    GoogleSignIn.getClient(context, gso).signOut().addOnCompleteListener {
        navController.navigate("login") {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }
}

// ── Topographic background ────────────────────────────────────────────────────
@Composable
private fun TopoBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w         = size.width
        val h         = size.height
        val lineColor = Color(0xFF0D7050).copy(alpha = 0.18f)
        val stroke    = Stroke(width = 1.5f, cap = StrokeCap.Round)
        val levels    = 20
        for (i in 0 until levels) {
            val progress = i.toFloat() / levels.toFloat()
            val path     = Path()
            val baseY    = h * progress
            var started  = false
            val steps    = 120
            for (j in 0..steps) {
                val x     = w * j.toFloat() / steps.toFloat()
                val wave1 = sin(x * 0.008f + progress * 8f) * h * 0.055f
                val wave2 = cos(x * 0.005f + progress * 5f) * h * 0.035f
                val wave3 = sin(x * 0.012f + progress * 3f) * h * 0.025f
                val y     = baseY + wave1 + wave2 + wave3
                if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
            }
            drawPath(path, lineColor, style = stroke)
        }
        val dotColor   = Color(0xFF1A9E6E).copy(alpha = 0.05f)
        val dotSpacing = 32f
        var dotX = 0f
        while (dotX < w) {
            var dotY = 0f
            while (dotY < h) {
                drawCircle(dotColor, radius = 1.5f, center = Offset(dotX, dotY))
                dotY += dotSpacing
            }
            dotX += dotSpacing
        }
    }
}

// ── Password strength ─────────────────────────────────────────────────────────
private enum class PwStrength(val label: String, val color: Color, val segments: Int) {
    WEAK(  "Weak",   Color(0xFFD32F2F), 1),
    FAIR(  "Fair",   Color(0xFFF57C00), 2),
    GOOD(  "Good",   Color(0xFFFBC02D), 3),
    STRONG("Strong", Color(0xFF388E3C), 4)
}

private fun evaluatePasswordStrength(password: String): PwStrength? {
    if (password.isEmpty()) return null
    var score = 0
    if (password.length >= 8)  score++
    if (password.length >= 12) score++
    if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    return when {
        score <= 1 -> PwStrength.WEAK
        score == 2 -> PwStrength.FAIR
        score == 3 -> PwStrength.GOOD
        else       -> PwStrength.STRONG
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────
@Composable
fun LoginScreen(navController: NavController, paddingValues: PaddingValues) {
    val context = LocalContext.current
    val db      = FirebaseFirestore.getInstance()
    val auth    = FirebaseAuth.getInstance()
    val prefs   = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Animation state ───────────────────────────────────────────────────────
    // Phase 0 = splash (logo centered), Phase 1 = login UI revealed
    var phase        by remember { mutableIntStateOf(0) }
    var showLoginUI  by remember { mutableStateOf(false) }

    // Delay phase 1 UI slightly so crossfade doesn't overlap
    LaunchedEffect(phase) {
        if (phase == 1) {
            delay(150)
            showLoginUI = true
        } else {
            showLoginUI = false
        }
    }

    // Back button — phase 1 goes back to splash, phase 0 exits app
    val activity = LocalContext.current as? android.app.Activity
    androidx.activity.compose.BackHandler(enabled = phase == 1) {
        phase       = 0
        showLoginUI = false
    }

    // Animated vertical offset for logo — starts at 0 (centered), moves to top
    val logoOffsetY by animateDpAsState(
        targetValue  = if (phase == 1) 0.dp else 0.dp,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label        = "logoOffset"
    )

    // Logo scale — slightly larger on splash, normal on login
    val logoScale by animateFloatAsState(
        targetValue   = if (phase == 1) 1f else 1.15f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label         = "logoScale"
    )

    // ── Tab state ─────────────────────────────────────────────────────────────
    var selectedTab by remember { mutableIntStateOf(0) }

    // ── Login state ───────────────────────────────────────────────────────────
    var loginEmail           by remember { mutableStateOf(prefs.getString(KEY_SAVED_EMAIL, "") ?: "") }
    var loginPassword        by remember { mutableStateOf("") }
    var loginPasswordVisible by remember { mutableStateOf(false) }
    var loginEmailError      by remember { mutableStateOf("") }
    var loginPasswordError   by remember { mutableStateOf("") }
    var loginGeneralError    by remember { mutableStateOf("") }
    var isLoginLoading       by remember { mutableStateOf(false) }

    var failCount      by remember { mutableStateOf(prefs.getInt(KEY_FAIL_COUNT, 0)) }
    var lockoutUntil   by remember { mutableStateOf(prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)) }
    var lockoutSeconds by remember { mutableStateOf(0L) }
    val isLockedOut    = lockoutSeconds > 0L

    val resetLockout = {
        failCount = 0; lockoutUntil = 0L
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

    // ── Register state ────────────────────────────────────────────────────────
    var regUsername               by remember { mutableStateOf("") }
    var regEmail                  by remember { mutableStateOf("") }
    var regPassword               by remember { mutableStateOf("") }
    var regConfirmPassword        by remember { mutableStateOf("") }
    var regPasswordVisible        by remember { mutableStateOf(false) }
    var regConfirmPasswordVisible by remember { mutableStateOf(false) }
    var regUsernameError          by remember { mutableStateOf("") }
    var regEmailError             by remember { mutableStateOf("") }
    var regPasswordError          by remember { mutableStateOf("") }
    var regConfirmError           by remember { mutableStateOf("") }
    var regGeneralError           by remember { mutableStateOf("") }
    var isRegLoading              by remember { mutableStateOf(false) }
    var termsAccepted             by remember { mutableStateOf(false) }
    var termsError                by remember { mutableStateOf("") }
    var usernameAvailable         by remember { mutableStateOf<Boolean?>(null) }
    var emailAvailable            by remember { mutableStateOf<Boolean?>(null) }
    var emailTypoSuggestion       by remember { mutableStateOf("") }
    val passwordStrength          = evaluatePasswordStrength(regPassword)

    // ── Focus ─────────────────────────────────────────────────────────────────
    val focusManager    = LocalFocusManager.current
    val loginPassFocus  = remember { FocusRequester() }
    val regEmailFocus   = remember { FocusRequester() }
    val regPassFocus    = remember { FocusRequester() }
    val regConfirmFocus = remember { FocusRequester() }

    // ── Auto-login ────────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        val saved = prefs.getString(KEY_SAVED_USER_NAME, null)
        if (!saved.isNullOrBlank()) {
            navController.navigate("home/$saved") { popUpTo(0) { inclusive = true } }
        }
    }

    // ── Google Sign-In ────────────────────────────────────────────────────────
    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account    = task.getResult(ApiException::class.java)!!
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            isLoginLoading = true
            auth.signInWithCredential(credential).addOnCompleteListener { authTask ->
                if (authTask.isSuccessful) {
                    val userEmail = auth.currentUser?.email ?: ""
                    db.collection("users").whereEqualTo("email", userEmail).get()
                        .addOnSuccessListener { docs ->
                            if (!docs.isEmpty) {
                                val doc       = docs.documents[0]
                                val name      = doc.getString("username") ?: "Rider"
                                val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                                resetLockout()
                                prefs.edit()
                                    .putString(KEY_SAVED_USER_NAME, name)
                                    .putString(KEY_SAVED_EMAIL, userEmail)
                                    .putLong(KEY_SAVED_CREATED_AT, createdAt).apply()
                                isLoginLoading = false
                                navController.navigate("home/$name") { popUpTo(0) { inclusive = true } }
                            } else {
                                val newName = auth.currentUser?.displayName ?: "Rider"
                                val now     = com.google.firebase.Timestamp.now()
                                db.collection("users").add(hashMapOf(
                                    "username"      to newName,
                                    "usernameLower" to newName.lowercase(),
                                    "email"         to userEmail,
                                    "createdAt"     to now
                                )).addOnSuccessListener {
                                    resetLockout()
                                    prefs.edit()
                                        .putString(KEY_SAVED_USER_NAME, newName)
                                        .putString(KEY_SAVED_EMAIL, userEmail)
                                        .putLong(KEY_SAVED_CREATED_AT, now.toDate().time).apply()
                                    isLoginLoading = false
                                    navController.navigate("home/$newName") { popUpTo(0) { inclusive = true } }
                                }.addOnFailureListener {
                                    isLoginLoading = false
                                    loginGeneralError = "Profile creation failed."
                                }
                            }
                        }
                } else {
                    isLoginLoading = false
                    loginGeneralError = "Firebase Auth failed."
                }
            }
        } catch (e: ApiException) {
            isLoginLoading = false
            if (e.statusCode != 12501)
                loginGeneralError = "Google Sign-In failed (${e.statusCode})"
        }
    }

    fun handleGoogleSignIn() {
        try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId == 0) { loginGeneralError = "Missing Web Client ID"; return }
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(resId)).requestEmail().build()
            val client = GoogleSignIn.getClient(context, gso)
            client.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(client.signInIntent)
            }
        } catch (e: Exception) { loginGeneralError = "Sign-in failed to initialize." }
    }

    // ── Login action ──────────────────────────────────────────────────────────
    fun doLogin() {
        if (isLockedOut) return
        loginEmailError = ""; loginPasswordError = ""; loginGeneralError = ""
        var valid = true
        if (loginEmail.trim().isEmpty()) { loginEmailError = "Email is required"; valid = false }
        else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(loginEmail.trim()).matches()) {
            loginEmailError = "Enter a valid email"; valid = false
        }
        if (loginPassword.isEmpty()) { loginPasswordError = "Password is required"; valid = false }
        if (!valid) return
        focusManager.clearFocus()
        isLoginLoading = true
        auth.signInWithEmailAndPassword(loginEmail.trim(), loginPassword)
            .addOnSuccessListener {
                db.collection("users").whereEqualTo("email", loginEmail.trim()).limit(1).get()
                    .addOnSuccessListener { docs ->
                        isLoginLoading = false
                        if (!docs.isEmpty) {
                            resetLockout()
                            val doc       = docs.documents[0]
                            val name      = doc.getString("username") ?: "Rider"
                            val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                            prefs.edit()
                                .putString(KEY_SAVED_USER_NAME, name)
                                .putString(KEY_SAVED_EMAIL, loginEmail.trim())
                                .putLong(KEY_SAVED_CREATED_AT, createdAt).apply()
                            navController.navigate("home/$name") { popUpTo(0) { inclusive = true } }
                        } else {
                            loginGeneralError = "Account not found. Please register."
                        }
                    }
                    .addOnFailureListener { isLoginLoading = false; loginGeneralError = "Network error" }
            }
            .addOnFailureListener { e ->
                isLoginLoading = false
                failCount++
                if (failCount >= MAX_ATTEMPTS) {
                    lockoutUntil = System.currentTimeMillis() + LOCKOUT_SECONDS * 1000L
                    prefs.edit().putInt(KEY_FAIL_COUNT, failCount)
                        .putLong(KEY_LOCKOUT_UNTIL, lockoutUntil).apply()
                }
                loginGeneralError = when {
                    e.message?.contains("password", ignoreCase = true) == true -> "Invalid email or password"
                    e.message?.contains("user", ignoreCase = true) == true     -> "No account found with this email"
                    e.message?.contains("network", ignoreCase = true) == true  -> "Network error"
                    else -> "Invalid email or password"
                }
            }
    }

    // ── Register action ───────────────────────────────────────────────────────
    fun doRegister() {
        regUsernameError = ""; regEmailError = ""; regPasswordError = ""
        regConfirmError  = ""; regGeneralError = ""; termsError = ""
        var valid        = true
        val trimUser     = regUsername.trim()
        val trimEmail    = regEmail.trim()
        when {
            trimUser.isBlank()   -> { regUsernameError = "Username is required"; valid = false }
            trimUser.length < 3  -> { regUsernameError = "At least 3 characters"; valid = false }
            trimUser.length > 30 -> { regUsernameError = "Max 30 characters"; valid = false }
            !trimUser.all { it.isLetterOrDigit() || it == '_' || it == '.' } ->
            { regUsernameError = "Only letters, numbers, _ and ."; valid = false }
        }
        when {
            trimEmail.isBlank() -> { regEmailError = "Email is required"; valid = false }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(trimEmail).matches() ->
            { regEmailError = "Enter a valid email"; valid = false }
        }
        when {
            regPassword.isEmpty()  -> { regPasswordError = "Password is required"; valid = false }
            regPassword.length < 8 -> { regPasswordError = "Min 8 characters"; valid = false }
            !regPassword.any { it.isUpperCase() } ->
            { regPasswordError = "Include at least one uppercase letter"; valid = false }
            !regPassword.any { it.isDigit() } ->
            { regPasswordError = "Include at least one number"; valid = false }
        }
        when {
            regConfirmPassword.isBlank()      -> { regConfirmError = "Please confirm password"; valid = false }
            regPassword != regConfirmPassword -> { regConfirmError = "Passwords do not match"; valid = false }
        }
        if (!termsAccepted) { termsError = "You must accept the terms"; valid = false }
        if (!valid) return
        focusManager.clearFocus()
        isRegLoading = true
        db.collection("users").whereEqualTo("usernameLower", trimUser.lowercase()).limit(1).get()
            .addOnSuccessListener { existingUser ->
                if (!existingUser.isEmpty) {
                    isRegLoading = false; regUsernameError = "Username already taken"; return@addOnSuccessListener
                }
                db.collection("users").whereEqualTo("email", trimEmail).limit(1).get()
                    .addOnSuccessListener { existingEmail ->
                        if (!existingEmail.isEmpty) {
                            isRegLoading = false; regEmailError = "Email already registered"; return@addOnSuccessListener
                        }
                        auth.createUserWithEmailAndPassword(trimEmail, regPassword)
                            .addOnSuccessListener {
                                val now = com.google.firebase.Timestamp.now()
                                db.collection("users").add(hashMapOf(
                                    "username"      to trimUser,
                                    "usernameLower" to trimUser.lowercase(),
                                    "email"         to trimEmail,
                                    "createdAt"     to now
                                )).addOnSuccessListener {
                                    isRegLoading = false
                                    prefs.edit()
                                        .putString(KEY_SAVED_USER_NAME, trimUser)
                                        .putString(KEY_SAVED_EMAIL, trimEmail)
                                        .putLong(KEY_SAVED_CREATED_AT, now.toDate().time).apply()
                                    Toast.makeText(context, "Welcome to PedalConnect, $trimUser! 🚴", Toast.LENGTH_SHORT).show()
                                    navController.navigate("home/$trimUser") { popUpTo(0) { inclusive = true } }
                                }.addOnFailureListener {
                                    auth.currentUser?.delete()
                                    isRegLoading = false; regGeneralError = "Registration failed. Please try again."
                                }
                            }
                            .addOnFailureListener { e ->
                                isRegLoading = false
                                regGeneralError = when {
                                    e.message?.contains("already in use", ignoreCase = true) == true -> "Email already registered"
                                    e.message?.contains("network", ignoreCase = true) == true        -> "No internet connection"
                                    else -> "Registration failed. Please try again."
                                }
                            }
                    }
                    .addOnFailureListener { isRegLoading = false; regGeneralError = "Network error" }
            }
            .addOnFailureListener { isRegLoading = false; regGeneralError = "Network error" }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to LGreen950,
                        0.5f to LGreen900,
                        1.0f to LGreen800
                    )
                )
            )
            .padding(paddingValues)
            // Tap anywhere on the background (phase 0) to reveal the form
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null
            ) { if (phase == 0) phase = 1 }
    ) {
        // Topo background always visible
        TopoBackground(modifier = Modifier.fillMaxSize())

        // ── PHASE 0: Splash — logo perfectly centered ─────────────────────────
        AnimatedVisibility(
            visible = phase == 0,
            enter   = fadeIn(tween(400)),
            exit    = fadeOut(tween(300))
        ) {
            Box(
                modifier         = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    androidx.compose.foundation.Image(
                        painter            = painterResource(id = R.drawable.pedalconnect_logo),
                        contentDescription = "PedalConnect",
                        modifier           = Modifier.size(100.dp)
                    )
                    Text(
                        "PedalConnect",
                        fontSize      = 32.sp,
                        fontWeight    = FontWeight.ExtraBold,
                        color         = Color.White,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        "Ride together, ride stronger",
                        fontSize = 14.sp,
                        color    = Color.White.copy(alpha = 0.65f)
                    )
                    Spacer(Modifier.height(40.dp))
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue  = 0.3f,
                        targetValue   = 0.7f,
                        animationSpec = infiniteRepeatable(
                            animation  = tween(1200, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseAlpha"
                    )
                    // Subtle pulsing chevron — signals interactivity without words
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint     = Color.White.copy(alpha = pulseAlpha),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // ── PHASE 1: Login UI ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showLoginUI,
            enter   = fadeIn(tween(350)),
            exit    = fadeOut(tween(200))
        ) {
            Column(
                modifier            = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo slides to top
                Spacer(Modifier.height(52.dp))
                androidx.compose.foundation.Image(
                    painter            = painterResource(id = R.drawable.pedalconnect_logo),
                    contentDescription = "PedalConnect",
                    modifier           = Modifier.size(72.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "PedalConnect",
                    fontSize      = 26.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    color         = Color.White,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    "Ride together, ride stronger",
                    fontSize = 12.sp,
                    color    = Color.White.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(24.dp))

                // Tab switcher
                Box(
                    modifier = Modifier
                        .padding(horizontal = 48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(4.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("Login", "Sign Up").forEachIndexed { index, label ->
                            val isSelected = selectedTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color.White else Color.Transparent)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication        = null
                                    ) { selectedTab = index }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    fontSize   = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color      = if (isSelected) LGreen900 else Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Form card slides up
                Card(
                    modifier  = Modifier.fillMaxWidth().weight(1f),
                    shape     = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color(0xFFFAFDFB)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 28.dp)
                            .padding(top = 28.dp, bottom = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        // ── LOGIN TAB ─────────────────────────────────────────
                        AnimatedVisibility(
                            visible = selectedTab == 0,
                            enter   = fadeIn() + slideInHorizontally { -40 },
                            exit    = fadeOut() + slideOutHorizontally { -40 }
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {

                                if (loginGeneralError.isNotEmpty() || isLockedOut) {
                                    LErrorBanner(
                                        if (isLockedOut) "Too many attempts. Try again in ${lockoutSeconds}s"
                                        else loginGeneralError
                                    )
                                    Spacer(Modifier.height(16.dp))
                                }

                                LFieldLabel("Email")
                                OutlinedTextField(
                                    value         = loginEmail,
                                    onValueChange = { loginEmail = it; loginEmailError = ""; loginGeneralError = "" },
                                    placeholder   = { Text("your@email.com", color = Color(0xFFB0BEC5)) },
                                    leadingIcon   = { Icon(Icons.Rounded.AccountCircle, null, tint = LGreen700, modifier = Modifier.size(20.dp)) },
                                    isError       = loginEmailError.isNotEmpty(),
                                    singleLine    = true,
                                    enabled       = !isLoginLoading && !isLockedOut,
                                    shape         = RoundedCornerShape(14.dp),
                                    modifier      = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { loginPassFocus.requestFocus() }),
                                    colors        = lFieldColors()
                                )
                                if (loginEmailError.isNotEmpty()) LFieldError(loginEmailError)
                                Spacer(Modifier.height(16.dp))

                                LFieldLabel("Password")
                                OutlinedTextField(
                                    value                = loginPassword,
                                    onValueChange        = { loginPassword = it; loginPasswordError = ""; loginGeneralError = "" },
                                    placeholder          = { Text("Enter your password", color = Color(0xFFB0BEC5)) },
                                    leadingIcon          = { Icon(Icons.Rounded.Lock, null, tint = LGreen700, modifier = Modifier.size(20.dp)) },
                                    visualTransformation = if (loginPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon         = {
                                        IconButton(onClick = { loginPasswordVisible = !loginPasswordVisible }) {
                                            Icon(if (loginPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = LMuted)
                                        }
                                    },
                                    isError       = loginPasswordError.isNotEmpty(),
                                    singleLine    = true,
                                    enabled       = !isLoginLoading && !isLockedOut,
                                    shape         = RoundedCornerShape(14.dp),
                                    modifier      = Modifier.fillMaxWidth().focusRequester(loginPassFocus),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); doLogin() }),
                                    colors        = lFieldColors()
                                )
                                if (loginPasswordError.isNotEmpty()) LFieldError(loginPasswordError)
                                Spacer(Modifier.height(24.dp))

                                Button(
                                    onClick   = { doLogin() },
                                    enabled   = !isLoginLoading && !isLockedOut,
                                    modifier  = Modifier.fillMaxWidth().height(54.dp),
                                    shape     = RoundedCornerShape(14.dp),
                                    colors    = ButtonDefaults.buttonColors(
                                        containerColor         = LGreen900,
                                        contentColor           = Color.White,
                                        disabledContainerColor = LGreen900.copy(alpha = 0.4f),
                                        disabledContentColor   = Color.White
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(4.dp)
                                ) {
                                    if (isLoginLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    else Text("Login", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                Spacer(Modifier.height(20.dp))
                                LDivider()
                                Spacer(Modifier.height(20.dp))

                                OutlinedButton(
                                    onClick  = { if (!isLoginLoading) handleGoogleSignIn() },
                                    enabled  = !isLoginLoading,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape    = RoundedCornerShape(14.dp),
                                    border   = BorderStroke(1.dp, Color(0xFFDDE3DE)),
                                    colors   = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color(0xFF111827))
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        GoogleLogo(modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Text("Continue with Google", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }

                        // ── SIGN UP TAB ───────────────────────────────────────
                        AnimatedVisibility(
                            visible = selectedTab == 1,
                            enter   = fadeIn() + slideInHorizontally { 40 },
                            exit    = fadeOut() + slideOutHorizontally { 40 }
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {

                                if (regGeneralError.isNotEmpty()) {
                                    LErrorBanner(regGeneralError)
                                    Spacer(Modifier.height(16.dp))
                                }

                                LFieldLabel("Username")
                                OutlinedTextField(
                                    value         = regUsername,
                                    onValueChange = { if (it.length <= 30) { regUsername = it; regUsernameError = "" } },
                                    placeholder   = { Text("e.g. juandelacruz", color = Color(0xFFB0BEC5)) },
                                    leadingIcon   = { Icon(Icons.Rounded.Person, null, tint = LGreen700, modifier = Modifier.size(20.dp)) },
                                    trailingIcon  = {
                                        when (usernameAvailable) {
                                            true  -> Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF388E3C), modifier = Modifier.size(18.dp))
                                            false -> Icon(Icons.Rounded.Cancel, null, tint = LError, modifier = Modifier.size(18.dp))
                                            null  -> {}
                                        }
                                    },
                                    isError       = regUsernameError.isNotEmpty(),
                                    singleLine    = true,
                                    enabled       = !isRegLoading,
                                    shape         = RoundedCornerShape(14.dp),
                                    modifier      = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { regEmailFocus.requestFocus() }),
                                    colors        = lFieldColors()
                                )
                                if (regUsernameError.isNotEmpty()) LFieldError(regUsernameError)
                                Spacer(Modifier.height(14.dp))

                                LFieldLabel("Email")
                                OutlinedTextField(
                                    value         = regEmail,
                                    onValueChange = {
                                        if (it.length <= 254) {
                                            regEmail            = it
                                            regEmailError       = ""
                                            emailTypoSuggestion = detectEmailTypo(it)
                                        }
                                    },
                                    placeholder   = { Text("your@email.com", color = Color(0xFFB0BEC5)) },
                                    leadingIcon   = { Icon(Icons.Rounded.AccountCircle, null, tint = LGreen700, modifier = Modifier.size(20.dp)) },
                                    trailingIcon  = {
                                        when (emailAvailable) {
                                            true  -> Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF388E3C), modifier = Modifier.size(18.dp))
                                            false -> Icon(Icons.Rounded.Cancel, null, tint = LError, modifier = Modifier.size(18.dp))
                                            null  -> {}
                                        }
                                    },
                                    isError       = regEmailError.isNotEmpty(),
                                    singleLine    = true,
                                    enabled       = !isRegLoading,
                                    shape         = RoundedCornerShape(14.dp),
                                    modifier      = Modifier.fillMaxWidth().focusRequester(regEmailFocus),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { regPassFocus.requestFocus() }),
                                    colors        = lFieldColors()
                                )
                                if (regEmailError.isNotEmpty()) LFieldError(regEmailError)
                                if (emailTypoSuggestion.isNotEmpty() && regEmailError.isEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier          = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp)
                                    ) {
                                        Icon(Icons.Rounded.Info, null, tint = Color(0xFFF57C00), modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Did you mean ", fontSize = 12.sp, color = Color(0xFFF57C00))
                                        Text(
                                            emailTypoSuggestion,
                                            fontSize   = 12.sp,
                                            color      = LGreen900,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier   = Modifier.clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication        = null
                                            ) {
                                                regEmail            = emailTypoSuggestion
                                                emailTypoSuggestion = ""
                                                regEmailError       = ""
                                                emailAvailable      = null
                                            }
                                        )
                                        Text("?", fontSize = 12.sp, color = Color(0xFFF57C00))
                                    }
                                }
                                Spacer(Modifier.height(14.dp))

                                LFieldLabel("Password")
                                OutlinedTextField(
                                    value                = regPassword,
                                    onValueChange        = { if (it.length <= 128) { regPassword = it; regPasswordError = "" } },
                                    placeholder          = { Text("Min 8 chars, 1 uppercase, 1 number", color = Color(0xFFB0BEC5)) },
                                    leadingIcon          = { Icon(Icons.Rounded.Lock, null, tint = LGreen700, modifier = Modifier.size(20.dp)) },
                                    visualTransformation = if (regPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon         = {
                                        IconButton(onClick = { regPasswordVisible = !regPasswordVisible }) {
                                            Icon(if (regPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = LMuted)
                                        }
                                    },
                                    isError       = regPasswordError.isNotEmpty(),
                                    singleLine    = true,
                                    enabled       = !isRegLoading,
                                    shape         = RoundedCornerShape(14.dp),
                                    modifier      = Modifier.fillMaxWidth().focusRequester(regPassFocus),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { regConfirmFocus.requestFocus() }),
                                    colors        = lFieldColors()
                                )
                                if (regPassword.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    LPasswordStrengthMeter(passwordStrength)
                                }
                                if (regPasswordError.isNotEmpty()) LFieldError(regPasswordError)
                                Spacer(Modifier.height(14.dp))

                                LFieldLabel("Confirm Password")
                                OutlinedTextField(
                                    value                = regConfirmPassword,
                                    onValueChange        = { if (it.length <= 128) { regConfirmPassword = it; regConfirmError = "" } },
                                    placeholder          = { Text("Re-enter password", color = Color(0xFFB0BEC5)) },
                                    leadingIcon          = { Icon(Icons.Rounded.Lock, null, tint = LGreen700, modifier = Modifier.size(20.dp)) },
                                    visualTransformation = if (regConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon         = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (regConfirmPassword.isNotEmpty()) {
                                                Icon(
                                                    if (regPassword == regConfirmPassword) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                                                    null,
                                                    tint     = if (regPassword == regConfirmPassword) Color(0xFF388E3C) else LError,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(onClick = { regConfirmPasswordVisible = !regConfirmPasswordVisible }) {
                                                Icon(if (regConfirmPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = LMuted)
                                            }
                                        }
                                    },
                                    isError       = regConfirmError.isNotEmpty(),
                                    singleLine    = true,
                                    enabled       = !isRegLoading,
                                    shape         = RoundedCornerShape(14.dp),
                                    modifier      = Modifier.fillMaxWidth().focusRequester(regConfirmFocus),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                    colors        = lFieldColors()
                                )
                                if (regConfirmError.isNotEmpty()) LFieldError(regConfirmError)
                                Spacer(Modifier.height(16.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier          = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication        = null
                                    ) { termsAccepted = !termsAccepted }
                                ) {
                                    Checkbox(
                                        checked         = termsAccepted,
                                        onCheckedChange = { termsAccepted = it },
                                        colors          = CheckboxDefaults.colors(
                                            checkedColor   = LGreen900,
                                            uncheckedColor = if (termsError.isNotEmpty()) LError else Color(0xFFCDD8CD),
                                            checkmarkColor = Color.White
                                        ),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text("I agree to the ", fontSize = 13.sp, color = Color(0xFF444444))
                                    Text("Terms & Privacy Policy", fontSize = 13.sp,
                                        color = LGreen900, fontWeight = FontWeight.SemiBold)
                                }
                                if (termsError.isNotEmpty()) LFieldError(termsError)
                                Spacer(Modifier.height(20.dp))

                                Button(
                                    onClick   = { doRegister() },
                                    enabled   = !isRegLoading,
                                    modifier  = Modifier.fillMaxWidth().height(54.dp),
                                    shape     = RoundedCornerShape(14.dp),
                                    colors    = ButtonDefaults.buttonColors(
                                        containerColor         = LGreen900,
                                        contentColor           = Color.White,
                                        disabledContainerColor = LGreen900.copy(alpha = 0.4f),
                                        disabledContentColor   = Color.White
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(4.dp)
                                ) {
                                    if (isRegLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    else Text("Create Account", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Atoms ─────────────────────────────────────────────────────────────────────
@Composable
private fun LFieldLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LGreen900,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
}

@Composable
private fun LFieldError(error: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
        Icon(Icons.Rounded.Info, null, tint = LError, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(error, color = LError, fontSize = 12.sp)
    }
}

@Composable
private fun LErrorBanner(message: String) {
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LErrorBg),
        shape  = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, null, tint = LError, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(message, fontSize = 13.sp, color = LError)
        }
    }
}

@Composable
private fun LDivider() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE5EDE8))
        Text("OR", modifier = Modifier.padding(horizontal = 16.dp),
            fontSize = 12.sp, color = LMuted, fontWeight = FontWeight.Medium)
        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE5EDE8))
    }
}

@Composable
private fun LPasswordStrengthMeter(strength: PwStrength?) {
    if (strength == null) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(4) { index ->
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (index < strength.segments) strength.color else Color(0xFFE0E0E0)))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(strength.color))
            Spacer(Modifier.width(5.dp))
            Text("Password strength: ${strength.label}", fontSize = 11.sp,
                color = strength.color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun lFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor        = Color(0xFF1A1A1A),
    unfocusedTextColor      = Color(0xFF1A1A1A),
    errorTextColor          = Color(0xFF1A1A1A),
    cursorColor             = LGreen900,
    focusedBorderColor      = LGreen900,
    unfocusedBorderColor    = Color(0xFFCDD8CD),
    errorBorderColor        = LError,
    focusedContainerColor   = Color.White,
    unfocusedContainerColor = Color.White,
    errorContainerColor     = LErrorBg,
    disabledContainerColor  = Color(0xFFF0F0F0),
    disabledBorderColor     = Color(0xFFDDDDDD),
    disabledTextColor       = Color(0xFF9E9E9E)
)

@Composable
private fun GoogleLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val cx = w / 2f; val cy = size.height / 2f; val r = w / 2f
        val ring = androidx.compose.ui.geometry.Size(r * 1.44f, r * 1.44f)
        val tl   = androidx.compose.ui.geometry.Offset(cx - r * 0.72f, cy - r * 0.72f)
        val sw   = r * 0.28f
        drawArc(Color(0xFF4285F4), -30f, 60f,  false, style = Stroke(sw), topLeft = tl, size = ring)
        drawArc(Color(0xFF34A853),  90f, 90f,  false, style = Stroke(sw), topLeft = tl, size = ring)
        drawArc(Color(0xFFFBBC05), 180f, 60f,  false, style = Stroke(sw), topLeft = tl, size = ring)
        drawArc(Color(0xFFEA4335), 240f, 90f,  false, style = Stroke(sw), topLeft = tl, size = ring)
        drawRect(Color(0xFF4285F4),
            topLeft = androidx.compose.ui.geometry.Offset(cx, cy - r * 0.18f),
            size    = androidx.compose.ui.geometry.Size(r * 0.76f, r * 0.36f))
    }
}