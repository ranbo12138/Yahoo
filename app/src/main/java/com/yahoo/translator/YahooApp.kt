package com.yahoo.translator

import android.app.Application

class YahooApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Logger.init(this)
        val v = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "?" }
        Logger.log("=== Yahoo! v$v 启动 ===")
    }
}
