package com.arziki.fileshare

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

const val DEFAULT_PORT = 48500          // same as PORT in the Windows app's config.py
const val EXPECTED_APP = "ShareFiles"   // same as APP_NAME in the Windows app
const val APP_VERSION = "1.01"

data class Conn(val host: String, val port: Int, val token: String = "")
data class RemoteFile(val id: String, val name: String, val size: Long)
data class PairResult(val token: String, val pcName: String)
data class FileMeta(val name: String, val size: Long)

class ApiException(val code: Int, val error: String, val retryAfter: Int = 0) : Exception(error)

object Api {

    private fun open(c: Conn, path: String, method: String, readTimeoutMs: Int = 20_000): HttpURLConnection {
        val conn = URL("http://${c.host}:${c.port}$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 6_000
        conn.readTimeout = readTimeoutMs
        if (c.token.isNotEmpty()) conn.setRequestProperty("Authorization", "Bearer ${c.token}")
        return conn
    }

    private fun raise(conn: HttpURLConnection): Nothing {
        val code = conn.responseCode
        val body = try {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (e: Exception) {
            ""
        }
        var error = "http_$code"
        var retry = 0
        try {
            val j = JSONObject(body)
            error = j.optString("error", error)
            retry = j.optInt("retry_after", 0)
        } catch (e: Exception) {
            // not JSON, keep defaults
        }
        throw ApiException(code, error, retry)
    }

    private fun copyStream(input: InputStream, output: OutputStream, onProgress: (Long) -> Unit) {
        val buf = ByteArray(256 * 1024)
        var total = 0L
        var last = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            output.write(buf, 0, n)
            total += n
            val now = SystemClock.elapsedRealtime()
            if (now - last > 150) {
                onProgress(total)
                last = now
            }
        }
        onProgress(total)
    }

    private fun mimeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    /** Checks that the address is really the Arziki Files Share PC app. Returns the PC name. */
    suspend fun info(c: Conn): String = withContext(Dispatchers.IO) {
        val conn = open(c, "/api/info", "GET")
        try {
            if (conn.responseCode != 200) raise(conn)
            val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            if (j.optString("app") != EXPECTED_APP) throw ApiException(0, "not_our_app")
            j.optString("name", "PC")
        } finally {
            conn.disconnect()
        }
    }

    /** Pair with a 6-digit code, or with an empty code to ask for approval on the PC. */
    suspend fun pair(c: Conn, deviceId: String, deviceName: String, code: String): PairResult =
        withContext(Dispatchers.IO) {
            val conn = open(c, "/api/pair", "POST", readTimeoutMs = 100_000)
            try {
                val json = JSONObject().put("device_id", deviceId).put("device_name", deviceName)
                if (code.isNotBlank()) json.put("code", code)
                val body = json.toString().toByteArray(Charsets.UTF_8)
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
                if (conn.responseCode != 200) raise(conn)
                val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                PairResult(j.getString("token"), j.optString("pc_name", "PC"))
            } finally {
                conn.disconnect()
            }
        }

    suspend fun listFiles(c: Conn): List<RemoteFile> = withContext(Dispatchers.IO) {
        val conn = open(c, "/api/files", "GET")
        try {
            if (conn.responseCode != 200) raise(conn)
            val arr = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getJSONArray("files")
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                RemoteFile(o.getString("id"), o.getString("name"), o.optLong("size", 0L))
            }
        } finally {
            conn.disconnect()
        }
    }

    /** PC -> phone. Saves into Downloads/ArzikiFiles. */
    suspend fun download(ctx: Context, c: Conn, file: RemoteFile, onProgress: (Long) -> Unit) {
        withContext(Dispatchers.IO) {
            val resolver = ctx.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name.replace('/', '_').replace('\\', '_'))
                put(MediaStore.Downloads.MIME_TYPE, mimeFor(file.name))
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ArzikiFiles")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Can't create the file in Downloads")
            var ok = false
            try {
                val conn = open(c, "/api/files/" + file.id, "GET", readTimeoutMs = 60_000)
                try {
                    if (conn.responseCode != 200) raise(conn)
                    conn.inputStream.use { input ->
                        val out = resolver.openOutputStream(target) ?: throw IOException("Can't write the file")
                        out.use { copyStream(input, it, onProgress) }
                    }
                } finally {
                    conn.disconnect()
                }
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                resolver.update(target, done, null, null)
                ok = true
            } finally {
                if (!ok) resolver.delete(target, null, null)
            }
            Unit
        }
    }

    fun queryMeta(ctx: Context, uri: Uri): FileMeta {
        var name: String? = null
        var size = -1L
        try {
            ctx.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
            )?.use { cur ->
                if (cur.moveToFirst()) {
                    if (!cur.isNull(0)) name = cur.getString(0)
                    if (!cur.isNull(1)) size = cur.getLong(1)
                }
            }
        } catch (e: Exception) {
            // fall back to defaults
        }
        return FileMeta(name ?: uri.lastPathSegment ?: "file", size)
    }

    /** Phone -> PC. Returns the name the PC saved the file as. */
    suspend fun upload(
        ctx: Context,
        c: Conn,
        uri: Uri,
        meta: FileMeta,
        onTotal: (Long) -> Unit,
        onProgress: (Long) -> Unit
    ): String = withContext(Dispatchers.IO) {
        var length = meta.size
        var cache: File? = null
        try {
            if (length < 0) {
                // Size unknown: copy to a temporary file first so we can send Content-Length.
                val tmp = File.createTempFile("upload", ".tmp", ctx.cacheDir)
                cache = tmp
                val src = ctx.contentResolver.openInputStream(uri) ?: throw IOException("Can't read this file")
                src.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                length = tmp.length()
                onTotal(length)
            }
            val input: InputStream = cache?.inputStream()
                ?: ctx.contentResolver.openInputStream(uri)
                ?: throw IOException("Can't read this file")
            val conn = open(c, "/api/upload?name=" + URLEncoder.encode(meta.name, "UTF-8"), "POST", readTimeoutMs = 120_000)
            try {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/octet-stream")
                conn.setFixedLengthStreamingMode(length)
                try {
                    input.use { i -> conn.outputStream.use { o -> copyStream(i, o, onProgress) } }
                } catch (e: IOException) {
                    val code = try { conn.responseCode } catch (x: Exception) { -1 }
                    if (code in 400..599) raise(conn)
                    throw e
                }
                if (conn.responseCode != 200) raise(conn)
                JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optString("saved_as", meta.name)
            } finally {
                conn.disconnect()
            }
        } finally {
            cache?.delete()
        }
    }
}
