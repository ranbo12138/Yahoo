package com.yahoo.translator

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

object Logger {
    private const val PREF_NAME = "yahoo_logs"
    private const val KEY_LOGS = "logs"
    private const val MAX_LOGS = 500
    
    private var prefs: SharedPreferences? = null
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        val entry = "[$timestamp] $message"
        
        android.util.Log.d("Yahoo", message)
        
        val logs = getLogs().toMutableList()
        logs.add(entry)
        
        // 限制日志数量
        while (logs.size > MAX_LOGS) {
            logs.removeAt(0)
        }
        
        prefs?.edit()?.putString(KEY_LOGS, logs.joinToString("\n"))?.apply()
    }
    
    fun getLogs(): List<String> {
        val raw = prefs?.getString(KEY_LOGS, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split("\n")
    }
    
    fun clear() {
        prefs?.edit()?.remove(KEY_LOGS)?.apply()
    }
}
