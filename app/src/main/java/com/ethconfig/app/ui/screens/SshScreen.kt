package com.ethconfig.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethconfig.app.net.SshHelper
import com.ethconfig.app.ui.MainViewModel

@Composable
fun SshScreen(viewModel: MainViewModel, host: String) {
    val state by viewModel.uiState.collectAsState()

    if (state.sshConnected) {
        SshTerminal(state, viewModel)
    } else {
        SshLoginForm(viewModel, host, state)
    }
}

@Composable
private fun SshLoginForm(viewModel: MainViewModel, host: String, state: MainViewModel.UiState) {
    var username by remember { mutableStateOf(state.sshUsername.ifEmpty { "root" }) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var port by remember { mutableStateOf("22") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("🔐", fontSize = 40.sp)
            }
        }

        Spacer(Modifier.height(24.dp))

        Text("SSH 连接", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        Text(
            "连接到 $host",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = host,
            onValueChange = {},
            label = { Text("主机地址") },
            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            enabled = false
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("端口") },
            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("用户名") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.Lock else Icons.Default.Lock,
                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        state.sshError?.let { error ->
            Spacer(Modifier.height(12.dp))
            Surface(
                color = Color(0xFFFFEBEE),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(error, color = Color(0xFFC62828), fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { viewModel.connectSsh(host, port.toIntOrNull() ?: 22, username, password) },
            enabled = username.isNotBlank() && password.isNotBlank() && !state.sshConnecting,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (state.sshConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("连接中...", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("连接", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("常用登录", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("root", "admin", "pi", "ubuntu").forEach { user ->
                AssistChip(
                    onClick = { username = user },
                    label = { Text(user, fontSize = 12.sp) }
                )
            }
        }
    }
}

@Composable
private fun SshTerminal(state: MainViewModel.UiState, viewModel: MainViewModel) {
    var command by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.sshOutput.size) {
        if (state.sshOutput.isNotEmpty()) {
            listState.animateScrollToItem(state.sshOutput.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    "⚡ 快捷命令",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(SshHelper.Shortcuts.DEFAULT_SHORTCUTS) { shortcut ->
                        SuggestionChip(
                            onClick = { viewModel.sendSshCommand(shortcut.command) },
                            label = {
                                Text(
                                    "${shortcut.icon} ${shortcut.label}",
                                    fontSize = 11.sp
                                )
                            }
                        )
                    }
                }
            }
        }

        Surface(
            color = Color(0xFF1E1E1E),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (state.sshOutput.isEmpty()) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("终端就绪", color = Color(0xFF888888), fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                        Text("输入命令或使用快捷按钮", color = Color(0xFF555555), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(8.dp)
                ) {
                    items(state.sshOutput) { line ->
                        val color = when {
                            line.contains("error", ignoreCase = true) ||
                            line.contains("fail", ignoreCase = true) ||
                            line.contains("denied", ignoreCase = true) -> Color(0xFFFF6B6B)
                            line.contains("success", ignoreCase = true) ||
                            line.contains("ok", ignoreCase = true) -> Color(0xFF69DB7C)
                            line.startsWith("$") || line.startsWith("#") || line.startsWith("root@") -> Color(0xFF74C0FC)
                            else -> Color(0xFFD4D4D4)
                        }
                        Text(
                            text = line,
                            color = color,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 1.dp),
                            softWrap = true
                        )
                    }
                }
            }
        }

        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = { viewModel.sendSshKey(3.toByte()) },
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("⌃C", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Spacer(Modifier.width(6.dp))

                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    placeholder = { Text("输入命令...", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (command.isNotBlank()) {
                                    viewModel.sendSshCommand(command)
                                    command = ""
                                }
                            },
                            enabled = command.isNotBlank()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, "发送")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii
                    )
                )

                Spacer(Modifier.width(6.dp))

                FilledTonalButton(
                    onClick = {
                        if (command.isNotBlank()) {
                            viewModel.sendSshCommand(command)
                            command = ""
                        }
                    },
                    enabled = command.isNotBlank(),
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("↵", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}
