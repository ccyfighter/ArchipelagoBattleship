package com.archipelago.battleship.domain.engine

import com.archipelago.battleship.domain.model.*
import kotlin.random.Random

/**
 * 房间状态机 + 战斗结算的唯一权威实现。
 * 纯 Kotlin：不引用 Android / Compose / 网络。
 *
 * 联机时由房主持有本引擎（权威状态），客机只下发 GameAction、接收 GameView；
 * 单机时由本地持有本引擎，B 侧交给 AiEngine 驱动。
 */
class GameEngine(config: GameConfig = GameConfig()) {

    var state: GameState = GameState(config = config)
        private set

    private var seq = 1L
    private var revision = 1L

    /** 本回合内的操作快照：只能撤回/重做自己当前回合的行动，换手时清空。 */
    private val undoStack = ArrayList<Pair<Side, GameState>>()
    private val redoStack = ArrayList<Pair<Side, GameState>>()

    init {
        val board = Rules.buildBoard(config, emptyList())
        state = state.copy(players = state.players.map { it.copy(board = board) })
        log(LogKind.SYSTEM, "战前大厅已就绪：房主可切换 10×10 / 12×12 / 14×14 与随机群岛，双方编成 5+4+3+2+1 舰队后点准备")
    }

    /* ==================== 通用工具 ==================== */

    private fun log(kind: LogKind, text: String) {
        val e = LogEntry(seq++, state.round, kind, text)
        state = state.copy(log = (state.log + e).takeLast(200))
        revision++
    }

    private fun addChat(side: Side, name: String, text: String, emoji: Boolean) {
        val m = ChatMessage(seq++, side, name, text, emoji)
        state = state.copy(chat = (state.chat + m).takeLast(120))
        revision++
    }

    private fun updatePlayer(side: Side, fn: (PlayerState) -> PlayerState) {
        val i = side.ordinal
        state = state.copy(players = state.players.mapIndexed { idx, p -> if (idx == i) fn(p) else p })
        revision++
    }

    private fun boardOf(side: Side): Board = state.players[side.ordinal].board
    private fun setBoard(side: Side, b: Board) = updatePlayer(side) { it.copy(board = b) }
    private fun nameOf(side: Side): String = state.players[side.ordinal].name
    private fun isMyTurn(side: Side): Boolean =
        state.phase == RoomPhase.IN_BATTLE && state.turn == side && state.turnPhase != TurnPhase.GAME_OVER

    private fun Board.record(
        at: Coord,
        r: ShotResult,
        by: Side,
        t: ShipType?,
        mark: Boolean,
        attacker: ShipType? = null
    ): Board = copy(
        shots = (shots + ShotRecord(at, r, by, state.round, t, mark, attacker)).takeLast(500),
        attackedPoints = if (mark) attackedPoints + at else attackedPoints
    )

    private fun markActed(side: Side, shipId: String) =
        updatePlayer(side) { it.copy(actedShipIds = it.actedShipIds + shipId) }

    /**
     * 所有行动的唯一入口守卫：已沉没舰船一律不得开火 / 释放技能。
     * 原先是静默 return，UI 没有任何反馈，容易被误认为「沉船还能起舰载机」；
     * 这里改为明确播报，并借 revision 变化触发一次界面刷新。
     */
    private fun actionableShip(side: Side, shipId: String): Ship? {
        val ship = state.players[side.ordinal].board.ships.firstOrNull { it.id == shipId }
        if (ship == null) {
            log(LogKind.INFO, "找不到该舰船")
            return null
        }
        if (ship.sunk) {
            log(LogKind.INFO, "【已沉没】${FleetCatalog.label(ship.type)} 已沉没，无法再开火或释放技能")
            return null
        }
        return ship
    }

    private fun addDamage(side: Side, type: ShipType) = updatePlayer(side) { p ->
        val k = type.name
        p.copy(damageByShip = p.damageByShip + (k to ((p.damageByShip[k] ?: 0) + 1)))
    }

    /* ==================== 对外：UDF dispatch ==================== */

    fun dispatch(side: Side, action: GameAction): Boolean {
        val before = revision
        val snapshot = state
        val signature = battleSignature()
        when (action) {
            is GameAction.SetName -> setName(side, action.name)
            is GameAction.SetConfig -> setConfig(side, action)
            is GameAction.SetFleet -> setFleet(side, action)
            is GameAction.SetReady -> setReady(side, action.ready)
            GameAction.StartBattle -> startBattle(side)
            GameAction.ReturnToLobby -> returnToLobby()
            is GameAction.Fire -> fire(side, action)
            is GameAction.Skill -> useSkill(side, action)
            GameAction.SkipTactical -> skipTactical(side)
            GameAction.EndTurn -> endTurn(side)
            GameAction.Undo -> undo(side)
            GameAction.Redo -> redo(side)
            is GameAction.Chat -> sendChat(side, action)
        }
        if (isBattleStep(action) && battleSignature() != signature) {
            undoStack.add(side to snapshot)
            if (undoStack.size > 40) undoStack.removeAt(0)
            redoStack.clear()
        }
        return revision != before
    }

    /** 只比较真正影响战局的部分，避免"非法操作只写了一条提示日志"也被压进撤销栈。 */
    private fun battleSignature(): List<Any?> = listOf(
        state.phase, state.round, state.turn, state.turnPhase, state.result,
        state.players.map { it.board },
        state.players.map { it.actedShipIds },
        state.players.map { it.tacticalUsed },
        state.players.map { it.cooldowns },
        state.players.map { it.useCounts }
    )

    private fun isBattleStep(a: GameAction): Boolean =
        a is GameAction.Fire || a is GameAction.Skill || a is GameAction.SkipTactical

