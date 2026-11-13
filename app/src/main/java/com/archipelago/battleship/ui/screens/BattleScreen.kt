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
import com.archipelago.battleship.ui.components.InfoLine
import com.archipelago.battleship.ui.components.LogList
import com.archipelago.battleship.ui.components.ResultBanner
import com.archipelago.battleship.ui.components.SectionTitle
import com.archipelago.battleship.ui.components.StatPill
import com.archipelago.battleship.ui.components.SurfaceCard

private fun subCenter(ship: Ship): Coord =
    if (ship.cells.size > 1) ship.cells[1] else ship.cells[0]

/**
 * 潜艇鱼雷：以中心为原点，按【每个方向各自的剩余射程】列出发射方向格。
 * v10 起同方向连射会 4→3→2→1 递减，射程归零的方向不再给候选格，
 * 候选格数量随射程收缩，正好把「这一向还能打多远」直接画在棋盘上。
 */
private fun torpedoCandidates(ship: Ship, enemy: Board): List<Coord> {
    val c0 = subCenter(ship)
    val out = ArrayList<Coord>()
    for (d in DIR4) {
        val reach = Rules.torpedoReach(ship, d.r, d.c)
        if (reach <= 0) continue
        for (step in 1..reach) {
            val c = Coord(c0.r + d.r * step, c0.c + d.c * step)
            // 只定方向：鱼雷会截断在已炮击 / 已炸过的格子前，所以这里不排除 attackedPoints
            if (c.inBounds(enemy.size) && c !in enemy.mountains) out.add(c)
        }
    }
    return out.distinct()
}

/** DIR4 顺序固定为 北 / 南 / 西 / 东。 */
private fun laneText(ship: Ship): String {
    val names = listOf("北", "南", "西", "东")
    return DIR4.mapIndexed { i, d ->
        names[i] + " " + Rules.torpedoReach(ship, d.r, d.c) + " 格"
    }.joinToString(" · ")
}

