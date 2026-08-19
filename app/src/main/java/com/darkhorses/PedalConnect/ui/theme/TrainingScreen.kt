package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

// ── Design tokens ─────────────────────────────────────────────────────────
internal val TGreen900  = Color(0xFF06402B)
internal val TGreen700  = Color(0xFF0D7050)
internal val TGreen100  = Color(0xFFDDF1E8)
internal val TGreen50   = Color(0xFFE8F5EE)
internal val TBgCanvas  = Color(0xFFF5F7F6)
internal val TBgSurface = Color(0xFFFFFFFF)
internal val TTextPrimary   = Color(0xFF111827)
internal val TTextSecondary = Color(0xFF374151)
internal val TTextMuted     = Color(0xFF6B7280)
internal val TDivider       = Color(0xFFE5E7EB)
internal val TCoralBg   = Color(0xFFFAECE7)
internal val TCoralIcon = Color(0xFF993C1D)
internal val TBlueBg    = Color(0xFFE6F1FB)
internal val TBlueIcon  = Color(0xFF185FA5)
internal val TRaceBg    = Color(0xFFF3E8FD)
internal val TRaceIcon  = Color(0xFF7C3AED)
internal val TPartialOrange = Color(0xFFF57C00)
internal val TInProgressRed = Color(0xFFD32F2F)

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
internal val WORKOUT_TYPES = listOf("Recovery", "Endurance", "Intervals", "Long Ride", "Race")
internal val TYPE_DIFFICULTIES = mapOf(
    "Recovery" to listOf("Light Recovery", "Active Recovery", "Extended Recovery"),
    "Endurance" to listOf("Base Builder", "Distance Builder", "Endurance Challenge"),
    "Intervals" to listOf("Intro Intervals", "Power Intervals", "VO₂ Max Intervals"),
    "Long Ride" to listOf("Weekend Ride", "Century Prep", "Epic Ride"),
    "Race" to listOf("Practice Race", "Club Competition", "Championship")
)
internal val DIFFICULTIES = listOf("Easy", "Medium", "Hard", "Custom")
internal val RACE_TYPES = listOf("Road", "MTB", "Time Trial", "Criterium")

private fun workoutTypeStyle(type: String): Triple<Color, Color, androidx.compose.ui.graphics.vector.ImageVector> = when (type) {
    "Recovery" -> Triple(TGreen50, TGreen700, Icons.Default.Eco)
    "Intervals" -> Triple(TCoralBg, TCoralIcon, Icons.Default.Bolt)
    "Race" -> Triple(TRaceBg, TRaceIcon, Icons.Default.EmojiEvents)
    else -> Triple(TBlueBg, TBlueIcon, Icons.Default.Route) // Endurance, Long Ride
}

private val planDateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
private val planDayMonthFormatter = SimpleDateFormat("MMM d", Locale.getDefault())
private val planDayFormatter = SimpleDateFormat("d", Locale.getDefault())
private val planYearFormatter = SimpleDateFormat("yyyy", Locale.getDefault())

private fun formatPlanDate(millis: Long): String = planDateFormatter.format(java.util.Date(millis))

// Collapses the common cases so the range stays short enough to fit on one line:
// same month → "Aug 8 – 14, 2026", same year → "Aug 8 – Sep 3, 2026", else full dates both sides.
private fun formatPlanDateRange(createdAt: Long, archivedAt: Long): String {
    val start = java.util.Calendar.getInstance().apply { timeInMillis = createdAt }
    val end = java.util.Calendar.getInstance().apply { timeInMillis = archivedAt }
    val sameYear = start.get(java.util.Calendar.YEAR) == end.get(java.util.Calendar.YEAR)
    val sameMonth = sameYear && start.get(java.util.Calendar.MONTH) == end.get(java.util.Calendar.MONTH)
    val startDate = java.util.Date(createdAt)
    val endDate = java.util.Date(archivedAt)
    return when {
        sameMonth -> "${planDayMonthFormatter.format(startDate)} \u2013 ${planDayFormatter.format(endDate)}, ${planYearFormatter.format(endDate)}"
        sameYear -> "${planDayMonthFormatter.format(startDate)} \u2013 ${planDayMonthFormatter.format(endDate)}, ${planYearFormatter.format(endDate)}"
        else -> "${formatPlanDate(createdAt)} \u2013 ${formatPlanDate(archivedAt)}"
    }
}

// Active plan → when it started. Archived plan → the span it ran for, since
// that's the meaningful "history" info for a past plan.
private fun planDateLabel(plan: TrainingPlan, isActive: Boolean): String {
    val created = plan.createdAt.takeIf { it > 0 }
    val archived = plan.archivedAt?.takeIf { it > 0 }
    return when {
        isActive && created != null -> "Started ${formatPlanDate(created)}"
        !isActive && created != null && archived != null -> formatPlanDateRange(created, archived)
        !isActive && archived != null -> "Archived ${formatPlanDate(archived)}"
        created != null -> "Started ${formatPlanDate(created)}"
        else -> ""
    }
}

@Composable
private fun WorkoutProgressBar(actualKm: Double, plannedKm: Double, completed: Boolean) {
    val fraction = if (plannedKm > 0) (actualKm / plannedKm).toFloat().coerceIn(0f, 1f) else 0f
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
        color = if (completed) TGreen700 else TPartialOrange,
        trackColor = TDivider
    )
}

