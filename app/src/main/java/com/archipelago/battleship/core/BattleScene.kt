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

/** 把一点绕 pivot 旋转 deg 度（屏幕坐标，顺时针为正）。 */
private fun turn(p: Offset, pivot: Offset, deg: Float): Offset {
    val a = Math.toRadians(deg.toDouble())
    val c = kotlin.math.cos(a).toFloat()
    val s = kotlin.math.sin(a).toFloat()
    val dx = p.x - pivot.x
    val dy = p.y - pivot.y
    return Offset(pivot.x + dx * c - dy * s, pivot.y + dx * s + dy * c)
}

/** 旋转后的实心多边形：所有载具都用它画出「机头朝前」的 3/4 视角。 */
private fun DrawScope.poly(points: List<Offset>, color: Color, pivot: Offset, deg: Float) {
    if (points.isEmpty()) return
    val path = Path().apply {
        val first = turn(points.first(), pivot, deg)
        moveTo(first.x, first.y)
        for (i in 1 until points.size) {
            val q = turn(points[i], pivot, deg)
            lineTo(q.x, q.y)
        }
        close()
    }
    drawPath(path, color = color)
}

private fun DrawScope.turnLine(
    from: Offset,
    to: Offset,
    color: Color,
    width: Float,
    pivot: Offset,
    deg: Float
) {
    drawLine(color, turn(from, pivot, deg), turn(to, pivot, deg), strokeWidth = width)
}

private fun DrawScope.burst(c: Offset, r: Float, color: Color) {
    drawCircle(color.copy(alpha = 0.18f), radius = r, center = c)
    for (k in 0 until 8) {
        val a = Math.toRadians(k * 45.0)
        val ux = kotlin.math.cos(a).toFloat()
        val uy = kotlin.math.sin(a).toFloat()
        drawLine(
            color.copy(alpha = 0.85f),
            Offset(c.x + ux * r * 0.55f, c.y + uy * r * 0.55f),
            Offset(c.x + ux * r, c.y + uy * r),
            strokeWidth = 2f
        )
    }
}

/**
 * 首页封面 · 三栖合击徽章（按用户手绘草图重绘的原创矢量图）。
 *
 * 构图：圆形作战徽章内
 *   ① 正中战列舰：舰首略上扬，舰首主炮向侧前方开火；
 *   ② 左上舰载机：斜向俯冲入场、机头朝画面中心，投下两枚炸弹；
 *   ③ 右下潜艇：艇首朝画面中心偏前，向左上方发射鱼雷。
 * 三者头部统一朝内/朝前，形成三栖合围。
 *
 * radarMode = true  → 雷达图底纹：同心环 + 十字基准 + 慢速扫描射线；
 * radarMode = false → 棋盘底纹：坐标格 + 命中/未命中/舰体标记。
 * 全部为 Canvas 几何绘制，不引用任何图片、字体或第三方素材。
 */
