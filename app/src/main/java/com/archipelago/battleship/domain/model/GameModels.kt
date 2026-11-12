package com.archipelago.battleship.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs

/* ============================ 基础枚举 ============================ */

@Serializable
enum class Side { A, B }

fun Side.other(): Side = if (this == Side.A) Side.B else Side.A

@Serializable
data class Coord(val r: Int, val c: Int)

fun Coord.inBounds(size: Int): Boolean = r in 0 until size && c in 0 until size
fun Coord.plus(d: Coord): Coord = Coord(r + d.r, c + d.c)
fun Coord.manhattan(o: Coord): Int = abs(r - o.r) + abs(c - o.c)
fun Coord.chebyshev(o: Coord): Int = maxOf(abs(r - o.r), abs(c - o.c))

val DIR4: List<Coord> = listOf(Coord(-1, 0), Coord(1, 0), Coord(0, -1), Coord(0, 1))

fun crossCells(center: Coord, size: Int): List<Coord> {
    val out = ArrayList<Coord>(5)
    out.add(center)
    for (d in DIR4) {
        val n = center.plus(d)
        if (n.inBounds(size)) out.add(n)
    }
    return out
}

fun coordLabel(c: Coord): String {
    val col = if (c.c in 0..25) ('A'.code + c.c).toChar().toString() else (c.c + 1).toString()
    return col + (c.r + 1).toString()
}

@Serializable
enum class Orientation { H, V }

@Serializable
enum class RoomPhase { ROOM_LOBBY, IN_BATTLE, POST_BATTLE_LOBBY }

@Serializable
enum class TurnPhase { TACTICAL, ATTACK, GAME_OVER }

@Serializable
enum class ShipType {
    CARRIER,
    BATTLESHIP,
    AVIATION_CRUISER,
    FIRE_CRUISER,
    SUBMARINE,
    AA_CRUISER,
    DESTROYER,
    ASW_SHIP,
    GUNBOAT
}

@Serializable
enum class ShotResult { MISS, HIT, SUNK, INTERCEPTED, IMMUNE, BLOCKED }

@Serializable
enum class AttackKind { GUN, BOMBER, DEPTH_CHARGE, AIR_CROSS, TORPEDO, INCENDIARY, LAND_AIR }

@Serializable
enum class SkillKind {
    RECON,
    SONAR,
    EVASIVE_MANEUVER,
    SALVO,
    TORPEDO_BOMBER,
    INCENDIARY,
    AIR_STRIKE_CALL
}

@Serializable
enum class LogKind { INFO, HIT, MISS, SYSTEM, AA, SKILL, CHAT }

/* ============================ 舰船目录 ============================ */

data class ShipSpec(
    val type: ShipType,
    val label: String,
    val size: Int,
    val attack: String,
    val skill: String,
    val passive: String
)

object FleetCatalog {

