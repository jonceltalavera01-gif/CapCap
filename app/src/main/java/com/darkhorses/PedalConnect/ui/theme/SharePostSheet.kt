package com.darkhorses.PedalConnect.ui.theme

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharePostSheet(
    post: Post,
    currentUserName: String,
    onDismiss: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    var friends by remember { mutableStateOf<List<User>>(emptyList()) }
    var isLoadingFriends by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredFriends = if (searchQuery.isBlank()) friends 
    else friends.filter { it.username.contains(searchQuery, ignoreCase = true) || it.displayName.contains(searchQuery, ignoreCase = true) }

    LaunchedEffect(Unit) {
        // Fetch friends list
        db.collection("users").whereEqualTo("username", currentUserName).limit(1).get()
            .addOnSuccessListener { snap ->
                val currentUserId = snap.documents.firstOrNull()?.id ?: ""
                if (currentUserId.isNotEmpty()) {
                    db.collection("users").document(currentUserId).get()
                        .addOnSuccessListener { userDoc ->
                            val friendIds = (userDoc.get("friends") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                            if (friendIds.isEmpty()) {
                                isLoadingFriends = false
                            } else {
                                db.collection("users").whereIn(com.google.firebase.firestore.FieldPath.documentId(), friendIds.take(30)).get()
                                    .addOnSuccessListener { friendSnap ->
                                        friends = friendSnap.documents.mapNotNull { it.toObject(User::class.java)?.copy(uid = it.id) }
                                        isLoadingFriends = false
                                    }
                                    .addOnFailureListener { isLoadingFriends = false }
                            }
                        }
                        .addOnFailureListener { isLoadingFriends = false }
                } else {
                    isLoadingFriends = false
                }
            }
            .addOnFailureListener { isLoadingFriends = false }
    }

    fun shareToSystem() {
        val authorName = post.displayName.ifBlank { post.userName }
        val distanceText = post.distance.trim()
            .removeSuffix("km").removeSuffix("KM").trim()
            .let { if (it.isNotBlank()) "$it km" else null }
        val descriptionSnippet = post.description.trim().take(120)
            .let { if (post.description.length > 120) "$it…" else it }

        val shareBody = buildString {
            append("$authorName just posted a ${post.activity} on PedalConnect")
            if (distanceText != null) append(" — $distanceText")
            append(".\n\n")
            if (descriptionSnippet.isNotBlank()) append("\"$descriptionSnippet\"\n\n")
            append("Open in PedalConnect: pedalconnect://post/${post.id}")
        }

        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, shareBody)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Share post"))
        onDismiss()
    }

    fun shareToFriend(friend: User) {
        scope.launch {
            val repository = ChatRepository()
            db.collection("users").whereEqualTo("username", currentUserName).limit(1).get()
                .addOnSuccessListener { snap ->
                    val currentUserId = snap.documents.firstOrNull()?.id ?: ""
                    if (currentUserId.isNotEmpty()) {
                        scope.launch {
                            val conversationId = repository.findExistingConversation(currentUserId, friend.uid) 
                                ?: repository.createConversation(currentUserId, currentUserName, friend.uid, friend.username)
                            
                            val postAuthor = post.displayName.ifBlank { post.userName }
                            val shareText = "Shared a post by $postAuthor: pedalconnect://post/${post.id}"
                            
                            repository.sendMessage(
                                conversationId = conversationId,
                                senderId = currentUserId,
                                text = shareText,
                                participantIds = listOf(currentUserId, friend.uid)
                            )
                            Toast.makeText(context, "Post shared with ${friend.username}!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                Modifier.padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFE5E7EB))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Share Post", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF111827))
            
            // Search friends
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search friends…", fontSize = 14.sp) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0D7050),
                    unfocusedBorderColor = Color(0xFFE5E7EB)
                )
            )

            // Friends list
            Text("Send to Friends", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF374151))
            
            if (isLoadingFriends) {
                Box(Modifier.fillMaxWidth().height(100.dp), Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF06402B))
                }
            } else if (friends.isEmpty()) {
                Text("No friends found.", color = Color(0xFF6B7280), fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filteredFriends) { friend ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { shareToFriend(friend) }.width(64.dp)
                        ) {
                            Box(
                                Modifier.size(52.dp).clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Color(0xFF06402B), Color(0xFF0A5C3D)))),
                                Alignment.Center
                            ) {
                                if (friend.photoUrl != null) {
                                    AsyncImage(
                                        model = friend.photoUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    val initials = friend.username.take(2).uppercase()
                                    Text(initials, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                friend.displayName.ifBlank { friend.username }.split(" ").first(),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Color(0xFF111827)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFE5E7EB))

            // System share option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF3F4F6))
                    .clickable { shareToSystem() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color.White),
                    Alignment.Center
                ) {
                    Icon(Icons.Default.Share, null, tint = Color(0xFF06402B))
                }
                Column {
                    Text("Other Apps", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF111827))
                    Text("Share via link, email, or other social apps", fontSize = 12.sp, color = Color(0xFF6B7280))
                }
            }
        }
    }
}