    private fun undo(side: Side) {
        if (state.turn != side) return
        if (state.phase != RoomPhase.IN_BATTLE && state.phase != RoomPhase.POST_BATTLE_LOBBY) return
        val idx = undoStack.indexOfLast { it.first == side }
        if (idx < 0) return
        val snap = undoStack.removeAt(idx).second
        redoStack.add(side to state)
        state = snap
        revision++
        log(LogKind.SYSTEM, "已撤回上一步操作：可重新选择舰船与目标")
    }

    private fun redo(side: Side) {
        if (state.turn != side) return
        if (state.phase != RoomPhase.IN_BATTLE && state.phase != RoomPhase.POST_BATTLE_LOBBY) return
        val idx = redoStack.indexOfLast { it.first == side }
        if (idx < 0) return
        val snap = redoStack.removeAt(idx).second
        undoStack.add(side to state)
        state = snap
        revision++
        log(LogKind.SYSTEM, "已重做上一步操作")
    }

    private fun setName(side: Side, name: String) {
        val n = name.trim().take(16).ifEmpty { if (side == Side.A) "房主" else "客机" }
        updatePlayer(side) { it.copy(name = n) }
    }

    /* ==================== 大厅阶段 ==================== */

    private fun setConfig(side: Side, a: GameAction.SetConfig) {
        if (side != Side.A) {
            log(LogKind.INFO, "只有房主可以修改地图规则")
            return
        }
        if (state.phase == RoomPhase.IN_BATTLE) return
        val size = when (a.size) { 14 -> 14; 12 -> 12; else -> 10 }
        val mountains = size >= 12 && a.mountains
        if (state.config.size == size && state.config.mountains == mountains) return
        val cfg = GameConfig(size, mountains, Random.nextLong(1L, 1_000_000_000L))
        val fresh = Rules.buildBoard(cfg, emptyList())
        state = state.copy(
            config = cfg,
            phase = RoomPhase.ROOM_LOBBY,
            round = 1,
            turn = Side.A,
            turnPhase = TurnPhase.TACTICAL,
            result = null,
            players = state.players.map {
                it.copy(
                    ready = false, placements = emptyList(), board = fresh,
                    tacticalUsed = false, actedShipIds = emptySet(),
                    cooldowns = emptyMap(), useCounts = emptyMap(), damageByShip = emptyMap(),
                    pendingStrike = null, subSurfaceNextTurn = false, submergedStreak = 0
                )
            }
        )
        revision++
        log(
            LogKind.SYSTEM,
            "房主调整地图：${size}×${size} · ${if (mountains) "随机群岛（总面积≤20%）" else "纯海无山"}；地形已重算，双方需重新布阵"
        )
    }

    private fun setFleet(side: Side, a: GameAction.SetFleet) {
        if (state.phase == RoomPhase.IN_BATTLE) return
        if (!a.fleet.isValid()) {
            log(LogKind.INFO, "舰队编成非法，必须 5+4+3+2+1 各一艘")
            return
        }
        updatePlayer(side) {
            it.copy(
                fleet = a.fleet,
                placements = a.placements,
                ready = false,
                board = Rules.buildBoard(state.config, a.placements)
            )
        }
        log(
            LogKind.INFO,
            "${nameOf(side)} 完成舰队编成：" + a.fleet.all().joinToString(" / ") { FleetCatalog.label(it) }
        )
    }

    private fun setReady(side: Side, ready: Boolean) {
        if (state.phase == RoomPhase.IN_BATTLE) return
        val p = state.players[side.ordinal]
        if (ready) {
            val err = Rules.validateFleet(state.config, p.fleet, p.placements)
            if (err != null) {
                log(LogKind.INFO, "无法准备：$err")
                return
            }
        }
        updatePlayer(side) { it.copy(ready = ready) }
        log(LogKind.SYSTEM, "${nameOf(side)} ${if (ready) "已准备" else "取消准备"}")
    }

    private fun startBattle(side: Side) {
        if (side != Side.A) {
            log(LogKind.INFO, "只有房主可以开始战斗")
            return
        }
        if (state.phase == RoomPhase.IN_BATTLE) return
        if (!state.players[0].ready || !state.players[1].ready) {
            log(LogKind.INFO, "双方都点击『准备』后才能开始战斗")
            return
        }
        for (p in state.players) {
            val err = Rules.validateFleet(state.config, p.fleet, p.placements)
            if (err != null) {
                log(LogKind.INFO, "${p.name} 布阵非法：$err")
                return
            }
        }
        state = state.copy(
            phase = RoomPhase.IN_BATTLE,
            round = 1,
            turn = Side.A,
            turnPhase = TurnPhase.TACTICAL,
            result = null,
            log = emptyList(),
            players = state.players.map {
                it.copy(
                    board = Rules.buildBoard(state.config, it.placements),
                    tacticalUsed = false, actedShipIds = emptySet(),
                    cooldowns = emptyMap(), useCounts = emptyMap(), damageByShip = emptyMap(),
                    pendingStrike = null, subSurfaceNextTurn = false, submergedStreak = 0
                )
            }
        )
        revision++
        log(LogKind.SYSTEM, "战斗开始！聊天室已锁定（战斗禁言），单回合限时 180 秒")
        beginTurn(Side.A)
    }

    private fun returnToLobby() {
        if (state.phase == RoomPhase.IN_BATTLE) return
        val cfg = state.config
        state = state.copy(
            phase = RoomPhase.ROOM_LOBBY,
            round = 1,
            turn = Side.A,
            turnPhase = TurnPhase.TACTICAL,
            result = null,
            players = state.players.map {
                it.copy(
                    ready = false,
                    board = Rules.buildBoard(cfg, it.placements),
                    tacticalUsed = false, actedShipIds = emptySet(),
                    cooldowns = emptyMap(), useCounts = emptyMap(), damageByShip = emptyMap(),
                    pendingStrike = null, subSurfaceNextTurn = false, submergedStreak = 0
                )
            }
        )
        revision++
        log(LogKind.SYSTEM, "已返回房间：连接保持、聊天窗口重新解锁，可调整地图 / 换船后直接开下一局")
    }

