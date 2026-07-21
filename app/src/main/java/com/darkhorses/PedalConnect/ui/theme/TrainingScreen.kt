package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.UUID

// ── Design tokens ─────────────────────────────────────────────────────────
private val TGreen900  = Color(0xFF06402B)
private val TGreen700  = Color(0xFF0D7050)
private val TGreen100  = Color(0xFFDDF1E8)
private val TGreen50   = Color(0xFFE8F5EE)
private val TBgCanvas  = Color(0xFFF5F7F6)
private val TBgSurface = Color(0xFFFFFFFF)
private val TTextPrimary   = Color(0xFF111827)
private val TTextSecondary = Color(0xFF374151)
private val TTextMuted     = Color(0xFF6B7280)
private val TDivider       = Color(0xFFE5E7EB)
private val TCoralBg   = Color(0xFFFAECE7)
private val TCoralIcon = Color(0xFF993C1D)
private val TBlueBg    = Color(0xFFE6F1FB)
private val TBlueIcon  = Color(0xFF185FA5)
private val TRaceBg    = Color(0xFFF3E8FD)
private val TRaceIcon  = Color(0xFF7C3AED)
private val TPartialOrange = Color(0xFFF57C00)
private val TInProgressRed = Color(0xFFD32F2F)

// ── Data model ───────────────────────────────────────────────────────────
internal data class TrainingWorkout(
    val id: String,
    val groupId: String = UUID.randomUUID().toString(), // Shared for "continuing" across days
    val title: String,
    val type: String,        // "Endurance", "Intervals", "Recovery", "Long Ride", "Race"
    val dayOfWeek: Int,       // 0 = Mon ... 6 = Sun — multiple workouts per day supported
    val order: Int = 0,      // position within its day, user-reorderable via drag
    val durationMin: Int,
    val distanceKm: Double,
    val difficulty: String = "Medium", // "Easy", "Medium", "Hard", "Custom"
    var completed: Boolean = false,
    val failed: Boolean = false,
    val actualDurationMin: Int? = null,
    val actualDistanceKm: Double? = null,
    val actualAvgSpeedKmh: Double? = null,
    val inProgress: Boolean = false,
    // Dynamic fields
    val hrZone: String? = null,
    val targetCadence: Int? = null,
    val numIntervals: Int? = null,
    val workDurationMin: Int? = null,
    val recoveryDurationMin: Int? = null,
    val elevationM: Double? = null,
    val targetFinishTime: String? = null,
    val raceType: String? = null
)

internal data class TrainingWeek(
    val weekNumber: Int,
    val workouts: List<TrainingWorkout>
)

internal data class TrainingPlan(
    val name: String,
    val description: String,
    val totalWeeks: Int,
    val weeks: List<TrainingWeek>
)

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
internal val WORKOUT_TYPES = listOf("Recovery", "Endurance", "Intervals", "Long Ride", "Race")
internal val DIFFICULTIES = listOf("Easy", "Medium", "Hard", "Custom")
internal val RACE_TYPES = listOf("Road", "MTB", "Time Trial", "Criterium")
internal val INTENSITIES = listOf("Easy", "Moderate", "Hard")

// ── Firestore <-> model mapping ─────────────────────────────────────────
private fun TrainingWorkout.toMap(): Map<String, Any> = mapOf(
    "id" to id, "groupId" to groupId, "title" to title, "type" to type, "dayOfWeek" to dayOfWeek, "order" to order,
    "durationMin" to durationMin, "distanceKm" to distanceKm,
    "difficulty" to difficulty,
    "completed" to completed, "failed" to failed,
    "actualDurationMin" to (actualDurationMin ?: -1),
    "actualDistanceKm" to (actualDistanceKm ?: -1.0),
    "actualAvgSpeedKmh" to (actualAvgSpeedKmh ?: -1.0),
    "inProgress" to inProgress,
    "hrZone" to (hrZone ?: ""),
    "targetCadence" to (targetCadence ?: -1),
    "numIntervals" to (numIntervals ?: -1),
    "workDurationMin" to (workDurationMin ?: -1),
    "recoveryDurationMin" to (recoveryDurationMin ?: -1),
    "elevationM" to (elevationM ?: -1.0),
    "targetFinishTime" to (targetFinishTime ?: ""),
    "raceType" to (raceType ?: "")
)

private fun TrainingWeek.toMap(): Map<String, Any> = mapOf(
    "weekNumber" to weekNumber,
    "workouts" to workouts.map { it.toMap() }
)

private fun TrainingPlan.toMap(): Map<String, Any> = mapOf(
    "name" to name, "description" to description, "totalWeeks" to totalWeeks,
    "weeks" to weeks.map { it.toMap() }
)