/** 情报视图用的「这一格发生过什么」检查卡，只读 board，不改任何状态。 */
private fun cellInfoLines(board: Board, c: Coord): List<String> {
    if (!c.inBounds(board.size)) return listOf("坐标越界")
    val out = ArrayList<String>()
    val terrain = when {
        c in board.mountains -> "山地（不可攻击 / 不可部署）"
        c in board.shallows -> "近岸浅滩"
        else -> "开阔海"
    }
    out.add("${coordLabel(c)} · 地形：$terrain")
    val rec = board.shots.lastOrNull { it.at == c }
    val verdict = when (rec?.result) {
        ShotResult.MISS -> "未命中（水花）"
        ShotResult.HIT -> "击中"
        ShotResult.SUNK -> "击沉"
        ShotResult.INTERCEPTED -> "攻击被拦截 / 战机被击落（A）"
        ShotResult.IMMUNE -> "攻击无效 · 目标免疫（I）"
        ShotResult.BLOCKED -> "攻击无效 · 山地阻挡（B）"
        else -> "尚无攻击记录"
    }
    out.add("最近判定：$verdict")
    out.add(
        "坐标状态：" +
            if (c in board.attackedPoints) "已封锁（单点攻击不能重复打）"
            else "可攻击，也可作为情报 / 部署选点"
    )
    val layers = ArrayList<String>()
    if (c in board.reconCells) layers.add("侦察机已扫描")
    if (c in board.sonarCells) layers.add("水听已扫描")
    if (c in board.radarCells) layers.add("监视雷达覆盖")
    if (c in board.strikeCells) layers.add("陆航空袭预告")
    out.add("情报层：" + if (layers.isEmpty()) "无" else layers.joinToString("、"))
    out.add("说明：侦察 / 水听 / 雷达 / 拦截都只是显示标记，不封锁坐标、不影响任何攻击判定。")
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
    /** 默认开：不画满屏「可打击」括号，画面只留战果与情报层。 */
    var cleanView by remember { mutableStateOf(true) }
    /** 点格子要看的信息，不参与任何结算。 */
    var inspectCoord by remember { mutableStateOf<Coord?>(null) }
    /** 完整战报用整屏弹窗承载，不再需要一边滚页面一边滚小列表。 */
    var showLog by remember { mutableStateOf(false) }

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
        if (need == 0) emptyList() // 雷达以船体为中心自动扫描，不需要选点
        else if (sk.kind == SkillKind.TORPEDO_BOMBER && skillTargets.size == 1) {
            // 第 2 个点只用于定航向：只提示射程内的同行 / 同列格，不画整行整列的长轨迹
            val s0 = skillTargets[0]
            val n = view.enemyBoard.size
            (1..Rules.TORPEDO_BOMBER_STEPS).flatMap { step ->
                listOf(
                    Coord(s0.r, s0.c + step), Coord(s0.r, s0.c - step),
                    Coord(s0.r + step, s0.c), Coord(s0.r - step, s0.c)
                )
            }.filter { it.inBounds(n) && it !in view.enemyBoard.mountains }.distinct()
        } else if (sk.kind == SkillKind.TORPEDO_BOMBER || Rules.isTactical(sk.kind)) {
            // 部署点 / 情报技能只排除山地：已打过、已侦察、被拦截的格子照样可以选
            view.intelTargets
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
        // 无论是不是自己的回合，点格子都先看信息（纯读操作，不会触发任何结算）。
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
        // 只暂存目标，不结算：点确认之前可以反复改选，绝不自动跳到下一艘船。
        previewTarget = c
    }

    // 完整战报：整屏弹窗，最新在上，一次看完，不用在页面里上下滑动。
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
                        "完整战况播报 · ${view.log.size} 条（最新在上）",
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
                StatPill("敌方", "${view.enemyAliveCount}", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
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
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = { showLog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("展开完整战报（${view.log.size} 条 · 免上下滑动）", fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
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
        InfoLine(
            if (cleanView)
                "情报视图：不显示任何可打击方格，画面只留地形 / 战果 / 四种情报层；点任意格子可查看该格打过什么、被谁侦察过、是否被拦截。选船 → 点格 → 确认开火仍然照常可用，无需来回切模式。"
            else
                "攻击视图：显示可打击格（内缩描边框 + 角点）。已打过的格子是灰斜纹，山地不可选；情报层只是显示标记，不影响攻击。"
        )

        val hint = when {
            pendingSkill?.kind == SkillKind.RADAR_SURVEILLANCE ->
                "监视雷达：无需选点，按「确认释放技能」即以本舰 3 格船体为中心扫描（射程 ${Rules.radarRange(view.enemyBoard.size)} 格，山地遮蔽后方海域）；只返回覆盖区内水面舰艇数量，不报坐标 / 舰种"
            pendingSkill?.kind == SkillKind.TORPEDO_BOMBER ->
                if (skillTargets.isEmpty())
                    "鱼雷机：第 1 点点投雷起点（须在敌方防空圈外），第 2 点点同行/同列一格只定航向；鱼雷从起点向前推进 ${Rules.TORPEDO_BOMBER_STEPS} 格（航线共 ${Rules.TORPEDO_BOMBER_STEPS + 1} 格），在「碰到敌舰 / 撞山前一格 / 射程尽头」单点爆炸，沿途不留轨迹、中间格仍可再打"
                else
                    "鱼雷机：已选起点 ${coordLabel(skillTargets[0])}，再点同行/同列一格定航向（已选 ${skillTargets.size}/$need）；起点在 AA 圈外即可打击防空舰，航线 / 炸点穿过防空区都不算被击落"
            pendingSkill != null ->
                "技能【${pendingSkill!!.label}】已选 ${skillTargets.size}/$need：${skillTargets.joinToString("、") { coordLabel(it) }}；点棋盘可改，按「确认释放」才生效"
            sub != null && sub.type == ShipType.SUBMARINE ->
                "潜艇鱼雷：以中心 ${coordLabel(subCenter(sub))} 为准，点同行 / 同列高亮格只定航向；各方向剩余射程 ${laneText(sub)}（同向连射 4→3→2→1，方向上有炮击 / 炸点会提前截断）；只在一个点引爆，不画航线、中间格仍可再打"
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
            interactive = true,
            legalTargets = if (showEnemy && !cleanView) candidates else emptyList(),
            highlight = if (showEnemy) highlight else null,
            markers = skillTargets,
            calm = cleanView,
            modifier = Modifier.fillMaxWidth().padding(2.dp),
            onTap = { onTap(it) }
        )
        Spacer(Modifier.height(8.dp))

        inspectCoord?.let { c ->
            SurfaceCard(accent = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)) {
                Text("格子信息", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                cellInfoLines(board, c).forEach { InfoLine(it) }
                OutlinedButton(onClick = { inspectCoord = null }) { Text("收起", fontSize = 11.sp) }
            }
            Spacer(Modifier.height(8.dp))
        }

        BoardLegend(state.theme)
        InfoLine("深色标尺带为坐标：横向 A… / 纵向 1…；选中目标时行列标签同步高亮。四种情报层各有独立画法：侦察机=琥珀放大镜、水听=蓝色同心波纹、雷达=青色虚线框、陆航空袭预告=橙红斜叉；它们只是显示标记，不封锁坐标、不影响任何攻击。")

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
        InfoLine("主炮齐射需点 2 个坐标；鱼雷机需点「起点 + 同行/同列航向点」共 2 点，起点在防空圈外即可从圈外打击 AA 舰（航线 / 炸点穿过防空区都不算被击落）；从第 1 点向前推进 3 格。两种鱼雷都只在一个点爆炸、只结算一次（可命中水下潜艇），撞山则在山前一格引爆。")
        InfoLine("防空规则 v10：区域防空网只击落「部署点」落在圈内的侦察机 / 鱼雷机；航母战术轰炸机从己方航母起飞，飞过防空区投弹不算被击落，只有炸弹落在防空舰本体格时才会被近防火力击落；陆航空袭无视一切 AA。拦截 / 免疫 / 阻挡都不写坐标去重，换艘舰仍可打同一格。")
        InfoLine("潜艇鱼雷同方向连射按 4→3→2→1 递减射程，方向上有炮击 / 前次炸点会提前截断，射程归零的方向不能再发射；换方向不受影响。战报情报：双方播报只显示「哪一方 + 攻击坐标 + 结果」，不报攻击方式与舰种；只有击沉才公开舰种。")

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
        OutlinedButton(
            onClick = onResign,
            enabled = view.phase == RoomPhase.IN_BATTLE,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "投降 · 结束本局（点击后二次确认）",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
        InfoLine("单机与联机均可投降：确认后本局立即结束、判对方获胜；双方仍可返回房间保持连接再开一局。系统返回键在战斗中也会先弹出确认，不会误退。")
        Spacer(Modifier.height(8.dp))

        SectionTitle("战况播报")
        OutlinedButton(
            onClick = { showLog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("打开完整战报（${view.log.size} 条）", fontSize = 12.sp)
        }
        InfoLine("顶部固定显示最近 3 条战果；完整日志在整屏面板里一次看完，不需要在页面和列表之间来回上下滑动。")
        Spacer(Modifier.height(16.dp))
    }
}
