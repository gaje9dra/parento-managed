package com.parento.managed.device

interface ManagementModeDetector {
    fun detect(): ManagementDetectionResult
}
