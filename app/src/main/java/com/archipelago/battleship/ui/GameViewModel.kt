package com.archipelago.battleship.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archipelago.battleship.core.AppThemeMode
import com.archipelago.battleship.data.net.FoundRoom
import com.archipelago.battleship.data.net.LanEndpoint
import com.archipelago.battleship.data.net.LanIp
import com.archipelago.battleship.data.net.NsdDiscovery
import com.archipelago.battleship.data.net.Packet
import com.archipelago.battleship.data.net.RoomAddress
import com.archipelago.battleship.domain.engine.AiEngine
import com.archipelago.battleship.domain.engine.GameEngine
import com.archipelago.battleship.domain.engine.Rules
import com.archipelago.battleship.domain.model.Coord
import com.archipelago.battleship.domain.model.FleetConfig
import com.archipelago.battleship.domain.model.GameAction
import com.archipelago.battleship.domain.model.GameConfig
import com.archipelago.battleship.domain.model.GameView
import com.archipelago.battleship.domain.model.Orientation
import com.archipelago.battleship.domain.model.Placement
import com.archipelago.battleship.domain.model.RoomPhase
import com.archipelago.battleship.domain.model.Side
import com.archipelago.battleship.domain.model.SkillKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class Screen { HOME, LOBBY, FLEET, BATTLE, SCANNER }
enum class Mode { LOCAL, HOST, GUEST }

data class UiState(
    val screen: Screen = Screen.HOME,
    val mode: Mode = Mode.LOCAL,
    val theme: AppThemeMode = AppThemeMode.PAPER,
    val mySide: Side = Side.A,
    val view: GameView? = null,
    val connection: String = "未连接",
    val roomAddress: String = "",
    val roomKey: String = "",
    val discovered: List<FoundRoom> = emptyList(),
    val discovering: Boolean = false,
    val guestConnected: Boolean = false,
    val timerSeconds: Int = 180,
    val toast: String = "",
    val manualJoin: Boolean = false
)

/**
 * MVVM + UDF 的中间层。
 * - LOCAL / HOST：本地持有权威 GameEngine，所有 GameAction 直接 dispatch。
 * - HOST：另起 ServerSocket + NSD，把 viewFor(B) 通过 Packet.State 推给客机。
 * - GUEST：没有引擎，只把 GameAction 打包发给房主，渲染房主下发的脱敏 GameView。
 */
class GameViewModel : ViewModel() {

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var engine: GameEngine? = null
    private var ai: AiEngine? = null
    private var endpoint: LanEndpoint? = null
    private var nsd: NsdDiscovery? = null
    private var timerJob: Job? = null
    private var turnKey: Triple<RoomPhase, Int, Side>? = null
    private var aiRunning = false

    /* ---------------- 生命周期 ---------------- */

    override fun onCleared() {
        cleanup()
        super.onCleared()
    }

    private fun cleanup() {
        timerJob?.cancel()
        timerJob = null
        turnKey = null
        aiRunning = false
        try {
            endpoint?.close()
        } catch (_: Exception) {
        }
        try {
            nsd?.stopAll()
        } catch (_: Exception) {
        }
        endpoint = null
        nsd = null
        engine = null
        ai = null
    }

    /* ---------------- 导航 / 外观 ---------------- */

    fun navigate(screen: Screen) {
        _ui.value = _ui.value.copy(screen = screen)
    }

    fun toggleTheme() {
        _ui.value = _ui.value.copy(
            theme = if (_ui.value.theme == AppThemeMode.PAPER) AppThemeMode.SONAR else AppThemeMode.PAPER
        )
    }

    fun showManualJoin(show: Boolean) {
        _ui.value = _ui.value.copy(manualJoin = show)
    }

    fun openScanner() {
        _ui.value = _ui.value.copy(screen = Screen.SCANNER)
    }

    fun closeScanner() {
        _ui.value = _ui.value.copy(screen = Screen.HOME)
    }

    fun consumeToast() {
        _ui.value = _ui.value.copy(toast = "")
    }

    private fun toast(message: String) {
        _ui.value = _ui.value.copy(toast = message)
    }

    fun leaveRoom() {
        cleanup()
        _ui.value = UiState(theme = _ui.value.theme)
    }

    /* ---------------- 三种入口 ---------------- */

    fun startLocal() {
        cleanup()
        val cfg = GameConfig(seed = Random.nextLong(1L, 1_000_000_000L))
        val e = GameEngine(cfg)
        engine = e
        ai = AiEngine()
        _ui.value = UiState(
            screen = Screen.LOBBY,
            mode = Mode.LOCAL,
            theme = _ui.value.theme,
            mySide = Side.A,
            connection = "单机模式 · AI 指挥官已入房（完全断网可用）"
        )
        e.dispatch(Side.B, GameAction.SetName("AI 指挥官"))
        e.autoPrepare(Side.B, cfg.seed + 7)
        publish()
    }

