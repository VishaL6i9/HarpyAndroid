package com.vishal.harpy

import android.app.Application
import com.vishal.harpy.core.utils.LogUtils
import com.vishal.harpy.core.utils.VendorLookup
import com.vishal.harpy.core.service.ServiceController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HarpyApplication : Application() {

    @Inject
    lateinit var serviceController: ServiceController

    override fun onCreate() {
        super.onCreate()
        VendorLookup.initialize(this)
        LogUtils.initialize(this)
        LogUtils.startLogcatCapture(this)
        LogUtils.clearLogBufferAtStart()
        
        // Auto-start notification service on app launch
        serviceController.startNotificationService()
    }
}