package com.archipelago.battleship.core

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.archipelago.battleship.domain.model.ShipType
import kotlin.math.max

/**
 * 原创舰种矢量标识（Compose Canvas 手绘）：
 *  潜艇        = 朝舰首实心三角 + 尾部一条垂直竖线
 *  驱逐/反潜/炮艇 = 纯实心锐角三角（反潜舰内加圆点、炮艇内加小横线）
 *  巡洋舰      = 矩形船体 + 三角舰首 + 一条斜杠 /
 *  战列舰      = 矩形船体 + 三角舰首 + 两条平行斜杠 //
 *  航空母舰    = 矩形船体 + 三角舰首 + 一条水平横杠 - + 交界处垂直竖杠 |
 */
fun DrawScope.drawShipGlyph(
    type: ShipType,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color,
    ink: Color,
    horizontal: Boolean,
    alpha: Float = 1f
) {
    val along = if (horizontal) width else height
    val thick = if (horizontal) height else width
    if (along <= 0f || thick <= 0f) return
    withTransform({
        if (horizontal) {
            translate(left, top)
        } else {
            translate(left + width, top)
            rotate(90f, pivot = Offset.Zero)
        }
    }) {
        drawGlyph(type, along, thick, color, ink, alpha)
    }
}

private fun DrawScope.drawGlyph(
    type: ShipType,
    w: Float,
    h: Float,
    color: Color,
    ink: Color,
    alpha: Float
) {
    val bow = w * 0.76f
    val stroke = max(1.5f, h * 0.11f)
    val p = Path()
    when (type) {
        ShipType.SUBMARINE -> {
            p.moveTo(0f, 0f)
            p.lineTo(w, h / 2f)
            p.lineTo(0f, h)
            p.close()
            drawPath(p, color, alpha = alpha)
            drawLine(color, Offset(0f, -h * 0.18f), Offset(0f, h * 1.18f), strokeWidth = stroke, alpha = alpha)
        }

        ShipType.DESTROYER, ShipType.ASW_SHIP, ShipType.GUNBOAT -> {
            p.moveTo(0f, 0f)
            p.lineTo(w, h / 2f)
            p.lineTo(0f, h)
            p.close()
            drawPath(p, color, alpha = alpha)
            if (type == ShipType.ASW_SHIP) {
                drawCircle(ink, radius = max(1.5f, h * 0.15f), center = Offset(w * 0.32f, h / 2f), alpha = alpha)
            }
            if (type == ShipType.GUNBOAT) {
                drawLine(ink, Offset(w * 0.28f, h / 2f), Offset(w * 0.56f, h / 2f), strokeWidth = stroke, alpha = alpha)
            }
        }

        else -> {
            p.moveTo(0f, 0f)
            p.lineTo(bow, 0f)
            p.lineTo(w, h / 2f)
            p.lineTo(bow, h)
            p.lineTo(0f, h)
            p.close()
            drawPath(p, color, alpha = alpha)
            when (type) {
                ShipType.BATTLESHIP -> {
                    slash(w * 0.22f, w * 0.42f, h, ink, stroke, alpha)
                    slash(w * 0.40f, w * 0.60f, h, ink, stroke, alpha)
                }
                ShipType.AVIATION_CRUISER, ShipType.FIRE_CRUISER, ShipType.AA_CRUISER -> {
                    slash(w * 0.28f, w * 0.52f, h, ink, stroke, alpha)
                }
                ShipType.CARRIER -> {
                    drawLine(ink, Offset(w * 0.12f, h / 2f), Offset(w * 0.66f, h / 2f), strokeWidth = stroke, alpha = alpha)
                    drawLine(ink, Offset(bow, 0f), Offset(bow, h), strokeWidth = stroke, alpha = alpha)
                }
                else -> Unit
            }
        }
    }
}

private fun DrawScope.slash(x0: Float, x1: Float, h: Float, ink: Color, stroke: Float, alpha: Float) {
    drawLine(ink, Offset(x0, h * 0.86f), Offset(x1, h * 0.14f), strokeWidth = stroke, alpha = alpha)
}