    private val all: List<ShipSpec> = listOf(
        ShipSpec(
            ShipType.CARRIER, "航空母舰", 5,
            "战术轰炸机：指定 1 格轰炸（等效 1 炮）",
            "舰载侦察机（阶段一 / 第 2 回合起 / CD3 / 全局 5 次）：探测十字 5 格内水面船数量，扫描成功时该十字 5 格会在敌方棋盘留下「已侦察」放大镜标记；成为己方最后存活舰时解锁绝境副炮（无视 AA 的单格物理炮击）",
            "舰载侦察机 / 本体点防空：自身 5 格免疫敌方飞机侦察与轰炸并击落来犯敌机"
        ),
        ShipSpec(
            ShipType.BATTLESHIP, "战列舰", 5,
            "常规单格重炮炮击",
            "主炮齐射（阶段二 / CD3 / 第 2 回合起）：同时轰击 2 个不同坐标，两发间距 ≤ 4 格",
            "无"
        ),
        ShipSpec(
            ShipType.AVIATION_CRUISER, "航空巡洋舰", 4,
            "常规单格炮击",
            "鱼雷机空袭（阶段二 / 第 2 回合起 / CD3 / 全局 2 次）：先点投雷起点，再点同行/同列一格定航向；鱼雷沿直线最多推进 3 格，只在「碰到敌舰 / 撞山前一格 / 射程尽头」单点爆炸，伤害判定与潜航鱼雷相同（可命中水下潜艇），不显示沿途轨迹；战机仍受 AA 拦截",
            "无"
        ),
        ShipSpec(
            ShipType.FIRE_CRUISER, "火力巡洋舰", 4,
            "常规单格炮击",
            "高能燃烧弹（阶段二 / 第 3 回合起 / 全局 2 发）：十字 5 格纯炮击无视 AA；命中航母或防空巡洋舰任意一格使其 AA 瘫痪 1 个敌方回合",
            "无"
        ),
        ShipSpec(
            ShipType.SUBMARINE, "潜艇", 3,
            "潜航鱼雷：沿自身所在行 / 列直线最多 4 格推进，遇山地、已攻击过的格子或命中第一艘船即停",
            "无（鱼雷即常规攻击）",
            "潜望镜隐蔽：水下仅中心第 2 格可被常规炮击 / 侦察命中，首尾反馈 MISS；【潜望镜一击必沉】任何命中中心格的攻击直接判整艇沉没，不会再带伤发射鱼雷或等上浮复核才沉；每次发射后下 1 回合强制上浮，连续潜航 3 回合后第 4 回合强制上浮"
        ),
        ShipSpec(
            ShipType.AA_CRUISER, "防空巡洋舰", 3,
            "常规单格炮击",
            "区域防空网：以船体向外扩展 1 圈，自动击落目标点落在该区域的敌方飞机",
            "【防空拦截】战机被击落（向双方播报）"
        ),
        ShipSpec(
            ShipType.DESTROYER, "驱逐舰", 2,
            "常规单格炮击",
            "紧急规避机动（阶段一 / 全局 1 次）：平移 / 转向至 4 格航程内未被攻击过的合法水域，保留已受损伤",
            "机动后向敌方全局播报『敌方驱逐舰已实施紧急规避机动』"
        ),
        ShipSpec(
            ShipType.ASW_SHIP, "反潜舰", 2,
            "深水炸弹炮：对水面船正常 1 格伤害；命中水下潜艇任意一格直接报 HIT 并损毁该格（打满 3 格或命中中心格即击沉）",
            "主动水听声呐（阶段一 / 第 2 回合起 / CD3）：探测十字 5 格内是否存在潜艇，无视水下隐蔽",
            "无"
        ),
        ShipSpec(
            ShipType.GUNBOAT, "高速炮艇", 1,
            "常规单格炮击（本档必选）",
            "呼叫陆航空袭（阶段二 / 第 4 回合起 / 全局 1 次）：锁定坐标后延时 1 回合，在己方下一回合开始时对十字 5 格执行无视一切 AA 的饱和轰炸",
            "灵活规避：免疫所有航空攻击，可部署于近岸浅滩"
        )
    )

    private val byType: Map<ShipType, ShipSpec> = all.associateBy { it.type }

    fun spec(type: ShipType): ShipSpec = byType.getValue(type)

    fun sizeOf(type: ShipType): Int = byType.getValue(type).size

    fun label(type: ShipType): String = byType.getValue(type).label

    fun optionsFor(size: Int): List<ShipType> = when (size) {
        5 -> listOf(ShipType.CARRIER, ShipType.BATTLESHIP)
        4 -> listOf(ShipType.AVIATION_CRUISER, ShipType.FIRE_CRUISER)
        3 -> listOf(ShipType.SUBMARINE, ShipType.AA_CRUISER)
        2 -> listOf(ShipType.DESTROYER, ShipType.ASW_SHIP)
        else -> listOf(ShipType.GUNBOAT)
    }
}