    private fun sendChat(side: Side, a: GameAction.Chat) {
        if (state.phase == RoomPhase.IN_BATTLE) {
            log(LogKind.INFO, "战斗中禁言，消息未发送")
            return
        }
        val text = a.text.trim().take(160)
        if (text.isEmpty()) return
        addChat(side, nameOf(side), text, a.emoji)
        log(LogKind.CHAT, "${nameOf(side)}：$text")
    }

    /* ==================== 回合流 ==================== */

    private fun skipTactical(side: Side) {
        if (!isMyTurn(side) || state.turnPhase != TurnPhase.TACTICAL) return
        state = state.copy(turnPhase = TurnPhase.ATTACK)
        revision++
        log(LogKind.SYSTEM, "${nameOf(side)} 跳过战术阶段，进入阶段二 · 多船协同攻击")
    }

    private fun endTurn(side: Side) {
        if (!isMyTurn(side)) return
        val p = state.players[side.ordinal]
        val sub = p.board.ships.firstOrNull { it.type == ShipType.SUBMARINE }
        var streak = p.submergedStreak
        if (sub != null && !sub.sunk) streak = if (sub.surfaced) 0 else streak + 1
        updatePlayer(side) { it.copy(submergedStreak = streak) }
        val next = side.other()
        val nextRound = if (side == Side.B) state.round + 1 else state.round
        state = state.copy(turn = next, round = nextRound)
        revision++
        beginTurn(next)
    }

    private fun beginTurn(side: Side) {
        undoStack.clear()
        redoStack.clear()
        val pending = state.players[side.ordinal].pendingStrike
        if (pending != null && pending.placedRound < state.round) {
            applyCross(
                side, pending.center, AttackKind.LAND_AIR, ShipType.GUNBOAT,
                ignoresAA = true, disableAAOnHit = false
            )
            log(
                LogKind.SKILL,
                "【陆航空袭】延时 1 回合生效：十字 5 格饱和轰炸 @${coordLabel(pending.center)}（无视一切 AA）"
            )
            checkGameOver()
            if (state.phase != RoomPhase.IN_BATTLE) return
        }

        var board = state.players[side.ordinal].board
        var surfaceNext = state.players[side.ordinal].subSurfaceNextTurn
        var streak = state.players[side.ordinal].submergedStreak
        val sub = board.ships.firstOrNull { it.type == ShipType.SUBMARINE }
        if (sub != null && !sub.sunk) {
            val mustSurface = surfaceNext || streak >= 3
            board = board.copy(
                ships = board.ships.map { s ->
                    if (s.type == ShipType.SUBMARINE) s.copy(surfaced = mustSurface) else s
                }
            )
            if (mustSurface) {
                streak = 0
                log(LogKind.SKILL, "【强制上浮】${nameOf(side)} 潜艇本回合全部 3 格暴露，可被常规炮击")
                board = resolveSubmergedMisses(side, sub, board)
            }
            surfaceNext = false
        }

        state = state.copy(
            turn = side,
            turnPhase = TurnPhase.TACTICAL,
            players = state.players.mapIndexed { i, q ->
                if (i == side.ordinal) q.copy(
                    board = board,
                    tacticalUsed = false,
                    actedShipIds = emptySet(),
                    pendingStrike = null,
                    subSurfaceNextTurn = surfaceNext,
                    submergedStreak = streak
                ) else q
            }
        )
        revision++
        log(LogKind.SYSTEM, "=== 第 ${state.round} 回合 · ${nameOf(side)} 行动｜阶段一：可选战术行动 ===")
        checkGameOver()
    }

    /**
     * 【上浮暴露复核】强制上浮的那一刻，把此前因【潜望镜隐蔽】被判为 MISS 的单点炮击复核为命中。
     *
     * 只处理"落在潜艇首尾两格、且当时根本没找到船"的单点 MISS（shipType == null && single）。
     * 空海 MISS、十字范围伤害 MISS、"该舰格已损毁"MISS 一律不动，
     * 因此其他舰船的打击结算逻辑完全不变；attackedPoints 也不改，坐标依旧不可重复攻击。
     *
     * 复核后把对应 ShotRecord 由 MISS 改为 HIT：棋盘上的 ○ 自动变 X（沿用既有渲染），
     * 玩家不需要重新点格子，也不会多获得一次攻击机会；下次上浮时这些记录已不是 MISS，
     * 天然不会重复结算。命中按 ShotRecord.attacker / by 归因给原攻击者，MVP 统计不漏。
     */
    private fun resolveSubmergedMisses(side: Side, sub: Ship, board: Board): Board {
        val center = if (sub.cells.size > 1) sub.cells[1] else sub.cells[0]
        val hidden = sub.cells.filter { it != center }.toSet()
        if (hidden.isEmpty()) return board

        val shots = board.shots.toMutableList()
        val owners = LinkedHashMap<Coord, ShotRecord>()
        for (i in shots.indices) {
            val s = shots[i]
            if (s.result == ShotResult.MISS && s.single && s.shipType == null && s.at in hidden) {
                if (!owners.containsKey(s.at)) owners[s.at] = s
                shots[i] = s.copy(result = ShotResult.HIT, shipType = ShipType.SUBMARINE)
            }
        }
        if (owners.isEmpty()) return board

        // 必须从 board 重新取当前潜艇对象，保留本回合刚设置的 surfaced = true
        val current = board.ships.firstOrNull { it.id == sub.id } ?: return board
        val updated = current.copy(hits = current.hits + owners.keys)
        val nb = board.copy(
            ships = board.ships.map { if (it.id == sub.id) updated else it },
            shots = shots
        )
        for (rec in owners.values) {
            rec.attacker?.let { addDamage(rec.by, it) }
        }
        val label = owners.keys.joinToString("、") { coordLabel(it) }
        log(
            LogKind.HIT,
            "【上浮暴露】${nameOf(side)} 潜艇上浮，此前 ${owners.size} 处水花点复核为命中 @$label" +
                (if (updated.sunk) "，潜艇被击沉！" else "")
        )
        return nb
    }

