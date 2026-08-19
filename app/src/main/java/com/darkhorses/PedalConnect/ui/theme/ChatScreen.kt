package com.darkhorses.PedalConnect.ui.theme

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.draw.shadow
import com.darkhorses.PedalConnect.utils.CloudinaryHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

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

private val QUICK_REACTIONS = listOf("❤️", "👍", "😂", "😮", "😢", "🙏")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val activeParticipants by viewModel.activeParticipants.collectAsStateWithLifecycle()
    val currentUserId = viewModel.currentUserId
    val isOtherOnline = onlineUsers.find { it.userId == otherUserId }?.isOnline ?: false
    val isOtherTyping = onlineUsers.find { it.userId == otherUserId }?.typingIn == conversationId

    var messageText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<Message?>(null) }
    var reactionTrayMessageId by remember { mutableStateOf<String?>(null) }
    var showSeenByMessageId by remember { mutableStateOf<String?>(null) }
    var showGroupInfo by remember { mutableStateOf(false) }
    var showAddParticipants by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activeConversation by viewModel.activeConversation.collectAsStateWithLifecycle()
    val myFriendIds by viewModel.myFriendIds.collectAsStateWithLifecycle()

    val isParticipant = activeConversation?.participantIds?.contains(currentUserId) ?: true
    val isStillFriend = if (activeConversation?.isGroup == false) {
        val otherId = activeConversation?.participantIds?.find { it != currentUserId }
        otherId == null || myFriendIds.contains(otherId)
    } else true

    val canMessage = isParticipant && isStillFriend

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)!!.readBytes()
                    val result = CloudinaryHelper.uploadImage(bytes)
                    viewModel.sendMessage(conversationId, otherUserId, "", imageUrl = result.url)
                } catch (e: Exception) {
                    Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── Load messages for this conversation + mark as read ──────────────────
    DisposableEffect(conversationId) {
        viewModel.openConversation(conversationId)
        viewModel.markAsRead(conversationId)
        viewModel.markMessagesAsRead(conversationId)
        onDispose {
            viewModel.closeConversation()
        }
    }

    // ── Auto-scroll to bottom when new messages arrive, and mark any newly
    // arrived messages from the other user as read since we're actively viewing ──
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(messages.size - 1) }
            viewModel.markMessagesAsRead(conversationId)
        }
    }

    var sendError by remember { mutableStateOf(false) }

    fun sendCurrentMessage() {
        val text = messageText.trim()
        if (text.isEmpty() && replyingTo == null) return
        sendError = false
        val currentReplyingTo = replyingTo
        messageText = ""
        replyingTo = null
        viewModel.setTyping(conversationId, false)

        val replySenderName = if (currentReplyingTo != null) {
            val sender = activeParticipants[currentReplyingTo.senderId]
            sender?.displayName?.ifBlank { sender.username } ?: (activeConversation?.participantNames?.get(currentReplyingTo.senderId) ?: "Unknown")
        } else null

        viewModel.sendMessage(
            conversationId = conversationId,
            otherUserId = otherUserId,
            text = text,
            replyToId = currentReplyingTo?.id,
            replyToText = currentReplyingTo?.text,
            replyToSenderName = replySenderName
        ) { success ->
            if (!success) {
                sendError = true
                messageText = text
                replyingTo = currentReplyingTo
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .clickable {
                                if (otherUserId == "group" || (activeConversation?.isGroup == true)) {
                                    showGroupInfo = true
                                } else {
                                    navController.navigate("public_profile/$otherUserName")
                                }
                            }
                    ) {
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(CGreen900, CGreen700))),
                                contentAlignment = Alignment.Center
                            ) {
                                if (otherUserId == "group" || (activeConversation?.isGroup == true)) {
                                    if (activeConversation?.groupPhotoUrl != null) {
                                        AsyncImage(
                                            model = activeConversation?.groupPhotoUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.People,
                                            null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else {
                                    val otherUser = activeParticipants[otherUserId]
                                    if (otherUser?.photoUrl != null) {
                                        AsyncImage(
                                            model = otherUser.photoUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            otherUserName.take(1).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                            if (isOtherOnline && otherUserId != "group") {
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
                            if (otherUserId == "group" || (activeConversation?.isGroup == true)) {
                                val memberNames = activeConversation?.participantNames?.values?.joinToString(", ")
                                Text(
                                    text = memberNames ?: "Group Chat",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 200.dp)
                                )
                            } else {
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
                if (!canMessage) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .padding(bottom = paddingValues.calculateBottomPadding()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (activeConversation?.isGroup == true)
                                "You can no longer send messages to this group because you have left."
                            else
                                "You can no longer send messages to this person.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                } else {
                    Column {
                        if (sendError) {
                            Text(
                                "Message failed to send. Check your connection and try again.",
                                color = Color(0xFFDC2626),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }

                        // ── Reply Preview ──────────────────────────────────────────
                        replyingTo?.let { replyMsg ->
                            val sender = activeParticipants[replyMsg.senderId]
                            val senderName = if (replyMsg.senderId == currentUserId) "You" else (sender?.displayName?.ifBlank { sender.username } ?: "Unknown")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF5F5F5))
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Reply, null, tint = CGreen700, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Replying to $senderName", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = CGreen700)
                                    Text(
                                        if (replyMsg.imageUrl != null) "📷 Photo" else replyMsg.text,
                                        maxLines = 1,
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { replyingTo = null }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .padding(bottom = paddingValues.calculateBottomPadding()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { photoPicker.launch("image/*") }) {
                                Icon(Icons.Default.Image, contentDescription = "Send Image", tint = CGreen900)
                            }
                            Spacer(Modifier.width(4.dp))
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
                                    unfocusedContainerColor = Color.White,
                                    focusedTextColor = COnSurface,
                                    unfocusedTextColor = COnSurface,
                                    cursorColor = CGreen900
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = { sendCurrentMessage() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(if (messageText.isNotBlank() || replyingTo != null) CGreen900 else Color(0xFFCDD8CD))
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (showGroupInfo && activeConversation != null) {
            GroupInfoDialog(
                viewModel = viewModel,
                navController = navController,
                conversation = activeConversation!!,
                onDismiss = { showGroupInfo = false },
                onAddParticipants = {
                    showGroupInfo = false
                    showAddParticipants = true
                }
            )
        }

        if (showAddParticipants && activeConversation != null) {
            AddParticipantsDialog(
                viewModel = viewModel,
                conversation = activeConversation!!,
                onDismiss = { showAddParticipants = false }
            )
        }

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
                    Text(
                        if (otherUserId == "group") "Say hi to the group! 👋" else "Say hi to $otherUserName! 👋",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
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

                    if (message.isSystemMessage) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = message.text,
                                fontSize = 12.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        var offsetX by remember { mutableStateOf(0f) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .offset { IntOffset(offsetX.roundToInt(), 0) }
                                .pointerInput(message.id, message.isUnsent) {
                                    if (!message.isUnsent) {
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                if (offsetX > 100f) replyingTo = message
                                                offsetX = 0f
                                            },
                                            onHorizontalDrag = { _, dragAmount ->
                                                offsetX = (offsetX + dragAmount).coerceIn(0f, 150f)
                                            }
                                        )
                                    }
                                },
                            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                            verticalAlignment = Alignment.Top
                        ) {
                            if (!isMine) {
                                val sender = activeParticipants[message.senderId]
                                val senderName = sender?.displayName?.ifBlank { sender.username } ?: (activeConversation?.participantNames?.get(message.senderId) ?: "Unknown")
                                Box(
                                    modifier = Modifier
                                        .padding(top = 16.dp, end = 8.dp)
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE8F5E9))
                                        .clickable {
                                            sender?.username?.let { navController.navigate("public_profile/$it") }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (sender?.photoUrl != null) {
                                        coil.compose.AsyncImage(
                                            model = sender.photoUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            senderName.take(1).uppercase(),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CGreen900
                                        )
                                    }
                                }
                            }

                            Column(
                                horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
                                modifier = Modifier.widthIn(max = 240.dp)
                            ) {
                                if (!isMine) {
                                    val sender = activeParticipants[message.senderId]
                                    val senderName = sender?.displayName?.ifBlank { sender.username } ?: (activeConversation?.participantNames?.get(message.senderId) ?: "Unknown")
                                    Text(
                                        text = senderName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }

                                // ── Quick-reaction tray — floats above the bubble, doesn't shift layout ──
                                if (reactionTrayMessageId == message.id && !message.isUnsent) {
                                    val density = LocalDensity.current
                                    val trayOffsetY = with(density) { (-52).dp.roundToPx() }
                                    Popup(
                                        alignment = if (isMine) Alignment.TopEnd else Alignment.TopStart,
                                        offset = IntOffset(0, trayOffsetY),
                                        onDismissRequest = { reactionTrayMessageId = null }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .shadow(elevation = 6.dp, shape = RoundedCornerShape(24.dp))
                                                .clip(RoundedCornerShape(24.dp))
                                                .background(Color.White)
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            QUICK_REACTIONS.forEach { emoji ->
                                                Text(
                                                    emoji,
                                                    fontSize = 22.sp,
                                                    modifier = Modifier
                                                        .clickable(
                                                            interactionSource = remember { MutableInteractionSource() },
                                                            indication = null
                                                        ) {
                                                            viewModel.toggleReaction(conversationId, message, emoji)
                                                            reactionTrayMessageId = null
                                                        }
                                                )
                                            }
                                            if (isMine && !message.isUnsent) {
                                                VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
                                                IconButton(
                                                    onClick = {
                                                        viewModel.unsendMessage(conversationId, message.id) { success ->
                                                            if (success) {
                                                                Toast.makeText(context, "Message unsent", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                Toast.makeText(context, "Failed to unsend", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                        reactionTrayMessageId = null
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, "Unsend", tint = Color.Red, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                }

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
                                        .combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { 
                                                if (reactionTrayMessageId == message.id) reactionTrayMessageId = null
                                                else if (isMine) showSeenByMessageId = if (showSeenByMessageId == message.id) null else message.id
                                            },
                                            onDoubleClick = { if (!message.isUnsent) viewModel.toggleReaction(conversationId, message, "❤️") },
                                            onLongClick = { if (!message.isUnsent) reactionTrayMessageId = message.id }
                                        )
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Column {
                                        if (message.replyToId != null) {
                                            Column(
                                                modifier = Modifier
                                                    .padding(bottom = 6.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (isMine) Color.Black.copy(alpha = 0.2f) else Color(0xFFECECEC))
                                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.AutoMirrored.Filled.Reply, null,
                                                        modifier = Modifier.size(12.dp),
                                                        tint = if (isMine) Color.White.copy(alpha = 0.7f) else CGreen700)
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        text = message.replyToSenderName ?: "Unknown",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = if (isMine) Color.White else CGreen900
                                                    )
                                                }
                                                Text(
                                                    text = message.replyToText ?: "",
                                                    fontSize = 12.sp,
                                                    color = if (isMine) Color.White.copy(alpha = 0.8f) else Color.DarkGray,
                                                    maxLines = 2,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        if (message.isUnsent) {
                                            Text(
                                                "This message was unsent",
                                                color = if (isMine) Color.White.copy(alpha = 0.6f) else Color.Gray,
                                                fontSize = 13.sp,
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                            )
                                        } else {
                                            if (message.imageUrl != null) {
                                                AsyncImage(
                                                    model = message.imageUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(max = 200.dp)
                                                        .clip(RoundedCornerShape(8.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                                if (message.text.isNotEmpty()) Spacer(Modifier.height(4.dp))
                                            }
                                            if (message.text.isNotEmpty()) {
                                                Text(
                                                    message.text,
                                                    color = if (isMine) Color.White else COnSurface,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                // ── Reaction pills — grouped by emoji, with count ──
                                if (message.reactions.isNotEmpty() && !message.isUnsent) {
                                    val grouped = message.reactions.values.groupingBy { it }.eachCount()
                                    val iReacted = message.reactions[currentUserId]
                                    Spacer(Modifier.height(3.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        grouped.forEach { (emoji, count) ->
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (emoji == iReacted) CGreen900.copy(alpha = 0.12f) else Color(0xFFF0F0F0))
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null
                                                    ) { viewModel.toggleReaction(conversationId, message, emoji) }
                                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(emoji, fontSize = 11.sp)
                                                if (count > 1) {
                                                    Spacer(Modifier.width(2.dp))
                                                    Text("$count", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        formatBubbleTime(message.timestamp),
                                        fontSize = 10.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    if (isMine && !message.isUnsent) {
                                        val seenCount = message.seenBy.size
                                        if (seenCount > 0) {
                                            val seenByNames = message.seenBy.mapNotNull { uid ->
                                                activeParticipants[uid]?.displayName?.ifBlank { activeParticipants[uid]?.username } ?: activeConversation?.participantNames?.get(uid)
                                            }
                                            
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = if (activeConversation?.isGroup == true) "Seen by $seenCount" else "Seen",
                                                    fontSize = 10.sp,
                                                    color = CGreen700,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier
                                                        .clickable { 
                                                            showSeenByMessageId = if (showSeenByMessageId == message.id) null else message.id 
                                                        }
                                                        .padding(horizontal = 4.dp)
                                                )
                                                
                                                if (showSeenByMessageId == message.id && seenByNames.isNotEmpty()) {
                                                    Text(
                                                        text = seenByNames.joinToString(", "),
                                                        fontSize = 9.sp,
                                                        color = Color.Gray,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                                        modifier = Modifier.padding(horizontal = 4.dp).widthIn(max = 200.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Removed my own avatar section
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupInfoDialog(
    viewModel: ChatViewModel,
    navController: NavController,
    conversation: Conversation,
    onDismiss: () -> Unit,
    onAddParticipants: () -> Unit
) {
    val currentUserId = viewModel.currentUserId
    val isAdmin = conversation.adminIds.contains(currentUserId)
    val activeParticipants by viewModel.activeParticipants.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isEditingName by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(conversation.groupName ?: "") }
    var isUploadingPhoto by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isUploadingPhoto = true
            scope.launch {
                try {
                    val url = withContext(Dispatchers.IO) {
                        val bytes = context.contentResolver.openInputStream(uri)!!.readBytes()
                        val result = CloudinaryHelper.uploadImage(bytes)
                        result.url
                    }
                    viewModel.updateGroupPhoto(conversation.id, url)
                    isUploadingPhoto = false
                    Toast.makeText(context, "Group photo updated!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    isUploadingPhoto = false
                    Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Group Info",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = CGreen900
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = Color.Gray)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Group Photo and Name Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(CGreen900.copy(alpha = 0.1f))
                            .clickable(enabled = isAdmin) { photoPicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isUploadingPhoto) {
                            CircularProgressIndicator(color = CGreen900, modifier = Modifier.size(24.dp))
                        } else if (conversation.groupPhotoUrl != null) {
                            AsyncImage(
                                model = conversation.groupPhotoUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.People, null, modifier = Modifier.size(40.dp), tint = CGreen900)
                        }

                        if (isAdmin) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(CGreen900)
                                    .align(Alignment.BottomEnd),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(14.dp), tint = Color.White)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (isEditingName) {
                        OutlinedTextField(
                            value = editedName,
                            onValueChange = { editedName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (editedName.isNotBlank()) {
                                        viewModel.updateGroupName(conversation.id, editedName)
                                        isEditingName = false
                                    }
                                }) {
                                    Icon(Icons.Default.Check, null, tint = CGreen900)
                                }
                            }
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                conversation.groupName ?: "Group Chat",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = COnSurface
                            )
                            if (isAdmin) {
                                IconButton(onClick = { isEditingName = true }) {
                                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                if (isAdmin) {
                    Button(
                        onClick = onAddParticipants,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CGreen900),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Participants")
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Text(
                    "Participants (${conversation.participantIds.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )

                Spacer(Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(conversation.participantIds) { userId ->
                        val sender = activeParticipants[userId]
                        val name = sender?.displayName?.ifBlank { sender.username } ?: (conversation.participantNames[userId] ?: "Unknown")
                        val isUserAdmin = conversation.adminIds.contains(userId)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val username = sender?.username ?: conversation.participantNames[userId]
                                    if (username != null) {
                                        onDismiss()
                                        if (userId == currentUserId) {
                                            navController.navigate("profile/$username")
                                        } else {
                                            navController.navigate("public_profile/$username")
                                        }
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE8F5E9)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (sender?.photoUrl != null) {
                                    coil.compose.AsyncImage(
                                        model = sender.photoUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        name.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = CGreen900
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (userId == currentUserId) "You" else name,
                                    fontWeight = FontWeight.SemiBold,
                                    color = COnSurface
                                )
                                if (isUserAdmin) {
                                    Text("Admin", fontSize = 11.sp, color = CGreen700)
                                }
                            }

                            if (isAdmin && userId != currentUserId) {
                                var showMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { showMenu = true }) {
                                        Icon(Icons.Default.MoreVert, null, tint = Color.Gray)
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        if (!isUserAdmin) {
                                            DropdownMenuItem(
                                                text = { Text("Make Admin") },
                                                onClick = {
                                                    viewModel.promoteToAdmin(conversation.id, userId)
                                                    showMenu = false
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Remove", color = Color.Red) },
                                            onClick = {
                                                viewModel.removeParticipant(conversation.id, userId)
                                                showMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        viewModel.leaveGroup(conversation.id) { success ->
                            if (success) {
                                onDismiss()
                                navController.popBackStack()
                                Toast.makeText(context, "You left the group", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to leave group", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFAECEA), contentColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Leave Group")
                }

                Spacer(Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close", color = CGreen900)
                }
            }
        }
    }
}

@Composable
fun AddParticipantsDialog(
    viewModel: ChatViewModel,
    conversation: Conversation,
    onDismiss: () -> Unit
) {
    val myFriends by viewModel.myFriends.collectAsStateWithLifecycle()
    val availableFriends = myFriends.filter { !conversation.participantIds.contains(it.uid) }
    val selectedParticipants = remember { mutableStateListOf<User>() }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Add Participants",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = CGreen900
                )

                Spacer(Modifier.height(16.dp))

                if (availableFriends.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), Alignment.Center) {
                        Text("All your friends are already in this group.", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(availableFriends) { friend ->
                            val isSelected = selectedParticipants.contains(friend)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color(0xFFE8F5E9) else Color(0xFFF8FBF9))
                                    .clickable {
                                        if (isSelected) selectedParticipants.remove(friend)
                                        else selectedParticipants.add(friend)
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE8F5E9)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        friend.username.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = CGreen900
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    friend.displayName.ifBlank { friend.username },
                                    fontWeight = FontWeight.SemiBold,
                                    color = COnSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = CGreen900)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.addParticipants(conversation.id, selectedParticipants.toList())
                            onDismiss()
                        },
                        enabled = selectedParticipants.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = CGreen900),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}
