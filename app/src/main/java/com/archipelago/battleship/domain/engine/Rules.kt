package com.archipelago.battleship.domain.engine

import com.archipelago.battleship.domain.model.Board
import com.archipelago.battleship.domain.model.Coord
import com.archipelago.battleship.domain.model.DIR4
import com.archipelago.battleship.domain.model.DIR8
import com.archipelago.battleship.domain.model.FleetCatalog
import com.archipelago.battleship.domain.model.FleetConfig
import com.archipelago.battleship.domain.model.GameConfig
import com.archipelago.battleship.domain.model.Orientation
import com.archipelago.battleship.domain.model.Placement
import com.archipelago.battleship.domain.model.Ship
import com.archipelago.battleship.domain.model.ShipType
import com.archipelago.battleship.domain.model.SkillKind
import com.archipelago.battleship.domain.model.inBounds
import kotlin.math.abs
import kotlin.random.Random

/**
 * 纯 Kotlin 规则层：地形生成、布阵合法性、技能参数表、传感器覆盖区。
 * 不依赖 Android / Compose / 网络，可单测。
 */
object Rules {

    const val TURN_SECONDS = 180

    /** 每回合阶段二的攻击名额上限（同时不得超过存活舰船数）。 */
    const val BASE_ATTACKS_PER_TURN = 3

    /** 山地总面积占全图的最大比例。 */
    const val MOUNTAIN_MAX_RATIO = 0.20f

    /**
     * 鱼雷机从「投雷起点」向前推进的格数。
     * 航线 = 起点 + 3 步 = 共 4 格，例如 M14 向西可打到 J14（L/K/J 各 1 步）。
     * v8 之前写成 0..2（只有 3 格、最远 K14），导致射程比标注少 1 格。
     */
    const val TORPEDO_BOMBER_STEPS = 3

    /** 潜艇潜航鱼雷单方向的最大推进格数；同向连射按 4→3→2→1 递减。 */
    const val TORPEDO_MAX_STEPS = 4

    /** 鱼雷发射方向台账的 key，例如向北 = "-1,0"。 */
    fun torpedoLaneKey(dr: Int, dc: Int): String = "$dr,$dc"

    /** 某个方向当前剩余推进格数；没有台账记录时视为满射程。 */
    fun torpedoReach(ship: Ship, dr: Int, dc: Int): Int =
        (ship.torpedoLanes[torpedoLaneKey(dr, dc)] ?: TORPEDO_MAX_STEPS)
            .coerceIn(0, TORPEDO_MAX_STEPS)

    fun attacksPerTurn(aliveShips: Int): Int = minOf(aliveShips, BASE_ATTACKS_PER_TURN)

    fun cellsOf(origin: Coord, size: Int, o: Orientation): List<Coord> {
        val out = ArrayList<Coord>(size)
        for (i in 0 until size) {
            out.add(if (o == Orientation.H) Coord(origin.r, origin.c + i) else Coord(origin.r + i, origin.c))
        }
        return out
    }

    /* ------------------------- 地形 ------------------------- */

    private var cachedConfig: GameConfig? = null
    private var cachedTerrain: Pair<Set<Coord>, Set<Coord>> = emptySet<Coord>() to emptySet<Coord>()

    @Synchronized
    fun terrain(config: GameConfig): Pair<Set<Coord>, Set<Coord>> {
        if (cachedConfig == config) return cachedTerrain
        val value = generateTerrain(config)
        cachedConfig = config
        cachedTerrain = value
        return value
    }

    /** 单座岛的相对形状：1~6 格，尺寸与轮廓都随机，不强制连片、不强制对称。 */
    private val ISLAND_SHAPES: List<List<Coord>> = listOf(
        listOf(Coord(0, 0)),
        listOf(Coord(0, 0), Coord(0, 1)),
        listOf(Coord(0, 0), Coord(1, 0)),
        listOf(Coord(0, 0), Coord(0, 1), Coord(0, 2)),
        listOf(Coord(0, 0), Coord(0, 1), Coord(0, 2), Coord(1, 0), Coord(1, 1), Coord(1, 2)),
        listOf(Coord(0, 0), Coord(0, 1), Coord(1, 0), Coord(1, 1)),
        listOf(Coord(0, 0), Coord(0, 1), Coord(1, 0), Coord(1, 1)),
        listOf(Coord(0, 0), Coord(1, 0), Coord(1, 1)),
        listOf(Coord(0, 0), Coord(0, 1), Coord(1, 1), Coord(2, 1)),
        listOf(Coord(0, 0), Coord(0, 1), Coord(1, 0), Coord(1, 1), Coord(2, 0), Coord(2, 1)),
        listOf(Coord(0, 0), Coord(0, 1), Coord(1, 0), Coord(1, 1), Coord(2, 0), Coord(2, 1))
    )

