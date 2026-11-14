package com.archipelago.battleship.ui

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.archipelago.battleship.core.AppThemeMode
import com.archipelago.battleship.core.BoardCanvas
import com.archipelago.battleship.domain.model.BattleEndReason
import com.archipelago.battleship.domain.model.BattleReplay
import com.archipelago.battleship.domain.model.BattleResult
import com.archipelago.battleship.domain.model.FleetCatalog
import com.archipelago.battleship.domain.model.RoomPhase
import com.archipelago.battleship.domain.model.ShotResult
import com.archipelago.battleship.domain.model.Side
import com.archipelago.battleship.domain.model.SideStats
import com.archipelago.battleship.ui.components.ChipButton
import com.archipelago.battleship.ui.components.SurfaceCard
import com.archipelago.battleship.ui.screens.BattleScreen
import com.archipelago.battleship.ui.screens.FleetScreen
import com.archipelago.battleship.ui.screens.HomeScreen
import com.archipelago.battleship.ui.screens.LobbyScreen
import com.archipelago.battleship.ui.screens.ScannerScreen
import kotlinx.coroutines.delay

private enum class ConfirmDialog { NONE, LEAVE_ROOM, BATTLE_MENU, RESIGN }

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

    var confirm by remember { mutableStateOf(ConfirmDialog.NONE) }
    // v13：结算页的「战场复盘」整屏界面
    var showReplay by remember { mutableStateOf(false) }
    // 大厅里的「离开房间」按钮与系统返回键共用同一个确认框
    LaunchedEffect(state.confirmLeave) {
        if (state.confirmLeave) confirm = ConfirmDialog.LEAVE_ROOM
    }
    val view = state.view
    val resultShowing = state.screen == Screen.BATTLE &&
        view != null && view.phase == RoomPhase.POST_BATTLE_LOBBY && view.result != null

    /**
     * 全页面系统返回键统一入口（HOME 交给系统默认退出 App）。
     * 扫码 / 布阵直接回上一层；大厅与战斗先弹二次确认，避免误触断线或误投。
     * 结算弹窗显示时交给 ResultDialog 自己的 onDismissRequest 处理。
     */
    BackHandler(
        enabled = state.screen != Screen.HOME && confirm == ConfirmDialog.NONE && !showReplay
    ) {
        when (state.screen) {
            Screen.SCANNER -> vm.closeScanner()
            Screen.FLEET -> vm.navigate(Screen.LOBBY)
            Screen.LOBBY -> confirm = ConfirmDialog.LEAVE_ROOM
            // 结算弹窗显示时按返回等同「返回房间」；若弹窗自己已消费返回键，这里不会被触发
            Screen.BATTLE -> if (resultShowing) vm.returnToLobby()
            else confirm = ConfirmDialog.BATTLE_MENU
            Screen.HOME -> Unit
        }
    }

    // 复盘整屏界面优先消费返回键：只关复盘，不会误触「返回房间」
    BackHandler(enabled = showReplay) { showReplay = false }

    // 房间解散说明弹窗优先级最高：按返回键只关说明，不会误退 App
    BackHandler(enabled = state.roomNotice.isNotEmpty()) {
        vm.dismissRoomNotice()
    }

    val primary = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val gridStep = with(density) { 56.dp.toPx() }
    val bandHeight = with(density) { 3.dp.toPx() }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 三套主题各自的全局底纹：都是矢量绘制，不与前景卡片抢视觉
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (state.theme) {
                AppThemeMode.PAPER -> {
                    val ink = primary.copy(alpha = 0.055f)
                    var x = 0f
                    while (x < size.width) {
                        drawLine(ink, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                        x += gridStep
                    }
                    var y = 0f
                    while (y < size.height) {
                        drawLine(ink, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        y += gridStep
                    }
                    // 泛旧坐标纸的两道折痕
                    val fold = primary.copy(alpha = 0.045f)
                    drawLine(fold, Offset(size.width * 0.33f, 0f), Offset(size.width * 0.33f, size.height), strokeWidth = 2f)
                    drawLine(fold, Offset(0f, size.height * 0.62f), Offset(size.width, size.height * 0.62f), strokeWidth = 2f)
                    drawRect(primary.copy(alpha = 0.10f), topLeft = Offset.Zero, size = Size(size.width, bandHeight))
                }
                AppThemeMode.WARROOM -> {
                    // 情报室作战桌：疏格 + 四角定位十字 + 台灯暖色顶带
                    val ink = primary.copy(alpha = 0.085f)
                    val step = gridStep * 2f
                    var x = step
                    while (x < size.width) {
                        drawLine(ink, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.2f)
                        x += step
                    }
                    var y = step
                    while (y < size.height) {
                        drawLine(ink, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.2f)
                        y += step
                    }
                    val mark = primary.copy(alpha = 0.28f)
                    val arm = 10f
                    for (cx in listOf(18f, size.width - 18f)) {
                        for (cy in listOf(18f, size.height - 18f)) {
                            drawLine(mark, Offset(cx - arm, cy), Offset(cx + arm, cy), strokeWidth = 1.4f)
                            drawLine(mark, Offset(cx, cy - arm), Offset(cx, cy + arm), strokeWidth = 1.4f)
                        }
                    }
                    drawRect(primary.copy(alpha = 0.16f), topLeft = Offset.Zero, size = Size(size.width, bandHeight * 2f))
                }
                AppThemeMode.SONAR -> {
                    // 舰桥雷达：同心环 + 十字基准 + 一条固定方位线
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val radius = maxOf(size.width, size.height) * 0.62f
                    for (k in 1..6) {
                        drawCircle(
                            primary.copy(alpha = 0.055f), radius = radius * k / 6f,
                            center = c, style = Stroke(1.4f)
                        )
                    }
                    drawLine(primary.copy(alpha = 0.075f), Offset(0f, c.y), Offset(size.width, c.y), strokeWidth = 1f)
                    drawLine(primary.copy(alpha = 0.075f), Offset(c.x, 0f), Offset(c.x, size.height), strokeWidth = 1f)
                    drawLine(
                        primary.copy(alpha = 0.16f), c,
                        Offset(c.x + radius * 0.72f, c.y - radius * 0.45f), strokeWidth = 2f
                    )
                    drawRect(primary.copy(alpha = 0.12f), topLeft = Offset.Zero, size = Size(size.width, bandHeight))
                }
            }
        }

        when (state.screen) {
            Screen.HOME -> HomeScreen(vm, state)
            Screen.LOBBY -> LobbyScreen(vm, state)
            Screen.FLEET -> FleetScreen(vm, state)
            Screen.BATTLE -> BattleScreen(vm, state, onResign = { confirm = ConfirmDialog.RESIGN })
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

        if (resultShowing && view != null && view.result != null) {
            ResultDialog(view.result, view.viewer, { showReplay = true }, vm::returnToLobby)
        }
        if (showReplay && view?.replay != null) {
            ReplayDialog(
                view!!.replay!!, view!!.viewer, state.theme, view!!.hostName, view!!.guestName
            ) { showReplay = false }
        }
    }

    when (confirm) {
        ConfirmDialog.LEAVE_ROOM -> ConfirmLeaveRoom(
            onConfirm = { confirm = ConfirmDialog.NONE; vm.cancelLeaveRoom(); vm.leaveRoom() },
            onDismiss = { confirm = ConfirmDialog.NONE; vm.cancelLeaveRoom() }
        )
        ConfirmDialog.BATTLE_MENU -> BattleBackMenu(
            onResign = { confirm = ConfirmDialog.RESIGN },
            onLeave = { confirm = ConfirmDialog.NONE; vm.cancelLeaveRoom(); vm.leaveRoom() },
            onDismiss = { confirm = ConfirmDialog.NONE }
        )
        ConfirmDialog.RESIGN -> ConfirmResign(
            onConfirm = { confirm = ConfirmDialog.NONE; vm.resign() },
            onDismiss = { confirm = ConfirmDialog.NONE }
        )
        ConfirmDialog.NONE -> Unit
    }

    if (state.roomNotice.isNotEmpty()) {
        RoomDissolvedDialog(
            reason = state.roomNotice,
            onDismiss = { vm.dismissRoomNotice() },
            onHostNewRoom = {
                vm.dismissRoomNotice()
                vm.createRoom(activity)
            }
        )
    }
}

