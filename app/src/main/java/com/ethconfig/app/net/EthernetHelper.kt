package com.ethconfig.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.util.Log
import com.ethconfig.app.shell.ShizukuShell
import kotlinx.coroutines.Dispatchers
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
        // 获取系统默认的活跃网络属性
        val activeNetwork = connectivityManager.activeNetwork
        val activeLinkProps = connectivityManager.getLinkProperties(activeNetwork)

        // 尝试寻找以太网接口（即便它不是系统当前的默认网络）
        val allInterfaces = getAllInterfaces()
        val ethIface = allInterfaces.find { it.type == InterfaceType.ETHERNET }

        // 重新定义 isConnected:
        // 只要以太网接口处于 UP 状态且有 IP 地址，我们就认为“以太网已连接”
        val isConnected = ethIface?.isUp == true

        return NetworkStatus(
            connected = isConnected,
            interfaceName = ethIface?.name ?: activeLinkProps?.interfaceName,
            ipAddress = ethIface?.addresses?.firstOrNull() ?: activeLinkProps?.linkAddresses?.firstOrNull { it.address is java.net.Inet4Address }?.address?.hostAddress,
            gateway = activeLinkProps?.routes?.firstOrNull { it.isDefaultRoute && it.gateway is java.net.Inet4Address }?.gateway?.hostAddress,
            dns = activeLinkProps?.dnsServers?.mapNotNull { if (it is java.net.Inet4Address) it.hostAddress else null } ?: emptyList(),
            linkProperties = activeLinkProps,
            allInterfaces = allInterfaces
        )
    }

    /**
     * Set Static IP using specified direct IP commands.
     */
    suspend fun setStaticIp(ip: String, prefixLength: Int = 24, gateway: String, dns: String = gateway, customInterface: String? = null): ConfigResult {
        val iface = customInterface ?: getNetworkStatus().interfaceName ?: return ConfigResult(false, "", "No interface")
        if (getInterfaceType(iface) == InterfaceType.WIFI) return ConfigResult(false, "WIFI_REDIRECT", "Please use System Settings for Wi-Fi")

        val commands = buildString {
            val subnet = ip.substringBeforeLast(".") + ".0"
            // 1. 先尝试删除这个 IP (防止重复设置报错)
            appendLine("ip rule del priority 1000 2>/dev/null")
            appendLine("ip rule del priority 1001 2>/dev/null")
            appendLine("ip addr del $ip/$prefixLength dev $iface 2>/dev/null")
            appendLine("ip route flush table 100 2>/dev/null")

            // 2. 将静态 IP 附加到接口上
            appendLine("ip addr add $ip/$prefixLength dev $iface")
            appendLine("ip rule add to all lookup 100 priority 1000")
            appendLine("ip rule add from $ip lookup 100 priority 1001")

            appendLine("ip route add $subnet/$prefixLength dev $iface table 100")
            appendLine("ip route add default via $gateway dev $iface table 100")

            appendLine("ip route flush cache")
        }

        val result = ShizukuShell.exec(commands)
        return if (result.success) ConfigResult(true, "", "Static IP Applied / 静态IP设置成功: $ip")
        else ConfigResult(false, "", "Failed / 设置失败: ${result.stderr}")
    }

    suspend fun resetIp(customInterface: String? = null): ConfigResult {
        val iface = customInterface ?: getNetworkStatus().interfaceName ?: return ConfigResult(false, "", "No interface")
        val commands = buildString {
            // 清除所有手动设置的 IP 和路由
            appendLine("ip rule del priority 1000 2>/dev/null")
            appendLine("ip rule del priority 1001 2>/dev/null")
            appendLine("ip addr flush dev $iface")
            appendLine("ip route flush dev $iface")

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

    suspend fun scanPorts(host: String, ports: List<Int> = COMMON_PORTS): List<ServiceInfo> = withContext(Dispatchers.IO) {
        ports.mapNotNull { port ->
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, port), 500)
                    val type = detectService(s, port)
                    ServiceInfo(port, type)
                }
            } catch (e: Exception) { null }
        }
    }

    data class NetworkStatus(val connected: Boolean, val interfaceName: String?, val ipAddress: String?, val gateway: String?, val dns: List<String>, val linkProperties: LinkProperties?, val allInterfaces: List<InterfaceInfo> = emptyList())
    data class InterfaceInfo(val name: String, val displayName: String, val isUp: Boolean, val addresses: List<String>, val type: InterfaceType)
    data class ConfigResult(val success: Boolean, val code: String = "", val message: String = "")
    data class PingResult(val reachable: Boolean, val timeMs: Long, val message: String? = null)

    data class ServiceInfo(val port: Int, val type: String)
    data class HostInfo(val ip: String, val rttMs: Long, val hostname: String? = null, val services: List<ServiceInfo> = emptyList())

    /**
     * @param deepScan false = quick scan (COMMON_PORTS), true = full 1-65535 port scan on alive hosts
     */
    suspend fun scanSubnet(
        subnet: String,
        onProgress: (Float) -> Unit,
        scanPorts: List<Int> = COMMON_PORTS,
        deepScan: Boolean = false
    ): List<HostInfo> = withContext(Dispatchers.IO) {
        val base = subnet.replace("/24", "").replace("/16", "").trim()
        val parts = base.split(".")
        if (parts.size != 4) return@withContext emptyList()
        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"

        val results = java.util.concurrent.ConcurrentLinkedQueue<HostInfo>()
        val counter = java.util.concurrent.atomic.AtomicInteger(0)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(32)

        (1..254).forEach { i ->
            pool.submit {
                val ip = "$prefix.$i"
                try {
                    val addr = InetAddress.getByName(ip)
                    val start = System.currentTimeMillis()
                    val reachable = addr.isReachable(800)
                    val rtt = System.currentTimeMillis() - start
                    if (reachable) {
                        val services = if (deepScan) {
                            // Full port scan: 1-65535 with 64 concurrent threads
                            deepPortScan(ip)
                        } else {
                            scanPorts.take(8).mapNotNull { port ->
                                try {
                                    Socket().use { s ->
                                        s.connect(InetSocketAddress(ip, port), 300)
                                        val type = detectService(s, port)
                                        ServiceInfo(port, type)
                                    }
                                } catch (e: Exception) { null }
                            }
                        }
                        val hostname = try { addr.canonicalHostName.takeIf { it != ip } } catch (e: Exception) { null }
                        results.add(HostInfo(ip, rtt, hostname, services))
                    }
                } catch (e: Exception) { /* skip */ }
                val done = counter.incrementAndGet()
                onProgress(done / 254f)
            }
        }

        pool.shutdown()
        pool.awaitTermination(if (deepScan) 300 else 60, java.util.concurrent.TimeUnit.SECONDS)

        results.sortedBy { it.ip.split(".").last().toIntOrNull() ?: 0 }
    }

    /**
     * Deep scan all 65535 ports on a single host using 64 concurrent threads.
     * Returns only open ports with detected service type.
     */
    private fun deepPortScan(ip: String): List<ServiceInfo> {
        val results = java.util.concurrent.ConcurrentLinkedQueue<ServiceInfo>()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(64)
        val counter = java.util.concurrent.atomic.AtomicInteger(0)

        (1..65535).forEach { port ->
            pool.submit {
                try {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(ip, port), 200)
                        val type = detectService(s, port)
                        results.add(ServiceInfo(port, type))
                    }
                } catch (e: Exception) { /* closed */ }
            }
        }

        pool.shutdown()
        pool.awaitTermination(120, java.util.concurrent.TimeUnit.SECONDS)

        return results.sortedBy { it.port }
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