    /* ==================== 攻击结算 ==================== */

    private data class ShotReport(
        val board: Board,
        val result: ShotResult,
        val text: String,
        val kind: LogKind,
        val damage: Boolean,
        val shipHit: ShipType? = null
    )

    private fun canBeHit(s: Ship, c: Coord, kind: AttackKind): Boolean {
        if (s.type != ShipType.SUBMARINE || s.surfaced) return true
        return when (kind) {
            AttackKind.DEPTH_CHARGE, AttackKind.TORPEDO -> true
            AttackKind.AIR_CROSS, AttackKind.LAND_AIR -> false
            else -> c == s.cells[1]
        }
    }

    private fun intercepted(board: Board, cells: List<Coord>): Boolean {
        for (s in board.ships) {
            if (s.sunk) continue
            if (s.type != ShipType.CARRIER && s.type != ShipType.AA_CRUISER) continue
            if (s.aaDisabledUntil == state.round) continue
            val zone = Rules.aaZone(s, board.size)
            if (cells.any { it in zone }) return true
        }
        return false
    }

    private fun applyShot(
        board: Board,
        at: Coord,
        kind: AttackKind,
        by: Side,
        attackerShip: ShipType,
        ignoresAA: Boolean,
        mark: Boolean
    ): ShotReport {
        if (!at.inBounds(board.size) || at in board.mountains) {
            return ShotReport(board, ShotResult.BLOCKED, "山地阻挡，攻击无效", LogKind.MISS, false)
        }
        val air = Rules.isAir(kind)
        if (air && !ignoresAA && intercepted(board, listOf(at))) {
            return ShotReport(
                board.record(at, ShotResult.INTERCEPTED, by, null, mark),
                ShotResult.INTERCEPTED, "【防空拦截】战机被击落", LogKind.AA, false
            )
        }
        if (air && board.ships.any { !it.sunk && it.type == ShipType.GUNBOAT && at in it.cells }) {
            return ShotReport(
                board.record(at, ShotResult.IMMUNE, by, ShipType.GUNBOAT, mark),
                ShotResult.IMMUNE, "高速炮艇【灵活规避】，航空攻击无效", LogKind.INFO, false
            )
        }
        val ship = board.ships.firstOrNull { !it.sunk && at in it.cells && canBeHit(it, at, kind) }
            ?: return ShotReport(
                board.record(at, ShotResult.MISS, by, null, mark, attackerShip),
                ShotResult.MISS, "未命中（水花）", LogKind.MISS, false
            )
        if (at in ship.hits) {
            return ShotReport(
                board.record(at, ShotResult.MISS, by, ship.type, mark),
                ShotResult.MISS, "该舰格已损毁，重复打击无效", LogKind.MISS, false
            )
        }
        // 【潜望镜一击必沉】水下潜艇的中心第 2 格是唯一暴露点（潜望镜）。
        // 一旦被任何能命中它的攻击（常规炮击 / 舰载机炸弹 / 深弹 / 鱼雷）打中，
        // 直接判整艇沉没：不再出现「只掉 1 格血还能发射鱼雷、等强制上浮复核才沉」。
        // 只作用于 SUBMARINE，其他舰船的打击结算完全不变。
        val periscopeHit = ship.type == ShipType.SUBMARINE &&
            !ship.surfaced && ship.cells.size > 1 && at == ship.cells[1]
        val extra = if (periscopeHit) ship.cells else emptyList()
        val updated = ship.copy(hits = ship.hits + at + extra)
        val ships = board.ships.map { if (it.id == ship.id) updated else it }
        val result = if (updated.sunk) ShotResult.SUNK else ShotResult.HIT
        val nb = board.copy(ships = ships).record(at, result, by, ship.type, mark)
        val text = when {
            updated.sunk && periscopeHit -> "命中【潜望镜】${FleetCatalog.label(ship.type)}，整艇直接沉没！"
            updated.sunk -> "击沉敌方 ${FleetCatalog.label(ship.type)}！"
            else -> "命中敌方 ${FleetCatalog.label(ship.type)}"
        }
        return ShotReport(nb, result, text, LogKind.HIT, true, ship.type)
    }

    private fun applyCross(
        side: Side,
        center: Coord,
        kind: AttackKind,
        attackerShip: ShipType,
        ignoresAA: Boolean,
        disableAAOnHit: Boolean
    ) {
        val enemy = side.other()
        var board = boardOf(enemy)
        val cells = crossCells(center, board.size)
        if (Rules.isAir(kind) && !ignoresAA && intercepted(board, cells)) {
            board = board.record(center, ShotResult.INTERCEPTED, side, null, false)
            setBoard(enemy, board)
            log(LogKind.AA, "【防空拦截】战机被击落，${FleetCatalog.label(attackerShip)} 的空袭全部失效 @${coordLabel(center)}")
            return
        }
        val hitTypes = HashSet<ShipType>()
        for (c in cells) {
            val rep = applyShot(board, c, kind, side, attackerShip, ignoresAA, mark = false)
            board = rep.board
            if (rep.damage) {
                addDamage(side, attackerShip)
                rep.shipHit?.let { hitTypes.add(it) }
            }
            log(
                if (rep.damage) LogKind.HIT else LogKind.MISS,
                "十字打击 ${coordLabel(c)}：${rep.text}"
            )
        }
        if (disableAAOnHit && hitTypes.any { it == ShipType.CARRIER || it == ShipType.AA_CRUISER }) {
            board = board.copy(
                ships = board.ships.map { s ->
                    if (s.type in hitTypes && (s.type == ShipType.CARRIER || s.type == ShipType.AA_CRUISER))
                        s.copy(aaDisabledUntil = state.round + 1) else s
                }
            )
            log(LogKind.SKILL, "【AA 瘫痪】被命中的航母 / 防空巡洋舰防空网失效 1 个敌方回合")
        }
        setBoard(enemy, board)
    }

