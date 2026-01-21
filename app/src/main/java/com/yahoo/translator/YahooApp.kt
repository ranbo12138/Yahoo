package com.yahoo.translator

import android.app.Application

class YahooApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Logger.init(this)
        Logger.log("=== APP启动 v0.3.2 ===")
    }
}
