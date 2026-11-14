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

/** 八方向（含四条对角线），雷达 / 水听射线复用。 */
val DIR8: List<Coord> = listOf(
    Coord(-1, 0), Coord(1, 0), Coord(0, -1), Coord(0, 1),
    Coord(-1, -1), Coord(-1, 1), Coord(1, -1), Coord(1, 1)
)

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
    GUNBOAT,
    RADAR_PICKET
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
    AIR_STRIKE_CALL,
    RADAR_SURVEILLANCE
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
            "舰载侦察机（阶段一 / 第 2 回合起 / CD3 / 全局 5 次）：以部署点为中心的十字 5 格计数舰船信号（水下潜艇只按潜望镜中心格计）；只有「部署点」落进敌方防空圈才会被击落，扫描范围大小与击落判定无关；扫描成功时该十字 5 格会在敌方棋盘留下「已侦察」放大镜标记；成为己方最后存活舰时解锁绝境副炮（无视 AA 的单格物理炮击）",
            "本体点防空：自身 5 格免疫敌方飞机侦察与投弹；区域防空网只按「部署点」击落敌机，本机仍可被从圈外投弹命中的炮弹 / 鱼雷打击"
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
            "鱼雷机空袭（阶段二 / 第 2 回合起 / CD3 / 全局 2 次）：先点投雷起点，再点同行/同列一格定航向；鱼雷沿直线从起点向前推进 3 格（共 4 格航线），只在「碰到敌舰 / 撞山前一格 / 射程尽头」单点爆炸，伤害判定与潜航鱼雷相同（可命中水下潜艇），沿途不留轨迹、不锁格；只要投雷起点不在敌方防空圈内即可实施 AA 圈外视距打击（起点落圈内战机才会被击落）",
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
            "潜航鱼雷：点同行 / 同列一格只定航向，沿直线最多推进 4 格；只在「碰到舰船 / 沉船残骸 / 撞山前一格 / 本次射程尽头」单点爆炸，路径上被炮击过的格子不再挡路；同一方向连射按 4-3-2-1 递减射程，余程归零后该方向只能空射（占 1 次攻击、无伤害、仍强制上浮）；沿途不留轨迹、不锁中间格、不播报发射坐标",
            "无（鱼雷即常规攻击）",
            "潜望镜隐蔽：潜航时仅中心第 2 格可被常规炮击 / 侦察命中，首尾反馈 MISS；上浮时三格都可被打击。【潜望镜一击必沉】不论潜航 / 上浮，任何命中中心格的攻击都直接判整艇沉没；每次发射后下 1 回合强制上浮，连续潜航 3 回合后第 4 回合强制上浮。【最后抵抗】只剩本艇时可在开火卡片切换「上浮炮击」：改为普通单格炮击（无视 AA、不减射程）、占 1 次攻击名额，立即上浮并暴露首尾格直到自己的下回合"
        ),
        ShipSpec(
            ShipType.AA_CRUISER, "防空巡洋舰", 3,
            "常规单格炮击",
            "区域防空网：以船体每格向外扩展 1 圈（3 格船共 15 格）形成防空区，只自动击落「部署点」落在该区域的敌方侦察机 / 鱼雷机；飞机从圈外投弹、鱼雷航线穿过该区、爆炸点落在该区都不算被击落",
            "【防空拦截】战机被击落（战报中性化，不公开舰种与攻击方式）；被拦截坐标不封锁，仍可被其他舰船攻击"
        ),
        ShipSpec(
            ShipType.RADAR_PICKET, "雷达哨舰", 3,
            "常规单格炮击",
            "监视雷达（阶段一 / 第 2 回合起 / CD3 / 无需选点）：以本舰 3 格船体为源点向八方向发射雷达波，射线逐格前进、遇山即止；10×10 / 12×12 射程 2 格，14×14 射程 3 格。只返回覆盖区内「水面舰艇 + 已上浮潜艇」的数量，敌舰只要有一格进入雷达区就计入，不报坐标、不报舰种；山地会遮蔽其后方海域",
            "无（纯情报舰，不提供防空、不免疫、不增加攻击名额）"
        ),
        ShipSpec(
            ShipType.DESTROYER, "驱逐舰", 2,
            "常规单格炮击",
            "紧急规避机动（阶段一 / 全局 1 次）：平移 / 转向至 4 格航程内未被攻击过的合法水域，保留已受损伤",
            "机动后仅作中性播报，不公开舰种"
        ),
        ShipSpec(
            ShipType.ASW_SHIP, "反潜舰", 2,
            "深水炸弹炮：对水面船正常 1 格伤害；命中水下潜艇首 / 尾格直接报 HIT 并损毁该格（命中中心格直接击沉，打满 3 格同样击沉）",
            "主动水听声呐（阶段一 / 第 2 回合起 / CD3）：以投放点为中心扫描 3×3 + 四边中间各外突 1 格（共 13 格），只探测潜艇且无视水下隐蔽，山地会阻挡声波、山后格子不计入；只返回发现数量",
            "无"
        ),
        ShipSpec(
            ShipType.GUNBOAT, "高速炮艇", 1,
            "常规单格炮击（本档必选）",
            "呼叫陆航空袭（阶段二 / 第 4 回合起 / 全局 1 次）：锁定坐标后延时 1 回合，在己方下一回合开始时对十字 5 格执行无视一切 AA 的饱和轰炸",
            "机动灵活：可部署于近岸浅滩；v13 起航空攻击可正常命中，不再免疫"
        )
    )

    private val byType: Map<ShipType, ShipSpec> = all.associateBy { it.type }

    fun spec(type: ShipType): ShipSpec = byType.getValue(type)

    fun sizeOf(type: ShipType): Int = byType.getValue(type).size

    fun label(type: ShipType): String = byType.getValue(type).label

    fun optionsFor(size: Int): List<ShipType> = when (size) {
        5 -> listOf(ShipType.CARRIER, ShipType.BATTLESHIP)
        4 -> listOf(ShipType.AVIATION_CRUISER, ShipType.FIRE_CRUISER)
        3 -> listOf(ShipType.SUBMARINE, ShipType.AA_CRUISER, ShipType.RADAR_PICKET)
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
    val aaDisabledUntil: Int = 0,
    /**
     * 潜艇鱼雷「同向递减射程」台账：key = 发射方向（"dr,dc"），value = 该方向剩余推进格数。
     * 初始视为 TORPEDO_MAX_STEPS(4)；每成功发射一次，该方向剩余射程 = 本次最远推进距离 - 1，
     * 因此同方向连射依次落在 4 / 3 / 2 / 1 格处，归零后该方向不能再发射。
     * 只记在自己棋盘的 Ship 上；viewFor() 对敌方棋盘只下发已沉舰，不会泄露给对手。
     */
    val torpedoLanes: Map<String, Int> = emptyMap()
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
    // 四种情报覆盖层：只用于棋盘示意，全部不写入 attackedPoints、不影响任何攻击判定。
    // 带默认值，联机 JSON 新旧版本互相兼容（Wire.json 已开 ignoreUnknownKeys）。
    // 侦察机扫过的十字 5 格（琥珀放大镜）
    val reconCells: Set<Coord> = emptySet(),
    // 水听声呐扫过的 13 格（蓝色同心波纹）
    val sonarCells: Set<Coord> = emptySet(),
    // 监视雷达覆盖区（青绿虚线框 + 角点）
    val radarCells: Set<Coord> = emptySet(),
    // 已标记、下回合生效的陆航空袭十字 5 格（橙红斜叉）
    val strikeCells: Set<Coord> = emptySet()
)

