package com.parento.managed.security

import com.parento.managed.domain.OperationResult

interface SecurityStore {
    fun hasDeviceIdentity(): Boolean
    fun secureRequestToken(): OperationResult<String>
}