/* ============================ 配置 / 布阵 ============================ */

@Serializable
data class FleetConfig(
    val five: ShipType = ShipType.CARRIER,
    val four: ShipType = ShipType.AVIATION_CRUISER,
    val three: ShipType = ShipType.SUBMARINE,
    val two: ShipType = ShipType.DESTROYER,
    val one: ShipType = ShipType.GUNBOAT
) {
    fun all(): List<ShipType> = listOf(five, four, three, two, one)

    fun at(size: Int): ShipType = when (size) {
        5 -> five
        4 -> four
        3 -> three
        2 -> two
        else -> one
    }

    fun withSize(size: Int, type: ShipType): FleetConfig = when (size) {
        5 -> copy(five = type)
        4 -> copy(four = type)
        3 -> copy(three = type)
        2 -> copy(two = type)
        else -> copy(one = type)
    }

    fun isValid(): Boolean =
        FleetCatalog.sizeOf(five) == 5 &&
            FleetCatalog.sizeOf(four) == 4 &&
            FleetCatalog.sizeOf(three) == 3 &&
            FleetCatalog.sizeOf(two) == 2 &&
            FleetCatalog.sizeOf(one) == 1 &&
            all().distinct().size == 5
}

@Serializable
data class GameConfig(
    val size: Int = 12,
    val mountains: Boolean = true,
    val seed: Long = 1L
)

@Serializable
data class Placement(val type: ShipType, val origin: Coord, val orientation: Orientation)

/* ============================ 棋盘 / 战况 ============================ */

@Serializable
data class Ship(
    val id: String,
    val type: ShipType,
    val origin: Coord,
    val orientation: Orientation,
    val cells: List<Coord>,
    val hits: Set<Coord> = emptySet(),
    val surfaced: Boolean = false,
    val aaDisabledUntil: Int = 0
) {
    val sunk: Boolean get() = cells.isNotEmpty() && hits.containsAll(cells)
}

@Serializable
data class ShotRecord(
    val at: Coord,
    val result: ShotResult,
    val by: Side,
    val round: Int,
    val shipType: ShipType? = null,
    // true = 单点攻击（受坐标去重约束）；false = 十字范围伤害 / AA 拦截中心
    val single: Boolean = true,
    // 打出这一炮的舰种，用于【上浮暴露复核】时把命中归因到原攻击者（MVP 统计）
    val attacker: ShipType? = null
)

@Serializable
data class Board(
    val size: Int = 10,
    val mountains: Set<Coord> = emptySet(),
    val shallows: Set<Coord> = emptySet(),
    val ships: List<Ship> = emptyList(),
    val shots: List<ShotRecord> = emptyList(),
    val attackedPoints: Set<Coord> = emptySet(),
    // 舰载侦察机成功扫描过的格子（十字 5 格）；只用于棋盘示意，不泄露舰船坐标。
    // 带默认值，联机 JSON 新旧版本互相兼容（Wire.json 已开 ignoreUnknownKeys）。
    val reconCells: Set<Coord> = emptySet()
)

@Serializable
data class PendingStrike(val center: Coord, val placedRound: Int)

@Serializable
data class ChatMessage(
    val id: Long,
    val side: Side,
    val name: String,
    val text: String,
    val emoji: Boolean = false
)

@Serializable
data class LogEntry(val id: Long, val round: Int, val kind: LogKind, val text: String)

@Serializable
data class SideStats(
    val alive: List<ShipType>,
    val sunk: List<ShipType>,
    val damage: Int,
    val mvp: String
)

@Serializable
data class BattleResult(
    val winner: Side,
    val rounds: Int,
    val host: SideStats,
    val guest: SideStats
)

