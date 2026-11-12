package com.archipelago.battleship.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archipelago.battleship.domain.model.LogEntry
import com.archipelago.battleship.domain.model.LogKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** 应用内矢量 Logo：雷达同心圆 + 扫描线 + 群岛 + 战舰剪影 + 命中叉，纯 Canvas 绘制，不依赖任何图片素材。 */
@Composable
fun GameLogo(modifier: Modifier = Modifier) {
    val sweep by produceState(0f) {
        while (isActive) {
            value = (value + 1.2f) % 360f
            delay(32)
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary
    val surface = MaterialTheme.colorScheme.surface

    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(surface, radius = r * 0.96f, center = c)
        drawCircle(primary.copy(alpha = 0.45f), radius = r * 0.94f, center = c, style = Stroke(2f))
        for (k in 1..3) {
            drawCircle(primary.copy(alpha = 0.22f), radius = r * 0.24f * k, center = c, style = Stroke(1.4f))
        }
        drawLine(primary.copy(alpha = 0.28f), Offset(c.x - r * 0.9f, c.y), Offset(c.x + r * 0.9f, c.y), strokeWidth = 1f)
        drawLine(primary.copy(alpha = 0.28f), Offset(c.x, c.y - r * 0.9f), Offset(c.x, c.y + r * 0.9f), strokeWidth = 1f)

        val rad = Math.toRadians(sweep.toDouble())
        drawLine(
            primary.copy(alpha = 0.75f), c,
            Offset(
                c.x + r * 0.88f * kotlin.math.cos(rad).toFloat(),
                c.y + r * 0.88f * kotlin.math.sin(rad).toFloat()
            ),
            strokeWidth = 2.5f
        )

        // 下方两座小岛
        val island = Path().apply {
            moveTo(c.x - r * 0.72f, c.y + r * 0.60f)
            lineTo(c.x - r * 0.48f, c.y + r * 0.30f)
            lineTo(c.x - r * 0.24f, c.y + r * 0.60f)
            close()
        }
        drawPath(island, color = primary.copy(alpha = 0.55f))
        val island2 = Path().apply {
            moveTo(c.x + r * 0.30f, c.y + r * 0.66f)
            lineTo(c.x + r * 0.50f, c.y + r * 0.42f)
            lineTo(c.x + r * 0.70f, c.y + r * 0.66f)
            close()
        }
        drawPath(island2, color = primary.copy(alpha = 0.38f))

        // 中央战舰：矩形船体 + 三角舰首，斜杠为巡洋舰标识
        val hull = Path().apply {
            moveTo(c.x - r * 0.46f, c.y - r * 0.11f)
            lineTo(c.x + r * 0.26f, c.y - r * 0.11f)
            lineTo(c.x + r * 0.46f, c.y)
            lineTo(c.x + r * 0.26f, c.y + r * 0.11f)
            lineTo(c.x - r * 0.46f, c.y + r * 0.11f)
            close()
        }
        drawPath(hull, color = primary)
        drawLine(surface, Offset(c.x - r * 0.20f, c.y + r * 0.09f), Offset(c.x - r * 0.06f, c.y - r * 0.09f), strokeWidth = 2.5f)
        drawLine(surface, Offset(c.x - r * 0.06f, c.y + r * 0.09f), Offset(c.x + r * 0.08f, c.y - r * 0.09f), strokeWidth = 2.5f)

        // 右上角命中叉
        val hx = c.x + r * 0.52f
        val hy = c.y - r * 0.52f
        val q = r * 0.09f
        drawLine(accent, Offset(hx - q, hy - q), Offset(hx + q, hy + q), strokeWidth = 3f)
        drawLine(accent, Offset(hx + q, hy - q), Offset(hx - q, hy + q), strokeWidth = 3f)
    }
}

@Composable
fun SectionTitle(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(4.dp, 18.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun InfoLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
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
    val shape = RoundedCornerShape(18.dp)
    val padding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            modifier = Modifier.padding(3.dp),
            contentPadding = padding
        ) {
            Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            modifier = Modifier.padding(3.dp),
            contentPadding = padding
        ) {
            Text(text, fontSize = 12.sp)
        }
    }
}

/** 最近 3 条战果：带彩色类型徽标，命中/未命中/防空拦截一眼可辨。 */
@Composable
fun ResultBanner(entries: List<LogEntry>, modifier: Modifier = Modifier) {
    val recent = entries.takeLast(3).reversed()
    if (recent.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        recent.forEach { e ->
            val (tag, color) = when (e.kind) {
                LogKind.HIT -> "命中" to MaterialTheme.colorScheme.error
                LogKind.MISS -> "未命中" to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f)
                LogKind.AA -> "防空拦截" to Color(0xFFFF8F00)
                LogKind.SKILL -> "技能" to Color(0xFFB8860B)
                LogKind.SYSTEM -> "系统" to MaterialTheme.colorScheme.primary
                LogKind.CHAT -> "聊天" to Color(0xFF2E8B57)
                LogKind.INFO -> "提示" to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.80f)
            }
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(color.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(tag, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    e.text,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    maxLines = 2,
                    modifier = Modifier.padding(start = 6.dp).weight(1f)
                )
            }
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
            Row(modifier = Modifier.padding(vertical = 1.dp)) {
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp, end = 6.dp)
                        .size(5.dp)
                        .background(color, RoundedCornerShape(3.dp))
                )
                Text(
                    text = "[第${e.round}回合] ${e.text}",
                    color = color,
                    fontSize = 11.sp
                )
            }
        }
    }
}
