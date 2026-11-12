package com.archipelago.battleship.ui.screens

import androidx.compose.foundation.layout.*
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
import com.archipelago.battleship.ui.components.ChipButton
import com.archipelago.battleship.ui.components.GameLogo
import com.archipelago.battleship.ui.components.InfoLine
import com.archipelago.battleship.ui.components.MenuCard
import com.archipelago.battleship.ui.components.SectionTitle
import com.archipelago.battleship.ui.components.SurfaceCard

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
        Spacer(Modifier.height(8.dp))
        SurfaceCard(accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GameLogo(modifier = Modifier.size(118.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    "群岛海战棋",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "ARCHIPELAGO  ·  BATTLESHIP",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    "5+4+3+2+1 五舰制 · 面对面战术战棋",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SurfaceCard {
            InfoLine(state.connection)
            InfoLine("单机完全离线；联机仅走局域网 / 手机热点，不经过公网服务器。")
        }
        Spacer(Modifier.height(8.dp))

        MenuCard(1, "单人本地对战", "Hunt & Target AI · 无需网络 · 立即开局", true) {
            vm.startLocal()
        }
        MenuCard(2, "创建房间（房主）", "NSD 广播 + 二维码邀请 · 可配置地图与规则", false) {
            vm.createRoom(context)
        }
        MenuCard(3, "扫码加入（客机）", "用相机扫房主二维码，秒连同一房间", false) {
            vm.openScanner()
        }
        MenuCard(4, "手动输入地址加入", "格式 IP:端口:房间密钥", false) {
            vm.showManualJoin(true)
        }

        SectionTitle("附近房间（NsdManager mDNS 自动发现）")
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
                InfoLine("暂无房间。也可让房主显示二维码扫码直连，或手动输入地址。")
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
                ChipButton("机舱手绘坐标纸", state.theme == AppThemeMode.PAPER, true) {
                    if (state.theme != AppThemeMode.PAPER) vm.toggleTheme()
                }
                ChipButton("深海战术声呐雷达", state.theme == AppThemeMode.SONAR, true) {
                    if (state.theme != AppThemeMode.SONAR) vm.toggleTheme()
                }
            }
            InfoLine("两套主题同时作用于界面与棋盘；棋盘外圈为深墨蓝坐标标尺带。")
        }
        Spacer(Modifier.height(10.dp))
        SurfaceCard {
            InfoLine("Android 14+ 权限：NEARBY_WIFI_DEVICES(neverForLocation) / ACCESS_NETWORK_STATE / ACCESS_WIFI_STATE / INTERNET / CAMERA")
            InfoLine("NSD 自动发现 + Socket 长连接，不依赖 Google Play Services。")
        }
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
