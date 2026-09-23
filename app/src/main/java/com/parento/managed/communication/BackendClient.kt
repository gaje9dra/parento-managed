package com.parento.managed.communication

import com.parento.managed.domain.OperationResult

interface BackendClient {
    fun healthCheck(): OperationResult<Unit>
    fun syncPolicy(): OperationResult<Unit>
}
