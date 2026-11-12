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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archipelago.battleship.core.BoardCanvas
import com.archipelago.battleship.domain.engine.Rules
import com.archipelago.battleship.domain.model.Coord
import com.archipelago.battleship.domain.model.FleetCatalog
import com.archipelago.battleship.domain.model.RoomPhase
import com.archipelago.battleship.domain.model.SkillAvailability
import com.archipelago.battleship.domain.model.SkillKind
import com.archipelago.battleship.domain.model.TurnPhase
import com.archipelago.battleship.domain.model.coordLabel
import com.archipelago.battleship.ui.GameViewModel
import com.archipelago.battleship.ui.UiState
import com.archipelago.battleship.ui.components.ChipButton
import com.archipelago.battleship.ui.components.InfoLine
import com.archipelago.battleship.ui.components.LogList
import com.archipelago.battleship.ui.components.SectionTitle

@Composable
fun BattleScreen(vm: GameViewModel, state: UiState) {
    val view = state.view ?: return

    var showEnemy by remember { mutableStateOf(true) }
    var selectedShipId by remember { mutableStateOf<String?>(null) }
    var pendingSkill by remember { mutableStateOf<SkillAvailability?>(null) }
    var skillTargets by remember { mutableStateOf<List<Coord>>(emptyList()) }
    var highlight by remember { mutableStateOf<Coord?>(null) }

    val myTurn = view.turn == view.viewer && view.phase == RoomPhase.IN_BATTLE
    val aliveShips = view.myBoard.ships.filter { !it.sunk }
    val board = if (showEnemy) view.enemyBoard else view.myBoard
    val need = pendingSkill?.let { Rules.skillTargetCount(it.kind) } ?: 0

    fun onTap(c: Coord) {
        if (!myTurn) return
        highlight = c
        val skill = pendingSkill
        if (skill != null) {
            val next = skillTargets + c
            if (next.size >= need) {
                vm.useSkill(skill.shipId, skill.kind, next, null)
                pendingSkill = null
                skillTargets = emptyList()
                showEnemy = true
            } else {
                skillTargets = next
            }
            return
        }
        if (!showEnemy) {
            showEnemy = true
            return
        }
        val shipId = selectedShipId ?: aliveShips.firstOrNull { it.id !in view.myActed }?.id
        if (shipId == null) return
        vm.fire(shipId, c)
        selectedShipId = aliveShips.firstOrNull { it.id != shipId && it.id !in view.myActed }?.id
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp)
    ) {
        SectionTitle("战斗阶段 · IN_BATTLE（战斗禁言）")
        InfoLine("第 ${view.round} 回合 · ${if (view.turn == view.viewer) "轮到你行动" else "对方行动中"} · ${if (view.turnPhase == TurnPhase.TACTICAL) "阶段一 可选战术行动" else "阶段二 多船协同攻击"}")
        InfoLine("我方存活 ${view.myAlive.size} 艘 ｜ 敌方存活 ${view.enemyAlive.size} 艘 ｜ 敌方已击沉 ${view.enemySunkTypes.size} 艘")
        InfoLine("聊天窗口已锁定／隐藏，界面专注棋盘、战况播报与 180s 回合倒计时。")

        val fraction = (state.timerSeconds.toFloat() / Rules.TURN_SECONDS.toFloat()).coerceIn(0f, 1f)
        Text(
            "回合倒计时 ${state.timerSeconds}s / ${Rules.TURN_SECONDS}s",
            fontSize = 13.sp,
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
            ChipButton("敌方海域（点击开火）", showEnemy) { showEnemy = true }
            ChipButton("我方海域（查看舰队）", !showEnemy) { showEnemy = false }
        }
        if (pendingSkill != null) {
            InfoLine("技能【${pendingSkill!!.label}】待选目标 ${skillTargets.size}/$need：${skillTargets.joinToString("、") { coordLabel(it) }}")
        } else if (skillTargets.isNotEmpty()) {
            InfoLine("已选目标：${skillTargets.joinToString("、") { coordLabel(it) }}")
        }

        BoardCanvas(
            board = board,
            mode = state.theme,
            interactive = myTurn,
            legalTargets = if (showEnemy && pendingSkill == null) view.legalTargets else emptyList(),
            highlight = highlight,
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            onTap = { onTap(it) }
        )

        SectionTitle("选择开火舰船（每艘每回合最多行动 1 次，共 ${aliveShips.size} 次名额）")
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            aliveShips.forEach { ship ->
                val acted = ship.id in view.myActed
                ChipButton(
                    FleetCatalog.label(ship.type) + if (acted) " ·已行动" else "",
                    selectedShipId == ship.id,
                    !acted && myTurn
                ) { selectedShipId = ship.id }
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
                    selectedShipId = s.shipId
                    showEnemy = s.kind != SkillKind.EVASIVE_MANEUVER
                }
            }
            if (pendingSkill != null) {
                OutlinedButton(onClick = {
                    pendingSkill = null
                    skillTargets = emptyList()
                }, modifier = Modifier.padding(3.dp)) { Text("取消技能", fontSize = 12.sp) }
            }
        }
        InfoLine("阶段一技能（侦察机 / 水听 / 规避机动）需先释放再开火；主炮齐射需点 2 个坐标；单点攻击自动置灰已打过的格子。")

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { vm.skipTactical() },
                enabled = myTurn && view.turnPhase == TurnPhase.TACTICAL,
                modifier = Modifier.weight(1f).padding(end = 4.dp)
            ) { Text("跳过战术阶段", fontSize = 12.sp) }
            Button(
                onClick = {
                    vm.endTurn()
                    pendingSkill = null
                    skillTargets = emptyList()
                    selectedShipId = null
                },
                enabled = myTurn,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            ) { Text("结束回合", fontSize = 12.sp) }
        }
        Spacer(Modifier.height(8.dp))

        SectionTitle("战况播报日志")
        LogList(view.log, Modifier.fillMaxWidth().height(150.dp))
        Spacer(Modifier.height(16.dp))
    }
}
