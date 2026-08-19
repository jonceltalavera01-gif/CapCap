package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonOutline
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
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt

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

    // Render nothing only until the fetch resolves — once loaded, always show
    // something (either a real rating or a neutral "new" state) so the rider
    // never sees a blank space where trust info should be.
    if (!loaded) return

    val data = ratingData

    val label: String
    val badgeColor: Color
    val badgeBg: Color
    val icon: androidx.compose.ui.graphics.vector.ImageVector

    if (data == null) {
        // Fewer than MIN_RATING_COUNT ratings — neutral "new" badge instead of nothing
        label     = "New\nHelper"
        badgeColor = Color(0xFF6A1B9A)
        badgeBg    = Color(0xFFF3E5F5)
        icon       = Icons.Default.SentimentSatisfiedAlt
    } else {
        val formattedAvg = String.format("%.1f", data.average)
        badgeColor = when {
            data.average >= 4.0 -> Color(0xFF2E7D32)   // green  — trusted
            data.average >= 2.5 -> Color(0xFFF57C00)   // amber  — acceptable
            else                -> Color(0xFFD32F2F)   // red    — low trust
        }
        badgeBg = when {
            data.average >= 4.0 -> Color(0xFFE8F5E9)
            data.average >= 2.5 -> Color(0xFFFFF3E0)
            else                -> Color(0xFFFFEBEE)
        }
        // Label: include count only when 3–9, omit when 10+
        label = if (data.count < 10) {
            val noun = if (data.count == 1) "rating" else "ratings"
            "$formattedAvg  (${data.count} $noun)"
        } else {
            formattedAvg
        }
        icon = Icons.Default.Star
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(badgeBg)
            .border(1.dp, badgeColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication        = null
                ) { onClick() } else Modifier
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint     = badgeColor,
            modifier = Modifier.size(11.dp)
        )
        Text(
            label,
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color      = badgeColor,
            lineHeight = 12.sp,
            maxLines   = 2,
            overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign  = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}