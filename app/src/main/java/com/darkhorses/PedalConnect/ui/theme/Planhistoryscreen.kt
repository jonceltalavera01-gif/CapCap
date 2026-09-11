package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

private const val PAGE_SIZE = 20L

// NOTE: This screen queries with .whereEqualTo("isActive", false).orderBy("archivedAt", DESC).
// Firestore requires a composite index for this combination. The first run will throw
// a runtime error containing a direct link to auto-create it in the Firebase console —
// click that link once; this is expected, not a bug.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanHistoryScreen(navController: NavController, userName: String) {
    val db = remember { FirebaseFirestore.getInstance() }
    val plansCollection = remember(userName) {
        db.collection("trainingPlans").document(userName).collection("plans")
    }

    var plans by remember { mutableStateOf<List<TrainingPlan>>(emptyList()) }
    var lastDoc by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var isLoadingInitial by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var planPendingDelete by remember { mutableStateOf<TrainingPlan?>(null) }

    fun loadPage() {
        if (lastDoc == null) isLoadingInitial = true else isLoadingMore = true

        var query = plansCollection
            .whereEqualTo("isActive", false)
            .orderBy("archivedAt", Query.Direction.DESCENDING)
            .limit(PAGE_SIZE)
        lastDoc?.let { query = query.startAfter(it) }

        query.get()
            .addOnSuccessListener { snap ->
                val newPlans = snap.documents.mapNotNull { it.data?.let { d -> documentToPlan(d) } }
                plans = plans + newPlans
                lastDoc = snap.documents.lastOrNull()
                hasMore = snap.documents.size.toLong() == PAGE_SIZE
                isLoadingInitial = false
                isLoadingMore = false
            }
            .addOnFailureListener {
                // Same no-rollback, fire-and-forget style as the rest of the training
                // feature — a failed page just stops "Load more" from working further.
                hasMore = false
                isLoadingInitial = false
                isLoadingMore = false
            }
    }

    LaunchedEffect(userName) { loadPage() }

    fun restorePlan(target: TrainingPlan) {
        plansCollection.whereEqualTo("isActive", true).limit(1).get()
            .addOnSuccessListener { activeSnap ->
                activeSnap.documents.firstOrNull()?.reference?.set(
                    mapOf("isActive" to false, "archivedAt" to System.currentTimeMillis()),
                    SetOptions.merge()
                )
                plansCollection.document(target.id)
                    .set(mapOf("isActive" to true, "archivedAt" to null), SetOptions.merge())
                navController.popBackStack()
            }
    }

    fun deletePlan(target: TrainingPlan) {
        plansCollection.document(target.id).delete()
        plans = plans.filterNot { it.id == target.id }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plan History", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TGreen900)
            )
        },
        containerColor = TBgCanvas
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoadingInitial -> {
                    CircularProgressIndicator(
                        color = TGreen900,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.align(Alignment.Center).size(32.dp)
                    )
                }
                plans.isEmpty() -> {
                    Text(
                        "No past plans yet",
                        modifier = Modifier.align(Alignment.Center),
                        color = TTextMuted,
                        fontSize = 14.sp
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(plans, key = { it.id }) { p ->
                            PlanHistoryCard(
                                plan = p,
                                isActive = false,
                                onRestore = { restorePlan(p) },
                                onDelete = { planPendingDelete = p }
                            )
                        }
                        if (hasMore) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isLoadingMore) {
                                        CircularProgressIndicator(
                                            color = TGreen900,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    } else {
                                        TextButton(onClick = { loadPage() }) {
                                            Text("Load more", color = TGreen700, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    planPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { planPendingDelete = null },
            title = { Text("Delete \"${target.name}\"?") },
            text = { Text("This permanently removes the plan and its workout history. This can't be undone.") },
            confirmButton = {
                Button(
                    onClick = { deletePlan(target); planPendingDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { planPendingDelete = null }) { Text("Cancel") } }
        )
    }
}