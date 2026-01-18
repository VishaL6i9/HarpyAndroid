package com.vishal.harpy

import android.app.Application
import com.vishal.harpy.core.utils.LogUtils
import com.vishal.harpy.core.utils.VendorLookup
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HarpyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        VendorLookup.initialize(this)
        LogUtils.initialize(this)
        LogUtils.startLogcatCapture(this)
        LogUtils.clearLogBufferAtStart()
    }
}