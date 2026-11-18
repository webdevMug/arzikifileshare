package com.arziki.fileshare

import android.content.Context
import android.os.Build
import java.util.UUID

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("arziki", Context.MODE_PRIVATE)

    var deviceId: String
        get() {
            val saved = sp.getString("device_id", null)
            if (saved != null) return saved
            val fresh = UUID.randomUUID().toString().replace("-", "").take(16)
            sp.edit().putString("device_id", fresh).apply()
            return fresh
        }
        set(v) { sp.edit().putString("device_id", v).apply() }

    var deviceName: String
        get() = sp.getString("device_name", null) ?: (Build.MODEL ?: "Android phone")
        set(v) { sp.edit().putString("device_name", v).apply() }

    /** What the user typed, e.g. "192.168.43.1" or "192.168.43.1:48500". */
    var hostText: String
        get() = sp.getString("host", "") ?: ""
        set(v) { sp.edit().putString("host", v).apply() }

    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) { sp.edit().putString("token", v).apply() }

    var pcName: String
        get() = sp.getString("pc_name", "") ?: ""
        set(v) { sp.edit().putString("pc_name", v).apply() }

    var theme: String
        get() = sp.getString("theme", "System") ?: "System"
        set(v) { sp.edit().putString("theme", v).apply() }
}
