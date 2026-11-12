package com.archipelago.battleship.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archipelago.battleship.domain.model.LogEntry
import com.archipelago.battleship.domain.model.LogKind

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
fun InfoLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.76f),
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
fun ChipButton(
    text: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val padding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.padding(3.dp),
            contentPadding = padding
        ) {
            Text(text, fontSize = 12.sp)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.padding(3.dp),
            contentPadding = padding
        ) {
            Text(text, fontSize = 12.sp)
        }
    }
}

@Composable
fun LogList(entries: List<LogEntry>, modifier: Modifier = Modifier) {
    val newestFirst = entries.takeLast(70).reversed()
    LazyColumn(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        items(newestFirst) { e ->
            val color = when (e.kind) {
                LogKind.HIT, LogKind.AA -> MaterialTheme.colorScheme.error
                LogKind.SYSTEM -> MaterialTheme.colorScheme.primary
                LogKind.SKILL -> Color(0xFFB8860B)
                LogKind.CHAT -> Color(0xFF2E8B57)
                LogKind.MISS -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                LogKind.INFO -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
            }
            Text(
                text = "[第${e.round}回合] ${e.text}",
                color = color,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 1.dp)
            )
        }
    }
}