    private fun generateTerrain(config: GameConfig): Pair<Set<Coord>, Set<Coord>> {
        if (!config.mountains || config.size < 12) {
            return emptySet<Coord>() to emptySet<Coord>()
        }
        val n = config.size
        val rnd = Random(config.seed)
        val hardLimit = (n * n * MOUNTAIN_MAX_RATIO).toInt().coerceAtLeast(1)
        val goal = (hardLimit * (0.85f + rnd.nextFloat() * 0.15f)).toInt().coerceAtLeast(4)
        val islandCount = 3 + rnd.nextInt(4) + (n - 12) // 12:3~6 座，14:5~8 座
        val mountains = LinkedHashSet<Coord>()

        var placed = 0
        var tries = 0
        while (placed < islandCount && mountains.size < goal && tries < 900) {
            tries++
            val shape = ISLAND_SHAPES[rnd.nextInt(ISLAND_SHAPES.size)]
            val h = shape.maxOf { it.r } + 1
            val w = shape.maxOf { it.c } + 1
            if (h > n || w > n) continue
            // r0 / c0 从 0 起算：允许岛屿贴边、落在四角。
            val r0 = rnd.nextInt(n - h + 1)
            val c0 = rnd.nextInt(n - w + 1)
            val cells = shape.map { Coord(r0 + it.r, c0 + it.c) }
            if (cells.any { !it.inBounds(n) }) continue
            if (cells.size > hardLimit - mountains.size) continue
            // 与已有山地保持 1 格间隔，避免所有岛连成一整块。
            val touches = cells.any { cell ->
                mountains.any { m -> maxOf(abs(m.r - cell.r), abs(m.c - cell.c)) <= 1 }
            }
            if (touches) continue
            mountains.addAll(cells)
            placed++
        }

        val shallows = LinkedHashSet<Coord>()
        for (m in mountains) {
            for (dr in -1..1) {
                for (dc in -1..1) {
                    val p = Coord(m.r + dr, m.c + dc)
                    if (p.inBounds(n) && p !in mountains) shallows.add(p)
                }
            }
        }
        return mountains to shallows
    }

    /* ------------------------- 布阵 ------------------------- */

    fun buildBoard(config: GameConfig, placements: List<Placement>): Board {
        val (mountains, shallows) = terrain(config)
        val ships = placements.map { p ->
            Ship(
                id = p.type.name,
                type = p.type,
                origin = p.origin,
                orientation = p.orientation,
                cells = cellsOf(p.origin, FleetCatalog.sizeOf(p.type), p.orientation)
            )
        }
        return Board(
            size = config.size,
            mountains = mountains,
            shallows = shallows,
            ships = ships,
            shots = emptyList(),
            attackedPoints = emptySet(),
            reconCells = emptySet(),
            sonarCells = emptySet(),
            radarCells = emptySet(),
            strikeCells = emptySet()
        )
    }

    fun validatePlacement(
        config: GameConfig,
        placements: List<Placement>,
        candidate: Placement,
        excludeType: ShipType? = null
    ): String? {
        val size = FleetCatalog.sizeOf(candidate.type)
        val cells = cellsOf(candidate.origin, size, candidate.orientation)
        if (cells.any { !it.inBounds(config.size) }) return "超出海域边界"
        val (mountains, shallows) = terrain(config)
        if (cells.any { it in mountains }) return "山地不可部署舰船"
        if (size >= 3 && cells.any { it in shallows }) return "近岸浅滩禁止部署 3~5 格大型舰船与潜艇"
        val skip = excludeType ?: candidate.type
        val occupied = placements
            .filter { it.type != skip }
            .flatMap { cellsOf(it.origin, FleetCatalog.sizeOf(it.type), it.orientation) }
        if (cells.any { it in occupied }) return "与已有舰船重叠"
        return null
    }