    /**
     * 鱼雷机投雷：沿「投雷起点 + 航向」直线推进，只在一处爆炸、只结算一次伤害，
     * 沿途不写 ShotRecord，棋盘上不会留下一串轨迹标记。
     *
     * 爆炸点按优先级确定：
     *   ① 航线上碰到的第一艘未沉敌舰所在格（与潜航鱼雷同为 AttackKind.TORPEDO，
     *      可命中水下潜艇、不受炮艇「灵活规避」影响）；
     *   ② 前方是山地时，取撞山前的那一格；投雷起点即山地则空袭无效；
     *   ③ 到达最长射程的最后一格。
     * 战机本身仍受整条航线的防空区拦截：被击落则整次空袭失效。
     */
    private fun applyTorpedoBomberLine(side: Side, line: List<Coord>) {
        val enemy = side.other()
        var board = boardOf(enemy)
        if (intercepted(board, line)) {
            board = board.record(line.first(), ShotResult.INTERCEPTED, side, null, false)
            setBoard(enemy, board)
            log(LogKind.AA, "【防空拦截】战机被击落，鱼雷机空袭全部失效 @${coordLabel(line.first())}")
            return
        }
        var impact = line.last()
        var reason = "到达最大射程"
        var invalid = false
        for (i in line.indices) {
            val c = line[i]
            if (c in board.mountains) {
                if (i == 0) {
                    invalid = true
                    reason = "投雷起点即山地"
                } else {
                    impact = line[i - 1]
                    reason = "前方 ${coordLabel(c)} 为山地，撞山前"
                }
                break
            }
            if (board.ships.any { !it.sunk && c in it.cells }) {
                impact = c
                reason = "发现目标"
                break
            }
        }
        if (invalid) {
            log(LogKind.INFO, "【鱼雷机空袭】$reason @${coordLabel(line.first())}，本次空袭无效果")
            setBoard(enemy, board)
            return
        }
        val rep = applyShot(
            board, impact, AttackKind.TORPEDO, side,
            ShipType.AVIATION_CRUISER, ignoresAA = true, mark = false
        )
        board = rep.board
        log(
            if (rep.damage) LogKind.HIT else LogKind.MISS,
            "【鱼雷机空袭】$reason，鱼雷于 ${coordLabel(impact)} 爆炸：${rep.text}"
        )
        if (rep.damage) addDamage(side, ShipType.AVIATION_CRUISER)
        setBoard(enemy, board)
    }

    private fun fire(side: Side, a: GameAction.Fire) {
        if (!isMyTurn(side)) return
        val me = state.players[side.ordinal]
        val ship = actionableShip(side, a.shipId) ?: return
        if (ship.id in me.actedShipIds) {
            log(LogKind.INFO, "该舰本回合已行动")
            return
        }
        if (state.turnPhase != TurnPhase.ATTACK) {
            log(LogKind.INFO, "请先点击「进入阶段二 · 开始攻击」，再选择开火坐标")
            return
        }
        val budget = Rules.attacksPerTurn(me.board.ships.count { !it.sunk })
        if (me.actedShipIds.size >= budget) {
            log(LogKind.INFO, "本回合 $budget 次攻击名额已用尽，请点击「结束回合」")
            return
        }
        val enemy = side.other()
        val eb = boardOf(enemy)
        val t = a.target
        if (!t.inBounds(eb.size) || t in eb.mountains) {
            log(LogKind.INFO, "目标非法：越界或为不可攻击的山地")
            return
        }
        if (ship.type == ShipType.SUBMARINE) {
            fireTorpedo(side, ship, t)
            return
        }
        if (t in eb.attackedPoints) {
            log(LogKind.INFO, "该坐标已攻击过（单点攻击去重），请换一个格子")
            return
        }
        val aliveCount = me.board.ships.count { !it.sunk }
        val lastGun = ship.type == ShipType.CARRIER && aliveCount == 1
        val kind = when (ship.type) {
            ShipType.CARRIER -> if (lastGun) AttackKind.GUN else AttackKind.BOMBER
            ShipType.ASW_SHIP -> AttackKind.DEPTH_CHARGE
            else -> AttackKind.GUN
        }
        val rep = applyShot(eb, t, kind, side, ship.type, ignoresAA = lastGun, mark = true)
        setBoard(enemy, rep.board)
        log(
            rep.kind,
            "${nameOf(side)} · ${FleetCatalog.label(ship.type)}${if (lastGun) "【绝境副炮】" else ""} @${coordLabel(t)}：${rep.text}"
        )
        if (rep.damage) addDamage(side, ship.type)
        markActed(side, ship.id)
        checkGameOver()
    }

