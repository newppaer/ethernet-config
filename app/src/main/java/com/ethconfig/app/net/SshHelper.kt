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
 * SSH 客户端工具类，基于 JSch (mwiede fork 2.27.x)
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
    @Volatile private var reading = false

    val isConnected: Boolean
        get() = session?.isConnected == true && channel?.isConnected == true

    /**
     * 连接到 SSH 服务器（不阻塞读取输出）
     * 返回 true 表示连接成功，false 表示连接失败
     */
    suspend fun connect(
        host: String,
        port: Int = DEFAULT_PORT,
        username: String,
        password: String
    ): Result = withContext(Dispatchers.IO) {
        try {
            disconnect()

            val jsch = JSch()
            session = jsch.getSession(username, host, port).apply {
                setPassword(password)
                val props = Properties()
                props["StrictHostKeyChecking"] = "no"
                props["kex"] = "curve25519-sha256,curve25519-sha256@libssh.org,ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521,diffie-hellman-group-exchange-sha256,diffie-hellman-group16-sha512,diffie-hellman-group18-sha512,diffie-hellman-group14-sha256"
                props["server_host_key"] = "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256,ssh-rsa"
                props["cipher.s2c"] = "aes256-gcm@openssh.com,aes128-gcm@openssh.com,aes256-ctr,aes192-ctr,aes128-ctr"
                props["cipher.c2s"] = "aes256-gcm@openssh.com,aes128-gcm@openssh.com,aes256-ctr,aes192-ctr,aes128-ctr"
                props["mac.s2c"] = "hmac-sha2-256-etm@openssh.com,hmac-sha2-512-etm@openssh.com,hmac-sha2-256,hmac-sha2-512"
                props["mac.c2s"] = "hmac-sha2-256-etm@openssh.com,hmac-sha2-512-etm@openssh.com,hmac-sha2-256,hmac-sha2-512"
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
            Result(true)
        } catch (e: Exception) {
            Log.e(TAG, "SSH connect failed", e)
            disconnect()
            Result(false, e.message ?: "连接失败")
        }
    }

    /**
     * 持续读取输出（必须在 connect 成功后调用，阻塞直到断开）
     */
    suspend fun readOutput(onOutput: (String) -> Unit, onError: (String) -> Unit) = withContext(Dispatchers.IO) {
        reading = true
        try {
            val buf = CharArray(4096)
            while (reading && isConnected) {
                val n = reader?.read(buf) ?: -1
                if (n > 0) {
                    onOutput(String(buf, 0, n))
                } else if (n == -1) {
                    break
                }
            }
        } catch (e: Exception) {
            if (reading && isConnected) {
                onError("读取异常: ${e.message}")
            }
        } finally {
            reading = false
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
        reading = false
        try {
            writer?.close()
            reader?.close()
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

    data class Result(val success: Boolean, val error: String = "")

    data class SshShortcut(
        val label: String,
        val icon: String,
        val command: String
    )

    object Shortcuts {
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