@Composable
private fun WorkoutList(
    workouts: List<TrainingWorkout>,
    onEdit: (TrainingWorkout) -> Unit,
    onToggle: (TrainingWorkout) -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        workouts.forEach { workout ->
            val (bg, iconTint, icon) = workoutTypeStyle(workout.type)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = TBgSurface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(bg),
                            contentAlignment = Alignment.Center
                        ) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp)) }

                        Column(
                            Modifier.weight(1f).clickable { onEdit(workout) }
                        ) {
                            Text(
                                workout.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                                color = if (workout.completed) TTextMuted else TTextPrimary,
                                textDecoration = if (workout.completed) TextDecoration.LineThrough else null
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                when {
                                    workout.inProgress -> "In progress — check the Map tab"
                                    workout.failed -> "Workout failed"
                                    workout.actualDistanceKm != null && !workout.completed && !workout.failed ->
                                        "${workout.type} · ${workout.difficulty} · ${String.format("%.1f", workout.actualDistanceKm)}/${String.format("%.0f", workout.distanceKm)} km so far"
                                    else -> buildString {
                                        append("${workout.type} · ")
                                        when (workout.type) {
                                            "Recovery" -> {
                                                append("${workout.durationMin} min")
                                                workout.hrZone?.let { append(" · $it") }
                                                workout.targetCadence?.let { append(" · $it RPM") }
                                            }
                                            "Endurance" -> {
                                                append("${String.format("%.0f", workout.distanceKm)} km · ${workout.durationMin} min")
                                                workout.hrZone?.let { append(" · $it") }
                                            }
                                            "Intervals" -> {
                                                workout.numIntervals?.let { append("$it Intervals · ") }
                                                append("${workout.workDurationMin}/${workout.recoveryDurationMin} min")
                                                append(" · ${workout.difficulty}")
                                            }
                                            "Long Ride" -> {
                                                append("${String.format("%.0f", workout.distanceKm)} km · ${workout.durationMin} min")
                                                workout.elevationM?.let { append(" · ${String.format("%.0f", it)}m ↑") }
                                            }
                                            "Race" -> {
                                                append("${String.format("%.0f", workout.distanceKm)} km · Target ${workout.targetFinishTime}")
                                                workout.raceType?.let { append(" · $it") }
                                            }
                                            else -> {
                                                append("${workout.durationMin} min")
                                                if (workout.distanceKm > 0) append(" · ${String.format("%.0f", workout.distanceKm)} km")
                                            }
                                        }
                                    }
                                },
                                fontSize = 12.sp,
                                color = when {
                                    workout.inProgress -> TInProgressRed
                                    workout.failed -> Color.Red
                                    else -> TTextMuted
                                }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        workout.completed -> TGreen700
                                        workout.failed -> Color.Red
                                        else -> Color(0xFFEDEFEE)
                                    }
                                )
                                .clickable(enabled = !workout.inProgress) { onToggle(workout) },
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                workout.completed -> Icon(Icons.Default.Check, "Done", tint = Color.White, modifier = Modifier.size(18.dp))
                                workout.failed -> Icon(Icons.Default.Close, "Failed", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    if (workout.actualDistanceKm != null && !workout.inProgress && !workout.failed) {
                        Spacer(Modifier.height(10.dp))
                        WorkoutProgressBar(workout.actualDistanceKm, workout.distanceKm, workout.completed)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    navController: NavController,
    userName: String,
    paddingValues: PaddingValues = PaddingValues()
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val plansCollection = remember(userName) { db.collection("trainingPlans").document(userName).collection("plans") }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var plan by remember(userName) { mutableStateOf<TrainingPlan?>(null) }
    var archivedPlans by remember(userName) { mutableStateOf<List<TrainingPlan>>(emptyList()) }
    var isLoadingPlan by remember(userName) { mutableStateOf(true) }
    var currentWeekIndex by remember(userName) { mutableIntStateOf(0) }
    var selectedDayIndex by remember(userName) { mutableIntStateOf(0) } // which day tab is active

    var showCreatePlanDialog by remember { mutableStateOf(false) }
    var showEditPlanDialog by remember { mutableStateOf(false) }
    var showWorkoutDialog by remember { mutableStateOf(false) }
    var showArchiveConfirmDialog by remember { mutableStateOf(false) }
    var editingWorkout by remember { mutableStateOf<TrainingWorkout?>(null) } // null = creating new

    LaunchedEffect(userName) {
        plansCollection.whereEqualTo("isActive", true).limit(1)
            .addSnapshotListener { snap, _ ->
                plan = snap?.documents?.firstOrNull()?.data?.let { documentToPlan(it) }
                isLoadingPlan = false
            }
        plansCollection.whereEqualTo("isActive", false)
            .orderBy("archivedAt", Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snap, _ ->
                archivedPlans = snap?.documents
                    ?.mapNotNull { it.data?.let { d -> documentToPlan(d) } }
                    ?: emptyList()
            }
    }

    fun persist(updated: TrainingPlan) {
        plan = updated
        plansCollection.document(updated.id).set(updated.toMap(), SetOptions.merge())
        // Note: no failure handling/rollback — a failed write leaves the optimistic
        // state until the next snapshot silently corrects it.
    }

    fun toggleWorkout(workoutId: String) {
        val current = plan ?: return
        val targetWorkout = current.weeks.flatMap { it.workouts }.find { it.id == workoutId } ?: return
        val gid = targetWorkout.groupId

        // Automated logic: compare actuals with planned, via the shared
        // evaluateWorkoutGoal used by the post-ride flow too, so a manual
        // toggle here and an automatic ride completion can't disagree.
        val actualKm = targetWorkout.actualDistanceKm ?: 0.0
        val actualMin = targetWorkout.actualDurationMin ?: 0
        val isGoalMet = evaluateWorkoutGoal(targetWorkout, actualKm, actualMin)

        persist(current.copy(weeks = current.weeks.map { w ->
            w.copy(workouts = w.workouts.map {
                if (it.groupId == gid) {
                    when {
                        // If already completed or failed, toggle back to pending
                        it.completed || it.failed -> it.copy(completed = false, failed = false, inProgress = false)
                        // Otherwise, decide based on accomplishment
                        isGoalMet -> it.copy(completed = true, failed = false, inProgress = false)
                        else -> it.copy(completed = false, failed = true, inProgress = false)
                    }
                } else it
            })
        }))
    }

    fun upsertWorkouts(weekNumber: Int, newWorkouts: List<TrainingWorkout>, removeIds: List<String> = emptyList()) {
        val current = plan ?: return
        val commonGroupId = newWorkouts.firstOrNull()?.groupId ?: UUID.randomUUID().toString()

        persist(current.copy(weeks = current.weeks.map { w ->
            if (w.weekNumber != weekNumber) w
            else {
                val updatedWorkouts = w.workouts.filterNot { it.id in removeIds }.toMutableList()
                newWorkouts.forEach { workout ->
                    val workoutWithGroup = workout.copy(groupId = commonGroupId)
                    val index = updatedWorkouts.indexOfFirst { it.id == workout.id }
                    if (index >= 0) {
                        updatedWorkouts[index] = workoutWithGroup
                    } else {
                        val nextOrder = (updatedWorkouts.filter { it.dayOfWeek == workout.dayOfWeek }.maxOfOrNull { it.order } ?: -1) + 1
                        updatedWorkouts.add(workoutWithGroup.copy(order = nextOrder))
                    }
                }
                w.copy(workouts = updatedWorkouts.sortedWith(compareBy({ it.dayOfWeek }, { it.order })))
            }
        }))
    }


    fun deleteWorkoutGroup(weekNumber: Int, workoutIds: List<String>) {
        val current = plan ?: return
        persist(current.copy(weeks = current.weeks.map { w ->
            if (w.weekNumber != weekNumber) w
            else w.copy(workouts = w.workouts.filterNot { it.id in workoutIds })
        }))
    }

    fun addWeek() {
        val current = plan ?: return
        val newWeekNum = (current.weeks.maxOfOrNull { it.weekNumber } ?: 0) + 1
        persist(current.copy(
            totalWeeks = maxOf(current.totalWeeks, newWeekNum),
            weeks = current.weeks + TrainingWeek(newWeekNum, emptyList())
        ))
        currentWeekIndex = current.weeks.size
    }

    fun createPlan(name: String, description: String) {
        val newPlan = TrainingPlan(
            name = name.ifBlank { "My Training Plan" },
            description = description,
            totalWeeks = 1,
            weeks = listOf(TrainingWeek(1, emptyList()))
        )
        plansCollection.document(newPlan.id).set(newPlan.toMap())
    }

    fun archiveCurrentAndCreate(name: String, description: String) {
        val current = plan
        if (current != null) {
            plansCollection.document(current.id)
                .set(current.copy(isActive = false, archivedAt = System.currentTimeMillis()).toMap(), SetOptions.merge())
        }
        createPlan(name, description)
    }

    fun updatePlanDetails(name: String, description: String) {
        val current = plan ?: return
        persist(current.copy(name = name.ifBlank { "My Training Plan" }, description = description))
    }

    fun deletePlan() {
        val current = plan ?: return
        plansCollection.document(current.id).delete()
        plan = null
    }

    fun restorePlan(target: TrainingPlan) {
        val current = plan
        if (current != null) {
            plansCollection.document(current.id)
                .set(current.copy(isActive = false, archivedAt = System.currentTimeMillis()).toMap(), SetOptions.merge())
        }
        plansCollection.document(target.id)
            .set(target.copy(isActive = true, archivedAt = null).toMap(), SetOptions.merge())
        scope.launch { drawerState.close() }
    }

    fun deleteArchivedPlan(target: TrainingPlan) {
        plansCollection.document(target.id).delete()
    }
    // ── Dialogs ──────────────────────────────────────────────────────────
    var pendingNewPlan by remember { mutableStateOf<Pair<String, String>?>(null) }

    if (showCreatePlanDialog) {
        PlanFormDialog(
            existing = null,
            onDismiss = { showCreatePlanDialog = false },
            onSave = { name, description ->
                showCreatePlanDialog = false
                if (plan != null) {
                    pendingNewPlan = name to description
                    showArchiveConfirmDialog = true
                } else {
                    createPlan(name, description)
                }
            },
            onDelete = null
        )
    }

    if (showArchiveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirmDialog = false; pendingNewPlan = null },
            title = { Text("Archive current training plan?") },
            text = { Text("\"${plan?.name}\" will move to your training plan history and a new active plan will start. You can restore it later from the drawer.") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingNewPlan?.let { (name, description) -> archiveCurrentAndCreate(name, description) }
                        showArchiveConfirmDialog = false
                        pendingNewPlan = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TGreen900, contentColor = Color.White)
                ) { Text("Archive & Start New") }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirmDialog = false; pendingNewPlan = null }) { Text("Cancel") }
            }
        )
    }

    if (showEditPlanDialog) {
        PlanFormDialog(
            existing = plan,
            onDismiss = { showEditPlanDialog = false },
            onSave = { name, description ->
                updatePlanDetails(name, description)
                showEditPlanDialog = false
            },
            onDelete = {
                deletePlan()
                showEditPlanDialog = false
            }
        )
    }

    val weekNumberForDialog = plan?.weeks?.getOrNull(currentWeekIndex)?.weekNumber ?: 1
    val weekWorkoutsForDialog = plan?.weeks?.getOrNull(currentWeekIndex)?.workouts ?: emptyList()
    if (showWorkoutDialog) {
        WorkoutFormDialog(
            existing = editingWorkout,
            weekWorkouts = weekWorkoutsForDialog,
            initialDayOfWeek = selectedDayIndex,
            onDismiss = { showWorkoutDialog = false; editingWorkout = null },
            onSave = { workouts, removedIds ->
                upsertWorkouts(weekNumberForDialog, workouts, removedIds)
                showWorkoutDialog = false; editingWorkout = null
            },
            onDeleteDay = editingWorkout?.let { w ->
                {
                    deleteWorkoutGroup(weekNumberForDialog, listOf(w.id))
                    showWorkoutDialog = false; editingWorkout = null
                }
            },
            onDeleteGroup = editingWorkout?.let { w ->
                {
                    val groupIds = weekWorkoutsForDialog.filter { it.groupId == w.groupId }.map { it.id }
                    deleteWorkoutGroup(weekNumberForDialog, groupIds)
                    showWorkoutDialog = false; editingWorkout = null
                }
            }
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pounding")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    PlanHistoryDrawer(
                        activePlan = plan,
                        archivedPlans = archivedPlans,
                        onNewPlan = {
                            scope.launch { drawerState.close() }
                            showCreatePlanDialog = true
                        },
                        onRestore = { restorePlan(it) },
                        onDelete = { deleteArchivedPlan(it) },
                        onClose = { scope.launch { drawerState.close() } },
                        onViewAll = {
                            scope.launch { drawerState.close() }
                            navController.navigate("plan_history/$userName")
                        }
                    )
                }
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                            }
                                    )
                                    Text("Training Mode", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.White)
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                                }
                            },
                            actions = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.List, "Training plan history", tint = Color.White)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = TGreen900),
                            modifier = Modifier.shadow(elevation = 2.dp)
                        )
                    },
                    floatingActionButton = {
                        if (plan != null) {
                            FloatingActionButton(
                                onClick = { editingWorkout = null; showWorkoutDialog = true },
                                containerColor = TGreen900,
                                contentColor = Color.White
                            ) { Icon(Icons.Default.Add, "Add workout for ${DAY_LABELS[selectedDayIndex]}") }
                        }
                    },
                    containerColor = TBgCanvas
                ) { innerPadding ->

                    if (isLoadingPlan) {
                        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = TGreen900, strokeWidth = 2.5.dp, modifier = Modifier.size(32.dp))
                        }
                        return@Scaffold
                    }

                    val currentPlan = plan
                    if (currentPlan == null) {
                        EmptyTrainingState(onStartPlan = { showCreatePlanDialog = true }, padding = innerPadding)
                        return@Scaffold
                    }

                    val allWorkouts = currentPlan.weeks.flatMap { it.workouts }
                    val completedCount = allWorkouts.count { it.completed }
                    val week = currentPlan.weeks.getOrNull(currentWeekIndex) ?: currentPlan.weeks.first()
                    val selectedDayWorkouts = week.workouts.filter { it.dayOfWeek == selectedDayIndex }.sortedBy { it.order }

                    val pendingWorkouts = currentPlan.weeks
                        .sortedBy { it.weekNumber }
                        .flatMap { w -> w.workouts.sortedWith(compareBy({ it.dayOfWeek }, { it.order })).map { w.weekNumber to it } }
                        .filter { !it.second.completed }

                    Column(
                        modifier = Modifier.fillMaxSize().padding(innerPadding)
                    ) {
                        // ── Next up section — now a scrollable row showing all pending workouts ──
                        if (pendingWorkouts.isNotEmpty()) {
                            Column(Modifier.padding(top = 16.dp)) {
                                Text(
                                    "Next up / Pending",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TTextMuted
                                )
                                androidx.compose.foundation.lazy.LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(pendingWorkouts) { (weekNum, workout) ->
                                        val (bg, iconTint, icon) = workoutTypeStyle(workout.type)
                                        Card(
                                            modifier = Modifier.width(300.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = TBgSurface),
                                            elevation = CardDefaults.cardElevation(2.dp)
                                        ) {
                                            Column(Modifier.padding(16.dp)) {
                                                Text("Week $weekNum · ${DAY_LABELS[workout.dayOfWeek]}", fontSize = 11.sp, color = TTextMuted)
                                                Spacer(Modifier.height(8.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    Box(
                                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(bg),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
                                                    }
                                                    Column(Modifier.weight(1f)) {
                                                        Text(workout.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Text(
                                                            buildString {
                                                                when (workout.type) {
                                                                    "Recovery" -> {
                                                                        append("${workout.durationMin} min")
                                                                        workout.hrZone?.let { append(" · $it") }
                                                                    }
                                                                    "Endurance" -> {
                                                                        append("${String.format("%.0f", workout.distanceKm)} km · ${workout.durationMin} min")
                                                                        workout.hrZone?.let { append(" · $it") }
                                                                    }
                                                                    "Intervals" -> {
                                                                        workout.numIntervals?.let { append("$it Intervals · ") }
                                                                        append("${workout.workDurationMin}/${workout.recoveryDurationMin} min")
                                                                    }
                                                                    "Long Ride" -> {
                                                                        append("${String.format("%.0f", workout.distanceKm)} km · ${workout.durationMin} min")
                                                                    }
                                                                    "Race" -> {
                                                                        append("${String.format("%.0f", workout.distanceKm)} km · Target ${workout.targetFinishTime}")
                                                                    }
                                                                    else -> {
                                                                        append("${workout.durationMin} min · ${workout.difficulty}")
                                                                        if (workout.distanceKm > 0) append(" · ${String.format("%.0f", workout.distanceKm)} km")
                                                                    }
                                                                }
                                                            },
                                                            fontSize = 12.sp, color = TTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    val isStarted = workout.inProgress || (workout.actualDistanceKm ?: 0.0) > 0.0 || (workout.actualDurationMin ?: 0) > 0
                                                    Button(
                                                        onClick = {
                                                            val startId = UUID.randomUUID().toString()
                                                            navController.navigate("home/$userName?linkedWeek=$weekNum&linkedWorkoutId=${workout.id}&autoStart=true&startId=$startId")
                                                        },
                                                        shape = RoundedCornerShape(10.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = TGreen900, contentColor = Color.White),
                                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                                    ) { Text(if (isStarted) "Resume" else "Start", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (allWorkouts.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 16.dp, end = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = TGreen50)
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.EmojiEvents, null, tint = TGreen700)
                                    Text("All workouts complete — plan finished!", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TGreen900)
                                }
                            }
                        }

                        // ── Plan name + overall progress — tap to edit plan details ───
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                                .clickable { showEditPlanDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = TBgSurface),
                            elevation = CardDefaults.cardElevation(1.dp)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(currentPlan.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TTextPrimary)
                                        Icon(Icons.Default.Edit, "Edit plan", tint = TTextMuted, modifier = Modifier.size(14.dp))
                                    }
                                    if (allWorkouts.isNotEmpty()) {
                                        Text("$completedCount of ${allWorkouts.size} done", fontSize = 12.sp, color = TTextSecondary)
                                    }
                                }
                                if (currentPlan.description.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(currentPlan.description, fontSize = 12.sp, color = TTextMuted, lineHeight = 17.sp)
                                }
                                if (allWorkouts.isNotEmpty()) {
                                    Spacer(Modifier.height(10.dp))
                                    LinearProgressIndicator(
                                        progress = { completedCount / allWorkouts.size.toFloat() },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = TGreen700,
                                        trackColor = TDivider
                                    )
                                } else {
                                    Spacer(Modifier.height(4.dp))
                                    Text("No workouts added yet", fontSize = 12.sp, color = TTextMuted)
                                }
                            }
                        }

                        // ── Week nav ────────────────────────────────────────────────
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { if (currentWeekIndex > 0) currentWeekIndex-- },
                                enabled = currentWeekIndex > 0
                            ) { Icon(Icons.Default.ChevronLeft, "Previous week", tint = if (currentWeekIndex > 0) TGreen900 else TTextMuted) }
                            Text("Week ${week.weekNumber}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TTextPrimary)
                            IconButton(onClick = {
                                if (currentWeekIndex < currentPlan.weeks.lastIndex) currentWeekIndex++ else addWeek()
                            }) {
                                if (currentWeekIndex < currentPlan.weeks.lastIndex)
                                    Icon(Icons.Default.ChevronRight, "Next week", tint = TGreen900)
                                else
                                    Icon(Icons.Default.AddCircleOutline, "Add week", tint = TGreen900)
                            }
                        }

                        // ── Day selector — rounded tile with a status dot, matches app-wide card language ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TBgSurface)
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DAY_LABELS.forEachIndexed { dayIndex, label ->
                                val dayWorkouts = week.workouts.filter { it.dayOfWeek == dayIndex }
                                val isSelected = selectedDayIndex == dayIndex
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isSelected) TGreen50 else Color.Transparent)
                                        .clickable { selectedDayIndex = dayIndex }
                                        .width(42.dp)
                                        .padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        label,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (isSelected) TGreen900 else TTextMuted
                                    )
                                    val allDone = dayWorkouts.isNotEmpty() && dayWorkouts.all { it.completed }
                                    val anyFailed = dayWorkouts.any { it.failed }
                                    val emptyDot = Color(0xFFEBEDEC)
                                    val dotColor = when {
                                        dayWorkouts.isEmpty() -> emptyDot
                                        anyFailed -> Color.Red
                                        dayWorkouts.any { it.inProgress } -> TInProgressRed
                                        allDone -> TGreen700
                                        dayWorkouts.any { it.completed || it.actualDistanceKm != null } -> TPartialOrange
                                        else -> emptyDot
                                    }
                                    Box(
                                        modifier = Modifier.size(22.dp).clip(CircleShape).background(dotColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        when {
                                            allDone -> Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            anyFailed -> Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            dayWorkouts.size > 1 -> Text(
                                                "${dayWorkouts.size}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                                color = if (dotColor == emptyDot) TTextMuted else Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = TDivider, thickness = 1.dp)

                        // ── Selected day's content — this replaces the old combined week list ──
                        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            if (selectedDayWorkouts.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(TBgSurface)
                                        .padding(vertical = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(48.dp).clip(CircleShape).background(TGreen50),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.DirectionsBike, null, tint = TGreen700, modifier = Modifier.size(22.dp))
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text("No workout planned for ${DAY_LABELS[selectedDayIndex]}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TTextPrimary)
                                }
                            } else {
                                WorkoutList(
                                    workouts = selectedDayWorkouts,
                                    onEdit = { editingWorkout = it; showWorkoutDialog = true },
                                    onToggle = { toggleWorkout(it.id) }
                                )
                            }
                        }
                    }
                } // closes inner LTR CompositionLocalProvider (main content)
            }
        } // closes ModalNavigationDrawer
    } // closes outer RTL CompositionLocalProvider
}

