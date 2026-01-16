package com.vishal.harpy

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HarpyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}