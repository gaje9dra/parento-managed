package com.parento.managed.device

import com.parento.managed.domain.ManagedDevice
import com.parento.managed.domain.OperationResult

interface DeviceManager {
    fun currentDevice(): ManagedDevice?
    fun updateStatus(): OperationResult<ManagedDevice>
}
