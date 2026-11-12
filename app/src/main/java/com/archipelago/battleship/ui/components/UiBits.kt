package com.archipelago.battleship.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archipelago.battleship.core.AppThemeMode
import com.archipelago.battleship.core.BoardPalette
import com.archipelago.battleship.core.palette
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
    val accent = MaterialTheme.colorScheme.error
    val surface = MaterialTheme.colorScheme.surface
    val container = MaterialTheme.colorScheme.primaryContainer

    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(container, radius = r * 0.98f, center = c)
        drawCircle(primary.copy(alpha = 0.65f), radius = r * 0.94f, center = c, style = Stroke(3f))
        drawCircle(primary.copy(alpha = 0.22f), radius = r * 0.80f, center = c, style = Stroke(1.2f))
        for (k in 1..3) {
            drawCircle(primary.copy(alpha = 0.22f), radius = r * 0.24f * k, center = c, style = Stroke(1.4f))
        }
        drawLine(primary.copy(alpha = 0.28f), Offset(c.x - r * 0.9f, c.y), Offset(c.x + r * 0.9f, c.y), strokeWidth = 1f)
        drawLine(primary.copy(alpha = 0.28f), Offset(c.x, c.y - r * 0.9f), Offset(c.x, c.y + r * 0.9f), strokeWidth = 1f)

        val rad = Math.toRadians(sweep.toDouble())
        val tip = Offset(
            c.x + r * 0.88f * kotlin.math.cos(rad).toFloat(),
            c.y + r * 0.88f * kotlin.math.sin(rad).toFloat()
        )
        drawLine(primary.copy(alpha = 0.22f), c, tip, strokeWidth = 9f)
        drawLine(primary.copy(alpha = 0.85f), c, tip, strokeWidth = 2.5f)

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

        val hx = c.x + r * 0.52f
        val hy = c.y - r * 0.52f
        val q = r * 0.09f
        drawCircle(accent.copy(alpha = 0.20f), radius = q * 1.9f, center = Offset(hx, hy))
        drawLine(accent, Offset(hx - q, hy - q), Offset(hx + q, hy + q), strokeWidth = 3f)
        drawLine(accent, Offset(hx + q, hy - q), Offset(hx - q, hy + q), strokeWidth = 3f)
    }
}

/** 统一卡片：圆角 + 细描边 + 可选强调色。 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                accent ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                shape
            )
            .padding(14.dp),
        content = content
    )
}

@Composable
fun SectionTitle(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp, 20.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 9.dp)
        )
        Box(
            modifier = Modifier
                .padding(start = 10.dp)
                .height(1.dp)
                .weight(1f)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
        )
    }
}

@Composable
fun InfoLine(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.80f),
            modifier = Modifier.padding(start = 7.dp).weight(1f)
        )
    }
}

/** 首页大按钮卡片：序号徽标 + 标题 + 说明，比一排纯按钮更有层次。 */
@Composable
fun MenuCard(
    index: Int,
    title: String,
    subtitle: String,
    emphasized: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val container = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val ink = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(container)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                index.toString(),
                color = if (emphasized) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink)
            Text(
                subtitle, fontSize = 11.sp, color = ink.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(">", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ink.copy(alpha = 0.45f))
    }
}

/** 状态胶囊：左上角小圆点 + 标签 + 数值。 */
@Composable
fun StatPill(label: String, value: String, tone: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(tone.copy(alpha = 0.12f))
            .border(1.dp, tone.copy(alpha = 0.45f), shape)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(tone)
        )
        Text(
            label, fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.padding(start = 6.dp)
        )
        Text(
            value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tone,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
fun ChipButton(
    text: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val padding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
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
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.padding(3.dp),
            contentPadding = padding
        ) {
            Text(text, fontSize = 12.sp)
        }
    }
}

/* ============================ 棋盘图例 ============================ */

private enum class LegendKind { LEGAL, SPENT, RECON, MISS, HIT, SUNK, BLOCKED, LAST, TERRAIN }

