package com.parento.managed.navigation

class RootNavigator {
    var currentDestination: RootDestination = RootDestination.DEVICE_STATUS
        private set

    fun navigate(destination: RootDestination) {
        currentDestination = destination
    }
}
