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

    @Serializable
    @SerialName("assign")
    data class Assign(
        val ok: Boolean,
        val side: Side,
        val config: GameConfig,
        val reason: String = ""
    ) : Packet()

    @Serializable
    @SerialName("state")
    data class State(val view: GameView) : Packet()

    @Serializable
    @SerialName("action")
    data class Action(val action: GameAction) : Packet()

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

    fun decode(line: String): Packet? = runCatching {
        json.decodeFromString(Packet.serializer(), line)
    }.getOrNull()
}
