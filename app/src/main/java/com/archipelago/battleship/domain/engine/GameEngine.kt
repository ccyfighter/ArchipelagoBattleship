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

    /** 公开战报：双方可见，文本只允许「谁 + 坐标 + 结果」，不含攻击方式与舰种。 */
    private fun log(kind: LogKind, text: String) = logTo(null, kind, text)

    /** 私有反馈：只给操作者自己看（校验失败、侦察结果、技能详情）。 */
    private fun logOwn(side: Side, kind: LogKind, text: String) = logTo(side, kind, text)

    private fun logTo(owner: Side?, kind: LogKind, text: String) {
        val e = LogEntry(seq++, state.round, kind, text, owner)
        state = state.copy(log = (state.log + e).takeLast(200))
        revision++
    }

    /**
     * 所有攻击结算的唯一公开出口。
     *
     * 只播报「哪一方 + 在哪一格 + 什么结果」：不出现鱼雷 / 舰载机 / 陆航 / 主炮齐射等攻击方式，
     * 也不出现开火舰种或被击中舰种（击沉除外，战果按既有规则公开）。
     * 这样对手无法从「鱼雷在无山海域打空」反推出潜艇位于炸点直线 4 格之外。
     * 结果类型统一收敛为 HIT / MISS 两类，避免「防空拦截」「技能」这类标签反过来泄露方式。
     */
    private fun reportAttack(side: Side, at: Coord, rep: ShotReport, note: String = "") {
        val outcome = when (rep.result) {
            ShotResult.SUNK -> rep.text
            ShotResult.HIT -> "击中"
            ShotResult.MISS -> "未命中"
            ShotResult.INTERCEPTED -> "攻击被拦截"
            ShotResult.IMMUNE -> "攻击无效"
            ShotResult.BLOCKED -> "攻击无效"
        }
        val kind = if (rep.result == ShotResult.SUNK || rep.result == ShotResult.HIT)
            LogKind.HIT else LogKind.MISS
        val prefix = if (note.isEmpty()) "" else "$note · "
        log(kind, "$prefix${nameOf(side)} 于 ${coordLabel(at)} 发动攻击：$outcome")
    }

    /** 未产生 ShotReport 的失败攻击（例如整片被拦截），同样只报坐标与结果。 */
    private fun reportFailed(side: Side, at: Coord, outcome: String) {
        log(LogKind.MISS, "${nameOf(side)} 于 ${coordLabel(at)} 发动攻击：$outcome")
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

    /**
     * 只有「真正打出去、且产生判定」的单点攻击才把坐标写进 attackedPoints。
     * 拦截 / 免疫 / 山地阻挡只是显示标记，绝不封锁坐标：之后仍可由其他舰船对该格开火。
     */
    private fun Board.record(
        at: Coord,
        r: ShotResult,
        by: Side,
        t: ShipType?,
        mark: Boolean,
        attacker: ShipType? = null
    ): Board {
        val consumes = mark && (r == ShotResult.MISS || r == ShotResult.HIT || r == ShotResult.SUNK)
        return copy(
            shots = (shots + ShotRecord(at, r, by, state.round, t, mark, attacker)).takeLast(500),
            attackedPoints = if (consumes) attackedPoints + at else attackedPoints
        )
    }

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
            logOwn(side, LogKind.INFO, "找不到该舰船")
            return null
        }
        if (ship.sunk) {
            logOwn(side, LogKind.INFO, "【已沉没】${FleetCatalog.label(ship.type)} 已沉没，无法再开火或释放技能")
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
            GameAction.Resign -> resign(side)
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
            logOwn(side, LogKind.INFO, "只有房主可以修改地图规则")
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
            logOwn(side, LogKind.INFO, "舰队编成非法，必须 5+4+3+2+1 各一艘")
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
        // 双方默认不知道对面选了哪些舰种：编成内容只回给操作者自己
        logOwn(side, LogKind.INFO, "舰队编成已保存：" + a.fleet.all().joinToString(" / ") { FleetCatalog.label(it) })
        log(LogKind.SYSTEM, "${nameOf(side)} 已完成舰队编成")
    }

    private fun setReady(side: Side, ready: Boolean) {
        if (state.phase == RoomPhase.IN_BATTLE) return
        val p = state.players[side.ordinal]
        if (ready) {
            val err = Rules.validateFleet(state.config, p.fleet, p.placements)
            if (err != null) {
                logOwn(side, LogKind.INFO, "无法准备：$err")
                return
            }
        }
        updatePlayer(side) { it.copy(ready = ready) }
        log(LogKind.SYSTEM, "${nameOf(side)} ${if (ready) "已准备" else "取消准备"}")
    }

    private fun startBattle(side: Side) {
        if (side != Side.A) {
            logOwn(side, LogKind.INFO, "只有房主可以开始战斗")
            return
        }
        if (state.phase == RoomPhase.IN_BATTLE) return
        if (!state.players[0].ready || !state.players[1].ready) {
            logOwn(side, LogKind.INFO, "双方都点击『准备』后才能开始战斗")
            return
        }
        for (p in state.players) {
            val err = Rules.validateFleet(state.config, p.fleet, p.placements)
            if (err != null) {
                logOwn(side, LogKind.INFO, "${p.name} 布阵非法：$err")
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

    /** 投降：立即结束本局并判对方获胜。单机 / 联机共用，联机时由房主权威结算后推给客机。 */
    private fun resign(side: Side) {
        if (state.phase != RoomPhase.IN_BATTLE) return
        val winner = side.other()
        val result = BattleResult(
            winner = winner,
            rounds = state.round,
            host = statsOf(state.players[0]),
            guest = statsOf(state.players[1]),
            reason = BattleEndReason.RESIGN,
            resigned = side
        )
        state = state.copy(
            phase = RoomPhase.POST_BATTLE_LOBBY,
            turnPhase = TurnPhase.GAME_OVER,
            result = result
        )
        revision++
        undoStack.clear()
        redoStack.clear()
        log(
            LogKind.SYSTEM,
            "${nameOf(side)} 已投降，${nameOf(winner)} 获胜｜共 ${state.round} 回合；点『返回房间』保持连接、解锁聊天、直接开下一局"
        )
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
            logOwn(side, LogKind.INFO, "战斗中禁言，消息未发送")
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
            // 预告标记是纯显示层：生效前先清掉，避免棋盘上一直留着旧十字
            val eb0 = boardOf(side.other())
            setBoard(side.other(), eb0.copy(strikeCells = emptySet()))
            applyCross(
                side, pending.center, AttackKind.LAND_AIR, ShipType.GUNBOAT,
                ignoresAA = true, disableAAOnHit = false
            )
            log(LogKind.SYSTEM, "${nameOf(side)} 标记的区域攻击已生效")
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
                log(LogKind.SYSTEM, "${nameOf(side)} 一艘舰船本回合已暴露，可被常规攻击命中")
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

        // 必须从 board 重新取当前潜艇对象：保留本回合刚设置的 surfaced = true，
        // 也用来排除已经被深水炸弹 / 鱼雷打穿的格子，避免同一格被复核重复计一次伤害。
        val current = board.ships.firstOrNull { it.id == sub.id } ?: return board
        val pendingCells = hidden.filter { it !in current.hits }.toSet()
        if (pendingCells.isEmpty()) return board

        val shots = board.shots.toMutableList()
        val owners = LinkedHashMap<Coord, ShotRecord>()
        for (i in shots.indices) {
            val s = shots[i]
            if (s.result == ShotResult.MISS && s.single && s.shipType == null && s.at in pendingCells) {
                if (!owners.containsKey(s.at)) owners[s.at] = s
                shots[i] = s.copy(result = ShotResult.HIT, shipType = ShipType.SUBMARINE)
            }
        }
        if (owners.isEmpty()) return board

        val updated = current.copy(hits = current.hits + owners.keys)
        val nb = board.copy(
            ships = board.ships.map { if (it.id == sub.id) updated else it },
            shots = shots
        )
        for (rec in owners.values) {
            rec.attacker?.let { addDamage(rec.by, it) }
        }
        // 复核播报同样只报坐标与结果，不提「上浮 / 潜艇 / 潜望镜」这些机制词
        for ((c, rec) in owners) {
            log(LogKind.HIT, "${nameOf(rec.by)} 于 ${coordLabel(c)} 的既有攻击复核为命中")
        }
        if (updated.sunk) log(LogKind.SYSTEM, "击沉敌方 潜艇！")
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
        // 深水炸弹 / 鱼雷能打水下整艇；其余攻击（含无视 AA 的饱和空袭）只能打潜望镜中心格。
        // v10 之前把 AIR_CROSS / LAND_AIR 一律判 false，导致「饱和轰炸」连露出潜望镜的
        // 那一格都打不到，与「任何命中中心格的攻击直接一击必沉」自相矛盾。
        return if (kind == AttackKind.DEPTH_CHARGE || kind == AttackKind.TORPEDO) true
        else c == s.cells[1]
    }

    /**
     * 防空判定唯一入口，两条互不混用的规则：
     *  area = true  → 区域防空网，只判定【部署点】（侦察机中心 / 鱼雷机投雷起点）；
     *                  扫描范围、鱼雷航线、爆炸点一律不参与，这就是「AA 圈外视距打击」。
     *  area = false → 本体点防空，只判定【炸点】是否落在防空舰自己的船体格上；
     *                  航母 5 格 / 防空巡洋舰 3 格，不会把周围 15 格海域变成禁炸区。
     * 陆航空袭（ignoresAA = true）与绝境副炮（物理炮击）都不进入本函数。
     * aaDisabledUntil 记「失效的最后回合」，用 state.round <= aaDisabledUntil 判定，
     * 燃烧弹压制当回合立即生效、下回合自动恢复。
     */
    private fun aaShipAt(board: Board, cells: List<Coord>, area: Boolean): Boolean {
        for (s in board.ships) {
            if (s.sunk) continue
            if (s.type != ShipType.CARRIER && s.type != ShipType.AA_CRUISER) continue
            // aaDisabledUntil 记的是「失效的最后回合」；默认 0，第 1 回合起不会误跳过。
            if (state.round <= s.aaDisabledUntil) continue
            val zone = if (area) Rules.aaZone(s, board.size) else Rules.aaBody(s, board.size)
            if (cells.any { it in zone }) return true
        }
        return false
    }

    /** 部署点落入区域防空圈：侦察机 / 鱼雷机会被击落。cells 只允许传部署点。 */
    private fun intercepted(board: Board, cells: List<Coord>): Boolean =
        aaShipAt(board, cells, area = true)

    /** 炸点落在防空舰本体：近防火力击落来犯敌机（点防空）。 */
    private fun aaBodyAt(board: Board, at: Coord): Boolean =
        aaShipAt(board, listOf(at), area = false)

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
        // 炮艇「灵活规避」优先于防空判定：炮艇恰好停在防空舰本体格上时也应报免疫，
        // 而不是被近防火力覆盖成「拦截」。
        if (air && board.ships.any { !it.sunk && it.type == ShipType.GUNBOAT && at in it.cells }) {
            return ShotReport(
                board.record(at, ShotResult.IMMUNE, by, ShipType.GUNBOAT, mark),
                ShotResult.IMMUNE, "目标免疫此次攻击", LogKind.INFO, false
            )
        }
        // v10：区域防空网不再按【炸点】击落飞机。航母战术轰炸机从己方航母起飞，
        // 部署点天然在敌方防空圈外，只是飞过防空区去投弹，按「飞机只是飞过该区
        // 去炸别处不算被击落」的既定规则不应判拦截。只有炸弹直接落在防空舰本体格时，
        // 才由该舰近防火力击落（本体点防空）。侦察机 / 鱼雷机的部署点判定在各自分支里。
        if (air && !ignoresAA && aaBodyAt(board, at)) {
            logOwn(
                by, LogKind.AA,
                "炸点 ${coordLabel(at)} 落在敌方防空舰本体，近防火力将战机击落；区域防空网只打部署点，从圈外投弹轰炸周围海域不会再被拦截；该坐标未封锁，可换一艘舰继续攻击"
            )
            return ShotReport(
                board.record(at, ShotResult.INTERCEPTED, by, null, mark),
                ShotResult.INTERCEPTED, "攻击被拦截", LogKind.AA, false
            )
        }
        val ship = board.ships.firstOrNull { !it.sunk && at in it.cells && canBeHit(it, at, kind) }
            ?: return ShotReport(
                board.record(at, ShotResult.MISS, by, null, mark, attackerShip),
                ShotResult.MISS, "未命中（水花）", LogKind.MISS, false
            )
        // 【潜望镜一击必沉】中心第 2 格是潜艇唯一暴露点。
        // v12：不再限制 !ship.surfaced —— 上浮时三格都能被打，但中心格挨的任何一击
        // 依然直接判整艇沉没，与手册「任何命中中心格的攻击直接判整艇沉没」一致。
        // 判定特意放在「该格重复打击无效」之前：上浮期中心格若已挨过 1 炮，
        // 它会同时写进 hits 与 attackedPoints，等潜艇下潜后必沉判定就永久够不着了。
        // 只作用于 SUBMARINE，其他舰船的打击结算完全不变。
        if (ship.type == ShipType.SUBMARINE && ship.cells.size > 1 && at == ship.cells[1]) {
            val killed = ship.copy(hits = ship.hits + ship.cells)
            val nb = board.copy(
                ships = board.ships.map { if (it.id == ship.id) killed else it }
            ).record(at, ShotResult.SUNK, by, ship.type, mark)
            return ShotReport(
                nb, ShotResult.SUNK, "击沉敌方 ${FleetCatalog.label(ship.type)}！",
                LogKind.HIT, true, ship.type
            )
        }
        if (at in ship.hits) {
            return ShotReport(
                board.record(at, ShotResult.MISS, by, ship.type, mark),
                ShotResult.MISS, "该格重复打击无效", LogKind.MISS, false
            )
        }
        val updated = ship.copy(hits = ship.hits + at)
        val ships = board.ships.map { if (it.id == ship.id) updated else it }
        val result = if (updated.sunk) ShotResult.SUNK else ShotResult.HIT
        val nb = board.copy(ships = ships).record(at, result, by, ship.type, mark)
        // 战报情报规则：只有「击沉」才公开舰种；单纯命中一律只说「击中」，
        // 否则全局广播的 log 会直接告诉对手打中了他哪一艘船。
        val text = when {
            updated.sunk -> "击沉敌方 ${FleetCatalog.label(ship.type)}！"
            else -> "击中"
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
        // 十字范围攻击的「部署点」= 中心格：只在这里判一次区域防空，
        // 5 个伤害格绝不逐个判拦截（陆航空袭 ignoresAA = true，本就不进这个分支）。
        if (Rules.isAir(kind) && !ignoresAA && intercepted(board, listOf(center))) {
            logOwn(
                side, LogKind.AA,
                "部署点 ${coordLabel(center)} 落在敌方防空区，战机被击落；十字 5 格的其余格与爆炸点都不参与防空判定"
            )
            board = board.record(center, ShotResult.INTERCEPTED, side, null, false)
            setBoard(enemy, board)
            reportFailed(side, center, "攻击被拦截")
            return
        }
        val hitTypes = HashSet<ShipType>()
        for (c in cells) {
            // 部署点已在上面判过，范围伤害的每一格不再重复做防空判定。
            val rep = applyShot(board, c, kind, side, attackerShip, ignoresAA = true, mark = false)
            board = rep.board
            if (rep.damage) {
                addDamage(side, attackerShip)
                rep.shipHit?.let { hitTypes.add(it) }
            }
            reportAttack(side, c, rep)
        }
        if (disableAAOnHit && hitTypes.any { it == ShipType.CARRIER || it == ShipType.AA_CRUISER }) {
            // 「瘫痪 1 个敌方回合」= 让攻击方本回合剩余的空中行动不再被该舰拦截。
            // 之前 A / B 分别写 round 与 round+1，导致 B 刚打中却在自己剩余攻击里仍被拦截，
            // 是 v10 复核 AA 时查出的真实不对称 bug；现在双方统一写当前 round。
            val disabledRound = state.round
            board = board.copy(
                ships = board.ships.map { s ->
                    if (s.type in hitTypes && (s.type == ShipType.CARRIER || s.type == ShipType.AA_CRUISER))
                        s.copy(aaDisabledUntil = disabledRound) else s
                }
            )
            log(LogKind.SYSTEM, "${nameOf(side)} 的一次攻击造成短暂压制效果")
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
     * 【AA 圈外视距打击】防空判定只看「投雷起点」这一个部署点：
     * 飞机只要没有飞进防空圈就能投雷，即便鱼雷的爆炸点落在 AA 舰 / 航母身上也照常结算。
     * 航线中间穿过防空区也绝不判战机被击落。
     */
    private fun applyTorpedoBomberLine(side: Side, line: List<Coord>) {
        val enemy = side.other()
        var board = boardOf(enemy)
        val start = line.first()
        // 只判「投雷起点」这一个部署点。航线穿过防空区、爆炸点落在防空舰身上都不算被击落。
        if (intercepted(board, listOf(start))) {
            logOwn(
                side, LogKind.AA,
                "【鱼雷机空袭】第 1 点（投雷起点）${coordLabel(start)} 落在敌方防空区，战机被击落；请把第 1 点点在防空圈外，第 2 点只用来定航向（航线 / 炸点穿过防空区不算被击落，两点不要点反）"
            )
            board = board.record(start, ShotResult.INTERCEPTED, side, null, false)
            setBoard(enemy, board)
            reportFailed(side, start, "攻击被拦截")
            return
        }
        var impact = line.last()
        var invalid = false
        for (i in line.indices) {
            val c = line[i]
            if (c in board.mountains) {
                if (i == 0) invalid = true
                else impact = line[i - 1]
                break
            }
            if (board.ships.any { !it.sunk && c in it.cells }) {
                impact = c
                break
            }
        }
        if (invalid) {
            logOwn(side, LogKind.INFO, "本次攻击无效果：目标区域不可达")
            setBoard(enemy, board)
            return
        }
        val rep = applyShot(
            board, impact, AttackKind.TORPEDO, side,
            ShipType.AVIATION_CRUISER, ignoresAA = true, mark = false
        )
        board = rep.board
        reportAttack(side, impact, rep)
        if (rep.damage) addDamage(side, ShipType.AVIATION_CRUISER)
        setBoard(enemy, board)
    }

    private fun fire(side: Side, a: GameAction.Fire) {
        if (!isMyTurn(side)) return
        val me = state.players[side.ordinal]
        val ship = actionableShip(side, a.shipId) ?: return
        if (ship.id in me.actedShipIds) {
            logOwn(side, LogKind.INFO, "该舰本回合已行动")
            return
        }
        if (state.turnPhase != TurnPhase.ATTACK) {
            logOwn(side, LogKind.INFO, "请先点击「进入阶段二 · 开始攻击」，再选择开火坐标")
            return
        }
        val budget = Rules.attacksPerTurn(me.board.ships.count { !it.sunk })
        if (me.actedShipIds.size >= budget) {
            logOwn(side, LogKind.INFO, "本回合 $budget 次攻击名额已用尽，请点击「结束回合」")
            return
        }
        val enemy = side.other()
        val eb = boardOf(enemy)
        val t = a.target
        if (!t.inBounds(eb.size) || t in eb.mountains) {
            logOwn(side, LogKind.INFO, "目标非法：越界或为不可攻击的山地")
            return
        }
        if (ship.type == ShipType.SUBMARINE) {
            fireTorpedo(side, ship, t)
            return
        }
        if (t in eb.attackedPoints) {
            logOwn(side, LogKind.INFO, "该坐标已攻击过（单点攻击去重），请换一个格子")
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
        reportAttack(side, t, rep)
        if (rep.damage) addDamage(side, ship.type)
        markActed(side, ship.id)
        checkGameOver()
    }

    /**
     * 潜航鱼雷：玩家点的坐标只用来「定航向」，鱼雷沿该方向推进，只找唯一爆炸点、只结算一次伤害：
     *   ① 碰到的第一艘未沉敌舰所在格；② 前方是山地 / 已炮击过的格子时取其前一格；③ 射程尽头一格。
     *
     * 【同向递减射程】每个方向各自记一本台账：首次可推进 4 格，之后每成功发射一次，
     * 该方向的最大推进格数减 1，于是连射依次落在 4 / 3 / 2 / 1 格处，归零后该方向不能再发射
     * （换一个方向仍可发射）。这样既符合「后面同方向的鱼雷应在前面最远坐标少一格处引爆」，
     * 也天然避免了同一格被多枚鱼雷重复结算。
     * 【已被炮击过的方向】爆炸点会写进 attackedPoints（mark = true），后续同向鱼雷遇到
     * 已被炮击 / 已炸过的格子立即截断，在它的前一格引爆；若第一格就被封死则整次发射无效，
     * 不消耗行动、不触发强制上浮。
     *
     * 沿途不写 ShotRecord、不画轨迹，棋盘上只留爆炸点一个标记；中间格后续仍可被火炮打击。
     * 日志不播报潜艇中心坐标，对手无法顺着一串水花反推出潜望镜所在格。
     */
    private fun fireTorpedo(side: Side, ship: Ship, target: Coord) {
        val center = if (ship.cells.size > 1) ship.cells[1] else ship.cells[0]
        if (target.r != center.r && target.c != center.c) {
            logOwn(side, LogKind.INFO, "鱼雷只能沿潜艇所在行或列的直线方向发射")
            return
        }
        if (target == center || target.manhattan(center) > Rules.TORPEDO_MAX_STEPS) {
            logOwn(side, LogKind.INFO, "鱼雷航向点须在直线 ${Rules.TORPEDO_MAX_STEPS} 格内")
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
        val lane = Rules.torpedoLaneKey(dr, dc)
        val reach = Rules.torpedoReach(ship, dr, dc)
        if (reach <= 0) {
            logOwn(
                side, LogKind.INFO,
                "该方向鱼雷射程已按 4→3→2→1 用尽（剩余 0 格），请换一个航向；本次未消耗行动"
            )
            return
        }
        val enemy = side.other()
        var board = boardOf(enemy)
        val line = ArrayList<Coord>(Rules.TORPEDO_MAX_STEPS)
        var blockedByMountain = false
        var blockedByFired = false
        for (step in 1..reach) {
            val c = Coord(center.r + dr * step, center.c + dc * step)
            if (!c.inBounds(board.size)) break
            if (c in board.mountains) {
                blockedByMountain = true
                break
            }
            // 这条航向上已有炮击 / 前次鱼雷炸点：鱼雷不能穿过去，在其前一格引爆。
            if (c in board.attackedPoints) {
                blockedByFired = true
                break
            }
            line.add(c)
        }
        if (line.isEmpty()) {
            logOwn(
                side, LogKind.INFO,
                "本次发射无效果：前方第一格即山地 / 边界 / 已被炮击过，未消耗行动（该方向剩余射程仍为 $reach 格）"
            )
            return
        }
        // 发射细节只回给操作者：一旦全局广播「有鱼雷出击」，对手就能从炸点反推发射源
        logOwn(
            side, LogKind.SKILL,
            "已发射潜航鱼雷：本次最远推进 ${line.size} 格，该方向下发射程将降为 ${line.size - 1} 格"
        )
        var impact = line.last()
        for (c in line) {
            if (board.ships.any { s -> !s.sunk && c in s.cells }) {
                impact = c
                break
            }
        }
        when {
            blockedByMountain -> logOwn(
                side, LogKind.INFO,
                "航线前方有山地遮挡，鱼雷在撞山前一格 ${coordLabel(line.last())} 引爆"
            )
            blockedByFired -> logOwn(
                side, LogKind.INFO,
                "航线前方已有炮击 / 炸点，鱼雷在其前一格 ${coordLabel(line.last())} 引爆"
            )
        }
        // 炸点写进坐标去重集合：防止同格重复结算，也让下一枚同向鱼雷自动少推进一格。
        val rep = applyShot(
            board, impact, AttackKind.TORPEDO, side,
            ShipType.SUBMARINE, ignoresAA = true, mark = true
        )
        board = rep.board
        reportAttack(side, impact, rep)
        if (rep.damage) addDamage(side, ShipType.SUBMARINE)
        setBoard(enemy, board)
        // 递减台账落在自己棋盘的潜艇上（viewFor 对敌方棋盘只下发已沉舰，不泄露）。
        val nextReach = (line.size - 1).coerceAtLeast(0)
        updatePlayer(side) { p ->
            p.copy(
                board = p.board.copy(
                    ships = p.board.ships.map { s ->
                        if (s.id == ship.id) s.copy(torpedoLanes = s.torpedoLanes + (lane to nextReach))
                        else s
                    }
                ),
                subSurfaceNextTurn = true
            )
        }
        markActed(side, ship.id)
        logOwn(side, LogKind.SYSTEM, "本舰下 1 回合将强制上浮")
        checkGameOver()
    }

    /* ==================== 技能 ==================== */

    private fun useSkill(side: Side, a: GameAction.Skill) {
        if (!isMyTurn(side)) return
        val me = state.players[side.ordinal]
        val ship = actionableShip(side, a.shipId) ?: return
        val kind = a.kind
        if (Rules.skillFor(ship.type) != kind) {
            logOwn(side, LogKind.INFO, "该舰不具备此技能")
            return
        }
        val tactical = Rules.isTactical(kind)
        if (tactical) {
            if (state.turnPhase != TurnPhase.TACTICAL || me.tacticalUsed) {
                logOwn(side, LogKind.INFO, "阶段一已结束或本回合战术行动已使用")
                return
            }
        } else {
            if (ship.id in me.actedShipIds) {
                logOwn(side, LogKind.INFO, "该舰本回合已行动")
                return
            }
            if (state.turnPhase != TurnPhase.ATTACK) {
                logOwn(side, LogKind.INFO, "请先点击「进入阶段二 · 开始攻击」，再释放攻击技能")
                return
            }
            val budget = Rules.attacksPerTurn(me.board.ships.count { !it.sunk })
            if (me.actedShipIds.size >= budget) {
                logOwn(side, LogKind.INFO, "本回合 $budget 次攻击名额已用尽")
                return
            }
        }
        if (state.round < Rules.skillMinRound(kind)) {
            logOwn(side, LogKind.INFO, "【${Rules.skillLabel(kind)}】第 ${Rules.skillMinRound(kind)} 回合起才可使用")
            return
        }
        val readyRound = me.cooldowns[kind.name] ?: 0
        if (state.round < readyRound) {
            logOwn(side, LogKind.INFO, "技能冷却中，第 $readyRound 回合可再次使用")
            return
        }
        val used = me.useCounts[kind.name] ?: 0
        if (used >= Rules.skillLimit(kind)) {
            logOwn(side, LogKind.INFO, "【${Rules.skillLabel(kind)}】全局次数已用尽")
            return
        }
        val need = Rules.skillTargetCount(kind)
        val center = a.targets.firstOrNull() ?: Coord(0, 0)
        if (need > 0) {
            if (a.targets.isEmpty()) {
                logOwn(side, LogKind.INFO, "缺少目标坐标")
                return
            }
            if (!center.inBounds(boardOf(side.other()).size)) {
                logOwn(side, LogKind.INFO, "目标坐标越界")
                return
            }
        }

        when (kind) {
            SkillKind.RECON -> {
                val eb = boardOf(side.other())
                val cells = crossCells(center, eb.size)
                // 【侦察范围 ≠ 击落范围】只判「部署点」，被扫描的 5 格哪怕擦到防空区也不算击落。
                if (intercepted(eb, listOf(center))) {
                    logOwn(
                        side, LogKind.AA,
                        "【舰载侦察机】部署点 ${coordLabel(center)} 位于敌方防空区，战机被击落（只按部署点判定，扫描范围不影响）"
                    )
                } else {
                    val n = eb.ships.count { s ->
                        !s.sunk && (when {
                            s.type != ShipType.SUBMARINE -> s.cells.any { it in cells }
                            s.surfaced -> s.cells.any { it in cells }
                            // 水下潜艇：潜望镜（中心第 2 格）进入扫描区即视为发现，
                            // 与是否上浮无关，也不因为首尾格被扫到就报错。
                            else -> (if (s.cells.size > 1) s.cells[1] else s.cells[0]) in cells
                        })
                    }
                    // 扫描成功才留痕：只记录「哪 5 格被扫过」，不记录船的位置，
                    // 棋盘据此画放大镜标记；撤销 / 重做随 state 快照自然回退。
                    setBoard(side.other(), eb.copy(reconCells = eb.reconCells + cells))
                    // 侦察结果属于己方情报，绝不广播给对手；棋盘放大镜标记仍会留在敌方海域
                    logOwn(
                        side, LogKind.SKILL,
                        "【舰载侦察机】@${coordLabel(center)}：十字 5 格扫描完成，发现 ${if (n == 0) "0" else "$n"} 个舰艇信号（含水下潜望镜，仅报数量，不报坐标）"
                    )
                }
            }
            SkillKind.SONAR -> {
                val eb = boardOf(side.other())
                val cells = Rules.hydrophoneCells(center, eb.size, eb.mountains)
                val n = eb.ships.count { s ->
                    !s.sunk && s.type == ShipType.SUBMARINE && s.cells.any { it in cells }
                }
                setBoard(side.other(), eb.copy(sonarCells = eb.sonarCells + cells))
                logOwn(
                    side, LogKind.SKILL,
                    "【主动水听声呐】@${coordLabel(center)}：扫描 ${cells.size} 格（3×3 + 四边外突 1 格，山地遮挡已扣除），发现潜艇信号 ${if (n == 0) "0" else "$n"} 个（仅报数量）"
                )
            }
            SkillKind.RADAR_SURVEILLANCE -> {
                val eb = boardOf(side.other())
                val hull = ship.cells
                val cells = Rules.radarCells(hull, eb.size, eb.mountains)
                // 只探测水面舰艇与已上浮潜艇；水下潜艇的潜望镜不算水面目标。
                val n = eb.ships.count { s ->
                    !s.sunk && (s.type != ShipType.SUBMARINE || s.surfaced) &&
                        s.cells.any { it in cells }
                }
                setBoard(side.other(), eb.copy(radarCells = eb.radarCells + cells))
                logOwn(
                    side, LogKind.SKILL,
                    "【监视雷达】以本舰 ${hull.size} 格船体为中心扫描 ${cells.size} 格（射程 ${Rules.radarRange(eb.size)}，山地遮蔽已扣除），覆盖区内水面舰艇 ${if (n == 0) "0" else "$n"} 条（仅报数量，不报坐标 / 舰种）"
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
                    logOwn(side, LogKind.INFO, "紧急规避机动失败：$err")
                    return
                }
                val kept = ship.cells.mapIndexedNotNull { i, c -> if (c in ship.hits) i else null }
                val newHits = kept.mapNotNull { newCells.getOrNull(it) }.toSet()
                val moved = ship.copy(origin = center, orientation = newO, cells = newCells, hits = newHits)
                setBoard(side, own.copy(ships = own.ships.map { if (it.id == ship.id) moved else it }))
                log(LogKind.SYSTEM, "${nameOf(side)} 一艘舰船已变更部署")
            }
            SkillKind.SALVO -> {
                if (a.targets.size < 2) {
                    logOwn(side, LogKind.INFO, "主炮齐射需要选择 2 个不同坐标")
                    return
                }
                val p1 = a.targets[0]
                val p2 = a.targets[1]
                val eb = boardOf(side.other())
                if (p1 == p2 || p1.chebyshev(p2) > 4) {
                    logOwn(side, LogKind.INFO, "两发需为不同坐标且间距 ≤ 4 格")
                    return
                }
                if (listOf(p1, p2).any { !it.inBounds(eb.size) || it in eb.mountains || it in eb.attackedPoints }) {
                    logOwn(side, LogKind.INFO, "齐射坐标非法（山地 / 越界 / 已攻击过）")
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
                    reportAttack(side, t, rep)
                    if (rep.damage) addDamage(side, ShipType.BATTLESHIP)
                }
                setBoard(side.other(), board)
            }
            SkillKind.TORPEDO_BOMBER -> {
                if (a.targets.size < 2) {
                    logOwn(side, LogKind.INFO, "【鱼雷机空袭】需先点突防起点、再点同行或同列的一格指定航向")
                    return
                }
                val eb0 = boardOf(side.other())
                val p0 = a.targets[0]
                val p1 = a.targets[1]
                if (!p0.inBounds(eb0.size) || !p1.inBounds(eb0.size) ||
                    p0 in eb0.mountains || p1 in eb0.mountains
                ) {
                    logOwn(side, LogKind.INFO, "【鱼雷机空袭】起点 / 航向点不能是山地或越界，未消耗行动")
                    return
                }
                val line = Rules.torpedoBomberLine(p0, p1, eb0.size)
                if (line.size < 2) {
                    logOwn(side, LogKind.INFO, "【鱼雷机空袭】航向非法：第 2 个点必须与起点同行或同列")
                    return
                }
                if (state.turnPhase == TurnPhase.TACTICAL) {
                    state = state.copy(turnPhase = TurnPhase.ATTACK)
                    revision++
                }
                logOwn(
                    side, LogKind.SKILL,
                    "【鱼雷机空袭】已投雷，从起点向前推进 ${line.size - 1} 格（航线共 ${line.size} 格），只在爆炸点结算"
                )
                applyTorpedoBomberLine(side, line)
            }
            SkillKind.INCENDIARY -> {
                if (state.turnPhase == TurnPhase.TACTICAL) {
                    state = state.copy(turnPhase = TurnPhase.ATTACK)
                    revision++
                }
                logOwn(side, LogKind.SKILL, "【高能燃烧弹】已发射 @${coordLabel(center)}")
                applyCross(side, center, AttackKind.INCENDIARY, ShipType.FIRE_CRUISER, true, true)
            }
            SkillKind.AIR_STRIKE_CALL -> {
                if (state.turnPhase == TurnPhase.TACTICAL) {
                    state = state.copy(turnPhase = TurnPhase.ATTACK)
                    revision++
                }
                updatePlayer(side) { it.copy(pendingStrike = PendingStrike(center, state.round)) }
                val eb0 = boardOf(side.other())
                setBoard(side.other(), eb0.copy(strikeCells = eb0.strikeCells + crossCells(center, eb0.size)))
                logOwn(side, LogKind.SKILL, "已标记 ${coordLabel(center)}，将在己方下一回合开始时生效")
                log(LogKind.SYSTEM, "${nameOf(side)} 已标记 ${coordLabel(center)}，该区域攻击将在其下一回合生效")
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

    /** 单点攻击候选：排除山地与已经真正打过的坐标。 */
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

    /** 情报 / 部署类候选：只排除山地，绝不因为「侦察过 / 被拦截过 / 打过」而屏蔽。 */
    fun intelTargetsFor(side: Side): List<Coord> {
        val b = boardOf(side.other())
        val out = ArrayList<Coord>()
        for (r in 0 until b.size) {
            for (c in 0 until b.size) {
                val p = Coord(r, c)
                if (p !in b.mountains) out.add(p)
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
        // 战报情报脱敏：ShotRecord.shipType（被击中舰种）/ attacker（开火舰种）
        // 一律不下发，命中只以「击中」呈现；击沉信息由已沉舰 ships / enemySunkTypes 公开。
        // engine 内部 state 保持完整，【上浮暴露复核】等逻辑完全不受影响。
        val cleanShots = { shots: List<ShotRecord> ->
            shots.map { rec ->
                if (rec.shipType == null && rec.attacker == null) rec
                else rec.copy(shipType = null, attacker = null)
            }
        }
        val redacted = enemy.board.copy(
            ships = enemy.board.ships.filter { it.sunk },
            shots = cleanShots(enemy.board.shots)
        )
        // 敌方选了哪些舰、还剩哪几艘，都属于战前情报：报文里也不下发。
        // 大厅 / 棋盘只显示「我方编成」和「敌方剩余数量」。
        val hiddenFleet = FleetConfig()
        val enemyAliveCount = enemy.board.ships.count { !it.sunk }
        // 四种情报覆盖层是「扫描者视角」：只应出现在扫描者的敌方海域。
        // 自己棋盘上那一层其实是对方扫描留下的，必须抹掉，否则等于告诉对手你在看哪里。
        val myBoardView = me.board.copy(
            shots = cleanShots(me.board.shots),
            reconCells = emptySet(),
            sonarCells = emptySet(),
            radarCells = emptySet(),
            strikeCells = emptySet()
        )
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
            hostFleet = if (side == Side.A) me.fleet else hiddenFleet,
            guestFleet = if (side == Side.B) me.fleet else hiddenFleet,
            myBoard = myBoardView,
            enemyBoard = redacted,
            enemySunkTypes = enemy.board.ships.filter { it.sunk }.map { it.type },
            myAlive = me.board.ships.filter { !it.sunk }.map { it.type },
            enemyAlive = emptyList(),
            enemyAliveCount = enemyAliveCount,
            legalTargets = if (s.phase == RoomPhase.IN_BATTLE && s.turn == side) legalTargetsFor(side) else emptyList(),
            intelTargets = if (s.phase == RoomPhase.IN_BATTLE && s.turn == side) intelTargetsFor(side) else emptyList(),
            skills = skillAvailabilities(side),
            myActed = me.actedShipIds.toList(),
            log = s.log.filter { it.owner == null || it.owner == side },
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
            reportAttack(side, t, rep, "超时托管")
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
