package com.vishal.harpy.core.di

import com.vishal.harpy.core.service.ServiceController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ServiceEntryPoint {
    fun getServiceController(): ServiceController
}
