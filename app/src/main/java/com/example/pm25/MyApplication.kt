package com.example.pm25

import android.app.Application
import android.content.Context

lateinit var applicationContext: Context

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        com.example.pm25.applicationContext = applicationContext
    }
}


