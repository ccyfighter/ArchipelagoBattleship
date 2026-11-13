package com.archipelago.battleship.core

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** 画一段虚线（弹道 / 鱼雷航迹 / 炸弹下落线都用它）。 */
private fun DrawScope.dashed(
    from: Offset,
    to: Offset,
    color: Color,
    width: Float,
    dash: Float = 9f,
    gap: Float = 7f
) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = kotlin.math.sqrt(dx * dx + dy * dy)
    if (len <= 0f) return
    val ux = dx / len
    val uy = dy / len
    var d = 0f
    while (d < len) {
        val e = minOf(d + dash, len)
        drawLine(
            color,
            Offset(from.x + ux * d, from.y + uy * d),
            Offset(from.x + ux * e, from.y + uy * e),
            strokeWidth = width
        )
        d += dash + gap
    }
}

private fun shipHull(cx: Float, cy: Float, halfW: Float, halfH: Float): Path =
    Path().apply {
        moveTo(cx - halfW, cy - halfH)
        lineTo(cx + halfW * 0.68f, cy - halfH)
        lineTo(cx + halfW, cy)
        lineTo(cx + halfW * 0.68f, cy + halfH)
        lineTo(cx - halfW, cy + halfH)
        close()
    }

/**
 * 首页封面：一张「海图作战态势图」上的三条攻击线。
 *
 * 纯 Canvas 矢量绘制，不引用任何图片 / 字体 / 影视素材，全部为原创几何图形：
 *  ① 水面战列舰主炮开火 → 抛物线弹道 → 右上命中爆花
 *  ② 右上舰载机水平飞行 → 投下两枚炸弹（虚线下落线）
 *  ③ 水下潜艇发射鱼雷 → 虚线航迹 + 尾流气泡，射向右侧目标
 * 另有坐标刻度、罗盘玫瑰、群岛剪影、水面线与扫描射线作为情报室装饰。
 */