@Composable
private fun PlanHistoryDrawer(
    activePlan: TrainingPlan?,
    archivedPlans: List<TrainingPlan>,
    onNewPlan: () -> Unit,
    onRestore: (TrainingPlan) -> Unit,
    onDelete: (TrainingPlan) -> Unit,
    onClose: () -> Unit,
    onViewAll: () -> Unit
) {
    var planPendingDelete by remember { mutableStateOf<TrainingPlan?>(null) }

    ModalDrawerSheet(
        drawerContainerColor = TBgCanvas,
        modifier = Modifier.widthIn(max = 320.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(vertical = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Training Plan History", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TTextPrimary)
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFF3F4F6))
                ) {
                    Icon(Icons.Default.Close, "Close", tint = TTextMuted, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onNewPlan,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TGreen900, contentColor = Color.White)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New Training Plan", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            Spacer(Modifier.height(20.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (activePlan != null) {
                    activePlanSection(activePlan)
                }
                if (archivedPlans.isNotEmpty()) {
                    pastPlansSection(
                        plans = archivedPlans,
                        onRestore = onRestore,
                        onDeleteRequest = { planPendingDelete = it }
                    )
                    if (archivedPlans.size >= 5) {
                        item {
                            TextButton(
                                onClick = onViewAll,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("View All Training Plans", color = TGreen700, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                } else if (activePlan != null) {
                    emptyHintSection("No past training plans yet")
                }
            }
        }
    }

    planPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { planPendingDelete = null },
            title = { Text("Delete \"${target.name}\"?") },
            text = { Text("This permanently removes the training plan and its workout history. This can't be undone.") },
            confirmButton = {
                Button(
                    onClick = { onDelete(target); planPendingDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { planPendingDelete = null }) { Text("Cancel") } }
        )
    }
}

