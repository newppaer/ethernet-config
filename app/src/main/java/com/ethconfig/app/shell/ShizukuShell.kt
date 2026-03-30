package com.ethconfig.app.shell

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

/**
 * Execute shell commands via Shizuku
 */
object ShizukuShell {

    private const val TAG = "ShizukuShell"

    fun isAvailable(): Boolean = try { Shizuku.pingBinder() } catch (e: Exception) { false }

    fun hasPermission(): Boolean = try { Shizuku.checkSelfPermission() == 0 } catch (e: Exception) { false }

    fun requestPermission(requestCode: Int) {
        try {
            Shizuku.requestPermission(requestCode)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request permission", e)
        }
    }

    /**
     * 执行 Shell 命令
     * 由于 Shizuku 13.x 中 newProcess 是私有的，我们通过反射强制调用
     */
    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext ShellResult(false, "", "Shizuku not running")

            // 使用反射调用私有的 newProcess 方法
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess", 
                Array<String>::class.java, 
                Array<String>::class.java, 
                String::class.java
            )
            newProcessMethod.isAccessible = true
            
            val process = newProcessMethod.invoke(
                null, 
                arrayOf("sh", "-c", command), 
                null, 
                null
            ) as ShizukuRemoteProcess

            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            Log.d(TAG, "exec: $command -> $exitCode")

            if (exitCode == 0) {
                ShellResult(true, stdout.trim(), "")
            } else {
                ShellResult(false, stdout.trim(), stderr.trim().ifEmpty { "Exit code: $exitCode" })
            }
        } catch (e: Exception) {
            Log.e(TAG, "exec failed", e)
            ShellResult(false, "", e.message ?: "Unknown error")
        }
    }

    data class ShellResult(
        val success: Boolean,
        val stdout: String,
        val stderr: String
    )
}
