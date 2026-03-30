package com.ethconfig.app.ui.screens

import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ethconfig.app.net.EthernetHelper
import com.ethconfig.app.net.SshHelper
import com.ethconfig.app.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()

    BackHandler(enabled = state.showWebView || state.showTools || state.showSsh) {
        if (state.showWebView) viewModel.setWebViewVisible(false)
        else if (state.showSsh) viewModel.setSshVisible(false)
        else if (state.showTools) viewModel.setToolsVisible(false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        when {
                            state.showWebView -> "🌍 Web Management"
                            state.showSsh -> "🔐 SSH Terminal"
                            state.showTools -> "🛠 Toolbox"
                            else -> "🌐 Ethernet Config"
                        },
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    if (state.showWebView || state.showTools || state.showSsh) {
                        IconButton(onClick = { 
                            if (state.showWebView) viewModel.setWebViewVisible(false)
                            else if (state.showSsh) viewModel.setSshVisible(false)
                            else viewModel.setToolsVisible(false)
                        }) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    }
                },
                actions = {
                    if (!state.showWebView && !state.showTools && !state.showSsh) {
                        IconButton(onClick = { viewModel.refreshStatus() }) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                        IconButton(onClick = { viewModel.setToolsVisible(true) }) {
                            Icon(Icons.Default.Build, "Tools")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                state.showWebView -> {
                    WebViewScreen(
                        url = if (state.managementIp.startsWith("http")) state.managementIp else "http://${state.managementIp}"
                    )
                }
                state.showSsh -> {
                    SshScreen(viewModel, state.sshTargetHost)
                }
                state.showTools -> {
                    ToolsScreen(state, viewModel)
                }
                else -> {
                    MainContent(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun MainContent(state: MainViewModel.UiState, viewModel: MainViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { StatusCard(state, viewModel) }
        item { InterfaceSelectionCard(state, viewModel) }
        item { ShizukuStatusCard(state, viewModel) }
        item { IpConfigCard(state, viewModel) }
        item { PresetsSection(state, viewModel) }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun ToolsScreen(state: MainViewModel.UiState, viewModel: MainViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ContinuousPingCard(state, viewModel) }
        item { PortScanCard(state, viewModel) }
        item { ManagementCard(state, viewModel) }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun StatusCard(state: MainViewModel.UiState, viewModel: MainViewModel) {
    val status = state.networkStatus
    val isConnected = status?.connected == true
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) Color(0xFF2E7D32) else Color(0xFFC62828)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = if (isConnected) "🟢" else "🔴", fontSize = 24.sp)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = if (isConnected) "Ethernet Active" else "No Ethernet Connection",
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = Color.White
                    )
                    Text(
                        text = if (isConnected) "Link Established" else "Check cable or interface",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
            if (isConnected && status != null) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))
                StatusRow("Interface", status.interfaceName ?: "—")
                StatusRow("Local IP", status.ipAddress ?: "—")
                StatusRow("Gateway", status.gateway ?: "—")
                StatusRow("DNS", status.dns.firstOrNull() ?: "—")
            }
        }
    }
}

@Composable
private fun InterfaceSelectionCard(state: MainViewModel.UiState, viewModel: MainViewModel) {
    val interfaces = state.networkStatus?.allInterfaces ?: emptyList()
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Select Interface", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(12.dp))
            interfaces.forEach { iface ->
                val isSelected = state.selectedInterface == iface.name
                val icon = if (iface.type == EthernetHelper.InterfaceType.WIFI) "📶" else "🖥"
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { viewModel.setSelectedInterface(iface.name) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = { viewModel.setSelectedInterface(iface.name) })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "$icon ${iface.name}", 
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            if (iface.addresses.isNotEmpty()) {
                                Text(
                                    text = iface.addresses.joinToString(), 
                                    fontSize = 12.sp, 
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else Color.Gray,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShizukuStatusCard(state: MainViewModel.UiState, viewModel: MainViewModel) {
    val isReady = state.shizukuAvailable && state.shizukuHasPermission
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isReady) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isReady) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isReady) Color(0xFF2E7D32) else Color(0xFFE65100),
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Shizuku Engine", fontWeight = FontWeight.Bold, color = Color.DarkGray)
                Text(
                    text = if (!state.shizukuAvailable) "Not running" else if (!state.shizukuHasPermission) "Waiting for authorization" else "Authorized & Running",
                    fontSize = 13.sp,
                    color = if (isReady) Color(0xFF2E7D32) else Color(0xFFE65100)
                )
            }
            if (state.shizukuAvailable && !state.shizukuHasPermission) {
                Button(
                    onClick = { viewModel.requestShizukuPermission() },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Grant")
                }
            }
        }
    }
}

