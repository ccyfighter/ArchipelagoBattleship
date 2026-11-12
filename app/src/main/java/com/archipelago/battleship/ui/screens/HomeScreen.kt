package com.archipelago.battleship.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archipelago.battleship.core.AppThemeMode
import com.archipelago.battleship.ui.GameViewModel
import com.archipelago.battleship.ui.UiState
import com.archipelago.battleship.ui.components.InfoLine
import com.archipelago.battleship.ui.components.SectionTitle

@Composable
fun HomeScreen(vm: GameViewModel, state: UiState) {
    val context = LocalContext.current
    var manualText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "群岛海战棋",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Archipelago Battleship · 5+4+3+2+1 五舰制 · 面对面战棋",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
        )
        Spacer(Modifier.height(10.dp))
        InfoLine(state.connection)
        Spacer(Modifier.height(18.dp))

        Button(onClick = { vm.startLocal() }, modifier = Modifier.fillMaxWidth()) {
            Text("单人本地对战（Hunt & Target AI · 完全断网）")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.createRoom(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("创建房间（房主）· 展示二维码")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.openScanner() }, modifier = Modifier.fillMaxWidth()) {
            Text("扫码加入（客机）")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { vm.showManualJoin(true) }, modifier = Modifier.fillMaxWidth()) {
            Text("手动输入 IP:端口:密钥 加入")
        }
        Spacer(Modifier.height(18.dp))

        SectionTitle("附近房间（NsdManager mDNS 自动发现）")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.startDiscovery(context) }, modifier = Modifier.weight(1f)) {
                Text(if (state.discovering) "重新搜索" else "搜索附近房间")
            }
            OutlinedButton(onClick = { vm.stopDiscovery() }, modifier = Modifier.weight(1f)) {
                Text("停止搜索")
            }
        }
        Spacer(Modifier.height(6.dp))
        if (state.discovered.isEmpty()) {
            InfoLine("暂无房间。也可让房主显示二维码扫码直连，或手动输入地址。")
        } else {
            state.discovered.forEach { room ->
                OutlinedButton(
                    onClick = { vm.joinFound(room) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Text("加入 ${room.name}  (${room.ip}:${room.port})")
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("外观")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.toggleTheme() }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (state.theme == AppThemeMode.PAPER)
                        "当前：机舱手绘坐标纸  →  切换到深海声呐雷达"
                    else
                        "当前：深海战术声呐雷达  →  切换到机舱手绘坐标纸"
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        InfoLine("Android 14+ 权限：NEARBY_WIFI_DEVICES(neverForLocation) / ACCESS_NETWORK_STATE / ACCESS_WIFI_STATE / INTERNET / CAMERA")
        InfoLine("联机仅走局域网 / 手机热点 + NSD 自动发现 + Socket 长连接，不依赖 Google Play Services，无公网服务器。")
        Spacer(Modifier.height(24.dp))
    }

    if (state.manualJoin) {
        AlertDialog(
            onDismissRequest = { vm.showManualJoin(false) },
            title = { Text("手动加入房间") },
            text = {
                Column {
                    Text("格式：192.168.43.1:41234:882731", fontSize = 12.sp)
                    Text("也可直接粘贴房主二维码里的整串文本", fontSize = 12.sp)
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
}
