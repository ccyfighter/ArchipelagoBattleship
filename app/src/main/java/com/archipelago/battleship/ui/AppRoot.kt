package com.archipelago.battleship.ui

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archipelago.battleship.domain.model.BattleResult
import com.archipelago.battleship.domain.model.FleetCatalog
import com.archipelago.battleship.domain.model.RoomPhase
import com.archipelago.battleship.domain.model.Side
import com.archipelago.battleship.domain.model.SideStats
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

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (state.screen) {
            Screen.HOME -> HomeScreen(vm, state)
            Screen.LOBBY -> LobbyScreen(vm, state)
            Screen.FLEET -> FleetScreen(vm, state)
            Screen.BATTLE -> BattleScreen(vm, state)
            Screen.SCANNER -> ScannerScreen(activity, vm::onScanResult, vm::closeScanner)
        }

        if (state.toast.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(state.toast, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
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
    AlertDialog(
        onDismissRequest = { },
        title = {
            Text(if (result.winner == viewer) "胜利！敌方舰队全灭" else "战败 · 我方舰队全灭")
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("总回合数：${result.rounds}", fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                StatBlock("房主", result.host)
                Spacer(Modifier.height(6.dp))
                StatBlock("客机", result.guest)
                Spacer(Modifier.height(8.dp))
                Text(
                    "点击『返回房间』后：双方不断开连接、不退出房间，聊天窗口重新解锁；房主可重调地图 / 山地，双方可换船重新布阵，直接开启下一局。",
                    fontSize = 12.sp
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
    Text("$name · 造成命中 ${s.damage} 次 · MVP：${s.mvp}", fontSize = 13.sp)
    Text(
        "存活：" + s.alive.joinToString(" / ") { FleetCatalog.label(it) }.ifEmpty { "无" },
        fontSize = 12.sp
    )
    Text(
        "击沉：" + s.sunk.joinToString(" / ") { FleetCatalog.label(it) }.ifEmpty { "无" },
        fontSize = 12.sp
    )
}
