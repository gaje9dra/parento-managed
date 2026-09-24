package com.parento.managed.lifecycle

import com.parento.managed.domain.OperationResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ManagedStartupOrchestrator(
    private val initializer: ManagedDeviceInitializer,
) {
    private val mutex = Mutex()

    suspend fun initialize(): OperationResult<ManagementState> =
        mutex.withLock { initializer.initialize() }

    suspend fun refresh(): OperationResult<ManagementState> =
        mutex.withLock { initializer.initialize() }
}