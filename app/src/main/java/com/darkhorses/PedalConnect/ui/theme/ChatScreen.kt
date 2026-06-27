package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ── Colour tokens (matches MessageScreen) ──────────────────────────────────────
private val CGreen900  = Color(0xFF06402B)
private val CGreen700  = Color(0xFF0A5C3D)
private val CSurfaceBg = Color(0xFFF4F6F5)
private val COnSurface = Color(0xFF1A1A1A)
private val CBubbleMine  = Color(0xFF06402B)
private val CBubbleTheir = Color(0xFFFFFFFF)

private fun formatBubbleTime(timestamp: Timestamp?): String {
    if (timestamp == null) return ""
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(timestamp.toDate())
}

private fun formatDateDivider(timestamp: Timestamp?): String {
    if (timestamp == null) return ""
    val msgDate = Calendar.getInstance().apply { timeInMillis = timestamp.toDate().time }
    val today   = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        msgDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                msgDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"
        msgDate.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                msgDate.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Yesterday"
        else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(timestamp.toDate())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    paddingValues: PaddingValues,
    conversationId: String,
    otherUserId: String,
    otherUserName: String,
    viewModel: ChatViewModel = viewModel()
) {
    val messages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val onlineUsers by viewModel.onlineUsers.collectAsStateWithLifecycle()
    val currentUserId = viewModel.currentUserId
    val isOtherOnline = onlineUsers.find { it.userId == otherUserId }?.isOnline ?: false
    val isOtherTyping = onlineUsers.find { it.userId == otherUserId }?.typingIn == conversationId

    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // ── Load messages for this conversation + mark as read ──────────────────
    DisposableEffect(conversationId) {
        viewModel.openConversation(conversationId)
        viewModel.markAsRead(conversationId)
        onDispose {
            viewModel.closeConversation()
        }
    }

    // ── Auto-scroll to bottom when new messages arrive ───────────────────────
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(messages.size - 1) }
        }
    }

    fun sendCurrentMessage() {
        val text = messageText.trim()
        if (text.isEmpty()) return
        viewModel.sendMessage(conversationId, otherUserId, text)
        messageText = ""
        viewModel.setTyping(conversationId, false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(CGreen900, CGreen700))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    otherUserName.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                            if (isOtherOnline) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .align(Alignment.BottomEnd),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                                }
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(otherUserName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                text = when {
                                    isOtherTyping -> "typing…"
                                    isOtherOnline -> "Online"
                                    else -> "Offline"
                                },
                                color = if (isOtherTyping) Color(0xFFB8E6CC) else Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontStyle = if (isOtherTyping) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CGreen900)
            )
        },
        containerColor = CSurfaceBg,
        bottomBar = {
            Surface(color = Color.White, shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .padding(bottom = paddingValues.calculateBottomPadding()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = {
                            messageText = it
                            viewModel.setTyping(conversationId, it.isNotEmpty())
                        },
                        placeholder = { Text("Type a message…", color = Color(0xFFB0BEC5)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendCurrentMessage() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CGreen900,
                            unfocusedBorderColor = Color(0xFFCDD8CD),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { sendCurrentMessage() },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (messageText.isNotBlank()) CGreen900 else Color(0xFFCDD8CD))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    ) { innerPadding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(CGreen900, CGreen700))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(otherUserName.take(1).uppercase(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Say hi to $otherUserName! 👋", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(messages) { index, message ->
                    val isMine = message.senderId == currentUserId
                    val showDateDivider = index == 0 ||
                            formatDateDivider(messages[index - 1].timestamp) != formatDateDivider(message.timestamp)

                    if (showDateDivider) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE5EDE8))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    formatDateDivider(message.timestamp),
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                    ) {
                        Column(
                            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 18.dp, topEnd = 18.dp,
                                            bottomStart = if (isMine) 18.dp else 4.dp,
                                            bottomEnd = if (isMine) 4.dp else 18.dp
                                        )
                                    )
                                    .background(if (isMine) CBubbleMine else CBubbleTheir)
                                    .then(
                                        if (!isMine) Modifier.background(
                                            CBubbleTheir,
                                            RoundedCornerShape(
                                                topStart = 18.dp, topEnd = 18.dp,
                                                bottomStart = 4.dp, bottomEnd = 18.dp
                                            )
                                        ) else Modifier
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    message.text,
                                    color = if (isMine) Color.White else COnSurface,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    formatBubbleTime(message.timestamp),
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                if (isMine && message.isRead) {
                                    Text("Seen", fontSize = 10.sp, color = CGreen700, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper to use itemsIndexed without extra import clutter at call site
private inline fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    list: List<Message>,
    crossinline content: @androidx.compose.runtime.Composable (Int, Message) -> Unit
) {
    items(list.size) { index ->
        content(index, list[index])
    }
}