@Composable
fun BattleScene(modifier: Modifier = Modifier, radarMode: Boolean = true) {
    val sweep by produceState(0f) {
        while (isActive) {
            value = (value + 0.8f) % 360f
            delay(40)
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val error = MaterialTheme.colorScheme.error

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val unit = minOf(w / 12f, h / 7f)
            val cx = w * 0.50f
            val cy = h * 0.48f
            val c = Offset(cx, cy)
            val r = minOf(w * 0.29f, h * 0.43f)

            // ---------- 1. 底图：雷达图 / 棋盘 ----------
            drawRect(primary.copy(alpha = 0.045f), topLeft = Offset.Zero, size = Size(w, h))
            if (radarMode) {
                for (k in 1..5) {
                    drawCircle(
                        primary.copy(alpha = 0.10f),
                        radius = unit * k * 0.78f,
                        center = c,
                        style = Stroke(1.2f)
                    )
                }
                drawLine(primary.copy(alpha = 0.12f), Offset(0f, cy), Offset(w, cy), strokeWidth = 1f)
                drawLine(primary.copy(alpha = 0.12f), Offset(cx, 0f), Offset(cx, h), strokeWidth = 1f)
                val rad = Math.toRadians(sweep.toDouble())
                val ux = kotlin.math.cos(rad).toFloat()
                val uy = kotlin.math.sin(rad).toFloat()
                drawLine(
                    primary.copy(alpha = 0.10f), c,
                    Offset(cx + ux * w * 0.62f, cy + uy * w * 0.62f), strokeWidth = 9f
                )
                drawLine(
                    primary.copy(alpha = 0.42f), c,
                    Offset(cx + ux * r * 1.02f, cy + uy * r * 1.02f), strokeWidth = 2f
                )
                for (k in 0 until 12) {
                    val a = Math.toRadians((k * 30.0) - 90.0)
                    val ex = kotlin.math.cos(a).toFloat()
                    val ey = kotlin.math.sin(a).toFloat()
                    drawLine(
                        primary.copy(alpha = 0.35f),
                        Offset(cx + ex * unit * 3.75f, cy + ey * unit * 3.75f),
                        Offset(cx + ex * unit * 3.95f, cy + ey * unit * 3.95f),
                        strokeWidth = 1.5f
                    )
                }
            } else {
                val step = unit * 0.86f
                var gx = 0f
                while (gx < w) {
                    drawLine(primary.copy(alpha = 0.13f), Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
                    gx += step
                }
                var gy = 0f
                while (gy < h) {
                    drawLine(primary.copy(alpha = 0.13f), Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                    gy += step
                }
                // 舰体格 / 未命中点 / 命中叉，全部落在四角，不抢中间徽章
                val cells = listOf(
                    Offset(step * 0.55f, step * 0.55f),
                    Offset(step * 1.55f, step * 0.55f),
                    Offset(step * 2.55f, step * 0.55f),
                    Offset(w - step * 1.45f, h - step * 1.45f),
                    Offset(w - step * 2.45f, h - step * 1.45f)
                )
                cells.forEach { p ->
                    drawRect(
                        secondary.copy(alpha = 0.18f),
                        topLeft = p,
                        size = Size(step * 0.78f, step * 0.78f)
                    )
                }
                val miss = Offset(w - step * 1.1f, step * 1.1f)
                drawCircle(primary.copy(alpha = 0.55f), radius = 4f, center = miss, style = Stroke(1.6f))
                val hit = Offset(step * 1.1f, h - step * 1.1f)
                val q = step * 0.20f
                drawLine(error, Offset(hit.x - q, hit.y - q), Offset(hit.x + q, hit.y + q), strokeWidth = 2.4f)
                drawLine(error, Offset(hit.x + q, hit.y - q), Offset(hit.x - q, hit.y + q), strokeWidth = 2.4f)
            }

            // ---------- 2. 圆形作战徽章 ----------
            drawCircle(surface.copy(alpha = 0.58f), radius = r, center = c)
            drawCircle(primary.copy(alpha = 0.62f), radius = r, center = c, style = Stroke(2.2f))
            drawCircle(primary.copy(alpha = 0.20f), radius = r * 0.87f, center = c, style = Stroke(1f))
            drawLine(primary.copy(alpha = 0.24f), Offset(cx - r * 0.84f, cy), Offset(cx + r * 0.84f, cy), strokeWidth = 1f)
            drawLine(primary.copy(alpha = 0.24f), Offset(cx, cy - r * 0.84f), Offset(cx, cy + r * 0.84f), strokeWidth = 1f)

            // ---------- 3. 左上：舰载机俯冲入场 + 投弹 ----------
            val pc = Offset(cx - r * 0.60f, cy - r * 0.58f)
            val pw = r * 0.44f
            val ph = r * 0.075f
            val planeDeg = 38f
            poly(
                listOf(
                    Offset(pc.x - pw, pc.y - ph * 0.7f),
                    Offset(pc.x + pw * 0.34f, pc.y - ph),
                    Offset(pc.x + pw, pc.y),
                    Offset(pc.x + pw * 0.34f, pc.y + ph),
                    Offset(pc.x - pw, pc.y + ph * 0.7f),
                    Offset(pc.x - pw * 1.1f, pc.y)
                ),
                onSurface.copy(alpha = 0.90f), pc, planeDeg
            )
            poly(
                listOf(
                    Offset(pc.x - pw * 0.25f, pc.y - ph),
                    Offset(pc.x + pw * 0.10f, pc.y - ph * 4.3f),
                    Offset(pc.x + pw * 0.32f, pc.y - ph * 4.3f),
                    Offset(pc.x + pw * 0.12f, pc.y - ph)
                ),
                onSurface.copy(alpha = 0.78f), pc, planeDeg
            )
            poly(
                listOf(
                    Offset(pc.x - pw * 0.25f, pc.y + ph),
                    Offset(pc.x + pw * 0.10f, pc.y + ph * 4.3f),
                    Offset(pc.x + pw * 0.32f, pc.y + ph * 4.3f),
                    Offset(pc.x + pw * 0.12f, pc.y + ph)
                ),
                onSurface.copy(alpha = 0.78f), pc, planeDeg
            )
            poly(
                listOf(
                    Offset(pc.x - pw * 0.86f, pc.y - ph),
                    Offset(pc.x - pw * 0.62f, pc.y - ph * 2.5f),
                    Offset(pc.x - pw * 0.48f, pc.y - ph * 2.5f),
                    Offset(pc.x - pw * 0.52f, pc.y - ph)
                ),
                onSurface.copy(alpha = 0.72f), pc, planeDeg
            )
            // 机头正面高光（强调「头偏朝前」）
            poly(
                listOf(
                    Offset(pc.x + pw * 0.34f, pc.y - ph),
                    Offset(pc.x + pw, pc.y),
                    Offset(pc.x + pw * 0.34f, pc.y)
                ),
                secondary, pc, planeDeg
            )
            // 两枚炸弹 + 下落虚线
            val drop1 = turn(Offset(pc.x + pw * 0.18f, pc.y + ph * 1.6f), pc, planeDeg)
            val drop2 = turn(Offset(pc.x - pw * 0.30f, pc.y + ph * 1.6f), pc, planeDeg)
            for ((start, len) in listOf(drop1 to r * 0.36f, drop2 to r * 0.26f)) {
                val end = Offset(start.x + r * 0.06f, start.y + len)
                dashed(start, end, secondary.copy(alpha = 0.60f), 1.6f, dash = 6f, gap = 6f)
                drawRect(
                    secondary,
                    topLeft = Offset(end.x - r * 0.028f, end.y - r * 0.09f),
                    size = Size(r * 0.056f, r * 0.14f)
                )
            }

            // ---------- 4. 正中：战列舰 + 舰首主炮侧向开火 ----------
            val sc = Offset(cx - r * 0.02f, cy + r * 0.10f)
            val hw = r * 0.62f
            val hh = r * 0.115f
            val shipDeg = -16f
            poly(
                listOf(
                    Offset(sc.x - hw, sc.y - hh),
                    Offset(sc.x + hw * 0.62f, sc.y - hh),
                    Offset(sc.x + hw, sc.y),
                    Offset(sc.x + hw * 0.62f, sc.y + hh),
                    Offset(sc.x - hw, sc.y + hh)
                ),
                primary, sc, shipDeg
            )
            // 舰首正面高光：3/4 视角下的「朝前」切面
            poly(
                listOf(
                    Offset(sc.x + hw * 0.62f, sc.y - hh),
                    Offset(sc.x + hw, sc.y),
                    Offset(sc.x + hw * 0.62f, sc.y)
                ),
                primary.copy(alpha = 0.55f), sc, shipDeg
            )
            poly(
                listOf(
                    Offset(sc.x - hw * 0.20f, sc.y - hh * 2.7f),
                    Offset(sc.x + hw * 0.14f, sc.y - hh * 2.7f),
                    Offset(sc.x + hw * 0.14f, sc.y - hh),
                    Offset(sc.x - hw * 0.20f, sc.y - hh)
                ),
                primary, sc, shipDeg
            )
            poly(
                listOf(
                    Offset(sc.x - hw * 0.12f, sc.y - hh * 2.25f),
                    Offset(sc.x + hw * 0.06f, sc.y - hh * 2.25f),
                    Offset(sc.x + hw * 0.06f, sc.y - hh * 1.65f),
                    Offset(sc.x - hw * 0.12f, sc.y - hh * 1.65f)
                ),
                surface.copy(alpha = 0.85f), sc, shipDeg
            )
            turnLine(
                Offset(sc.x - hw * 0.03f, sc.y - hh * 2.7f),
                Offset(sc.x - hw * 0.03f, sc.y - hh * 4.1f),
                primary, 2.4f, sc, shipDeg
            )
            // 烟囱 / 后炮塔
            poly(
                listOf(
                    Offset(sc.x - hw * 0.50f, sc.y - hh * 1.9f),
                    Offset(sc.x - hw * 0.36f, sc.y - hh * 1.9f),
                    Offset(sc.x - hw * 0.36f, sc.y - hh),
                    Offset(sc.x - hw * 0.50f, sc.y - hh)
                ),
                primary.copy(alpha = 0.78f), sc, shipDeg
            )
            // 舰首炮塔 + 略偏向侧的炮管
            poly(
                listOf(
                    Offset(sc.x + hw * 0.36f, sc.y - hh * 1.85f),
                    Offset(sc.x + hw * 0.58f, sc.y - hh * 1.85f),
                    Offset(sc.x + hw * 0.58f, sc.y - hh),
                    Offset(sc.x + hw * 0.36f, sc.y - hh)
                ),
                primary, sc, shipDeg
            )
            turnLine(
                Offset(sc.x + hw * 0.52f, sc.y - hh * 1.55f),
                Offset(sc.x + hw * 1.02f, sc.y - hh * 2.55f),
                primary, 4.2f, sc, shipDeg
            )
            val muzzle = turn(Offset(sc.x + hw * 1.02f, sc.y - hh * 2.55f), sc, shipDeg)
            poly(
                listOf(
                    Offset(muzzle.x, muzzle.y - r * 0.045f),
                    Offset(muzzle.x + r * 0.16f, muzzle.y - r * 0.02f),
                    Offset(muzzle.x + r * 0.08f, muzzle.y),
                    Offset(muzzle.x + r * 0.16f, muzzle.y + r * 0.05f),
                    Offset(muzzle.x, muzzle.y + r * 0.045f)
                ),
                secondary, Offset(muzzle.x, muzzle.y), 0f
            )
            // 弹道虚线 → 右上命中爆花
            val impact = Offset(cx + r * 0.82f, cy - r * 0.62f)
            dashed(muzzle, impact, secondary.copy(alpha = 0.75f), 2f)
            burst(impact, r * 0.16f, secondary)
            val q2 = r * 0.055f
            drawLine(error, Offset(impact.x - q2, impact.y - q2), Offset(impact.x + q2, impact.y + q2), strokeWidth = 2.2f)
            drawLine(error, Offset(impact.x + q2, impact.y - q2), Offset(impact.x - q2, impact.y + q2), strokeWidth = 2.2f)

            // ---------- 5. 右下：潜艇 + 鱼雷 ----------
            val uc = Offset(cx + r * 0.58f, cy + r * 0.64f)
            val uw = r * 0.46f
            val uh = r * 0.10f
            val subDeg = 18f
            poly(
                listOf(
                    Offset(uc.x + uw, uc.y - uh),
                    Offset(uc.x - uw * 0.62f, uc.y - uh),
                    Offset(uc.x - uw, uc.y),
                    Offset(uc.x - uw * 0.62f, uc.y + uh),
                    Offset(uc.x + uw, uc.y + uh)
                ),
                tertiary.copy(alpha = 0.95f), uc, subDeg
            )
            // 艇首正面高光
            poly(
                listOf(
                    Offset(uc.x - uw * 0.62f, uc.y - uh),
                    Offset(uc.x - uw, uc.y),
                    Offset(uc.x - uw * 0.62f, uc.y)
                ),
                tertiary.copy(alpha = 0.60f), uc, subDeg
            )
            poly(
                listOf(
                    Offset(uc.x - uw * 0.10f, uc.y - uh * 2.5f),
                    Offset(uc.x + uw * 0.24f, uc.y - uh * 2.5f),
                    Offset(uc.x + uw * 0.24f, uc.y - uh),
                    Offset(uc.x - uw * 0.10f, uc.y - uh)
                ),
                tertiary, uc, subDeg
            )
            turnLine(
                Offset(uc.x + uw * 0.07f, uc.y - uh * 2.5f),
                Offset(uc.x + uw * 0.07f, uc.y - uh * 3.6f),
                tertiary, 2.2f, uc, subDeg
            )
            poly(
                listOf(
                    Offset(uc.x - uw * 0.02f, uc.y - uh * 2.15f),
                    Offset(uc.x + uw * 0.16f, uc.y - uh * 2.15f),
                    Offset(uc.x + uw * 0.16f, uc.y - uh * 1.65f),
                    Offset(uc.x - uw * 0.02f, uc.y - uh * 1.65f)
                ),
                surface.copy(alpha = 0.80f), uc, subDeg
            )
            // 鱼雷：从艇首朝画面中心射出，无沿途轨迹，仅一条虚线指向弹体
            val torpFrom = turn(Offset(uc.x - uw * 1.05f, uc.y), uc, subDeg)
            val torpEnd = Offset(cx - r * 0.20f, cy + r * 0.34f)
            dashed(torpFrom, torpEnd, secondary.copy(alpha = 0.70f), 1.8f)
            poly(
                listOf(
                    Offset(torpEnd.x - r * 0.09f, torpEnd.y - r * 0.025f),
                    Offset(torpEnd.x + r * 0.035f, torpEnd.y - r * 0.025f),
                    Offset(torpEnd.x + r * 0.075f, torpEnd.y),
                    Offset(torpEnd.x + r * 0.035f, torpEnd.y + r * 0.025f),
                    Offset(torpEnd.x - r * 0.09f, torpEnd.y + r * 0.025f)
                ),
                secondary, torpEnd, 0f
            )
            // 尾部气泡
            for (k in 1..3) {
                val b = turn(Offset(uc.x + uw + k * r * 0.07f, uc.y + uh * 0.6f), uc, subDeg)
                drawCircle(
                    primary.copy(alpha = 0.35f),
                    radius = r * (0.014f + k * 0.006f),
                    center = b,
                    style = Stroke(1.2f)
                )
            }

            // ---------- 6. 图框四角定位角 ----------
            val corner = unit * 0.30f
            val ink = primary.copy(alpha = 0.70f)
            for (xx in listOf(4f, w - 4f)) {
                for (yy in listOf(4f, h - 4f)) {
                    val hx = if (xx < w / 2f) corner else -corner
                    val vy = if (yy < h / 2f) corner else -corner
                    drawLine(ink, Offset(xx, yy), Offset(xx + hx, yy), strokeWidth = 2f)
                    drawLine(ink, Offset(xx, yy), Offset(xx, yy + vy), strokeWidth = 2f)
                }
            }
        }

        Text(
            "ARCHIPELAGO  ·  TRI-SERVICE EMBLEM 001",
            fontSize = 9.sp,
            color = primary.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 10.dp)
        )
        Text(
            if (radarMode) "RADAR CHART  /  SURFACE · AIR · SUBSURFACE"
            else "TACTICAL GRID  /  SURFACE · AIR · SUBSURFACE",
            fontSize = 9.sp,
            color = primary.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 8.dp)
        )
    }
}