@Suppress("UNCHECKED_CAST")
private fun mapToWorkout(m: Map<String, Any?>): TrainingWorkout = TrainingWorkout(
    id = m["id"] as? String ?: "",
    groupId = m["groupId"] as? String ?: (m["id"] as? String ?: UUID.randomUUID().toString()),
    title = m["title"] as? String ?: "",
    type = m["type"] as? String ?: "Endurance",
    dayOfWeek = (m["dayOfWeek"] as? Long)?.toInt() ?: 0,
    order = (m["order"] as? Long)?.toInt() ?: 0,
    durationMin = (m["durationMin"] as? Long)?.toInt() ?: 0,
    distanceKm = (m["distanceKm"] as? Number)?.toDouble() ?: 0.0,
    difficulty = m["difficulty"] as? String ?: "Medium",
    completed = m["completed"] as? Boolean ?: false,
    failed = m["failed"] as? Boolean ?: false,
    actualDurationMin = (m["actualDurationMin"] as? Long)?.toInt()?.takeIf { it >= 0 },
    actualDistanceKm = (m["actualDistanceKm"] as? Number)?.toDouble()?.takeIf { it >= 0 },
    actualAvgSpeedKmh = (m["actualAvgSpeedKmh"] as? Number)?.toDouble()?.takeIf { it >= 0 },
    inProgress = m["inProgress"] as? Boolean ?: false,
    hrZone = (m["hrZone"] as? String)?.takeIf { it.isNotBlank() },
    targetCadence = (m["targetCadence"] as? Long)?.toInt()?.takeIf { it >= 0 },
    numIntervals = (m["numIntervals"] as? Long)?.toInt()?.takeIf { it >= 0 },
    workDurationMin = (m["workDurationMin"] as? Long)?.toInt()?.takeIf { it >= 0 },
    recoveryDurationMin = (m["recoveryDurationMin"] as? Long)?.toInt()?.takeIf { it >= 0 },
    elevationM = (m["elevationM"] as? Number)?.toDouble()?.takeIf { it >= 0 },
    targetFinishTime = (m["targetFinishTime"] as? String)?.takeIf { it.isNotBlank() },
    raceType = (m["raceType"] as? String)?.takeIf { it.isNotBlank() }
)

@Suppress("UNCHECKED_CAST")
private fun mapToWeek(m: Map<String, Any?>): TrainingWeek = TrainingWeek(
    weekNumber = (m["weekNumber"] as? Long)?.toInt() ?: 0,
    workouts = (m["workouts"] as? List<Map<String, Any?>>)?.map { mapToWorkout(it) } ?: emptyList()
)

@Suppress("UNCHECKED_CAST")
private fun documentToPlan(data: Map<String, Any?>): TrainingPlan = TrainingPlan(
    name = data["name"] as? String ?: "Training Plan",
    description = data["description"] as? String ?: "",
    totalWeeks = (data["totalWeeks"] as? Long)?.toInt() ?: 0,
    weeks = (data["weeks"] as? List<Map<String, Any?>>)?.map { mapToWeek(it) } ?: emptyList()
)