    private fun fireTorpedo(side: Side, ship: Ship, target: Coord) {
        val center = if (ship.cells.size > 1) ship.cells[1] else ship.cells[0]
        if (target.r != center.r && target.c != center.c) {
            log(LogKind.INFO, "鱼雷只能沿潜艇所在行或列的直线方向发射")
            return
        }
        if (target == center || target.manhattan(center) > 4) {
            log(LogKind.INFO, "鱼雷射程为直线 4 格内")
            return
        }
        val dr = when {
            target.r > center.r -> 1
            target.r < center.r -> -1
            else -> 0
        }
        val dc = when {
            target.c > center.c -> 1
            target.c < center.c -> -1
            else -> 0
        }
        val enemy = side.other()
        var board = boardOf(enemy)
        log(LogKind.SKILL, "${nameOf(side)} · 潜艇【潜航鱼雷】自 ${coordLabel(center)} 沿直线发射，最多推进 4 格")
        for (step in 1..4) {
            val cur = Coord(center.r + dr * step, center.c + dc * step)
            if (!cur.inBounds(board.size)) break
            if (cur in board.mountains) {
                log(LogKind.INFO, "鱼雷被山地阻挡，停止于 ${coordLabel(cur)}")
                break
            }
            if (cur in board.attackedPoints) {
                log(LogKind.INFO, "鱼雷在已攻击过的格子处停止：${coordLabel(cur)}")
                break
            }
            val rep = applyShot(board, cur, AttackKind.TORPEDO, side, ShipType.SUBMARINE, ignoresAA = true, mark = true)
            board = rep.board
            log(rep.kind, "鱼雷 ${coordLabel(cur)}：${rep.text}")
            if (rep.damage) addDamage(side, ShipType.SUBMARINE)
            if (rep.result == ShotResult.HIT || rep.result == ShotResult.SUNK) break
        }
        setBoard(enemy, board)
        updatePlayer(side) { it.copy(subSurfaceNextTurn = true) }
        markActed(side, ship.id)
        log(LogKind.SYSTEM, "${nameOf(side)} 潜艇下 1 回合将【强制上浮】")
        checkGameOver()
    }

    /* ==================== 技能 ==================== */

