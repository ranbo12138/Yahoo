package com.yahoo.translator

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

object Logger {
    private const val PREF = "yahoo_logs"
    private const val KEY = "logs"
    private var prefs: SharedPreferences? = null
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    }
    
    fun log(msg: String) {
        android.util.Log.d("Yahoo", msg)
        try {
            val entry = "[${fmt.format(Date())}] $msg"
            val logs = getLogs().toMutableList().apply { add(entry) }
            while (logs.size > 500) logs.removeAt(0)
            prefs?.edit()?.putString(KEY, logs.joinToString("\n"))?.commit()
        } catch (_: Exception) {}
    }
    
    fun getLogs(): List<String> {
        val raw = prefs?.getString(KEY, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split("\n")
    }
    
    fun clear() { prefs?.edit()?.remove(KEY)?.commit() }
}
