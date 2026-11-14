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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.archipelago.battleship.ui.components.HelpSheet
import com.archipelago.battleship.ui.components.HelpTopic
import com.archipelago.battleship.ui.components.ScreenTopBar
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
    var layoutStyle by remember { mutableStateOf(0) }
    var roll by remember { mutableStateOf(0) }
    var showHelp by remember { mutableStateOf(false) }

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
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        ScreenTopBar(
            title = "舰队编成",
            subtitle = "${cfg.size}×${cfg.size} · ${if (cfg.mountains) "随机群岛" else "纯海"} · 已布置 ${placements.size}/5",
            onHelp = { showHelp = true }
        )
        Spacer(Modifier.height(8.dp))

        if (error.isNotEmpty()) {
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
        }

        SectionTitle("选船")
        for (size in listOf(5, 4, 3, 2, 1)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$size 格",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(end = 6.dp)
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "当前：${spec.label}（${spec.size} 格）",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { showHelp = true }) {
                Text("舰种详情 ?", fontSize = 11.sp)
            }
        }

        SectionTitle("布阵")
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            for (size in listOf(5, 4, 3, 2, 1)) {
                ChipButton("放置 $size 格", selectedSize == size) { selectedSize = size }
            }
            ChipButton("横向 H", orientation == Orientation.H) { orientation = Orientation.H }
            ChipButton("纵向 V", orientation == Orientation.V) { orientation = Orientation.V }
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
            OutlinedButton(
                onClick = {
                    roll++
                    placements = Rules.defaultPlacements(cfg, fleet, Random.nextLong(), layoutStyle)
                    error = ""
                },
                modifier = Modifier.padding(3.dp)
            ) { Text("一键随机布阵", fontSize = 12.sp) }
            OutlinedButton(
                onClick = {
                    placements = emptyList()
                    roll = 0
                    error = ""
                },
                modifier = Modifier.padding(3.dp)
            ) { Text("清空布阵", fontSize = 12.sp) }
        }
        Text(
            "方案 $roll · ${Rules.layoutLabel(layoutStyle)}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )

        Spacer(Modifier.height(4.dp))
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
            }
        }

        Text(
            "已布置：" + placements.joinToString(" / ") {
                FleetCatalog.label(it.type) + "@" + coordLabel(it.origin) +
                    if (it.orientation == Orientation.H) "H" else "V"
            },
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = { save() }, modifier = Modifier.weight(1f)) {
                Text("保存并准备")
            }
            OutlinedButton(onClick = { vm.navigate(Screen.LOBBY) }, modifier = Modifier.weight(1f)) {
                Text("返回大厅")
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showHelp) {
        HelpSheet(HelpTopic.FLEET, state.theme) { showHelp = false }
    }
}
