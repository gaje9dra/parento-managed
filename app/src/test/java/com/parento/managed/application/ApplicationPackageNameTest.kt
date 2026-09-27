package com.parento.managed.application

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class ApplicationPackageNameTest {
    @Test fun acceptsValidAndroidPackageNames() {
        assertNotNull(normalizeApplicationPackageName("com.example.app"))
        assertNotNull(normalizeApplicationPackageName("com.example_app.child"))
    }

    @Test fun rejectsMalformedPackageNames() {
        assertNull(normalizeApplicationPackageName(""))
        assertNull(normalizeApplicationPackageName("example"))
        assertNull(normalizeApplicationPackageName("com..example"))
        assertNull(normalizeApplicationPackageName("com.example app"))
    }

    @Test fun trimsOnlyOuterWhitespaceWithoutChangingIdentifierContent() {
        assertEquals("com.example.app", normalizeApplicationPackageName("  com.example.app  "))
    }
}
