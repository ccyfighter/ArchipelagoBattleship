package com.archipelago.battleship.data.net

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

/** 二维码 / NSD 里携带的房间地址，格式 ARCHIPELAGO|1|ip|port|key */
data class RoomAddress(val ip: String, val port: Int, val key: String) {

    fun encode(): String = "ARCHIPELAGO|1|$ip|$port|$key"

    companion object {
        fun parse(raw: String): RoomAddress? {
            val p = raw.trim().split("|")
            if (p.size != 5) return null
            if (p[0] != "ARCHIPELAGO" || p[1] != "1") return null
            val port = p[3].toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            if (p[2].isBlank() || p[4].isBlank()) return null
            return RoomAddress(p[2].trim(), port, p[4].trim())
        }
    }
}

/** 不依赖 WifiManager 过期 API，直接枚举网卡拿热点 / Wi-Fi 的 IPv4。 */
object LanIp {
    fun best(): String {
        val preferred = listOf("wlan", "ap", "softap", "eth")
        val found = ArrayList<String>()
        try {
            val e = NetworkInterface.getNetworkInterfaces()
            while (e.hasMoreElements()) {
                val ni = e.nextElement()
                val name = ni.name.lowercase()
                val isPreferred = preferred.any { name.startsWith(it) }
                val addrs = ni.inetAddresses
                while (addrs.hasMoreElements()) {
                    val a = addrs.nextElement()
                    if (a is Inet4Address && !a.isLoopbackAddress && a.isSiteLocalAddress) {
                        val ip = a.hostAddress ?: continue
                        if (isPreferred) return ip
                        found.add(ip)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return found.firstOrNull() ?: "127.0.0.1"
    }
}

/**
 * 一个 Socket 长连接端点。房主用 host() 监听 + 接受一个客机，客机用 join() 直连。
 * 所有回调都在 IO 线程触发，调用方自行切回主线程。
 */
class LanEndpoint(
    private val onPacket: (Packet) -> Unit,
    private val onConnected: () -> Unit,
    private val onClosed: (String) -> Unit
) {
    private var server: ServerSocket? = null
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private val lock = Any()

    @Volatile
    private var running = true

    @Volatile
    var peerAttached: Boolean = false
        private set

    /** 绑定随机端口并开始监听，返回实际端口（二维码 / NSD 用）。 */
    fun host(): Int {
        val s = ServerSocket(0)
        server = s
        Thread({
            try {
                while (running) {
                    val c = s.accept()
                    if (!running) {
                        c.close()
                        break
                    }
                    attach(c)
                    onConnected()
                    break
                }
            } catch (e: Exception) {
                if (running) onClosed("房间监听中断：${e.message}")
            }
        }, "archipelago-accept").start()
        return s.localPort
    }

    /** 客机直连房主。 */
    fun join(ip: String, port: Int): Boolean = try {
        val c = Socket()
        c.connect(InetSocketAddress(ip, port), 6000)
        c.tcpNoDelay = true
        attach(c)
        onConnected()
        true
    } catch (e: Exception) {
        onClosed("连接失败：${e.message}")
        false
    }

    private fun attach(c: Socket) {
        socket = c
        val w = BufferedWriter(OutputStreamWriter(c.getOutputStream(), Charsets.UTF_8))
        val reader = BufferedReader(InputStreamReader(c.getInputStream(), Charsets.UTF_8))
        synchronized(lock) { writer = w }
        peerAttached = true
        Thread({
            try {
                while (running) {
                    val line = reader.readLine() ?: break
                    val packet = Wire.decode(line) ?: continue
                    onPacket(packet)
                }
            } catch (_: Exception) {
            } finally {
                if (running) onClosed("对端已断开连接")
            }
        }, "archipelago-read").start()
    }

    fun send(packet: Packet) {
        val w = synchronized(lock) { writer } ?: return
        try {
            synchronized(w) {
                w.write(Wire.encode(packet))
                w.newLine()
                w.flush()
            }
        } catch (e: Exception) {
            onClosed("发送失败：${e.message}")
        }
    }

    fun close() {
        running = false
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        try {
            server?.close()
        } catch (_: Exception) {
        }
        synchronized(lock) { writer = null }
        socket = null
        server = null
        peerAttached = false
    }
}