private fun DrawScope.legendMark(kind: LegendKind, pal: BoardPalette) {
    val s = size.minDimension
    val c = Offset(s / 2f, s / 2f)
    val q = s * 0.28f
    when (kind) {
        LegendKind.LEGAL -> {
            drawRect(pal.legalFill, topLeft = Offset.Zero, size = Size(s, s))
            val m = s * 0.16f
            val len = s * 0.30f
            for (sx in listOf(0f, 1f)) {
                for (sy in listOf(0f, 1f)) {
                    val x = if (sx == 0f) m else s - m
                    val y = if (sy == 0f) m else s - m
                    val dx = if (sx == 0f) len else -len
                    val dy = if (sy == 0f) len else -len
                    drawLine(pal.legalInk, Offset(x, y + dy), Offset(x, y), strokeWidth = 2f)
                    drawLine(pal.legalInk, Offset(x, y), Offset(x + dx, y), strokeWidth = 2f)
                }
            }
            drawCircle(pal.legalInk, radius = s * 0.06f, center = c)
        }
        LegendKind.SPENT -> {
            drawRect(pal.spentFill, topLeft = Offset.Zero, size = Size(s, s))
            drawLine(pal.spentInk, Offset(s * 0.12f, s * 0.88f), Offset(s * 0.88f, s * 0.12f), strokeWidth = 2f)
            drawLine(pal.spentInk, Offset(s * 0.42f, s * 0.88f), Offset(s * 0.88f, s * 0.42f), strokeWidth = 1f)
        }
        LegendKind.RECON -> {
            drawRect(pal.reconFill, topLeft = Offset.Zero, size = Size(s, s))
            val m = s * 0.12f
            drawRect(
                pal.reconInk.copy(alpha = 0.65f),
                topLeft = Offset(m, m), size = Size(s - 2f * m, s - 2f * m),
                style = Stroke(1.5f)
            )
            val lens = Offset(s * 0.43f, s * 0.43f)
            drawCircle(pal.reconInk, radius = s * 0.15f, center = lens, style = Stroke(2f))
            drawLine(
                pal.reconInk,
                Offset(lens.x + s * 0.11f, lens.y + s * 0.11f),
                Offset(lens.x + s * 0.25f, lens.y + s * 0.25f),
                strokeWidth = 2f
            )
        }
        LegendKind.MISS -> {
            drawRect(pal.missFill, topLeft = Offset.Zero, size = Size(s, s))
            drawCircle(pal.miss, radius = q, center = c, style = Stroke(2f))
            drawCircle(pal.miss, radius = q * 0.30f, center = c)
        }
        LegendKind.HIT -> {
            drawRect(pal.hitFill, topLeft = Offset.Zero, size = Size(s, s))
            drawRect(pal.hit, topLeft = Offset.Zero, size = Size(s, s), style = Stroke(2f))
            drawCircle(pal.hit, radius = q * 0.92f, center = c)
            val d = q * 0.44f
            drawLine(pal.onHit, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), strokeWidth = 2f)
            drawLine(pal.onHit, Offset(c.x + d, c.y - d), Offset(c.x - d, c.y + d), strokeWidth = 2f)
        }
        LegendKind.SUNK -> {
            drawRect(pal.sunk, topLeft = Offset.Zero, size = Size(s, s))
            drawRect(pal.sunkInk, topLeft = Offset.Zero, size = Size(s, s), style = Stroke(2f))
            drawCircle(pal.onSunk, radius = q * 0.90f, center = c)
            val d = q * 0.44f
            drawLine(pal.sunkInk, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), strokeWidth = 2.4f)
            drawLine(pal.sunkInk, Offset(c.x + d, c.y - d), Offset(c.x - d, c.y + d), strokeWidth = 2.4f)
        }
        LegendKind.BLOCKED -> {
            drawRect(pal.blockedFill, topLeft = Offset.Zero, size = Size(s, s))
            val shield = Path().apply {
                moveTo(c.x, c.y - q)
                lineTo(c.x + q * 0.82f, c.y)
                lineTo(c.x, c.y + q)
                lineTo(c.x - q * 0.82f, c.y)
                close()
            }
            drawPath(shield, pal.blocked)
            drawLine(pal.onSunk, Offset(c.x - q * 0.36f, c.y + q * 0.30f), Offset(c.x + q * 0.36f, c.y - q * 0.30f), strokeWidth = 2f)
        }
        LegendKind.LAST -> {
            drawRect(pal.select.copy(alpha = 0.14f), topLeft = Offset.Zero, size = Size(s, s))
            drawRect(pal.select, topLeft = Offset(1f, 1f), size = Size(s - 2f, s - 2f), style = Stroke(2.6f))
            drawRect(pal.select.copy(alpha = 0.40f), topLeft = Offset(4f, 4f), size = Size(s - 8f, s - 8f), style = Stroke(1f))
        }
        LegendKind.TERRAIN -> {
            drawRect(pal.shallow, topLeft = Offset.Zero, size = Size(s, s))
            drawRect(pal.mountain, topLeft = Offset(s * 0.42f, 0f), size = Size(s * 0.58f, s))
            drawCircle(pal.shallowInk, radius = s * 0.07f, center = Offset(s * 0.24f, s * 0.35f))
            drawCircle(pal.shallowInk, radius = s * 0.07f, center = Offset(s * 0.24f, s * 0.68f))
            val peak = Path().apply {
                moveTo(s * 0.48f, s * 0.76f)
                lineTo(s * 0.71f, s * 0.22f)
                lineTo(s * 0.94f, s * 0.76f)
                close()
            }
            drawPath(peak, pal.mountainInk.copy(alpha = 0.9f))
        }
    }
}

