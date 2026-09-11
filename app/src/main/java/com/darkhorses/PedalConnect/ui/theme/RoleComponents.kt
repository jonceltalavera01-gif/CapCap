package com.darkhorses.PedalConnect.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun RolePill(role: String, onDarkBackground: Boolean = true) {
    if (role != "admin" && role != "helper") return
    val label = if (role == "admin") "Admin" else "Helper"
    val icon  = if (role == "admin") Icons.Default.AdminPanelSettings else Icons.Default.Star
    val bg    = if (onDarkBackground) Color.White.copy(alpha = 0.20f)
    else if (role == "admin") Color(0xFFF3E5F5) else Color(0xFFE3F2FD)
    val fg    = if (onDarkBackground) Color.White
    else if (role == "admin") Color(0xFF6A1B9A) else Color(0xFF1565C0)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(11.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}