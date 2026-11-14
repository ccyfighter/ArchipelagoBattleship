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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import kotlin.math.abs
import kotlin.math.max

/**
 * 双主题海图棋盘。
 * 画布四周是一圈深墨蓝「坐标标尺带」，与页面背景、与浅色水面都形成强反差；
 * 格内状态采用「底色 + 形状 + 颜色」三重编码，任意两项之间都不会混淆：
 *   可攻击 = 高对比内缩描边框 + 角点 / 已攻击 = 灰色斜纹 / 未命中 = 蓝空心圆环
 *   命中 = 红实心圆 + 白叉 / 击沉 = 整艘船深红框 + 白圆红叉 + S 角标
 *   拦截·免疫·阻挡 = 紫斜纹 + 菱形盾 / 最近一击 = 橙色呼吸双框
 *
 * v10 降噪：calm = true 时压掉交错格的明暗跳变、手绘细网格、声呐同心圆与扫描扇形，
 * 棋盘只保留「地形 / 战果 / 情报层」这些真正需要读的信息。
 * 「可攻击」标记也从每格 8 条四角括号 + 中心点，简化为 1 个内缩描边框 + 1 个角点。
 */
@Composable
fun BoardCanvas(
    board: Board,
    mode: AppThemeMode,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    legalTargets: List<Coord> = emptyList(),
    highlight: Coord? = null,
    markers: List<Coord> = emptyList(),
    /** 情报视图：压掉装饰性纹理，让战果 / 情报层成为画面主体。 */
    calm: Boolean = false,
    onTap: (Coord) -> Unit = {}
) {
    val pal = palette(mode)
    val density = LocalDensity.current
    val axisPx = with(density) { 26.dp.toPx() }
    val outerPx = with(density) { 10.dp.toPx() }
    val originPx = outerPx + axisPx
    fun cellFor(side: Float, n: Int): Float = (side - outerPx * 2f - axisPx) / n.toFloat()

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
            .pointerInput(board.size, interactive, axisPx, outerPx) {
                if (!interactive) return@pointerInput
                detectTapGestures { off ->
                    // PointerInputScope.size is IntSize (no minDimension); DrawScope.size is Size.
                    val side = minOf(size.width, size.height).toFloat()
                    val cw = cellFor(side, board.size)
                    if (cw <= 0f) return@detectTapGestures
                    val c = ((off.x - originPx) / cw).toInt()
                    val r = ((off.y - originPx) / cw).toInt()
                    val coord = Coord(r, c)
                    if (coord.inBounds(board.size)) currentOnTap(coord)
                }
            }
    ) {
        val n = board.size
        val cell = max(1f, cellFor(size.minDimension, n))
        val span = cell * n

        fun topLeft(c: Coord) = Offset(originPx + c.c * cell, originPx + c.r * cell)
        fun center(c: Coord) = Offset(originPx + (c.c + 0.5f) * cell, originPx + (c.r + 0.5f) * cell)

        val nc = drawContext.canvas.nativeCanvas

        // ---------- 1. 海图外框：与页面背景拉开差异 ----------
        drawRect(pal.frame)
        drawRect(
            pal.frameEdge,
            topLeft = Offset(2f, 2f),
            size = Size(size.width - 4f, size.height - 4f),
            style = Stroke(2f)
        )

        // ---------- 2. 深色坐标标尺带 ----------
        drawRect(pal.axis, topLeft = Offset(originPx, outerPx), size = Size(span, axisPx))
        drawRect(pal.axis, topLeft = Offset(outerPx, originPx), size = Size(axisPx, span))
        drawRect(pal.axisCorner, topLeft = Offset(outerPx, outerPx), size = Size(axisPx, axisPx))
        drawRect(
            pal.frameEdge,
            topLeft = Offset(outerPx, outerPx),
            size = Size(axisPx + span, axisPx + span),
            style = Stroke(1.5f)
        )
        drawLine(
            pal.frame, Offset(outerPx + axisPx * 0.22f, outerPx + axisPx * 0.5f),
            Offset(outerPx + axisPx * 0.78f, outerPx + axisPx * 0.5f), strokeWidth = 1.5f
        )
        drawLine(
            pal.frame, Offset(outerPx + axisPx * 0.5f, outerPx + axisPx * 0.22f),
            Offset(outerPx + axisPx * 0.5f, outerPx + axisPx * 0.78f), strokeWidth = 1.5f
        )
        for (i in 0 until n) {
            val p = originPx + (i + 1) * cell
            drawLine(
                pal.axisEdge.copy(alpha = 0.55f),
                Offset(p, outerPx + axisPx * 0.60f), Offset(p, originPx), strokeWidth = 1f
            )
            drawLine(
                pal.axisEdge.copy(alpha = 0.55f),
                Offset(outerPx + axisPx * 0.60f, p), Offset(originPx, p), strokeWidth = 1f
            )
        }
        drawLine(pal.axisEdge, Offset(originPx, originPx), Offset(originPx + span, originPx), strokeWidth = 2f)
        drawLine(pal.axisEdge, Offset(originPx, originPx), Offset(originPx, originPx + span), strokeWidth = 2f)

        val axisTextSize = axisPx * 0.48f
        fun axisPaint(ink: Int, size: Float) = Paint().apply {
            color = ink
            textSize = size
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        val colPaint = axisPaint(pal.axisText.toArgb(), axisTextSize)
        val rowPaint = axisPaint(pal.axisText.toArgb(), axisPx * 0.46f)
        val hotPaint = axisPaint(pal.frame.toArgb(), axisTextSize)
        for (i in 0 until n) {
            val label = if (i < 26) ('A'.code + i).toChar().toString() else (i + 1).toString()
            val hot = highlight != null && highlight.c == i
            if (hot) {
                drawRect(
                    pal.select.copy(alpha = 0.92f),
                    topLeft = Offset(originPx + i * cell + 1f, outerPx + 1f),
                    size = Size(cell - 2f, axisPx - 2f)
                )
            }
            nc.drawText(
                label, originPx + (i + 0.5f) * cell, outerPx + axisPx * 0.71f,
                if (hot) hotPaint else colPaint
            )
        }
        for (i in 0 until n) {
            val hot = highlight != null && highlight.r == i
            if (hot) {
                drawRect(
                    pal.select.copy(alpha = 0.92f),
                    topLeft = Offset(outerPx + 1f, originPx + i * cell + 1f),
                    size = Size(axisPx - 2f, cell - 2f)
                )
            }
            nc.drawText(
                (i + 1).toString(), outerPx + axisPx * 0.5f,
                originPx + (i + 0.5f) * cell + axisPx * 0.17f,
                if (hot) hotPaint else rowPaint
            )
        }

        // ---------- 3. 水面：交错格 + 手绘细网格 ----------
        drawRect(pal.water, topLeft = Offset(originPx, originPx), size = Size(span, span))
        for (r in 0 until n) {
            for (c in 0 until n) {
                if ((r + c) % 2 == 0) {
                    drawRect(
                        if (calm) pal.waterAlt.copy(alpha = 0.28f) else pal.waterAlt,
                        topLeft = Offset(originPx + c * cell, originPx + r * cell),
                        size = Size(cell, cell)
                    )
                }
            }
        }
        if (mode == AppThemeMode.PAPER && !calm) {
            for (i in 0 until n * 4) {
                val p = originPx + i * cell / 4f
                drawLine(
                    pal.grid.copy(alpha = 0.09f),
                    Offset(p, originPx), Offset(p, originPx + span), strokeWidth = 0.6f
                )
                drawLine(
                    pal.grid.copy(alpha = 0.09f),
                    Offset(originPx, p), Offset(originPx + span, p), strokeWidth = 0.6f
                )
            }
        }

        // ---------- 4. 地形：浅滩点阵 / 山地斜纹 + 山峰 ----------
        for (c in board.shallows) {
            if (!c.inBounds(n)) continue
            val tl = topLeft(c)
            drawRect(pal.shallow, topLeft = tl, size = Size(cell, cell))
            val d = max(1f, cell * 0.05f)
            for (k in 1..2) {
                for (j in 1..2) {
                    drawCircle(
                        pal.shallowInk.copy(alpha = 0.62f),
                        radius = d,
                        center = Offset(tl.x + cell * k / 3f, tl.y + cell * j / 3f)
                    )
                }
            }
        }
        for (c in board.mountains) {
            if (!c.inBounds(n)) continue
            val tl = topLeft(c)
            drawRect(pal.mountain, topLeft = tl, size = Size(cell, cell))
            drawLine(
                pal.mountainInk.copy(alpha = 0.30f),
                Offset(tl.x + cell * 0.10f, tl.y + cell * 0.92f),
                Offset(tl.x + cell * 0.55f, tl.y + cell * 0.08f), strokeWidth = 1f
            )
            drawLine(
                pal.mountainInk.copy(alpha = 0.30f),
                Offset(tl.x + cell * 0.48f, tl.y + cell * 0.92f),
                Offset(tl.x + cell * 0.92f, tl.y + cell * 0.08f), strokeWidth = 1f
            )
            val peak = Path().apply {
                moveTo(tl.x + cell * 0.16f, tl.y + cell * 0.76f)
                lineTo(tl.x + cell * 0.50f, tl.y + cell * 0.20f)
                lineTo(tl.x + cell * 0.84f, tl.y + cell * 0.76f)
                close()
            }
            drawPath(peak, pal.mountainInk.copy(alpha = 0.85f))
            drawLine(
                pal.frame,
                Offset(tl.x + cell * 0.38f, tl.y + cell * 0.50f),
                Offset(tl.x + cell * 0.62f, tl.y + cell * 0.50f),
                strokeWidth = max(1f, cell * 0.05f)
            )
        }

        // ---------- 5. 主网格：每 5 格加粗 ----------
        val gridAlpha = when {
            calm && mode == AppThemeMode.PAPER -> 0.18f
            calm -> 0.10f
            mode == AppThemeMode.PAPER -> 0.42f
            else -> 0.24f
        }
        val strongBoost = if (calm) 0.12f else 0.30f
        for (i in 0..n) {
            val p = originPx + i * cell
            val strong = i % 5 == 0 || i == n
            val ink = if (strong) pal.gridStrong else pal.grid
            val a = if (strong) gridAlpha + strongBoost else gridAlpha
            val w = if (strong) 1.8f else 1f
            drawLine(ink.copy(alpha = a), Offset(p, originPx), Offset(p, originPx + span), strokeWidth = w)
            drawLine(ink.copy(alpha = a), Offset(originPx, p), Offset(originPx + span, p), strokeWidth = w)
        }

        // ---------- 6. 声呐主题：同心圆 + 扫描扇形 ----------
        if (mode == AppThemeMode.SONAR && !calm) {
            val cx = originPx + span / 2f
            val cy = originPx + span / 2f
            val radius = span / 2f
            for (k in 1..4) {
                drawCircle(
                    pal.grid.copy(alpha = 0.13f), radius = radius * k / 4f,
                    center = Offset(cx, cy), style = Stroke(1f)
                )
            }
            drawArc(
                color = pal.accent.copy(alpha = 0.09f),
                startAngle = sweep - 24f,
                sweepAngle = 38f,
                useCenter = true,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2f, radius * 2f)
            )
            val rad = Math.toRadians(sweep.toDouble())
            drawLine(
                pal.accent.copy(alpha = 0.45f),
                Offset(cx, cy),
                Offset(
                    cx + radius * kotlin.math.cos(rad).toFloat(),
                    cy + radius * kotlin.math.sin(rad).toFloat()
                ),
                strokeWidth = 2f
            )
        }

        // ---------- 6.5 情报覆盖层：四种互不相同的底纹 + 角标 ----------
        // 纯显示层：不写 attackedPoints、不参与候选格计算，拦截 / 侦察 / 扫描都不影响攻击。
        /** v13：情报层统一「内缩底衬 + 不透明描边」，不再用一层淡色盖在水面上。 */
        fun intelFrame(c: Coord, fill: Color, ink: Color) {
            val tl = topLeft(c)
            val m = cell * 0.10f
            val p = Offset(tl.x + m, tl.y + m)
            val s = Size(cell - 2f * m, cell - 2f * m)
            drawRect(fill.copy(alpha = 0.38f), topLeft = p, size = s)
            drawRect(ink, topLeft = p, size = s, style = Stroke(max(1.3f, cell * 0.045f)))
        }
        // 陆航空袭预告：橙红底 + 细斜叉
        for (c in board.strikeCells) {
            if (!c.inBounds(n)) continue
            val tl = topLeft(c)
            intelFrame(c, pal.strikeFill, pal.strikeInk)
            val m = cell * 0.24f
            val w = max(1.2f, cell * 0.045f)
            drawLine(pal.strikeInk.copy(alpha = 0.85f), Offset(tl.x + m, tl.y + m), Offset(tl.x + cell - m, tl.y + cell - m), strokeWidth = w)
            drawLine(pal.strikeInk.copy(alpha = 0.85f), Offset(tl.x + cell - m, tl.y + m), Offset(tl.x + m, tl.y + cell - m), strokeWidth = w)
        }
        // 监视雷达：青底 + 四边短划虚线框 + 中心回波点
        for (c in board.radarCells) {
            if (!c.inBounds(n)) continue
            val tl = topLeft(c)
            intelFrame(c, pal.radarFill, pal.radarInk)
            val m = cell * 0.14f
            val seg = cell * 0.18f
            val w = max(1f, cell * 0.035f)
            for (k in 0..1) {
                val a = m + k * (cell - 2f * m - seg)
                drawLine(pal.radarInk.copy(alpha = 0.75f), Offset(tl.x + a, tl.y + m), Offset(tl.x + a + seg, tl.y + m), strokeWidth = w)
                drawLine(pal.radarInk.copy(alpha = 0.75f), Offset(tl.x + a, tl.y + cell - m), Offset(tl.x + a + seg, tl.y + cell - m), strokeWidth = w)
                drawLine(pal.radarInk.copy(alpha = 0.75f), Offset(tl.x + m, tl.y + a), Offset(tl.x + m, tl.y + a + seg), strokeWidth = w)
                drawLine(pal.radarInk.copy(alpha = 0.75f), Offset(tl.x + cell - m, tl.y + a), Offset(tl.x + cell - m, tl.y + a + seg), strokeWidth = w)
            }
            drawCircle(pal.radarInk, radius = max(1.3f, cell * 0.075f), center = center(c))
        }
        // 主动水听声呐：蓝底 + 两圈同心波纹
        for (c in board.sonarCells) {
            if (!c.inBounds(n)) continue
            val tl = topLeft(c)
            intelFrame(c, pal.sonarFill, pal.sonarInk)
            val ctr = center(c)
            val sw = max(1f, cell * 0.04f)
            drawCircle(pal.sonarInk.copy(alpha = 0.85f), radius = cell * 0.17f, center = ctr, style = Stroke(sw))
            drawCircle(pal.sonarInk.copy(alpha = 0.55f), radius = cell * 0.31f, center = ctr, style = Stroke(sw))
        }
        // 舰载侦察机：琥珀底 + 方框 + 放大镜角标
        for (c in board.reconCells) {
            if (!c.inBounds(n)) continue
            val tl = topLeft(c)
            intelFrame(c, pal.reconFill, pal.reconInk)
            val m = cell * 0.12f
            drawRect(
                pal.reconInk.copy(alpha = 0.65f),
                topLeft = Offset(tl.x + m, tl.y + m),
                size = Size(cell - 2f * m, cell - 2f * m),
                style = Stroke(max(1f, cell * 0.04f))
            )
            // 放大镜：小圆环 + 右下短柄，与未命中圆环 / 命中圆点 / 击沉方块都不重复
            val lens = Offset(tl.x + cell * 0.43f, tl.y + cell * 0.43f)
            drawCircle(
                pal.reconInk, radius = cell * 0.15f, center = lens,
                style = Stroke(max(1f, cell * 0.05f))
            )
            drawLine(
                pal.reconInk,
                Offset(lens.x + cell * 0.11f, lens.y + cell * 0.11f),
                Offset(lens.x + cell * 0.25f, lens.y + cell * 0.25f),
                strokeWidth = max(1f, cell * 0.06f)
            )
        }

        // ---------- 7. 已攻击格：灰斜纹，明确「此格已封锁」 ----------
        for (c in board.attackedPoints) {
            if (!c.inBounds(n)) continue
            val tl = topLeft(c)
            drawRect(pal.spentFill, topLeft = tl, size = Size(cell, cell))
            drawLine(
                pal.spentInk,
                Offset(tl.x + cell * 0.10f, tl.y + cell * 0.90f),
                Offset(tl.x + cell * 0.90f, tl.y + cell * 0.10f),
                strokeWidth = max(1.3f, cell * 0.035f)
            )
            drawRect(
                pal.markerEdge.copy(alpha = 0.30f), topLeft = tl,
                size = Size(cell, cell), style = Stroke(1f)
            )
        }

        // ---------- 8. 可攻击候选：单个内缩描边框 + 左上实心角点 ----------
        // v10 之前每格画 8 条四角括号 + 1 个中心圆点，14×14 满屏近 2000 条线段，
        // 加上交错格与细网格就是「看着花」的主因。现在每格只画 2 个形状，
        // 并用与水面明显拉开的高对比 legalInk 描边，反而更容易一眼看清哪些格能打。
        if (interactive && legalTargets.isNotEmpty()) {
            for (c in legalTargets) {
                if (!c.inBounds(n) || c in board.attackedPoints) continue
                val tl = topLeft(c)
                drawRect(pal.legalFill, topLeft = tl, size = Size(cell, cell))
                val m = cell * 0.15f
                drawRect(
                    pal.legalInk,
                    topLeft = Offset(tl.x + m, tl.y + m),
                    size = Size(cell - 2f * m, cell - 2f * m),
                    style = Stroke(max(1.8f, cell * 0.065f))
                )
                drawCircle(
                    pal.legalInk,
                    radius = max(1.2f, cell * 0.055f),
                    center = Offset(tl.x + m, tl.y + m)
                )
            }
        }

        // ---------- 9. 舰船：船体底板 + 矢量舰种图标；沉船整艘标残骸 ----------
        val sunkCells = HashSet<Coord>()
        for (ship in board.ships) {
            if (ship.cells.isEmpty()) continue
            val minR = ship.cells.minOf { it.r }
            val maxR = ship.cells.maxOf { it.r }
            val minC = ship.cells.minOf { it.c }
            val maxC = ship.cells.maxOf { it.c }
            val horizontal = ship.orientation == Orientation.H
            val col = if (ship.sunk) pal.sunk else pal.ship
            val alpha = if (ship.type == ShipType.SUBMARINE && !ship.surfaced && !ship.sunk) 0.68f else 1f

            val hullLeft = originPx + minC * cell + cell * 0.06f
            val hullTop = originPx + minR * cell + cell * 0.06f
            val hullW = (maxC - minC + 1) * cell - cell * 0.12f
            val hullH = (maxR - minR + 1) * cell - cell * 0.12f
            drawRect(
                if (ship.sunk) pal.sunkFill else pal.shipHull.copy(alpha = 0.78f),
                topLeft = Offset(hullLeft, hullTop), size = Size(hullW, hullH)
            )
            drawRect(
                col, topLeft = Offset(hullLeft, hullTop), size = Size(hullW, hullH),
                style = Stroke(max(1.2f, cell * 0.05f))
            )
            if (ship.sunk) {
                drawLine(
                    pal.sunkInk.copy(alpha = 0.95f),
                    Offset(hullLeft, hullTop + hullH), Offset(hullLeft + hullW, hullTop),
                    strokeWidth = max(1.5f, cell * 0.07f)
                )
                sunkCells.addAll(ship.cells)
            }
            if (horizontal) {
                val w = (maxC - minC + 1) * cell
                val h = minOf(cell * 0.60f, w * 0.42f)
                drawShipGlyph(
                    ship.type, originPx + minC * cell, originPx + (minR + 0.5f) * cell - h / 2f,
                    w, h, col, pal.water, true, alpha
                )
            } else {
                val w = (maxR - minR + 1) * cell
                val h = minOf(cell * 0.60f, w * 0.42f)
                drawShipGlyph(
                    ship.type, originPx + (minC + 0.5f) * cell - h / 2f, originPx + minR * cell,
                    h, w, col, pal.water, false, alpha
                )
            }
            for (hc in ship.hits) {
                drawCircle(
                    if (ship.sunk) pal.onSunk else pal.hit,
                    radius = max(1.6f, cell * 0.09f), center = center(hc)
                )
            }
        }

        // ---------- 10. 判定结果：底色 + 形状 + 颜色三重编码 ----------
        val lastByCell = HashMap<Coord, ShotResult>()
        for (s in board.shots) lastByCell[s.at] = s.result
        val lastShot = board.shots.lastOrNull()
        val labelPaint = Paint().apply {
            textSize = max(8f, cell * 0.24f)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        for ((c, res0) in lastByCell) {
            if (!c.inBounds(n)) continue
            // 击沉是整艘船的状态：把该船所有格统一渲染为「击沉」，不只是最后一炮那一格
            val res = if (c in sunkCells) ShotResult.SUNK else res0
            val tl = topLeft(c)
            val ctr = center(c)
            val q = cell * 0.28f
            val sw = max(1.8f, cell * 0.075f)
            // v13：每类判定先垫一块「不透明底衬 + 深色描边」的方块，
            // 让标识从水面、交错格与网格里彻底分离；符号再加一圈暗色光晕提高辨识度。
            val inset = cell * 0.07f
            val plateTl = Offset(tl.x + inset, tl.y + inset)
            val plateSize = Size(cell - 2f * inset, cell - 2f * inset)
            drawRect(pal.markerPlate, topLeft = plateTl, size = plateSize)
            drawRect(
                pal.markerEdge, topLeft = plateTl, size = plateSize,
                style = Stroke(max(1.3f, cell * 0.045f))
            )
            when (res) {
                ShotResult.MISS -> {
                    drawRect(pal.missFill, topLeft = plateTl, size = plateSize)
                    drawCircle(
                        pal.markerEdge, radius = q * 1.10f, center = ctr,
                        style = Stroke(max(1.8f, cell * 0.072f))
                    )
                    drawCircle(pal.miss, radius = q, center = ctr, style = Stroke(max(1.4f, cell * 0.055f)))
                    drawCircle(pal.miss, radius = q * 0.30f, center = ctr)
                    val a = q * 1.18f
                    val b = q * 1.48f
                    drawLine(pal.miss.copy(alpha = 0.75f), Offset(ctr.x, ctr.y - a), Offset(ctr.x, ctr.y - b), strokeWidth = 1.2f)
                    drawLine(pal.miss.copy(alpha = 0.75f), Offset(ctr.x, ctr.y + a), Offset(ctr.x, ctr.y + b), strokeWidth = 1.2f)
                    drawLine(pal.miss.copy(alpha = 0.75f), Offset(ctr.x - a, ctr.y), Offset(ctr.x - b, ctr.y), strokeWidth = 1.2f)
                    drawLine(pal.miss.copy(alpha = 0.75f), Offset(ctr.x + a, ctr.y), Offset(ctr.x + b, ctr.y), strokeWidth = 1.2f)
                }
                ShotResult.HIT -> {
                    drawRect(pal.hitFill, topLeft = plateTl, size = plateSize)
                    drawRect(pal.hit, topLeft = plateTl, size = plateSize, style = Stroke(sw))
                    drawCircle(pal.markerEdge, radius = q * 1.05f, center = ctr)
                    drawCircle(pal.hit, radius = q * 0.92f, center = ctr)
                    val d = q * 0.46f
                    drawLine(pal.onHit, Offset(ctr.x - d, ctr.y - d), Offset(ctr.x + d, ctr.y + d), strokeWidth = max(1.4f, cell * 0.055f))
                    drawLine(pal.onHit, Offset(ctr.x + d, ctr.y - d), Offset(ctr.x - d, ctr.y + d), strokeWidth = max(1.4f, cell * 0.055f))
                }
                ShotResult.SUNK -> {
                    drawRect(pal.sunk, topLeft = plateTl, size = plateSize)
                    drawRect(pal.sunkInk, topLeft = plateTl, size = plateSize, style = Stroke(sw * 1.2f))
                    drawCircle(pal.markerEdge, radius = q * 1.05f, center = ctr)
                    drawCircle(pal.onSunk, radius = q * 0.94f, center = ctr)
                    val d = q * 0.46f
                    drawLine(pal.sunkInk, Offset(ctr.x - d, ctr.y - d), Offset(ctr.x + d, ctr.y + d), strokeWidth = max(1.6f, cell * 0.065f))
                    drawLine(pal.sunkInk, Offset(ctr.x + d, ctr.y - d), Offset(ctr.x - d, ctr.y + d), strokeWidth = max(1.6f, cell * 0.065f))
                    val corner = max(1.5f, cell * 0.055f)
                    drawRect(pal.onSunk, topLeft = Offset(tl.x + cell * 0.10f, tl.y + cell * 0.10f), size = Size(corner * 2f, corner * 2f))
                    drawRect(pal.onSunk, topLeft = Offset(tl.x + cell * 0.90f - corner * 2f, tl.y + cell * 0.90f - corner * 2f), size = Size(corner * 2f, corner * 2f))
                    labelPaint.color = pal.sunkInk.toArgb()
                    nc.drawText("S", tl.x + cell * 0.80f, tl.y + cell * 0.36f, labelPaint)
                }
                ShotResult.INTERCEPTED, ShotResult.IMMUNE, ShotResult.BLOCKED -> {
                    // 这些格子并未被锁死，但仍要有清晰的独立标识：底衬 + 斜纹 + 盾牌
                    drawRect(pal.blockedFill.copy(alpha = 0.30f), topLeft = plateTl, size = plateSize)
                    for (k in 0..2) {
                        val o = cell * (0.18f + k * 0.28f)
                        drawLine(
                            pal.blocked.copy(alpha = 0.55f),
                            Offset(tl.x + o, tl.y + cell * 0.88f),
                            Offset(tl.x + o + cell * 0.16f, tl.y + cell * 0.12f),
                            strokeWidth = 1f
                        )
                    }
                    drawCircle(pal.markerEdge, radius = q * 1.06f, center = ctr)
                    val shield = Path().apply {
                        moveTo(ctr.x, ctr.y - q)
                        lineTo(ctr.x + q * 0.82f, ctr.y)
                        lineTo(ctr.x, ctr.y + q)
                        lineTo(ctr.x - q * 0.82f, ctr.y)
                        close()
                    }
                    drawPath(shield, pal.blocked)
                    drawLine(
                        pal.onSunk, Offset(ctr.x - q * 0.36f, ctr.y + q * 0.30f),
                        Offset(ctr.x + q * 0.36f, ctr.y - q * 0.30f),
                        strokeWidth = max(1.6f, cell * 0.06f)
                    )
                    labelPaint.color = pal.markerEdge.toArgb()
                    val tag = when (res) {
                        ShotResult.INTERCEPTED -> "A"
                        ShotResult.IMMUNE -> "I"
                        else -> "B"
                    }
                    nc.drawText(tag, tl.x + cell * 0.80f, tl.y + cell * 0.36f, labelPaint)
                }
            }
        }

        // ---------- 11. 最近一次攻击：橙色呼吸双框 ----------
        if (lastShot != null) {
            val pulse = 0.50f + 0.35f * abs(kotlin.math.sin(Math.toRadians(sweep.toDouble()))).toFloat()
            val tl = topLeft(lastShot.at)
            drawRect(
                pal.select.copy(alpha = pulse), topLeft = tl, size = Size(cell, cell),
                style = Stroke(max(2.2f, cell * 0.10f))
            )
            drawRect(
                pal.select.copy(alpha = pulse * 0.45f),
                topLeft = Offset(tl.x - 3f, tl.y - 3f),
                size = Size(cell + 6f, cell + 6f),
                style = Stroke(1.5f)
            )
        }

        // ---------- 12. 技能多选：右上角序号徽标 ----------
        for ((i, m) in markers.withIndex()) {
            if (!m.inBounds(n)) continue
            val tl = topLeft(m)
            drawRect(pal.select.copy(alpha = 0.22f), topLeft = tl, size = Size(cell, cell))
            drawRect(
                pal.select, topLeft = tl, size = Size(cell, cell),
                style = Stroke(max(2f, cell * 0.07f))
            )
            val badge = Offset(tl.x + cell * 0.78f, tl.y + cell * 0.22f)
            drawCircle(pal.select, radius = cell * 0.19f, center = badge)
            labelPaint.color = pal.frame.toArgb()
            nc.drawText((i + 1).toString(), badge.x, badge.y + cell * 0.085f, labelPaint)
        }

        // ---------- 13. 当前预览目标：粗框 + 四角大括号 ----------
        // 两种鱼雷都不再绘制航线：整条推进线会把潜艇所在行 / 列直接画给对手看，
        // 现在棋盘只保留「定方向点」的高亮框，爆炸结果只出现在唯一爆炸格。
        if (highlight != null && highlight.inBounds(n)) {
            val tl = topLeft(highlight)
            drawRect(
                pal.select, topLeft = tl, size = Size(cell, cell),
                style = Stroke(max(2.2f, cell * 0.10f))
            )
            val e = cell * 0.34f
            val g = 3f
            val w = max(2f, cell * 0.09f)
            drawLine(pal.select, Offset(tl.x - g, tl.y + e), Offset(tl.x - g, tl.y - g), strokeWidth = w)
            drawLine(pal.select, Offset(tl.x - g, tl.y - g), Offset(tl.x + e, tl.y - g), strokeWidth = w)
            drawLine(pal.select, Offset(tl.x + cell - e, tl.y - g), Offset(tl.x + cell + g, tl.y - g), strokeWidth = w)
            drawLine(pal.select, Offset(tl.x + cell + g, tl.y - g), Offset(tl.x + cell + g, tl.y + e), strokeWidth = w)
            drawLine(pal.select, Offset(tl.x - g, tl.y + cell - e), Offset(tl.x - g, tl.y + cell + g), strokeWidth = w)
            drawLine(pal.select, Offset(tl.x - g, tl.y + cell + g), Offset(tl.x + e, tl.y + cell + g), strokeWidth = w)
            drawLine(pal.select, Offset(tl.x + cell - e, tl.y + cell + g), Offset(tl.x + cell + g, tl.y + cell + g), strokeWidth = w)
            drawLine(pal.select, Offset(tl.x + cell + g, tl.y + cell - e), Offset(tl.x + cell + g, tl.y + cell + g), strokeWidth = w)
        }
    }
}
