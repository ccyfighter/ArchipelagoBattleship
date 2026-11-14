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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.archipelago.battleship.core.BoardCanvas
import com.archipelago.battleship.domain.engine.Rules
import com.archipelago.battleship.domain.model.Board
import com.archipelago.battleship.domain.model.Coord
import com.archipelago.battleship.domain.model.DIR4
import com.archipelago.battleship.domain.model.FleetCatalog
import com.archipelago.battleship.domain.model.RoomPhase
import com.archipelago.battleship.domain.model.Ship
import com.archipelago.battleship.domain.model.ShipType
import com.archipelago.battleship.domain.model.ShotResult
import com.archipelago.battleship.domain.model.SkillAvailability
import com.archipelago.battleship.domain.model.SkillKind
import com.archipelago.battleship.domain.model.TurnPhase
import com.archipelago.battleship.domain.model.coordLabel
import com.archipelago.battleship.domain.model.inBounds
import com.archipelago.battleship.ui.GameViewModel
import com.archipelago.battleship.ui.UiState
import com.archipelago.battleship.ui.components.BoardLegend
import com.archipelago.battleship.ui.components.ChipButton
import com.archipelago.battleship.ui.components.HelpSheet
import com.archipelago.battleship.ui.components.HelpTopic
import com.archipelago.battleship.ui.components.LogList
import com.archipelago.battleship.ui.components.ResultBanner
import com.archipelago.battleship.ui.components.ScreenTopBar
import com.archipelago.battleship.ui.components.SectionTitle
import com.archipelago.battleship.ui.components.SurfaceCard
import com.archipelago.battleship.ui.components.StatPill

private fun subCenter(ship: Ship): Coord =
    if (ship.cells.size > 1) ship.cells[1] else ship.cells[0]

/** 潜艇鱼雷：按每个方向各自的剩余射程列出定航向格；余程归零的方向仍留一格用于「空射」。 */
private fun torpedoCandidates(ship: Ship, enemy: Board): List<Coord> {
    val c0 = subCenter(ship)
    val out = ArrayList<Coord>()
    for (d in DIR4) {
        val reach = Rules.torpedoReach(ship, d.r, d.c)
        val steps = if (reach > 0) 1..reach else 1..1
        for (step in steps) {
            val c = Coord(c0.r + d.r * step, c0.c + d.c * step)
            if (!c.inBounds(enemy.size)) continue
            // 空射方向不再受地形限制：点它只是定航向，引擎会直接判空射。
            if (reach > 0 && c in enemy.mountains) continue
            out.add(c)
        }
    }
    return out.distinct()
}

private fun laneText(ship: Ship): String {
    val names = listOf("北", "南", "西", "东")
    return DIR4.mapIndexed { i, d ->
        names[i] + " " + Rules.torpedoReach(ship, d.r, d.c) + " 格"
    }.joinToString(" · ")
}

/** 情报视图用的「这一格发生过什么」，只读 board，不改任何状态。 */
private fun cellInfoLines(board: Board, c: Coord): List<String> {
    if (!c.inBounds(board.size)) return listOf("坐标越界")
    val out = ArrayList<String>()
    val terrain = when {
        c in board.mountains -> "山地"
        c in board.shallows -> "近岸浅滩"
        else -> "开阔海"
    }
    out.add("地形：$terrain")
    val rec = board.shots.lastOrNull { it.at == c }
    val verdict = when (rec?.result) {
        ShotResult.MISS -> "未命中"
        ShotResult.HIT -> "击中"
        ShotResult.SUNK -> "击沉"
        ShotResult.INTERCEPTED -> "攻击被拦截 / 战机被击落"
        ShotResult.IMMUNE -> "攻击无效 · 目标免疫"
        ShotResult.BLOCKED -> "攻击无效 · 山地阻挡"
        else -> "尚无攻击记录"
    }
    out.add(
        "最近判定：$verdict · " +
            if (c in board.attackedPoints) "已封锁" else "可攻击"
    )
    val layers = ArrayList<String>()
    if (c in board.reconCells) layers.add("侦察机")
    if (c in board.sonarCells) layers.add("水听")
    if (c in board.radarCells) layers.add("监视雷达")
    if (c in board.strikeCells) layers.add("陆航空袭预告")
    out.add("情报层：" + if (layers.isEmpty()) "无" else layers.joinToString("、"))
    return out
}