@Serializable
data class PlayerState(
    val side: Side,
    val name: String,
    val ready: Boolean = false,
    val fleet: FleetConfig = FleetConfig(),
    val placements: List<Placement> = emptyList(),
    val board: Board = Board(),
    val tacticalUsed: Boolean = false,
    val actedShipIds: Set<String> = emptySet(),
    val cooldowns: Map<String, Int> = emptyMap(),
    val useCounts: Map<String, Int> = emptyMap(),
    val damageByShip: Map<String, Int> = emptyMap(),
    val pendingStrike: PendingStrike? = null,
    val subSurfaceNextTurn: Boolean = false,
    val submergedStreak: Int = 0
)

@Serializable
data class GameState(
    val phase: RoomPhase = RoomPhase.ROOM_LOBBY,
    val config: GameConfig = GameConfig(),
    val round: Int = 1,
    val turn: Side = Side.A,
    val turnPhase: TurnPhase = TurnPhase.TACTICAL,
    val players: List<PlayerState> = listOf(
        PlayerState(Side.A, "房主"),
        PlayerState(Side.B, "客机")
    ),
    val chat: List<ChatMessage> = emptyList(),
    val log: List<LogEntry> = emptyList(),
    val result: BattleResult? = null
)

/* ============================ 行动（UDF Intent） ============================ */

@Serializable
sealed class GameAction {

    @Serializable
    @SerialName("set_name")
    data class SetName(val name: String) : GameAction()

    @Serializable
    @SerialName("set_config")
    data class SetConfig(val size: Int, val mountains: Boolean) : GameAction()

    @Serializable
    @SerialName("set_fleet")
    data class SetFleet(val fleet: FleetConfig, val placements: List<Placement>) : GameAction()

    @Serializable
    @SerialName("set_ready")
    data class SetReady(val ready: Boolean) : GameAction()

    @Serializable
    @SerialName("start_battle")
    object StartBattle : GameAction()

    @Serializable
    @SerialName("return_to_lobby")
    object ReturnToLobby : GameAction()

    @Serializable
    @SerialName("fire")
    data class Fire(val shipId: String, val target: Coord) : GameAction()

    @Serializable
    @SerialName("skill")
    data class Skill(
        val shipId: String,
        val kind: SkillKind,
        val targets: List<Coord> = emptyList(),
        val orientation: Orientation? = null
    ) : GameAction()

    @Serializable
    @SerialName("skip_tactical")
    object SkipTactical : GameAction()

    @Serializable
    @SerialName("end_turn")
    object EndTurn : GameAction()

    @Serializable
    @SerialName("undo")
    object Undo : GameAction()

    @Serializable
    @SerialName("redo")
    object Redo : GameAction()

    @Serializable
    @SerialName("chat")
    data class Chat(val text: String, val emoji: Boolean = false) : GameAction()
}

@Serializable
data class SkillAvailability(
    val shipId: String,
    val kind: SkillKind,
    val label: String,
    val tactical: Boolean,
    val enabled: Boolean,
    val reason: String
)

/* ============================ 视图（网络下发 / UI 唯一数据源） ============================ */

@Serializable
data class GameView(
    val viewer: Side,
    val phase: RoomPhase,
    val config: GameConfig,
    val round: Int,
    val turn: Side,
    val turnPhase: TurnPhase,
    val hostName: String,
    val guestName: String,
    val hostReady: Boolean,
    val guestReady: Boolean,
    val hostFleet: FleetConfig,
    val guestFleet: FleetConfig,
    val myBoard: Board,
    val enemyBoard: Board,
    val enemySunkTypes: List<ShipType>,
    val myAlive: List<ShipType>,
    val enemyAlive: List<ShipType>,
    val legalTargets: List<Coord>,
    val skills: List<SkillAvailability>,
    val myActed: List<String> = emptyList(),
    val log: List<LogEntry>,
    val chat: List<ChatMessage>,
    val result: BattleResult?,
    val revision: Long,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val attackBudget: Int = 3,
    val attackUsed: Int = 0
)
