package com.parento.managed.data

import com.parento.managed.domain.ManagedDevice
import com.parento.managed.domain.OperationResult

interface LocalDataStore {
    fun readDevice(): ManagedDevice?
    fun saveDevice(device: ManagedDevice): OperationResult<Unit>
}
