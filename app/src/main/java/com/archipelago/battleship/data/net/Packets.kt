package com.archipelago.battleship.data.net

import com.archipelago.battleship.domain.model.GameAction
import com.archipelago.battleship.domain.model.GameConfig
import com.archipelago.battleship.domain.model.GameView
import com.archipelago.battleship.domain.model.Side
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 局域网长连接上的行分隔 JSON 帧。每行一条，不含裸换行。 */
@Serializable
sealed class Packet {

    @Serializable
    @SerialName("hello")
    data class Hello(val name: String, val key: String) : Packet()

    /**
     * 握手应答。view 直接带上第一份完整战况，客机收到 Assign 就能立刻进大厅，
     * 不再依赖之后那条独立的 State 帧（帧丢失就会卡在「正在同步房间…」）。
     * 失败时 ok=false + reason，客机明确知道被拒绝的原因，不会静默停在首页。
     */
    @Serializable
    @SerialName("assign")
    data class Assign(
        val ok: Boolean,
        val side: Side,
        val config: GameConfig,
        val reason: String = "",
        val view: GameView? = null
    ) : Packet()

    @Serializable
    @SerialName("state")
    data class State(val view: GameView) : Packet()

    @Serializable
    @SerialName("action")
    data class Action(val action: GameAction) : Packet()

    /** 客机主动索要一份全量战况：握手包丢失 / 状态帧解析失败 / 切网后的兜底。 */
    @Serializable
    @SerialName("sync")
    object Sync : Packet()

    @Serializable
    @SerialName("tick")
    data class Tick(val seconds: Int) : Packet()

    @Serializable
    @SerialName("error")
    data class ServerError(val message: String) : Packet()

    @Serializable
    @SerialName("bye")
    object Bye : Packet()
}

object Wire {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "t"
    }

    fun encode(packet: Packet): String = json.encodeToString(Packet.serializer(), packet)

    /** 解析失败时把异常原因带出来，避免静默丢帧导致「连上了但什么都没有」。 */
    fun decodeDetailed(line: String): Pair<Packet?, String?> = try {
        json.decodeFromString(Packet.serializer(), line) to null
    } catch (e: Exception) {
        null to (e.message ?: e.toString())
    }

    fun decode(line: String): Packet? = decodeDetailed(line).first
}
