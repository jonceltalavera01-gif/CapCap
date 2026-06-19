package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore

// ── HelperRatingData ──────────────────────────────────────────────────────────
// Holds the fetched rating info for a helper. Null when not yet loaded or
// when the count is below the minimum display threshold (3).
data class HelperRatingData(
    val average: Double,
    val count:   Int
)

// ── fetchHelperRating ─────────────────────────────────────────────────────────
// Fetches helperRating and helperRatingCount from the users collection.
// Returns null if the user doesn't exist, has no ratings, or has fewer
// than MIN_RATING_COUNT ratings (too few to be trustworthy).
private const val MIN_RATING_COUNT = 3

fun fetchHelperRating(
    username: String,
    onResult: (HelperRatingData?) -> Unit
) {
    FirebaseFirestore.getInstance()
        .collection("users")
        .whereEqualTo("username", username)
        .limit(1)
        .get()
        .addOnSuccessListener { snap ->
            val doc     = snap.documents.firstOrNull()
            val avg     = doc?.getDouble("helperRating")           ?: 0.0
            val count   = doc?.getLong("helperRatingCount")?.toInt() ?: 0
            if (count >= MIN_RATING_COUNT) {
                onResult(HelperRatingData(average = avg, count = count))
            } else {
                onResult(null)
            }
        }
        .addOnFailureListener { onResult(null) }
}

// ── HelperRatingBadge ─────────────────────────────────────────────────────────
// Inline badge shown on the response card next to the responder's name.
// Displays nothing until the fetch resolves or if count < MIN_RATING_COUNT.
//
// Display rules:
//   count <  3   → nothing shown
//   count  3–9   → "4.2 ★ (3 ratings)"
//   count >= 10  → "4.2 ★"
//
// Color rules:
//   avg >= 4.0   → green  (trusted helper)
//   avg >= 2.5   → amber  (acceptable)
//   avg <  2.5   → red    (flagged — low trust)
//
// Parameters:
//   responderUsername — the username field (not display name) used to query Firestore
//   modifier          — optional layout modifier
@Composable
fun HelperRatingBadge(
    responderUsername: String,
    modifier:          Modifier = Modifier,
    onClick:           (() -> Unit)? = null
) {
    var ratingData by remember(responderUsername) { mutableStateOf<HelperRatingData?>(null) }
    var loaded     by remember(responderUsername) { mutableStateOf(false) }

    LaunchedEffect(responderUsername) {
        if (responderUsername.isBlank()) return@LaunchedEffect
        fetchHelperRating(responderUsername) { data ->
            ratingData = data
            loaded     = true
        }
    }

    // Render nothing until loaded or if data is null (insufficient ratings)
    if (!loaded || ratingData == null) return

    val data         = ratingData!!
    val formattedAvg = String.format("%.1f", data.average)

    // Badge color based on average
    val badgeColor = when {
        data.average >= 4.0 -> Color(0xFF2E7D32)   // green  — trusted
        data.average >= 2.5 -> Color(0xFFF57C00)   // amber  — acceptable
        else                -> Color(0xFFD32F2F)   // red    — low trust
    }
    val badgeBg = when {
        data.average >= 4.0 -> Color(0xFFE8F5E9)
        data.average >= 2.5 -> Color(0xFFFFF3E0)
        else                -> Color(0xFFFFEBEE)
    }

    // Label: include count only when 3–9, omit when 10+
    val label = if (data.count < 10) {
        val noun = if (data.count == 1) "rating" else "ratings"
        "$formattedAvg  (${data.count} $noun)"
    } else {
        formattedAvg
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(badgeBg)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication        = null
                ) { onClick() } else Modifier
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            Icons.Default.Star,
            contentDescription = null,
            tint     = badgeColor,
            modifier = Modifier.size(11.dp)
        )
        Text(
            label,
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color      = badgeColor
        )
    }
}