    private fun useSkill(side: Side, a: GameAction.Skill) {
        if (!isMyTurn(side)) return
        val me = state.players[side.ordinal]
        val ship = actionableShip(side, a.shipId) ?: return
        val kind = a.kind
        if (Rules.skillFor(ship.type) != kind) {
            log(LogKind.INFO, "该舰不具备此技能")
            return
        }
        val tactical = Rules.isTactical(kind)
        if (tactical) {
            if (state.turnPhase != TurnPhase.TACTICAL || me.tacticalUsed) {
                log(LogKind.INFO, "阶段一已结束或本回合战术行动已使用")
                return
            }
        } else {
            if (ship.id in me.actedShipIds) {
                log(LogKind.INFO, "该舰本回合已行动")
                return
            }
            if (state.turnPhase != TurnPhase.ATTACK) {
                log(LogKind.INFO, "请先点击「进入阶段二 · 开始攻击」，再释放攻击技能")
                return
            }
            val budget = Rules.attacksPerTurn(me.board.ships.count { !it.sunk })
            if (me.actedShipIds.size >= budget) {
                log(LogKind.INFO, "本回合 $budget 次攻击名额已用尽")
                return
            }
        }
        if (state.round < Rules.skillMinRound(kind)) {
            log(LogKind.INFO, "【${Rules.skillLabel(kind)}】第 ${Rules.skillMinRound(kind)} 回合起才可使用")
            return
        }
        val readyRound = me.cooldowns[kind.name] ?: 0
        if (state.round < readyRound) {
            log(LogKind.INFO, "技能冷却中，第 $readyRound 回合可再次使用")
            return
        }
        val used = me.useCounts[kind.name] ?: 0
        if (used >= Rules.skillLimit(kind)) {
            log(LogKind.INFO, "【${Rules.skillLabel(kind)}】全局次数已用尽")
            return
        }
        val center = a.targets.firstOrNull() ?: run {
            log(LogKind.INFO, "缺少目标坐标")
            return
        }
        if (!center.inBounds(boardOf(side.other()).size)) {
            log(LogKind.INFO, "目标坐标越界")
            return
        }

        when (kind) {
            SkillKind.RECON -> {
                val eb = boardOf(side.other())
                val cells = crossCells(center, eb.size)
                if (intercepted(eb, cells)) {
                    log(LogKind.AA, "【防空拦截】舰载侦察机被击落 @${coordLabel(center)}")
                } else {
                    val n = eb.ships.count { s ->
                        !s.sunk && s.type != ShipType.SUBMARINE && s.cells.any { it in cells }
                    }
                    // 扫描成功才留痕：只记录「哪 5 格被扫过」，不记录船的位置，
                    // 棋盘据此画放大镜标记；撤销 / 重做随 state 快照自然回退。
                    setBoard(side.other(), eb.copy(reconCells = eb.reconCells + cells))
                    log(
                        LogKind.SKILL,
                        "${nameOf(side)}【舰载侦察机】@${coordLabel(center)}：十字 5 格已完成扫描（棋盘标记「已侦察」），水面舰信号 ${if (n == 0) "无" else "$n 个"}（仅报数量，不报坐标）"
                    )
                }
            }
            SkillKind.SONAR -> {
                val cells = crossCells(center, boardOf(side.other()).size)
                val n = boardOf(side.other()).ships.count { s ->
                    !s.sunk && s.type == ShipType.SUBMARINE && s.cells.any { it in cells }
                }
                log(
                    LogKind.SKILL,
                    "${nameOf(side)}【主动水听声呐】@${coordLabel(center)}：${if (n > 0) "发现潜艇信号（无视水下隐蔽）" else "未发现潜艇信号"}"
                )
            }
            SkillKind.EVASIVE_MANEUVER -> {
                val newO = a.orientation ?: ship.orientation
                val newCells = Rules.cellsOf(center, 2, newO)
                val own = me.board
                val err = when {
                    newCells.any { !it.inBounds(own.size) } -> "超出海域边界"
                    newCells.any { it in own.mountains } -> "山地不可进入"
                    newCells.any { it in own.attackedPoints } -> "目标水域已被攻击过"
                    center.manhattan(ship.origin) > 4 -> "超过 4 格航程"
                    own.ships.any { s -> s.id != ship.id && !s.sunk && s.cells.any { it in newCells } } -> "与己方舰船重叠"
                    else -> null
                }
                if (err != null) {
                    log(LogKind.INFO, "紧急规避机动失败：$err")
                    return
                }
                val kept = ship.cells.mapIndexedNotNull { i, c -> if (c in ship.hits) i else null }
                val newHits = kept.mapNotNull { newCells.getOrNull(it) }.toSet()
                val moved = ship.copy(origin = center, orientation = newO, cells = newCells, hits = newHits)
                setBoard(side, own.copy(ships = own.ships.map { if (it.id == ship.id) moved else it }))
                log(LogKind.SKILL, "敌方驱逐舰已实施紧急规避机动（向双方全局播报，保留已受损伤）")
            }
            SkillKind.SALVO -> {
                if (a.targets.size < 2) {
                    log(LogKind.INFO, "主炮齐射需要选择 2 个不同坐标")
                    return
                }
                val p1 = a.targets[0]
                val p2 = a.targets[1]
                val eb = boardOf(side.other())
                if (p1 == p2 || p1.chebyshev(p2) > 4) {
                    log(LogKind.INFO, "两发需为不同坐标且间距 ≤ 4 格")
                    return
                }
                if (listOf(p1, p2).any { !it.inBounds(eb.size) || it in eb.mountains || it in eb.attackedPoints }) {
                    log(LogKind.INFO, "齐射坐标非法（山地 / 越界 / 已攻击过）")
                    return
                }
                if (state.turnPhase == TurnPhase.TACTICAL) {
                    state = state.copy(turnPhase = TurnPhase.ATTACK)
                    revision++
                }
                var board = eb
                for (t in listOf(p1, p2)) {
                    val rep = applyShot(board, t, AttackKind.GUN, side, ShipType.BATTLESHIP, ignoresAA = true, mark = true)
                    board = rep.board
                    log(rep.kind, "【主炮齐射】${coordLabel(t)}：${rep.text}")
                    if (rep.damage) addDamage(side, ShipType.BATTLESHIP)
                }
                setBoard(side.other(), board)
            }
            SkillKind.TORPEDO_BOMBER -> {
                if (a.targets.size < 2) {
                    log(LogKind.INFO, "【鱼雷机空袭】需先点突防起点、再点同行或同列的一格指定航向")
                    return
                }
                val line = Rules.torpedoBomberLine(
                    a.targets[0], a.targets[1], boardOf(side.other()).size
                )
                if (line.size < 2) {
                    log(LogKind.INFO, "【鱼雷机空袭】航向非法：第 2 个点必须与起点同行或同列")
                    return
                }
                if (state.turnPhase == TurnPhase.TACTICAL) {
                    state = state.copy(turnPhase = TurnPhase.ATTACK)
                    revision++
                }
                log(
                    LogKind.SKILL,
                    "${nameOf(side)} 派出【鱼雷机空袭】@${coordLabel(line.first())}：沿航向投雷，最多推进 ${line.size} 格，只在爆炸点结算，不显示轨迹"
                )
                applyTorpedoBomberLine(side, line)
            }
            SkillKind.INCENDIARY -> {
                if (state.turnPhase == TurnPhase.TACTICAL) {
                    state = state.copy(turnPhase = TurnPhase.ATTACK)
                    revision++
                }
                log(LogKind.SKILL, "${nameOf(side)} 发射【高能燃烧弹】@${coordLabel(center)}：纯炮击无视 AA")
                applyCross(side, center, AttackKind.INCENDIARY, ShipType.FIRE_CRUISER, true, true)
            }
            SkillKind.AIR_STRIKE_CALL -> {
                if (state.turnPhase == TurnPhase.TACTICAL) {
                    state = state.copy(turnPhase = TurnPhase.ATTACK)
                    revision++
                }
                updatePlayer(side) { it.copy(pendingStrike = PendingStrike(center, state.round)) }
                log(
                    LogKind.SKILL,
                    "${nameOf(side)}【呼叫陆航空袭】锁定 ${coordLabel(center)}，将在己方下一回合开始时无视一切 AA 饱和轰炸十字 5 格"
                )
            }
        }

        if (tactical) updatePlayer(side) { it.copy(tacticalUsed = true) }
        else markActed(side, ship.id)
        updatePlayer(side) { p ->
            val k = kind.name
            val cd = Rules.skillCooldown(kind)
            p.copy(
                useCounts = p.useCounts + (k to (used + 1)),
                cooldowns = if (cd > 0) p.cooldowns + (k to (state.round + cd)) else p.cooldowns
            )
        }
        // 战术技能释放后仍停留在阶段一，由玩家显式点击「进入阶段二」推进，不自动跳转。
        checkGameOver()
    }

    /* ==================== 结算 / 视图 / 托管 ==================== */

    private fun statsOf(p: PlayerState): SideStats {
        val alive = p.board.ships.filter { !it.sunk }.map { it.type }
        val sunk = p.board.ships.filter { it.sunk }.map { it.type }
        val total = p.damageByShip.values.sum()
        val best = p.damageByShip.maxByOrNull { it.value }
        val mvp = if (best == null || best.value <= 0) "无" else
            "${FleetCatalog.label(ShipType.valueOf(best.key))}（${best.value} 次命中）"
        return SideStats(alive, sunk, total, mvp)
    }

    private fun checkGameOver() {
        val s = state
        if (s.phase != RoomPhase.IN_BATTLE) return
        fun dead(p: PlayerState) = p.board.ships.isNotEmpty() && p.board.ships.all { it.sunk }
        val aDead = dead(s.players[0])
        val bDead = dead(s.players[1])
        if (!aDead && !bDead) return
        val winner = when {
            bDead && !aDead -> Side.A
            aDead && !bDead -> Side.B
            else -> s.turn
        }
        val result = BattleResult(winner, s.round, statsOf(s.players[0]), statsOf(s.players[1]))
        state = s.copy(phase = RoomPhase.POST_BATTLE_LOBBY, turnPhase = TurnPhase.GAME_OVER, result = result)
        revision++
        log(LogKind.SYSTEM, "战斗结束！胜利方：${nameOf(winner)}｜共 ${s.round} 回合｜点『返回房间』保持连接、解锁聊天、直接开下一局")
    }

