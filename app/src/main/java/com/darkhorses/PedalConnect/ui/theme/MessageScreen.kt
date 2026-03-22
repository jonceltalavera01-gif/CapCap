package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

// ── Colour tokens ─────────────────────────────────────────────────────────────
private val Green900  = Color(0xFF06402B)
private val Green700  = Color(0xFF0A5C3D)
private val Green100  = Color(0xFFE8F5E9)
private val SurfaceBg = Color(0xFFF4F6F5)
private val OnSurface = Color(0xFF1A1A1A)

// ── Data models ───────────────────────────────────────────────────────────────
data class Contact(
    val name: String,
    val lastMessage: String,
    val isOnline: Boolean,
    val timestamp: String   = "",
    val unreadCount: Int    = 0,
    val isTyping: Boolean   = false
)

// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(
    navController: NavController,
    paddingValues: PaddingValues
) {
    var searchQuery by remember { mutableStateOf("") }
    var showSearch  by remember { mutableStateOf(false) }

    val contacts = remember {
        listOf(
            Contact("Juan Dela Cruz",    "Huy, nandito na ako sa starting point!",  true,  "2m ago",  unreadCount = 3),
            Contact("Maria Clara",       "You: Otw na ako, 10 mins pa",             true,  "15m ago", unreadCount = 0),
            Contact("Joncel Talavera",   "You: Otw",                                true,  "1h ago",  unreadCount = 0),
            Contact("Kyle Sebastian",    "Nice ride kanina! 🚴",                     true,  "3h ago",  unreadCount = 1, isTyping = true),
            Contact("John Doe",          "Let's ride this Sunday!",                  false, "Yesterday",unreadCount = 0),
            Contact("Chain Gang PH",     "Carlo: See you all at 5AM!",              true,  "Yesterday",unreadCount = 7),
            Contact("Bagyo Riders",      "You: Confirmed, I'll be there",            false, "Mon",     unreadCount = 0),
        )
    }

    val onlineContacts = contacts.filter { it.isOnline }

    val filtered = if (searchQuery.isBlank()) contacts
    else contacts.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
                it.lastMessage.contains(searchQuery, ignoreCase = true)
    }

    val totalUnread = contacts.sumOf { it.unreadCount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearch) {
                        OutlinedTextField(
                            value         = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder   = { Text("Search messages…", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f)) },
                            singleLine    = true,
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor      = Color.White.copy(alpha = 0.5f),
                                unfocusedBorderColor    = Color.White.copy(alpha = 0.3f),
                                focusedContainerColor   = Color.White.copy(alpha = 0.12f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                                cursorColor             = Color.White,
                                focusedTextColor        = Color.White,
                                unfocusedTextColor      = Color.White
                            ),
                            shape    = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Message,
                                contentDescription = null,
                                tint     = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Messages",
                                fontWeight    = FontWeight.ExtraBold,
                                color         = Color.White,
                                fontSize      = 20.sp,
                                letterSpacing = 0.3.sp
                            )
                            if (totalUnread > 0) {
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier         = Modifier
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF4444))
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (totalUnread > 99) "99+" else "$totalUnread",
                                        color      = Color.White,
                                        fontSize   = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showSearch) { showSearch = false; searchQuery = "" }
                        else navController.popBackStack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showSearch = !showSearch
                        if (!showSearch) searchQuery = ""
                    }) {
                        Icon(
                            if (showSearch) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White
                        )
                    }
                    if (!showSearch) {
                        IconButton(onClick = { /* New group */ }) {
                            Icon(Icons.Default.GroupAdd, contentDescription = "New group", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Green900)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { /* New message */ },
                containerColor = Green900,
                contentColor   = Color.White,
                shape          = CircleShape,
                modifier       = Modifier
                    .padding(bottom = paddingValues.calculateBottomPadding())
                    .size(56.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = "New message", modifier = Modifier.size(24.dp))
            }
        },
        containerColor = SurfaceBg
    ) { innerPadding ->
        LazyColumn(
            modifier        = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding  = PaddingValues(bottom = paddingValues.calculateBottomPadding() + 80.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            // ── Online riders row ─────────────────────────────────────────────
            if (!showSearch || searchQuery.isBlank()) {
                item {
                    OnlineRidersRow(onlineContacts = onlineContacts)
                }
            }

            // ── Search result label ───────────────────────────────────────────
            if (showSearch && searchQuery.isNotBlank()) {
                item {
                    Text(
                        "${filtered.size} result${if (filtered.size != 1) "s" else ""} for \"$searchQuery\"",
                        fontSize = 13.sp,
                        color    = Color.Gray,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            } else {
                item {
                    Text(
                        "Recent",
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.Gray,
                        modifier   = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }

            // ── Empty search state ────────────────────────────────────────────
            if (filtered.isEmpty()) {
                item {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                tint     = Color(0xFFCCCCCC),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("No conversations found", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                }
            }

            // ── Contact list ──────────────────────────────────────────────────
            items(filtered) { contact ->
                ContactItem(
                    contact = contact,
                    onClick = { /* Navigate to chat */ }
                )
                HorizontalDivider(
                    modifier  = Modifier.padding(start = 82.dp, end = 16.dp),
                    thickness = 0.5.dp,
                    color     = Color(0xFFF0F0F0)
                )
            }
        }
    }
}

// ── Online riders horizontal scroll ──────────────────────────────────────────
@Composable
fun OnlineRidersRow(onlineContacts: List<Contact>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${onlineContacts.size} Online Now",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color      = OnSurface
                )
            }
            Text("See all", fontSize = 12.sp, color = Green700, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(10.dp))

        LazyRow(
            contentPadding        = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(onlineContacts) { contact ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { /* Open chat */ }
                ) {
                    Box {
                        Box(
                            modifier         = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(Green900, Green700))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                contact.name.take(1).uppercase(),
                                fontSize   = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color      = Color.White
                            )
                        }
                        // Online dot
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .align(Alignment.BottomEnd),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        contact.name.split(" ").first(),
                        fontSize   = 11.sp,
                        color      = OnSurface,
                        fontWeight = FontWeight.Medium,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
    HorizontalDivider(color = Color(0xFFF0F0F0), thickness = 0.5.dp)
}

// ── Contact row ───────────────────────────────────────────────────────────────
@Composable
fun ContactItem(contact: Contact, onClick: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .background(if (contact.unreadCount > 0) Color(0xFFF9FFF9) else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar + online dot
        Box(modifier = Modifier.size(54.dp)) {
            Box(
                modifier         = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        if (contact.unreadCount > 0)
                            Brush.linearGradient(listOf(Green900, Green700))
                        else
                            Brush.linearGradient(listOf(Color(0xFF9E9E9E), Color(0xFF757575)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    contact.name.take(1).uppercase(),
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = Color.White
                )
            }
            if (contact.isOnline) {
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .align(Alignment.BottomEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // Name + last message
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = contact.name,
                fontWeight = if (contact.unreadCount > 0) FontWeight.ExtraBold else FontWeight.SemiBold,
                fontSize   = 15.sp,
                color      = OnSurface,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            if (contact.isTyping) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "typing…",
                        fontSize   = 13.sp,
                        color      = Green700,
                        fontWeight = FontWeight.Medium,
                        fontStyle  = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            } else {
                Text(
                    text     = contact.lastMessage,
                    color    = if (contact.unreadCount > 0) OnSurface else Color.Gray,
                    fontSize = 13.sp,
                    fontWeight = if (contact.unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Timestamp + unread badge
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text     = contact.timestamp,
                fontSize = 11.sp,
                color    = if (contact.unreadCount > 0) Green900 else Color.Gray,
                fontWeight = if (contact.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
            )
            Spacer(Modifier.height(4.dp))
            if (contact.unreadCount > 0) {
                Box(
                    modifier         = Modifier
                        .clip(CircleShape)
                        .background(Green900)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (contact.unreadCount > 99) "99+" else "${contact.unreadCount}",
                        color      = Color.White,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // Spacer to keep alignment consistent
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

// ── Status badge (kept for backward compat) ───────────────────────────────────
@Composable
fun StatusBadge(isOnline: Boolean) {
    val bg         = if (isOnline) Color.DarkGray else Color.Gray
    val statusText = if (isOnline) "Active" else "Offline"
    val dotColor   = if (isOnline) Color(0xFF4CAF50) else Color.Red
    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
        Text(statusText, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}