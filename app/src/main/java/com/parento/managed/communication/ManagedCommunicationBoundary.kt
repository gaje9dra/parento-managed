package com.parento.managed.communication

interface ManagedCommunicationBoundary {
    fun restApi(): BackendClient
    fun realtime(): RealtimeBoundary
    fun push(): PushBoundary
}
interface RealtimeBoundary
interface PushBoundary