    fun validateFleet(config: GameConfig, fleet: FleetConfig, placements: List<Placement>): String? {
        if (!fleet.isValid()) return "舰队编成不符合 5+4+3+2+1 五舰制"
        if (placements.size != 5) return "需要完成 5 艘舰船布阵（当前 ${placements.size} 艘）"
        if (placements.map { it.type }.toSet() != fleet.all().toSet()) return "布阵与所选舰种不一致"
        for (p in placements) {
            val err = validatePlacement(config, placements, p, excludeType = p.type)
            if (err != null) return "${FleetCatalog.label(p.type)}：$err"
        }
        return null
    }

    /** 随机布阵风格：0 均衡散布 / 1 边角分散 / 2 中央集结 / 3 同向编队。 */
    fun layoutLabel(style: Int): String = when (style) {
        1 -> "边角分散"
        2 -> "中央集结"
        3 -> "同向编队"
        else -> "均衡散布"
    }

    /**
     * 真随机布阵：每次调用传入不同的 seed 都会给出不同阵型。
     * v10 之前 UI 固定传 cfg.seed + 31，导致「一键随机布阵」每次点的结果完全一样（伪随机）。
     * 现在按风格生成一批合法候选，再按风格打分挑选，四种风格给出肉眼可见的不同排列方式：
     *   0 均衡散布：随机挑一个合法候选，不偏向任何区域
     *   1 边角分散：优先贴边、且尽量远离已布置的舰船
     *   2 中央集结：优先靠近棋盘中央、彼此靠拢
     *   3 同向编队：所有舰船统一横 / 纵，其余随机
     * 候选池为空时退回全盘穷举，保证 5 艘一定都能放完。
     */
    fun defaultPlacements(
        config: GameConfig,
        fleet: FleetConfig,
        seed: Long,
        style: Int = 0
    ): List<Placement> {
        val rnd = Random(seed)
        val n = config.size
        val result = ArrayList<Placement>(5)
        val order = fleet.all().sortedByDescending { FleetCatalog.sizeOf(it) }
        val forcedO: Orientation? = when (style) {
            3 -> if (rnd.nextBoolean()) Orientation.H else Orientation.V
            else -> null
        }

        /** 按风格偏置某一维起点：1 贴边、2 靠中、其余全图均匀。 */
        fun biased(): Int = when (style) {
            1 -> {
                val band = maxOf(1, n / 3)
                if (rnd.nextBoolean()) rnd.nextInt(band) else n - 1 - rnd.nextInt(band)
            }
            2 -> {
                val half = maxOf(1, n / 2)
                (n / 2 - half / 2 + rnd.nextInt(half)).coerceIn(0, n - 1)
            }
            else -> rnd.nextInt(n)
        }

        for (type in order) {
            val size = FleetCatalog.sizeOf(type)
            val pool = ArrayList<Pair<Placement, Double>>()
            var tries = 0
            while (pool.size < 28 && tries < 900) {
                tries++
                val o = forcedO ?: if (rnd.nextBoolean()) Orientation.H else Orientation.V
                val p = Placement(type, Coord(biased(), biased()), o)
                if (validatePlacement(config, result, p) != null) continue
                val cells = cellsOf(p.origin, size, o)
                val other = result.flatMap {
                    cellsOf(it.origin, FleetCatalog.sizeOf(it.type), it.orientation)
                }
                var sep = n.toDouble()
                if (other.isNotEmpty()) {
                    var best = Int.MAX_VALUE
                    for (c in cells) {
                        for (q in other) {
                            val d = maxOf(abs(c.r - q.r), abs(c.c - q.c))
                            if (d < best) best = d
                        }
                    }
                    sep = best.toDouble()
                }
                val mid = (n - 1) / 2.0
                val ar = cells.map { it.r }.average()
                val ac = cells.map { it.c }.average()
                val edge = minOf(minOf(ar, ac), minOf(n - 1 - ar, n - 1 - ac))
                val score = when (style) {
                    1 -> sep * 3.0 - edge + rnd.nextDouble()
                    2 -> -abs(ar - mid) - abs(ac - mid) - sep * 0.6 + rnd.nextDouble()
                    3 -> -abs(ar - mid) * 0.15 - abs(ac - mid) * 0.15 + rnd.nextDouble() * 3.0
                    else -> rnd.nextDouble()
                }
                pool.add(p to score)
            }
            val best = pool.maxByOrNull { it.second }?.first
            if (best != null) {
                result.add(best)
                continue
            }
            // 兜底：全盘穷举，保证一定能放完 5 艘
            outer@ for (r in 0 until n) {
                for (c in 0 until n) {
                    for (o in Orientation.values()) {
                        val p = Placement(type, Coord(r, c), o)
                        if (validatePlacement(config, result, p) == null) {
                            result.add(p)
                            break@outer
                        }
                    }
                }
            }
        }
        return result
    }

