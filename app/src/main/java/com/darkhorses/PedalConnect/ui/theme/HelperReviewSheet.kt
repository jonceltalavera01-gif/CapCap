package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

data class HelperReview(
    val riderDisplayName : String,
    val rating           : Int,
    val review           : String?,
    val timestamp        : Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelperReviewSheet(
    responderUsername    : String,
    responderDisplayName : String,
    onDismiss            : () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var ratingData by remember { mutableStateOf<HelperRatingData?>(null) }
    var reviews    by remember { mutableStateOf<List<HelperReview>>(emptyList()) }
    var isLoading  by remember { mutableStateOf(true) }

    LaunchedEffect(responderUsername) {
        val db = FirebaseFirestore.getInstance()

        // Fetch aggregate rating
        fetchHelperRating(responderUsername) { data ->
            ratingData = data
        }

        // Fetch individual reviews
        db.collection("helperReviews")
            .whereEqualTo("responderName", responderUsername)
            .get()
            .addOnSuccessListener { snap ->
                reviews = snap.documents.mapNotNull { doc ->
                    val rating = doc.getLong("rating")?.toInt() ?: return@mapNotNull null
                    HelperReview(
                        riderDisplayName = doc.getString("riderDisplayName")
                            ?.takeIf { it.isNotBlank() }
                            ?: doc.getString("riderName")
                            ?: "Anonymous",
                        rating    = rating,
                        review    = doc.getString("review")?.takeIf { it.isNotBlank() },
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )
                }.sortedByDescending { it.timestamp }
                isLoading = false
            }
            .addOnFailureListener { isLoading = false }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = Color.White,
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        LazyColumn(
            modifier       = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            item {
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFF3E0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            responderDisplayName.take(1).uppercase(),
                            fontSize   = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = Color(0xFFF57C00)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            responderDisplayName,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 16.sp,
                            color      = Color(0xFF1A1A1A)
                        )
                        Text(
                            "Helper ratings & reviews",
                            fontSize = 12.sp,
                            color    = Color.Gray
                        )
                    }
                }
            }

            // ── Aggregate score ───────────────────────────────────────────────
            item {
                ratingData?.let { data ->
                    val badgeColor = when {
                        data.average >= 4.0 -> Color(0xFF2E7D32)
                        data.average >= 2.5 -> Color(0xFFF57C00)
                        else                -> Color(0xFFD32F2F)
                    }
                    val badgeBg = when {
                        data.average >= 4.0 -> Color(0xFFE8F5E9)
                        data.average >= 2.5 -> Color(0xFFFFF3E0)
                        else                -> Color(0xFFFFEBEE)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(badgeBg)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            String.format("%.1f", data.average),
                            fontSize   = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = badgeColor
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                (1..5).forEach { star ->
                                    Icon(
                                        if (star <= data.average.toInt()) Icons.Default.Star
                                        else Icons.Default.StarBorder,
                                        null,
                                        tint     = badgeColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                "${data.count} ${if (data.count == 1) "rating" else "ratings"}",
                                fontSize   = 12.sp,
                                color      = badgeColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            item {
                HorizontalDivider(color = Color(0xFFF0F0F0))
            }

            // ── Loading state ─────────────────────────────────────────────────
            if (isLoading) {
                item {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color       = Color(0xFFF57C00),
                            modifier    = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp
                        )
                    }
                }
            }

            // ── Empty state ───────────────────────────────────────────────────
            else if (reviews.isEmpty()) {
                item {
                    Column(
                        modifier            = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.StarBorder, null,
                            tint     = Color(0xFFDDDDDD),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            "No written reviews yet",
                            fontSize   = 14.sp,
                            color      = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ── Review cards ──────────────────────────────────────────────────
            else {
                items(reviews) { review ->
                    ReviewCard(review)
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(review: HelperReview) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier            = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    review.riderDisplayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp,
                    color      = Color(0xFF1A1A1A)
                )
                Text(
                    formatReviewDate(review.timestamp),
                    fontSize = 11.sp,
                    color    = Color.Gray
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                (1..5).forEach { star ->
                    Icon(
                        if (star <= review.rating) Icons.Default.Star
                        else Icons.Default.StarBorder,
                        null,
                        tint     = Color(0xFFF57C00),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            if (!review.review.isNullOrBlank()) {
                Text(
                    review.review,
                    fontSize   = 13.sp,
                    color      = Color(0xFF555555),
                    lineHeight = 19.sp
                )
            }
        }
    }
}

private fun formatReviewDate(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val postYear    = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.YEAR)
    return if (postYear == currentYear)
        SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
    else
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
}