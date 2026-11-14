package com.archipelago.battleship.core

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 三套原创主题（不使用任何影视 / 游戏素材，全部为通用视觉语言 + 矢量绘制）：
 *  PAPER   机舱手绘坐标纸：泛黄海图 + 钢笔蓝网格 + 红蓝铅笔标记
 *  WARROOM 二战情报室：墨绿呢面作战桌 + 黄铜标尺 + 暖色台灯光
 *  SONAR   舰桥声呐雷达：深海蓝 + 青绿荧光扫描
 */
enum class AppThemeMode { PAPER, WARROOM, SONAR }

fun themeLabel(mode: AppThemeMode): String = when (mode) {
    AppThemeMode.PAPER -> "机舱手绘坐标纸"
    AppThemeMode.WARROOM -> "战时情报室"
    AppThemeMode.SONAR -> "深海战术声呐"
}

/**
 * 海图配色。
 * frame / axis 系列是一整圈「深墨蓝（或墨绿 / 深蓝）坐标框」，与页面背景和水面都形成强对比；
 * legal / spent / recon / miss / hit / sunk / blocked 各自有独立底色 + 独立图形，互不混淆。
 */
data class BoardPalette(
    val frame: Color,
    val frameEdge: Color,
    val axis: Color,
    val axisCorner: Color,
    val axisText: Color,
    val axisEdge: Color,
    val water: Color,
    val waterAlt: Color,
    val grid: Color,
    val gridStrong: Color,
    val shallow: Color,
    val shallowInk: Color,
    val mountain: Color,
    val mountainInk: Color,
    val ship: Color,
    val shipHull: Color,
    val sunk: Color,
    val sunkFill: Color,
    val sunkInk: Color,
    val onSunk: Color,
    val hit: Color,
    val hitFill: Color,
    val onHit: Color,
    val miss: Color,
    val missFill: Color,
    val blocked: Color,
    val blockedFill: Color,
    val legalFill: Color,
    val legalInk: Color,
    val spentFill: Color,
    val spentInk: Color,
    val reconFill: Color,
    val reconInk: Color,
    val sonarFill: Color,
    val sonarInk: Color,
    val radarFill: Color,
    val radarInk: Color,
    val strikeFill: Color,
    val strikeInk: Color,
    val accent: Color,
    val select: Color,
    /** v13：判定 / 情报标识专用的不透明底衬与描边，负责把标识从水面里摘出来。 */
    val markerPlate: Color,
    val markerEdge: Color
)

