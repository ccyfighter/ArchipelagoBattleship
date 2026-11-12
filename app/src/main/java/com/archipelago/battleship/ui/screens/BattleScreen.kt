package com.archipelago.battleship.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archipelago.battleship.core.BoardCanvas
import com.archipelago.battleship.domain.engine.Rules
import com.archipelago.battleship.domain.model.Board
import com.archipelago.battleship.domain.model.Coord
import com.archipelago.battleship.domain.model.FleetCatalog
import com.archipelago.battleship.domain.model.RoomPhase
import com.archipelago.battleship.domain.model.Ship
import com.archipelago.battleship.domain.model.ShipType
import com.archipelago.battleship.domain.model.SkillAvailability
import com.archipelago.battleship.domain.model.SkillKind
import com.archipelago.battleship.domain.model.TurnPhase
import com.archipelago.battleship.domain.model.coordLabel
import com.archipelago.battleship.domain.model.inBounds
import com.archipelago.battleship.ui.GameViewModel
import com.archipelago.battleship.ui.UiState
import com.archipelago.battleship.ui.components.ChipButton
import com.archipelago.battleship.ui.components.InfoLine
import com.archipelago.battleship.ui.components.LogList
import com.archipelago.battleship.ui.components.ResultBanner
import com.archipelago.battleship.ui.components.SectionTitle

private fun subCenter(ship: Ship): Coord =
    if (ship.cells.size > 1) ship.cells[1] else ship.cells[0]

/** 潜艇鱼雷：以潜艇中心为原点，同行 / 同列 4 格内的可用发射方向格。 */
private fun torpedoCandidates(ship: Ship, enemy: Board): List<Coord> {
    val c0 = subCenter(ship)
    val out = ArrayList<Coord>()
    for (step in 1..4) {
        for (d in listOf(Coord(-1, 0), Coord(1, 0), Coord(0, -1), Coord(0, 1))) {
            val c = Coord(c0.r + d.r * step, c0.c + d.c * step)
            if (c.inBounds(enemy.size) && c !in enemy.mountains && c !in enemy.attackedPoints) out.add(c)
        }
    }
    return out.distinct()
}

/** 从潜艇中心沿所选方向推进到目标，用于棋盘上的路径线 + 箭头。 */
private fun torpedoPath(ship: Ship, target: Coord): List<Coord> {
    val c0 = subCenter(ship)
    val dr = when { target.r > c0.r -> 1; target.r < c0.r -> -1; else -> 0 }
    val dc = when { target.c > c0.c -> 1; target.c < c0.c -> -1; else -> 0 }
    if (dr == 0 && dc == 0) return emptyList()
    val out = ArrayList<Coord>()
    out.add(c0)
    for (step in 1..4) {
        val c = Coord(c0.r + dr * step, c0.c + dc * step)
        out.add(c)
        if (c == target) break
    }
    return out
}