/** 房主退出后房间解散：明确告知原因，并给客机「自己开新房」的快速出口。 */
@Composable
private fun RoomDissolvedDialog(
    reason: String,
    onDismiss: () -> Unit,
    onHostNewRoom: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("房间已解散", fontWeight = FontWeight.Bold) },
        text = {
            Text(reason, fontSize = 13.sp)
        },
        confirmButton = {
            Button(onClick = onHostNewRoom) { Text("新建房间", fontSize = 12.sp) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("返回首页") }
        }
    )
}

@Composable
private fun ConfirmLeaveRoom(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("离开房间？", fontWeight = FontWeight.Bold) },
        text = {
            Text("将返回首页并断开连接，未结束的对局不会保存。", fontSize = 13.sp)
        },
        confirmButton = { Button(onClick = onConfirm) { Text("确认离开") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("继续留在房间") } }
    )
}

@Composable
private fun BattleBackMenu(
    onResign: () -> Unit,
    onLeave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("离开对局？", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("可以投降结束本局，或直接返回首页。", fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onLeave) {
                    Text("返回首页", fontSize = 12.sp)
                }
            }
        },
        confirmButton = { Button(onClick = onResign) { Text("投降结束本局") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("继续战斗") } }
    )
}

@Composable
private fun ConfirmResign(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "确认投降？",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(
                "投降会立即结束本局并判对方获胜，本回合未完成的行动作废，且无法撤销。",
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("确认投降") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("继续战斗") } }
    )
}