    /* ------------------------- 防空区 ------------------------- */

    /**
     * 防空舰「本体点防空」：只保护自己的船体格。
     * 用于判定飞机投弹的【炸点】是否落在近防火力里，与 aaZone（判定【部署点】的区域防空网）
     * 严格分开 —— 否则整片 15 格海域都会变成不可轰炸区，也会和「AA 圈外视距打击」自相矛盾。
     */
    fun aaBody(ship: Ship, size: Int): Set<Coord> =
        ship.cells.filter { it.inBounds(size) }.toSet()

    /**
     * 区域防空圈只用于判定「敌方飞机的部署点」是否会被击落。
     * 绝不因为侦查范围 / 鱼雷航线 / 爆炸点扫过防空区就把飞机打下来。
     */
    fun aaZone(ship: Ship, size: Int): Set<Coord> {
        val out = HashSet<Coord>()
        when (ship.type) {
            ShipType.CARRIER -> out.addAll(ship.cells)
            ShipType.AA_CRUISER -> {
                for (c in ship.cells) {
                    for (dr in -1..1) {
                        for (dc in -1..1) out.add(Coord(c.r + dr, c.c + dc))
                    }
                }
            }
            else -> Unit
        }
        return out.filter { it.inBounds(size) }.toSet()
    }

    /* ------------------------- 传感器覆盖区 ------------------------- */

    /**
     * 主动水听声呐：中心 3×3（9 格）+ 四边中间各向外突出 1 格 = 共 13 格。
     * 山地阻挡声波：山地本身不计入，且该方向被山挡住后，山后那一格也不计入。
     * 只用于「是否存在潜艇」的计数，不写 attackedPoints、不锁定任何格子。
     */
    fun hydrophoneCells(center: Coord, size: Int, mountains: Set<Coord>): Set<Coord> {
        val out = LinkedHashSet<Coord>()
        if (!center.inBounds(size) || center in mountains) return out
        out.add(center)
        for (d in DIR8) {
            val p = Coord(center.r + d.r, center.c + d.c)
            if (p.inBounds(size) && p !in mountains) out.add(p)
        }
        for (d in DIR4) {
            val p1 = Coord(center.r + d.r, center.c + d.c)
            if (p1 !in out) continue // 前一格就是山，射线到此中断
            val p2 = Coord(center.r + d.r * 2, center.c + d.c * 2)
            if (p2.inBounds(size) && p2 !in mountains) out.add(p2)
        }
        return out
    }

    /**
     * 监视雷达射程按地图尺寸自适应，避免小图超模 / 大图无用：
     * 10×10 与 12×12 为 2 格射线，14×14 为 3 格射线。
     * 覆盖率大致 30% / 21% / 25%，且只返回「数量」这一极粗情报，面积越大信息量反而越低。
     */
    fun radarRange(size: Int): Int = when {
        size >= 14 -> 3
        else -> 2
    }

    /**
     * 以船体每格为源点，向八方向发射雷达波，逐格前进；
     * 遇山地 / 越界立即停止该射线，山后海域被遮蔽。所有射线的并集即雷达覆盖区。
     * 敌舰只要有一格落在覆盖区里就计入数量。
     */
    fun radarCells(hull: List<Coord>, size: Int, mountains: Set<Coord>): Set<Coord> {
        val out = LinkedHashSet<Coord>()
        val steps = radarRange(size)
        for (h in hull) {
            if (!h.inBounds(size) || h in mountains) continue
            out.add(h)
            for (d in DIR8) {
                for (k in 1..steps) {
                    val p = Coord(h.r + d.r * k, h.c + d.c * k)
                    if (!p.inBounds(size) || p in mountains) break
                    out.add(p)
                }
            }
        }
        return out
    }

    /* ------------------------- 技能参数表 ------------------------- */

