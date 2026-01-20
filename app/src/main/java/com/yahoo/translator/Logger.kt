package com.yahoo.translator

import java.text.SimpleDateFormat
import java.util.*

object Logger {
    private val logs = mutableListOf<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] $message"
        logs.add(logEntry)
        android.util.Log.d("Yahoo", message)
    }
    
    fun getLogs(): List<String> = logs.toList()
    
    fun clear() = logs.clear()
}
