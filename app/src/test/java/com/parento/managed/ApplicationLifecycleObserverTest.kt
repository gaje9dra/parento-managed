package com.parento.managed

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import com.parento.managed.lifecycle.ApplicationLifecycleObserver
import com.parento.managed.lifecycle.ApplicationLifecycleState
import com.parento.managed.logging.LogLevel
import com.parento.managed.logging.ManagedLogger
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplicationLifecycleObserverTest {
    @Test
    fun foregroundAndBackgroundTransitionsAreExplicit() {
        val owner = TestOwner()
        var foreground = 0
        val observer = ApplicationLifecycleObserver(
            onForeground = { foreground++ },
            logger = object : ManagedLogger {
                override fun log(level: LogLevel, message: String) = Unit
            },
        )

        observer.onStart(owner)

        assertEquals(ApplicationLifecycleState.FOREGROUND, observer.state)
        assertEquals(1, foreground)

        observer.onStop(owner)

        assertEquals(ApplicationLifecycleState.BACKGROUND, observer.state)
    }

    private class TestOwner : androidx.lifecycle.LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)
    }
}
