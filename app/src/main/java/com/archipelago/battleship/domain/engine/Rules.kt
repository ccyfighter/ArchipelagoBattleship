package com.archipelago.battleship.domain.engine

import com.archipelago.battleship.domain.model.Board
import com.archipelago.battleship.domain.model.Coord
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
 * 纯 Kotlin 规则层：地形生成、布阵合法性、技能参数表。
 * 不依赖 Android / Compose / 网络，可单测。
 */
object Rules {

    const val TURN_SECONDS = 180

    /** 每回合阶段二的攻击名额上限（同时不得超过存活舰船数）。 */
    const val BASE_ATTACKS_PER_TURN = 3

    /** 山地总面积占全图的最大比例。 */
    const val MOUNTAIN_MAX_RATIO = 0.20f

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
            attackedPoints = emptySet()
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

    fun defaultPlacements(config: GameConfig, fleet: FleetConfig, seed: Long): List<Placement> {
        val rnd = Random(seed)
        val result = ArrayList<Placement>(5)
        val order = fleet.all().sortedByDescending { FleetCatalog.sizeOf(it) }
        for (type in order) {
            var done = false
            for (attempt in 0 until 500) {
                val o = if (rnd.nextBoolean()) Orientation.H else Orientation.V
                val p = Placement(type, Coord(rnd.nextInt(config.size), rnd.nextInt(config.size)), o)
                if (validatePlacement(config, result, p) == null) {
                    result.add(p)
                    done = true
                    break
                }
            }
            if (!done) {
                outer@ for (r in 0 until config.size) {
                    for (c in 0 until config.size) {
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
        }
        return result
    }

    /* ------------------------- 防空区 ------------------------- */

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

    /* ------------------------- 技能参数表 ------------------------- */

    fun skillFor(type: ShipType): SkillKind? = when (type) {
        ShipType.CARRIER -> SkillKind.RECON
        ShipType.BATTLESHIP -> SkillKind.SALVO
        ShipType.AVIATION_CRUISER -> SkillKind.TORPEDO_BOMBER
        ShipType.FIRE_CRUISER -> SkillKind.INCENDIARY
        ShipType.DESTROYER -> SkillKind.EVASIVE_MANEUVER
        ShipType.ASW_SHIP -> SkillKind.SONAR
        ShipType.GUNBOAT -> SkillKind.AIR_STRIKE_CALL
        ShipType.SUBMARINE -> null
        ShipType.AA_CRUISER -> null
    }

    fun isTactical(kind: SkillKind): Boolean = when (kind) {
        SkillKind.RECON, SkillKind.SONAR, SkillKind.EVASIVE_MANEUVER -> true
        else -> false
    }

    fun skillLabel(kind: SkillKind): String = when (kind) {
        SkillKind.RECON -> "舰载侦察机"
        SkillKind.SONAR -> "主动水听声呐"
        SkillKind.EVASIVE_MANEUVER -> "紧急规避机动"
        SkillKind.SALVO -> "主炮齐射"
        SkillKind.TORPEDO_BOMBER -> "鱼雷机空袭"
        SkillKind.INCENDIARY -> "高能燃烧弹"
        SkillKind.AIR_STRIKE_CALL -> "呼叫陆航空袭"
    }

    fun skillCooldown(kind: SkillKind): Int = when (kind) {
        SkillKind.RECON, SkillKind.SONAR, SkillKind.SALVO, SkillKind.TORPEDO_BOMBER -> 3
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
        SkillKind.RECON, SkillKind.SONAR, SkillKind.SALVO, SkillKind.TORPEDO_BOMBER -> 2
        SkillKind.INCENDIARY -> 3
        SkillKind.AIR_STRIKE_CALL -> 4
        else -> 1
    }

    /** 鱼雷机需要 2 个点：第 1 个是突防起点，第 2 个与起点同行 / 同列，只用于确定航向。 */
    fun skillTargetCount(kind: SkillKind): Int = when (kind) {
        SkillKind.SALVO, SkillKind.TORPEDO_BOMBER -> 2
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

    /** 起点 + 沿航向 2 格，最多 3 格直线；越界即截断。 */
    fun torpedoBomberLine(start: Coord, dir: Coord, size: Int): List<Coord> {
        val (sr, sc) = torpedoBomberDir(start, dir) ?: return listOf(start)
        val out = ArrayList<Coord>(3)
        for (i in 0..2) {
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