    fun skillFor(type: ShipType): SkillKind? = when (type) {
        ShipType.CARRIER -> SkillKind.RECON
        ShipType.BATTLESHIP -> SkillKind.SALVO
        ShipType.AVIATION_CRUISER -> SkillKind.TORPEDO_BOMBER
        ShipType.FIRE_CRUISER -> SkillKind.INCENDIARY
        ShipType.DESTROYER -> SkillKind.EVASIVE_MANEUVER
        ShipType.ASW_SHIP -> SkillKind.SONAR
        ShipType.GUNBOAT -> SkillKind.AIR_STRIKE_CALL
        ShipType.RADAR_PICKET -> SkillKind.RADAR_SURVEILLANCE
        ShipType.SUBMARINE -> null
        ShipType.AA_CRUISER -> null
    }

    fun isTactical(kind: SkillKind): Boolean = when (kind) {
        SkillKind.RECON, SkillKind.SONAR, SkillKind.RADAR_SURVEILLANCE,
        SkillKind.EVASIVE_MANEUVER -> true
        else -> false
    }

    fun skillLabel(kind: SkillKind): String = when (kind) {
        SkillKind.RECON -> "舰载侦察机"
        SkillKind.SONAR -> "主动水听声呐"
        SkillKind.RADAR_SURVEILLANCE -> "监视雷达"
        SkillKind.EVASIVE_MANEUVER -> "紧急规避机动"
        SkillKind.SALVO -> "主炮齐射"
        SkillKind.TORPEDO_BOMBER -> "鱼雷机空袭"
        SkillKind.INCENDIARY -> "高能燃烧弹"
        SkillKind.AIR_STRIKE_CALL -> "呼叫陆航空袭"
    }

    fun skillCooldown(kind: SkillKind): Int = when (kind) {
        SkillKind.RECON, SkillKind.SONAR, SkillKind.RADAR_SURVEILLANCE,
        SkillKind.SALVO, SkillKind.TORPEDO_BOMBER -> 3
        else -> 0
    }

    fun skillLimit(kind: SkillKind): Int = when (kind) {
        SkillKind.RECON -> 5
        SkillKind.TORPEDO_BOMBER -> 2
        SkillKind.INCENDIARY -> 2
        SkillKind.EVASIVE_MANEUVER -> 1
        SkillKind.AIR_STRIKE_CALL -> 1
        else -> Int.MAX_VALUE
    }

    fun skillMinRound(kind: SkillKind): Int = when (kind) {
        SkillKind.RECON, SkillKind.SONAR, SkillKind.RADAR_SURVEILLANCE,
        SkillKind.SALVO, SkillKind.TORPEDO_BOMBER -> 2
        SkillKind.INCENDIARY -> 3
        SkillKind.AIR_STRIKE_CALL -> 4
        else -> 1
    }

    /** 返回技能需要玩家点选的坐标数：0 = 以船体为中心自动扫描，无需选点。 */
    fun skillTargetCount(kind: SkillKind): Int = when (kind) {
        SkillKind.SALVO, SkillKind.TORPEDO_BOMBER -> 2
        SkillKind.RADAR_SURVEILLANCE -> 0
        else -> 1
    }

    /** 返回鱼雷机单位航向；dir 必须与起点同行或同列，且不能是同一个点。 */
    fun torpedoBomberDir(start: Coord, dir: Coord): Pair<Int, Int>? {
        val dr = dir.r - start.r
        val dc = dir.c - start.c
        if ((dr == 0) == (dc == 0)) return null
        return (when { dr > 0 -> 1; dr < 0 -> -1; else -> 0 }) to
            (when { dc > 0 -> 1; dc < 0 -> -1; else -> 0 })
    }

    /** 起点 + 沿航向 TORPEDO_BOMBER_STEPS 格；越界即截断。 */
    fun torpedoBomberLine(start: Coord, dir: Coord, size: Int): List<Coord> {
        val (sr, sc) = torpedoBomberDir(start, dir) ?: return listOf(start)
        val out = ArrayList<Coord>(TORPEDO_BOMBER_STEPS + 1)
        for (i in 0..TORPEDO_BOMBER_STEPS) {
            val c = Coord(start.r + sr * i, start.c + sc * i)
            if (!c.inBounds(size)) break
            out.add(c)
        }
        return out
    }

    fun isAir(kind: com.archipelago.battleship.domain.model.AttackKind): Boolean = when (kind) {
        com.archipelago.battleship.domain.model.AttackKind.BOMBER,
        com.archipelago.battleship.domain.model.AttackKind.AIR_CROSS,
        com.archipelago.battleship.domain.model.AttackKind.LAND_AIR -> true
        else -> false
    }
}
