package com.parento.managed.application

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationInventoryCollectorInstrumentedTest {
    @Test fun collectIsDeterministicAndBounded() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val inventory = ApplicationInventoryCollector(context, maxApplications = 50).collect()
        assertTrue(inventory.applications.size <= 50)
        assertEquals(
            inventory.applications.map { it.packageName }.sorted(),
            inventory.applications.map { it.packageName },
        )
        assertTrue(inventory.applications.all { normalizeApplicationPackageName(it.packageName) == it.packageName })
    }
}