@Composable
private fun IpConfigCard(state: MainViewModel.UiState, viewModel: MainViewModel) {
    var ip by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("24") }
    var gateway by remember { mutableStateOf("") }
    var dns by remember { mutableStateOf("") }

    LaunchedEffect(state.selectedProfile) {
        state.selectedProfile?.let {
            ip = it.ip
            prefix = it.prefixLength.toString()
            gateway = it.gateway
            dns = it.dns
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("⚙️ Static IP Configuration", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Spacer(Modifier.height(16.dp))
            
            OutlinedTextField(
                value = ip, 
                onValueChange = { ip = it }, 
                label = { Text("IP Address") }, 
                placeholder = { Text("e.g. 192.168.1.100") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(12.dp))
            
            Row {
                OutlinedTextField(
                    value = prefix, 
                    onValueChange = { prefix = it }, 
                    label = { Text("Prefix") }, 
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = gateway, 
                    onValueChange = { gateway = it }, 
                    label = { Text("Gateway") }, 
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(12.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            
            OutlinedTextField(
                value = dns, 
                onValueChange = { dns = it }, 
                label = { Text("Primary DNS") }, 
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(Modifier.height(20.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { viewModel.setStaticIp(ip, prefix.toIntOrNull() ?: 24, gateway, dns.ifBlank { gateway }) },
                    enabled = ip.isNotBlank() && gateway.isNotBlank() && !state.configuring && state.selectedInterface != null,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    if (state.configuring) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 3.dp)
                    } else {
                        Text("Apply Config", fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(
                    onClick = { viewModel.resetIp() }, 
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Text("Restore DHCP", fontWeight = FontWeight.Bold)
                }
            }
            state.configResult?.let {
                Surface(
                    modifier = Modifier.padding(top = 12.dp),
                    color = if (it.success) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = it.message, 
                        color = if (it.success) Color(0xFF2E7D32) else Color(0xFFC62828), 
                        fontSize = 13.sp, 
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetsSection(state: MainViewModel.UiState, viewModel: MainViewModel) {
    Column {
        Text("📋 Quick Presets", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.profiles) { profile ->
                FilterChip(
                    selected = state.selectedProfile == profile,
                    onClick = { viewModel.selectProfile(profile) },
                    label = { Text(profile.name) },
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ContinuousPingCard(state: MainViewModel.UiState, viewModel: MainViewModel) {
    var host by remember { mutableStateOf("") }
    val currentGateway = state.networkStatus?.gateway ?: "Not available"
    val listState = rememberLazyListState()

    LaunchedEffect(state.pingLogs.size) {
        if (state.pingLogs.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📡 Continuous Diagnosis (Ping)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = host, 
                    onValueChange = { host = it }, 
                    label = { Text("Target IP / Host") }, 
                    placeholder = { Text("e.g. 8.8.8.8") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    enabled = !state.isContinuousPinging
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { 
                        if (state.isContinuousPinging) viewModel.stopContinuousPing()
                        else viewModel.startContinuousPing(host)
                    }, 
                    enabled = host.isNotBlank() || (state.isContinuousPinging),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isContinuousPinging) Color(0xFFC62828) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (state.isContinuousPinging) "Stop" else "Start")
                }
            }
            
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { host = currentGateway; viewModel.startContinuousPing(currentGateway) }, 
                    label = { Text("Ping Gateway") },
                    enabled = currentGateway != "Not available" && !state.isContinuousPinging
                )
                AssistChip(
                    onClick = { host = "8.8.8.8"; viewModel.startContinuousPing("8.8.8.8") }, 
                    label = { Text("Ping DNS (8.8.8.8)") },
                    enabled = !state.isContinuousPinging
                )
            }

            Spacer(Modifier.height(12.dp))
            Surface(
                color = Color.Black,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                if (state.pingLogs.isEmpty()) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Terminal Ready...", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        items(state.pingLogs) { log ->
                            Text(
                                text = log,
                                color = if (log.contains("out")) Color.Red else Color(0xFF00FF00),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PortScanCard(state: MainViewModel.UiState, viewModel: MainViewModel) {
    var host by remember { mutableStateOf("") }
    
    LaunchedEffect(state.networkStatus?.gateway) {
        if (host.isBlank()) host = state.networkStatus?.gateway ?: ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("🔍 Port Scanner", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            
            OutlinedTextField(
                value = host, 
                onValueChange = { host = it }, 
                label = { Text("Target IP Address") }, 
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            
            Spacer(Modifier.height(12.dp))
            
            Button(
                onClick = { viewModel.scanPorts(host) }, 
                modifier = Modifier.fillMaxWidth(), 
                enabled = !state.scanning && host.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.scanning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Scanning Common Ports...")
                } else {
                    Text("Scan Management Ports")
                }
            }
            
            if (state.openPorts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Open Ports Found:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.openPorts.forEach { port ->
                        if (port == 22) {
                            // Port 22 → SSH Terminal
                            SuggestionChip(
                                onClick = { 
                                    viewModel.setSshVisible(true, host)
                                },
                                label = { Text("PORT 22 (SSH) 🔐") },
                                icon = { Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                        } else {
                            val url = if (port == 443) "https://$host" else "http://$host:$port"
                            SuggestionChip(
                                onClick = { 
                                    viewModel.setManagementIp(url)
                                    viewModel.setWebViewVisible(true) 
                                },
                                label = { Text("PORT $port (${if (port == 443) "HTTPS" else "HTTP"})") },
                                icon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = { content() }
    )
}

@Composable
private fun ManagementCard(state: MainViewModel.UiState, viewModel: MainViewModel) {
    var url by remember { mutableStateOf(state.managementIp) }
    var useHttps by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("🌍 Web Management Console", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = url, 
                    onValueChange = { url = it }, 
                    label = { Text("IP or URL") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { 
                        val finalUrl = when {
                            url.startsWith("http") -> url
                            useHttps -> "https://$url"
                            else -> "http://$url"
                        }
                        viewModel.setManagementIp(finalUrl)
                        viewModel.setWebViewVisible(true) 
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Open")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Checkbox(checked = useHttps, onCheckedChange = { useHttps = it })
                Text("Use HTTPS", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun WebViewScreen(url: String) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = object : WebViewClient() {
                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                        // Trust self-signed certs commonly found on internal management pages
                        handler?.proceed()
                    }
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp), 
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(value, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}
