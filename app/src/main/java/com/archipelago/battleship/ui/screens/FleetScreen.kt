package com.archipelago.battleship.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archipelago.battleship.core.BoardCanvas
import com.archipelago.battleship.domain.engine.Rules
import com.archipelago.battleship.domain.model.FleetCatalog
import com.archipelago.battleship.domain.model.Orientation
import com.archipelago.battleship.domain.model.Placement
import com.archipelago.battleship.domain.model.Side
import com.archipelago.battleship.domain.model.coordLabel
import com.archipelago.battleship.ui.GameViewModel
import com.archipelago.battleship.ui.Screen
import com.archipelago.battleship.ui.UiState
import com.archipelago.battleship.ui.components.ChipButton
import com.archipelago.battleship.ui.components.InfoLine
import com.archipelago.battleship.ui.components.SectionTitle
import kotlin.random.Random

@Composable
fun FleetScreen(vm: GameViewModel, state: UiState) {
    val view = state.view ?: return
    val cfg = view.config

    var fleet by remember {
        mutableStateOf(if (state.mySide == Side.A) view.hostFleet else view.guestFleet)
    }
    var placements by remember {
        mutableStateOf(view.myBoard.ships.map { Placement(it.type, it.origin, it.orientation) })
    }
    var selectedSize by remember { mutableStateOf(5) }
    var orientation by remember { mutableStateOf(Orientation.H) }
    var error by remember { mutableStateOf("") }
    /** 随机布阵风格：0 均衡散布 / 1 边角分散 / 2 中央集结 / 3 同向编队。 */
    var layoutStyle by remember { mutableStateOf(0) }
    /** 已生成的方案序号，仅用于给玩家一个「确实换了一套」的反馈。 */
    var roll by remember { mutableStateOf(0) }

    val board = remember(cfg, placements) { Rules.buildBoard(cfg, placements) }
    val selectedType = fleet.at(selectedSize)
    val spec = FleetCatalog.spec(selectedType)

    fun save() {
        val err = Rules.validateFleet(cfg, fleet, placements)
        if (err != null) {
            error = err
            return
        }
        vm.setFleetAndPlacements(fleet, placements)
        vm.setReady(true)
        vm.navigate(Screen.LOBBY)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        SectionTitle("舰队编成 · 5+4+3+2+1 五舰制（3 格档三选一，其余二选一，1 格默认炮艇）")
        InfoLine("地图 ${cfg.size}×${cfg.size} · ${if (cfg.mountains) "随机群岛" else "纯海"} · 已布置 ${placements.size}/5 艘")
        if (error.isNotEmpty()) {
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Spacer(Modifier.height(6.dp))

        for (size in listOf(5, 4, 3, 2, 1)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    "$size 格",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 14.dp, end = 6.dp)
                )
                FleetCatalog.optionsFor(size).forEach { t ->
                    ChipButton(
                        text = FleetCatalog.label(t),
                        selected = fleet.at(size) == t
                    ) {
                        val old = fleet.at(size)
                        fleet = fleet.withSize(size, t)
                        placements = placements.filter { it.type != old && it.type != t }
                        selectedSize = size
                        error = ""
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        SectionTitle("当前选中的舰种说明")
        Text("${spec.label}（${spec.size} 格）", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        InfoLine("进攻：" + spec.attack)
        InfoLine("技能：" + spec.skill)
        InfoLine("被动：" + spec.passive)
        Spacer(Modifier.height(8.dp))

        SectionTitle("布阵：点选槽位 → 点棋盘格子放置")
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            for (size in listOf(5, 4, 3, 2, 1)) {
                ChipButton("放置 $size 格", selectedSize == size) { selectedSize = size }
            }
            ChipButton("横向 H", orientation == Orientation.H) { orientation = Orientation.H }
            ChipButton("纵向 V", orientation == Orientation.V) { orientation = Orientation.V }
            // 想换槽位时显式点，不再由放置动作偷偷跳走。
            ChipButton("下一未布置槽", false) {
                val next = listOf(5, 4, 3, 2, 1).firstOrNull { s ->
                    placements.none { it.type == fleet.at(s) }
                }
                if (next != null) selectedSize = next
            }
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            for (s in 0..3) {
                ChipButton(Rules.layoutLabel(s), layoutStyle == s) { layoutStyle = s }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            OutlinedButton(onClick = {
                // v10 之前固定传 cfg.seed + 31，每次点都是同一套阵型（伪随机）。
                // 现在每次点都用全新随机种子，再叠加所选风格，能真正排出多种排列。
                roll++
                placements = Rules.defaultPlacements(cfg, fleet, Random.nextLong(), layoutStyle)
                error = ""
            }, modifier = Modifier.padding(3.dp)) { Text("一键随机布阵", fontSize = 12.sp) }
            OutlinedButton(onClick = {
                placements = emptyList()
                roll = 0
                error = ""
            }, modifier = Modifier.padding(3.dp)) { Text("清空布阵", fontSize = 12.sp) }
        }
        InfoLine("已生成 $roll 套方案 · 当前风格「${Rules.layoutLabel(layoutStyle)}」。每次点随机都用新种子 + 候选池打分，可反复换出不同阵型；先挑风格再随机，会明显看出贴边 / 抱团 / 同向的差别。")
        InfoLine("放置成功后【停留在当前槽位】，可以反复点不同格子微调这一艘；想换船请点上方「放置 N 格」或「下一未布置槽」。切换舰种时该档旧船会被移除，需要重新放置。")

        BoardCanvas(
            board = board,
            mode = state.theme,
            interactive = true,
            calm = true,
            modifier = Modifier.fillMaxWidth().padding(4.dp)
        ) { tapped ->
            val p = Placement(fleet.at(selectedSize), tapped, orientation)
            val err = Rules.validatePlacement(cfg, placements, p)
            if (err != null) {
                error = "${coordLabel(tapped)}：$err"
            } else {
                placements = placements.filter { it.type != p.type } + p
                error = ""
                // 不再自动 selectedSize = next：调整某一舰种时不会被悄悄切到下一艘。
            }
        }

        InfoLine("已布置：" + placements.joinToString(" / ") {
            FleetCatalog.label(it.type) + "@" + coordLabel(it.origin) + if (it.orientation == Orientation.H) "H" else "V"
        })
        Spacer(Modifier.height(10.dp))
        Row {
            Button(onClick = { save() }, modifier = Modifier.weight(1f)) {
                Text("保存并准备")
            }
            OutlinedButton(onClick = { vm.navigate(Screen.LOBBY) }, modifier = Modifier.weight(1f)) {
                Text("返回大厅")
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
