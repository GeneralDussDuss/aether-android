package com.aether.player

import android.content.ComponentName
import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors

/**
 * Car App Session for Android Auto navigation display.
 * Creates the AetherMapScreen which renders the Tron-styled map.
 * Connects to AetherMediaService's MediaSession and passes now-playing
 * metadata to the map screen's HUD overlay.
 */
class AetherNavigationSession : Session() {

    private var mapScreen: AetherMapScreen? = null
    private var mediaController: MediaController? = null

    override fun onCreateScreen(intent: Intent): Screen {
        val screen = AetherMapScreen(carContext)
        mapScreen = screen
        connectToMediaSession()
        return screen
    }

    override fun onCarConfigurationChanged(newConfiguration: android.content.res.Configuration) {
        // Release media controller when session is reconfigured
    }

    /**
     * Clean up media controller. Called internally when the session lifecycle ends.
     * Car App Session doesn't have onDestroy, so we use the lifecycle observer pattern.
     */
    init {
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                mediaController?.release()
                mediaController = null
                mapScreen = null
            }
        })
    }

    /**
     * Connect to AetherMediaService's MediaSession via MediaController.
     * Listens for metadata changes and forwards them to the map screen HUD.
     */
    private fun connectToMediaSession() {
        try {
            val sessionToken = SessionToken(
                carContext,
                ComponentName(carContext, AetherMediaService::class.java)
            )
            val controllerFuture = MediaController.Builder(carContext, sessionToken).buildAsync()
            controllerFuture.addListener({
                try {
                    val controller = controllerFuture.get()
                    mediaController = controller

                    controller.addListener(object : Player.Listener {
                        override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                            mapScreen?.updateNowPlaying(
                                metadata.title?.toString(),
                                metadata.artist?.toString()
                            )
                        }
                    })

                    // Read initial metadata if already playing
                    controller.mediaMetadata.let { meta ->
                        mapScreen?.updateNowPlaying(
                            meta.title?.toString(),
                            meta.artist?.toString()
                        )
                    }
                } catch (_: Exception) { }
            }, MoreExecutors.directExecutor())
        } catch (_: Exception) { }
    }
}
