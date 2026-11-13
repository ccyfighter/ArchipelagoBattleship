package com.archipelago.battleship.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archipelago.battleship.data.qr.QrCodec
import com.archipelago.battleship.domain.engine.Rules
import com.archipelago.battleship.domain.model.ChatMessage
import com.archipelago.battleship.domain.model.RoomPhase
import com.archipelago.battleship.domain.model.Side
import com.archipelago.battleship.ui.GameViewModel
import com.archipelago.battleship.ui.Mode
import com.archipelago.battleship.ui.Screen
import com.archipelago.battleship.ui.UiState
import com.archipelago.battleship.ui.components.ChipButton
import com.archipelago.battleship.ui.components.InfoLine
import com.archipelago.battleship.ui.components.SectionTitle

private val QUICK_EMOJI = listOf("👍 收到", "⚓ 就位", "🎯 开火", "🤝 好局", "😄 哈哈", "⏳ 稍等")

@Composable
fun LobbyScreen(vm: GameViewModel, state: UiState) {
    val view = state.view
    var chatText by remember { mutableStateOf("") }
    val qrBitmap = remember(state.roomAddress) {
        if (state.roomAddress.isEmpty()) null else QrCodec.generate(state.roomAddress, 420)
    }

    if (view == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SectionTitle("正在同步房间战况…")
            InfoLine(state.connection)
            InfoLine("身份：${if (state.mode == Mode.GUEST) "客机" else "房主"} · 我方席位：${if (state.mySide == Side.A) "A" else "B"}")
            Spacer(Modifier.height(10.dp))
            if (state.mode == Mode.GUEST) {
                Button(onClick = { vm.requestSync() }, modifier = Modifier.fillMaxWidth()) {
                    Text("重新向房主同步战况")
                }
                Spacer(Modifier.height(8.dp))
                InfoLine("若一直停在这里：① 两台手机装同一版本 APK；② 房主房间仍开着；③ 两机在同一 Wi-Fi / 热点；④ 必要时让房主重开房间后重新扫码。")
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(onClick = { vm.leaveRoom() }, modifier = Modifier.fillMaxWidth()) {
                Text("返回首页")
            }
        }
        return
    }

    val canConfigure = state.mode != Mode.GUEST
    val myReady = if (state.mySide == Side.A) view.hostReady else view.guestReady
    val chatLocked = view.phase == RoomPhase.IN_BATTLE

    fun send(text: String, emoji: Boolean = false) {
        if (text.isBlank()) return
        vm.sendChat(text, emoji)
        chatText = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        SectionTitle(
            when (view.phase) {
                RoomPhase.ROOM_LOBBY -> "战前准备大厅 · ROOM_LOBBY"
                RoomPhase.IN_BATTLE -> "战斗中 · IN_BATTLE"
                RoomPhase.POST_BATTLE_LOBBY -> "单局结算 · 房间保持 · POST_BATTLE_LOBBY"
            }
        )
        InfoLine(state.connection)
        InfoLine("地图：${view.config.size}×${view.config.size} · ${if (view.config.mountains) "随机群岛（≤20%）" else "纯海无山"}")
        InfoLine("房主 ${view.hostName}${if (view.hostReady) " ✔已准备" else " …未准备"}   ｜   客机 ${view.guestName}${if (view.guestReady) " ✔已准备" else " …未准备"}")
        Spacer(Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.toggleTheme() }, modifier = Modifier.weight(1f)) { Text("切换主题") }
            OutlinedButton(onClick = { vm.leaveRoom() }, modifier = Modifier.weight(1f)) { Text("离开房间") }
        }
        Spacer(Modifier.height(8.dp))

        if (qrBitmap != null && state.mode == Mode.HOST) {
            SectionTitle("房主二维码 · 客机点『扫码加入』秒连")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    bitmap = qrBitmap,
                    contentDescription = "房间二维码",
                    modifier = Modifier.size(170.dp).padding(4.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    InfoLine(state.roomAddress)
                    InfoLine("内含 IP:Port + 房间密钥，仅在同一 Wi-Fi / 热点内有效。")
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        SectionTitle("房主规则配置")
        if (canConfigure) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChipButton("10×10 快棋", view.config.size == 10) { vm.setConfig(10, false) }
                ChipButton("12×12 标准", view.config.size == 12) { vm.setConfig(12, view.config.mountains) }
                ChipButton("14×14 大战场", view.config.size == 14) { vm.setConfig(14, view.config.mountains) }
            }
            if (view.config.size >= 12) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = view.config.mountains,
                        onCheckedChange = { vm.setConfig(view.config.size, it) }
                    )
                    Text("随机群岛（取消勾选 = 纯海无山）", fontSize = 13.sp)
                }
                InfoLine("3~6 座随机小岛，单岛 1~6 格，可贴边角，总面积 ≤20%；山地不可部署 / 攻击 / 阻挡鱼雷，周边 1 圈浅滩禁停 3~5 格大舰与潜艇。")
            }
            InfoLine("每回合阶段二最多 ${Rules.BASE_ATTACKS_PER_TURN} 次攻击（且不超过存活舰船数）；改动规则会实时同步并重置双方准备与布阵。")
        } else {
            InfoLine("由房主设置，配置变动会实时同步到这里。")
        }
        Spacer(Modifier.height(8.dp))

        SectionTitle("舰队编成与布阵（5+4+3+2+1）")
        InfoLine("我方：" + (if (state.mySide == Side.A) view.hostFleet else view.guestFleet)
            .all().joinToString(" / ") { com.archipelago.battleship.domain.model.FleetCatalog.label(it) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.navigate(Screen.FLEET) }, modifier = Modifier.weight(1f)) {
                Text("选船 / 布阵")
            }
            OutlinedButton(onClick = { vm.setReady(!myReady) }, modifier = Modifier.weight(1f)) {
                Text(if (myReady) "取消准备" else "准备完毕")
            }
        }
        Spacer(Modifier.height(6.dp))
        if (canConfigure) {
            Button(
                onClick = { vm.startBattle() },
                enabled = view.hostReady && view.guestReady,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("开始战斗（需双方均准备）")
            }
        } else {
            InfoLine("等待房主点击『开始战斗』…")
        }
        if (view.phase == RoomPhase.POST_BATTLE_LOBBY) {
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { vm.returnToLobby() }, modifier = Modifier.fillMaxWidth()) {
                Text("返回房间（不断开连接，聊天已解锁）")
            }
        }
        Spacer(Modifier.height(10.dp))

        SectionTitle(if (chatLocked) "大厅聊天（战斗中已锁定 / 禁言）" else "大厅聊天 + 快捷战术表情")
        if (chatLocked) {
            InfoLine("聊天输入框与发送功能已禁用，界面专注棋盘与战况播报。")
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().height(170.dp)) {
                items(view.chat) { m: ChatMessage ->
                    Text(
                        "${m.name}：${m.text}",
                        fontSize = 12.sp,
                        color = if (m.side == state.mySide) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                QUICK_EMOJI.forEach { e ->
                    TextButton(onClick = { send(e, true) }) { Text(e, fontSize = 12.sp) }
                }
            }
            OutlinedTextField(
                value = chatText,
                onValueChange = { chatText = it },
                label = { Text("输入消息…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(chatText) }),
                trailingIcon = {
                    TextButton(onClick = { send(chatText) }) { Text("发送") }
                }
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}