    fun createRoom(context: Context) {
        cleanup()
        val key = Random.nextInt(100000, 999999).toString()
        val cfg = GameConfig(seed = Random.nextLong(1L, 1_000_000_000L))
        val e = GameEngine(cfg)
        engine = e
        val link = LanEndpoint(
            onPacket = { p -> handlePacket(p) },
            onConnected = {
                viewModelScope.launch { _ui.value = _ui.value.copy(connection = "客机已连入，等待握手…") }
            },
            onClosed = { msg -> onLinkClosed(msg) }
        )
        endpoint = link
        val port = link.host()
        val ip = LanIp.best()
        val address = RoomAddress(ip, port, key)
        _ui.value = UiState(
            screen = Screen.LOBBY,
            mode = Mode.HOST,
            theme = _ui.value.theme,
            mySide = Side.A,
            roomAddress = address.encode(),
            roomKey = key,
            connection = "房间 $ip:$port · 密钥 $key · 等待客机加入"
        )
        publish()
        val d = NsdDiscovery(context.applicationContext)
        nsd = d
        d.register(key, port) { ok, msg ->
            viewModelScope.launch {
                _ui.value = _ui.value.copy(
                    connection = if (ok) "房间已广播（mDNS）：$ip:$port · 密钥 $key" else msg
                )
            }
        }
    }

    fun startDiscovery(context: Context) {
        nsd?.stopAll()
        val d = NsdDiscovery(context.applicationContext)
        nsd = d
        _ui.value = _ui.value.copy(discovering = true, discovered = emptyList(), connection = "正在搜索附近房间…")
        d.discover(
            onFound = { room ->
                viewModelScope.launch {
                    val cur = _ui.value.discovered
                    if (cur.none { it.key == room.key }) {
                        _ui.value = _ui.value.copy(
                            discovered = cur + room,
                            connection = "已发现 ${cur.size + 1} 个房间"
                        )
                    }
                }
            },
            onError = { msg -> viewModelScope.launch { toast(msg) } }
        )
    }

    fun stopDiscovery() {
        nsd?.stopAll()
        nsd = null
        _ui.value = _ui.value.copy(discovering = false, connection = "已停止搜索")
    }

    fun joinFound(room: FoundRoom) = connectGuest(room.ip, room.port, room.key)

    fun joinManual(raw: String) {
        val text = raw.trim()
        val address = if (text.startsWith("ARCHIPELAGO")) {
            RoomAddress.parse(text)
        } else {
            val p = text.split(":")
            if (p.size >= 3) {
                val port = p[1].trim().toIntOrNull()
                if (port != null && p[0].trim().isNotEmpty() && p[2].trim().isNotEmpty()) {
                    RoomAddress(p[0].trim(), port, p[2].trim())
                } else null
            } else null
        }
        if (address == null) {
            toast("格式：IP:端口:房间密钥，或直接粘贴二维码文本")
            return
        }
        _ui.value = _ui.value.copy(manualJoin = false)
        connectGuest(address.ip, address.port, address.key)
    }

    fun onScanResult(raw: String) {
        val address = RoomAddress.parse(raw)
        if (address == null) {
            _ui.value = _ui.value.copy(screen = Screen.HOME)
            toast("这不是本游戏的房间二维码")
            return
        }
        _ui.value = _ui.value.copy(screen = Screen.HOME)
        connectGuest(address.ip, address.port, address.key)
    }

    private fun connectGuest(ip: String, port: Int, key: String) {
        cleanup()
        val link = LanEndpoint(
            onPacket = { p -> handlePacket(p) },
            onConnected = {
                if (_ui.value.mode == Mode.GUEST) endpoint?.send(Packet.Hello("客机", key))
            },
            onClosed = { msg -> onLinkClosed(msg) }
        )
        endpoint = link
        _ui.value = UiState(
            screen = Screen.HOME,
            mode = Mode.GUEST,
            theme = _ui.value.theme,
            mySide = Side.B,
            roomKey = key,
            connection = "正在连接 $ip:$port …"
        )
        viewModelScope.launch(Dispatchers.IO) {
            val ok = link.join(ip, port)
            if (!ok) {
                withContext(Dispatchers.Main) {
                    toast("连不上房主，请确认两机在同一 Wi-Fi / 热点且 IP 正确")
                }
            }
        }
    }

