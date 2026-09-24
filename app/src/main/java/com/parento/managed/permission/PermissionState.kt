package com.parento.managed.permission

enum class PermissionState {
    GRANTED,
    DENIED,
    NOT_REQUESTED,
    REQUIRES_USER_ACTION,
    NOT_APPLICABLE,
}

enum class ManagedPermission {
    CAMERA,
    MICROPHONE,
    LOCATION,
    MEDIA_PROJECTION,
}

interface PermissionManager {
    fun state(permission: ManagedPermission): PermissionState
}