fun palette(mode: AppThemeMode): BoardPalette = when (mode) {
    AppThemeMode.PAPER -> BoardPalette(
        frame = Color(0xFF11314C),
        frameEdge = Color(0xFF4E7CA6),
        axis = Color(0xFF0B2438),
        axisCorner = Color(0xFF1D4E72),
        axisText = Color(0xFFEAF4FF),
        axisEdge = Color(0xFF5B8CB5),
        water = Color(0xFFF8F2E2),
        waterAlt = Color(0xFFF5EDDC),
        grid = Color(0xFF2F5C8A),
        gridStrong = Color(0xFF1F4E79),
        shallow = Color(0xFFD9E9E6),
        shallowInk = Color(0xFF7FB4B0),
        mountain = Color(0xFF8A7352),
        mountainInk = Color(0xFF4A3A24),
        ship = Color(0xFF1B4066),
        shipHull = Color(0xFFDCE9F2),
        sunk = Color(0xFF6E1E1E),
        sunkFill = Color(0x66B03A2E),
        sunkInk = Color(0xFF7A1C14),
        onSunk = Color(0xFFFFF3E6),
        hit = Color(0xFFD3402C),
        hitFill = Color(0x55D3402C),
        onHit = Color(0xFFFFFFFF),
        miss = Color(0xFF1769A5),
        missFill = Color(0x331769A5),
        blocked = Color(0xFF7B4BA8),
        blockedFill = Color(0x4D7B4BA8),
        legalFill = Color(0x260E7A64),
        legalInk = Color(0xFF075E50),
        spentFill = Color(0x261B3350),
        spentInk = Color(0x884A5E70),
        reconFill = Color(0x2FB8791B),
        reconInk = Color(0xFFA9690F),
        sonarFill = Color(0x2A2F6FB0),
        sonarInk = Color(0xFF2F6FB0),
        radarFill = Color(0x2A12806C),
        radarInk = Color(0xFF127C74),
        strikeFill = Color(0x33C0492C),
        strikeInk = Color(0xFFC0492C),
        accent = Color(0xFF1F4E79),
        select = Color(0xFFE07B1B),
        markerPlate = Color(0xFFFFFFFF),
        markerEdge = Color(0xFF11314C)
    )
    AppThemeMode.WARROOM -> BoardPalette(
        frame = Color(0xFF1B2A20),
        frameEdge = Color(0xFF9A8552),
        axis = Color(0xFF101B14),
        axisCorner = Color(0xFF3C4A30),
        axisText = Color(0xFFF6E8C4),
        axisEdge = Color(0xFF9C8A55),
        water = Color(0xFF202C22),
        waterAlt = Color(0xFF243026),
        grid = Color(0xFFB9A46A),
        gridStrong = Color(0xFFD8C081),
        shallow = Color(0xFF33483C),
        shallowInk = Color(0xFF6E8C6A),
        mountain = Color(0xFF4A4030),
        mountainInk = Color(0xFFC6AE76),
        ship = Color(0xFFE8D9AC),
        shipHull = Color(0xFF2C3A2C),
        sunk = Color(0xFF7A2A22),
        sunkFill = Color(0x66B23B32),
        sunkInk = Color(0xFFFF9A7A),
        onSunk = Color(0xFFFFF0E6),
        hit = Color(0xFFE2603C),
        hitFill = Color(0x55E2603C),
        onHit = Color(0xFFFFFFFF),
        miss = Color(0xFF6FA8C9),
        missFill = Color(0x336FA8C9),
        blocked = Color(0xFFB98AD8),
        blockedFill = Color(0x4DB98AD8),
        legalFill = Color(0x2C9BE2A4),
        legalInk = Color(0xFFA8E8B4),
        spentFill = Color(0x26C9C09A),
        spentInk = Color(0x88D6C79E),
        reconFill = Color(0x2FE0A83A),
        reconInk = Color(0xFFE0C060),
        sonarFill = Color(0x337FB3E0),
        sonarInk = Color(0xFF7FB3E0),
        radarFill = Color(0x2F79D3B0),
        radarInk = Color(0xFF79D3B0),
        strikeFill = Color(0x33FF9A5A),
        strikeInk = Color(0xFFFF9A5A),
        accent = Color(0xFFD8B45A),
        select = Color(0xFFFF8A3C),
        markerPlate = Color(0xFFFBF3D9),
        markerEdge = Color(0xFF16241A)
    )
    AppThemeMode.SONAR -> BoardPalette(
        frame = Color(0xFF041C2B),
        frameEdge = Color(0xFF2B8C86),
        axis = Color(0xFF0A3A46),
        axisCorner = Color(0xFF12606A),
        axisText = Color(0xFF9BF6E2),
        axisEdge = Color(0xFF2F8E8C),
        water = Color(0xFF0A2034),
        waterAlt = Color(0xFF0B2539),
        grid = Color(0xFF35E0C0),
        gridStrong = Color(0xFF4FE6C8),
        shallow = Color(0xFF123C4A),
        shallowInk = Color(0xFF2E7C86),
        mountain = Color(0xFF2C4A4C),
        mountainInk = Color(0xFF7FD6C4),
        ship = Color(0xFF35E0C0),
        shipHull = Color(0xFF123C4A),
        sunk = Color(0xFF8E2E28),
        sunkFill = Color(0x66B23B32),
        sunkInk = Color(0xFFFF8A72),
        onSunk = Color(0xFFFFF0EA),
        hit = Color(0xFFFF6B4A),
        hitFill = Color(0x55FF6B4A),
        onHit = Color(0xFF2A0B06),
        miss = Color(0xFF4FC3F7),
        missFill = Color(0x334FC3F7),
        blocked = Color(0xFFC08AF0),
        blockedFill = Color(0x4DC08AF0),
        legalFill = Color(0x2C7BFFE0),
        legalInk = Color(0xFF8BFFE6),
        spentFill = Color(0x269FD8CC),
        spentInk = Color(0x884FC3F7),
        reconFill = Color(0x2FFFD166),
        reconInk = Color(0xFFFFD166),
        sonarFill = Color(0x334FC3F7),
        sonarInk = Color(0xFF4FC3F7),
        radarFill = Color(0x2F9BE1FF),
        radarInk = Color(0xFF9BE1FF),
        strikeFill = Color(0x33FF8A5C),
        strikeInk = Color(0xFFFF8A5C),
        accent = Color(0xFF35E0C0),
        select = Color(0xFFFFC107),
        markerPlate = Color(0xFFF0FFFB),
        markerEdge = Color(0xFF03202E)
    )
}

