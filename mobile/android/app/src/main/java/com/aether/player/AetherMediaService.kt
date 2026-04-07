package com.aether.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Media3 MediaLibraryService — powers both phone notification controls and Android Auto.
 *
 * Browse tree:
 *   ROOT → Albums / Artists / Playlists / Genres / Recently Added / Random Mix
 *        → drill into album/playlist → individual tracks
 *
 * Online: streams via Subsonic/Navidrome.
 * Offline: falls back to LocalMediaProvider (Android MediaStore) for local files.
 */
class AetherMediaService : MediaLibraryService() {

    companion object {
        const val CHANNEL_ID = "aether_playback"
        const val NOTIFICATION_ID = 1

        // Browse tree root IDs
        const val ROOT_ID = "__ROOT__"
        const val ALBUMS_ID = "__ALBUMS__"
        const val ARTISTS_ID = "__ARTISTS__"
        const val PLAYLISTS_ID = "__PLAYLISTS__"
        const val GENRES_ID = "__GENRES__"
        const val RECENT_ID = "__RECENT__"
        const val RANDOM_ID = "__RANDOM__"
    }

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var subsonic: SubsonicClient
    private lateinit var localMedia: LocalMediaProvider
    private val executor = Executors.newSingleThreadExecutor()

    private var isOffline = false
    private var lastNetworkCheck = 0L
    private val NETWORK_RETRY_MS = 60_000L

    /** When true, WebView is actively playing audio — native player yields audio focus. */
    @Volatile
    var webViewPlaying = false

