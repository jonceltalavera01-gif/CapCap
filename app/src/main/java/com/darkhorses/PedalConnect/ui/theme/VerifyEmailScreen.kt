package com.darkhorses.PedalConnect.ui.theme

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val VGreen900 = Color(0xFF06402B)
private val VGreen700 = Color(0xFF0A5C3D)
private val VGreen100 = Color(0xFFDDF1E8)
private val VMuted    = Color(0xFF6B7280)
private const val RESEND_COOLDOWN_SECONDS = 60

@Composable
fun VerifyEmailScreen(navController: NavController, userName: String) {
    val context = LocalContext.current
    val auth    = FirebaseAuth.getInstance()
    val scope   = rememberCoroutineScope()

    var isChecking     by remember { mutableStateOf(false) }
    var isResending    by remember { mutableStateOf(false) }
    var cooldownSeconds by remember { mutableStateOf(0) }

    fun goHome() {
        navController.navigate("home/$userName") { popUpTo(0) { inclusive = true } }
    }

    // Ticks the resend cooldown down once per second while > 0.
    LaunchedEffect(cooldownSeconds) {
        if (cooldownSeconds > 0) {
            delay(1000)
            cooldownSeconds -= 1
        }
    }

    // No custom BackHandler here on purpose: this screen only appears with an
    // already-cleared backstack (popUpTo(0) on the way in), so system back
    // exits the app — same "back exits, doesn't dead-end" decision already
    // made for the login screen. "Verify later" is the intentional skip path.

    Box(
        modifier = Modifier.fillMaxSize().background(Color.White).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(72.dp).background(VGreen100, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.MarkEmailUnread, null, tint = VGreen700, modifier = Modifier.size(34.dp))
            }
            Text("Verify your email", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = VGreen900, textAlign = TextAlign.Center)
            Text(
                "We sent a verification link to ${auth.currentUser?.email ?: "your email"}. " +
                        "Click it, then come back here.",
                fontSize = 14.sp, color = VMuted, textAlign = TextAlign.Center, lineHeight = 20.sp
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    isChecking = true
                    scope.launch {
                        try { auth.currentUser?.reload()?.await() } catch (_: Exception) { }
                        isChecking = false
                        if (auth.currentUser?.isEmailVerified == true) {
                            Toast.makeText(context, "Email verified ✅", Toast.LENGTH_SHORT).show()
                            goHome()
                        } else {
                            Toast.makeText(context, "Not verified yet — check your inbox.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled  = !isChecking,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = VGreen900, contentColor = Color.White)
            ) {
                if (isChecking) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("I've verified, continue", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = {
                    isResending = true
                    scope.launch {
                        try {
                            auth.currentUser?.sendEmailVerification()?.await()
                            Toast.makeText(context, "Verification email resent.", Toast.LENGTH_SHORT).show()
                            cooldownSeconds = RESEND_COOLDOWN_SECONDS
                        } catch (e: Exception) {
                            Toast.makeText(context, "Couldn't resend: ${e.message?.take(60)}", Toast.LENGTH_SHORT).show()
                        }
                        isResending = false
                    }
                },
                enabled  = !isResending && cooldownSeconds == 0,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Text(
                    if (cooldownSeconds > 0) "Resend in ${cooldownSeconds}s" else "Resend email",
                    color = VGreen900, fontWeight = FontWeight.SemiBold
                )
            }

            TextButton(onClick = { goHome() }) {
                Text("Verify later", color = VMuted, fontSize = 13.sp)
            }
        }
    }
}