@Composable
fun BattleScreen(vm: GameViewModel, state: UiState) {
    val view = state.view ?: return

    var showEnemy by remember { mutableStateOf(true) }
    var selectedShipId by remember { mutableStateOf<String?>(null) }
    var pendingSkill by remember { mutableStateOf<SkillAvailability?>(null) }
    var skillTargets by remember { mutableStateOf<List<Coord>>(emptyList()) }
    var previewTarget by remember { mutableStateOf<Coord?>(null) }

    val myTurn = view.turn == view.viewer && view.phase == RoomPhase.IN_BATTLE
    val aliveShips = view.myBoard.ships.filter { !it.sunk }
    val board = if (showEnemy) view.enemyBoard else view.myBoard
    val need = pendingSkill?.let { Rules.skillTargetCount(it.kind) } ?: 0
    val tacticPhase = view.turnPhase == TurnPhase.TACTICAL
    val selectedShip = aliveShips.firstOrNull { it.id == selectedShipId }
    val budgetLeft = (view.attackBudget - view.attackUsed).coerceAtLeast(0)
    val pt = previewTarget
    val sub = selectedShip

    val candidates: List<Coord> = if (!myTurn) emptyList()
    else if (pendingSkill != null) view.legalTargets
    else if (tacticPhase) emptyList()
    else if (sub == null) emptyList()
    else if (sub.type == ShipType.SUBMARINE) torpedoCandidates(sub, view.enemyBoard)
    else view.legalTargets
    val path: List<Coord> =
        if (sub != null && sub.type == ShipType.SUBMARINE && pt != null && showEnemy) torpedoPath(sub, pt)
        else emptyList()
    val highlight: Coord? = when {
        skillTargets.isNotEmpty() -> skillTargets.last()
        pt != null && showEnemy -> pt
        else -> null
    }
    val canFire = myTurn && !tacticPhase && sub != null &&
        sub.id !in view.myActed && budgetLeft > 0 && pt != null

    fun clearDraft() {
        pendingSkill = null
        skillTargets = emptyList()
        previewTarget = null
    }

    fun onTap(c: Coord) {
        if (!myTurn) return
        val skill = pendingSkill
        if (skill != null) {
            skillTargets = if (skillTargets.size < need) skillTargets + c
            else skillTargets.dropLast(1) + c
            return
        }
        if (!showEnemy) {
            showEnemy = true
            return
        }
        if (selectedShip == null) return
        // 只暂存目标，不结算：点确认之前可以反复改选，绝不自动跳到下一艘船。
        previewTarget = c
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp)
    ) {
        SectionTitle("战斗阶段 · IN_BATTLE（战斗禁言）")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "第 ${view.round} 回合 · ${if (myTurn) "轮到你行动" else "对方行动中"}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    if (tacticPhase) "阶段一 · 可选战术行动" else "阶段二 · 多船协同攻击",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                "攻击名额 ${view.attackUsed}/${view.attackBudget}",
                fontSize = 13.sp,
                color = if (budgetLeft == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
            )
        }

        InfoLine("我方存活 ${view.myAlive.size} 艘 ｜ 敌方存活 ${view.enemyAlive.size} 艘 ｜ 敌方已击沉 ${view.enemySunkTypes.size} 艘")
        ResultBanner(view.log)

        val fraction = (state.timerSeconds.toFloat() / Rules.TURN_SECONDS.toFloat()).coerceIn(0f, 1f)
        Text(
            "回合倒计时 ${state.timerSeconds}s / ${Rules.TURN_SECONDS}s",
            fontSize = 12.sp,
            color = if (fraction < 0.2f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(7.dp)
                    .background(if (fraction < 0.2f) Color(0xFFE53935) else MaterialTheme.colorScheme.primary)
            )
        }
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            ChipButton("敌方海域", showEnemy, true) {
                showEnemy = true
                previewTarget = null
            }
            ChipButton("我方海域", !showEnemy, true) {
                showEnemy = false
                previewTarget = null
            }
        }

        val hint = when {
            pendingSkill != null ->
                "技能【${pendingSkill!!.label}】已选 ${skillTargets.size}/$need：${skillTargets.joinToString("、") { coordLabel(it) }}；点棋盘可改，按「确认释放」才生效"
            sub != null && sub.type == ShipType.SUBMARINE ->
                "潜艇鱼雷：只能沿潜艇中心 ${coordLabel(subCenter(sub))} 的同行 / 同列 4 格内发射，棋盘已高亮方向与路径"
            sub != null ->
                "已选【${FleetCatalog.label(sub.type)}】· ${if (pt != null) "暂存目标 ${coordLabel(pt)}" else "点击敌方海域格子选择目标"}；确认前可反复修改"
            else -> "先选择一艘开火舰船，或选择一个技能"
        }
        InfoLine(hint)

        BoardCanvas(
            board = board,
            mode = state.theme,
            interactive = myTurn,
            legalTargets = if (showEnemy) candidates else emptyList(),
            highlight = if (showEnemy) highlight else null,
            pathCells = path,
            markers = skillTargets,
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            onTap = { onTap(it) }
        )
        InfoLine("图例：○ 未命中 ｜ X 命中 ｜ S 击沉 ｜ AA 战机被击落 ｜ - 免疫/阻挡 ｜ 金色呼吸框=最近一次攻击")

        // ---- 操作确认区：所有动作都显式确认，不自动推进 ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                .padding(8.dp)
        ) {
            if (pendingSkill != null) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val sk = pendingSkill
                            if (sk != null && skillTargets.size >= need) {
                                vm.useSkill(sk.shipId, sk.kind, skillTargets, null)
                                clearDraft()
                            }
                        },
                        enabled = myTurn && skillTargets.size >= need,
                        modifier = Modifier.weight(1f).padding(end = 3.dp)
                    ) { Text("确认释放技能 ${skillTargets.size}/$need", fontSize = 11.sp) }
                    OutlinedButton(
                        onClick = { skillTargets = emptyList() },
                        modifier = Modifier.padding(end = 3.dp)
                    ) { Text("清空目标", fontSize = 11.sp) }
                    OutlinedButton(onClick = { clearDraft() }) { Text("取消技能", fontSize = 11.sp) }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val id = selectedShipId
                            val t = previewTarget
                            if (id != null && t != null) {
                                vm.fire(id, t)
                                previewTarget = null
                            }
                        },
                        enabled = canFire,
                        modifier = Modifier.weight(1f).padding(end = 3.dp)
                    ) {
                        Text(
                            if (pt != null) "确认开火 @${coordLabel(pt)}" else "确认开火",
                            fontSize = 11.sp
                        )
                    }
                    OutlinedButton(
                        onClick = { previewTarget = null },
                        enabled = pt != null
                    ) { Text("改选目标", fontSize = 11.sp) }
                }
            }
            if (tacticPhase && myTurn) {
                InfoLine("当前处于阶段一：可释放 1 次战术技能，或点「进入阶段二」后才能开火。")
            }
            if (budgetLeft == 0 && !tacticPhase && myTurn) {
                InfoLine("本回合攻击名额已用尽，请点「结束回合」；也可用「撤销」回退后重选。")
            }
        }

        SectionTitle("选择开火舰船（每艘每回合最多行动 1 次 · 本回合共 ${view.attackBudget} 次名额）")
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            aliveShips.forEach { ship ->
                val acted = ship.id in view.myActed
                ChipButton(
                    FleetCatalog.label(ship.type) + if (acted) " · 已行动" else "",
                    selectedShipId == ship.id,
                    !acted && myTurn
                ) {
                    selectedShipId = ship.id
                    clearDraft()
                    showEnemy = true
                }
            }
        }

        SectionTitle("舰船技能")
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            view.skills.forEach { s ->
                ChipButton(
                    s.label + if (s.enabled) "" else " · ${s.reason}",
                    pendingSkill?.shipId == s.shipId && pendingSkill?.kind == s.kind,
                    s.enabled && myTurn
                ) {
                    pendingSkill = s
                    skillTargets = emptyList()
                    previewTarget = null
                    selectedShipId = s.shipId
                    showEnemy = s.kind != SkillKind.EVASIVE_MANEUVER
                }
            }
        }
        InfoLine("主炮齐射需点 2 个坐标；十字类技能可重复覆盖；单点攻击自动排除已打过的格子。")

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { clearDraft(); vm.undo() },
                enabled = myTurn && view.canUndo,
                modifier = Modifier.weight(1f).padding(end = 2.dp)
            ) { Text("撤销", fontSize = 11.sp) }
            OutlinedButton(
                onClick = { clearDraft(); vm.redo() },
                enabled = myTurn && view.canRedo,
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
            ) { Text("重做", fontSize = 11.sp) }
            OutlinedButton(
                onClick = { clearDraft(); vm.skipTactical() },
                enabled = myTurn && tacticPhase,
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
            ) { Text("进入阶段二", fontSize = 11.sp) }
            Button(
                onClick = {
                    clearDraft()
                    selectedShipId = null
                    vm.endTurn()
                },
                enabled = myTurn,
                modifier = Modifier.weight(1f).padding(start = 2.dp)
            ) { Text("结束回合", fontSize = 11.sp) }
        }
        Spacer(Modifier.height(8.dp))

        SectionTitle("战况播报日志")
        LogList(view.log, Modifier.fillMaxWidth().height(150.dp))
        Spacer(Modifier.height(16.dp))
    }
}
