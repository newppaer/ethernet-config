package com.ethconfig.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

// ========== LOGIN FORM ==========

@Composable
private fun SshLoginForm(viewModel: MainViewModel, host: String, state: MainViewModel.UiState) {
    var username by remember { mutableStateOf("root") }
    var password by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var screen by remember { mutableStateOf("login") } // login | accounts

    if (screen == "accounts") {
        AccountEditorScreen(
            initialAccounts = state.savedAccounts,
            onSave = { viewModel.saveAccounts(it); screen = "login" },
            onDismiss = { screen = "login" }
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("SSH Connection", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        Text(host, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(24.dp))

        // Saved accounts row
        if (state.savedAccounts.isNotEmpty()) {
            Text("Saved Accounts", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.savedAccounts) { acc ->
                    val sel = acc.username == username && acc.password == password
                    FilterChip(selected = sel, onClick = {
                        username = acc.username; password = acc.password
                        if (acc.port > 0) port = acc.port.toString()
                    }, label = { Text("${acc.username}@${acc.label}", fontSize = 12.sp) })
                }
                item {
                    FilterChip(selected = false, onClick = { screen = "accounts" },
                        label = { Text("✏️", fontSize = 13.sp) })
                }
            }
            Spacer(Modifier.height(16.dp))
        } else {
            TextButton(onClick = { screen = "accounts" }, modifier = Modifier.align(Alignment.Start)) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                Text("Add Accounts", fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(host, {}, label = { Text("Host") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true, enabled = false)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(port, { port = it }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(username, { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true, visualTransformation = PasswordVisualTransformation())

        state.sshError?.let { error ->
            Spacer(Modifier.height(10.dp))
            Surface(color = Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(error, color = Color(0xFFC62828), fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { viewModel.connectSsh(host, port.toIntOrNull() ?: 22, username, password) },
            enabled = username.isNotBlank() && password.isNotBlank() && !state.sshConnecting,
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp)
        ) {
            if (state.sshConnecting) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp)); Text("Connecting...", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            } else {
                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp))
                Text("Connect", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// ========== ACCOUNT EDITOR ==========

private data class EditableAccount(
    val label: String,
    val username: String,
    val password: String,
    val port: String
)

@Composable
private fun AccountEditorScreen(
    initialAccounts: List<MainViewModel.SshAccount>,
    onSave: (List<MainViewModel.SshAccount>) -> Unit,
    onDismiss: () -> Unit
) {
    val accounts = remember {
        mutableStateListOf<EditableAccount>().apply {
            initialAccounts.forEach { add(EditableAccount(it.label, it.username, it.password, if (it.port > 0) it.port.toString() else "")) }
            if (isEmpty()) add(EditableAccount("", "root", "", "22"))
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Saved Accounts", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val result = accounts.filter { it.username.isNotBlank() }.map {
                    MainViewModel.SshAccount(it.label, it.username, it.password, it.port.toIntOrNull() ?: 22)
                }
                onSave(result)
            }) { Text("Save") }
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(accounts, key = { idx -> idx }) { _ ->
                // Use indexed access inside items
            }
            // Better approach: items with index
            items(accounts.size) { idx ->
                AccountRow(accounts, idx)
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { accounts.add(EditableAccount("", "root", "", "22")) },
            modifier = Modifier.fillMaxWidth()
        ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Add Account") }
    }
}

@Composable
private fun AccountRow(accounts: SnapshotStateList<EditableAccount>, idx: Int) {
    val acc = accounts[idx]
    Surface(color = Color(0xFFF5F5F5), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = acc.label,
                    onValueChange = { accounts[idx] = acc.copy(label = it) },
                    label = { Text("Label") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp), singleLine = true
                )
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = { if (accounts.size > 1) accounts.removeAt(idx) }) {
                    Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFC62828))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                OutlinedTextField(
                    value = acc.username,
                    onValueChange = { accounts[idx] = acc.copy(username = it) },
                    label = { Text("Username") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp), singleLine = true
                )
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(
                    value = acc.password,
                    onValueChange = { accounts[idx] = acc.copy(password = it) },
                    label = { Text("Password") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp), singleLine = true
                )
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = acc.port,
                onValueChange = { accounts[idx] = acc.copy(port = it) },
                label = { Text("Port") },
                modifier = Modifier.width(100.dp),
                shape = RoundedCornerShape(8.dp), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
    }
}

// ========== SSH TERMINAL ==========

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SshTerminal(state: MainViewModel.UiState, viewModel: MainViewModel) {
    var command by remember { mutableStateOf("") }
    var screen by remember { mutableStateOf("terminal") } // terminal | cmds
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(state.sshOutput.size) {
        if (state.sshOutput.isNotEmpty()) listState.animateScrollToItem(state.sshOutput.size - 1)
    }

    if (screen == "cmds") {
        QuickCommandEditorScreen(
            initialGroups = state.quickCommands,
            onSave = { viewModel.saveQuickCommands(it); screen = "terminal" },
            onDismiss = { screen = "terminal" }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Quick commands bar
        if (state.quickCommands.isNotEmpty()) {
            Surface(color = Color(0xFF252525), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    state.quickCommands.forEach { group ->
                        if (group.commands.isNotEmpty()) {
                            Text(group.name, fontSize = 10.sp, color = Color(0xFFAAAAAA), fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 10.dp, top = 2.dp, bottom = 2.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(group.commands) { cmd ->
                                    AssistChip(
                                        onClick = { viewModel.sendSshCommand(cmd) },
                                        label = { Text(cmd, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFE0E0E0)) },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF3A3A3A))
                                    )
                                }
                            }
                        }
                    }
                    TextButton(onClick = { screen = "cmds" }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Text("✏️ Edit Commands", fontSize = 11.sp, color = Color(0xFF888888))
                    }
                }
            }
        } else {
            Surface(color = Color(0xFF252525), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { screen = "cmds" }, modifier = Modifier.padding(4.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp), tint = Color(0xFFAAAAAA))
                    Spacer(Modifier.width(4.dp)); Text("Add Quick Commands", fontSize = 12.sp, color = Color(0xFFAAAAAA))
                }
            }
        }

        // Terminal
        Surface(color = Color(0xFF1E1E1E), modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.sshOutput.isEmpty()) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Ready. Type a command below.", color = Color(0xFF555555), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.padding(8.dp)) {
                    items(state.sshOutput) { line ->
                        val c = line.replace(Regex("\\x1B\\[[0-9;]*[a-zA-Z]"), "")
                            .replace(Regex("\\x1B\\][^\\x07]*\\x07"), "")
                            .replace(Regex("\\x1B\\([A-Z]"), "")
                        if (c.isBlank()) return@items
                        val color = when {
                            c.contains("error", true) || c.contains("fail", true) || c.contains("denied", true) -> Color(0xFFFF6B6B)
                            c.contains("success", true) || c.contains("ok", true) -> Color(0xFF69DB7C)
                            c.startsWith("$") || c.startsWith("#") || c.startsWith("root@") -> Color(0xFF74C0FC)
                            else -> Color(0xFFD4D4D4)
                        }
                        Text(text = c, color = color, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                                .combinedClickable(onClick = {}, onLongClick = { clipboard.setText(AnnotatedString(c.trim())) }),
                            softWrap = true)
                    }
                }
            }
        }

        // Input
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { viewModel.sendSshKey(3.toByte()) }, modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 10.dp))
                { Text("⌃C", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(value = command, onValueChange = { command = it },
                    placeholder = { Text("command...", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), singleLine = true,
                    trailingIcon = { IconButton(onClick = { if (command.isNotBlank()) { viewModel.sendSshCommand(command); command = "" } }, enabled = command.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "Send") } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(onClick = { if (command.isNotBlank()) { viewModel.sendSshCommand(command); command = "" } },
                    enabled = command.isNotBlank(), modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 10.dp))
                { Text("↵", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    }
}

// ========== QUICK COMMAND EDITOR ==========

private data class EditableGroup(val name: String, val cmds: String)

@Composable
private fun QuickCommandEditorScreen(
    initialGroups: List<MainViewModel.CommandGroup>,
    onSave: (List<MainViewModel.CommandGroup>) -> Unit,
    onDismiss: () -> Unit
) {
    val groups = remember {
        mutableStateListOf<EditableGroup>().apply {
            initialGroups.forEach { add(EditableGroup(it.name, it.commands.joinToString("\n"))) }
            if (isEmpty()) {
                add(EditableGroup("View", "df -h\nfree -h\nip addr show"))
                add(EditableGroup("Execute", ""))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quick Commands", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val result = groups.filter { it.name.isNotBlank() }.map { g ->
                    MainViewModel.CommandGroup(g.name, g.cmds.split("\n").map { it.trim() }.filter { it.isNotBlank() })
                }
                onSave(result)
            }) { Text("Save") }
        }
        Text("One command per line under each group", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(groups.size) { idx ->
                val g = groups[idx]
                Surface(color = Color(0xFFF5F5F5), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = g.name,
                                onValueChange = { groups[idx] = g.copy(name = it) },
                                label = { Text("Group Name") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp), singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            )
                            Spacer(Modifier.width(6.dp))
                            IconButton(onClick = { if (groups.size > 1) groups.removeAt(idx) }) {
                                Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFC62828))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = g.cmds,
                            onValueChange = { groups[idx] = g.copy(cmds = it) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                            shape = RoundedCornerShape(8.dp),
                            placeholder = { Text("command1\ncommand2\n...") },
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { groups.add(EditableGroup("New Group", "")) },
            modifier = Modifier.fillMaxWidth()
        ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Add Group") }
    }
}
