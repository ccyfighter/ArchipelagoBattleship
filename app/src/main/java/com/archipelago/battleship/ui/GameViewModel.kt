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
 *
 * 联机可靠性约定（v5）：
 * 1) 身份 / 密钥 / 客机在线标志全部放成员变量，不读易被覆盖的 UiState；
 * 2) 所有 UI 写入走 updateUi 原子读-改-写，网络回调统一切主线程；
 * 3) 握手 Assign 直接携带第一份完整 GameView，客机不必再等独立 State 帧；
 * 4) 客机可随时发 Packet.Sync 索要多轮全量状态，房主每次收到 Action 必回 State；
 * 5) 序列化 / 解析失败显式上报，不再静默丢帧。
 */
class GameViewModel : ViewModel() {

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val uiLock = Any()

    /** 跨线程统一入口：避免 IO 线程与主线程各自做 value = value.copy(...) 丢更新。 */
    private fun updateUi(block: (UiState) -> UiState) {
        synchronized(uiLock) { _ui.value = block(_ui.value) }
    }

    private var engine: GameEngine? = null
    private var ai: AiEngine? = null
    private var endpoint: LanEndpoint? = null
    private var nsd: NsdDiscovery? = null
    private var timerJob: Job? = null
    private var syncJob: Job? = null
    private var turnKey: Triple<RoomPhase, Int, Side>? = null
    private var aiRunning = false

    private var role: Mode = Mode.LOCAL
    private var mySide: Side = Side.A
    private var roomKey: String = ""
    private var joinKey: String = ""
    private var guestConnected: Boolean = false

    /* ---------------- 生命周期 ---------------- */

    override fun onCleared() {
        cleanup()
        super.onCleared()
    }

    private fun cleanup() {
        timerJob?.cancel()
        timerJob = null
        syncJob?.cancel()
        syncJob = null
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
        role = Mode.LOCAL
        mySide = Side.A
        roomKey = ""
        joinKey = ""
        guestConnected = false
    }

    /* ---------------- 导航 / 外观 ---------------- */

    fun navigate(screen: Screen) {
        updateUi { it.copy(screen = screen) }
    }

    fun toggleTheme() {
        updateUi {
            it.copy(theme = if (it.theme == AppThemeMode.PAPER) AppThemeMode.SONAR else AppThemeMode.PAPER)
        }
    }

    fun showManualJoin(show: Boolean) {
        updateUi { it.copy(manualJoin = show) }
    }

    fun openScanner() {
        updateUi { it.copy(screen = Screen.SCANNER) }
    }

    fun closeScanner() {
        updateUi { it.copy(screen = Screen.HOME) }
    }

    fun consumeToast() {
        updateUi { it.copy(toast = "") }
    }

    private fun toast(message: String) {
        updateUi { it.copy(toast = message) }
    }

    fun leaveRoom() {
        if (role == Mode.HOST && endpoint?.peerAttached == true) {
            endpoint?.send(Packet.Bye)
        }
        cleanup()
        updateUi { UiState(theme = it.theme) }
    }

    /* ---------------- 三种入口 ---------------- */

    fun startLocal() {
        cleanup()
        role = Mode.LOCAL
        mySide = Side.A
        val cfg = GameConfig(seed = Random.nextLong(1L, 1_000_000_000L))
        val e = GameEngine(cfg)
        engine = e
        ai = AiEngine()
        updateUi {
            UiState(
                screen = Screen.LOBBY,
                mode = Mode.LOCAL,
                theme = it.theme,
                mySide = Side.A,
                connection = "单机模式 · AI 指挥官已入房（完全断网可用）"
            )
        }
        e.dispatch(Side.B, GameAction.SetName("AI 指挥官"))
        e.autoPrepare(Side.B, cfg.seed + 7)
        publish()
    }

    fun createRoom(context: Context) {
        cleanup()
        val key = Random.nextInt(100000, 999999).toString()
        role = Mode.HOST
        mySide = Side.A
        roomKey = key
        guestConnected = false
        val cfg = GameConfig(seed = Random.nextLong(1L, 1_000_000_000L))
        val e = GameEngine(cfg)
        engine = e
        val link = LanEndpoint(
            onPacket = { p -> onNetworkPacket(p) },
            onConnected = { onNetworkConnected() },
            onClosed = { msg -> onLinkClosed(msg) },
            onMalformed = { msg -> onWireProblem(msg) }
        )
        endpoint = link
        val port = link.host()
        val ip = LanIp.best()
        val address = RoomAddress(ip, port, key)
        updateUi {
            UiState(
                screen = Screen.LOBBY,
                mode = Mode.HOST,
                theme = it.theme,
                mySide = Side.A,
                roomAddress = address.encode(),
                roomKey = key,
                connection = "房间 $ip:$port · 密钥 $key · 等待客机加入"
            )
        }
        publish()
        val d = NsdDiscovery(context.applicationContext)
        nsd = d
        d.register(key, port) { ok, msg ->
            // mDNS 注册回调常晚于客机连入，别把「客机已加入」覆盖掉
            updateUi {
                if (it.guestConnected) it
                else it.copy(connection = if (ok) "房间已广播（mDNS）：$ip:$port · 密钥 $key" else msg)
            }
        }
    }