private fun LazyListScope.activePlanSection(activePlan: TrainingPlan) {
    item {
        Text(
            "ACTIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TGreen700,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
    item { PlanHistoryCard(plan = activePlan, isActive = true) }
}

private fun LazyListScope.pastPlansSection(
    plans: List<TrainingPlan>,
    onRestore: (TrainingPlan) -> Unit,
    onDeleteRequest: (TrainingPlan) -> Unit
) {
    item {
        Text(
            "PAST PLANS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TTextMuted,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 2.dp)
        )
    }
    items(plans) { p ->
        PlanHistoryCard(
            plan = p,
            isActive = false,
            onRestore = { onRestore(p) },
            onDelete = { onDeleteRequest(p) }
        )
    }
}

private fun LazyListScope.emptyHintSection(text: String) {
    item {
        Text(
            text, fontSize = 13.sp, color = TTextMuted,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
        )
    }
}

@Composable
internal fun PlanHistoryCard(
    plan: TrainingPlan,
    isActive: Boolean,
    onRestore: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val completed = plan.weeks.flatMap { it.workouts }.count { it.completed }
    val total = plan.weeks.flatMap { it.workouts }.size
    val progress = if (total > 0) completed / total.toFloat() else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TBgSurface),
        elevation = CardDefaults.cardElevation(if (isActive) 2.dp else 1.dp),
        border = if (isActive) BorderStroke(1.dp, TGreen100) else null
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isActive) TGreen50 else TBlueBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isActive) Icons.AutoMirrored.Filled.DirectionsBike else Icons.Default.History,
                        null,
                        tint = if (isActive) TGreen700 else TBlueIcon,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(plan.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val dateLabel = remember(plan.id, plan.createdAt, plan.archivedAt, isActive) {
                        planDateLabel(plan, isActive)
                    }
                    if (dateLabel.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                Icons.Default.DateRange, null,
                                tint = TTextMuted,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                dateLabel, fontSize = 10.sp, color = TTextMuted,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (total > 0) "$completed of $total workouts done" else "No workouts added",
                        fontSize = 11.sp, color = TTextMuted
                    )
                }
                if (!isActive) {
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        IconButton(
                            onClick = { onRestore?.invoke() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Restore, "Restore", tint = TGreen700, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { onDelete?.invoke() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, "Delete", tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            if (total > 0) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = if (isActive) TGreen700 else TBlueIcon,
                    trackColor = TDivider
                )
            }
        }
    }
}

