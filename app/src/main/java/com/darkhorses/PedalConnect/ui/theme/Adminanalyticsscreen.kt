package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

// ── Design tokens (matches AdminScreen) ───────────────────────────────────────
private val AGreen900  = Color(0xFF06402B)
private val AGreen700  = Color(0xFF0A5C3D)
private val AGreen50   = Color(0xFFF0FAF5)
private val AAmber500  = Color(0xFFF59E0B)
private val ARed       = Color(0xFFD32F2F)
private val ABlue      = Color(0xFF1976D2)
private val ASurface   = Color(0xFFF5F7F6)
private val AWhite     = Color(0xFFFFFFFF)
private val AOnSurface = Color(0xFF111827)
private val AMuted     = Color(0xFF6B7280)
private val AGreen100  = Color(0xFFDDF1E8)

// ── Bucket models ─────────────────────────────────────────────────────────────
private data class WeekBucket(val label: String, val startMs: Long, val endMs: Long, var count: Int = 0)
private data class DayBucket(val label: String, val startMs: Long, val endMs: Long, val users: MutableSet<String> = mutableSetOf())

// Per-user activity tallies for the leaderboard. Scoring weights below are our
// own transparent definition (not derived from any external formula) — tune
// freely, they only affect leaderboard ordering.
private data class RiderStat(
    var posts: Int = 0,
    var likes: Int = 0,
    var sosSent: Int = 0,
    var sosResponded: Int = 0,
    var rideOrganized: Int = 0,
    var rideJoined: Int = 0,
    var messaging: Int = 0,
    var friendRequests: Int = 0
) {
    fun points(): Int =
        posts * 3 + likes + sosSent + sosResponded * 4 +
                rideOrganized * 4 + rideJoined * 2 + messaging + friendRequests
}

private fun buildWeekBuckets(weeks: Int): List<WeekBucket> {
    val cal = Calendar.getInstance()
    cal.firstDayOfWeek = Calendar.SUNDAY
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) cal.add(Calendar.DAY_OF_MONTH, -1)
    val fmt = SimpleDateFormat("M/d", Locale.getDefault())
    val thisWeekStart = cal.timeInMillis
    val buckets = mutableListOf<WeekBucket>()
    for (i in (weeks - 1) downTo 0) {
        val start = thisWeekStart - i.toLong() * 7 * 24 * 3600 * 1000L
        val end = start + 7L * 24 * 3600 * 1000L
        buckets.add(WeekBucket(fmt.format(Date(start)), start, end))
    }
    return buckets
}

private fun buildDayBuckets(days: Int): List<DayBucket> {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    val fmt = SimpleDateFormat("M/d", Locale.getDefault())
    val todayStart = cal.timeInMillis
    val buckets = mutableListOf<DayBucket>()
    for (i in (days - 1) downTo 0) {
        val start = todayStart - i.toLong() * 24 * 3600 * 1000L
        val end = start + 24L * 3600 * 1000L
        buckets.add(DayBucket(fmt.format(Date(start)), start, end))
    }
    return buckets
}

// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAnalyticsScreen(paddingValues: PaddingValues) {
    val db = remember { FirebaseFirestore.getInstance() }

    var isLoading   by remember { mutableStateOf(true) }
    var totalUsers  by remember { mutableIntStateOf(0) }
    var totalPosts  by remember { mutableIntStateOf(0) }
    var totalAlerts by remember { mutableIntStateOf(0) }

    var postsPending  by remember { mutableIntStateOf(0) }
    var postsAccepted by remember { mutableIntStateOf(0) }
    var postsRejected by remember { mutableIntStateOf(0) }

    var ridesPending  by remember { mutableIntStateOf(0) }
    var ridesApproved by remember { mutableIntStateOf(0) }
    var ridesRejected by remember { mutableIntStateOf(0) }

    var usersWeekly  by remember { mutableStateOf<List<WeekBucket>>(emptyList()) }
    var postsWeekly  by remember { mutableStateOf<List<WeekBucket>>(emptyList()) }
    var alertsWeekly by remember { mutableStateOf<List<WeekBucket>>(emptyList()) }
    var ridesWeekly  by remember { mutableStateOf<List<WeekBucket>>(emptyList()) }
    var dauDaily      by remember { mutableStateOf<List<DayBucket>>(emptyList()) }

    var totalConversations by remember { mutableIntStateOf(0) }
    var totalFriendRequests by remember { mutableIntStateOf(0) }
    var totalSavedRoutes by remember { mutableIntStateOf(0) }
    var totalTrainingPlans by remember { mutableIntStateOf(0) }
    var totalRideEvents by remember { mutableIntStateOf(0) }
    var newRidersThisWeek by remember { mutableIntStateOf(0) }
    var avgPostEngagement by remember { mutableStateOf(0.0) }
    var favoriteRideSpot by remember { mutableStateOf("") }
    var alertHotspots by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var leaderboard by remember { mutableStateOf<List<Pair<String, RiderStat>>>(emptyList()) }

    var loadError by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTrigger) {
        isLoading  = true
        loadError  = false
        postsPending = 0; postsAccepted = 0; postsRejected = 0
        ridesPending  = 0; ridesApproved = 0; ridesRejected = 0

        val weekTemplate = buildWeekBuckets(8)
        val uWeeks = weekTemplate.map { it.copy() }.toMutableList()
        val pWeeks = weekTemplate.map { it.copy() }.toMutableList()
        val aWeeks = weekTemplate.map { it.copy() }.toMutableList()
        val rWeeks = weekTemplate.map { it.copy() }.toMutableList()
        val dayBuckets = buildDayBuckets(14)

        fun weekBucketFor(list: MutableList<WeekBucket>, ts: Long): WeekBucket? =
            list.firstOrNull { ts >= it.startMs && ts < it.endMs }

        fun dayBucketFor(ts: Long): DayBucket? =
            dayBuckets.firstOrNull { ts >= it.startMs && ts < it.endMs }

        val riderStats = mutableMapOf<String, RiderStat>()
        fun statFor(name: String): RiderStat = riderStats.getOrPut(name) { RiderStat() }

        try {
            val usersSnap = db.collection("users").get().await()
            totalUsers = usersSnap.size()
            val uidToUsername = mutableMapOf<String, String>()
            usersSnap.documents.forEach { doc ->
                doc.getString("username")?.takeIf { it.isNotBlank() }?.let { uidToUsername[doc.id] = it }
                val ts = doc.getTimestamp("createdAt")?.toDate()?.time ?: return@forEach
                weekBucketFor(uWeeks, ts)?.let { it.count++ }
            }

            var engagementSum = 0.0
            var engagementCount = 0

            val postsSnap = db.collection("posts").get().await()
            totalPosts = postsSnap.size()
            postsSnap.documents.forEach { doc ->
                val status = doc.getString("status")
                when (status) {
                    "pending"  -> postsPending++
                    "accepted" -> postsAccepted++
                    "rejected" -> postsRejected++
                }
                val userName = doc.getString("userName") ?: ""
                val likes = (doc.getLong("likes") ?: 0L).toInt()
                val postComments = (doc.getLong("comments") ?: 0L).toInt()
                if (status == "accepted") {
                    engagementSum += (likes + postComments).toDouble()
                    engagementCount++
                }
                if (userName.isNotBlank()) {
                    statFor(userName).let { s -> s.posts++; s.likes += likes }
                }
                val ts = doc.getLong("timestamp") ?: return@forEach
                weekBucketFor(pWeeks, ts)?.let { it.count++ }
                if (userName.isNotBlank()) dayBucketFor(ts)?.users?.add(userName)
            }
            avgPostEngagement = if (engagementCount > 0) engagementSum / engagementCount else 0.0

            val alertLocationCounts = mutableMapOf<String, Int>()
            val alertLocationDisplay = mutableMapOf<String, String>()
            val placeholderLocations = setOf(
                "unknown location", "resolving...", "location error",
                "location unavailable — check on rider"
            )

            val alertsSnap = db.collection("alerts").get().await()
            totalAlerts = alertsSnap.size()
            alertsSnap.documents.forEach { doc ->
                val riderName = doc.getString("riderName") ?: ""
                val responderName = doc.getString("responderName") ?: ""
                if (riderName.isNotBlank()) statFor(riderName).sosSent++
                if (responderName.isNotBlank()) statFor(responderName).sosResponded++
                doc.getString("locationName")?.trim()?.takeIf {
                    it.isNotBlank() && it.lowercase() !in placeholderLocations
                }?.let { loc ->
                    val key = loc.lowercase()
                    alertLocationCounts[key] = (alertLocationCounts[key] ?: 0) + 1
                    alertLocationDisplay.putIfAbsent(key, loc)
                }
                val ts = doc.getLong("timestamp") ?: return@forEach
                weekBucketFor(aWeeks, ts)?.let { it.count++ }
                if (riderName.isNotBlank()) dayBucketFor(ts)?.users?.add(riderName)
            }
            alertHotspots = alertLocationCounts.entries
                .sortedByDescending { it.value }
                .take(3)
                .mapNotNull { (key, count) -> alertLocationDisplay[key]?.let { it to count } }

            val routeCounts = mutableMapOf<String, Int>()
            val routeDisplay = mutableMapOf<String, String>()
            val ridesSnap = db.collection("rideEvents").get().await()
            ridesSnap.documents.forEach { doc ->
                when (doc.getString("status")) {
                    "pending"  -> ridesPending++
                    "approved" -> ridesApproved++
                    "rejected" -> ridesRejected++
                }
                val organizer = doc.getString("organizer") ?: ""
                val participants = (doc.get("participants") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                if (organizer.isNotBlank()) statFor(organizer).rideOrganized++
                participants.forEach { p -> if (p.isNotBlank() && p != organizer) statFor(p).rideJoined++ }
                doc.getString("route")?.trim()?.takeIf { it.isNotBlank() }?.let { route ->
                    val key = route.lowercase()
                    routeCounts[key] = (routeCounts[key] ?: 0) + 1
                    routeDisplay.putIfAbsent(key, route)
                }
                val ts = doc.getLong("timestamp") ?: return@forEach
                weekBucketFor(rWeeks, ts)?.let { it.count++ }
            }
            totalRideEvents  = ridesPending + ridesApproved + ridesRejected
            favoriteRideSpot = routeCounts.maxByOrNull { it.value }?.key?.let { routeDisplay[it] } ?: ""

            val conversationsSnap = db.collection("conversations").get().await()
            totalConversations = conversationsSnap.size()
            conversationsSnap.documents.forEach { doc ->
                val participantIds = (doc.get("participantIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                participantIds.forEach { uid -> uidToUsername[uid]?.let { statFor(it).messaging++ } }
            }

            val friendReqSnap = db.collection("friend_requests").get().await()
            totalFriendRequests = friendReqSnap.size()
            friendReqSnap.documents.forEach { doc ->
                val fromId = doc.getString("fromId") ?: return@forEach
                uidToUsername[fromId]?.let { statFor(it).friendRequests++ }
            }

            totalSavedRoutes = db.collection("savedRoutes").get().await().size()
            totalTrainingPlans = db.collectionGroup("plans").get().await().size()

            newRidersThisWeek = uWeeks.lastOrNull()?.count ?: 0
            leaderboard = riderStats.toList().sortedByDescending { it.second.points() }.take(5)

            usersWeekly  = uWeeks
            postsWeekly  = pWeeks
            alertsWeekly = aWeeks
            ridesWeekly  = rWeeks
            dauDaily     = dayBuckets
        } catch (e: Exception) {
            android.util.Log.e("AdminAnalytics", "Failed to load analytics", e)
            loadError = true
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Analytics, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Analytics", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { refreshTrigger++ }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AGreen900)
            )
        },
        containerColor = ASurface
    ) { innerPadding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                CircularProgressIndicator(color = AGreen900, strokeWidth = 2.5.dp, modifier = Modifier.size(32.dp))
            }
            return@Scaffold
        }

        if (loadError) {
            Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.WifiOff, null, tint = AMuted, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Couldn't load analytics", fontWeight = FontWeight.SemiBold, color = AOnSurface)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { refreshTrigger++ }, colors = ButtonDefaults.buttonColors(containerColor = AGreen900)) {
                        Text("Retry", color = Color.White)
                    }
                }
            }
            return@Scaffold
        }

        val listState = rememberLazyListState()
        val navScope = rememberCoroutineScope()
        data class SectionNav(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val index: Int)
        val sectionNavs = listOf(
            SectionNav("Overview", Icons.Default.BarChart, 0),
            SectionNav("Community", Icons.Default.Insights, 3),
            SectionNav("Trends", Icons.Default.Event, 6),
            SectionNav("Leaderboard", Icons.Default.MilitaryTech, 11)
        )

        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            // ── Breadcrumb section nav — jump straight to any card ──────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AWhite)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sectionNavs.forEach { sec ->
                    AssistChip(
                        onClick = { navScope.launch { listState.animateScrollToItem(sec.index) } },
                        label = { Text(sec.label, fontSize = 12.sp) },
                        leadingIcon = { Icon(sec.icon, null, modifier = Modifier.size(14.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = AGreen50,
                            labelColor = AGreen900,
                            leadingIconContentColor = AGreen900
                        )
                    )
                }
            }
            HorizontalDivider(color = Color(0xFFE5E7EB), thickness = 1.dp)

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 12.dp,
                    bottom = paddingValues.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            item {
                AnalyticsCard(title = "Statistics Overview", icon = Icons.Default.BarChart) {
                    SimpleBarChart(
                        bars = listOf(
                            Triple("Users", totalUsers, ABlue),
                            Triple("Posts", totalPosts, AAmber500),
                            Triple("Alerts", totalAlerts, ARed)
                        )
                    )
                }
            }

            item {
                AnalyticsCard(title = "Posts Status", icon = Icons.Default.Article) {
                    SimpleDonutChart(
                        segments = listOf(
                            Triple("Pending", postsPending, AAmber500),
                            Triple("Accepted", postsAccepted, AGreen700),
                            Triple("Rejected", postsRejected, ARed)
                        ),
                        centerLabel = "${postsPending + postsAccepted + postsRejected}\nPosts"
                    )
                }
            }

            item {
                AnalyticsCard(title = "Ride Events Status", icon = Icons.Default.DirectionsBike) {
                    SimpleDonutChart(
                        segments = listOf(
                            Triple("Pending", ridesPending, AAmber500),
                            Triple("Approved", ridesApproved, AGreen700),
                            Triple("Rejected", ridesRejected, ARed)
                        ),
                        centerLabel = "${ridesPending + ridesApproved + ridesRejected}\nRides"
                    )
                }
            }

            item {
                AnalyticsCard(title = "Community Insights", icon = Icons.Default.Insights) {
                    InsightRow(Icons.AutoMirrored.Filled.DirectionsBike, "New Riders This Week", "$newRidersThisWeek", ABlue)
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    InsightRow(Icons.Default.LocationOn, "Favorite Ride Spot", favoriteRideSpot, Color(0xFF7C3AED))
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    Spacer(Modifier.height(8.dp))
                    Text("Most Alert-Prone Places", fontSize = 11.sp, color = AMuted, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    if (alertHotspots.isEmpty()) {
                        Text("No alert location data yet", fontSize = 12.sp, color = AMuted, modifier = Modifier.padding(vertical = 6.dp))
                    } else {
                        Column(Modifier.fillMaxWidth()) {
                            alertHotspots.forEachIndexed { idx, (location, count) ->
                                AlertHotspotRow(rank = idx + 1, location = location, count = count)
                            }
                        }
                    }
                }
            }

            item {
                AnalyticsCard(title = "User Engagement", icon = Icons.Default.Groups) {
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        EngagementStat("$totalConversations", "Conversations", Icons.Default.Chat, ABlue)
                        EngagementStat("$totalFriendRequests", "Friend Requests", Icons.Default.PersonAdd, AAmber500)
                        EngagementStat("$totalSavedRoutes", "Saved Routes", Icons.AutoMirrored.Filled.DirectionsBike, Color(0xFF7C3AED))
                        EngagementStat("$totalTrainingPlans", "Training Plans", Icons.Default.FitnessCenter, Color(0xFF00796B))
                        EngagementStat(String.format("%.1f", avgPostEngagement), "Avg Post Engagement", Icons.Default.Favorite, Color(0xFFEC4899))
                    }
                }
            }

            item {
                AnalyticsCard(title = "Most Used Features", icon = Icons.Default.EmojiEvents) {
                    SimpleHorizontalBarChart(
                        bars = listOf(
                            "Friend Requests" to totalFriendRequests,
                            "Alerts / SOS" to totalAlerts,
                            "Posts" to totalPosts,
                            "Saved Routes" to totalSavedRoutes,
                            "Messaging" to totalConversations,
                            "Training Plans" to totalTrainingPlans,
                            "Ride Events" to totalRideEvents
                        )
                    )
                }
            }
            item {
                AnalyticsCard(title = "Ride Events per Week", icon = Icons.Default.Event) {
                    SimpleLineChart(points = ridesWeekly.map { it.label to it.count }, color = Color(0xFF7C3AED))
                }
            }
            item {
                AnalyticsCard(title = "Users per Week", icon = Icons.Default.People) {
                    SimpleLineChart(points = usersWeekly.map { it.label to it.count }, color = ABlue)
                }
            }
            item {
                AnalyticsCard(title = "Posts per Week", icon = Icons.Default.Article) {
                    SimpleLineChart(points = postsWeekly.map { it.label to it.count }, color = AAmber500)
                }
            }
            item {
                AnalyticsCard(title = "Alerts per Week", icon = Icons.Default.Warning) {
                    SimpleLineChart(points = alertsWeekly.map { it.label to it.count }, color = ARed)
                }
            }

            item {
                AnalyticsCard(title = "Daily Active Users (last 14 days)", icon = Icons.Default.CalendarMonth) {
                    Text(
                        "Unique riders who posted or sent an SOS per day",
                        fontSize = 11.sp, color = AMuted,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    SimpleLineChart(
                        points = dauDaily.map { it.label to it.users.size },
                        color = Color(0xFF0891B2)
                    )
                }
            }

                item {
                    AnalyticsCard(title = "Most Active Riders", icon = Icons.Default.MilitaryTech) {
                        if (leaderboard.isEmpty()) {
                            Text("Not enough activity yet.", fontSize = 12.sp, color = AMuted)
                        } else {
                            Column(Modifier.fillMaxWidth()) {
                                leaderboard.forEachIndexed { idx, (name, stat) ->
                                    LeaderboardRow(rank = idx + 1, name = name, stat = stat)
                                    if (idx < leaderboard.lastIndex) HorizontalDivider(color = Color(0xFFF0F0F0))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Card shell ─────────────────────────────────────────────────────────────────
@Composable
private fun AnalyticsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = AGreen900, modifier = Modifier.size(18.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AOnSurface)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ── Engagement stat cell ────────────────────────────────────────────────────
@Composable
private fun EngagementStat(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        }
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = AOnSurface)
        Text(label, fontSize = 11.sp, color = AMuted, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

// ── Simple bar chart ───────────────────────────────────────────────────────────
@Composable
private fun SimpleBarChart(bars: List<Triple<String, Int, Color>>) {
    val maxVal = (bars.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val chartHeight = 120.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEach { (label, value, color) ->
            val barHeight = chartHeight * (value.toFloat() / maxVal).coerceIn(0.03f, 1f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$value", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AOnSurface)
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(barHeight)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(color)
                )
                Spacer(Modifier.height(6.dp))
                Text(label, fontSize = 11.sp, color = AMuted, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ── Simple donut chart ─────────────────────────────────────────────────────────
@Composable
private fun SimpleDonutChart(segments: List<Triple<String, Int, Color>>, centerLabel: String) {
    val total = segments.sumOf { it.second }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(180.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = size.minDimension * 0.22f
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                if (total <= 0) {
                    drawArc(
                        color = Color(0xFFE5E7EB),
                        startAngle = 0f, sweepAngle = 360f, useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )
                } else {
                    var startAngle = -90f
                    segments.filter { it.second > 0 }.forEach { (_, value, color) ->
                        val sweep = 360f * (value.toFloat() / total)
                        drawArc(
                            color = color,
                            startAngle = startAngle, sweepAngle = sweep, useCenter = false,
                            topLeft = topLeft, size = arcSize,
                            style = Stroke(width = strokeWidth)
                        )
                        startAngle += sweep
                    }
                }
            }
            Text(
                centerLabel, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                color = AOnSurface, textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            segments.forEach { (label, value, color) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(color))
                    Text("$label ($value)", fontSize = 11.sp, color = AMuted)
                }
            }
        }
    }
}

// ── Simple line chart ─────────────────────────────────────────────────────────
@Composable
private fun SimpleLineChart(points: List<Pair<String, Int>>, color: Color) {
    if (points.isEmpty()) return
    val maxVal = (points.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val chartHeight = 140.dp

    Column(Modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            val w = size.width
            val h = size.height
            val stepX = if (points.size > 1) w / (points.size - 1) else 0f
            val pts = points.mapIndexed { i, (_, v) ->
                val x = if (points.size > 1) i * stepX else w / 2f
                val y = h - (v.toFloat() / maxVal) * h * 0.9f - h * 0.05f
                Offset(x, y)
            }
            val gridColor = Color(0xFFE5E7EB)
            for (i in 0..3) {
                val y = h * i / 3f
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            if (pts.size > 1) {
                for (i in 0 until pts.size - 1) {
                    drawLine(
                        color = color,
                        start = pts[i], end = pts[i + 1],
                        strokeWidth = 5f, cap = StrokeCap.Round
                    )
                }
            }
            pts.forEach { p ->
                drawCircle(color = color, radius = 6f, center = p)
                drawCircle(color = Color.White, radius = 3f, center = p)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val step = (points.size / 6).coerceAtLeast(1)
            val shown = if (points.size <= 8) points else points.filterIndexed { i, _ -> i % step == 0 }
            shown.forEach { (label, _) ->
                Text(label, fontSize = 9.sp, color = AMuted)
            }
        }
    }
}

// ── Alert hotspot row (rank + location + count) ────────────────────────────────
@Composable
private fun AlertHotspotRow(rank: Int, location: String, count: Int) {
    // Severity-style scale (darkest/most urgent first), not a medal scale —
    // this is a hotspot ranking, not an achievement, so it shouldn't feel
    // like a reward. Kept visually distinct from the count pill below.
    val rankColor = when (rank) {
        1 -> ARed
        2 -> Color(0xFFEA580C)
        3 -> Color(0xFFF59E0B)
        else -> AMuted
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).background(rankColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text("$rank", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = rankColor)
        }
        Text(
            location, fontSize = 13.sp, color = AOnSurface, fontWeight = FontWeight.Medium,
            maxLines = 1, modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(ARed.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text("$count alerts", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ARed)
        }
    }
}

// ── Insight row (icon + label + value) ────────────────────────────────────────
@Composable
private fun InsightRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, tint: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(label, fontSize = 11.sp, color = AMuted, fontWeight = FontWeight.Medium)
            Text(
                value.ifBlank { "No data yet" }, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, color = AOnSurface, maxLines = 2
            )
        }
    }
}

// ── Simple horizontal bar chart ────────────────────────────────────────────────
@Composable
private fun SimpleHorizontalBarChart(bars: List<Pair<String, Int>>) {
    val maxVal = (bars.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        bars.sortedByDescending { it.second }.forEach { (label, value) ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, fontSize = 12.sp, color = AOnSurface, fontWeight = FontWeight.Medium)
                    Text("$value", fontSize = 12.sp, color = AMuted, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (value.toFloat() / maxVal).coerceIn(0.02f, 1f))
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(AGreen700)
                )
            }
        }
    }
}

// ── Leaderboard row ────────────────────────────────────────────────────────────
@Composable
private fun LeaderboardRow(rank: Int, name: String, stat: RiderStat) {
    val rankColor = when (rank) {
        1 -> Color(0xFFF59E0B)
        2 -> Color(0xFF9CA3AF)
        3 -> Color(0xFFB45309)
        else -> AMuted
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).background(rankColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text("$rank", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = rankColor)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AOnSurface)
            val parts = buildList {
                if (stat.posts > 0) add("Posts: ${stat.posts}")
                if (stat.likes > 0) add("Likes: ${stat.likes}")
                if (stat.sosSent > 0) add("SOS Sent: ${stat.sosSent}")
                if (stat.sosResponded > 0) add("SOS Responded: ${stat.sosResponded}")
                if (stat.rideOrganized > 0) add("Ride Organized: ${stat.rideOrganized}")
                if (stat.rideJoined > 0) add("Ride Joined: ${stat.rideJoined}")
                if (stat.messaging > 0) add("Messaging: ${stat.messaging}")
                if (stat.friendRequests > 0) add("Friend Requests: ${stat.friendRequests}")
            }
            Text(parts.joinToString(" · "), fontSize = 11.sp, color = AMuted, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(AGreen100)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text("${stat.points()} pts", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AGreen900)
        }
    }
}