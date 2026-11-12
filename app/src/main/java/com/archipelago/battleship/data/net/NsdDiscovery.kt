package com.archipelago.battleship.data.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentLinkedQueue

data class FoundRoom(val name: String, val ip: String, val port: Int, val key: String)

/**
 * 原生 mDNS（NsdManager）自动发现，不依赖 Google Play Services。
 * 房间密钥直接编进 serviceName：archipelago_<key>。
 * 二维码 / 手动输入 IP 是并行的兜底通道，NSD 注册失败不影响直连。
 */
class NsdDiscovery(context: Context) {

    companion object {
        const val SERVICE_TYPE = "_archipelago._tcp"
        const val PREFIX = "archipelago_"
    }

    private val app = context.applicationContext
    private val nsd = app.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())
    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    @Volatile
    private var resolving = false

    @Volatile
    private var alive = true

    fun register(key: String, port: Int, onResult: (Boolean, String) -> Unit) {
        val info = NsdServiceInfo().apply {
            serviceName = PREFIX + key
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val cb = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                main.post { onResult(true, serviceInfo.serviceName) }
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                main.post { onResult(false, "mDNS 注册失败 code=$errorCode（可用二维码 / 手动 IP 加入）") }
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = cb
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, cb)
        } catch (e: Exception) {
            main.post { onResult(false, e.message ?: "mDNS 异常（可用二维码 / 手动 IP 加入）") }
        }
    }

    fun discover(onFound: (FoundRoom) -> Unit, onError: (String) -> Unit) {
        val cb = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val name = serviceInfo.serviceName ?: return
                if (!name.startsWith(PREFIX)) return
                resolveQueue.add(serviceInfo)
                pump(onFound, onError)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                main.post { onError("mDNS 发现失败 code=$errorCode") }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        discoveryListener = cb
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, cb)
        } catch (e: Exception) {
            main.post { onError(e.message ?: "mDNS 异常") }
        }
    }

    private fun pump(onFound: (FoundRoom) -> Unit, onError: (String) -> Unit) {
        if (!alive || resolving) return
        val next = resolveQueue.poll() ?: return
        resolving = true
        val cb = object : NsdManager.ResolveListener {
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                resolving = false
                val ip = serviceInfo.host?.hostAddress
                val name = serviceInfo.serviceName ?: ""
                if (!ip.isNullOrEmpty()) {
                    main.post { onFound(FoundRoom(name, ip, serviceInfo.port, name.removePrefix(PREFIX))) }
                }
                main.post { pump(onFound, onError) }
            }

            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                resolving = false
                main.post { onError("解析房间失败 code=$errorCode") }
                main.post { pump(onFound, onError) }
            }
        }
        try {
            nsd.resolveService(next, cb)
        } catch (e: Exception) {
            resolving = false
            main.post { onError(e.message ?: "解析异常") }
        }
    }

    fun stopAll() {
        alive = false
        registrationListener?.let { runCatching { nsd.unregisterService(it) } }
        discoveryListener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        registrationListener = null
        discoveryListener = null
        resolveQueue.clear()
        resolving = false
    }
}
