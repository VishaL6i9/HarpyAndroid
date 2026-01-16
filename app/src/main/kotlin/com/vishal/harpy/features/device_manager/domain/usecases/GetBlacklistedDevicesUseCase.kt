package com.vishal.harpy.features.device_manager.domain.usecases

import com.vishal.harpy.features.device_manager.data.repository.DeviceFilterRepository
import com.vishal.harpy.core.utils.NetworkDevice

class GetBlacklistedDevicesUseCase(
    private val repository: DeviceFilterRepository
) {
    suspend operator fun invoke(allDevices: List<NetworkDevice>): List<NetworkDevice> {
        return repository.getBlacklistedDevices(allDevices)
    }
}