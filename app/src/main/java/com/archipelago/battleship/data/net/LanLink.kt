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
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

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
 * 一个 Socket 长连接端点。房主用 host() 监听，客机用 join() 直连。
 *
 * 关键点：**所有写操作都投递到专用单线程 outbound**，调用方可以在主线程安全地 send。
 * Android 的 StrictMode 会在主线程写 Socket 时抛 NetworkOnMainThreadException，
 * 之前 UI 层直接 send(Packet.State) 会被捕获成「连接已断开」，客机就永远拿不到战况。
 * 单写线程同时保证帧顺序（Assign 一定先于之后补发的 State）。
 */
class LanEndpoint(
    private val onPacket: (Packet) -> Unit,
    private val onConnected: () -> Unit,
    private val onClosed: (String) -> Unit,
    private val onMalformed: (String) -> Unit = {}
) {
    private var server: ServerSocket? = null
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private val lock = Any()

    private val outbound = Executors.newSingleThreadExecutor(
        ThreadFactory { r -> Thread(r, "archipelago-write").apply { isDaemon = true } }
    )

    @Volatile
    private var running = true

    // 每次 attach 递增，旧读线程退出时不会误报「对端已断开连接」
    @Volatile
    private var generation = 0

    @Volatile
    var peerAttached: Boolean = false
        private set

    /** 绑定随机端口并开始监听，返回实际端口（二维码 / NSD 用）。支持客机断线重连。 */
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
                }
            } catch (e: Exception) {
                if (running) onClosed("房间监听中断：${e.message}")
            }
        }, "archipelago-accept").apply { isDaemon = true }.start()
        return s.localPort
    }

    /** 客机直连房主。调用方应在 IO 线程执行。 */
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
        val w = BufferedWriter(OutputStreamWriter(c.getOutputStream(), Charsets.UTF_8))
        val reader = BufferedReader(InputStreamReader(c.getInputStream(), Charsets.UTF_8))
        val gen = synchronized(lock) {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
            socket = c
            writer = w
            peerAttached = true
            ++generation
        }
        Thread({
            try {
                while (running && generation == gen) {
                    val line = reader.readLine() ?: break
                    if (generation != gen) break
                    val (packet, error) = Wire.decodeDetailed(line)
                    if (packet == null) {
                        onMalformed("数据帧解析失败：${error ?: "未知错误"}｜${line.take(90)}")
                        continue
                    }
                    onPacket(packet)
                }
            } catch (_: Exception) {
            } finally {
                if (running && generation == gen) {
                    synchronized(lock) { writer = null }
                    peerAttached = false
                    onClosed("对端已断开连接")
                }
            }
        }, "archipelago-read-$gen").apply { isDaemon = true }.start()
    }

    /** 可在任意线程调用；返回是否成功投递（不保证对端已收到）。 */
    fun send(packet: Packet): Boolean {
        if (!running) return false
        val text = try {
            Wire.encode(packet)
        } catch (e: Exception) {
            onMalformed("状态序列化失败：${e.message}")
            return false
        }
        return try {
            outbound.execute {
                if (running) {
                    val w = synchronized(lock) { writer }
                    if (w != null) {
                        try {
                            synchronized(w) {
                                w.write(text)
                                w.newLine()
                                w.flush()
                            }
                        } catch (e: Exception) {
                            onClosed("发送失败：${e.message}")
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun close() {
        if (!running) return
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
        outbound.shutdownNow()
        socket = null
        server = null
        peerAttached = false
    }
}
