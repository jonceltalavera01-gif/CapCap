package com.darkhorses.PedalConnect.ui.theme

import java.util.UUID

internal data class TrainingWorkout(
    val id: String,
    val groupId: String = UUID.randomUUID().toString(),
    val title: String,
    val type: String,        // "Endurance", "Intervals", "Recovery", "Long Ride", "Race"
    val dayOfWeek: Int,
    val order: Int = 0,
    val durationMin: Int,
    val distanceKm: Double,
    val difficulty: String = "Medium",
    var completed: Boolean = false,
    val failed: Boolean = false,
    // Sticky flag: once true, stays true even after a later successful retry.
    // Lets the UI show "you failed this before" without needing full attempt history.
    val previouslyFailed: Boolean = false,
    val actualDurationMin: Int? = null,
    val actualDistanceKm: Double? = null,
    val actualAvgSpeedKmh: Double? = null,
    val inProgress: Boolean = false,
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
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val totalWeeks: Int,
    val weeks: List<TrainingWeek>,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val archivedAt: Long? = null
)

internal fun TrainingWorkout.toMap(): Map<String, Any> = mapOf(
    "id" to id, "groupId" to groupId, "title" to title, "type" to type, "dayOfWeek" to dayOfWeek, "order" to order,
    "durationMin" to durationMin, "distanceKm" to distanceKm,
    "difficulty" to difficulty,
    "completed" to completed, "failed" to failed, "previouslyFailed" to previouslyFailed,
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

internal fun TrainingWeek.toMap(): Map<String, Any> = mapOf(
    "weekNumber" to weekNumber,
    "workouts" to workouts.map { it.toMap() }
)

internal fun TrainingPlan.toMap(): Map<String, Any> = mapOf(
    "id" to id, "name" to name, "description" to description, "totalWeeks" to totalWeeks,
    "weeks" to weeks.map { it.toMap() },
    "isActive" to isActive,
    "createdAt" to createdAt,
    "archivedAt" to (archivedAt ?: -1L)
)

@Suppress("UNCHECKED_CAST")
internal fun mapToWorkout(m: Map<String, Any?>): TrainingWorkout = TrainingWorkout(
    id = m["id"] as? String ?: "",
    groupId = m["groupId"] as? String ?: (m["id"] as? String ?: UUID.randomUUID().toString()),
    title = m["title"] as? String ?: "",
    type = m["type"] as? String ?: "Endurance",
    dayOfWeek = (m["dayOfWeek"] as? Number)?.toInt() ?: 0,
    order = (m["order"] as? Number)?.toInt() ?: 0,
    durationMin = (m["durationMin"] as? Number)?.toInt() ?: 0,
    distanceKm = (m["distanceKm"] as? Number)?.toDouble() ?: 0.0,
    difficulty = m["difficulty"] as? String ?: "Medium",
    completed = m["completed"] as? Boolean ?: false,
    failed = m["failed"] as? Boolean ?: false,
    previouslyFailed = m["previouslyFailed"] as? Boolean ?: false,
    actualDurationMin = (m["actualDurationMin"] as? Number)?.toInt()?.takeIf { it >= 0 },
    actualDistanceKm = (m["actualDistanceKm"] as? Number)?.toDouble()?.takeIf { it >= 0 },
    actualAvgSpeedKmh = (m["actualAvgSpeedKmh"] as? Number)?.toDouble()?.takeIf { it >= 0 },
    inProgress = m["inProgress"] as? Boolean ?: false,
    hrZone = (m["hrZone"] as? String)?.takeIf { it.isNotBlank() },
    targetCadence = (m["targetCadence"] as? Number)?.toInt()?.takeIf { it >= 0 },
    numIntervals = (m["numIntervals"] as? Number)?.toInt()?.takeIf { it >= 0 },
    workDurationMin = (m["workDurationMin"] as? Number)?.toInt()?.takeIf { it >= 0 },
    recoveryDurationMin = (m["recoveryDurationMin"] as? Number)?.toInt()?.takeIf { it >= 0 },
    elevationM = (m["elevationM"] as? Number)?.toDouble()?.takeIf { it >= 0 },
    targetFinishTime = (m["targetFinishTime"] as? String)?.takeIf { it.isNotBlank() },
    raceType = (m["raceType"] as? String)?.takeIf { it.isNotBlank() }
)

@Suppress("UNCHECKED_CAST")
internal fun mapToWeek(m: Map<String, Any?>): TrainingWeek = TrainingWeek(
    weekNumber = (m["weekNumber"] as? Number)?.toInt() ?: 0,
    workouts = (m["workouts"] as? List<Map<String, Any?>>)?.map { mapToWorkout(it) } ?: emptyList()
)

@Suppress("UNCHECKED_CAST")
internal fun documentToPlan(data: Map<String, Any?>): TrainingPlan = TrainingPlan(
    id = data["id"] as? String ?: UUID.randomUUID().toString(),
    name = data["name"] as? String ?: "Training Plan",
    description = data["description"] as? String ?: "",
    totalWeeks = (data["totalWeeks"] as? Number)?.toInt() ?: 0,
    weeks = (data["weeks"] as? List<Map<String, Any?>>)?.map { mapToWeek(it) } ?: emptyList(),
    isActive = data["isActive"] as? Boolean ?: true,
    createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
    archivedAt = (data["archivedAt"] as? Number)?.toLong()?.takeIf { it >= 0 }
)

// Single source of truth for "did the user hit this workout's goal."
// Starting guess, not a measured target — a ride below this fraction of the
// planned distance/duration is treated as a genuine miss, not silently marked done.
// Called from both the manual checkbox toggle (TrainingScreen) and the
// automatic post-ride evaluation (RideSummarySheet) so the two paths can't
// disagree on the same ride's outcome.
internal const val WORKOUT_COMPLETION_THRESHOLD = 0.9

internal fun evaluateWorkoutGoal(
    workout: TrainingWorkout,
    actualDistanceKm: Double,
    actualDurationMin: Int
): Boolean = when (workout.type) {
    "Recovery" -> workout.durationMin > 0 &&
            actualDurationMin >= workout.durationMin * WORKOUT_COMPLETION_THRESHOLD
    "Endurance" -> workout.distanceKm > 0 &&
            actualDistanceKm >= workout.distanceKm * WORKOUT_COMPLETION_THRESHOLD &&
            actualDurationMin >= workout.durationMin * WORKOUT_COMPLETION_THRESHOLD
    "Intervals" -> {
        val cycleMin = (workout.workDurationMin ?: 0) + (workout.recoveryDurationMin ?: 0)
        val sets = workout.numIntervals ?: 0
        cycleMin > 0 && sets > 0 &&
                (actualDurationMin.toDouble() / cycleMin) >= sets.toDouble() * WORKOUT_COMPLETION_THRESHOLD
    }
    "Long Ride" -> workout.distanceKm > 0 &&
            actualDistanceKm >= workout.distanceKm * WORKOUT_COMPLETION_THRESHOLD &&
            actualDurationMin >= workout.durationMin * WORKOUT_COMPLETION_THRESHOLD
    "Race" -> workout.distanceKm > 0 &&
            actualDistanceKm >= workout.distanceKm * WORKOUT_COMPLETION_THRESHOLD
    else -> workout.distanceKm > 0 &&
            actualDistanceKm >= workout.distanceKm * WORKOUT_COMPLETION_THRESHOLD
}

// Companion to evaluateWorkoutGoal — bundles the resulting completed/failed
// flags together with previouslyFailed, so any call site that scores an
// attempt (manual toggle, automatic ride completion) derives all three the
// same way. previouslyFailed only ever turns true, and stays true through a
// later successful retry, so a failed attempt is never silently forgotten.
internal fun applyWorkoutOutcome(
    workout: TrainingWorkout,
    actualDistanceKm: Double,
    actualDurationMin: Int
): TrainingWorkout {
    // Defensive: a corrupted GPS reading or bad caller input shouldn't be
    // able to produce a negative distance/duration feeding into the goal
    // check below.
    val safeDistanceKm = actualDistanceKm.coerceAtLeast(0.0)
    val safeDurationMin = actualDurationMin.coerceAtLeast(0)
    val goalMet = evaluateWorkoutGoal(workout, safeDistanceKm, safeDurationMin)
    return workout.copy(
        completed = goalMet,
        failed = !goalMet,
        previouslyFailed = workout.previouslyFailed || !goalMet
    )
}
