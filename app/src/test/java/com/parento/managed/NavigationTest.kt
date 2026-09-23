package com.parento.managed

import com.parento.managed.navigation.RootDestination
import com.parento.managed.navigation.RootNavigator
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTest {
    @Test
    fun rootDestinations_areStable() {
        assertEquals(RootDestination.DEVICE_STATUS, RootDestination.valueOf("DEVICE_STATUS"))
        assertEquals(
            RootDestination.ENROLLMENT_PLACEHOLDER,
            RootDestination.valueOf("ENROLLMENT_PLACEHOLDER"),
        )
        assertEquals(RootDestination.ERROR, RootDestination.valueOf("ERROR"))
    }

    @Test
    fun navigator_changesDestination() {
        val navigator = RootNavigator()
        navigator.navigate(RootDestination.ERROR)
        assertEquals(RootDestination.ERROR, navigator.currentDestination)
    }

    @Test
    fun navigator_doesNotChangeDestinationWithoutNavigation() {
        val navigator = RootNavigator()
        assertEquals(RootDestination.DEVICE_STATUS, navigator.currentDestination)
    }
}