    /** BroadcastReceiver: reloads SubsonicClient credentials when updated from WebView. */
    private val credentialReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            subsonic.loadCredentials()
        }
    }

    /** BroadcastReceiver: WebView notifies us when it starts/stops playing. */
    private val webViewAudioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val playing = intent?.getBooleanExtra("playing", false) ?: false
            webViewPlaying = playing
            if (playing && player.isPlaying) {
                // WebView took over — pause native ExoPlayer to avoid audio fight
                player.pause()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        subsonic = SubsonicClient(this)
        subsonic.loadCredentials()
        localMedia = LocalMediaProvider(this)

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // handle audio focus
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Scrobble online tracks only (skip local files)
                mediaItem?.mediaId?.let { id ->
                    if (id.startsWith("track_") && !id.startsWith("track_local_")) {
                        executor.execute { subsonic.scrobble(id.removePrefix("track_")) }
                    }
                }
            }
        })

        mediaSession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .build()

        // Register broadcast receivers
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                credentialReceiver,
                IntentFilter("com.aether.player.CREDENTIALS_UPDATED"),
                RECEIVER_NOT_EXPORTED
            )
            registerReceiver(
                webViewAudioReceiver,
                IntentFilter("com.aether.player.WEBVIEW_AUDIO_STATE"),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(
                credentialReceiver,
                IntentFilter("com.aether.player.CREDENTIALS_UPDATED")
            )
            registerReceiver(
                webViewAudioReceiver,
                IntentFilter("com.aether.player.WEBVIEW_AUDIO_STATE")
            )
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(credentialReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(webViewAudioReceiver) } catch (_: Exception) {}
        mediaSession.release()
        player.release()
        executor.shutdown()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AETHER Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media playback controls"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    // ── Online/offline detection ──────────────────────────────────────────

    /** Returns true if Navidrome is reachable. Caches result for 60s. */
    private fun checkOnline(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastNetworkCheck < NETWORK_RETRY_MS) return !isOffline
        lastNetworkCheck = now
        isOffline = !subsonic.pingFast()
        return !isOffline
    }

    /** Resolve coverArt ID to a URI (handles both local and online artwork). */
    private fun resolveArtworkUri(coverArt: String): Uri? {
        if (coverArt.isEmpty()) return null
        if (coverArt.startsWith("local_album_")) {
            val id = coverArt.removePrefix("local_album_").toLongOrNull() ?: return null
            return localMedia.artworkUri(id)
        }
        return Uri.parse(subsonic.coverUrl(coverArt, 300))
    }

    // ── Browse tree callback ─────────────────────────────────────────────

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        @OptIn(UnstableApi::class)
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = buildBrowsableItem(ROOT_ID, "AETHER")
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        @OptIn(UnstableApi::class)
        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            executor.execute {
                try {
                    val items = when (parentId) {
                        ROOT_ID -> getRootChildren()
                        ALBUMS_ID -> getAlbumList()
                        ARTISTS_ID -> getArtistList()
                        PLAYLISTS_ID -> getPlaylistList()
                        GENRES_ID -> getGenreList()
                        RECENT_ID -> getRecentList()
                        RANDOM_ID -> getRandomTracks()
                        else -> {
                            when {
                                parentId.startsWith("album_") -> getAlbumTracks(parentId.removePrefix("album_"))
                                parentId.startsWith("artist_") -> getArtistAlbums(parentId.removePrefix("artist_"))
                                parentId.startsWith("playlist_") -> getPlaylistTracks(parentId.removePrefix("playlist_"))
                                parentId.startsWith("genre_") -> getGenreSongs(parentId.removePrefix("genre_"))
                                else -> emptyList()
                            }
                        }
                    }
                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                } catch (e: Exception) {
                    future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                }
            }
            return future
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            executor.execute {
                val results = mutableListOf<MediaItem>()
                try {
                    if (checkOnline()) {
                        val resp = subsonic.get("search3", mapOf(
                            "query" to query,
                            "songCount" to "20",
                            "albumCount" to "10",
                            "artistCount" to "5"
                        ))
                        val searchResult = resp?.optJSONObject("searchResult3")
                        // Parse songs
                        val songs = searchResult?.optJSONArray("song")
                        if (songs != null) {
                            for (i in 0 until songs.length()) {
                                results.add(buildTrackItem(songs.getJSONObject(i)))
                            }
                        }
                        // Parse albums
                        val albums = searchResult?.optJSONArray("album")
                        if (albums != null) {
                            for (i in 0 until albums.length()) {
                                val a = albums.getJSONObject(i)
                                val id = a.optString("id")
                                val name = a.optString("name", "Unknown Album")
                                val artist = a.optString("artist", "")
                                val coverArt = a.optString("coverArt", "")
                                results.add(buildBrowsableItem("album_$id", name, artist, resolveArtworkUri(coverArt)))
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Search failed — return empty results
                }
                session.notifySearchResultChanged(browser, query, results.size, params)
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            executor.execute {
                val results = mutableListOf<MediaItem>()
                try {
                    if (checkOnline()) {
                        val resp = subsonic.get("search3", mapOf(
                            "query" to query,
                            "songCount" to "20",
                            "albumCount" to "10",
                            "artistCount" to "5"
                        ))
                        val searchResult = resp?.optJSONObject("searchResult3")
                        val songs = searchResult?.optJSONArray("song")
                        if (songs != null) {
                            for (i in 0 until songs.length()) {
                                results.add(buildTrackItem(songs.getJSONObject(i)))
                            }
                        }
                        val albums = searchResult?.optJSONArray("album")
                        if (albums != null) {
                            for (i in 0 until albums.length()) {
                                val a = albums.getJSONObject(i)
                                val id = a.optString("id")
                                val name = a.optString("name", "Unknown Album")
                                val artist = a.optString("artist", "")
                                val coverArt = a.optString("coverArt", "")
                                results.add(buildBrowsableItem("album_$id", name, artist, resolveArtworkUri(coverArt)))
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Search failed
                }
                // Apply pagination
                val start = page * pageSize
                val end = minOf(start + pageSize, results.size)
                val pageResults = if (start < results.size) results.subList(start, end) else emptyList()
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(pageResults), params))
            }
            return future
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                val mediaId = item.mediaId
                when {
                    mediaId.startsWith("track_local_") -> {
                        val msId = mediaId.removePrefix("track_local_").toLongOrNull()
                        if (msId != null) {
                            item.buildUpon()
                                .setUri(localMedia.contentUri(msId))
                                .build()
                        } else item
                    }
                    mediaId.startsWith("track_") -> {
                        val trackId = mediaId.removePrefix("track_")
                        item.buildUpon()
                            .setUri(Uri.parse(subsonic.streamUrl(trackId)))
                            .build()
                    }
                    else -> item
                }
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }

    // ── Browse tree builders ─────────────────────────────────────────────

    private fun getRootChildren(): List<MediaItem> {
        val online = checkOnline()
        val items = mutableListOf(
            buildBrowsableItem(ALBUMS_ID, "Albums"),
            buildBrowsableItem(ARTISTS_ID, "Artists"),
        )
        // Playlists are online-only (no local equivalent)
        if (online) items.add(buildBrowsableItem(PLAYLISTS_ID, "Playlists"))
        items.add(buildBrowsableItem(GENRES_ID, "Genres"))
        items.add(buildBrowsableItem(RECENT_ID, "Recently Added"))
        items.add(buildBrowsableItem(RANDOM_ID, "Random Mix"))
        return items
    }

    private fun getAlbumList(): List<MediaItem> {
        val albums = if (checkOnline()) subsonic.getAlbums() else localMedia.getAlbums()
        return albums.map { album ->
            val id = album.optString("id")
            val name = album.optString("name", "Unknown Album")
            val artist = album.optString("artist", "")
            val coverArt = album.optString("coverArt", "")
            buildBrowsableItem("album_$id", name, artist, resolveArtworkUri(coverArt))
        }
    }

    private fun getArtistList(): List<MediaItem> {
        val artists = if (checkOnline()) subsonic.getArtists() else localMedia.getArtists()
        return artists.map { artist ->
            val id = artist.optString("id")
            val name = artist.optString("name", "Unknown Artist")
            val coverArt = artist.optString("coverArt", "")
            buildBrowsableItem("artist_$id", name, subtitle = null, resolveArtworkUri(coverArt))
        }
    }

    private fun getArtistAlbums(artistId: String): List<MediaItem> {
        // Local artist IDs: "local_artist_{hash}"
        if (artistId.startsWith("local_artist_")) {
            val hash = artistId.removePrefix("local_artist_")
            return localMedia.getArtistAlbums(hash).map { album ->
                val id = album.optString("id")
                val name = album.optString("name", "Unknown Album")
                val coverArt = album.optString("coverArt", "")
                buildBrowsableItem("album_$id", name, subtitle = null, resolveArtworkUri(coverArt))
            }
        }
        // Online artist
        val resp = subsonic.get("getArtist", mapOf("id" to artistId))
        val albums = resp?.optJSONObject("artist")?.optJSONArray("album") ?: return emptyList()
        return (0 until albums.length()).map { i ->
            val album = albums.getJSONObject(i)
            val id = album.optString("id")
            val name = album.optString("name", "Unknown Album")
            val coverArt = album.optString("coverArt", "")
            buildBrowsableItem(
                "album_$id", name, subtitle = null,
                if (coverArt.isNotEmpty()) Uri.parse(subsonic.coverUrl(coverArt, 300)) else null
            )
        }
    }

    private fun getPlaylistList(): List<MediaItem> {
        // Playlists are online-only
        if (!checkOnline()) return emptyList()
        return subsonic.getPlaylists().map { playlist ->
            val id = playlist.optString("id")
            val name = playlist.optString("name", "Playlist")
            val count = playlist.optInt("songCount", 0)
            buildBrowsableItem("playlist_$id", name, "$count tracks")
        }
    }

    private fun getPlaylistTracks(playlistId: String): List<MediaItem> {
        if (!checkOnline()) return emptyList()
        return subsonic.getPlaylist(playlistId).map { track -> buildTrackItem(track) }
    }

    private fun getGenreList(): List<MediaItem> {
        val genres = if (checkOnline()) subsonic.getGenres() else localMedia.getGenres()
        return genres.map { genre ->
            val name = genre.optString("value", "Unknown")
            val count = genre.optInt("songCount", 0)
            buildBrowsableItem("genre_$name", name, "$count songs")
        }
    }

    private fun getGenreSongs(genre: String): List<MediaItem> {
        val songs = if (checkOnline()) subsonic.getSongsByGenre(genre) else localMedia.getSongsByGenre(genre)
        return songs.map { track -> buildTrackItem(track) }
    }

    private fun getRecentList(): List<MediaItem> {
        val albums = if (checkOnline()) subsonic.getRecentlyAdded() else localMedia.getRecentlyAdded()
        return albums.map { album ->
            val id = album.optString("id")
            val name = album.optString("name", "Unknown Album")
            val artist = album.optString("artist", "")
            val coverArt = album.optString("coverArt", "")
            buildBrowsableItem("album_$id", name, artist, resolveArtworkUri(coverArt))
        }
    }

    private fun getRandomTracks(): List<MediaItem> {
        val songs = if (checkOnline()) subsonic.getRandomSongs() else localMedia.getRandomSongs()
        return songs.map { track -> buildTrackItem(track) }
    }

    private fun getAlbumTracks(albumId: String): List<MediaItem> {
        // Local album IDs: "local_album_{mediaStoreAlbumId}"
        if (albumId.startsWith("local_album_")) {
            val msAlbumId = albumId.removePrefix("local_album_")
            return localMedia.getAlbumTracks(msAlbumId).map { track -> buildTrackItem(track) }
        }
        return subsonic.getAlbumTracks(albumId).map { track -> buildTrackItem(track) }
    }

    // ── MediaItem builders ───────────────────────────────────────────────

    private fun buildBrowsableItem(
        id: String,
        title: String,
        subtitle: String? = null,
        artworkUri: Uri? = null
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        if (subtitle != null) metadata.setSubtitle(subtitle)
        if (artworkUri != null) metadata.setArtworkUri(artworkUri)

        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata.build())
            .build()
    }

    private fun buildTrackItem(track: org.json.JSONObject): MediaItem {
        val id = track.optString("id")
        val title = track.optString("title", "Unknown")
        val artist = track.optString("artist", "Unknown")
        val album = track.optString("album", "")
        val coverArt = track.optString("coverArt", "")
        val isLocal = id.startsWith("local_")

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        if (coverArt.isNotEmpty()) {
            metadata.setArtworkUri(resolveArtworkUri(coverArt))
        }

        val uri = if (isLocal) {
            val msId = id.removePrefix("local_").toLongOrNull()
            if (msId != null) localMedia.contentUri(msId) else null
        } else {
            Uri.parse(subsonic.streamUrl(id))
        }

        val builder = MediaItem.Builder()
            .setMediaId("track_$id")
            .setMediaMetadata(metadata.build())
        if (uri != null) builder.setUri(uri)
        return builder.build()
    }
}
