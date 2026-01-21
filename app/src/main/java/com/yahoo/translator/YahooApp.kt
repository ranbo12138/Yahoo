package com.yahoo.translator

import android.app.Application

class YahooApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Logger.init(this)
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "?" }
        Logger.log("=== APP启动 v$ver ===")
    }
}
