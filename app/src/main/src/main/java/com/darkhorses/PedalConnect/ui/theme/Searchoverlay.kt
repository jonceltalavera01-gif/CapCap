package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

private val SearchGreen900 = Color(0xFF06402B)
private val SearchGreen100 = Color(0xFFE8F5E9)

// ── SearchOverlay ─────────────────────────────────────────────────────────────
// Parameters:
//   initialQuery    — pre-fills the text field (current searchQuery from HomeScreen)
//   suggestions     — live Nominatim suggestions from HomeScreen
//   recentSearches  — persisted recent searches from SharedPreferences
//   isLoadingRoute  — shows spinner in leading icon while routing
//   onSearch        — called with query when user commits a search
//   onDismiss       — called when overlay should close without searching
//   onClearRecents  — called when user taps "Clear all"
@Composable
fun SearchOverlay(
    initialQuery   : String,
    suggestions    : List<String>,
    recentSearches : List<String>,
    isLoadingRoute : Boolean,
    onQueryChange  : (String) -> Unit,   // notifies HomeScreen as user types → triggers fetchSuggestions
    onSearch       : (String) -> Unit,
    onDismiss      : () -> Unit,
    onClearRecents : () -> Unit
) {
    val focusManager       = LocalFocusManager.current
    var overlayQuery       by remember { mutableStateOf(initialQuery) }

    fun commitSearch(query: String) {
        if (query.isBlank()) return
        focusManager.clearFocus()
        onSearch(query)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                indication     = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) {
                focusManager.clearFocus()
                onDismiss()
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 16.dp)
                .clickable(
                    indication     = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { /* block tap-through dismiss */ },
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick  = { focusManager.clearFocus(); onDismiss() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, "Close search",
                        tint = Color(0xFF1A1A1A), modifier = Modifier.size(22.dp))
                }
                Text("Search Destination", fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp, color = Color(0xFF1A1A1A))
            }

            // ── Search input ──────────────────────────────────────────────────
            TextField(
                value         = overlayQuery,
                onValueChange = {
                    overlayQuery = it
                    onQueryChange(it)   // bubble up so HomeScreen can call fetchSuggestions
                },
                placeholder   = {
                    Text("Type a place, address or landmark…",
                        fontSize = 14.sp, color = Color(0xFF9E9E9E))
                },
                modifier        = Modifier.fillMaxWidth(),
                shape           = RoundedCornerShape(16.dp),
                singleLine      = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { commitSearch(overlayQuery) }),
                leadingIcon     = {
                    if (isLoadingRoute)
                        CircularProgressIndicator(modifier = Modifier.size(18.dp),
                            color = SearchGreen900, strokeWidth = 2.dp)
                    else
                        Icon(Icons.Default.Search, null,
                            tint = SearchGreen900, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (overlayQuery.isNotEmpty()) {
                        IconButton(onClick = { overlayQuery = "" }) {
                            Icon(Icons.Default.Close, "Clear",
                                tint = Color.Gray, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor   = Color(0xFFF2F5F3),
                    unfocusedContainerColor = Color(0xFFF2F5F3),
                    focusedIndicatorColor   = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor             = SearchGreen900,
                    focusedTextColor        = Color(0xFF1A1A1A),
                    unfocusedTextColor      = Color(0xFF1A1A1A)
                )
            )

            // ── Suggestions list ──────────────────────────────────────────────
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                suggestions.forEachIndexed { idx, suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { commitSearch(suggestion) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape)
                                .background(SearchGreen100),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.LocationOn, null,
                                tint = SearchGreen900, modifier = Modifier.size(18.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            val parts = suggestion.split(",")
                            Text(
                                parts.first().trim(),
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1A1A1A), maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (parts.size > 1) {
                                Text(
                                    parts.drop(1).joinToString(",").trim(),
                                    fontSize = 12.sp, color = Color(0xFF7A8F7A),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Icon(Icons.Default.NorthWest, null,
                            tint = Color(0xFFCCCCCC), modifier = Modifier.size(16.dp))
                    }
                    if (idx < suggestions.size - 1) {
                        HorizontalDivider(color = Color(0xFFF0F0F0),
                            modifier = Modifier.padding(start = 56.dp))
                    }
                }

                // ── Empty state ───────────────────────────────────────────────────
            } else if (overlayQuery.isBlank()) {
                if (recentSearches.isNotEmpty()) {
                    // Recent searches
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Recent", fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold, color = Color(0xFF9E9E9E))
                        TextButton(
                            onClick        = onClearRecents,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Clear all", fontSize = 12.sp, color = Color(0xFF9E9E9E))
                        }
                    }
                    recentSearches.forEachIndexed { idx, recent ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { commitSearch(recent) }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape)
                                    .background(Color(0xFFF2F5F3)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.History, null,
                                    tint = Color(0xFF9E9E9E), modifier = Modifier.size(18.dp))
                            }
                            Text(
                                recent, fontSize = 14.sp, color = Color(0xFF1A1A1A),
                                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            // Arrow pre-fills without submitting
                            IconButton(
                                onClick  = { overlayQuery = recent },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.NorthWest, null,
                                    tint = Color(0xFFCCCCCC), modifier = Modifier.size(14.dp))
                            }
                        }
                        if (idx < recentSearches.size - 1) {
                            HorizontalDivider(color = Color(0xFFF0F0F0),
                                modifier = Modifier.padding(start = 56.dp))
                        }
                    }
                } else {
                    // First-time empty state
                    Spacer(Modifier.height(28.dp))
                    Column(
                        modifier            = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Search, null,
                            tint = Color(0xFFDDDDDD), modifier = Modifier.size(40.dp))
                        Text(
                            "Search a place, address or landmark",
                            fontSize  = 13.sp, color = Color(0xFF9E9E9E),
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }

            // ── Search button (shown when query is non-empty) ─────────────────
            if (overlayQuery.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick  = { commitSearch(overlayQuery) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = SearchGreen900,
                        contentColor   = Color.White
                    )
                ) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Search \"$overlayQuery\"",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}