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
        // ALLOW_ALL_HOSTS_VALIDATOR is used for both debug and release builds because:
        // 1. This is a personal/sideloaded app, not distributed via Google Play Store.
        // 2. Signature-based validation (HostValidator.Builder().addAllowedHost(...))
        //    requires knowing the SHA-256 certificate fingerprints of each car head unit
        //    manufacturer (Google, Samsung, etc.), which vary by OEM and firmware version.
        // 3. If this app is ever published to Play Store, replace with signature-based
        //    validation using known Android Auto host signatures.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return AetherNavigationSession()
    }
}
