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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archipelago.battleship.data.qr.QrCodec
import com.archipelago.battleship.domain.model.ChatMessage
import com.archipelago.battleship.domain.model.FleetCatalog
import com.archipelago.battleship.domain.model.RoomPhase
import com.archipelago.battleship.domain.model.Side
import com.archipelago.battleship.ui.GameViewModel
import com.archipelago.battleship.ui.Mode
import com.archipelago.battleship.ui.Screen
import com.archipelago.battleship.ui.UiState
import com.archipelago.battleship.ui.components.ChipButton
import com.archipelago.battleship.ui.components.HelpSheet
import com.archipelago.battleship.ui.components.HelpTopic
import com.archipelago.battleship.ui.components.ScreenTopBar
import com.archipelago.battleship.ui.components.SectionTitle
import com.archipelago.battleship.ui.components.SurfaceCard

private val QUICK_EMOJI = listOf("👍 收到", "⚓ 就位", "🎯 开火", "🤝 好局", "😄 哈哈", "⏳ 稍等")

@Composable
fun LobbyScreen(vm: GameViewModel, state: UiState) {
    val view = state.view
    var chatText by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }
    val qrBitmap = remember(state.roomAddress) {
        if (state.roomAddress.isEmpty()) null else QrCodec.generate(state.roomAddress, 420)
    }

    if (view == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            ScreenTopBar(
                title = "正在同步战况",
                subtitle = state.connection,
                onHelp = { showHelp = true }
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "身份：${if (state.mode == Mode.GUEST) "客机" else "房主"} · 席位 ${if (state.mySide == Side.A) "A" else "B"}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(10.dp))
            if (state.mode == Mode.GUEST) {
                Button(onClick = { vm.requestSync() }, modifier = Modifier.fillMaxWidth()) {
                    Text("重新同步")
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(onClick = { vm.requestLeaveRoom() }, modifier = Modifier.fillMaxWidth()) {
                Text("返回首页")
            }
        }
        if (showHelp) {
            HelpSheet(HelpTopic.ONLINE, state.theme) { showHelp = false }
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
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        ScreenTopBar(
            title = when (view.phase) {
                RoomPhase.ROOM_LOBBY -> "战前准备"
                RoomPhase.IN_BATTLE -> "战斗中"
                RoomPhase.POST_BATTLE_LOBBY -> "本局结算"
            },
            subtitle = "${view.config.size}×${view.config.size} · ${if (view.config.mountains) "随机群岛" else "纯海"} · ${state.connection}",
            onHelp = { showHelp = true }
        )
        Spacer(Modifier.height(10.dp))

        SurfaceCard {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "房主 ${view.hostName}",
                    fontSize = 12.sp,
                    fontWeight = if (view.hostReady) FontWeight.Bold else FontWeight.Normal,
                    color = if (view.hostReady) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (view.hostReady) "已准备" else "未准备",
                    fontSize = 12.sp,
                    color = if (view.hostReady) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(
                    "客机 ${view.guestName}",
                    fontSize = 12.sp,
                    fontWeight = if (view.guestReady) FontWeight.Bold else FontWeight.Normal,
                    color = if (view.guestReady) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (view.guestReady) "已准备" else "未准备",
                    fontSize = 12.sp,
                    color = if (view.guestReady) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.toggleTheme() }, modifier = Modifier.weight(1f)) {
                Text("切换主题")
            }
            OutlinedButton(onClick = { vm.requestLeaveRoom() }, modifier = Modifier.weight(1f)) {
                Text("离开房间")
            }
        }
        Spacer(Modifier.height(8.dp))

        if (qrBitmap != null && state.mode == Mode.HOST) {
            SectionTitle("房间二维码")
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        bitmap = qrBitmap,
                        contentDescription = "房间二维码",
                        modifier = Modifier.size(150.dp).padding(4.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            state.roomAddress,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        SectionTitle("规则配置")
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
                    Text("随机群岛", fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        SectionTitle("舰队编成")
        Text(
            (if (state.mySide == Side.A) view.hostFleet else view.guestFleet)
                .all().joinToString(" / ") { FleetCatalog.label(it) },
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(6.dp))
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
                Text(if (view.hostReady && view.guestReady) "开始战斗" else "等待双方准备")
            }
        } else {
            Text(
                "等待房主开始…",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }
        if (view.phase == RoomPhase.POST_BATTLE_LOBBY) {
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { vm.returnToLobby() }, modifier = Modifier.fillMaxWidth()) {
                Text("返回房间")
            }
        }
        Spacer(Modifier.height(10.dp))

        SectionTitle(if (chatLocked) "大厅聊天 · 战斗中禁言" else "大厅聊天")
        if (!chatLocked) {
            SurfaceCard {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(150.dp)) {
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
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showHelp) {
        HelpSheet(HelpTopic.ONLINE, state.theme) { showHelp = false }
    }
}
