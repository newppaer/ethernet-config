package com.ethconfig.app.net

import android.util.Log
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.Properties

/**
 * SSH 客户端工具类，基于 JSch 实现
 */
class SshHelper {

    companion object {
        private const val TAG = "SshHelper"
        private const val DEFAULT_PORT = 22
        private const val CONNECT_TIMEOUT = 10000
    }

    private var session: Session? = null
    private var channel: ChannelShell? = null
    private var reader: BufferedReader? = null
    private var writer: OutputStream? = null

    val isConnected: Boolean
        get() = session?.isConnected == true

    /**
     * 连接到 SSH 服务器
     */
    suspend fun connect(
        host: String,
        port: Int = DEFAULT_PORT,
        username: String,
        password: String,
        onOutput: (String) -> Unit,
        onError: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            disconnect()

            val jsch = JSch()
            session = jsch.getSession(username, host, port).apply {
                setPassword(password)
                val props = Properties()
                props["StrictHostKeyChecking"] = "no"
                setConfig(props)
                setTimeout(CONNECT_TIMEOUT)
            }

            session?.connect(CONNECT_TIMEOUT)
            Log.d(TAG, "SSH session connected to $host:$port")

            channel = session?.openChannel("shell") as? ChannelShell
            channel?.let { ch ->
                reader = BufferedReader(InputStreamReader(ch.inputStream))
                writer = ch.outputStream
                ch.setPtyType("vt102", 80, 24, 640, 480)
                ch.connect(CONNECT_TIMEOUT)
            }

            Log.d(TAG, "SSH shell channel connected")

            // 读取输出
            try {
                val buf = CharArray(4096)
                while (isConnected) {
                    val n = reader?.read(buf) ?: -1
                    if (n > 0) {
                        onOutput(String(buf, 0, n))
                    } else if (n == -1) {
                        break
                    }
                }
            } catch (e: Exception) {
                if (isConnected) {
                    onError("读取输出异常: ${e.message}")
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "SSH connect failed", e)
            onError("连接失败: ${e.message}")
            disconnect()
            false
        }
    }

    /**
     * 发送命令
     */
    fun sendCommand(command: String): Boolean {
        return try {
            writer?.let {
                it.write("$command\n".toByteArray())
                it.flush()
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Send command failed", e)
            false
        }
    }

    /**
     * 发送特殊按键
     */
    fun sendKey(code: Byte): Boolean {
        return try {
            writer?.let {
                it.write(byteArrayOf(code))
                it.flush()
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        try {
            reader?.close()
            writer?.close()
            channel?.disconnect()
            session?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Disconnect error", e)
        }
        reader = null
        writer = null
        channel = null
        session = null
    }

    /**
     * SSH 快捷命令预设
     */
    data class SshShortcut(
        val label: String,
        val icon: String,
        val command: String
    )

    companion object Shortcuts {
        val DEFAULT_SHORTCUTS = listOf(
            SshShortcut("系统信息", "📋", "uname -a && cat /etc/os-release 2>/dev/null | head -5"),
            SshShortcut("磁盘空间", "💾", "df -h"),
            SshShortcut("内存使用", "🧠", "free -h"),
            SshShortcut("CPU 信息", "⚡", "top -bn1 | head -20"),
            SshShortcut("网络接口", "🌐", "ip addr show"),
            SshShortcut("路由表", "🔀", "ip route show"),
            SshShortcut("进程列表", "📊", "ps aux --sort=-%mem | head -15"),
            SshShortcut("系统负载", "📈", "uptime && cat /proc/loadavg"),
            SshShortcut("监听端口", "🔌", "ss -tlnp"),
            SshShortcut("最近日志", "📜", "journalctl -n 20 --no-pager 2>/dev/null || tail -20 /var/log/syslog 2>/dev/null"),
            SshShortcut("重启网络", "🔄", "systemctl restart networking 2>/dev/null || systemctl restart NetworkManager 2>/dev/null"),
            SshShortcut("清屏", "🧹", "clear"),
        )
    }
}
