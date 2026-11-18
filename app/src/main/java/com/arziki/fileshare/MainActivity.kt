@file:OptIn(ExperimentalMaterial3Api::class)

package com.arziki.fileshare

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            ArzikiTheme(vm.theme) { MainScreen(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    @Suppress("DEPRECATION")
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.toList() ?: emptyList()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) {
            vm.sendUris(uris)
            intent.action = null
        }
    }
}

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = -1
    while (v >= 1024 && i < units.size - 1) {
        v /= 1024
        i++
    }
    return String.format(Locale.US, "%.1f %s", v, units[i])
}

@Composable
fun MainScreen(vm: MainViewModel) {
    var showSettings by remember { mutableStateOf(false) }
    val tabs = listOf("Connect" to "🔗", "Receive" to "⬇️", "Send" to "⬆️", "History" to "🕘")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Arziki Files Share") },
                navigationIcon = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, emoji) ->
                    NavigationBarItem(
                        selected = vm.tab == i,
                        onClick = { vm.tab = i },
                        icon = { Text(emoji) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (vm.tab) {
                0 -> ConnectTab(vm)
                1 -> ReceiveTab(vm)
                2 -> SendTab(vm)
                else -> HistoryTab(vm)
            }
        }
    }

    if (showSettings) SettingsDialog(vm) { showSettings = false }
}

@Composable
fun ConnectTab(vm: MainViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Share files", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Connect your device", style = MaterialTheme.typography.titleMedium)

        if (vm.connected) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Connected", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        "${vm.pcName}  (${vm.hostInput})",
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Text("Use Receive to download files from the PC, or Send to upload files to it.")
            OutlinedButton(onClick = { vm.disconnect() }) { Text("Disconnect") }
        } else {
            Text("Put your phone and PC on the same WiFi, or connect the PC to your phone's hotspot. Then enter the address shown in the PC app.")
            OutlinedTextField(
                value = vm.hostInput,
                onValueChange = { vm.hostInput = it },
                label = { Text("PC address (for example 192.168.43.1)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vm.codeInput,
                onValueChange = { vm.codeInput = it.filter { ch -> ch.isDigit() }.take(6) },
                label = { Text("6-digit code (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Leave the code empty to approve this phone on the PC instead.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = { vm.connect() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (vm.busy) "Please wait…" else "Connect")
            }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (vm.message.isNotEmpty()) {
            Text(vm.message, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun NotConnected(vm: MainViewModel) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Connect to your PC first.", textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { vm.tab = 0 }) { Text("Go to Connect") }
    }
}

@Composable
fun ReceiveTab(vm: MainViewModel) {
    if (!vm.connected) {
        NotConnected(vm)
        return
    }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Files on your PC",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = { vm.refreshFiles() }) { Text("Refresh") }
        }
        if (vm.filesLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (vm.filesMessage.isNotEmpty()) {
            Text(vm.filesMessage, color = MaterialTheme.colorScheme.error)
        }
        if (vm.files.isEmpty() && !vm.filesLoading && vm.filesMessage.isEmpty()) {
            Text("Nothing is shared from the PC yet. Add files in the PC app, then tap Refresh.")
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.files, key = { it.id }) { f ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, fontWeight = FontWeight.Medium, maxLines = 2)
                            Text(formatSize(f.size), style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = { vm.download(f) }) { Text("Download") }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.downloadAll() },
                enabled = vm.files.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) { Text("Download all") }
            OutlinedButton(
                onClick = {
                    try {
                        context.startActivity(
                            Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) {
                        // no app to open Downloads
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Open downloads") }
        }
    }
}

@Composable
fun SendTab(vm: MainViewModel) {
    if (!vm.connected) {
        NotConnected(vm)
        return
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.sendUris(uris)
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Send files to ${vm.pcName}", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Choose files to send") }
        Spacer(Modifier.height(16.dp))
        Text(
            "Tip: you can also use Share in Gallery or Files and pick Arziki Files Share.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun HistoryTab(vm: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Transfers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.clearHistory() }) { Text("Clear") }
        }
        if (vm.transfers.isEmpty()) Text("No transfers yet.")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.transfers) { t ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text((if (t.sending) "⬆️ " else "⬇️ ") + t.name, fontWeight = FontWeight.Medium, maxLines = 2)
                        when (t.status) {
                            "active" -> {
                                val p = if (t.total > 0) (t.done.toFloat() / t.total.toFloat()).coerceIn(0f, 1f) else 0f
                                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                                Text(
                                    "${formatSize(t.done)} of ${if (t.total > 0) formatSize(t.total) else "?"}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            "done" -> Text("✅ " + t.message, style = MaterialTheme.typography.bodySmall)
                            else -> Text(
                                "❌ " + t.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(vm: MainViewModel, onClose: () -> Unit) {
    var name by remember { mutableStateOf(vm.deviceName) }
    var aboutOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Settings") },
        confirmButton = {
            TextButton(onClick = {
                vm.saveDeviceName(name)
                onClose()
            }) { Text("Done") }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Appearance", fontWeight = FontWeight.Bold)
                listOf("System", "Light", "Dark").forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.setThemeMode(mode) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = vm.theme == mode, onClick = null)
                        Text(mode, modifier = Modifier.padding(start = 10.dp))
                    }
                }
                HorizontalDivider()
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("This phone's name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("The PC sees this name when you connect.", style = MaterialTheme.typography.bodySmall)
                if (vm.connected) {
                    OutlinedButton(onClick = {
                        vm.disconnect()
                        onClose()
                    }) { Text("Forget this PC") }
                }
                HorizontalDivider()
                Text(
                    if (aboutOpen) "About  ▴" else "About  ▾",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().clickable { aboutOpen = !aboutOpen }.padding(vertical = 6.dp)
                )
                if (aboutOpen) {
                    Text("Arziki Files Share")
                    Text("Developer: Muhammad Umar Gumel")
                    Text("Version $APP_VERSION")
                }
            }
        }
    )
}