@Composable
private fun ResultDialog(
    result: BattleResult,
    viewer: Side,
    onReplay: () -> Unit,
    onReturn: () -> Unit
) {
    val win = result.winner == viewer
    val resigned = result.reason == BattleEndReason.RESIGN
    val title = when {
        resigned && win -> "对方已投降 · 你获胜"
        resigned -> "你已投降 · 本局结束"
        win -> "胜利 · 敌方舰队全灭"
        else -> "战败 · 我方舰队全灭"
    }
    AlertDialog(
        // 结算弹窗不能再吞掉返回键：按系统返回等同「返回房间」
        onDismissRequest = onReturn,
        title = {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                color = if (win) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("总回合数：${result.rounds}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                if (resigned) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "结束方式：一方主动投降",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(8.dp))
                SurfaceCard(accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) {
                    StatBlock("房主", result.host)
                }
                Spacer(Modifier.height(6.dp))
                SurfaceCard(accent = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)) {
                    StatBlock("客机", result.guest)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onReplay) { Text("战场复盘", fontSize = 11.sp) }
        },
        confirmButton = {
            Button(onClick = onReturn) { Text("返回房间") }
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


/**
 * v13 结算复盘：只有战斗结束后 viewFor() 才下发双方完整 Board（含全部舰船与所有打击记录）。
 * 两行切换「房主 / 客机」×「部署图 / 打击图」，四种视角共用一块只读棋盘。
 * 部署图 = 舰船 + 地形；打击图 = 再叠加全部 shots / attackedPoints / 情报层。
 */
@Composable
private fun ReplayDialog(
    replay: BattleReplay,
    viewer: Side,
    mode: AppThemeMode,
    hostName: String,
    guestName: String,
    onClose: () -> Unit
) {
    var side by remember { mutableStateOf(viewer) }
    var strikes by remember { mutableStateOf(true) }
    val full = if (side == Side.A) replay.hostBoard else replay.guestBoard
    val board = if (strikes) full else full.copy(
        shots = emptyList(),
        attackedPoints = emptySet(),
        reconCells = emptySet(),
        sonarCells = emptySet(),
        radarCells = emptySet(),
        strikeCells = emptySet()
    )
    val hits = full.shots.count { it.result == ShotResult.HIT || it.result == ShotResult.SUNK }
    val misses = full.shots.count { it.result == ShotResult.MISS }
    val fleet = full.ships.joinToString(" / ") { FleetCatalog.label(it.type) }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "战场复盘",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onClose) { Text("关闭", fontSize = 11.sp) }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                ChipButton("房主 · $hostName", side == Side.A, true) { side = Side.A }
                ChipButton("客机 · $guestName", side == Side.B, true) { side = Side.B }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                ChipButton("部署图", !strikes, true) { strikes = false }
                ChipButton("打击图", strikes, true) { strikes = true }
            }
            Text(
                if (strikes) "打击图：全部炮击落点、战果与已沉舰"
                else "部署图：全部舰船、山地与浅滩，不含打击记录",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            BoardCanvas(
                board = board,
                mode = mode,
                calm = true,
                modifier = Modifier.fillMaxWidth().padding(2.dp)
            )
            Spacer(Modifier.height(8.dp))
            SurfaceCard(accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) {
                Text("编成：$fleet", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "受击记录：命中 $hits 次 · 未命中 $misses 次 · 共 ${full.shots.size} 条",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "已沉：" + full.ships.filter { it.sunk }
                        .joinToString(" / ") { FleetCatalog.label(it.type) }.ifEmpty { "无" },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
