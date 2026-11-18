package com.arziki.fileshare

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException

class TransferItem(val name: String, val sending: Boolean, total: Long) {
    var total by mutableLongStateOf(total)
    var done by mutableLongStateOf(0L)
    var status by mutableStateOf("active")   // active, done, failed
    var message by mutableStateOf("")
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val prefs = Prefs(ctx)

    var tab by mutableIntStateOf(0)
    var hostInput by mutableStateOf(prefs.hostText)
    var codeInput by mutableStateOf("")
    var token by mutableStateOf(prefs.token)
    var pcName by mutableStateOf(prefs.pcName)
    var message by mutableStateOf("")
    var busy by mutableStateOf(false)
    var theme by mutableStateOf(prefs.theme)
    var deviceName by mutableStateOf(prefs.deviceName)

    var files by mutableStateOf<List<RemoteFile>>(emptyList())
    var filesLoading by mutableStateOf(false)
    var filesMessage by mutableStateOf("")
    val transfers = mutableStateListOf<TransferItem>()

    val connected: Boolean get() = token.isNotEmpty()

    init {
        if (connected) {
            tab = 1
            refreshFiles()
        }
    }

    private fun parseHost(raw: String): Pair<String, Int> {
        var s = raw.trim().removePrefix("http://").trimEnd('/')
        var port = DEFAULT_PORT
        val idx = s.lastIndexOf(':')
        if (idx > 0) {
            val p = s.substring(idx + 1).toIntOrNull()
            if (p != null) {
                port = p
                s = s.substring(0, idx)
            }
        }
        return Pair(s, port)
    }

    private fun conn(): Conn {
        val (h, p) = parseHost(prefs.hostText)
        return Conn(h, p, token)
    }

    private fun friendly(e: Exception): String = when (e) {
        is ApiException -> when (e.error) {
            "wrong_code" -> "That code is wrong. Check the 6-digit code on the PC."
            "code_required" -> "Enter the 6-digit code shown on the PC."
            "denied" -> "The request was declined on the PC."
            "busy" -> "The PC is busy with another request. Try again."
            "too_many_attempts" -> "Too many wrong codes. Wait ${e.retryAfter} seconds and try again."
            "unauthorized" -> "Pairing expired. Please connect again."
            "not_our_app" -> "That address isn't running Arziki Files Share."
            "not_found" -> "The PC can't find that file any more. Refresh the list."
            else -> "The PC replied with an error (${e.error})."
        }
        is SocketTimeoutException -> "The PC took too long to answer."
        is IOException -> "Can't reach the PC. Make sure both devices are on the same WiFi or hotspot, and the app is open on the PC."
        else -> e.message ?: "Something went wrong."
    }

    // ---- connect -----------------------------------------------------------
    fun connect() {
        if (busy) return
        val (h, p) = parseHost(hostInput)
        if (h.isBlank()) {
            message = "Enter the PC address first."
            return
        }
        busy = true
        message = if (codeInput.isBlank()) "Waiting for you to approve on the PC…" else "Connecting…"
        viewModelScope.launch {
            try {
                Api.info(Conn(h, p))
                val res = Api.pair(Conn(h, p), prefs.deviceId, prefs.deviceName, codeInput.trim())
                prefs.hostText = hostInput.trim()
                prefs.token = res.token
                prefs.pcName = res.pcName
                token = res.token
                pcName = res.pcName
                codeInput = ""
                message = ""
                tab = 1
                refreshFiles()
            } catch (e: Exception) {
                message = friendly(e)
            }
            busy = false
        }
    }

    fun disconnect() {
        dropPairing()
        message = ""
    }

    private fun dropPairing() {
        token = ""
        prefs.token = ""
        files = emptyList()
        tab = 0
    }

    // ---- settings ------------------------------------------------------------
    fun setThemeMode(mode: String) {
        theme = mode
        prefs.theme = mode
    }

    fun saveDeviceName(name: String) {
        val n = name.trim()
        if (n.isNotEmpty()) {
            deviceName = n
            prefs.deviceName = n
        }
    }

    // ---- receive (PC -> phone) -------------------------------------------------
    fun refreshFiles() {
        if (!connected) return
        viewModelScope.launch {
            filesLoading = true
            filesMessage = ""
            try {
                files = Api.listFiles(conn())
            } catch (e: ApiException) {
                if (e.code == 401) {
                    dropPairing()
                    message = friendly(e)
                } else {
                    filesMessage = friendly(e)
                }
            } catch (e: Exception) {
                filesMessage = friendly(e)
            }
            filesLoading = false
        }
    }

    fun download(f: RemoteFile) {
        tab = 3
        viewModelScope.launch { runDownload(f) }
    }

    fun downloadAll() {
        if (files.isEmpty()) return
        val list = files
        tab = 3
        viewModelScope.launch { for (f in list) runDownload(f) }
    }

    private suspend fun runDownload(f: RemoteFile) {
        val item = TransferItem(f.name, false, f.size)
        transfers.add(0, item)
        try {
            Api.download(ctx, conn(), f) { item.done = it }
            item.done = item.total
            item.status = "done"
            item.message = "Saved in Downloads/ArzikiFiles"
        } catch (e: Exception) {
            markFailed(item, e)
        }
    }

    // ---- send (phone -> PC) ----------------------------------------------------
    fun sendUris(uris: List<Uri>) {
        if (!connected) {
            message = "Connect to your PC first."
            tab = 0
            return
        }
        tab = 3
        viewModelScope.launch {
            for (u in uris) {
                val meta = Api.queryMeta(ctx, u)
                val item = TransferItem(meta.name, true, if (meta.size > 0) meta.size else 0L)
                transfers.add(0, item)
                try {
                    val saved = Api.upload(ctx, conn(), u, meta, { item.total = it }, { item.done = it })
                    item.done = item.total
                    item.status = "done"
                    item.message = "Saved on the PC as $saved"
                } catch (e: Exception) {
                    markFailed(item, e)
                }
            }
        }
    }

    private fun markFailed(item: TransferItem, e: Exception) {
        item.status = "failed"
        item.message = friendly(e)
        if (e is ApiException && e.code == 401) {
            dropPairing()
            message = friendly(e)
        }
    }

    fun clearHistory() {
        transfers.removeAll { it.status != "active" }
    }
}
