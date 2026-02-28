package com.darkhorses.RideSafe.ui.theme

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*



data class PostItem(
    val id: String = "",
    val userName: String = "",
    val description: String = "",
    val activity: String = "",
    val distance: String = "",
    val timestamp: Long = 0,
    val likes: Int = 0,
    val comments: Int = 0,
    val likedBy: List<String> = emptyList(),
    val status: String = ""
)

data class NotificationItem(
    val id: String = "",
    val message: String = "",
    val type: String = "",
    val timestamp: Long = 0,
    val read: Boolean = false
)

data class CommentItem(
    val id: String = "",
    val userName: String = "",
    val text: String = "",
    val timestamp: Long = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Homepage(
    navController: NavController,
    paddingValues: PaddingValues,
    userName: String
) {
    val db = FirebaseFirestore.getInstance()
    val posts = remember { mutableStateListOf<PostItem>() }
    val context = LocalContext.current

    var showCommentsSheet by remember { mutableStateOf(false) }
    var selectedPostId by remember { mutableStateOf("") }
    val notifications = remember { mutableStateListOf<NotificationItem>() }
    var showNotifBadge by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }
    val postComments = remember { mutableStateListOf<CommentItem>() }

    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(Unit) {

        db.collection("notifications")
            .whereEqualTo("userName", userName)
            .whereEqualTo("read", false)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null) {
                    notifications.clear()
                    for (doc in snapshot.documents) {
                        val notif = NotificationItem(
                            id = doc.id,
                            message = doc.getString("message") ?: "",
                            type = doc.getString("type") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0,
                            read = doc.getBoolean("read") ?: false
                        )
                        notifications.add(notif)
                    }
                    showNotifBadge = notifications.isNotEmpty()
                }
            }
        // Show accepted posts from everyone
        db.collection("posts")
            .whereEqualTo("status", "accepted")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null) {
                    posts.clear()
                    for (doc in snapshot.documents) {
                        val post = doc.toObject(PostItem::class.java)?.copy(id = doc.id)
                        if (post != null) posts.add(post)
                    }
                }
            }

        // Show current user's own pending posts
        db.collection("posts")
            .whereEqualTo("userName", userName)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null) {
                    for (doc in snapshot.documents) {
                        val post = doc.toObject(PostItem::class.java)?.copy(id = doc.id)
                        if (post != null && !posts.any { it.id == post.id }) posts.add(post)
                    }
                }
            }
    }

    LaunchedEffect(selectedPostId, showCommentsSheet) {
        if (showCommentsSheet && selectedPostId.isNotEmpty()) {
            db.collection("posts").document(selectedPostId)
                .collection("comments")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    if (snapshot != null) {
                        postComments.clear()
                        for (doc in snapshot.documents) {
                            val comment = doc.toObject(CommentItem::class.java)?.copy(id = doc.id)
                            if (comment != null) postComments.add(comment)
                        }
                    }
                }
        }
    }

    fun toggleLike(post: PostItem) {
        val postRef = db.collection("posts").document(post.id)
        if (post.likedBy.contains(userName)) {
            postRef.update(
                "likedBy", FieldValue.arrayRemove(userName),
                "likes", FieldValue.increment(-1)
            )
        } else {
            postRef.update(
                "likedBy", FieldValue.arrayUnion(userName),
                "likes", FieldValue.increment(1)
            )
        }
    }

    fun submitComment() {
        if (commentText.isNotBlank()) {
            val commentData = hashMapOf(
                "userName" to userName,
                "text" to commentText,
                "timestamp" to System.currentTimeMillis()
            )
            db.collection("posts").document(selectedPostId)
                .collection("comments").add(commentData)
                .addOnSuccessListener {
                    db.collection("posts").document(selectedPostId)
                        .update("comments", FieldValue.increment(1))
                    commentText = ""
                    Toast.makeText(context, "Comment added!", Toast.LENGTH_SHORT).show()
                }
        }
    }

    if (showCommentsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCommentsSheet = false },
            sheetState = sheetState,
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.8f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    "Comments",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (postComments.isEmpty()) {
                        item {
                            Text("No comments yet. Start the conversation!", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                    items(postComments) { comment ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.LightGray),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(comment.userName.take(1), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(
                                modifier = Modifier
                                    .background(Color(0xFFF1F1F1), RoundedCornerShape(12.dp))
                                    .padding(8.dp)
                            ) {
                                Text(comment.userName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(comment.text, fontSize = 14.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = { Text("Write a comment...") },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                    IconButton(onClick = { submitComment() }) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFF06402B))
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Home",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { navController.navigate("notifications/$userName") }) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = Color.White
                            )
                        }
                        if (showNotifBadge) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color.Red, CircleShape)
                                    .align(Alignment.TopEnd)
                            )
                        }
                    }
                    IconButton(onClick = { navController.navigate("message") }) {
                        Icon(
                            imageVector = Icons.Filled.Message,
                            contentDescription = "Chat",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF333333)
                ),
                // Completely remove all window insets from the top bar
                windowInsets = WindowInsets(0.dp)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("add_post/$userName") },
                containerColor = Color(0xFF06402B),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add Post",
                    tint = Color.White
                )
            }
        },
        // Remove all window insets from the scaffold
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFAAB7AE))
                // Use the innerPadding from Scaffold which accounts for the top bar
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Add a small top padding for visual comfort
            Spacer(modifier = Modifier.height(7.dp))

            AnnouncementCard(
                message = "New bike trail opened in Rizal! Check it out this weekend.",
                date = "2 hours ago"
            )

            Spacer(modifier = Modifier.height(16.dp))

            RidingEventsCard(onExploreRides = { /* Navigate to All Rides */ })

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Community Feed",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
            )

            if (posts.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No posts yet. Be the first to share!", color = Color.DarkGray)
                }
            }

            posts.forEach { post ->
                CommunityFeedCard(
                    name = post.userName,
                    activity = post.activity,
                    distance = post.distance,
                    description = post.description,
                    time = formatTimestamp(post.timestamp),
                    speed = "",
                    likes = post.likes,
                    comments = post.comments,
                    isLiked = post.likedBy.contains(userName),
                    status = post.status,
                    onLike = { toggleLike(post) },
                    onComment = {
                        selectedPostId = post.id
                        showCommentsSheet = true
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return "Just now"
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
    }
}

@Composable
fun AnnouncementCard(message: String, date: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Announcement,
                contentDescription = null,
                tint = Color(0xFFF57F17),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Community Announcement",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFFF57F17)
                )
                Text(text = message, fontSize = 13.sp)
                Text(text = date, fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun CommunityFeedCard(
    name: String,
    activity: String,
    distance: String,
    description: String,
    time: String,
    speed: String,
    likes: Int = 0,
    comments: Int = 0,
    isLiked: Boolean = false,
    status: String = "",
    onLike: () -> Unit = {},
    onComment: () -> Unit = {}
) {
    var isFollowing by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.LightGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(name.take(1), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (distance.isNotEmpty()) "$activity • $distance" else activity,
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        if (status == "pending") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFFFC107))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "⏳ Pending Approval",
                                    color = Color.Black,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                TextButton(
                    onClick = { isFollowing = !isFollowing },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = if (isFollowing) "Following" else "+ Follow",
                        color = if (isFollowing) Color.Gray else Color(0xFF06402B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Text(
                text = description,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (time.isNotEmpty() || speed.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    if (time.isNotEmpty()) {
                        Text("⏱ $time", fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.width(16.dp))
                    }
                    if (speed.isNotEmpty()) {
                        Text("⚡ $speed", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onLike() }.padding(8.dp)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) Color.Red else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text = likes.toString(), fontSize = 13.sp, color = Color.Gray)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onComment() }.padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = "Comment",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text = comments.toString(), fontSize = 13.sp, color = Color.Gray)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { /* Share Action */ }.padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RidingEventsCard(onExploreRides: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF06402B)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Weekly Group Ride",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFD700))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("HOT", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Every Sunday • 6:00 AM", color = Color.LightGray, fontSize = 13.sp)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("MOA Seaside to Cavite", color = Color.LightGray, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onExploreRides,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Join the Ride", color = Color(0xFF06402B), fontWeight = FontWeight.Bold)
            }
        }
    }
}