private fun workoutTypeStyle(type: String): Triple<Color, Color, androidx.compose.ui.graphics.vector.ImageVector> = when (type) {
    "Recovery" -> Triple(TGreen50, TGreen700, Icons.Default.Eco)
    "Intervals" -> Triple(TCoralBg, TCoralIcon, Icons.Default.Bolt)
    "Race" -> Triple(TRaceBg, TRaceIcon, Icons.Default.EmojiEvents)
    else -> Triple(TBlueBg, TBlueIcon, Icons.Default.Route) // Endurance, Long Ride
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
private fun ReorderableWorkoutList(
    workouts: List<TrainingWorkout>,
    onEdit: (TrainingWorkout) -> Unit,
    onToggle: (TrainingWorkout) -> Unit,
    onReorder: (List<String>) -> Unit
) {
    var items by remember { mutableStateOf(workouts) }
    val itemHeights = remember { mutableStateMapOf<String, Int>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(workouts) {
        if (draggingId == null) items = workouts
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { workout ->
            val isDragging = workout.id == draggingId
            val (bg, iconTint, icon) = workoutTypeStyle(workout.type)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                    .onSizeChanged { itemHeights[workout.id] = it.height }
                    .then(
                        if (items.size > 1) Modifier.pointerInput(workout.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggingId = workout.id; dragOffset = 0f },
                                onDragEnd = {
                                    draggingId = null
                                    dragOffset = 0f
                                    onReorder(items.map { it.id })
                                },
                                onDragCancel = { draggingId = null; dragOffset = 0f },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val draggedIndex = items.indexOfFirst { it.id == workout.id }
                                    val itemHeight = itemHeights[workout.id] ?: return@detectDragGesturesAfterLongPress
                                    val slots = (dragOffset / itemHeight).toInt()
                                    if (slots != 0) {
                                        val targetIndex = (draggedIndex + slots).coerceIn(0, items.lastIndex)
                                        if (targetIndex != draggedIndex) {
                                            items = items.toMutableList().apply { add(targetIndex, removeAt(draggedIndex)) }
                                            dragOffset -= slots * itemHeight
                                        }
                                    }
                                }
                            )
                        } else Modifier
                    ),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = TBgSurface),
                elevation = CardDefaults.cardElevation(if (isDragging) 6.dp else 1.dp)
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

    var plan by remember(userName) { mutableStateOf<TrainingPlan?>(null) }
    var isLoadingPlan by remember(userName) { mutableStateOf(true) }
    var currentWeekIndex by remember(userName) { mutableIntStateOf(0) }
    var selectedDayIndex by remember(userName) { mutableIntStateOf(0) } // which day tab is active

    var showCreatePlanDialog by remember { mutableStateOf(false) }
    var showEditPlanDialog by remember { mutableStateOf(false) }
    var showWorkoutDialog by remember { mutableStateOf(false) }
    var editingWorkout by remember { mutableStateOf<TrainingWorkout?>(null) } // null = creating new

    LaunchedEffect(userName) {
        db.collection("trainingPlans").document(userName)
            .addSnapshotListener { snap, _ ->
                plan = if (snap != null && snap.exists()) documentToPlan(snap.data ?: emptyMap()) else null
                isLoadingPlan = false
            }
    }

    fun persist(updated: TrainingPlan) {
        plan = updated
        db.collection("trainingPlans").document(userName).set(updated.toMap(), SetOptions.merge())
        // Note: no failure handling/rollback — a failed write leaves the optimistic
        // state until the next snapshot silently corrects it.
    }

    fun toggleWorkout(workoutId: String) {
        val current = plan ?: return
        val targetWorkout = current.weeks.flatMap { it.workouts }.find { it.id == workoutId } ?: return
        val gid = targetWorkout.groupId

        // Automated logic: compare actuals with planned
        val actualKm = targetWorkout.actualDistanceKm ?: 0.0
        val actualMin = targetWorkout.actualDurationMin ?: 0
        val isGoalMet = actualKm >= targetWorkout.distanceKm && actualMin >= targetWorkout.durationMin

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

    fun upsertWorkouts(weekNumber: Int, newWorkouts: List<TrainingWorkout>) {
        val current = plan ?: return
        val commonGroupId = newWorkouts.firstOrNull()?.groupId ?: UUID.randomUUID().toString()
        
        persist(current.copy(weeks = current.weeks.map { w ->
            if (w.weekNumber != weekNumber) w
            else {
                val updatedWorkouts = w.workouts.toMutableList()
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

    fun reorderDayWorkouts(weekNumber: Int, dayOfWeek: Int, orderedIds: List<String>) {
        val current = plan ?: return
        persist(current.copy(weeks = current.weeks.map { w ->
            if (w.weekNumber != weekNumber) w
            else w.copy(workouts = w.workouts.map { wo ->
                if (wo.dayOfWeek != dayOfWeek) wo else wo.copy(order = orderedIds.indexOf(wo.id).let { if (it >= 0) it else wo.order })
            }.sortedWith(compareBy({ it.dayOfWeek }, { it.order })))
        }))
    }

    fun deleteWorkout(weekNumber: Int, workoutId: String) {
        val current = plan ?: return
        persist(current.copy(weeks = current.weeks.map { w ->
            if (w.weekNumber != weekNumber) w
            else w.copy(workouts = w.workouts.filterNot { it.id == workoutId })
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
        db.collection("trainingPlans").document(userName).set(newPlan.toMap())
    }

    fun updatePlanDetails(name: String, description: String) {
        val current = plan ?: return
        persist(current.copy(name = name.ifBlank { "My Training Plan" }, description = description))
    }

    fun deletePlan() {
        db.collection("trainingPlans").document(userName).delete()
        plan = null
    }

    // ── Dialogs ──────────────────────────────────────────────────────────
    if (showCreatePlanDialog) {
        PlanFormDialog(
            existing = null,
            onDismiss = { showCreatePlanDialog = false },
            onSave = { name, description ->
                createPlan(name, description)
                showCreatePlanDialog = false
            },
            onDelete = null
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
    if (showWorkoutDialog) {
        WorkoutFormDialog(
            existing = editingWorkout,
            initialDayOfWeek = selectedDayIndex,
            onDismiss = { showWorkoutDialog = false; editingWorkout = null },
            onSave = { workouts ->
                upsertWorkouts(weekNumberForDialog, workouts)
                showWorkoutDialog = false; editingWorkout = null
            },
            onDelete = editingWorkout?.let { w ->
                { deleteWorkout(weekNumberForDialog, w.id); showWorkoutDialog = false; editingWorkout = null }
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
                        Column {
                            Text("Training", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.White)
                            Text("Training Mode", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
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
                    ReorderableWorkoutList(
                        workouts = selectedDayWorkouts,
                        onEdit = { editingWorkout = it; showWorkoutDialog = true },
                        onToggle = { toggleWorkout(it.id) },
                        onReorder = { orderedIds -> reorderDayWorkouts(week.weekNumber, selectedDayIndex, orderedIds) }
                    )
                }
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
                    label = { Text("Plan name") },
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

@Composable
private fun WorkoutFormDialog(
    existing: TrainingWorkout?,
    initialDayOfWeek: Int = 0,
    onDismiss: () -> Unit,
    onSave: (List<TrainingWorkout>) -> Unit,
    onDelete: (() -> Unit)?
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: WORKOUT_TYPES.first()) }
    var difficulty by remember { mutableStateOf(existing?.difficulty ?: DIFFICULTIES[1]) }
    var selectedDays by remember { mutableStateOf(existing?.let { setOf(it.dayOfWeek) } ?: setOf(initialDayOfWeek)) }
    
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
    var intervalIntensity by remember { mutableStateOf(existing?.difficulty ?: INTENSITIES[1]) }

    // Initialize fields based on type if creating new
    LaunchedEffect(type) {
        if (existing == null) {
            when (type) {
                "Recovery" -> { hrZone = "Zone 1"; if(durationText.isBlank()) durationText = "30" }
                "Endurance" -> { hrZone = "Zone 2"; if(durationText.isBlank()) durationText = "60"; if(distanceText.isBlank()) distanceText = "20" }
                "Intervals" -> { if(numIntervals.isBlank()) numIntervals = "5"; if(workDuration.isBlank()) workDuration = "2"; if(recoveryDuration.isBlank()) recoveryDuration = "2" }
                "Long Ride" -> { if(durationText.isBlank()) durationText = "180"; if(distanceText.isBlank()) distanceText = "60" }
                "Race" -> { if(distanceText.isBlank()) distanceText = "40" }
            }
        }
    }

    val isValid by remember {
        derivedStateOf {
            title.isNotBlank() && selectedDays.isNotEmpty() && when (type) {
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
                    value = title, onValueChange = { title = it },
                    label = { Text("Workout Title") },
                    placeholder = { Text("e.g. Morning Recovery") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TGreen700, focusedLabelColor = TGreen700, cursorColor = TGreen700,
                        focusedTextColor = TTextPrimary, unfocusedTextColor = TTextPrimary
                    )
                )

                Column {
                    Text("Training Type", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TTextMuted, modifier = Modifier.padding(bottom = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WORKOUT_TYPES.forEach { t ->
                            FilterChip(
                                selected = type == t,
                                onClick = { type = t },
                                label = { Text(t) },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TGreen100, selectedLabelColor = TGreen900,
                                    containerColor = Color.Transparent, labelColor = TTextMuted
                                )
                            )
                        }
                    }
                }

                Column {
                    Text("Day(s)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TTextMuted, modifier = Modifier.padding(bottom = 8.dp))
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
                                Column {
                                    Text("Intensity", fontSize = 13.sp, color = TTextMuted, modifier = Modifier.padding(bottom = 6.dp))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        INTENSITIES.forEach { i ->
                                            FilterChip(
                                                selected = intervalIntensity == i,
                                                onClick = { intervalIntensity = i; difficulty = i },
                                                label = { Text(i) },
                                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TGreen100, selectedLabelColor = TGreen900)
                                            )
                                        }
                                    }
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

                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626)),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete workout")
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
                        onClick = {
                            val gid = existing?.groupId ?: UUID.randomUUID().toString()
                            val workouts = selectedDays.map { day ->
                                TrainingWorkout(
                                    id = if (day == existing?.dayOfWeek) existing.id else UUID.randomUUID().toString(),
                                    groupId = gid,
                                    title = title.trim(),
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
                                    completed = existing?.completed ?: false,
                                    failed = existing?.failed ?: false,
                                    actualDurationMin = existing?.actualDurationMin,
                                    actualDistanceKm = existing?.actualDistanceKm,
                                    actualAvgSpeedKmh = existing?.actualAvgSpeedKmh,
                                    inProgress = existing?.inProgress ?: false
                                )
                            }
                            onSave(workouts)
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = isValid,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TGreen900, contentColor = Color.White)
                    ) { Text("Save Workout", fontWeight = FontWeight.SemiBold) }
                }
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