    fun startDiscovery(context: Context) {
        nsd?.stopAll()
        val d = NsdDiscovery(context.applicationContext)
        nsd = d
        updateUi { it.copy(discovering = true, discovered = emptyList(), connection = "正在搜索附近房间…") }
        d.discover(
            onFound = { room ->
                val cur = _ui.value.discovered
                if (cur.none { r -> r.key == room.key }) {
                    updateUi {
                        it.copy(
                            discovered = it.discovered + room,
                            connection = "已发现 ${it.discovered.size + 1} 个房间"
                        )
                    }
                }
            },
            onError = { msg -> toast(msg) }
        )
    }

    fun stopDiscovery() {
        nsd?.stopAll()
        nsd = null
        updateUi { it.copy(discovering = false, connection = "已停止搜索") }
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
        updateUi { it.copy(manualJoin = false) }
        connectGuest(address.ip, address.port, address.key)
    }

    fun onScanResult(raw: String) {
        val address = RoomAddress.parse(raw)
        if (address == null) {
            updateUi { it.copy(screen = Screen.HOME) }
            toast("这不是本游戏的房间二维码")
            return
        }
        updateUi { it.copy(screen = Screen.HOME) }
        connectGuest(address.ip, address.port, address.key)
    }

    private fun connectGuest(ip: String, port: Int, key: String) {
        cleanup()
        role = Mode.GUEST
        mySide = Side.B
        joinKey = key
        guestConnected = false
        val link = LanEndpoint(
            onPacket = { p -> onNetworkPacket(p) },
            onConnected = { onNetworkConnected() },
            onClosed = { msg -> onLinkClosed(msg) },
            onMalformed = { msg -> onWireProblem(msg) }
        )
        endpoint = link
        updateUi {
            UiState(
                screen = Screen.HOME,
                mode = Mode.GUEST,
                theme = it.theme,
                mySide = Side.B,
                roomKey = key,
                connection = "正在连接 $ip:$port …"
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = link.join(ip, port)
            if (!ok) toast("连不上房主，请确认两机在同一 Wi-Fi / 热点且 IP 正确")
        }
    }

    /* ---------------- 连接回调 ---------------- */

    private fun onNetworkPacket(p: Packet) {
        // Socket 读线程 -> 主线程；所有状态变更集中在一个线程内完成
        viewModelScope.launch { handlePacket(p) }
    }

    private fun onNetworkConnected() {
        when (role) {
            Mode.GUEST -> {
                // 直接用加入时保存的密钥，不再读可能被覆盖的 UiState.mode
                endpoint?.send(Packet.Hello("客机", joinKey))
                updateUi { it.copy(connection = "已连上房主，正在握手（密钥 $joinKey）…") }
            }
            Mode.HOST -> updateUi { it.copy(connection = "客机已连入，等待握手…") }
            Mode.LOCAL -> Unit
        }
    }

    private fun onLinkClosed(message: String) {
        guestConnected = false
        updateUi { it.copy(connection = "连接已断开：$message", guestConnected = false) }
        toast(message)
    }

    private fun onWireProblem(message: String) {
        updateUi { it.copy(connection = "联机诊断：$message") }
        toast("联机异常：$message")
    }

    /* ---------------- 收包 ---------------- */

    private fun handlePacket(p: Packet) {
        when (p) {
            is Packet.Hello -> {
                if (role != Mode.HOST) return
                val e = engine ?: return
                if (p.key != roomKey) {
                    // 密钥不匹配也要回一个明确的拒绝，客机才不会静默卡在首页
                    endpoint?.send(
                        Packet.Assign(
                            false, Side.B, e.state.config,
                            "房间密钥不匹配（客机 ${p.key} / 房主 $roomKey）"
                        )
                    )
                    updateUi { it.copy(connection = "已拒绝密钥不匹配的客机：${p.key}") }
                    return
                }
                e.dispatch(Side.B, GameAction.SetName(p.name))
                guestConnected = true
                val snapshot = e.viewFor(Side.B)
                endpoint?.send(Packet.Assign(true, Side.B, e.state.config, "", snapshot))
                updateUi {
                    it.copy(
                        guestConnected = true,
                        connection = "客机 ${p.name} 已加入 · 初始战况已下发"
                    )
                }
                publish()
            }

            is Packet.Assign -> {
                if (role != Mode.GUEST) return
                if (!p.ok) {
                    val reason = p.reason.ifEmpty { "房主拒绝加入" }
                    cleanup()
                    updateUi { UiState(theme = it.theme, toast = "加入失败：$reason") }
                    return
                }
                mySide = p.side
                updateUi {
                    it.copy(
                        mySide = p.side,
                        screen = Screen.LOBBY,
                        view = p.view ?: it.view,
                        connection = "已加入房间（${if (p.side == Side.B) "客机位" else "房主位"}）· 长连接已建立"
                    )
                }
                nsd?.stopAll()
                nsd = null
                if (p.view != null) onViewUpdated()
                // 无论 Assign 有没有带 view，都要一份最新全量状态兜底
                endpoint?.send(Packet.Sync)
                startSyncWatch()
            }

            is Packet.State -> {
                if (role != Mode.GUEST) return
                val cur = _ui.value.view
                if (cur != null && p.view.revision < cur.revision) return
                updateUi { it.copy(view = p.view) }
                onViewUpdated()
            }

            is Packet.Action -> {
                if (role != Mode.HOST) return
                val e = engine ?: return
                e.dispatch(Side.B, p.action)
                // 动作即便被规则拒绝也要回状态，否则客机看不到提示、以为点了没反应
                publish()
            }

            Packet.Sync -> {
                if (role == Mode.HOST) publish()
                Unit
            }

            is Packet.Tick -> updateUi { it.copy(timerSeconds = p.seconds) }

            is Packet.ServerError -> toast(p.message)

            Packet.Bye -> {
                cleanup()
                updateUi { UiState(theme = it.theme, toast = "房主已关闭房间") }
            }
        }
    }

    /* ---------------- 发动作 / 状态下发 ---------------- */

    private fun dispatch(action: GameAction) {
        if (role == Mode.GUEST) {
            val link = endpoint
            if (link == null || !link.peerAttached) {
                toast("连接尚未建立，请点『重新同步』或返回首页重新加入")
                return
            }
            if (_ui.value.view == null) {
                link.send(Packet.Sync)
                startSyncWatch()
            }
            link.send(Packet.Action(action))
            return
        }
        val e = engine ?: return
        e.dispatch(mySide, action)
        publish()
    }

    private fun publish() {
        val e = engine ?: return
        val side = mySide
        updateUi { it.copy(view = e.viewFor(side)) }
        if (role == Mode.HOST && guestConnected && endpoint?.peerAttached == true) {
            val guestView = e.viewFor(Side.B)
            endpoint?.send(Packet.State(guestView))
        }
        onViewUpdated()
    }

    /** 客机在拿到第一份 view 之前，定时向房主索要状态，避免首帧丢失后死锁。 */
    private fun startSyncWatch() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            var attempt = 0
            while (attempt < 6 && role == Mode.GUEST && _ui.value.view == null) {
                delay(1200)
                if (role != Mode.GUEST || _ui.value.view != null) break
                attempt++
                updateUi { it.copy(connection = "已连入房间，正在向房主请求战况同步…（$attempt/6）") }
                endpoint?.send(Packet.Sync)
            }
            if (role == Mode.GUEST && _ui.value.view == null) {
                toast("房主未回应状态同步：请确认房间仍开着、两机在同一网络")
            }
        }
    }

    /** 大厅「重新同步」按钮 / 客机主动恢复。 */
    fun requestSync() {
        if (role != Mode.GUEST) {
            toast("当前不是客机模式")
            return
        }
        val link = endpoint
        if (link == null || !link.peerAttached) {
            toast("连接已断开，请返回首页重新加入房间")
            return
        }
        link.send(Packet.Sync)
        updateUi { it.copy(connection = "已向房主请求重新同步战况…") }
        startSyncWatch()
    }

    private fun onViewUpdated() {
        val s = _ui.value
        val v = s.view ?: return
        val screen = s.screen
        if (screen != Screen.SCANNER) {
            if (v.phase == RoomPhase.IN_BATTLE && screen != Screen.BATTLE) {
                updateUi { it.copy(screen = Screen.BATTLE) }
            }
            if (v.phase == RoomPhase.ROOM_LOBBY && screen == Screen.BATTLE) {
                updateUi { it.copy(screen = Screen.LOBBY) }
            }
        }
        val key = Triple(v.phase, v.round, v.turn)
        if (key != turnKey) {
            turnKey = key
            if (role != Mode.GUEST) {
                if (v.phase == RoomPhase.IN_BATTLE) startTimer()
                else {
                    timerJob?.cancel()
                    updateUi { it.copy(timerSeconds = Rules.TURN_SECONDS) }
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
                updateUi { it.copy(timerSeconds = sec) }
                if (role == Mode.HOST && guestConnected) endpoint?.send(Packet.Tick(sec))
                delay(1000)
                sec--
                if (sec < 0) {
                    val e = engine
                    if (e != null && role != Mode.GUEST) {
                        val turnSide = e.state.turn
                        e.autoRandomAttacks(turnSide)
                        publish()
                    }
                    break
                }
            }
        }
    }

    private fun maybeRunAi() {
        if (role != Mode.LOCAL || aiRunning) return
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
        if (role == Mode.LOCAL) rePrepareAi()
    }

    fun setFleetAndPlacements(fleet: FleetConfig, placements: List<Placement>) =
        dispatch(GameAction.SetFleet(fleet, placements))

    fun setReady(ready: Boolean) = dispatch(GameAction.SetReady(ready))

    fun startBattle() = dispatch(GameAction.StartBattle)

    fun returnToLobby() {
        dispatch(GameAction.ReturnToLobby)
        if (role == Mode.LOCAL) rePrepareAi()
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
