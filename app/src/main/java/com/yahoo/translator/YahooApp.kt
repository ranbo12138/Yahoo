package com.yahoo.translator

import android.app.Application

class YahooApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        AILogger.init(this)
    }
}
