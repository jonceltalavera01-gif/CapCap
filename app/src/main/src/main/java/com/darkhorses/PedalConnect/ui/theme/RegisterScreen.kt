package com.darkhorses.PedalConnect.ui.theme

import android.util.Patterns
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.darkhorses.PedalConnect.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore

// ── Colour tokens ─────────────────────────────────────────────────────────────
private val RegGreen900  = Color(0xFF06402B)
private val RegGreen700  = Color(0xFF0A5C3D)
private val RegSurfaceBg = Color(0xFFF5F7F5)

// ── Password strength ─────────────────────────────────────────────────────────
private enum class PasswordStrength(val label: String, val color: Color, val segments: Int) {
    WEAK(  "Weak",   Color(0xFFD32F2F), 1),
    FAIR(  "Fair",   Color(0xFFF57C00), 2),
    GOOD(  "Good",   Color(0xFFFBC02D), 3),
    STRONG("Strong", Color(0xFF388E3C), 4)
}

private fun evaluateStrength(password: String): PasswordStrength? {
    if (password.isEmpty()) return null
    var score = 0
    if (password.length >= 8)  score++
    if (password.length >= 12) score++
    if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    return when {
        score <= 1 -> PasswordStrength.WEAK
        score == 2 -> PasswordStrength.FAIR
        score == 3 -> PasswordStrength.GOOD
        else       -> PasswordStrength.STRONG
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────
@Composable
fun RegisterScreen(navController: NavController, paddingValues: PaddingValues) {
    val context = LocalContext.current
    val db      = FirebaseFirestore.getInstance()
    val prefs   = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    var username               by remember { mutableStateOf("") }
    var email                  by remember { mutableStateOf("") }
    var password               by remember { mutableStateOf("") }
    var confirmPassword        by remember { mutableStateOf("") }
    var passwordVisible        by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var isLoading              by remember { mutableStateOf(false) }
    var termsAccepted          by remember { mutableStateOf(false) }

    var usernameError        by remember { mutableStateOf("") }
    var emailError           by remember { mutableStateOf("") }
    var passwordError        by remember { mutableStateOf("") }
    var confirmPasswordError by remember { mutableStateOf("") }
    var termsError           by remember { mutableStateOf("") }
    var generalError         by remember { mutableStateOf("") }

    // Email live-check states
    var emailTypoSuggestion by remember { mutableStateOf("") }
    var emailCheckLoading   by remember { mutableStateOf(false) }
    var emailAvailable      by remember { mutableStateOf<Boolean?>(null) }

    // Username live-check states
    var usernameCheckLoading by remember { mutableStateOf(false) }
    var usernameAvailable    by remember { mutableStateOf<Boolean?>(null) }

    val passwordStrength = evaluateStrength(password)
    val focusManager        = LocalFocusManager.current
    val emailFocus          = remember { FocusRequester() }
    val passwordFocus       = remember { FocusRequester() }
    val confirmPasswordFocus = remember { FocusRequester() }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // ── Domain typo map ───────────────────────────────────────────────────────
    val domainTypos = mapOf(
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

    fun detectTypo(input: String): String {
        val trimmed = input.trim().lowercase()
        if (!trimmed.contains("@")) return ""
        val domain = trimmed.substringAfter("@")
        val suggestion = domainTypos[domain] ?: return ""
        return trimmed.substringBefore("@") + "@" + suggestion
    }

    // ── Focus-out: email duplicate check ──────────────────────────────────────
    fun checkEmailAvailability(trimEmail: String) {
        if (trimEmail.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(trimEmail).matches()) return
        emailCheckLoading = true
        emailAvailable    = null
        db.collection("users")
            .whereEqualTo("email", trimEmail)
            .limit(1)
            .get()
            .addOnSuccessListener { docs ->
                emailCheckLoading = false
                val taken = !docs.isEmpty
                emailAvailable = !taken
                if (taken) emailError = "An account with this email already exists"
                else if (emailError == "An account with this email already exists") emailError = ""
            }
            .addOnFailureListener {
                emailCheckLoading = false
                emailAvailable    = null
            }
    }

    // ── Focus-out: username uniqueness check ──────────────────────────────────
    fun checkUsernameAvailability(trimUsername: String) {
        if (trimUsername.isBlank()) return
        usernameCheckLoading = true
        usernameAvailable    = null
        db.collection("users")
            .whereEqualTo("usernameLower", trimUsername.lowercase())
            .limit(1)
            .get()
            .addOnSuccessListener { docs ->
                usernameCheckLoading = false
                val taken = !docs.isEmpty
                usernameAvailable = !taken
                if (taken) usernameError = "This username is already taken"
                else if (usernameError == "This username is already taken") usernameError = ""
            }
            .addOnFailureListener {
                usernameCheckLoading = false
                usernameAvailable    = null
            }
    }

    // ── Validation ────────────────────────────────────────────────────────────
    // retryCount intentionally NOT reset here — only resets on success
    fun validate(): Boolean {
        usernameError = ""; emailError = ""; passwordError = ""
        confirmPasswordError = ""; termsError = ""; generalError = ""
        var valid = true

        val trimUsername = username.trim()
        val trimEmail    = email.trim()

        when {
            trimUsername.isBlank()   -> { usernameError = "Username is required"; valid = false }
            trimUsername.length < 3  -> { usernameError = "Username must be at least 3 characters"; valid = false }
            trimUsername.length > 30 -> { usernameError = "Username must be 30 characters or less"; valid = false }
            !trimUsername.all { it.isLetterOrDigit() || it == '_' || it == '.' } ->
            { usernameError = "Only letters, numbers, _ and . are allowed"; valid = false }
            trimUsername.startsWith(".") || trimUsername.startsWith("_") ->
            { usernameError = "Username cannot start with . or _"; valid = false }
        }
        when {
            trimEmail.isBlank()    -> { emailError = "Email is required"; valid = false }
            !Patterns.EMAIL_ADDRESS.matcher(trimEmail).matches() ->
            { emailError = "Enter a valid email address"; valid = false }
            trimEmail.length > 254 -> { emailError = "Email is too long"; valid = false }
        }
        when {
            password.isEmpty()    -> { passwordError = "Password is required"; valid = false }
            password.length < 8   -> { passwordError = "Minimum 8 characters required"; valid = false }
            password.length > 128 -> { passwordError = "Password is too long"; valid = false }
            !password.any { it.isUpperCase() } ->
            { passwordError = "Include at least one uppercase letter"; valid = false }
            !password.any { it.isDigit() } ->
            { passwordError = "Include at least one number"; valid = false }
        }
        when {
            confirmPassword.isBlank()   -> { confirmPasswordError = "Please confirm your password"; valid = false }
            password != confirmPassword -> { confirmPasswordError = "Passwords do not match"; valid = false }
        }
        if (!termsAccepted) { termsError = "You must accept the terms to continue"; valid = false }

        return valid
    }

    // ── Registration flow ─────────────────────────────────────────────────────
    fun registerUser(trimUsername: String, trimEmail: String) {
        // Guard — short-circuit if focus-out checks already confirmed conflicts
        if (emailAvailable == false) {
            isLoading  = false
            emailError = "An account with this email already exists"
            return
        }
        if (usernameAvailable == false) {
            isLoading     = false
            usernameError = "This username is already taken"
            return
        }

        // Safety-net username check (covers skipped focus-out)
        db.collection("users")
            .whereEqualTo("usernameLower", trimUsername.lowercase())
            .limit(1)
            .get()
            .addOnSuccessListener { existingUsername ->
                if (!existingUsername.isEmpty) {
                    isLoading         = false
                    usernameAvailable = false
                    usernameError     = "This username is already taken"
                    return@addOnSuccessListener
                }

                // Safety-net email check (covers skipped focus-out)
                db.collection("users")
                    .whereEqualTo("email", trimEmail)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { existingEmail ->
                        if (!existingEmail.isEmpty) {
                            isLoading      = false
                            emailAvailable = false
                            emailError     = "An account with this email already exists"
                            return@addOnSuccessListener
                        }

                        // All clear — write user document
                        val now = com.google.firebase.Timestamp.now()
                        val user = hashMapOf(
                            "username"      to trimUsername,
                            "usernameLower" to trimUsername.lowercase(),
                            "email"         to trimEmail,
                            "password"      to password,  // TODO: replace with Firebase Auth
                            "createdAt"     to now
                        )
                        db.collection("users").add(user)
                            .addOnSuccessListener {
                                isLoading       = false
                                password        = ""
                                confirmPassword = ""

                                prefs.edit()
                                    .putString(KEY_SAVED_USER_NAME, trimUsername)
                                    .putString(KEY_SAVED_EMAIL, trimEmail)
                                    .putLong(KEY_SAVED_CREATED_AT, now.toDate().time)
                                    .apply()

                                Toast.makeText(
                                    context,
                                    "Welcome to PedalConnect, $trimUsername! 🚴",
                                    Toast.LENGTH_SHORT
                                ).show()

                                navController.navigate("home/$trimUsername") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                            .addOnFailureListener { e ->
                                isLoading       = false
                                password        = ""
                                confirmPassword = ""
                                generalError    = regErrorMessage(e)
                            }
                    }
                    .addOnFailureListener { e ->
                        isLoading = false

                        generalError = regErrorMessage(e)
                    }
            }
            .addOnFailureListener { e ->
                isLoading = false
                generalError = regErrorMessage(e)
            }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    // Card fills the full screen — no separate hero above, no scrolling on normal phones.
    // Hero is collapsed into a compact inline header at the top of the card.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color(0xFF021F14),
                    0.45f to RegGreen900,
                    1.0f to Color(0xFF1A9E6E)
                )
            ))
            .padding(paddingValues)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn() + slideInVertically { 60 },
            modifier = Modifier.fillMaxSize()
        ) {
            Card(
                modifier  = Modifier
                    .fillMaxSize()
                    .padding(top = 32.dp),
                shape     = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp,
                    bottomStart = 0.dp, bottomEnd = 0.dp),
                colors    = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                elevation = CardDefaults.cardElevation(12.dp)
            ) {
                Column(
                    modifier            = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp)
                        .padding(top = 28.dp, bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ── Back arrow + inline hero ──────────────────────────────
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        IconButton(
                            onClick  = { navController.navigateUp() },
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                                tint = RegGreen900)
                        }
                        Column(
                            modifier            = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            androidx.compose.foundation.Image(
                                painter            = painterResource(id = R.drawable.pedalconnect_logo),
                                contentDescription = "PedalConnect Logo",
                                modifier           = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("PedalConnect", fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold, color = RegGreen900)
                        }
                    }
                    Text("Create your account", fontSize = 13.sp,
                        color = Color(0xFF7A8F7A),
                        modifier = Modifier.padding(bottom = 24.dp))

                    // General error banner
                    if (generalError.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            colors   = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFEBEE)),
                            shape    = RoundedCornerShape(12.dp)
                        ) {
                            Row(Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Warning, null,
                                    tint     = Color(0xFFD32F2F),
                                    modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(generalError, fontSize = 13.sp,
                                    color = Color(0xFFD32F2F))
                            }
                        }
                    }

                    // ── Username ──────────────────────────────────────────
                    RegFieldLabel("Username")
                    OutlinedTextField(
                        value         = username,
                        onValueChange = { raw ->
                            if (raw.length <= 30) {
                                username          = raw
                                usernameError     = ""
                                usernameAvailable = null
                            }
                        },
                        leadingIcon  = { Icon(Icons.Rounded.Person, null, tint = RegGreen900) },
                        placeholder  = { Text("e.g. juandelacruz", color = Color.LightGray) },
                        trailingIcon = {
                            when {
                                usernameCheckLoading ->
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color       = RegGreen700
                                    )
                                usernameAvailable == true ->
                                    Icon(Icons.Rounded.CheckCircle, null,
                                        tint     = Color(0xFF388E3C),
                                        modifier = Modifier.size(20.dp))
                                usernameAvailable == false ->
                                    Icon(Icons.Rounded.Cancel, null,
                                        tint     = Color(0xFFD32F2F),
                                        modifier = Modifier.size(20.dp))
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { emailFocus.requestFocus() }),
                        isError    = usernameError.isNotEmpty(),
                        singleLine = true,
                        enabled    = !isLoading,
                        shape      = RoundedCornerShape(14.dp),
                        modifier   = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { fs ->
                                if (!fs.isFocused && username.isNotBlank()) {
                                    val t = username.trim()
                                    when {
                                        t.length < 3 ->
                                            usernameError = "Username must be at least 3 characters"
                                        !t.all { it.isLetterOrDigit() || it == '_' || it == '.' } ->
                                            usernameError = "Only letters, numbers, _ and . are allowed"
                                        t.startsWith(".") || t.startsWith("_") ->
                                            usernameError = "Username cannot start with . or _"
                                        else -> checkUsernameAvailability(t)
                                    }
                                }
                            },
                        colors     = regFieldColors()
                    )
                    RegFieldError(usernameError)
                    Spacer(Modifier.height(16.dp))

                    // ── Email ─────────────────────────────────────────────
                    RegFieldLabel("Email")
                    OutlinedTextField(
                        value         = email,
                        onValueChange = { raw ->
                            if (raw.length <= 254) {
                                email               = raw
                                emailError          = ""
                                emailAvailable      = null
                                emailTypoSuggestion = detectTypo(raw)
                            }
                        },
                        leadingIcon     = {
                            Icon(Icons.Rounded.AccountCircle, null, tint = RegGreen900)
                        },
                        placeholder     = { Text("your@email.com", color = Color.LightGray) },
                        trailingIcon    = {
                            when {
                                emailCheckLoading ->
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color       = RegGreen700
                                    )
                                emailAvailable == true ->
                                    Icon(Icons.Rounded.CheckCircle, null,
                                        tint     = Color(0xFF388E3C),
                                        modifier = Modifier.size(20.dp))
                                emailAvailable == false ->
                                    Icon(Icons.Rounded.Cancel, null,
                                        tint     = Color(0xFFD32F2F),
                                        modifier = Modifier.size(20.dp))
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction    = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                        isError         = emailError.isNotEmpty(),
                        singleLine      = true,
                        enabled         = !isLoading,
                        shape           = RoundedCornerShape(14.dp),
                        modifier        = Modifier
                            .fillMaxWidth()
                            .focusRequester(emailFocus)
                            .onFocusChanged { fs ->
                                if (!fs.isFocused && email.isNotBlank()) {
                                    val t = email.trim()
                                    if (!Patterns.EMAIL_ADDRESS.matcher(t).matches())
                                        emailError = "Enter a valid email address"
                                    else
                                        checkEmailAvailability(t)
                                }
                            },
                        colors          = regFieldColors()
                    )
                    if (emailTypoSuggestion.isNotEmpty() && emailError.isEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier          = Modifier.fillMaxWidth()
                                .padding(start = 4.dp, top = 4.dp)
                        ) {
                            Icon(Icons.Rounded.Info, null,
                                tint     = Color(0xFFF57C00),
                                modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Did you mean ", fontSize = 12.sp, color = Color(0xFFF57C00))
                            Text(
                                emailTypoSuggestion,
                                fontSize   = 12.sp,
                                color      = RegGreen900,
                                fontWeight = FontWeight.SemiBold,
                                modifier   = Modifier.clickable {
                                    email               = emailTypoSuggestion
                                    emailTypoSuggestion = ""
                                    emailError          = ""
                                    emailAvailable      = null
                                }
                            )
                            Text("?", fontSize = 12.sp, color = Color(0xFFF57C00))
                        }
                    }
                    RegFieldError(emailError)
                    Spacer(Modifier.height(16.dp))

                    // ── Password ──────────────────────────────────────────
                    RegFieldLabel("Password")
                    OutlinedTextField(
                        value         = password,
                        onValueChange = {
                            if (it.length <= 128) { password = it; passwordError = "" }
                        },
                        leadingIcon          = {
                            Icon(Icons.Rounded.Lock, null, tint = RegGreen900)
                        },
                        placeholder          = {
                            Text("Min. 8 chars, 1 uppercase, 1 number",
                                color = Color.LightGray)
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon         = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Rounded.VisibilityOff
                                    else Icons.Rounded.Visibility,
                                    if (passwordVisible) "Hide" else "Show",
                                    tint = RegGreen700
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { confirmPasswordFocus.requestFocus() }),
                        isError    = passwordError.isNotEmpty(),
                        singleLine = true,
                        enabled    = !isLoading,
                        shape      = RoundedCornerShape(14.dp),
                        modifier   = Modifier.fillMaxWidth().focusRequester(passwordFocus),
                        colors     = regFieldColors()
                    )
                    if (password.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        PasswordStrengthMeter(strength = passwordStrength)
                    }
                    RegFieldError(passwordError)
                    Spacer(Modifier.height(16.dp))

                    // ── Confirm Password ──────────────────────────────────
                    RegFieldLabel("Confirm Password")
                    OutlinedTextField(
                        value         = confirmPassword,
                        onValueChange = {
                            if (it.length <= 128) {
                                confirmPassword = it; confirmPasswordError = ""
                            }
                        },
                        leadingIcon          = {
                            Icon(Icons.Rounded.Lock, null, tint = RegGreen900)
                        },
                        placeholder          = {
                            Text("Re-enter password", color = Color.LightGray)
                        },
                        visualTransformation = if (confirmPasswordVisible)
                            VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon         = {
                            // Show visibility toggle always; match indicator when content present
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (confirmPassword.isNotEmpty()) {
                                    val matches = password == confirmPassword
                                    Icon(
                                        if (matches) Icons.Rounded.CheckCircle
                                        else Icons.Rounded.Cancel,
                                        null,
                                        tint     = if (matches) Color(0xFF388E3C)
                                        else Color(0xFFD32F2F),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                    Icon(
                                        if (confirmPasswordVisible) Icons.Rounded.VisibilityOff
                                        else Icons.Rounded.Visibility,
                                        if (confirmPasswordVisible) "Hide" else "Show",
                                        tint = RegGreen700
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        isError    = confirmPasswordError.isNotEmpty(),
                        singleLine = true,
                        enabled    = !isLoading,
                        shape      = RoundedCornerShape(14.dp),
                        modifier   = Modifier.fillMaxWidth().focusRequester(confirmPasswordFocus),
                        colors     = regFieldColors()
                    )
                    RegFieldError(confirmPasswordError)
                    Spacer(Modifier.height(20.dp))

                    // ── Terms ─────────────────────────────────────────────
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier          = Modifier.clickable {
                                termsAccepted = !termsAccepted
                            }
                        ) {
                            Checkbox(
                                checked         = termsAccepted,
                                onCheckedChange = { termsAccepted = it },
                                colors          = CheckboxDefaults.colors(
                                    checkedColor   = RegGreen900,
                                    uncheckedColor = if (termsError.isNotEmpty())
                                        Color(0xFFD32F2F) else Color(0xFFCDD8CD),
                                    checkmarkColor = Color.White
                                ),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            // Using buildAnnotatedString avoids text wrapping mid-clause
                            androidx.compose.foundation.text.ClickableText(
                                text  = androidx.compose.ui.text.buildAnnotatedString {
                                    withStyle(androidx.compose.ui.text.SpanStyle(
                                        fontSize = 13.sp, color = Color(0xFF444444)
                                    )) { append("I agree to the ") }
                                    withStyle(androidx.compose.ui.text.SpanStyle(
                                        fontSize   = 13.sp,
                                        color      = RegGreen900,
                                        fontWeight = FontWeight.SemiBold
                                    )) { append("Terms") }
                                    withStyle(androidx.compose.ui.text.SpanStyle(
                                        fontSize = 13.sp, color = Color(0xFF444444)
                                    )) { append(" & ") }
                                    withStyle(androidx.compose.ui.text.SpanStyle(
                                        fontSize   = 13.sp,
                                        color      = RegGreen900,
                                        fontWeight = FontWeight.SemiBold
                                    )) { append("Privacy Policy") }
                                },
                                onClick = { termsAccepted = !termsAccepted }
                            )
                        }
                        if (termsError.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier          = Modifier.padding(start = 4.dp, top = 4.dp)
                            ) {
                                Icon(Icons.Rounded.Info, null,
                                    tint     = Color(0xFFD32F2F),
                                    modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(termsError, color = Color(0xFFD32F2F), fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(28.dp))

                    // ── Register button ───────────────────────────────────
                    Button(
                        onClick = {
                            if (!validate()) return@Button
                            isLoading = true
                            registerUser(username.trim(), email.trim())
                        },
                        enabled   = !isLoading,
                        modifier  = Modifier.fillMaxWidth().height(54.dp),
                        shape     = RoundedCornerShape(14.dp),
                        colors    = ButtonDefaults.buttonColors(
                            containerColor         = RegGreen900,
                            disabledContainerColor = RegGreen900.copy(alpha = 0.4f)
                        ),
                        elevation = ButtonDefaults.buttonElevation(4.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White,
                                modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        } else {
                            Text("Create Account", fontSize = 16.sp,
                                fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier              = Modifier.fillMaxWidth()
                    ) {
                        Text("Already have an account? ", fontSize = 14.sp,
                            color = Color(0xFF4B5563))
                        Text(
                            "Login", fontSize = 14.sp,
                            fontWeight = FontWeight.Bold, color = RegGreen900,
                            modifier   = Modifier.clickable {
                                navController.navigate("login")
                            }
                        )
                    }
                }
            }
        }
    }
}


// ── Error message mapper ──────────────────────────────────────────────────────
private fun regErrorMessage(e: Exception): String = when {
    e is com.google.firebase.firestore.FirebaseFirestoreException &&
            e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE ->
        "Service temporarily unavailable. Please try again in a moment."
    e is com.google.firebase.firestore.FirebaseFirestoreException &&
            e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
        "Request timed out. Check your connection and try again."
    e is com.google.firebase.firestore.FirebaseFirestoreException &&
            e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
        "Access denied. Please contact support."
    e.message?.contains("network", ignoreCase = true) == true ||
            e.message?.contains("unable to resolve", ignoreCase = true) == true ->
        "No internet connection. Please check your network."
    else -> "Something went wrong. Please try again."
}

// ── Password strength meter ───────────────────────────────────────────────────
@Composable
private fun PasswordStrengthMeter(strength: PasswordStrength?) {
    if (strength == null) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f).height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (index < strength.segments) strength.color
                            else Color(0xFFE0E0E0)
                        )
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(strength.color))
            Spacer(Modifier.width(5.dp))
            Text("Password strength: ${strength.label}", fontSize = 11.sp,
                color = strength.color, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Small reusables ───────────────────────────────────────────────────────────
@Composable
private fun RegFieldLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = RegGreen900,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
}

@Composable
private fun RegFieldError(error: String) {
    if (error.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.padding(start = 4.dp, top = 4.dp)) {
            Icon(Icons.Rounded.Info, null, tint = Color(0xFFD32F2F),
                modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(error, color = Color(0xFFD32F2F), fontSize = 12.sp)
        }
    }
}

@Composable
private fun regFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor        = Color(0xFF1A1A1A),
    unfocusedTextColor      = Color(0xFF1A1A1A),
    errorTextColor          = Color(0xFF1A1A1A),
    disabledTextColor       = Color(0xFF9E9E9E),
    cursorColor             = RegGreen900,
    focusedBorderColor      = RegGreen900,
    unfocusedBorderColor    = Color(0xFFCDD8CD),
    errorBorderColor        = Color(0xFFD32F2F),
    focusedContainerColor   = Color.White,
    unfocusedContainerColor = Color.White,
    errorContainerColor     = Color(0xFFFFEBEE),
    disabledContainerColor  = Color(0xFFF0F0F0),
    disabledBorderColor     = Color(0xFFDDDDDD)
)