@Serializable
data class PendingStrike(val center: Coord, val placedRound: Int)

/**
 * 战斗结束后公开的双方完整棋盘，用于结算页的「部署图 / 打击图」复盘。
 * 只在 POST_BATTLE_LOBBY 由 viewFor() 下发；战斗中恒为 null，绝不提前泄露隐藏信息。
 */
@Serializable
data class BattleReplay(val hostBoard: Board, val guestBoard: Board)

@Serializable
data class ChatMessage(
    val id: Long,
    val side: Side,
    val name: String,
    val text: String,
    val emoji: Boolean = false
)

/**
 * 战报条目。
 * owner == null 表示双方可见的公开战报（只含坐标 + 结果，不含攻击方式与舰种）；
 * owner == 某一方表示仅该方可见的操作反馈（校验失败、侦察结果、技能详情等）。
 * viewFor() 按收件方过滤，因此联机下发的报文里也不会夹带对方的情报。
 */
@Serializable
data class LogEntry(
    val id: Long,
    val round: Int,
    val kind: LogKind,
    val text: String,
    val owner: Side? = null
)

@Serializable
data class SideStats(
    val alive: List<ShipType>,
    val sunk: List<ShipType>,
    val damage: Int,
    val mvp: String
)

@Serializable
enum class BattleEndReason { ANNIHILATION, RESIGN }

@Serializable
data class BattleResult(
    val winner: Side,
    val rounds: Int,
    val host: SideStats,
    val guest: SideStats,
    val reason: BattleEndReason = BattleEndReason.ANNIHILATION,
    val resigned: Side? = null
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
    @SerialName("resign")
    object Resign : GameAction()

    @Serializable
    @SerialName("fire")
    data class Fire(
        val shipId: String,
        val target: Coord,
        /** v13 潜艇【最后抵抗】：仅剩本艇时可选择上浮，改用普通单格炮击。 */
        val surfaceGun: Boolean = false
    ) : GameAction()

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
    /** 敌方存活舰种属于情报，一律不下发；UI 只显示 enemyAliveCount 这个数量。 */
    val enemyAlive: List<ShipType>,
    val enemyAliveCount: Int = 0,
    val legalTargets: List<Coord>,
    /** 情报 / 部署类技能候选格：只排除山地，不排除「已攻击过」的坐标。 */
    val intelTargets: List<Coord> = emptyList(),
    val skills: List<SkillAvailability>,
    val myActed: List<String> = emptyList(),
    val log: List<LogEntry>,
    val chat: List<ChatMessage>,
    val result: BattleResult?,
    val revision: Long,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val attackBudget: Int = 3,
    val attackUsed: Int = 0,
    /** 仅战斗结束后下发；战斗中为 null。 */
    val replay: BattleReplay? = null
)