/** 棋盘下方图例：用与棋盘完全相同的画法缩略绘制，所见即所得。 */
@Composable
fun BoardLegend(mode: AppThemeMode, modifier: Modifier = Modifier) {
    val pal = palette(mode)
    val shape = RoundedCornerShape(12.dp)
    val items = listOf(
        LegendKind.LEGAL to "可攻击",
        LegendKind.SPENT to "已打过",
        LegendKind.RECON to "已侦察",
        LegendKind.MISS to "未命中",
        LegendKind.HIT to "命中",
        LegendKind.SUNK to "击沉",
        LegendKind.BLOCKED to "拦截/免疫",
        LegendKind.LAST to "最近一击",
        LegendKind.TERRAIN to "山地/浅滩"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), shape)
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Text(
            "棋盘图例",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
        )
        items.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                row.forEach { (kind, label) ->
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(modifier = Modifier.size(17.dp)) { legendMark(kind, pal) }
                        Text(
                            label,
                            fontSize = 10.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 最近 3 条战果：带彩色类型徽标，命中/未命中/防空拦截一眼可辨。 */
@Composable
fun ResultBanner(entries: List<LogEntry>, modifier: Modifier = Modifier) {
    val recent = entries.takeLast(3).reversed()
    if (recent.isEmpty()) return
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), shape)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        recent.forEach { e ->
            val (tag, color) = when (e.kind) {
                LogKind.HIT -> "命中" to MaterialTheme.colorScheme.error
                LogKind.MISS -> "未命中" to Color(0xFF2F7FC1)
                LogKind.AA -> "防空拦截" to Color(0xFF8E5BC8)
                LogKind.SKILL -> "技能" to Color(0xFFC77B22)
                LogKind.SYSTEM -> "系统" to MaterialTheme.colorScheme.primary
                LogKind.CHAT -> "聊天" to Color(0xFF2E8B57)
                LogKind.INFO -> "提示" to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.80f)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
                Box(
                    modifier = Modifier
                        .padding(start = 7.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(color.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(tag, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    e.text,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    maxLines = 2,
                    modifier = Modifier.padding(start = 7.dp).weight(1f)
                )
            }
        }
    }
}

@Composable
fun LogList(entries: List<LogEntry>, modifier: Modifier = Modifier) {
    val newestFirst = entries.takeLast(70).reversed()
    val shape = RoundedCornerShape(12.dp)
    LazyColumn(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), shape)
            .padding(8.dp)
    ) {
        items(newestFirst) { e ->
            val color = when (e.kind) {
                LogKind.HIT -> MaterialTheme.colorScheme.error
                LogKind.AA -> Color(0xFF8E5BC8)
                LogKind.SYSTEM -> MaterialTheme.colorScheme.primary
                LogKind.SKILL -> Color(0xFFC77B22)
                LogKind.CHAT -> Color(0xFF2E8B57)
                LogKind.MISS -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                LogKind.INFO -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color.copy(alpha = 0.07f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp, 16.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                )
                Text(
                    text = "R${e.round}  ${e.text}",
                    color = color,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 7.dp).weight(1f)
                )
            }
        }
    }
}
