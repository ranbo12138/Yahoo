package com.yahoo.translator

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

data class AILogEntry(
    val id: String = UUID.randomUUID().toString(),
    val time: String,
    val method: String,
    val url: String,
    val status: Int,
    val duration: Long,
    val requestHeaders: String,
    val requestBody: String,
    val responseHeaders: String,
    val responseBody: String
)

object AILogger {
    private const val PREF = "ai_logs"
    private const val KEY = "logs"
    private const val MAX = 100
    
    private var prefs: SharedPreferences? = null
    private val gson = Gson()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    }
    
    fun log(
        method: String, url: String, status: Int, duration: Long,
        reqHeaders: String, reqBody: String, resHeaders: String, resBody: String
    ) {
        try {
            val entry = AILogEntry(
                time = fmt.format(Date()),
                method = method, url = url, status = status, duration = duration,
                requestHeaders = reqHeaders, requestBody = reqBody,
                responseHeaders = resHeaders, responseBody = resBody
            )
            val logs = getLogs().toMutableList()
            logs.add(0, entry) // 最新的在前面
            while (logs.size > MAX) logs.removeLast()
            prefs?.edit()?.putString(KEY, gson.toJson(logs))?.commit()
        } catch (_: Exception) {}
    }
    
    fun getLogs(): List<AILogEntry> {
        return try {
            val raw = prefs?.getString(KEY, "[]") ?: "[]"
            gson.fromJson(raw, object : TypeToken<List<AILogEntry>>() {}.type)
        } catch (_: Exception) { emptyList() }
    }
    
    fun clear() { prefs?.edit()?.putString(KEY, "[]")?.commit() }
    
    fun export(): String {
        return getLogs().joinToString("\n\n${"─".repeat(40)}\n\n") { entry ->
            """
            |${entry.method} ${entry.url}
            |Time: ${entry.time} | Status: ${entry.status} | ${entry.duration}ms
            |
            |── Request Headers ──
            |${entry.requestHeaders}
            |
            |── Request Body ──
            |${entry.requestBody}
            |
            |── Response Headers ──
            |${entry.responseHeaders}
            |
            |── Response Body ──
            |${entry.responseBody}
            """.trimMargin()
        }
    }
}
