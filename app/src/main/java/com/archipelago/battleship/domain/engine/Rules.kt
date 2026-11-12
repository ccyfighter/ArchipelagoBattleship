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

    private fun generateTerrain(config: GameConfig): Pair<Set<Coord>, Set<Coord>> {
        if (!config.mountains || config.size != 12) {
            return emptySet<Coord>() to emptySet<Coord>()
        }
        val rnd = Random(config.seed)
        val count = if (rnd.nextBoolean()) 3 else 4
        val mountains = LinkedHashSet<Coord>()
        val mirror: (Coord) -> Coord = { Coord(config.size - 1 - it.r, config.size - 1 - it.c) }

        fun tooClose(cells: List<Coord>): Boolean = cells.any { n ->
            mountains.any { m -> abs(m.r - n.r) <= 1 && abs(m.c - n.c) <= 1 }
        }

        val pairs = count / 2
        var placedPairs = 0
        var tries = 0
        while (placedPairs < pairs && tries < 500) {
            tries++
            val square = rnd.nextBoolean()
            val r = 1 + rnd.nextInt(4)
            val c = 1 + rnd.nextInt(config.size - 3)
            val cells = ArrayList<Coord>()
            cells.add(Coord(r, c))
            if (square) {
                cells.add(Coord(r, c + 1))
                cells.add(Coord(r + 1, c))
                cells.add(Coord(r + 1, c + 1))
            } else {
                cells.add(Coord(r, c + 1))
            }
            val mirrored = cells.map(mirror)
            val union = cells + mirrored
            if (union.any { !it.inBounds(config.size) }) continue
            if (union.distinct().size != union.size) continue
            if (tooClose(cells) || tooClose(mirrored)) continue
            mountains.addAll(union)
            placedPairs++
        }

        if (count % 2 == 1) {
            val center = listOf(Coord(5, 5), Coord(5, 6), Coord(6, 5), Coord(6, 6))
            if (!tooClose(center)) mountains.addAll(center)
        }

        val shallows = LinkedHashSet<Coord>()
        for (m in mountains) {
            for (dr in -1..1) {
                for (dc in -1..1) {
                    val n = Coord(m.r + dr, m.c + dc)
                    if (n.inBounds(config.size) && n !in mountains) shallows.add(n)
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
        SkillKind.SALVO -> 2
        SkillKind.TORPEDO_BOMBER -> 2
        SkillKind.INCENDIARY -> 3
        SkillKind.AIR_STRIKE_CALL -> 4
        else -> 1
    }

    fun skillTargetCount(kind: SkillKind): Int = if (kind == SkillKind.SALVO) 2 else 1

    fun isAir(kind: com.archipelago.battleship.domain.model.AttackKind): Boolean = when (kind) {
        com.archipelago.battleship.domain.model.AttackKind.BOMBER,
        com.archipelago.battleship.domain.model.AttackKind.AIR_CROSS,
        com.archipelago.battleship.domain.model.AttackKind.LAND_AIR -> true
        else -> false
    }
}
