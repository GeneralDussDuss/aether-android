package com.aether.player

import android.content.Context
import android.content.Intent
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

/**
 * Capacitor plugin that bridges WebView credentials to native SharedPreferences.
 * Called from mobile-bridge.js when settings change.
 *
 * JS side: Capacitor.Plugins.AetherCredentials.syncCredentials({baseUrl, user, password})
 * Native side: writes to SharedPreferences "aether_credentials"
 * Media service reads these on startup via SubsonicClient.loadCredentials()
 */
@CapacitorPlugin(name = "AetherCredentials")
class AetherCredentialPlugin : Plugin() {

    @PluginMethod
    fun syncCredentials(call: PluginCall) {
        val baseUrl = call.getString("baseUrl") ?: ""
        val user = call.getString("user") ?: ""
        val password = call.getString("password") ?: ""

        if (baseUrl.isEmpty() || user.isEmpty()) {
            call.reject("Missing baseUrl or user")
            return
        }

        val prefs = context.getSharedPreferences("aether_credentials", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("baseUrl", baseUrl)
            .putString("user", user)
            .putString("password", password)
            .apply()

        // Notify MediaService to reload credentials (package-scoped for Android 14+)
        val intent = Intent("com.aether.player.CREDENTIALS_UPDATED")
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)

        call.resolve()
    }
}
