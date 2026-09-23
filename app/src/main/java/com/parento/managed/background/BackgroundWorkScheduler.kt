package com.parento.managed.background

interface BackgroundWorkScheduler {
    /**
     * Future background synchronization work belongs here.
     * Phase 1.2 does not schedule persistent background work.
     */
    fun schedulePolicySync()
}