@Composable
private fun EmptyTrainingState(onStartPlan: () -> Unit, padding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(TGreen50),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.DirectionsBike, null, tint = TGreen700, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("No active training plan", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TTextPrimary)
        Spacer(Modifier.height(6.dp))
        Text("Create a plan, then add your own workouts day by day.", fontSize = 13.sp, color = TTextMuted)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onStartPlan,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TGreen900, contentColor = Color.White)
        ) { Text("Create a Plan", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun PlanFormDialog(
    existing: TrainingPlan?,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = TBgSurface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    if (existing == null) "Create Training Plan" else "Edit Plan",
                    fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TTextPrimary
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Training plan name") },
                    placeholder = { Text("e.g. Race Week") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TGreen700, focusedLabelColor = TGreen700, cursorColor = TGreen700,
                        focusedTextColor = TTextPrimary, unfocusedTextColor = TTextPrimary
                    )
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TGreen700, focusedLabelColor = TGreen700, cursorColor = TGreen700,
                        focusedTextColor = TTextPrimary, unfocusedTextColor = TTextPrimary
                    )
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626))) {
                        Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete plan")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TTextSecondary)
                    ) { Text("Cancel") }
                    Button(
                        onClick = { onSave(name.trim(), description.trim()) },
                        modifier = Modifier.weight(1f),
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = TGreen900, contentColor = Color.White)
                    ) { Text(if (existing == null) "Create" else "Save") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutFormDialog(
    existing: TrainingWorkout?,
    weekWorkouts: List<TrainingWorkout> = emptyList(),
    initialDayOfWeek: Int = 0,
    onDismiss: () -> Unit,
    onSave: (List<TrainingWorkout>, List<String>) -> Unit,
    onDeleteDay: (() -> Unit)?,
    onDeleteGroup: (() -> Unit)?
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var titleManuallyEdited by remember { mutableStateOf(existing != null) }
    var type by remember { mutableStateOf(existing?.type ?: WORKOUT_TYPES.first()) }
    var difficulty by remember { mutableStateOf(existing?.difficulty ?: (TYPE_DIFFICULTIES[type]?.first() ?: "Medium")) }
    val siblingsByDay = remember(existing, weekWorkouts) {
        if (existing == null) emptyMap()
        else weekWorkouts.filter { it.groupId == existing.groupId }.associateBy { it.dayOfWeek }
    }
    var selectedDays by remember { mutableStateOf(existing?.let { siblingsByDay.keys.ifEmpty { setOf(it.dayOfWeek) } } ?: setOf(initialDayOfWeek)) }

    // Core fields
    var durationText by remember { mutableStateOf(existing?.durationMin?.toString() ?: "") }
    var distanceText by remember { mutableStateOf(existing?.distanceKm?.let { if (it == 0.0) "" else it.toString() } ?: "") }

    // Dynamic fields
    var hrZone by remember { mutableStateOf(existing?.hrZone ?: "") }
    var targetCadence by remember { mutableStateOf(existing?.targetCadence?.toString() ?: "") }
    var numIntervals by remember { mutableStateOf(existing?.numIntervals?.toString() ?: "") }
    var workDuration by remember { mutableStateOf(existing?.workDurationMin?.toString() ?: "") }
    var recoveryDuration by remember { mutableStateOf(existing?.recoveryDurationMin?.toString() ?: "") }
    var elevationGain by remember { mutableStateOf(existing?.elevationM?.toString() ?: "") }
    var targetFinishTime by remember { mutableStateOf(existing?.targetFinishTime ?: "") }
    var raceType by remember { mutableStateOf(existing?.raceType ?: RACE_TYPES.first()) }

    // Initialize fields based on type and difficulty if creating new
    LaunchedEffect(type) {
        if (existing == null) {
            val valid = TYPE_DIFFICULTIES[type] ?: emptyList()
            if (difficulty !in valid) {
                difficulty = valid.firstOrNull() ?: "Medium"
            }
        }
    }

    LaunchedEffect(type, difficulty) {
        if (existing == null) {
            if (!titleManuallyEdited) title = "$difficulty $type"
            when (type) {
                "Recovery" -> {
                    hrZone = "Zone 1"
                    when (difficulty) {
                        "Light Recovery" -> { durationText = "25"; targetCadence = "82" }
                        "Active Recovery" -> { durationText = "37"; targetCadence = "87" }
                        "Extended Recovery" -> { durationText = "52"; targetCadence = "92" }
                    }
                }
                "Endurance" -> {
                    hrZone = "Zone 2"
                    when (difficulty) {
                        "Base Builder" -> { distanceText = "20"; durationText = "60" }
                        "Distance Builder" -> { distanceText = "40"; durationText = "120" }
                        "Endurance Challenge" -> { distanceText = "85"; durationText = "240" }
                    }
                }
                "Intervals" -> {
                    when (difficulty) {
                        "Intro Intervals" -> { numIntervals = "4"; workDuration = "1"; recoveryDuration = "2" }
                        "Power Intervals" -> { numIntervals = "6"; workDuration = "2"; recoveryDuration = "2" }
                        "VO₂ Max Intervals" -> { numIntervals = "9"; workDuration = "4"; recoveryDuration = "2" }
                    }
                }
                "Long Ride" -> {
                    when (difficulty) {
                        "Weekend Ride" -> { distanceText = "40"; durationText = "120" }
                        "Century Prep" -> { distanceText = "80"; durationText = "240" }
                        "Epic Ride" -> { distanceText = "120"; durationText = "360" }
                    }
                }
                "Race" -> {
                    when (difficulty) {
                        "Practice Race" -> { distanceText = "30"; targetFinishTime = "Beginner pace" }
                        "Club Competition" -> { distanceText = "65"; targetFinishTime = "Moderate pace" }
                        "Championship" -> { distanceText = "100"; targetFinishTime = "Competitive pace" }
                    }
                }
            }
        }
    }

    val isValid by remember {
        derivedStateOf {
            selectedDays.isNotEmpty() && when (type) {
                "Recovery" -> durationText.isNotBlank()
                "Endurance" -> durationText.isNotBlank() && distanceText.isNotBlank()
                "Intervals" -> numIntervals.isNotBlank() && workDuration.isNotBlank() && recoveryDuration.isNotBlank()
                "Long Ride" -> durationText.isNotBlank() && distanceText.isNotBlank()
                "Race" -> distanceText.isNotBlank() && targetFinishTime.isNotBlank()
                else -> true
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = TBgSurface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                Modifier.padding(24.dp).fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    if (existing == null) "Plan Your Workout" else "Edit Workout",
                    fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TTextPrimary
                )

                // ── Basic Info ──
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; titleManuallyEdited = true },
                    label = { Text("Workout Title") },
                    placeholder = { Text("Auto-filled — edit to personalize") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TGreen700, focusedLabelColor = TGreen700, cursorColor = TGreen700,
                        focusedTextColor = TTextPrimary, unfocusedTextColor = TTextPrimary
                    )
                )

                TDropdownField(
                    label = "Training Type",
                    selected = type,
                    options = WORKOUT_TYPES,
                    onSelected = { type = it }
                )

                TDropdownField(
                    label = "Difficulty",
                    selected = difficulty,
                    options = TYPE_DIFFICULTIES[type] ?: emptyList(),
                    onSelected = { difficulty = it }
                )

                Column {
                    Text("Day(s)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TTextMuted, modifier = Modifier.padding(bottom = 8.dp))
                    if (existing != null && siblingsByDay.size > 1) {
                        Text(
                            "This workout repeats on multiple days — changes here apply to all of them.",
                            fontSize = 11.sp, color = TBlueIcon, modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DAY_LABELS.forEachIndexed { idx, label ->
                            FilterChip(
                                selected = selectedDays.contains(idx),
                                onClick = {
                                    selectedDays = if (selectedDays.contains(idx)) {
                                        if (selectedDays.size > 1) selectedDays - idx else selectedDays
                                    } else {
                                        selectedDays + idx
                                    }
                                },
                                label = { Text(label, fontSize = 12.sp) },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TGreen100, selectedLabelColor = TGreen900
                                )
                            )
                        }
                    }
                }

                HorizontalDivider(color = TDivider, thickness = 1.dp)

                // ── Dynamic Fields with Animation ──
                androidx.compose.animation.AnimatedContent(
                    targetState = type,
                    transitionSpec = {
                        (fadeIn() + expandVertically()).togetherWith(fadeOut() + shrinkVertically())
                    },
                    label = "form_fields"
                ) { targetType ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (targetType) {
                            "Recovery" -> {
                                Text("Recovery: Easy spin to flush legs.", fontSize = 12.sp, color = TTextMuted)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    TFormField(value = durationText, onValueChange = { durationText = it }, label = "Duration (min)", modifier = Modifier.weight(1f))
                                    TFormField(value = hrZone, onValueChange = { hrZone = it }, label = "HR Zone", modifier = Modifier.weight(1f))
                                }
                                TFormField(value = targetCadence, onValueChange = { targetCadence = it }, label = "Target Cadence (RPM)")
                            }
                            "Endurance" -> {
                                Text("Endurance: Build aerobic base.", fontSize = 12.sp, color = TTextMuted)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    TFormField(value = distanceText, onValueChange = { distanceText = it }, label = "Distance (km)", modifier = Modifier.weight(1f), isDecimal = true)
                                    TFormField(value = durationText, onValueChange = { durationText = it }, label = "Duration (min)", modifier = Modifier.weight(1f))
                                }
                                TFormField(value = hrZone, onValueChange = { hrZone = it }, label = "HR Zone (e.g. Zone 2)")
                            }
                            "Intervals" -> {
                                Text("Intervals: Improve power and speed.", fontSize = 12.sp, color = TTextMuted)
                                TFormField(value = numIntervals, onValueChange = { numIntervals = it }, label = "Number of Intervals")
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    TFormField(value = workDuration, onValueChange = { workDuration = it }, label = "Work (min)", modifier = Modifier.weight(1f))
                                    TFormField(value = recoveryDuration, onValueChange = { recoveryDuration = it }, label = "Recovery (min)", modifier = Modifier.weight(1f))
                                }
                            }
                            "Long Ride" -> {
                                Text("Long Ride: Spend time in the saddle.", fontSize = 12.sp, color = TTextMuted)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    TFormField(value = distanceText, onValueChange = { distanceText = it }, label = "Distance (km)", modifier = Modifier.weight(1f), isDecimal = true)
                                    TFormField(value = durationText, onValueChange = { durationText = it }, label = "Est. Duration (min)", modifier = Modifier.weight(1f))
                                }
                                TFormField(value = elevationGain, onValueChange = { elevationGain = it }, label = "Elevation Gain (m, optional)", isDecimal = true)
                            }
                            "Race" -> {
                                Text("Race Prep: Simulation or race day.", fontSize = 12.sp, color = TTextMuted)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    TFormField(value = distanceText, onValueChange = { distanceText = it }, label = "Race Distance (km)", modifier = Modifier.weight(1f), isDecimal = true)
                                    TFormField(value = targetFinishTime, onValueChange = { targetFinishTime = it }, label = "Target Finish Time", modifier = Modifier.weight(1f), isNumeric = false)
                                }
                                Column {
                                    Text("Race Type", fontSize = 13.sp, color = TTextMuted, modifier = Modifier.padding(bottom = 6.dp))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        RACE_TYPES.forEach { rt ->
                                            FilterChip(
                                                selected = raceType == rt,
                                                onClick = { raceType = rt },
                                                label = { Text(rt) },
                                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TGreen100, selectedLabelColor = TGreen900)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (onDeleteDay != null || onDeleteGroup != null) {
                    val isGrouped = siblingsByDay.size > 1
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isGrouped && onDeleteDay != null) {
                            IconButton(
                                onClick = onDeleteDay,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFFAECEA))
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Delete ${DAY_LABELS[existing?.dayOfWeek ?: 0]} only",
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        if (onDeleteGroup != null) {
                            TextButton(
                                onClick = onDeleteGroup,
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626))
                            ) {
                                Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isGrouped) "Delete all days" else "Delete workout", fontSize = 13.sp)
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TTextSecondary)
                    ) { Text("Cancel", fontWeight = FontWeight.Medium) }
                    Button(
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                        onClick = {
                            val gid = existing?.groupId ?: UUID.randomUUID().toString()
                            val workouts = selectedDays.map { day ->
                                val sibling = siblingsByDay[day]
                                TrainingWorkout(
                                    id = sibling?.id ?: UUID.randomUUID().toString(),
                                    groupId = gid,
                                    title = title.trim().ifBlank { "$difficulty $type" },
                                    type = type,
                                    difficulty = difficulty,
                                    dayOfWeek = day,
                                    durationMin = durationText.toIntOrNull() ?: 0,
                                    distanceKm = distanceText.toDoubleOrNull() ?: 0.0,
                                    hrZone = hrZone.takeIf { it.isNotBlank() },
                                    targetCadence = targetCadence.toIntOrNull(),
                                    numIntervals = numIntervals.toIntOrNull(),
                                    workDurationMin = workDuration.toIntOrNull(),
                                    recoveryDurationMin = recoveryDuration.toIntOrNull(),
                                    elevationM = elevationGain.toDoubleOrNull(),
                                    targetFinishTime = targetFinishTime.takeIf { it.isNotBlank() },
                                    raceType = if(type == "Race") raceType else null,
                                    completed = sibling?.completed ?: false,
                                    failed = sibling?.failed ?: false,
                                    actualDurationMin = sibling?.actualDurationMin,
                                    actualDistanceKm = sibling?.actualDistanceKm,
                                    actualAvgSpeedKmh = sibling?.actualAvgSpeedKmh,
                                    inProgress = sibling?.inProgress ?: false
                                )
                            }
                            val removedIds = siblingsByDay.filterKeys { it !in selectedDays }.values.map { it.id }
                            onSave(workouts, removedIds)
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = isValid,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TGreen900, contentColor = Color.White)
                    ) { Text("Save Workout", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, softWrap = false) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TDropdownField(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontSize = 13.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TGreen700, unfocusedBorderColor = TDivider,
                focusedLabelColor = TGreen700, cursorColor = TGreen700,
                focusedTextColor = TTextPrimary, unfocusedTextColor = TTextPrimary
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TFormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isNumeric: Boolean = true,
    isDecimal: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            if (!isNumeric) onValueChange(input)
            else if (isDecimal) {
                if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) onValueChange(input)
            } else {
                if (input.all { it.isDigit() }) onValueChange(input)
            }
        },
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isNumeric) {
                if (isDecimal) KeyboardType.Decimal else KeyboardType.Number
            } else KeyboardType.Text
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TGreen700, unfocusedBorderColor = TDivider,
            focusedLabelColor = TGreen700, cursorColor = TGreen700,
            focusedTextColor = TTextPrimary, unfocusedTextColor = TTextPrimary
        )
    )
}