@Composable
fun BattleScreen(vm: GameViewModel, state: UiState, onResign: () -> Unit = {}) {
    val view = state.view ?: return

    var showEnemy by remember { mutableStateOf(true) }
    var selectedShipId by remember { mutableStateOf<String?>(null) }
    var pendingSkill by remember { mutableStateOf<SkillAvailability?>(null) }
    var skillTargets by remember { mutableStateOf<List<Coord>>(emptyList()) }
    var previewTarget by remember { mutableStateOf<Coord?>(null) }
    var cleanView by remember { mutableStateOf(true) }
    var inspectCoord by remember { mutableStateOf<Coord?>(null) }
    var showLog by remember { mutableStateOf(false) }
    var showLegend by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var surfaceGun by remember { mutableStateOf(false) }

    val myTurn = view.turn == view.viewer && view.phase == RoomPhase.IN_BATTLE
    val aliveShips = view.myBoard.ships.filter { !it.sunk }
    val board = if (showEnemy) view.enemyBoard else view.myBoard
    val need = pendingSkill?.let { Rules.skillTargetCount(it.kind) } ?: 0
    val tacticPhase = view.turnPhase == TurnPhase.TACTICAL
    val selectedShip = aliveShips.firstOrNull { it.id == selectedShipId }
    val budgetLeft = (view.attackBudget - view.attackUsed).coerceAtLeast(0)
    val pt = previewTarget
    val sub = selectedShip
    val lastStand = aliveShips.size == 1 && aliveShips[0].type == ShipType.SUBMARINE

    val candidates: List<Coord> = if (!myTurn) emptyList()
    else if (pendingSkill != null) {
        val sk = pendingSkill!!
        if (need == 0) emptyList()
        else if (sk.kind == SkillKind.TORPEDO_BOMBER && skillTargets.size == 1) {
            val s0 = skillTargets[0]
            val n = view.enemyBoard.size
            (1..Rules.TORPEDO_BOMBER_STEPS).flatMap { step ->
                listOf(
                    Coord(s0.r, s0.c + step), Coord(s0.r, s0.c - step),
                    Coord(s0.r + step, s0.c), Coord(s0.r - step, s0.c)
                )
            }.filter { it.inBounds(n) && it !in view.enemyBoard.mountains }.distinct()
        } else if (sk.kind == SkillKind.TORPEDO_BOMBER || Rules.isTactical(sk.kind)) {
            view.intelTargets
        } else view.legalTargets
    }
    else if (tacticPhase) emptyList()
    else if (sub == null) emptyList()
    else if (sub.type == ShipType.SUBMARINE && !surfaceGun) torpedoCandidates(sub, view.enemyBoard)
    else view.legalTargets

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
        surfaceGun = false
    }

    fun onTap(c: Coord) {
        inspectCoord = c
        if (!myTurn) return
        val skill = pendingSkill
        if (skill != null) {
            if (need == 0) return
            skillTargets = if (skillTargets.size < need) skillTargets + c
            else skillTargets.dropLast(1) + c
            return
        }
        if (!showEnemy) {
            showEnemy = true
            inspectCoord = null
            return
        }
        if (selectedShip == null) return
        previewTarget = c
    }

    if (showLog) {
        Dialog(
            onDismissRequest = { showLog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "完整战报 · ${view.log.size} 条",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { showLog = false }) { Text("关闭", fontSize = 11.sp) }
                }
                Spacer(Modifier.height(8.dp))
                LogList(view.log, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        ScreenTopBar(
            title = "第 ${view.round} 回合",
            subtitle = (if (myTurn) "轮到你行动" else "对方行动中") +
                " · " + (if (tacticPhase) "阶段一 战术" else "阶段二 攻击"),
            onHelp = { showHelp = true }
        )
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatPill("攻击", "${view.attackUsed}/${view.attackBudget}",
                if (budgetLeft == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                Modifier.weight(1f))
            StatPill("我方", "${view.myAlive.size}", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            StatPill("敌方", "${view.enemyAliveCount}", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            StatPill("击沉", "${view.enemySunkTypes.size}", MaterialTheme.colorScheme.error, Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        val fraction = (state.timerSeconds.toFloat() / Rules.TURN_SECONDS.toFloat()).coerceIn(0f, 1f)
        val timerColor = if (fraction < 0.2f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(timerColor)
            )
        }
        Text(
            "${state.timerSeconds}s",
            fontSize = 10.sp,
            color = timerColor,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.height(6.dp))

        ResultBanner(view.log)
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                ChipButton("敌方海域", showEnemy, true) {
                    showEnemy = true
                    previewTarget = null
                    inspectCoord = null
                }
                ChipButton("我方海域", !showEnemy, true) {
                    showEnemy = false
                    previewTarget = null
                    inspectCoord = null
                }
                ChipButton("情报视图", cleanView, true) { cleanView = !cleanView }
            }
            TextButton(onClick = { showLog = true }) {
                Text("战报 ${view.log.size}", fontSize = 11.sp)
            }
        }

        val hint = when {
            pendingSkill?.kind == SkillKind.RADAR_SURVEILLANCE ->
                "监视雷达 · 自动扫描，点「确认释放技能」"
            pendingSkill?.kind == SkillKind.TORPEDO_BOMBER ->
                if (skillTargets.isEmpty())
                    "鱼雷机 · 第 1 点点投雷起点（防空区外），第 2 点定航向 · 推进 ${Rules.TORPEDO_BOMBER_STEPS} 格"
                else
                    "鱼雷机 · 已选起点 ${coordLabel(skillTargets[0])}，再点同行 / 同列一格定航向（${skillTargets.size}/$need）"
            pendingSkill != null ->
                "【${pendingSkill!!.label}】已选 ${skillTargets.size}/$need · ${skillTargets.joinToString("、") { coordLabel(it) }}"
            sub != null && sub.type == ShipType.SUBMARINE && surfaceGun ->
                "上浮炮击 · 点敌方海域格子选目标 · 首尾格暴露到下回合"
            sub != null && sub.type == ShipType.SUBMARINE ->
                "潜艇鱼雷 · 点同行 / 同列格定航向 · 余程 ${laneText(sub)}"
            sub != null ->
                "已选【${FleetCatalog.label(sub.type)}】· " +
                    if (pt != null) "暂存目标 ${coordLabel(pt)}" else "点敌方海域格子选目标"
            tacticPhase -> "阶段一：可释放 1 次战术技能，或点「进入阶段二」"
            else -> "先选择一艘开火舰船，或选择一个技能"
        }
        Text(
            hint,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            maxLines = 2,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        BoardCanvas(
            board = board,
            mode = state.theme,
            interactive = true,
            legalTargets = if (showEnemy && !cleanView) candidates else emptyList(),
            highlight = if (showEnemy) highlight else null,
            markers = skillTargets,
            calm = cleanView,
            modifier = Modifier.fillMaxWidth().padding(2.dp),
            onTap = { onTap(it) }
        )

        inspectCoord?.let { c ->
            Spacer(Modifier.height(6.dp))
            SurfaceCard(accent = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "格子 ${coordLabel(c)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { inspectCoord = null }) {
                        Text("收起", fontSize = 11.sp)
                    }
                }
                cellInfoLines(board, c).forEach {
                    Text(
                        it,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showLegend = !showLegend }) {
                Text(if (showLegend) "收起棋盘图例" else "棋盘图例", fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showHelp = true }) {
                Text("规则 ?", fontSize = 11.sp)
            }
        }
        if (showLegend) {
            BoardLegend(state.theme)
            Spacer(Modifier.height(6.dp))
        }

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
                    ) { Text("确认释放 ${skillTargets.size}/$need", fontSize = 11.sp) }
                    OutlinedButton(
                        onClick = { skillTargets = emptyList() },
                        modifier = Modifier.padding(end = 3.dp)
                    ) { Text("清空目标", fontSize = 11.sp) }
                    OutlinedButton(onClick = { clearDraft() }) { Text("取消", fontSize = 11.sp) }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (lastStand && sub?.type == ShipType.SUBMARINE && myTurn) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            ChipButton("潜航鱼雷", !surfaceGun, true) {
                                surfaceGun = false
                                previewTarget = null
                            }
                            ChipButton("上浮炮击", surfaceGun, true) {
                                surfaceGun = true
                                previewTarget = null
                            }
                        }
                        Text(
                            "仅剩潜艇 · 上浮炮击改为普通单格炮击，首尾格暴露到下回合",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                val id = selectedShipId
                                val t = previewTarget
                                if (id != null && t != null) {
                                    vm.fire(id, t, surfaceGun)
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
            }
        }
        Spacer(Modifier.height(6.dp))

        SectionTitle("开火舰船")
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
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = onResign,
            enabled = view.phase == RoomPhase.IN_BATTLE,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("投降", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showHelp) {
        HelpSheet(HelpTopic.SKILL, state.theme) { showHelp = false }
    }
}
