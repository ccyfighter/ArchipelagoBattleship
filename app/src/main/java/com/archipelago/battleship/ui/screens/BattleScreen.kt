package com.archipelago.battleship.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.archipelago.battleship.ui.components.BoardLegend
import com.archipelago.battleship.ui.components.ChipButton
import com.archipelago.battleship.ui.components.InfoLine
import com.archipelago.battleship.ui.components.LogList
import com.archipelago.battleship.ui.components.ResultBanner
import com.archipelago.battleship.ui.components.SectionTitle
import com.archipelago.battleship.ui.components.StatPill
import com.archipelago.battleship.ui.components.SurfaceCard

private fun subCenter(ship: Ship): Coord =
    if (ship.cells.size > 1) ship.cells[1] else ship.cells[0]

/** 潜艇鱼雷：以潜艇中心为原点，同行 / 同列 4 格内的可用发射方向格。 */
private fun torpedoCandidates(ship: Ship, enemy: Board): List<Coord> {
    val c0 = subCenter(ship)
    val out = ArrayList<Coord>()
    for (step in 1..4) {
        for (d in listOf(Coord(-1, 0), Coord(1, 0), Coord(0, -1), Coord(0, 1))) {
            val c = Coord(c0.r + d.r * step, c0.c + d.c * step)
            // 只定方向：鱼雷会穿过已打过的格子（沿途不再写攻击记录），所以这里不再排除 attackedPoints
            if (c.inBounds(enemy.size) && c !in enemy.mountains) out.add(c)
        }
    }
    return out.distinct()
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
    else if (pendingSkill != null) {
        val sk = pendingSkill!!
        if (sk.kind == SkillKind.TORPEDO_BOMBER && skillTargets.size == 1) {
            // 第 2 个点只用于定航向：只提示射程内（2 格）的同行 / 同列格，不画整行整列的长轨迹
            val s0 = skillTargets[0]
            val n = view.enemyBoard.size
            (1..2).flatMap { step ->
                listOf(
                    Coord(s0.r, s0.c + step), Coord(s0.r, s0.c - step),
                    Coord(s0.r + step, s0.c), Coord(s0.r - step, s0.c)
                )
            }.filter { it.inBounds(n) && it !in view.enemyBoard.mountains }.distinct()
        } else view.legalTargets
    }
    else if (tacticPhase) emptyList()
    else if (sub == null) emptyList()
    else if (sub.type == ShipType.SUBMARINE) torpedoCandidates(sub, view.enemyBoard)
    else view.legalTargets
    // 两种鱼雷都不画航线：棋盘上只保留「定方向点」的高亮与序号框。
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
            .padding(12.dp)
    ) {
        SectionTitle("战斗阶段 · IN_BATTLE（战斗禁言）")

        SurfaceCard(accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "第 ${view.round} 回合",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    if (myTurn) "  ·  轮到你行动" else "  ·  对方行动中",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
            Text(
                if (tacticPhase) "阶段一 · 可选战术行动" else "阶段二 · 多船协同攻击",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatPill("攻击", "${view.attackUsed}/${view.attackBudget}",
                    if (budgetLeft == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    Modifier.weight(1f))
                StatPill("我方", "${view.myAlive.size}", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                StatPill("敌方", "${view.enemyAlive.size}", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                StatPill("击沉", "${view.enemySunkTypes.size}", MaterialTheme.colorScheme.error, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            val fraction = (state.timerSeconds.toFloat() / Rules.TURN_SECONDS.toFloat()).coerceIn(0f, 1f)
            val timerColor = if (fraction < 0.2f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("回合倒计时", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Spacer(Modifier.weight(1f))
                Text("${state.timerSeconds}s / ${Rules.TURN_SECONDS}s", fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = timerColor)
            }
            Spacer(Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(timerColor)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        ResultBanner(view.log)
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
            pendingSkill?.kind == SkillKind.TORPEDO_BOMBER ->
                if (skillTargets.isEmpty())
                    "鱼雷机：第 1 点点投雷起点（须在敌方防空圈外），第 2 点点同行/同列一格只定航向；鱼雷最多推进 3 格，在「碰到敌舰 / 撞山前一格 / 射程尽头」单点爆炸，沿途不留轨迹、中间格仍可再打"
                else
                    "鱼雷机：已选起点 ${coordLabel(skillTargets[0])}，再点同行/同列一格定航向（已选 ${skillTargets.size}/$need）；起点在 AA 圈外即可打击防空舰，只显示选点、不画航线"
            pendingSkill != null ->
                "技能【${pendingSkill!!.label}】已选 ${skillTargets.size}/$need：${skillTargets.joinToString("、") { coordLabel(it) }}；点棋盘可改，按「确认释放」才生效"
            sub != null && sub.type == ShipType.SUBMARINE ->
                "潜艇鱼雷：以中心 ${coordLabel(subCenter(sub))} 为准，点同行 / 同列 4 格内任意格只定航向；鱼雷最多推进 4 格，在「碰到敌舰 / 撞山前一格 / 射程尽头」单点爆炸，不画航线、中间格仍可再打"
            sub != null ->
                "已选【${FleetCatalog.label(sub.type)}】· ${if (pt != null) "暂存目标 ${coordLabel(pt)}" else "点击敌方海域格子选择目标"}；确认前可反复修改"
            else -> "先选择一艘开火舰船，或选择一个技能"
        }
        Spacer(Modifier.height(6.dp))
        SurfaceCard(accent = MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)) {
            InfoLine(hint)
        }
        Spacer(Modifier.height(8.dp))

        BoardCanvas(
            board = board,
            mode = state.theme,
            interactive = myTurn,
            legalTargets = if (showEnemy) candidates else emptyList(),
            highlight = if (showEnemy) highlight else null,
            markers = skillTargets,
            modifier = Modifier.fillMaxWidth().padding(2.dp),
            onTap = { onTap(it) }
        )
        Spacer(Modifier.height(8.dp))
        BoardLegend(state.theme)
        InfoLine("深色标尺带为坐标：横向 A… / 纵向 1…；选中目标时行列标签同步高亮；侦察机扫过的格子会留下放大镜标记。")

        Spacer(Modifier.height(8.dp))
        // ---- 操作确认区：所有动作都显式确认，不自动推进 ----
        SurfaceCard {
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
        // 双保险：即便状态帧有延迟，已沉没舰船的技能也绝不出现在可点列表里
        val availableSkills = view.skills.filter { s -> aliveShips.any { it.id == s.shipId } }
        LaunchedEffect(view.revision) {
            if (selectedShipId != null && aliveShips.none { it.id == selectedShipId }) {
                selectedShipId = null
                clearDraft()
            }
            val sk = pendingSkill
            if (sk != null && availableSkills.none { it.shipId == sk.shipId && it.kind == sk.kind }) {
                clearDraft()
            }
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            availableSkills.forEach { s ->
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
        InfoLine("主炮齐射需点 2 个坐标；鱼雷机需点「起点 + 同行/同列航向点」共 2 点，起点在防空圈外即可打击 AA 舰。两种鱼雷都只在一个点爆炸、只结算一次（可命中水下潜艇），沿途不留轨迹、不锁格，中间格后续仍可用火炮打击；单点攻击自动排除已打过的格子。")
        InfoLine("战报情报：命中只播报「击中」，不公开被击中的舰种；只有击沉才报舰种。射击记录中的舰种字段也不会随联机状态下发。")

        Spacer(Modifier.height(8.dp))
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