@Composable
fun ArchipelagoTheme(mode: AppThemeMode, content: @Composable () -> Unit) {
    val scheme = when (mode) {
        AppThemeMode.PAPER -> lightColorScheme(
            primary = Color(0xFF1F4E79),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFDCE9F5),
            onPrimaryContainer = Color(0xFF123A5C),
            secondary = Color(0xFFB85C16),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFFBE6CE),
            onSecondaryContainer = Color(0xFF6B3410),
            tertiary = Color(0xFF1B7D6B),
            background = Color(0xFFF2E9D6),
            onBackground = Color(0xFF142C45),
            surface = Color(0xFFFFFDF6),
            onSurface = Color(0xFF142C45),
            surfaceVariant = Color(0xFFE7DBC0),
            onSurfaceVariant = Color(0xFF415A70),
            outline = Color(0xFF8FA3B4),
            error = Color(0xFFC0392B),
            errorContainer = Color(0xFFFBE0DC),
            onErrorContainer = Color(0xFF7A1C14)
        )
        AppThemeMode.WARROOM -> darkColorScheme(
            primary = Color(0xFFE0C070),
            onPrimary = Color(0xFF2A2010),
            primaryContainer = Color(0xFF4A4128),
            onPrimaryContainer = Color(0xFFF6E8C0),
            secondary = Color(0xFFE08A5A),
            onSecondary = Color(0xFF2A1408),
            secondaryContainer = Color(0xFF5A3826),
            onSecondaryContainer = Color(0xFFFFD9BE),
            tertiary = Color(0xFF8FBF8A),
            background = Color(0xFF151E18),
            onBackground = Color(0xFFEDE2C6),
            surface = Color(0xFF223026),
            onSurface = Color(0xFFF0E6CC),
            surfaceVariant = Color(0xFF33402F),
            onSurfaceVariant = Color(0xFFC9BC93),
            outline = Color(0xFF7C7247),
            error = Color(0xFFE2603C),
            errorContainer = Color(0xFF4A231A),
            onErrorContainer = Color(0xFFFFD9CD)
        )
        AppThemeMode.SONAR -> darkColorScheme(
            primary = Color(0xFF35E0C0),
            onPrimary = Color(0xFF052018),
            primaryContainer = Color(0xFF0E4A47),
            onPrimaryContainer = Color(0xFFB6F5E7),
            secondary = Color(0xFFFFB074),
            onSecondary = Color(0xFF3B1C08),
            secondaryContainer = Color(0xFF4A2E1E),
            onSecondaryContainer = Color(0xFFFFD9BC),
            tertiary = Color(0xFFFFD166),
            background = Color(0xFF081426),
            onBackground = Color(0xFFD5F2EA),
            surface = Color(0xFF10263C),
            onSurface = Color(0xFFDDF3EC),
            surfaceVariant = Color(0xFF173A50),
            onSurfaceVariant = Color(0xFF9FD8CC),
            outline = Color(0xFF3A6E78),
            error = Color(0xFFFF6B4A),
            errorContainer = Color(0xFF4A1E1A),
            onErrorContainer = Color(0xFFFFD9D1)
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
