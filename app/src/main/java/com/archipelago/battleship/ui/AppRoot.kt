package com.archipelago.battleship.ui

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archipelago.battleship.domain.model.BattleResult
import com.archipelago.battleship.domain.model.FleetCatalog
import com.archipelago.battleship.domain.model.RoomPhase
import com.archipelago.battleship.domain.model.Side
import com.archipelago.battleship.domain.model.SideStats
import com.archipelago.battleship.ui.components.SurfaceCard
import com.archipelago.battleship.ui.screens.BattleScreen
import com.archipelago.battleship.ui.screens.FleetScreen
import com.archipelago.battleship.ui.screens.HomeScreen
import com.archipelago.battleship.ui.screens.LobbyScreen
import com.archipelago.battleship.ui.screens.ScannerScreen
import kotlinx.coroutines.delay

@Composable
fun AppRoot(vm: GameViewModel, state: UiState, activity: ComponentActivity) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        val permissions = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        permissions.add(Manifest.permission.CAMERA)
        permissionLauncher.launch(permissions.toTypedArray())
    }

    LaunchedEffect(state.toast) {
        if (state.toast.isNotEmpty()) {
            delay(2800)
            vm.consumeToast()
        }
    }

    val gridInk = MaterialTheme.colorScheme.primary.copy(alpha = 0.055f)
    val bandInk = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val density = LocalDensity.current
    val gridStep = with(density) { 56.dp.toPx() }
    val bandHeight = with(density) { 3.dp.toPx() }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 全局海图网格底纹：极淡，不与前景卡片抢视觉
        Canvas(modifier = Modifier.fillMaxSize()) {
            var x = 0f
            while (x < size.width) {
                drawLine(gridInk, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += gridStep
            }
            var y = 0f
            while (y < size.height) {
                drawLine(gridInk, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += gridStep
            }
            drawRect(bandInk, topLeft = Offset.Zero, size = Size(size.width, bandHeight))
        }

        when (state.screen) {
            Screen.HOME -> HomeScreen(vm, state)
            Screen.LOBBY -> LobbyScreen(vm, state)
            Screen.FLEET -> FleetScreen(vm, state)
            Screen.BATTLE -> BattleScreen(vm, state)
            Screen.SCANNER -> ScannerScreen(activity, vm::onScanResult, vm::closeScanner)
        }

        if (state.toast.isNotEmpty()) {
            val shape = RoundedCornerShape(14.dp)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surface, shape)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f), shape)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                )
                Text(
                    state.toast,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }

        val view = state.view
        if (
            state.screen == Screen.BATTLE &&
            view != null &&
            view.phase == RoomPhase.POST_BATTLE_LOBBY &&
            view.result != null
        ) {
            ResultDialog(view.result, view.viewer, vm::returnToLobby)
        }
    }
}

@Composable
private fun ResultDialog(result: BattleResult, viewer: Side, onReturn: () -> Unit) {
    val win = result.winner == viewer
    AlertDialog(
        onDismissRequest = { },
        title = {
            Text(
                if (win) "胜利 · 敌方舰队全灭" else "战败 · 我方舰队全灭",
                fontWeight = FontWeight.Bold,
                color = if (win) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("总回合数：${result.rounds}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                SurfaceCard(accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) {
                    StatBlock("房主", result.host)
                }
                Spacer(Modifier.height(6.dp))
                SurfaceCard(accent = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)) {
                    StatBlock("客机", result.guest)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "点击『返回房间』后：双方不断开连接、不退出房间，聊天窗口重新解锁；房主可重调地图 / 山地，双方可换船重新布阵，直接开启下一局。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.80f)
                )
            }
        },
        confirmButton = {
            Button(onClick = onReturn) { Text("返回房间（保持连接）") }
        }
    )
}

@Composable
private fun StatBlock(name: String, s: SideStats) {
    Text(
        "$name · 造成命中 ${s.damage} 次",
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Text("MVP：${s.mvp}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
    Text(
        "存活：" + s.alive.joinToString(" / ") { FleetCatalog.label(it) }.ifEmpty { "无" },
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        "击沉：" + s.sunk.joinToString(" / ") { FleetCatalog.label(it) }.ifEmpty { "无" },
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.error
    )
}
