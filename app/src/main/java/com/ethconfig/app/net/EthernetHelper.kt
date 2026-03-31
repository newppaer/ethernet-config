package com.ethconfig.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.util.Log
import com.ethconfig.app.shell.ShizukuShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * Manages network configuration. Supports adding secondary IPs to avoid system DHCP reset.
 */
class EthernetHelper(private val context: Context) {

    enum class InterfaceType { ETHERNET, WIFI, OTHER }

    companion object {
        private const val TAG = "EthernetHelper"
        val COMMON_GATEWAYS = listOf(
            "192.168.1.1", "192.168.0.1", "192.168.2.1",
            "10.0.0.1", "172.16.0.1", "192.168.10.1", "192.168.100.1"
        )
        val COMMON_PORTS = listOf(80, 443, 8080, 8443, 22, 5000, 8123)
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun getInterfaceType(name: String): InterfaceType {
        return when {
            name.startsWith("eth") || name.startsWith("usb") -> InterfaceType.ETHERNET
            name.startsWith("wlan") -> InterfaceType.WIFI
            else -> InterfaceType.OTHER
        }
    }

    fun getAllInterfaces(): List<InterfaceInfo> {
        return try {
            NetworkInterface.getNetworkInterfaces().toList().mapNotNull { ni ->
                val type = getInterfaceType(ni.name)
                if (type == InterfaceType.OTHER) return@mapNotNull null
                val addresses = ni.inetAddresses.toList().filter { it is java.net.Inet4Address }.map { it.hostAddress }
                if (addresses.isEmpty() && !ni.isUp) return@mapNotNull null
                InterfaceInfo(ni.name, ni.displayName, ni.isUp, addresses, type)
            }.sortedBy { it.type != InterfaceType.ETHERNET }
        } catch (e: Exception) { emptyList() }
    }

    fun getNetworkStatus(): NetworkStatus {
        val network = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(network)
        val linkProps = connectivityManager.getLinkProperties(network)
        val ifaceName = linkProps?.interfaceName

        // Requirement: Connected only if ethX is active
        val isEthActive = ifaceName?.startsWith("eth") == true
        val hasIp = linkProps?.linkAddresses?.any { it.address is java.net.Inet4Address } == true
        val isConnected = isEthActive && (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true || hasIp)

        return NetworkStatus(
            connected = isConnected,
            interfaceName = ifaceName,
            ipAddress = linkProps?.linkAddresses?.firstOrNull { it.address is java.net.Inet4Address }?.address?.hostAddress,
            gateway = linkProps?.routes?.firstOrNull { it.isDefaultRoute && it.gateway is java.net.Inet4Address }?.gateway?.hostAddress,
            dns = linkProps?.dnsServers?.mapNotNull { if (it is java.net.Inet4Address) it.hostAddress else null } ?: emptyList(),
            linkProperties = linkProps,
            allInterfaces = getAllInterfaces()
        )
    }

    /**
     * Set Static IP using specified direct IP commands.
     */
    suspend fun setStaticIp(ip: String, prefixLength: Int = 24, gateway: String, dns: String = gateway, customInterface: String? = null): ConfigResult {
        val iface = customInterface ?: getNetworkStatus().interfaceName ?: return ConfigResult(false, "", "No interface")
        if (getInterfaceType(iface) == InterfaceType.WIFI) return ConfigResult(false, "WIFI_REDIRECT", "Please use System Settings for Wi-Fi")

        val commands = buildString {
            // 1. 先尝试删除这个 IP (防止重复设置报错)
            appendLine("ip addr del $ip/$prefixLength dev $iface 2>/dev/null")

            // 2. 将静态 IP 附加到接口上
            appendLine("ip addr add $ip/$prefixLength dev $iface")

            // 3. 静态路由设置 (Gateway/Prefix)
            // 先确保网关在链路层可达，再添加默认网关
            appendLine("ip route add $gateway dev $iface scope link 2>/dev/null")
            appendLine("ip route add default via $gateway dev $iface 2>/dev/null")

            // 4. 刷新路由缓存
            appendLine("ip route flush cache")
            
            // 5. 设置 DNS 属性 (辅助)
            appendLine("setprop net.dns1 $dns")
            appendLine("ndc resolver setnetdns $iface \"\" $dns 2>/dev/null")
        }

        val result = ShizukuShell.exec(commands)
        return if (result.success) ConfigResult(true, "", "Static IP Applied / 静态IP设置成功: $ip")
        else ConfigResult(false, "", "Failed / 设置失败: ${result.stderr}")
    }

    suspend fun resetIp(customInterface: String? = null): ConfigResult {
        val iface = customInterface ?: getNetworkStatus().interfaceName ?: return ConfigResult(false, "", "No interface")
        val commands = buildString {
            // 清除所有手动设置的 IP 和路由
            appendLine("ip addr flush dev $iface")
            appendLine("ip route flush dev $iface")
            // 触发系统 DHCP
            appendLine("ndc interface clearaddrs $iface 2>/dev/null")
            appendLine("ip link set $iface down")
            appendLine("sleep 1")
            appendLine("ip link set $iface up")
        }
        val result = ShizukuShell.exec(commands)
        return ConfigResult(result.success, "", "DHCP Restored / 已恢复自动获取")
    }

    suspend fun ping(host: String, timeoutMs: Int = 2000): PingResult = withContext(Dispatchers.IO) {
        try {
            val addr = InetAddress.getAllByName(host).firstOrNull { it is java.net.Inet4Address }
                ?: return@withContext PingResult(false, 0, "No IPv4")
            val startTime = System.currentTimeMillis()
            val reachable = addr.isReachable(timeoutMs)
            val elapsed = System.currentTimeMillis() - startTime
            PingResult(reachable, elapsed, if (!reachable) "Timeout" else null)
        } catch (e: Exception) { PingResult(false, 0, e.message) }
    }

    suspend fun scanPorts(host: String, ports: List<Int> = COMMON_PORTS): List<Int> = withContext(Dispatchers.IO) {
        ports.filter { port ->
            try { Socket().use { it.connect(InetSocketAddress(host, port), 500) }; true }
            catch (e: Exception) { false }
        }
    }

    data class NetworkStatus(val connected: Boolean, val interfaceName: String?, val ipAddress: String?, val gateway: String?, val dns: List<String>, val linkProperties: LinkProperties?, val allInterfaces: List<InterfaceInfo> = emptyList())
    data class InterfaceInfo(val name: String, val displayName: String, val isUp: Boolean, val addresses: List<String>, val type: InterfaceType)
    data class ConfigResult(val success: Boolean, val code: String = "", val message: String = "")
    data class PingResult(val reachable: Boolean, val timeMs: Long, val message: String? = null)

    data class ServiceInfo(val port: Int, val type: String)
    data class HostInfo(val ip: String, val rttMs: Long, val hostname: String? = null, val services: List<ServiceInfo> = emptyList())

    suspend fun scanSubnet(
        subnet: String,
        onProgress: (Float) -> Unit,
        scanPorts: List<Int> = COMMON_PORTS
    ): List<HostInfo> = withContext(Dispatchers.IO) {
        // Parse subnet like "192.168.1.0/24"
        val base = subnet.replace("/24", "").replace("/16", "").trim()
        val parts = base.split(".")
        if (parts.size != 4) return@withContext emptyList()
        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"

        val results = java.util.concurrent.ConcurrentLinkedQueue<HostInfo>()
        val counter = java.util.concurrent.atomic.AtomicInteger(0)

        // Concurrent ping all 254 IPs
        (1..254).chunked(32).forEach { chunk ->
            val jobs = chunk.map { i ->
                kotlinx.coroutines.async {
                    val ip = "$prefix.$i"
                    try {
                        val addr = InetAddress.getByName(ip)
                        val start = System.currentTimeMillis()
                        val reachable = addr.isReachable(800)
                        val rtt = System.currentTimeMillis() - start
                        if (reachable) {
                            // Quick port scan for open services
                            val services = scanPorts.take(8).mapNotNull { port ->
                                try {
                                    Socket().use { s ->
                                        s.connect(InetSocketAddress(ip, port), 300)
                                        val type = detectService(s, port)
                                        ServiceInfo(port, type)
                                    }
                                } catch (e: Exception) { null }
                            }
                            val hostname = try { addr.canonicalHostName.takeIf { it != ip } } catch (e: Exception) { null }
                            results.add(HostInfo(ip, rtt, hostname, services))
                        }
                    } catch (e: Exception) { /* skip */ }
                    val done = counter.incrementAndGet()
                    onProgress(done / 254f)
                }
            }
            jobs.awaitAll()
        }

        results.sortedBy { it.ip.split(".").last().toIntOrNull() ?: 0 }
    }

    private fun detectService(socket: java.net.Socket, port: Int): String {
        return try {
            socket.soTimeout = 500
            val input = socket.getInputStream()
            // Passive: SSH sends banner immediately
            val banner = ByteArray(128)
            val n = try { input.read(banner) } catch (e: Exception) { -1 }
            if (n > 0) {
                val text = String(banner, 0, n)
                if (text.startsWith("SSH-")) return "SSH"
                if (text.startsWith("HTTP/")) return if (port == 443) "HTTPS" else "HTTP"
            }
            // Active: send HTTP probe
            try {
                socket.outputStream.write("HEAD / HTTP/1.0\r\nHost: $port\r\n\r\n".toByteArray())
                socket.outputStream.flush()
                val resp = ByteArray(128)
                val m = input.read(resp)
                if (m > 0) {
                    val text = String(resp, 0, m)
                    if (text.startsWith("HTTP/")) return if (port == 443) "HTTPS" else "HTTP"
                }
            } catch (e: Exception) { }
            "OPEN"
        } catch (e: Exception) { "OPEN" }
    }
    data class IpPreset(val ip: String, val prefixLength: Int, val gateway: String, val dns: String)
}
