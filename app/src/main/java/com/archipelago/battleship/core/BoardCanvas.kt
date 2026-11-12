package com.archipelago.battleship.core

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.archipelago.battleship.domain.model.Board
import com.archipelago.battleship.domain.model.Coord
import com.archipelago.battleship.domain.model.Orientation
import com.archipelago.battleship.domain.model.ShotResult
import com.archipelago.battleship.domain.model.ShipType
import com.archipelago.battleship.domain.model.inBounds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.max

/** 双主题棋盘：A 机舱手绘坐标纸 / B 深海战术声呐雷达（带动态扫描扇形）。 */
@Composable
fun BoardCanvas(
    board: Board,
    mode: AppThemeMode,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    legalTargets: List<Coord> = emptyList(),
    highlight: Coord? = null,
    pathCells: List<Coord> = emptyList(),
    markers: List<Coord> = emptyList(),
    onTap: (Coord) -> Unit = {}
) {
    val pal = palette(mode)
    val pad = with(LocalDensity.current) { 16.dp.toPx() }
    val sweep by produceState(0f) {
        while (isActive) {
            value = (value + 1.5f) % 360f
            delay(32)
        }
    }
    val currentOnTap by rememberUpdatedState(onTap)

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(board.size, interactive) {
                if (!interactive) return@pointerInput
                detectTapGestures { off ->
                    // PointerInputScope.size is IntSize (no minDimension); DrawScope.size is Size.
                    val side: Float = minOf(size.width, size.height).toFloat()
                    val cw: Float = (side - pad) / board.size.toFloat()
                    if (cw <= 0f) return@detectTapGestures
                    val c = ((off.x - pad) / cw).toInt()
                    val r = ((off.y - pad) / cw).toInt()
                    val coord = Coord(r, c)
                    if (coord.inBounds(board.size)) currentOnTap(coord)
                }
            }
    ) {
        val n = board.size
        val cell = (size.minDimension - pad) / n
        val span = cell * n

        fun topLeft(c: Coord) = Offset(pad + c.c * cell, pad + c.r * cell)
        fun center(c: Coord) = Offset(pad + (c.c + 0.5f) * cell, pad + (c.r + 0.5f) * cell)

        drawRect(pal.bg)
        drawRect(pal.water, topLeft = Offset(pad, pad), size = Size(span, span))

        if (mode == AppThemeMode.PAPER) {
            for (i in 0 until n * 4) {
                val p = pad + i * cell / 4f
                drawLine(pal.grid.copy(alpha = 0.10f), Offset(p, pad), Offset(p, pad + span), strokeWidth = 0.6f)
                drawLine(pal.grid.copy(alpha = 0.10f), Offset(pad, p), Offset(pad + span, p), strokeWidth = 0.6f)
            }
        }

        for (c in board.shallows) {
            drawRect(pal.shallow, topLeft = topLeft(c), size = Size(cell, cell))
        }
        for (c in board.mountains) {
            val tl = topLeft(c)
            drawRect(pal.mountain, topLeft = tl, size = Size(cell, cell))
            drawLine(
                pal.bg.copy(alpha = 0.35f),
                Offset(tl.x + cell * 0.15f, tl.y + cell * 0.8f),
                Offset(tl.x + cell * 0.8f, tl.y + cell * 0.15f),
                strokeWidth = 1.5f
            )
        }

        val gridAlpha = if (mode == AppThemeMode.PAPER) 0.75f else 0.38f
        for (i in 0..n) {
            val p = pad + i * cell
            drawLine(pal.grid.copy(alpha = gridAlpha), Offset(p, pad), Offset(p, pad + span), strokeWidth = 1.2f)
            drawLine(pal.grid.copy(alpha = gridAlpha), Offset(pad, p), Offset(pad + span, p), strokeWidth = 1.2f)
        }

        if (mode == AppThemeMode.SONAR) {
            val cx = pad + span / 2f
            val cy = pad + span / 2f
            val radius = span / 2f
            for (k in 1..4) {
                drawCircle(
                    pal.grid.copy(alpha = 0.20f),
                    radius = radius * k / 4f,
                    center = Offset(cx, cy),
                    style = Stroke(1f)
                )
            }
            drawArc(
                color = pal.accent.copy(alpha = 0.11f),
                startAngle = sweep - 24f,
                sweepAngle = 38f,
                useCenter = true,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2f, radius * 2f)
            )
            val rad = Math.toRadians(sweep.toDouble())
            drawLine(
                pal.accent.copy(alpha = 0.55f),
                Offset(cx, cy),
                Offset(
                    cx + radius * kotlin.math.cos(rad).toFloat(),
                    cy + radius * kotlin.math.sin(rad).toFloat()
                ),
                strokeWidth = 2f
            )
        }

        // 可攻击候选格：淡色底 + 中心瞄准点（不再把整张非候选区域涂暗）。
        if (interactive && legalTargets.isNotEmpty()) {
            for (c in legalTargets) {
                if (!c.inBounds(n)) continue
                drawRect(pal.legal, topLeft = topLeft(c), size = Size(cell, cell))
                drawCircle(pal.accent.copy(alpha = 0.80f), radius = max(1.5f, cell * 0.07f), center = center(c))
            }
        }

        val nc = drawContext.canvas.nativeCanvas
        val colPaint = Paint().apply {
            color = pal.label.toArgb()
            textSize = max(9f, cell * 0.34f)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        for (i in 0 until n) {
            val label = if (i < 26) ('A'.code + i).toChar().toString() else (i + 1).toString()
            nc.drawText(label, pad + (i + 0.5f) * cell, pad - 4f, colPaint)
        }
        val rowPaint = Paint().apply {
            color = pal.label.toArgb()
            textSize = max(8f, cell * 0.30f)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        for (i in 0 until n) {
            nc.drawText((i + 1).toString(), pad - 4f, pad + (i + 0.62f) * cell, rowPaint)
        }

        for (ship in board.ships) {
            if (ship.cells.isEmpty()) continue
            val minR = ship.cells.minOf { it.r }
            val maxR = ship.cells.maxOf { it.r }
            val minC = ship.cells.minOf { it.c }
            val maxC = ship.cells.maxOf { it.c }
            val horizontal = ship.orientation == Orientation.H
            val col = if (ship.sunk) pal.sunk else pal.ship
            val alpha = if (ship.type == ShipType.SUBMARINE && !ship.surfaced && !ship.sunk) 0.72f else 1f
            if (horizontal) {
                val w = (maxC - minC + 1) * cell
                val h = minOf(cell * 0.78f, w * 0.45f)
                drawShipGlyph(
                    ship.type, pad + minC * cell, pad + (minR + 0.5f) * cell - h / 2f,
                    w, h, col, pal.bg, true, alpha
                )
            } else {
                val w = (maxR - minR + 1) * cell
                val h = minOf(cell * 0.78f, w * 0.45f)
                drawShipGlyph(
                    ship.type, pad + (minC + 0.5f) * cell - h / 2f, pad + minR * cell,
                    h, w, col, pal.bg, false, alpha
                )
            }
            for (hc in ship.hits) {
                drawCircle(pal.hit, radius = max(2f, cell * 0.12f), center = center(hc))
            }
        }

        // 攻击结果：每个被打过的格子都有底色 + 图形 + 文字标签。
        val lastByCell = HashMap<Coord, ShotResult>()
        for (s in board.shots) lastByCell[s.at] = s.result
        val lastShot = board.shots.lastOrNull()
        val markPaint = Paint().apply {
            textSize = max(8f, cell * 0.24f)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        for ((c, res) in lastByCell) {
            val ctr = center(c)
            val tl = topLeft(c)
            val q = cell * 0.30f
            drawRect(
                pal.label.copy(alpha = if (mode == AppThemeMode.PAPER) 0.08f else 0.13f),
                topLeft = tl,
                size = Size(cell, cell)
            )
            when (res) {
                ShotResult.MISS -> {
                    if (mode == AppThemeMode.PAPER) {
                        drawCircle(pal.miss, radius = q, center = ctr, style = Stroke(max(1.2f, cell * 0.06f)))
                        drawCircle(pal.miss.copy(alpha = 0.45f), radius = q * 0.55f, center = ctr, style = Stroke(1f))
                    } else {
                        drawCircle(pal.miss.copy(alpha = 0.85f), radius = max(2f, cell * 0.10f), center = ctr)
                        drawCircle(pal.miss.copy(alpha = 0.32f), radius = q, center = ctr, style = Stroke(1f))
                    }
                    markPaint.color = pal.miss.toArgb()
                    nc.drawText("o", ctr.x, ctr.y + cell * 0.62f, markPaint)
                }
                ShotResult.HIT, ShotResult.SUNK -> {
                    drawRect(pal.hit.copy(alpha = 0.18f), topLeft = tl, size = Size(cell, cell))
                    val sw = max(2f, cell * 0.09f)
                    drawLine(pal.hit, Offset(ctr.x - q, ctr.y - q), Offset(ctr.x + q, ctr.y + q), strokeWidth = sw)
                    drawLine(pal.hit, Offset(ctr.x + q, ctr.y - q), Offset(ctr.x - q, ctr.y + q), strokeWidth = sw)
                    if (res == ShotResult.SUNK) {
                        drawCircle(pal.hit, radius = q * 1.45f, center = ctr, style = Stroke(sw))
                        drawRect(pal.hit, topLeft = tl, size = Size(cell, cell), style = Stroke(sw))
                    }
                    markPaint.color = pal.hit.toArgb()
                    nc.drawText(if (res == ShotResult.SUNK) "S" else "X", ctr.x, ctr.y - cell * 0.40f, markPaint)
                }
                ShotResult.INTERCEPTED, ShotResult.IMMUNE, ShotResult.BLOCKED -> {
                    val c2 = if (res == ShotResult.INTERCEPTED) pal.select else pal.label
                    drawCircle(c2.copy(alpha = 0.18f), radius = q * 1.15f, center = ctr)
                    drawLine(c2, Offset(ctr.x - q, ctr.y + q), Offset(ctr.x + q, ctr.y - q), strokeWidth = max(2f, cell * 0.07f))
                    drawLine(c2, Offset(ctr.x - q * 0.5f, ctr.y), Offset(ctr.x + q * 0.5f, ctr.y), strokeWidth = 1.5f)
                    markPaint.color = c2.toArgb()
                    nc.drawText(
                        if (res == ShotResult.INTERCEPTED) "AA" else "-",
                        ctr.x, ctr.y + cell * 0.62f, markPaint
                    )
                }
            }
        }

        // 最新一击：随雷达扫描呼吸的粗边框。
        if (lastShot != null) {
            val pulse = 0.45f + 0.35f * kotlin.math.abs(kotlin.math.sin(Math.toRadians(sweep.toDouble()))).toFloat()
            drawRect(
                pal.select.copy(alpha = pulse),
                topLeft = topLeft(lastShot.at),
                size = Size(cell, cell),
                style = Stroke(max(2f, cell * 0.10f))
            )
        }

        // 技能多选：目标序号框。
        for ((i, m) in markers.withIndex()) {
            drawRect(pal.select, topLeft = topLeft(m), size = Size(cell, cell), style = Stroke(max(2f, cell * 0.09f)))
            markPaint.color = pal.select.toArgb()
            nc.drawText((i + 1).toString(), center(m).x - cell * 0.34f, center(m).y - cell * 0.30f, markPaint)
        }

        // 潜艇鱼雷：从潜艇中心指向所选方向的路径与箭头。
        if (pathCells.size >= 2) {
            val a = center(pathCells.first())
            val b = center(pathCells.last())
            val lw = max(2f, cell * 0.10f)
            drawLine(pal.select.copy(alpha = 0.85f), a, b, strokeWidth = lw)
            for (p in pathCells.drop(1).dropLast(1)) {
                drawCircle(pal.select, radius = max(2f, cell * 0.08f), center = center(p))
            }
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            if (len > 0f) {
                val ux = dx / len
                val uy = dy / len
                val px = -uy
                val py = ux
                val s = cell * 0.28f
                drawLine(
                    pal.select, b,
                    Offset(b.x - ux * s + px * s * 0.6f, b.y - uy * s + py * s * 0.6f),
                    strokeWidth = lw
                )
                drawLine(
                    pal.select, b,
                    Offset(b.x - ux * s - px * s * 0.6f, b.y - uy * s - py * s * 0.6f),
                    strokeWidth = lw
                )
            }
        }

        if (highlight != null) {
            drawRect(
                pal.select,
                topLeft = topLeft(highlight),
                size = Size(cell, cell),
                style = Stroke(max(2f, cell * 0.10f))
            )
        }
    }
}
