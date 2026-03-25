package com.aether.player

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Car App Library entry point for Android Auto.
 * Declared in manifest with category.NAVIGATION.
 * Returns AetherNavigationSession which hosts the Tron-styled map.
 */
class AetherCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        // Allow all hosts for sideloaded development.
        // For production, restrict to known Android Auto hosts.
        return if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        }
    }

    override fun onCreateSession(): Session {
        return AetherNavigationSession()
    }
}
