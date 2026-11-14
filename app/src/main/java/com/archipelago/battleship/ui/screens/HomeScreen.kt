package com.archipelago.battleship.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archipelago.battleship.core.AppThemeMode
import com.archipelago.battleship.core.BattleScene
import com.archipelago.battleship.ui.GameViewModel
import com.archipelago.battleship.ui.Mode
import com.archipelago.battleship.ui.UiState
import com.archipelago.battleship.ui.components.ChipButton
import com.archipelago.battleship.ui.components.HelpSheet
import com.archipelago.battleship.ui.components.HelpTopic
import com.archipelago.battleship.ui.components.MenuCard
import com.archipelago.battleship.ui.components.ScreenTopBar
import com.archipelago.battleship.ui.components.SectionTitle
import com.archipelago.battleship.ui.components.SurfaceCard

@Composable
fun HomeScreen(vm: GameViewModel, state: UiState) {
    val context = LocalContext.current
    var manualText by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenTopBar(
            title = "群岛海战棋",
            subtitle = state.connection,
            onHelp = { showHelp = true }
        )
        Spacer(Modifier.height(10.dp))

        SurfaceCard {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                BattleScene(Modifier.fillMaxSize(), state.coverRadar)
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                ChipButton("雷达图", state.coverRadar, true) { vm.setCoverMode(true) }
                ChipButton("棋盘", !state.coverRadar, true) { vm.setCoverMode(false) }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (state.mode == Mode.GUEST) {
            SurfaceCard {
                OutlinedButton(onClick = { vm.leaveRoom() }, modifier = Modifier.fillMaxWidth()) {
                    Text("取消连接 · 返回首页")
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        MenuCard(1, "单人本地对战", "本地 AI 对手 · 立即开局", true) {
            vm.startLocal()
        }
        MenuCard(2, "创建房间（房主）", "二维码邀请 · 可配置地图", false) {
            vm.createRoom(context)
        }
        MenuCard(3, "扫码加入（客机）", "扫描房主二维码", false) {
            vm.openScanner()
        }
        MenuCard(4, "手动输入地址加入", "IP:端口:房间密钥", false) {
            vm.showManualJoin(true)
        }

        SectionTitle("附近房间")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.startDiscovery(context) }, modifier = Modifier.weight(1f)) {
                Text(if (state.discovering) "重新搜索" else "搜索附近房间", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { vm.stopDiscovery() }, modifier = Modifier.weight(1f)) {
                Text("停止搜索", fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        SurfaceCard {
            if (state.discovered.isEmpty()) {
                Text(
                    "暂无房间",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                state.discovered.forEach { room ->
                    OutlinedButton(
                        onClick = { vm.joinFound(room) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Text("加入 ${room.name}  (${room.ip}:${room.port})", fontSize = 12.sp)
                    }
                }
            }
        }

        SectionTitle("外观")
        SurfaceCard {
            Row(modifier = Modifier.fillMaxWidth()) {
                ChipButton("坐标纸", state.theme == AppThemeMode.PAPER, true) {
                    vm.setTheme(AppThemeMode.PAPER)
                }
                ChipButton("情报室", state.theme == AppThemeMode.WARROOM, true) {
                    vm.setTheme(AppThemeMode.WARROOM)
                }
                ChipButton("声呐雷达", state.theme == AppThemeMode.SONAR, true) {
                    vm.setTheme(AppThemeMode.SONAR)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    if (state.manualJoin) {
        AlertDialog(
            onDismissRequest = { vm.showManualJoin(false) },
            title = { Text("手动加入房间") },
            text = {
                Column {
                    Text("格式：192.168.43.1:41234:882731", fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualText,
                        onValueChange = { manualText = it },
                        label = { Text("IP:端口:房间密钥") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { vm.joinManual(manualText) }) { Text("连接") }
            },
            dismissButton = {
                TextButton(onClick = { vm.showManualJoin(false) }) { Text("取消") }
            }
        )
    }

    if (showHelp) {
        HelpSheet(HelpTopic.RULES, state.theme) { showHelp = false }
    }
}