    private fun onLinkClosed(message: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(connection = "连接已断开")
            toast(message)
        }
    }

    /* ---------------- 收包 ---------------- */

    private fun handlePacket(packet: Packet) {
        when (packet) {
            is Packet.Hello -> {
                if (_ui.value.mode != Mode.HOST) return
                if (packet.key != _ui.value.roomKey) {
                    endpoint?.send(Packet.ServerError("房间密钥不匹配"))
                    return
                }
                val e = engine ?: return
                e.dispatch(Side.B, GameAction.SetName(packet.name))
                endpoint?.send(Packet.Assign(true, Side.B, e.state.config, ""))
                viewModelScope.launch {
                    _ui.value = _ui.value.copy(
                        guestConnected = true,
                        connection = "客机 ${packet.name} 已加入房间（保持长连接）"
                    )
                    publish()
                }
            }

            is Packet.Assign -> {
                if (packet.ok) {
                    _ui.value = _ui.value.copy(
                        mySide = packet.side,
                        screen = Screen.LOBBY,
                        connection = "已加入房间，正在同步战前大厅…"
                    )
                    nsd?.stopAll()
                    nsd = null
                } else {
                    toast(packet.reason)
                }
            }

            is Packet.State -> {
                _ui.value = _ui.value.copy(view = packet.view)
                onViewUpdated()
            }

            is Packet.Action -> {
                if (_ui.value.mode == Mode.HOST) {
                    viewModelScope.launch {
                        engine?.dispatch(Side.B, packet.action)
                        publish()
                    }
                }
            }

            is Packet.Tick -> {
                _ui.value = _ui.value.copy(timerSeconds = packet.seconds)
            }

            is Packet.ServerError -> toast(packet.message)

            Packet.Bye -> toast("房主已关闭房间")
        }
    }

    /* ---------------- 发动作 / 状态下发 ---------------- */

    private fun dispatch(action: GameAction) {
        if (_ui.value.mode == Mode.GUEST) {
            endpoint?.send(Packet.Action(action))
            return
        }
        val e = engine ?: return
        e.dispatch(_ui.value.mySide, action)
        publish()
    }

    private fun publish() {
        val e = engine ?: return
        val side = _ui.value.mySide
        _ui.value = _ui.value.copy(view = e.viewFor(side))
        if (_ui.value.mode == Mode.HOST && _ui.value.guestConnected) {
            endpoint?.send(Packet.State(e.viewFor(Side.B)))
        }
        onViewUpdated()
    }

    private fun onViewUpdated() {
        val v = _ui.value.view ?: return
        val screen = _ui.value.screen
        if (screen != Screen.SCANNER) {
            if (v.phase == RoomPhase.IN_BATTLE && screen != Screen.BATTLE) {
                _ui.value = _ui.value.copy(screen = Screen.BATTLE)
            }
            if (v.phase == RoomPhase.ROOM_LOBBY && screen == Screen.BATTLE) {
                _ui.value = _ui.value.copy(screen = Screen.LOBBY)
            }
        }
        val key = Triple(v.phase, v.round, v.turn)
        if (key != turnKey) {
            turnKey = key
            if (_ui.value.mode != Mode.GUEST) {
                if (v.phase == RoomPhase.IN_BATTLE) startTimer()
                else {
                    timerJob?.cancel()
                    _ui.value = _ui.value.copy(timerSeconds = Rules.TURN_SECONDS)
                }
            }
        }
        maybeRunAi()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var sec = Rules.TURN_SECONDS
            while (true) {
                _ui.value = _ui.value.copy(timerSeconds = sec)
                if (_ui.value.mode == Mode.HOST && _ui.value.guestConnected) {
                    endpoint?.send(Packet.Tick(sec))
                }
                delay(1000)
                sec--
                if (sec < 0) {
                    val e = engine
                    if (e != null && _ui.value.mode != Mode.GUEST) {
                        e.autoRandomAttacks(_ui.value.mySide)
                        publish()
                    }
                    break
                }
            }
        }
    }

    private fun maybeRunAi() {
        if (_ui.value.mode != Mode.LOCAL || aiRunning) return
        val v = _ui.value.view ?: return
        if (v.phase != RoomPhase.IN_BATTLE || v.turn != Side.B) return
        val e = engine ?: return
        val bot = ai ?: return
        aiRunning = true
        viewModelScope.launch {
            delay(450)
            bot.playTurn(e, Side.B) { publish() }
            aiRunning = false
        }
    }

    /* ---------------- 房主规则 / 大厅互动 ---------------- */

    fun setConfig(size: Int, mountains: Boolean) {
        dispatch(GameAction.SetConfig(size, mountains))
        if (_ui.value.mode == Mode.LOCAL) rePrepareAi()
    }

    fun setFleetAndPlacements(fleet: FleetConfig, placements: List<Placement>) =
        dispatch(GameAction.SetFleet(fleet, placements))

    fun setReady(ready: Boolean) = dispatch(GameAction.SetReady(ready))

    fun startBattle() = dispatch(GameAction.StartBattle)

    fun returnToLobby() {
        dispatch(GameAction.ReturnToLobby)
        if (_ui.value.mode == Mode.LOCAL) rePrepareAi()
    }

    /** 单机模式：地图变化 / 回到房间会清空准备状态，AI 需要重新自动布阵并准备。 */
    private fun rePrepareAi() {
        val e = engine ?: return
        e.autoPrepare(Side.B, e.state.config.seed + 7)
        publish()
    }

    fun sendChat(text: String, emoji: Boolean = false) = dispatch(GameAction.Chat(text, emoji))

    /* ---------------- 战斗操作 ---------------- */

    fun fire(shipId: String, target: Coord) = dispatch(GameAction.Fire(shipId, target))

    fun useSkill(
        shipId: String,
        kind: SkillKind,
        targets: List<Coord>,
        orientation: Orientation? = null
    ) = dispatch(GameAction.Skill(shipId, kind, targets, orientation))

    fun skipTactical() = dispatch(GameAction.SkipTactical)

    fun endTurn() = dispatch(GameAction.EndTurn)

    fun undo() = dispatch(GameAction.Undo)

    fun redo() = dispatch(GameAction.Redo)
}