    fun legalTargetsFor(side: Side): List<Coord> {
        val b = boardOf(side.other())
        val out = ArrayList<Coord>()
        for (r in 0 until b.size) {
            for (c in 0 until b.size) {
                val p = Coord(r, c)
                if (p !in b.mountains && p !in b.attackedPoints) out.add(p)
            }
        }
        return out
    }

    private fun skillAvailabilities(side: Side): List<SkillAvailability> {
        val s = state
        val p = s.players[side.ordinal]
        if (s.phase != RoomPhase.IN_BATTLE) return emptyList()
        val out = ArrayList<SkillAvailability>()
        for (ship in p.board.ships) {
            if (ship.sunk) continue
            val kind = Rules.skillFor(ship.type) ?: continue
            val tactical = Rules.isTactical(kind)
            val used = p.useCounts[kind.name] ?: 0
            val reason = when {
                s.turn != side -> "等待对方回合"
                tactical && (s.turnPhase != TurnPhase.TACTICAL || p.tacticalUsed) -> "阶段一已结束"
                !tactical && ship.id in p.actedShipIds -> "该舰本回合已行动"
                !tactical && s.turnPhase != TurnPhase.ATTACK -> "需先进入阶段二"
                !tactical && p.actedShipIds.size >= Rules.attacksPerTurn(p.board.ships.count { !it.sunk }) -> "攻击名额已用尽"
                s.round < Rules.skillMinRound(kind) -> "第 ${Rules.skillMinRound(kind)} 回合起可用"
                s.round < (p.cooldowns[kind.name] ?: 0) -> "冷却中（第 ${p.cooldowns[kind.name]} 回合可用）"
                used >= Rules.skillLimit(kind) -> "全局次数已用尽"
                else -> ""
            }
            out.add(
                SkillAvailability(ship.id, kind, Rules.skillLabel(kind), tactical, reason.isEmpty(), reason)
            )
        }
        return out
    }

    fun viewFor(side: Side): GameView {
        val s = state
        val me = s.players[side.ordinal]
        val enemy = s.players[side.other().ordinal]
        val redacted = enemy.board.copy(ships = enemy.board.ships.filter { it.sunk })
        return GameView(
            viewer = side,
            phase = s.phase,
            config = s.config,
            round = s.round,
            turn = s.turn,
            turnPhase = s.turnPhase,
            hostName = s.players[0].name,
            guestName = s.players[1].name,
            hostReady = s.players[0].ready,
            guestReady = s.players[1].ready,
            hostFleet = s.players[0].fleet,
            guestFleet = s.players[1].fleet,
            myBoard = me.board,
            enemyBoard = redacted,
            enemySunkTypes = enemy.board.ships.filter { it.sunk }.map { it.type },
            myAlive = me.board.ships.filter { !it.sunk }.map { it.type },
            enemyAlive = enemy.board.ships.filter { !it.sunk }.map { it.type },
            legalTargets = if (s.phase == RoomPhase.IN_BATTLE && s.turn == side) legalTargetsFor(side) else emptyList(),
            skills = skillAvailabilities(side),
            myActed = me.actedShipIds.toList(),
            log = s.log,
            chat = s.chat,
            result = s.result,
            revision = revision,
            canUndo = undoStack.any { it.first == side },
            canRedo = redoStack.any { it.first == side },
            attackBudget = Rules.attacksPerTurn(me.board.ships.count { !it.sunk }),
            attackUsed = me.actedShipIds.size
        )
    }

    /** 180 秒超时：按剩余未行动存活舰船数，执行对应次数的随机常规单格炮击并自动结束回合。 */
    fun autoRandomAttacks(side: Side, rnd: Random = Random.Default) {
        if (!isMyTurn(side)) return
        if (state.turnPhase == TurnPhase.TACTICAL) {
            state = state.copy(turnPhase = TurnPhase.ATTACK)
            revision++
        }
        val ids = state.players[side.ordinal].board.ships.filter { !it.sunk }.map { it.id }
        for (id in ids) {
            if (state.phase != RoomPhase.IN_BATTLE || state.turn != side) break
            if (id in state.players[side.ordinal].actedShipIds) continue
            val ship = state.players[side.ordinal].board.ships.firstOrNull { it.id == id } ?: continue
            val legal = legalTargetsFor(side)
            if (legal.isEmpty()) break
            val t = legal[rnd.nextInt(legal.size)]
            val rep = applyShot(boardOf(side.other()), t, AttackKind.GUN, side, ship.type, ignoresAA = true, mark = true)
            setBoard(side.other(), rep.board)
            log(rep.kind, "【180s 超时托管】${nameOf(side)} 随机常规炮击 ${coordLabel(t)}：${rep.text}")
            if (rep.damage) addDamage(side, ship.type)
            markActed(side, id)
            checkGameOver()
        }
        if (state.phase == RoomPhase.IN_BATTLE && state.turn == side) {
            log(LogKind.SYSTEM, "托管完成，系统自动结束回合")
            endTurn(side)
        }
    }

    /** 单机模式：为 AI 侧自动完成编成与准备。 */
    fun autoPrepare(side: Side, seed: Long) {
        if (state.phase == RoomPhase.IN_BATTLE) return
        val placements = Rules.defaultPlacements(state.config, state.players[side.ordinal].fleet, seed)
        dispatch(side, GameAction.SetFleet(state.players[side.ordinal].fleet, placements))
        dispatch(side, GameAction.SetReady(true))
    }
}
