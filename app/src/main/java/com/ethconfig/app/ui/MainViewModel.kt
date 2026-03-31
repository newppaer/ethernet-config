package com.ethconfig.app.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethconfig.app.data.ProfileStorage
import com.ethconfig.app.net.EthernetHelper
import com.ethconfig.app.net.SshHelper
import com.ethconfig.app.shell.ShizukuShell
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(
    private val context: Context,
    private val ethernetHelper: EthernetHelper, 
    private val profileStorage: ProfileStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var pingJob: Job? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val sshHelper = SshHelper()

    init {
        refreshStatus()
        _uiState.update { it.copy(
            profiles = profileStorage.loadProfiles(),
            quickCommands = profileStorage.loadQuickCommands()
        ) }
    }

    fun refreshStatus() {
        val status = ethernetHelper.getNetworkStatus()
        val shizukuOk = ShizukuShell.isAvailable()
        val shizukuPerm = ShizukuShell.hasPermission()
        _uiState.update {
            it.copy(
                networkStatus = status,
                shizukuAvailable = shizukuOk,
                shizukuHasPermission = shizukuPerm,
                selectedInterface = it.selectedInterface ?: status.interfaceName
            )
        }
    }

    fun requestShizukuPermission() {
        ShizukuShell.requestPermission(1001)
    }

    fun setSelectedInterface(iface: String) {
        _uiState.update { it.copy(selectedInterface = iface) }
    }

    fun setStaticIp(ip: String, prefixLength: Int, gateway: String, dns: String) {
        viewModelScope.launch {
            val iface = _uiState.value.selectedInterface
            _uiState.update { it.copy(configuring = true, configResult = null) }
            
            val result = ethernetHelper.setStaticIp(ip, prefixLength, gateway, dns, iface)
            
            if (result.code == "WIFI_REDIRECT") {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                _uiState.update { it.copy(configuring = false, configResult = result) }
            } else {
                _uiState.update {
                    it.copy(
                        configuring = false,
                        configResult = result,
                        lastAction = if (result.success) "IP已配置: $ip" else "配置失败"
                    )
                }
                if (result.success) {
                    profileStorage.saveLastConfig(ProfileStorage.IpProfile("last", ip, prefixLength, gateway, dns))
                    delay(1500)
                    refreshStatus()
                }
            }
        }
    }

    fun resetIp() {
        viewModelScope.launch {
            val iface = _uiState.value.selectedInterface
            _uiState.update { it.copy(configuring = true) }
            val result = ethernetHelper.resetIp(iface)
            _uiState.update { it.copy(configuring = false, configResult = result) }
            delay(1500)
            refreshStatus()
        }
    }

    // --- Toolbox Logic ---

    fun startContinuousPing(host: String) {
        stopContinuousPing()
        pingJob = viewModelScope.launch {
            _uiState.update { it.copy(isContinuousPinging = true, pingLogs = emptyList()) }
            while (true) {
                val result = ethernetHelper.ping(host)
                val time = timeFormat.format(Date())
                val log = if (result.reachable) {
                    "[$time] Reply from $host: time=${result.timeMs}ms"
                } else {
                    "[$time] Request timed out / 请求超时"
                }
                _uiState.update { 
                    val newLogs = (listOf(log) + it.pingLogs).take(100)
                    it.copy(pingLogs = newLogs) 
                }
                delay(1000)
            }
        }
    }

    fun stopContinuousPing() {
        pingJob?.cancel()
        pingJob = null
        _uiState.update { it.copy(isContinuousPinging = false) }
    }

    fun scanPorts(host: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(scanning = true, openPorts = emptyList()) }
            val found = ethernetHelper.scanPorts(host)
            _uiState.update { it.copy(scanning = false, openPorts = found) }
        }
    }

    fun setManagementIp(ip: String) {
        _uiState.update { it.copy(managementIp = ip) }
        profileStorage.saveLastManagementIp(ip)
    }

    fun setWebViewVisible(visible: Boolean) {
        _uiState.update { it.copy(showWebView = visible) }
    }

    fun setToolsVisible(visible: Boolean) {
        // If entering tools, also stop ping just in case
        if (!visible) stopContinuousPing()
        _uiState.update { it.copy(showTools = visible) }
    }

    fun selectProfile(profile: ProfileStorage.IpProfile) {
        _uiState.update { it.copy(selectedProfile = profile) }
    }

    // --- SSH Terminal ---

    fun connectSsh(host: String, port: Int, username: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(sshConnecting = true, sshError = null, sshOutput = emptyList()) }
            
            val time = timeFormat.format(Date())
            _uiState.update { 
                it.copy(sshOutput = listOf("[$time] 正在连接 $host:$port ...")) 
            }

            // 第一步：建立连接
            val result = sshHelper.connect(host, port, username, password)
            
            if (result.success) {
                // 连接成功，更新状态
                _uiState.update {
                    it.copy(
                        sshConnected = true,
                        sshConnecting = false,
                        sshHost = host,
                        sshUsername = username,
                        sshOutput = it.sshOutput + listOf("[$time] ✓ 连接成功")
                    )
                }
                
                // 第二步：在新协程中读取输出（阻塞直到断开）
                launch {
                    sshHelper.readOutput(
                        onOutput = { output ->
                            val lines = output.split("\n")
                            _uiState.update { state ->
                                val newLogs = state.sshOutput + lines.filter { it.isNotBlank() }
                                state.copy(sshOutput = newLogs.takeLast(500))
                            }
                        },
                        onError = { error ->
                            _uiState.update { it.copy(sshError = error) }
                        }
                    )
                    // 读取结束 = 连接断开
                    _uiState.update { it.copy(sshConnected = false) }
                }
            } else {
                // 连接失败
                _uiState.update {
                    it.copy(
                        sshConnecting = false,
                        sshConnected = false,
                        sshError = result.error
                    )
                }
            }
        }
    }

    fun sendSshCommand(command: String) {
        val time = timeFormat.format(Date())
        _uiState.update { state ->
            val newLogs = state.sshOutput + listOf("$ $command")
            state.copy(sshOutput = newLogs.takeLast(500))
        }
        viewModelScope.launch { sshHelper.sendCommand(command) }
    }

    fun sendSshKey(code: Byte) {
        viewModelScope.launch { sshHelper.sendKey(code) }
    }

    fun disconnectSsh() {
        sshHelper.disconnect()
        _uiState.update {
            it.copy(
                sshConnected = false,
                sshConnecting = false,
                sshHost = "",
                sshUsername = "",
                sshOutput = emptyList(),
                sshError = null,
                showSsh = false,
                sshTargetHost = ""
            )
        }
    }

    fun setSshVisible(visible: Boolean, host: String = "") {
        // Don't auto-disconnect - keep connection alive in background
        _uiState.update { it.copy(showSsh = visible, sshTargetHost = host) }
    }

    fun saveQuickCommands(commands: List<String>) {
        _uiState.update { it.copy(quickCommands = commands) }
        profileStorage.saveQuickCommands(commands)
    }

    data class UiState(
        val networkStatus: EthernetHelper.NetworkStatus? = null,
        val shizukuAvailable: Boolean = false,
        val shizukuHasPermission: Boolean = false,
        val configuring: Boolean = false,
        val configResult: EthernetHelper.ConfigResult? = null,
        val isContinuousPinging: Boolean = false,
        val pingLogs: List<String> = emptyList(),
        val pingResult: EthernetHelper.PingResult? = null,
        val scanning: Boolean = false,
        val openPorts: List<Int> = emptyList(),
        val profiles: List<ProfileStorage.IpProfile> = emptyList(),
        val selectedProfile: ProfileStorage.IpProfile? = null,
        val managementIp: String = "",
        val showWebView: Boolean = false,
        val showTools: Boolean = false,
        val lastAction: String = "",
        val selectedInterface: String? = null,
        // SSH state
        val showSsh: Boolean = false,
        val sshTargetHost: String = "",
        val sshConnected: Boolean = false,
        val sshConnecting: Boolean = false,
        val sshHost: String = "",
        val sshUsername: String = "",
        val sshOutput: List<String> = emptyList(),
        val sshError: String? = null,
        val quickCommands: List<String> = emptyList()
    )
}
