package com.ethconfig.app.ui.screens

import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("SSH Connection", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        Text(host, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(host, {}, label = { Text("Host") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true, enabled = false)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(port, { port = it }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(username, { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)

        state.sshError?.let { error ->
            Spacer(Modifier.height(12.dp))
            Surface(color = Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp)
        ) {
            if (state.sshConnecting) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Connecting...", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            } else {
                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp))
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
    var showCmdEditor by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(state.sshOutput.size) {
        if (state.sshOutput.isNotEmpty()) listState.animateScrollToItem(state.sshOutput.size - 1)
    }

    if (showCmdEditor) {
        QuickCommandEditor(
            commands = state.quickCommands,
            onSave = { viewModel.saveQuickCommands(it) },
            onDismiss = { showCmdEditor = false }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Quick commands bar
        if (state.quickCommands.isNotEmpty()) {
            Surface(color = Color(0xFF2A2A2A), modifier = Modifier.fillMaxWidth()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.quickCommands) { cmd ->
                        AssistChip(
                            onClick = { viewModel.sendSshCommand(cmd) },
                            label = { Text(cmd, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                        )
                    }
                    // Edit button
                    item {
                        AssistChip(
                            onClick = { showCmdEditor = true },
                            label = { Text("✏️", fontSize = 12.sp) }
                        )
                    }
                }
            }
        } else {
            // No commands yet - show add button
            Surface(color = Color(0xFF2A2A2A), modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { showCmdEditor = true },
                    modifier = Modifier.padding(4.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Quick Commands", fontSize = 12.sp)
                }
            }
        }

        // Terminal output
        Surface(color = Color(0xFF1E1E1E), modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.sshOutput.isEmpty()) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Ready. Type a command below.", color = Color(0xFF555555), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    items(state.sshOutput) { line ->
                        val displayLine = line.replace(Regex("\\x1B\\[[0-9;]*[a-zA-Z]"), "")
                            .replace(Regex("\\x1B\\][^\\x07]*\\x07"), "")
                            .replace(Regex("\\x1B\\([A-Z]"), "")
                        if (displayLine.isBlank()) return@items
                        val color = when {
                            displayLine.contains("error", true) || displayLine.contains("fail", true) || displayLine.contains("denied", true) -> Color(0xFFFF6B6B)
                            displayLine.contains("success", true) || displayLine.contains("ok", true) -> Color(0xFF69DB7C)
                            displayLine.startsWith("$") || displayLine.startsWith("#") || displayLine.startsWith("root@") -> Color(0xFF74C0FC)
                            else -> Color(0xFFD4D4D4)
                        }
                        Text(
                            text = displayLine, color = color, fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        clipboard.setText(AnnotatedString(displayLine.trim()))
                                    }
                                ),
                            softWrap = true
                        )
                    }
                }
            }
        }

        // Input bar
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = { viewModel.sendSshKey(3.toByte()) },
                    modifier = Modifier.height(40.dp), shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("⌃C", fontWeight = FontWeight.Bold, fontSize = 12.sp) }

                Spacer(Modifier.width(4.dp))
                FilledTonalButton(
                    onClick = { viewModel.sendSshKey(9.toByte()) },
                    modifier = Modifier.height(40.dp), shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("⇥Tab", fontWeight = FontWeight.Bold, fontSize = 11.sp) }

                Spacer(Modifier.width(6.dp))
                OutlinedTextField(
                    value = command, onValueChange = { command = it },
                    placeholder = { Text("command...", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = { if (command.isNotBlank()) { viewModel.sendSshCommand(command); command = "" } },
                            enabled = command.isNotBlank()
                        ) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                )

                Spacer(Modifier.width(4.dp))
                FilledTonalButton(
                    onClick = { if (command.isNotBlank()) { viewModel.sendSshCommand(command); command = "" } },
                    enabled = command.isNotBlank(),
                    modifier = Modifier.height(40.dp), shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) { Text("↵", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    }
}

@Composable
private fun QuickCommandEditor(
    commands: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(commands.joinToString("\n")) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quick Commands", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val list = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                onSave(list)
                onDismiss()
            }) { Text("Save") }
        }
        Text("One command per line", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            placeholder = { Text("df -h\nfree -h\nip addr show\n...") },
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        )
    }
}
