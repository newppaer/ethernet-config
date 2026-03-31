package com.ethconfig.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var port by remember { mutableStateOf("22") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("SSH Connection", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        Text(host, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = host, onValueChange = {},
            label = { Text("Host") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp), singleLine = true, enabled = false
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = port, onValueChange = { port = it },
            label = { Text("Port") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = username, onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp), singleLine = true
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp), singleLine = true
        )

        state.sshError?.let { error ->
            Spacer(Modifier.height(12.dp))
            Surface(color = Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(error, color = Color(0xFFC62828), fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { viewModel.connectSsh(host, port.toIntOrNull() ?: 22, username, password) },
            enabled = username.isNotBlank() && password.isNotBlank() && !state.sshConnecting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (state.sshConnecting) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Connecting...", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            } else {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Connect", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("root", "admin", "pi", "ubuntu").forEach { user ->
                AssistChip(onClick = { username = user }, label = { Text(user, fontSize = 12.sp) })
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
        // Terminal output
        Surface(
            color = Color(0xFF1E1E1E),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            if (state.sshOutput.isEmpty()) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Ready. Type a command below.", color = Color(0xFF555555), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    items(state.sshOutput) { line ->
                        val color = when {
                            line.contains("error", true) || line.contains("fail", true) || line.contains("denied", true) -> Color(0xFFFF6B6B)
                            line.contains("success", true) || line.contains("ok", true) -> Color(0xFF69DB7C)
                            line.startsWith("$") || line.startsWith("#") || line.startsWith("root@") -> Color(0xFF74C0FC)
                            else -> Color(0xFFD4D4D4)
                        }
                        Text(
                            text = line, color = color, fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 1.dp), softWrap = true
                        )
                    }
                }
            }
        }

        // Input bar — Tab and Ctrl+C are sent as raw keys, typed text sent as command on Enter
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ctrl+C
                FilledTonalButton(
                    onClick = { viewModel.sendSshKey(3.toByte()) },
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("⌃C", fontWeight = FontWeight.Bold, fontSize = 12.sp) }

                Spacer(Modifier.width(4.dp))

                // Tab
                FilledTonalButton(
                    onClick = { viewModel.sendSshKey(9.toByte()) },
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("⇥Tab", fontWeight = FontWeight.Bold, fontSize = 11.sp) }

                Spacer(Modifier.width(6.dp))

                // Command input
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    placeholder = { Text("command...", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
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
                        ) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                )

                Spacer(Modifier.width(4.dp))

                // Enter
                FilledTonalButton(
                    onClick = {
                        if (command.isNotBlank()) {
                            viewModel.sendSshCommand(command)
                            command = ""
                        }
                    },
                    enabled = command.isNotBlank(),
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) { Text("↵", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    }
}