@Composable
fun BattleScene(modifier: Modifier = Modifier) {
    val sweep by produceState(0f) {
        while (isActive) {
            value = (value + 0.8f) % 360f
            delay(40)
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val error = MaterialTheme.colorScheme.error
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val tertiary = MaterialTheme.colorScheme.tertiary

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val seaY = h * 0.56f
            val unit = minOf(w / 12f, h / 7f)

            // ---------- 1. 海图底：水下更深，水面带暖色高光 ----------
            drawRect(
                primary.copy(alpha = 0.055f),
                topLeft = Offset.Zero,
                size = Size(w, h)
            )
            drawRect(
                primary.copy(alpha = 0.10f),
                topLeft = Offset(0f, seaY),
                size = Size(w, h - seaY)
            )
            // 网格
            val grid = primary.copy(alpha = 0.13f)
            val strong = primary.copy(alpha = 0.24f)
            var gx = 0f
            var col = 0
            while (gx < w) {
                drawLine(
                    if (col % 5 == 0) strong else grid,
                    Offset(gx, 0f), Offset(gx, h), strokeWidth = if (col % 5 == 0) 1.6f else 1f
                )
                gx += unit
                col++
            }
            var gy = 0f
            var row = 0
            while (gy < h) {
                drawLine(
                    if (row % 5 == 0) strong else grid,
                    Offset(0f, gy), Offset(w, gy), strokeWidth = if (row % 5 == 0) 1.6f else 1f
                )
                gy += unit
                row++
            }
            // 上下刻度尺
            for (i in 0..12) {
                val x = i * unit
                if (x > w) break
                drawLine(primary.copy(alpha = 0.42f), Offset(x, 0f), Offset(x, 5f), strokeWidth = 1.5f)
                drawLine(primary.copy(alpha = 0.42f), Offset(x, h - 5f), Offset(x, h), strokeWidth = 1.5f)
            }

            // ---------- 2. 罗盘玫瑰（左上） ----------
            val cc = Offset(w * 0.11f, h * 0.22f)
            val cr = unit * 0.86f
            drawCircle(primary.copy(alpha = 0.16f), radius = cr, center = cc, style = Stroke(1.5f))
            drawCircle(primary.copy(alpha = 0.10f), radius = cr * 0.62f, center = cc, style = Stroke(1f))
            for (k in 0 until 8) {
                val a = Math.toRadians((k * 45.0) - 90.0)
                val ux = kotlin.math.cos(a).toFloat()
                val uy = kotlin.math.sin(a).toFloat()
                val len = if (k % 2 == 0) cr else cr * 0.62f
                drawLine(
                    primary.copy(alpha = if (k % 2 == 0) 0.72f else 0.34f),
                    cc, Offset(cc.x + ux * len, cc.y + uy * len), strokeWidth = if (k % 2 == 0) 2f else 1.2f
                )
            }
            drawCircle(secondary, radius = 2.6f, center = cc)

            // ---------- 3. 群岛剪影 ----------
            val island = Path().apply {
                moveTo(w * 0.80f, seaY + unit * 0.10f)
                lineTo(w * 0.855f, seaY - unit * 0.58f)
                lineTo(w * 0.90f, seaY - unit * 0.16f)
                lineTo(w * 0.94f, seaY + unit * 0.10f)
                close()
            }
            drawPath(island, color = tertiary.copy(alpha = 0.55f))
            val island2 = Path().apply {
                moveTo(w * 0.06f, seaY + unit * 0.04f)
                lineTo(w * 0.115f, seaY - unit * 0.34f)
                lineTo(w * 0.17f, seaY + unit * 0.04f)
                close()
            }
            drawPath(island2, color = tertiary.copy(alpha = 0.34f))

            // ---------- 4. 雷达扫描射线（缓慢旋转） ----------
            val radarC = Offset(w * 0.52f, h * 0.50f)
            val rad = Math.toRadians(sweep.toDouble())
            drawLine(
                primary.copy(alpha = 0.10f),
                radarC,
                Offset(
                    radarC.x + w * 0.62f * kotlin.math.cos(rad).toFloat(),
                    radarC.y + w * 0.62f * kotlin.math.sin(rad).toFloat()
                ),
                strokeWidth = 10f
            )

            // ---------- 5. 水面线 ----------
            drawLine(primary.copy(alpha = 0.55f), Offset(0f, seaY), Offset(w, seaY), strokeWidth = 2f)
            for (i in 0..16) {
                val x = i * (w / 16f)
                drawLine(
                    primary.copy(alpha = 0.20f),
                    Offset(x, seaY + 3f), Offset(x + w / 32f, seaY + 3f), strokeWidth = 1.2f
                )
            }

            // ---------- 6. 水面战列舰 + 主炮开火 ----------
            val bx = w * 0.40f
            val by = seaY - unit * 0.20f
            val bw = unit * 2.05f
            val bh = unit * 0.30f
            // 舰体阴影 / 舰体 / 水线
            drawPath(
                shipHull(bx, by + 2f, bw, bh),
                color = primary.copy(alpha = 0.22f)
            )
            drawPath(shipHull(bx, by, bw, bh), color = primary)
            drawLine(
                surface.copy(alpha = 0.85f),
                Offset(bx - bw * 0.86f, by + bh * 0.55f),
                Offset(bx + bw * 0.62f, by + bh * 0.55f),
                strokeWidth = 1.6f
            )
            // 舰桥 + 桅杆 + 烟囱
            drawRect(
                primary,
                topLeft = Offset(bx - bw * 0.18f, by - bh * 1.85f),
                size = Size(bw * 0.42f, bh * 1.72f)
            )
            drawRect(
                surface.copy(alpha = 0.75f),
                topLeft = Offset(bx - bw * 0.10f, by - bh * 1.55f),
                size = Size(bw * 0.26f, bh * 0.32f)
            )
            drawLine(
                primary, Offset(bx + bw * 0.02f, by - bh * 1.85f),
                Offset(bx + bw * 0.02f, by - bh * 3.10f), strokeWidth = 2f
            )
            drawRect(
                primary.copy(alpha = 0.75f),
                topLeft = Offset(bx - bw * 0.62f, by - bh * 1.15f),
                size = Size(bw * 0.20f, bh * 1.05f)
            )
            // 两座炮塔
            for (px in listOf(bx - bw * 0.62f, bx + bw * 0.46f)) {
                drawRect(
                    primary,
                    topLeft = Offset(px - bh * 0.36f, by - bh * 0.95f),
                    size = Size(bh * 0.72f, bh * 0.62f)
                )
            }
            // 炮口火焰
            val muzzle = Offset(bx + bw * 0.92f, by - bh * 1.05f)
            val flame = Path().apply {
                moveTo(muzzle.x, muzzle.y - 3f)
                lineTo(muzzle.x + unit * 0.44f, muzzle.y - unit * 0.12f)
                lineTo(muzzle.x + unit * 0.20f, muzzle.y)
                lineTo(muzzle.x + unit * 0.40f, muzzle.y + unit * 0.14f)
                lineTo(muzzle.x, muzzle.y + 3f)
                close()
            }
            drawPath(flame, color = secondary)
            drawCircle(Color(0xFFFFD27A), radius = unit * 0.09f, center = muzzle)

            // 弹道虚线 → 右上命中爆花
            val impact = Offset(w * 0.665f, h * 0.245f)
            dashed(muzzle, impact, secondary.copy(alpha = 0.85f), 2.2f)
            drawCircle(error.copy(alpha = 0.16f), radius = unit * 0.52f, center = impact)
            drawCircle(secondary.copy(alpha = 0.75f), radius = unit * 0.24f, center = impact)
            for (k in 0 until 8) {
                val a = Math.toRadians(k * 45.0)
                val ux = kotlin.math.cos(a).toFloat()
                val uy = kotlin.math.sin(a).toFloat()
                drawLine(
                    secondary.copy(alpha = 0.85f),
                    Offset(impact.x + ux * unit * 0.30f, impact.y + uy * unit * 0.30f),
                    Offset(impact.x + ux * unit * 0.50f, impact.y + uy * unit * 0.50f),
                    strokeWidth = 2f
                )
            }
            val q = unit * 0.14f
            drawLine(error, Offset(impact.x - q, impact.y - q), Offset(impact.x + q, impact.y + q), strokeWidth = 2.6f)
            drawLine(error, Offset(impact.x + q, impact.y - q), Offset(impact.x - q, impact.y + q), strokeWidth = 2.6f)

            // ---------- 7. 右上舰载机 + 投弹 ----------
            val ax = w * 0.80f
            val ay = h * 0.15f
            val plane = Path().apply {
                moveTo(ax - unit * 0.86f, ay)
                lineTo(ax - unit * 0.18f, ay - unit * 0.10f)
                lineTo(ax + unit * 0.88f, ay - unit * 0.04f)
                lineTo(ax + unit * 0.98f, ay)
                lineTo(ax + unit * 0.88f, ay + unit * 0.04f)
                lineTo(ax - unit * 0.18f, ay + unit * 0.10f)
                lineTo(ax - unit * 0.86f, ay)
                close()
            }
            drawPath(plane, color = onSurface.copy(alpha = 0.88f))
            // 主翼 / 尾翼
            drawLine(
                onSurface.copy(alpha = 0.88f),
                Offset(ax - unit * 0.06f, ay - unit * 0.40f),
                Offset(ax + unit * 0.30f, ay + unit * 0.40f),
                strokeWidth = unit * 0.13f
            )
            drawLine(
                onSurface.copy(alpha = 0.88f),
                Offset(ax - unit * 0.64f, ay - unit * 0.24f),
                Offset(ax - unit * 0.46f, ay + unit * 0.24f),
                strokeWidth = unit * 0.09f
            )
            drawCircle(secondary, radius = unit * 0.06f, center = Offset(ax + unit * 0.92f, ay))
            // 两枚下落的炸弹
            for ((ox, drop) in listOf(-unit * 0.16f to unit * 0.92f, unit * 0.22f to unit * 1.22f)) {
                val from = Offset(ax + ox, ay + unit * 0.14f)
                val to = Offset(from.x + unit * 0.14f, from.y + drop)
                dashed(from, to, secondary.copy(alpha = 0.60f), 1.6f, dash = 6f, gap = 6f)
                drawRect(
                    secondary,
                    topLeft = Offset(to.x - unit * 0.07f, to.y - unit * 0.16f),
                    size = Size(unit * 0.14f, unit * 0.28f)
                )
                drawLine(
                    secondary.copy(alpha = 0.8f),
                    Offset(to.x, to.y - unit * 0.16f),
                    Offset(to.x, to.y - unit * 0.30f),
                    strokeWidth = 1.5f
                )
            }

            // ---------- 8. 水下潜艇 + 鱼雷航迹 ----------
            val sx = w * 0.24f
            val sy = h * 0.78f
            val sw = unit * 1.55f
            val sh = unit * 0.24f
            val sub = Path().apply {
                moveTo(sx - sw, sy)
                lineTo(sx - sw * 0.78f, sy - sh)
                lineTo(sx + sw * 0.74f, sy - sh)
                lineTo(sx + sw, sy)
                lineTo(sx + sw * 0.74f, sy + sh)
                lineTo(sx - sw * 0.78f, sy + sh)
                close()
            }
            drawPath(sub, color = tertiary.copy(alpha = 0.92f))
            drawRect(
                tertiary,
                topLeft = Offset(sx - sw * 0.18f, sy - sh * 2.05f),
                size = Size(sw * 0.36f, sh * 1.85f)
            )
            drawLine(
                tertiary, Offset(sx + sw * 0.06f, sy - sh * 2.05f),
                Offset(sx + sw * 0.06f, sy - sh * 2.85f), strokeWidth = 2f
            )
            drawCircle(onSurface.copy(alpha = 0.75f), radius = 2.4f, center = Offset(sx + sw * 0.06f, sy - sh * 2.85f))
            // 鱼雷 + 虚线航迹 + 尾流气泡
            val torpStart = Offset(sx + sw * 1.02f, sy - sh * 0.10f)
            val torpEnd = Offset(w * 0.64f, sy - sh * 0.10f)
            dashed(torpStart, torpEnd, secondary.copy(alpha = 0.72f), 2f)
            drawRect(
                secondary,
                topLeft = Offset(torpEnd.x - unit * 0.16f, torpEnd.y - unit * 0.055f),
                size = Size(unit * 0.32f, unit * 0.11f)
            )
            for (k in 1..5) {
                val bx2 = sx - sw - k * unit * 0.20f
                drawCircle(
                    primary.copy(alpha = 0.30f),
                    radius = unit * (0.035f + k * 0.012f),
                    center = Offset(bx2, sy + sh * 0.35f + (k % 2) * unit * 0.08f),
                    style = Stroke(1.2f)
                )
            }
            // 鱼雷目标处的水花圈
            drawCircle(
                primary.copy(alpha = 0.55f), radius = unit * 0.22f,
                center = Offset(w * 0.68f, sy - sh * 0.10f), style = Stroke(2f)
            )

            // ---------- 9. 图框四角定位角 ----------
            val corner = unit * 0.30f
            val ink = primary.copy(alpha = 0.70f)
            for (cx in listOf(4f, w - 4f)) {
                for (cy in listOf(4f, h - 4f)) {
                    val hx = if (cx < w / 2f) corner else -corner
                    val vy = if (cy < h / 2f) corner else -corner
                    drawLine(ink, Offset(cx, cy), Offset(cx + hx, cy), strokeWidth = 2f)
                    drawLine(ink, Offset(cx, cy), Offset(cx, cy + vy), strokeWidth = 2f)
                }
            }
        }

        Text(
            "ARCHIPELAGO  ·  OPERATION CHART 001",
            fontSize = 9.sp,
            color = primary.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 10.dp)
        )
        Text(
            "SURFACE  /  AIR  /  SUBSURFACE",
            fontSize = 9.sp,
            color = primary.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 8.dp)
        )
    }
}
