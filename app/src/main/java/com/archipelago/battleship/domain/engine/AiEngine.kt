package com.archipelago.battleship.domain.engine

import com.archipelago.battleship.domain.model.*
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 单机 AI：Hunt & Target 双状态机。
 * - HUNT：按棋盘奇偶parity在未攻击格中搜索，保证对 ≥2 格舰船的搜索效率。
 * - TARGET：命中且未击沉时把命中点四邻压入队列，逐格追打；击沉后清空队列回到 HUNT。
 * AI 只读取 engine.viewFor(B)（已脱敏视图），不读取权威状态里的敌方布阵。
 */
class AiEngine(private val rnd: Random = Random.Default) {

    private val pending = ArrayDeque<Coord>()

    fun reset() {
        pending.clear()
    }

    suspend fun playTurn(engine: GameEngine, side: Side, onStep: () -> Unit) {
        if (engine.state.phase != RoomPhase.IN_BATTLE || engine.state.turn != side) return
        engine.dispatch(side, GameAction.SkipTactical)
        onStep()
        delay(220)

        var guard = 0
        while (guard++ < 12) {
            if (engine.state.phase != RoomPhase.IN_BATTLE || engine.state.turn != side) break
            val view = engine.viewFor(side)
            val acted = engine.state.players[side.ordinal].actedShipIds
            if (acted.size >= Rules.attacksPerTurn(view.myBoard.ships.count { !it.sunk })) break
            val ship = view.myBoard.ships.firstOrNull { !it.sunk && it.id !in acted } ?: break
            val legal = view.legalTargets
            if (legal.isEmpty()) break

            val target: Coord = if (ship.type == ShipType.SUBMARINE) {
                val center = if (ship.cells.size > 1) ship.cells[1] else ship.cells[0]
                val aligned = legal.filter {
                    (it.r == center.r || it.c == center.c) && it.manhattan(center) in 1..4
                }
                aligned.randomOrNull(rnd) ?: break
            } else {
                pickTarget(legal) ?: break
            }

            val before = view.enemyBoard.shots.size
            engine.dispatch(side, GameAction.Fire(ship.id, target))
            onStep()

            val after = engine.viewFor(side)
            val last = after.enemyBoard.shots.drop(before).lastOrNull()
            if (last != null) {
                when (last.result) {
                    ShotResult.SUNK -> pending.clear()
                    ShotResult.HIT -> {
                        for (d in DIR4) {
                            val n = last.at.plus(d)
                            if (n.inBounds(after.config.size) && !pending.contains(n)) pending.addLast(n)
                        }
                    }
                    else -> Unit
                }
            }
            delay(300)
        }

        if (engine.state.phase == RoomPhase.IN_BATTLE && engine.state.turn == side) {
            engine.dispatch(side, GameAction.EndTurn)
            onStep()
        }
    }

    private fun pickTarget(legal: List<Coord>): Coord? {
        while (pending.isNotEmpty()) {
            val c = pending.removeFirst()
            if (c in legal) return c
        }
        val parity = legal.filter { (it.r + it.c) % 2 == 0 }
        val pool = parity.ifEmpty { legal }
        return pool.randomOrNull(rnd)
    }
}
